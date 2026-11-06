# 来源与贡献

Movness 电脑工具包：在 Operit ToolPkg SDK 上增加 Movness 的连接界面、DSH HTTP RPC 任务适配、状态解释与断线接收记录核对。项目代码按 LGPL-3.0-only 发布，完整许可证见 LICENSE。未宣称独立实现 Android Agent、电脑 Agent 或远程认证系统。

| 依赖 | 固定版本／来源 | 许可证及用途 |
|---|---|---|
| Operit | 783ee301da88eaf197fb9f05ed3b70a607bf7408 | LGPL-3.0-only；Android Agent、ToolPkg、原生 Compose DSL、Java 桥与工具权限。SDK 类型随源码包保留 |
| DeepSeek Harness | @deepseek-ai/dsh 0.2.2-rc.1 | MIT；电脑会话与实际工具执行。工具包调用其官方 HTTP RPC；不携带电脑模型密钥 |
| DSH Remote Control | @copylee/dsh-remote-control 0.2.9；98c54302a4cdee0b09622ed1888b5a4491fc8cb6 | MIT；二维码、电脑首次批准、设备凭据、撤销和远程入口。直接复用原插件，未发布新的 DSH 市场插件 |
| Zod | 4.6.7 | MIT；已打包到 JavaScript 的协议校验代码；许可证见 licenses/zod-LICENSE |
| ZXing | 3.5.3 | Apache-2.0；复用 r46 APK 已有二维码解码库，未再次打包到 native jar |
| AndroidX Security Crypto | 1.1.0-alpha06 | Apache-2.0；复用 r46 的 Keystore / EncryptedSharedPreferences |
| OkHttp / Okio | 4.12.0 / 3.6.0 | Apache-2.0；复用 r46 APK 已有网络库 |
| esbuild | 0.28.2 | MIT；构建工具，不进入手机运行时 |
| R8 D8 | 8.7.18 | BSD-3-Clause；DEX 编译工具，不进入手机运行时 |

NativeBridge.java、PairingUri.java、QrDecoder.java、RpcFileResponse.java、ArtifactFiles.java 是 Movness 平台适配代码；native jar 只包含 io.movness.computer 自有类，没有重新分发上表运行时库。编译依赖的精确下载地址、大小与 SHA256 见 native/dependencies.json。Android 4.1.1.4 Maven jar 仅用作编译符号；实际 DEX 最低 API 为26，目标宿主是 r46 的现代 Android 应用，不代表支持 Android 4。

上游：[Operit](https://github.com/AAswordman/Operit)、[DeepSeek Harness](https://github.com/deepseek-ai/deepseek-harness)、[DSH Remote Control](https://github.com/copylee711/dsh-remote-control)。本次发现的快速重复投递竞态仅在本地留下复现证据，未代用户向上游发消息或提交 Issue。

DSH workspaceFiles/readBytes 的 multipart响应使用 r46 已有 OkHttp MultipartReader 解析，不增加新的文件服务器或传输协议；系统分享复用上游 FileProvider。HTML审阅页由实际Compose DSL交互夹具输出转换，用于结构与排版检查，不代表原生像素渲染或实机验收。

审阅页内嵌 Noto Sans CJK 的 WOFF2 子集，内部字体名已改为 Movness Preview Sans，SIL OFL1.1 许可证随页及 licenses/preview-font-OFL.txt 保留；字体来源与 SHA256 见 assets/PROVENANCE.json。只用于审阅页，未修改手机运行时字体。
