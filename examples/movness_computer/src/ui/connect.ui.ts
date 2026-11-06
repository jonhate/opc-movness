import type { ComposeDslContext, ComposeNode } from "../../../types/compose-dsl";
import { decodeQrFile, deviceLabel, getClient, openResult, previewResult, publicError, resetLocalConnection } from "../android";
import type { Artifact } from "../results";
import type { ComputerClient, SessionRow, TaskStatus } from "../core";

const PACKAGE = "movness_computer";
const ORDINARY_TOOLS = ["computer_status", "computer_sessions", "computer_submit", "computer_recover_submission", "computer_wait", "computer_cancel", "computer_fetch_file", "computer_results"];
const labels: Record<TaskStatus, string> = {
  submitting: "提交结果待确认", accepted: "电脑已接收", running: "电脑正在处理", needs_input: "电脑需要你确认",
  reply_ready: "电脑回复已到达", no_reply: "本轮尚未收到电脑回复", cancel_requested: "已发出停止请求", stopped: "电脑已停止",
};
type Info = ReturnType<ComputerClient["publicState"]>;
interface View { busy: boolean; info: Info; sessions: SessionRow[]; error: string; notice: string }

async function toolPermissions(ctx: ComposeDslContext, level: "ALLOW" | "ASK") {
  if (!ctx.resolveToolName || !ctx.importPackage || !ctx.usePackage || !ctx.isPackageImported) {
    throw new Error("当前应用未提供电脑工具包所需的接口。");
  }
  await ctx.importPackage(PACKAGE);
  if (!(await ctx.isPackageImported(PACKAGE))) throw new Error("电脑工具尚未导入。");
  await ctx.usePackage(PACKAGE);
  const values = Java.type("java.util.HashMap").newInstance();
  const permission = Java.type("com.ai.assistance.operit.ui.permissions.PermissionLevel").getStatic<JavaBridgeInstance>(level);
  for (const toolName of ORDINARY_TOOLS) {
    const name = await ctx.resolveToolName({ packageName: PACKAGE, toolName, preferImported: true });
    if (typeof name !== "string" || name.length === 0) throw new Error("电脑工具注册尚未完成。");
    values.call("put", name, permission);
  }
  const companion = Java.type("com.ai.assistance.operit.ui.permissions.ToolPermissionSystem").getStatic<JavaBridgeInstance>("Companion");
  const system = companion.call<JavaBridgeInstance>("getInstance", Java.getApplicationContext());
  await system.callSuspend("saveToolPermissions", values);
}

function title(session: SessionRow): string {
  const value = session.projections?.title;
  if (typeof value === "string" && value.trim().length > 0) return value;
  if (session.cwd !== undefined && session.cwd.length > 0) {
    const parts = session.cwd.split(/[\\/]/).filter(part => part.length > 0);
    if (parts.length > 0) return parts[parts.length - 1];
  }
  return `会话 ${session.sessionId.slice(0, 8)}`;
}

export default function Screen(ctx: ComposeDslContext): ComposeNode {
  const UI = ctx.UI;
  const [view, setView] = ctx.useState<View>("computer-view", {
    busy: false, info: { kind: "empty" }, sessions: [], error: "", notice: "",
  });
  const current = ctx.useRef<View>("computer-current", view);
  current.current = view;
  const publish = (next: View) => { current.current = next; setView(next); };
  const patch = (next: Partial<View>) => publish({ ...current.current, ...next });
  const [results, setResults] = ctx.useState<Artifact[]>("computer-results", []);
  const [preview, setPreview] = ctx.useState<{ file: Artifact; text: string; truncated: boolean } | null>("computer-preview", null);
  const [choosing, setChoosing] = ctx.useState("computer-choosing-session", false);
  const boot = ctx.useRef("computer-loaded", false);
  const polling = ctx.useRef("computer-pair-poll", false);

  const refresh = async () => {
    const client = await getClient();
    setResults(await client.results());
    const info = client.publicState();
    if (info.kind === "paired") {
      const sessions = await client.sessions();
      publish({ busy: false, info: client.publicState(), sessions, error: "", notice: "电脑已连接" });
    } else {
      publish({ busy: info.kind === "pairing", info, sessions: [], error: "", notice: "" });
      if (info.kind === "pairing") await pollPairing();
    }
  };
  const failed = async (error: unknown) => {
    const visible = publicError(error);
    try {
      const info = (await getClient()).publicState();
      patch({ busy: false, info, error: visible.message, ...(info.kind === "empty" ? { sessions: [] } : {}) });
    } catch { patch({ busy: false, error: visible.message }); }
  };
  const pollPairing = async () => {
    if (polling.current) return;
    polling.current = true;
    try {
      const client = await getClient();
      while (client.publicState().kind === "pairing") {
        const state = await client.claimPairing();
        if (state === "approved") { await refresh(); return; }
        if (state === "rejected") {
          publish({ busy: false, info: { kind: "empty" }, sessions: [], error: "电脑未允许这次连接。", notice: "" });
          return;
        }
        await new Promise<void>(resolve => setTimeout(resolve, 1_200));
      }
    } finally { polling.current = false; }
  };
  const scan = async () => {
    patch({ busy: true, error: "", notice: "对准电脑上的配对二维码" });
    try {
      const picked = await ctx.openFilePicker({ picker: "camera", allowMultiple: false });
      if (picked.cancelled) { await refresh(); return; }
      const file = picked.files[0];
      if (!file || file.path === undefined) throw new Error("相机未返回可读取的图片。");
      const qr = await decodeQrFile(file.path);
      const client = await getClient();
      await client.beginPairing(qr, deviceLabel());
      publish({ busy: true, info: client.publicState(), sessions: [], error: "", notice: "请在电脑上允许这台手机连接" });
      await pollPairing();
    } catch (error) { await failed(error); }
  };
  const select = async (session: SessionRow) => {
    patch({ busy: true, error: "" });
    try {
      const client = await getClient();
      await client.bindSession(session.sessionId);
      await toolPermissions(ctx, "ALLOW");
      setChoosing(false);
      await refresh();
    } catch (error) { await failed(error); }
  };
  const stop = async () => {
    try {
      const client = await getClient();
      await client.cancel();
      patch({ busy: true, info: client.publicState(), notice: "已发出停止请求", error: "" });
      await client.waitForReply();
      await refresh();
    } catch (error) { await failed(error); }
  };
  const disconnect = async () => {
    try {
      await resetLocalConnection();
      publish({ busy: false, info: { kind: "empty" }, sessions: [], error: "", notice: "已移除本机连接" });
      await toolPermissions(ctx, "ASK");
    } catch (error) { await failed(error); }
  };

  const content: ComposeNode[] = [
    UI.Text({ text: "电脑", style: "headlineMedium" }),
    ...(results.length === 0 ? [UI.Text({ text: "让电脑接着做。", style: "titleLarge" })] : []),
  ];
  if (results.length > 0) {
    content.push(UI.Text({ text: "成果", style: "titleLarge" }));
    content.push(UI.Text({ text: "已经带回手机，离线也能查看。", style: "bodySmall" }));
    for (const file of results.slice(0, 12)) {
      const versions = results.filter(item => item.sessionId === file.sessionId && item.sourcePath === file.sourcePath);
      const versionLabel = versions.length > 1 ? (versions[0].id === file.id ? "最近保存" : "历史版本") : "已保存在手机";
      const textFile = file.mime.startsWith("text/") || file.mime === "application/json";
      const action = async (share: boolean) => { try { await openResult(file.id, share); } catch (error) { await failed(error); } };
      content.push(UI.Card({ fillMaxWidth: true, shape: { type: "rounded", cornerRadius: 22 }, elevation: 0 }, [
        UI.Column({ padding: 20, spacing: 8 }, [
          UI.Text({ text: file.name, style: "titleMedium", maxLines: 2 }),
          UI.Text({ text: `${file.bytes < 1024 ? `${file.bytes} B` : `${Math.ceil(file.bytes / 1024)} KB`} · ${versionLabel}`, style: "bodySmall" }),
          UI.Row({ spacing: 12 }, [
            UI.TextButton({ onClick: async () => { try { if (textFile) setPreview(await previewResult(file.id)); else await action(false); } catch (error) { await failed(error); } } }, [UI.Text({ text: textFile ? "预览" : "打开" })]),
            UI.TextButton({ onClick: () => action(true) }, [UI.Text({ text: "分享" })]),
          ]),
        ]),
      ]));
    }
  }
  if (view.info.kind === "empty") {
    content.push(UI.Text({ text: "拍摄 DSH 的配对二维码，在电脑上允许一次连接。", style: "bodyMedium" }));
    content.push(UI.Button({ text: "扫码连接", enabled: !view.busy, onClick: scan, shape: { type: "pill" } }));
  } else if (view.info.kind === "pairing") {
    content.push(UI.CircularProgressIndicator({}));
    content.push(UI.Text({ text: "请在电脑上允许这台手机连接", style: "titleMedium" }));
  } else {
    const selected = view.info.sessionId;
    content.push(UI.Text({ text: selected ? "已准备好协作" : "选择要继续的电脑会话", style: "titleMedium" }));
    content.push(UI.Text({ text: "选择后，Movness 可在这项会话中自动发起常规任务。", style: "bodySmall" }));
    if (selected && !choosing) {
      const session = view.sessions.find(item => item.sessionId === selected);
      content.push(UI.Text({ text: session ? title(session) : "已绑定的电脑会话", style: "bodyMedium" }));
      content.push(UI.TextButton({ onClick: () => setChoosing(true) }, [UI.Text({ text: "选择会话与授权" })]));
    }
    for (const session of (selected && !choosing ? [] : view.sessions.slice(0, 12))) {
      content.push(UI.Button({
        text: `${selected === session.sessionId ? "✓ " : ""}${title(session)}`,
        enabled: !view.busy, onClick: () => select(session), fillMaxWidth: true,
        shape: { type: "rounded", cornerRadius: 18 },
      }));
    }
    if (view.sessions.length === 0) content.push(UI.Text({ text: "电脑上还没有可继续的会话，请先在 DSH 建立一项工作。", style: "bodyMedium" }));
    if (selected) content.push(UI.Text({ text: "回到 Movness 首页，直接说出你的目标。", style: "bodyMedium" }));
    const task = view.info.lastTask;
    if (task) {
      content.push(UI.Text({ text: labels[task.status], style: "titleMedium" }));
      if (["accepted", "running", "needs_input", "cancel_requested"].includes(task.status)) {
        content.push(UI.Button({ text: "停止此会话", onClick: stop, enabled: !view.busy }));
      }
    }
  }
  if (preview) {
    return UI.LazyColumn({ key: "movness-result-preview", fillMaxSize: true, padding: 24, spacing: 18 }, [
      UI.TextButton({ onClick: () => setPreview(null) }, [UI.Text({ text: "返回成果" })]),
      UI.Text({ text: preview.file.name, style: "headlineSmall" }),
      UI.Row({ spacing: 12 }, [
        UI.TextButton({ onClick: async () => { try { await openResult(preview.file.id, false); } catch (error) { await failed(error); } } }, [UI.Text({ text: "其他应用打开" })]),
        UI.TextButton({ onClick: async () => { try { await openResult(preview.file.id, true); } catch (error) { await failed(error); } } }, [UI.Text({ text: "分享" })]),
      ]),
      preview.file.mime === "text/markdown" ? UI.Markdown({ text: preview.text, enableDialogs: false }) : UI.Text({ text: preview.text, style: "bodyMedium" }),
      ...(preview.truncated ? [UI.Text({ text: "这里只展示部分内容，完整文件已经保存，可用其他应用打开。", style: "bodySmall" })] : []),
      ...(view.error ? [UI.Text({ text: view.error, color: ctx.MaterialTheme.colorScheme.error })] : []),
    ]);
  }
  if (view.error) content.push(UI.Text({ text: view.error, style: "bodyMedium", color: ctx.MaterialTheme.colorScheme.error }));
  if (view.notice && view.info.kind !== "pairing") content.push(UI.Text({ text: view.notice, style: "bodySmall" }));
  content.push(UI.TextButton({ enabled: !view.busy, onClick: async () => { try { await refresh(); } catch (error) { await failed(error); } } }, [UI.Text({ text: "检查连接" })]));
  content.push(UI.TextButton({ onClick: disconnect }, [UI.Text({ text: "移除本机连接" })]));

  return UI.LazyColumn({
    key: "movness-computer-connect", fillMaxSize: true, padding: 24, spacing: 16,
    onLoad: async () => {
      if (boot.current) return;
      boot.current = true;
      try { await refresh(); } catch (error) { await failed(error); }
    },
  }, content);
}
