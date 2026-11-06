import { z } from "zod";
import { MAX_FILE_BYTES, type ResultFiles } from "./results";

export type Json = string | number | boolean | null | Json[] | { [key: string]: Json };
export interface HttpRequest {
  method: "GET" | "POST";
  url: string;
  headers: Record<string, string>;
  body?: string;
  timeoutMs: number;
  binary?: boolean;
}
export interface HttpResponse { status: number; headers: Record<string, string>; body: string }
export interface Transport { request(request: HttpRequest): Promise<HttpResponse> }

const Task = z.object({
  requestId: z.string().min(1), sessionId: z.string().min(1), message: z.string(),
  cursor: z.number().int().nonnegative(),
  status: z.enum(["submitting", "accepted", "running", "needs_input", "reply_ready", "no_reply", "cancel_requested", "stopped"]),
});
const Connection = z.object({
  kind: z.literal("paired"), origin: z.string().min(1), credential: z.string().min(1),
  deviceName: z.string(), sessionId: z.string().optional(), lastTask: Task.optional(),
});
const Pending = z.object({
  kind: z.literal("pairing"), origin: z.string().min(1), deviceName: z.string(),
  id: z.string().min(1), key: z.string().min(1), expiresAt: z.number(),
});
export const StoredState = z.discriminatedUnion("kind", [z.object({ kind: z.literal("empty") }), Pending, Connection]);
export type StoredState = z.infer<typeof StoredState>;
export type PairedConnection = z.infer<typeof Connection>;
export type PendingPairing = z.infer<typeof Pending>;
export interface Store { read(): StoredState; write(state: StoredState): void }
export interface PairTarget { origin: string; token: string }
export interface Dependencies {
  transport: Transport; store: Store; newId(): string; now(): number;
  parsePairCode(text: string): PairTarget; timeZone: string; files: ResultFiles;
}
export class ComputerError extends Error {
  constructor(public readonly code: string, message: string) { super(message); this.name = "ComputerError"; }
}

const SessionState = z.object({
  cursor: z.number().int().nonnegative(), active: z.boolean(), running: z.boolean(),
  queue: z.array(z.object({ rpcId: z.string().optional() }).passthrough()).nullable(),
  pendingInput: z.array(z.enum(["approval", "question", "other"])),
});
const WaitResponse = z.object({
  reason: z.enum(["idle", "input-required", "timeout"]), state: SessionState,
  output: z.object({ records: z.array(z.json()), truncated: z.boolean() }),
});
const SessionList = z.object({ items: z.array(z.object({
  sessionId: z.string(), cwd: z.string().optional(), running: z.boolean(), updatedAt: z.number(),
  projections: z.record(z.string(), z.json()).optional(),
})) });
export type SessionRow = z.infer<typeof SessionList>["items"][number];
export type TaskStatus = z.infer<typeof Task>["status"];

/** Uses the existing remote plugin for device trust and DSH's own HTTP RPC for work. */
export class ComputerClient {
  constructor(private readonly deps: Dependencies) {}

  publicState() {
    const stored = this.deps.store.read();
    if (stored.kind === "empty") return { kind: "empty" as const };
    if (stored.kind === "pairing") return {
      kind: "pairing" as const, origin: stored.origin, deviceName: stored.deviceName, expiresAt: stored.expiresAt,
    };
    return {
      kind: "paired" as const, origin: stored.origin, deviceName: stored.deviceName,
      sessionId: stored.sessionId, lastTask: stored.lastTask,
    };
  }

  async beginPairing(qrText: string, deviceName: string): Promise<void> {
    const target = this.deps.parsePairCode(qrText);
    const response = await this.post(target.origin, "/rc/pair/request", {
      token: target.token, model: deviceName,
    });
    const result = z.object({ id: z.string(), key: z.string(), expiresAt: z.number() }).parse(this.parseJson(response.body));
    this.deps.store.write({ kind: "pairing", origin: target.origin, deviceName, ...result });
  }

  async claimPairing(): Promise<"pending" | "approved" | "rejected"> {
    const pending = this.deps.store.read();
    if (pending.kind !== "pairing") throw new ComputerError("not_pairing", "请先识别电脑上的配对二维码。");
    if (this.deps.now() >= pending.expiresAt) {
      this.deps.store.write({ kind: "empty" });
      throw new ComputerError("pair_expired", "二维码已过期，请在电脑上刷新后重试。");
    }
    const response = await this.post(pending.origin, "/rc/pair/claim", { id: pending.id, key: pending.key });
    const result = z.object({ state: z.enum(["pending", "approved", "rejected"]) }).parse(this.parseJson(response.body));
    const current = this.deps.store.read();
    if (current.kind !== "pairing" || current.id !== pending.id) throw new ComputerError("pairing_changed", "这次配对已取消或替换。");
    if (result.state === "approved") {
      const setCookie = Object.entries(response.headers).find(([key]) => key.toLowerCase() === "set-cookie");
      if (!setCookie) throw new ComputerError("missing_cookie", "电脑已批准，但未返回设备凭据。");
      const match = /(?:^|;\s*)dsh_rc=([A-Za-z0-9_-]+\.[A-Za-z0-9_-]+)/.exec(setCookie[1]);
      if (!match) throw new ComputerError("invalid_cookie", "电脑返回的设备凭据格式无效。");
      this.deps.store.write({ kind: "paired", origin: pending.origin, credential: match[1], deviceName: pending.deviceName });
    } else if (result.state === "rejected") {
      this.deps.store.write({ kind: "empty" });
    }
    return result.state;
  }

  async checkConnection(): Promise<void> {
    await this.sessions();
  }

  async sessions(): Promise<SessionRow[]> {
    const result = await this.rpc("session/list", { _request: {} }, SessionList);
    return result.items;
  }

  async bindSession(sessionId: string): Promise<void> {
    const previous = this.paired();
    if (previous.sessionId === sessionId) return;
    if (previous.lastTask && ["submitting", "accepted", "running", "needs_input", "cancel_requested"].includes(previous.lastTask.status)) {
      throw new ComputerError("task_active", "请先查询或停止当前电脑会话，再切换会话。");
    }
    const sessions = await this.sessions();
    if (!sessions.some(session => session.sessionId === sessionId)) {
      throw new ComputerError("unknown_session", "这项电脑会话不存在或当前设备无权访问。");
    }
    const paired = this.paired();
    this.deps.store.write({ ...paired, sessionId, lastTask: undefined });
  }

  async submit(message: string) {
    if (!message.trim() || message.length > 16_000) throw new ComputerError("invalid_message", "请提供不超过16000字的明确任务。");
    const paired = this.paired();
    if (!paired.sessionId) throw new ComputerError("select_session", "请先选择要继续的电脑会话。");
    if (paired.lastTask?.status === "submitting") {
      throw new ComputerError("submission_uncertain", "上次提交的网络结果尚未确认，请先核对原任务的接收记录。");
    }
    if (paired.lastTask && ["accepted", "running", "needs_input", "cancel_requested"].includes(paired.lastTask.status)) {
      throw new ComputerError("task_active", "电脑任务仍在进行，请先查询这项任务的结果。");
    }
    const state = await this.rpc("session/state", { request: { sessionId: paired.sessionId } }, SessionState);
    const task = {
      requestId: this.deps.newId(), sessionId: paired.sessionId, message,
      cursor: state.cursor, status: "submitting" as const,
    };
    // Save the exact request identity before sending. A dropped response must not duplicate work.
    this.saveTask(paired, task);
    return this.sendSavedTask();
  }

  /** A timeout may hide an admitted prompt. Recover its receipt; never resend automatically. */
  async recoverSubmission() {
    const paired = this.paired();
    const task = paired.lastTask;
    if (task?.status !== "submitting") throw new ComputerError("no_uncertain_submission", "没有需要核对的原任务。");
    const state = await this.rpc("session/state", { request: { sessionId: task.sessionId } }, SessionState);
    let admitted = state.queue?.some(item => item.rpcId === task.requestId) === true;
    if (!admitted) {
      const events = await this.rpc("session/events", { request: {
        address: { kind: "session", sessionId: task.sessionId }, maxMessages: 40,
      } }, z.object({ records: z.array(z.object({ type: z.literal("event"), event: z.json() })), hasMore: z.boolean(), cursor: z.number() }));
      const userEvent = z.object({ type: z.literal("user/message"), seq: z.number(),
        data: z.object({ source: z.object({ kind: z.literal("user"), rpcId: z.string() }) }) });
      const inboxEvent = z.object({ type: z.literal("agent/inbox/spliced"), seq: z.number(),
        data: z.object({ inserted: z.array(z.object({ source: z.object({ kind: z.string(), rpcId: z.string().optional() }) })) }) });
      admitted = events.records.some(record => {
        const user = userEvent.safeParse(record.event);
        if (user.success && user.data.seq > task.cursor && user.data.data.source.rpcId === task.requestId) return true;
        const inbox = inboxEvent.safeParse(record.event);
        return inbox.success && inbox.data.seq > task.cursor
          && inbox.data.data.inserted.some(item => item.source.kind === "user" && item.source.rpcId === task.requestId);
      });
    }
    if (admitted) this.saveTask(paired, { ...task, status: "accepted" });
    return { status: admitted ? "accepted" as const : "submitting" as const,
      sessionId: task.sessionId, requestId: task.requestId, resent: false,
      meaning: admitted ? "已找到电脑的原任务接收记录，可继续查询结果。" : "尚未找到原任务接收记录；可稍后继续核对，未再次发送任务。" };
  }

  private async sendSavedTask() {
    const paired = this.paired();
    const task = paired.lastTask;
    if (!task) throw new ComputerError("no_task", "还没有电脑任务。");
    await this.rpc("session/prompt", { request: {
      requestId: task.requestId, sessionId: task.sessionId, mode: "queue",
      content: [{ type: "text", text: task.message }], clientTimeZone: this.deps.timeZone,
    } }, z.object({ accepted: z.literal(true) }));
    this.saveTask(paired, { ...task, status: "accepted" });
    return { status: "accepted" as const, sessionId: task.sessionId, requestId: task.requestId };
  }

  async waitForReply() {
    const paired = this.paired();
    const task = paired.lastTask;
    if (!task) throw new ComputerError("no_task", "还没有电脑任务。");
    if (task.status === "submitting") throw new ComputerError("submission_uncertain", "请先用 computer_recover_submission 核对原任务的接收记录。");
    const result = await this.rpc("session/wait", { request: {
      sessionId: task.sessionId, afterSeq: task.cursor, maxMessages: 4, timeoutMs: 25_000,
    } }, WaitResponse, 35_000);
    let status: TaskStatus;
    if (task.status === "cancel_requested") {
      status = result.reason === "idle" && !result.state.running && (result.state.queue === null || result.state.queue.length === 0)
        ? "stopped" : "cancel_requested";
    }
    else if (result.reason === "input-required") status = "needs_input";
    else if (result.reason === "timeout" || result.state.running || (result.state.queue !== null && result.state.queue.length > 0)) status = "running";
    else if (result.output.records.length === 0) status = "no_reply";
    else status = "reply_ready";
    this.saveTask(paired, { ...task, status });
    return {
      status, sessionId: task.sessionId, requestId: task.requestId,
      records: result.output.records, truncated: result.output.truncated,
      pendingInput: result.state.pendingInput,
      meaning: "reply_ready只表示收到电脑回复；文件保存和实际动作需核对对应执行证据。",
    };
  }

  async cancel() {
    const paired = this.paired();
    const task = paired.lastTask;
    if (!task) throw new ComputerError("no_task", "还没有电脑任务。");
    await this.rpc("session/cancel", { request: { sessionId: task.sessionId } }, z.object({ accepted: z.literal(true) }));
    this.saveTask(paired, { ...task, status: "cancel_requested" });
    return { status: "cancel_requested" as const, sessionId: task.sessionId };
  }

  private saveTask(connection: PairedConnection, task: z.infer<typeof Task>) {
    const current = this.deps.store.read();
    if (current.kind !== "paired" || current.credential !== connection.credential || current.sessionId !== connection.sessionId) {
      throw new ComputerError("connection_changed", "电脑连接已移除或切换，本次响应不会恢复旧连接。");
    }
    this.deps.store.write({ ...current, lastTask: task });
  }

  forget() { this.deps.store.write({ kind: "empty" }); }

  private paired(): PairedConnection {
    const paired = this.deps.store.read();
    if (paired.kind !== "paired") throw new ComputerError("not_paired", "请先连接电脑。");
    return paired;
  }

  async results() { return this.deps.files.list(); }

  async fetchFile(path: string) {
    // The computer owns path resolution. The phone accepts project-relative result paths.
    const parts = path.replace(/\\/g, "/").split("/");
    if (!path || path.length > 4096 || path.includes("\0") || /^[\\/]/.test(path) || /^[A-Za-z][A-Za-z0-9+.-]*:/.test(path)
        || parts.some(part => part === ".." || part === "")) {
      throw new ComputerError("invalid_file_path", "请提供电脑会话项目内的相对文件路径，例如 storyboard.md。");
    }
    const paired = this.paired();
    if (!paired.sessionId) throw new ComputerError("select_session", "请先选择电脑会话。");
    const fileStat = z.object({ absolutePath: z.string(), version: z.string(), bytes: z.number().int().nonnegative().optional() });
    const args = { workspaceFileScopeId: paired.sessionId, path };
    const before = await this.rpc("workspaceFiles/stat", args, fileStat);
    if (before.bytes !== undefined && before.bytes > MAX_FILE_BYTES) {
      throw new ComputerError("file_too_large", "当前成果回传支持5MiB以内的单个文件。");
    }
    const data = await this.rpc("workspaceFiles/readBytes", { ...args, options: {} }, fileStat.extend({
      offset: z.literal(0), eof: z.literal(true), dataBase64: z.string(),
    }), 35_000, true);
    const after = await this.rpc("workspaceFiles/stat", args, fileStat);
    if (before.absolutePath !== data.absolutePath || before.absolutePath !== after.absolutePath
        || before.version !== data.version || before.version !== after.version
        || before.bytes !== data.bytes || before.bytes !== after.bytes) {
      throw new ComputerError("file_changed", "电脑文件在传输期间发生了变化，请等待生成结束后重新获取。");
    }
    const current = this.paired();
    if (current.credential !== paired.credential || current.sessionId !== paired.sessionId) {
      throw new ComputerError("connection_changed", "电脑连接已移除或切换，本次文件不会保存。");
    }
    const file = await this.deps.files.save({
      name: parts[parts.length - 1], dataBase64: data.dataBase64, expectedBytes: data.bytes ?? null,
      sessionId: paired.sessionId, sourcePath: data.absolutePath, sourceVersion: data.version, receivedAt: this.deps.now(),
    });
    return { saved_to_phone: true, file, meaning: "文件已完整保存到本机成果；可在电脑连接界面预览、打开或分享。" };
  }

  private async rpc<T>(method: string, args: Record<string, Json>, schema: z.ZodType<T>, timeoutMs = 15_000, binary = false): Promise<T> {
    const paired = this.paired();
    const rpcId = this.deps.newId();
    const response = await this.request(paired.origin, `/api/${method}`, "POST", {
      type: "client-request", rpcId, method, payload: { args },
    }, paired.credential, timeoutMs, binary);
    const envelope = z.object({
      type: z.literal("server-response"), rpcId: z.string(),
      result: z.discriminatedUnion("ok", [
        z.object({ ok: z.literal(true), value: schema }),
        z.object({ ok: z.literal(false), error: z.object({ code: z.string(), message: z.string() }) }),
      ]),
    }).parse(this.parseJson(response.body));
    if (envelope.rpcId !== rpcId) throw new ComputerError("wrong_response", "电脑返回的响应与本次请求不匹配。");
    if (!envelope.result.ok) throw new ComputerError(envelope.result.error.code, `电脑接口未完成请求（${envelope.result.error.code}）。`);
    return envelope.result.value;
  }

  private post(origin: string, path: string, body: Record<string, Json>) {
    return this.request(origin, path, "POST", body);
  }

  private async request(origin: string, path: string, method: "GET" | "POST", body?: Record<string, Json>, credential?: string, timeoutMs = 15_000, binary = false): Promise<HttpResponse> {
    const headers: Record<string, string> = { Origin: origin, Accept: "application/json", "User-Agent": "Movness/0.1 Android" };
    if (body !== undefined) headers["Content-Type"] = "application/json";
    if (credential !== undefined) headers.Cookie = `dsh_rc=${credential}`;
    const response = await this.deps.transport.request({ method, url: origin + path, headers,
      body: body === undefined ? undefined : JSON.stringify(body), timeoutMs, binary });
    if (response.status === 403 && credential !== undefined) {
      const denial = z.object({ code: z.literal("unpaired") }).safeParse(this.parseJson(response.body));
      if (denial.success) {
        this.deps.store.write({ kind: "empty" });
        throw new ComputerError("authorization_revoked", "这台手机的电脑授权已失效，请重新配对。");
      }
    }
    if (response.status < 200 || response.status >= 300) throw new ComputerError(`http_${response.status}`, `电脑连接请求失败（HTTP ${response.status}）。`);
    return response;
  }

  private parseJson(body: string): Json {
    try { return z.json().parse(JSON.parse(body)); }
    catch { throw new ComputerError("invalid_response", "电脑返回了无法识别的响应，请核对插件与DSH版本。"); }
  }
}
