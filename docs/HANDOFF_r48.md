# Movness r48 · 开发交接

2026-11-06。r48 是安装在 r46 宿主上的增量 ToolPkg，版本0.2.0、容器ID `io.movness.computer`。本轮没有新APK；保留 r46、r45 及已有配置，不卸载旧应用。

## 本轮实际改进

电脑完成工作后，Movness 新增 `computer_fetch_file` 取回成果文件，检查传输前后的版本与大小，再用生产 Java 保存代码将原始字节写入本机成果；落盘成功才返回 `saved_to_phone`。`computer_results` 可在电脑离线、授权已撤销时列出已保存成果。

成果页移到连接模块的前部：显示文档卡、“最近保存／历史版本”，支持 Markdown／文本预览、其他应用打开和系统分享。续改产生新内容时保留旧文件；同一来源文件的相同内容不重复列出，不会把不同来源的相同内容错误合并。新增两项工具后，共8项常规电脑工具。

复用 DSH 官方 workspaceFiles 的 stat/readBytes、多段二进制响应、r46 内已有 OkHttp MultipartReader、私有文件目录与 FileProvider。没有新增电脑 Worker、文件服务器或认证网关，没有发布新的 DSH 市场插件。

## 验证结果及证据

| 项目 | 实际结论 |
|---|---|
| 类型检查、原生编译、DEX与JS打包 | 通过；r46已有库提供运行时依赖，未重建大体积APK |
| 相关测试 | 15项通过，0失败、0跳过；另有生产URI／JPEG QR及文件保存JVM检查 |
| 第一轮电脑任务 | 启动真实 DSH0.2.2-rc.1 和原远程插件0.2.9，实际write写出《校园一小时》分镜文档，取回完整原始字节 |
| 下载与保存 | 真实workspaceFiles响应经生产Java multipart解析器和成果保存类处理；本地文件与电脑文件字节及SHA256一致；另测含NUL／非UTF-8字节的二进制种子文件 |
| 第二轮续改 | 真实DSH read读取原文档，write将操场段改成图书馆取书；新版本下载后，旧的本地文档保持不变。两轮合计5次明确的模型协议夹具调用 |
| 故障处理 | 版本变化、大小不符、超上限、保存失败及连接被移除时，不发布虚假的“已保存”成果；旧的断线接收记录恢复仍不自动重发prompt |
| 交互结构 | 调用实际Compose DSL函数，测试注册、结果卡、最新／历史标记、分别打开新旧Markdown、分享Intent构造及返回；包含Android桥夹具 |
| 审阅页 | 从实际DSL交互快照生成单文件HTML；检查了静态排版和审阅页切换逻辑。不是原生像素截图或手机验收 |

`examples/movness_computer/evidence/`：`tests.log`、`native-jvm.log`、`native-build.log`、`file-transfer.json`、`continuation.json`、`real-dsh.json`、`downloaded-storyboard-v1.md`、`downloaded-storyboard-v2.md`、`ui-compose-fixture.json`。样稿明确标注为协议测试，不是实际模型创作作品。

**没有通过的部分**：本环境没有手机ADB或用户Windows连接。真实模型推理、手机Context／Keystore／相机／Java回调、Android系统预览和分享、用户局域网与公网、运行中任务真正被中断，以及24小时运行，均没有本轮实机通过证据。网卡枚举使用了仅限测试的回环夹具；没有修改上游插件。

文件下载与本机成果模块已实现并完成上述集成验证；主聊天内的成果卡／直达入口尚未接入，当前入口仍在工具箱“电脑连接”。r46首页停止只停止手机当前轮，跨端停止联动、r45历史迁移也尚未实现。

## 安装与接手

1. 已有 r46：导入 `Movness-r48-computer-results.toolpkg` 更新同一容器，配对信息保留。打开“电脑连接”，通过“选择会话与授权”选回原会话，允许新增的两项成果工具；普通任务不逐步点击执行。
2. 已授权ADB可由DSH使用 `scripts/install_adb.py` 复制、验hash并调用现成debug receiver，等待应用加载成功日志。脚本尚未在设备执行；广播回执不能替代实际打开模块的验收。
3. 没有 r46 宿主时，使用此前的 `Movness-r46-operit-preview.apk`：包名 `io.movness.app.operit.preview`，versionCode46，SHA256 `0edc9364c2b2e1d26f4223e2e981fb197528aa52640e536507dcd320e63fe63f`。不以卸载旧版解决问题。

来源锁定：Operit `783ee301da88eaf197fb9f05ed3b70a607bf7408`，terminal `045868988e4091d613cb34b2941057703899b96e`，DSH0.2.2-rc.1，remote-control0.2.9。Android宿主及源码档的现有引用见 R46_BASE.json。原插件已有能力优先验证，不降级或覆盖用户活跃profile。

## 开发定位与复验

本包完整包含工具包增量与相邻SDK类型；已有r46工程时合入 `examples/`。需要重建APK时再恢复 r46 原源码档，不把本增量包误认为完整Android仓库。当前无需重建APK即可安装工具包。

| 修改内容 | 文件 |
|---|---|
| 电脑任务、状态、接收记录恢复与文件版本检查 | src/core.ts |
| 成果数据契约、大小限制 | src/results.ts |
| 连接、成果卡、历史版本与文档预览 | src/ui/connect.ui.ts |
| 8项模型工具、使用说明 | src/packages/computer.ts |
| Android桥与分享Intent | src/android.ts、native/NativeBridge.java |
| 二进制RPC解包、私有文件保存、校验与预览 | native/RpcFileResponse.java、native/ArtifactFiles.java |
| 同一入口的相关检查 | scripts/verify.py |

进入 `examples/movness_computer`，Node24.22.1、含jdk.compiler的JDK17：

```text
npm ci --ignore-scripts
python -m venv .venv
.venv/bin/python scripts/verify.py --cache <绝对缓存目录>
.venv/bin/python scripts/package.py --output <绝对路径/Movness-r48-computer-results.toolpkg>
```

Windows使用 `.venv/Scripts/python.exe`。verify.py准备生产JVM夹具后执行15项测试；直接npm test需要已准备的 MOVNESS_NATIVE_TEST_CLASSPATH，优先统一入口。原生依赖按SHA256下载，native jar只包含Movness自有适配类。许可证、准确来源与新增贡献见 THIRD_PARTY_NOTICES.md。

单文件上限5MiB，仍受DSH宿主自己的上限约束。手机侧计算SHA256留存；测试额外核对了电脑与本地hash，不能把它写成已实现通用的电脑侧密码学验签服务。文档预览最多显示24000字符，完整文件保留，可用其他应用打开。

## 下一项工作和24小时接力

**先做 M48-PHONE**：由用户Windows上的DSH检查已授权ADB、既有profile和模型配置，安装r48，实际打开模块，用已有模型执行一次生成与续改；核对手机文件、预览、历史版本及分享。接着做 M48-CHAT 的聊天成果入口；如需要重构首页，先交由Jon决定，不能擅自大改。

随后 M48-DEMO 沿用既定场景：一句话安排电脑生成分镜、手机打开相机、文档返回、用户续改。连续真实录屏，展示实际动作与成果；当前测试夹具不能代替参赛视频。文件下载是基础，评审呈现应突出跨设备接力如何连续完成一个目标。

`relay/`沿用既有Beads、DSH Goal、dsh-automation、Agent Bridge和结构化回执。DSH集成实机与电脑，已授权本地Codex CLI在独立工作树中改代码；每棵树一个写入者。网页聊天不常驻；本轮没有向外部发任务或启动24小时运行。先完成一次实际交接和进程中断恢复，再长测，记录run_id、有效产物、失败与恢复点。
