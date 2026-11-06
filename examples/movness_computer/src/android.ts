import { z } from "zod";
import { Artifact } from "./results";
import type { JavaBridgeArg } from "../../types/java-bridge";
import { ComputerClient, ComputerError, StoredState, type HttpRequest, type HttpResponse } from "./core";

const HttpResult = z.object({ status: z.number(), headers: z.record(z.string(), z.string()), body: z.string() });
const PairTarget = z.object({ origin: z.string(), token: z.string() });
const NATIVE_CLASS = "io.movness.computer.NativeBridge";
let bridgePromise: Promise<JavaBridgeClass> | undefined;
let clientPromise: Promise<ComputerClient> | undefined;

async function nativeBridge(): Promise<JavaBridgeClass> {
  if (bridgePromise === undefined) {
    bridgePromise = (async () => {
      const file = await ToolPkg.readResource("android_bridge", "movness-computer-native-r48.jar", true);
      Java.loadJar(file, { childFirstPrefixes: ["io.movness.computer."] });
      return Java.type(NATIVE_CLASS);
    })().catch(error => { bridgePromise = undefined; throw error; });
  }
  return bridgePromise;
}

function nativeHttp(bridge: JavaBridgeClass, request: HttpRequest): Promise<HttpResponse> {
  return new Promise((resolve, reject) => {
    const listener = Java.implement(`${NATIVE_CLASS}$HttpResult`, {
      response(value: JavaBridgeArg) {
        if (typeof value !== "string") {
          reject(new ComputerError("native_response", "网络组件返回了无法识别的响应。"));
          return;
        }
        try { resolve(HttpResult.parse(JSON.parse(value))); }
        catch { reject(new ComputerError("native_response", "网络组件返回了无法识别的响应。")); }
      },
      failure() { reject(new ComputerError("network_error", "暂时无法连接电脑，已提交的任务不会自动重发。")); },
    });
    bridge.callStatic("request", JSON.stringify(request), listener);
  });
}

export async function getClient(): Promise<ComputerClient> {
  if (clientPromise === undefined) {
    clientPromise = (async () => {
      const bridge = await nativeBridge();
      const context = Java.getApplicationContext();
      const timeZone = Java.callStatic<JavaBridgeInstance>("java.util.TimeZone", "getDefault").call<string>("getID");
      return new ComputerClient({
        transport: { request: request => nativeHttp(bridge, request) },
        files: {
          async save(input) { return Artifact.parse(JSON.parse(bridge.callStatic<string>("saveArtifact", context, JSON.stringify(input)))); },
          async list() { return z.array(Artifact).parse(JSON.parse(bridge.callStatic<string>("listArtifacts", context))); },
        },
        store: {
          read() {
            try {
              const raw = bridge.callStatic<string>("readState", context);
              return StoredState.parse(JSON.parse(raw));
            } catch (error) {
              console.error("Movness connection storage read failed", error instanceof Error ? error.name : typeof error);
              throw new ComputerError("storage_error", "连接信息无法读取，请移除本机连接后重新配对。");
            }
          },
          write(state) { bridge.callStatic("writeState", context, JSON.stringify(state)); },
        },
        newId: () => Java.callStatic<JavaBridgeInstance>("java.util.UUID", "randomUUID").call<string>("toString"),
        now: () => Date.now(), timeZone,
        parsePairCode: text => {
          try { return PairTarget.parse(JSON.parse(bridge.callStatic<string>("parsePairCode", text))); }
          catch (error) {
            console.error("Movness QR link rejected", error instanceof Error ? error.name : typeof error);
            throw new ComputerError("invalid_qr", "请识别 DSH 远程控制插件生成的配对二维码。");
          }
        },
      });
    })().catch(error => { clientPromise = undefined; throw error; });
  }
  return clientPromise;
}

export async function decodeQrFile(path: string): Promise<string> {
  const bridge = await nativeBridge();
  try { return bridge.callStatic<string>("decodeQr", path); }
  catch (error) {
    console.error("Movness QR decode failed", error instanceof Error ? error.name : typeof error);
    throw new ComputerError("qr_not_found", "未识别到二维码，请对准电脑上的配对二维码拍摄。");
  }
}

export function deviceLabel(): string {
  const model = Java.type("android.os.Build").getStatic<string>("MODEL");
  return `Movness · ${model}`;
}

export async function resetLocalConnection() {
  const bridge = await nativeBridge();
  bridge.callStatic("resetState", Java.getApplicationContext());
}

export function publicError(error: unknown): { code: string; message: string } {
  console.error("Movness computer operation failed", error instanceof Error ? error.name : typeof error);
  if (error instanceof ComputerError) return { code: error.code, message: error.message };
  if (error instanceof z.ZodError) return { code: "protocol_mismatch", message: "电脑接口与当前工具包不匹配，请核对 DSH 和远程插件版本。" };
  return { code: "component_error", message: "电脑协作组件未完成操作，请查看运行记录。" };
}

export async function previewResult(id: string) {
  const bridge = await nativeBridge();
  return z.object({ file: Artifact, text: z.string(), truncated: z.boolean() }).parse(
    JSON.parse(bridge.callStatic<string>("previewArtifact", Java.getApplicationContext(), id)));
}

export async function openResult(id: string, share: boolean): Promise<void> {
  const bridge = await nativeBridge();
  const context = Java.getApplicationContext();
  const file = Artifact.parse(JSON.parse(bridge.callStatic<string>("artifactInfo", context, id)));
  const applicationId = context.call<string>("getPackageName");
  const uri = Java.callStatic<JavaBridgeInstance>("androidx.core.content.FileProvider", "getUriForFile",
    context, `${applicationId}.fileprovider`, Java.newInstance("java.io.File", file.phonePath));
  const Intent = Java.type("android.content.Intent");
  const read = Intent.getStatic<number>("FLAG_GRANT_READ_URI_PERMISSION");
  const newTask = Intent.getStatic<number>("FLAG_ACTIVITY_NEW_TASK");
  const intent = Intent.newInstance();
  intent.call("addFlags", read);
  const clip = Java.callStatic<JavaBridgeInstance>("android.content.ClipData", "newRawUri", "Movness", uri);
  intent.call("setClipData", clip);
  if (share) {
    intent.call("setAction", "android.intent.action.SEND");
    intent.call("setType", file.mime);
    intent.call("putExtra", "android.intent.extra.STREAM", uri);
    const chooser = Intent.callStatic<JavaBridgeInstance>("createChooser", intent, "分享成果");
    chooser.call("addFlags", newTask | read);
    context.call("startActivity", chooser);
  } else {
    intent.call("setAction", "android.intent.action.VIEW");
    intent.call("setDataAndType", uri, file.mime);
    intent.call("addFlags", newTask);
    context.call("startActivity", intent);
  }
}
