# Movness r50 交接

2026-11-06；版本 0.5.2 / 50。先安装 Movness-r50.apk，覆盖已有 r46/r48/r49 预览版，不要先卸载。

包名 `io.movness.app.operit.preview`；APK 396724628 字节；SHA256 `9338aa3548d70e2443b133a90b33238cc3100213aa0026661bdbc7a71b975b25`。
沿用原证书 `ce3ca43c7ea2752dca11840c2e5a9f5c711da1656e0d805870d824ae5bb0c021`。r45 的 io.movness.app.context 是另一条旧线，不自动迁移。

## 本轮实际交付

- 换回 Movness 原生圆润输入框和紫色发送图标，保持 AI 对话为唯一主会话入口。键盘采用 adjustResize 和单一可视区域补偿，移除重复抬升。回复保留 Markdown、编号和可选择文字。
- 移除可见工作台切换；菜单只保留 AI 对话、对话记录、自动任务、示范学习、连接 DSH、设置。角色卡、世界书、请求头和高级模型参数隐藏，原实现及用户数据保留。
- 模型设置只显示协议、地址、密钥、模型；写入当前实际绑定的模型配置，未变更协议时保留原 providerId、请求头、参数和绑定，不重建用户配置。
- 顶部始终有连接 DSH，直达原配对与电脑成果模块；原 r48 工具包 0.2.0 的 8 项能力及配对数据路径保留。没有要求用户手工导入新包。
- 加入首次引导，手机系统授权、连续执行、模型与可选电脑连接集中呈现。Android 的首次系统授权仍需用户完成；不会伪装成已经授予。
- 对话记录与自动任务分开：后者读取原 WorkflowRepository 的真实时间／条件触发任务，不再把聊天列表叫任务，也不增加逐步手动执行按钮。创建继续用底座 workflow 工具。
- 示范学习已实现事件录制 → 本机保存 → 精确控件回放 → 状态／停止原因。支持点击、长按、非密码文字和滚动；默认不录制，用户主动开始。重名控件、目标缺失、应用不接受或服务中断会停止。它是操作示范复用，不是模型训练，也不承诺适配任意应用。
- 内置 movness_phone 0.1.0，提供 phone_apps / phone_launch / phone_screen / phone_act，以及 learning_list / learning_run / learning_status / learning_cancel。Agent 可调用已保存示范；收到 running 后必须查询实际结果，动作被接受不等于业务完成。
- 用户协议按新导航和真实数据流改写；保留 Operit、终端与 DSH 的真实署名。CommonMark 0.24.0 原始 BSD 许可与完整 GNU 文本随包提供。

## 已验证与未验证

已通过：9 项 Robolectric 原生交互测试；8 个 JS 工具出口、原生参数路由与错误返回；实际宿主 14 项直接方法引用和 ViewModel／配置接口审计；APK v2/v3、原证书、对齐和 CRC；原有资源 ID、124047 个原类、40 个未修改 DEX、原生库、模型及电脑工具包字节保留。

测试覆盖发送一次、菜单返回与草稿、设置简化、独立自动任务列表、首次引导、键盘视口缩小、无障碍事件保存／回放、密码过滤和歧义停止。evidence/ui 的图片是实际原生 View 在测试环境的渲染，使用固定消息和后端夹具；不是手机截图、真实模型输出或真实输入法截图。

未验证：真机安装启动、用户现有模型服务、真实输入法、系统无障碍授权及第三方 App、相机扫码、用户 Windows／LAN 闭环、定时后台长期运行。本轮没有连接用户手机或 Windows。构建是 Java17 + D8 + 原 APK 的 Apktool 3.0.3 重组，不是完整 Gradle 重建。

未完成：DSH 插件市场接入；主聊天内直接展示电脑成果；24 小时无人值守服务。文件结果仍在连接 DSH 的成果列表。位置／日程主动服务不是本版新增能力。

## 文件和继续开发位置

| 内容 | 包内位置 |
|---|---|
| 最新可编辑源码 | r50-simple/src/io/movness/ui/ |
| 完整 Gradle 修改覆盖层 | r50-simple/overlay/ |
| 精确 APK 补丁与哈希 | r50-simple/apk-patch/、apk-patch-manifest.json |
| 界面／发送／菜单／键盘 | MovnessView.java |
| 实际模型、会话、权限与任务适配 | UpstreamBackend.java |
| 原导航与 DSH 入口 | MovnessBridge.java；overlay 的 OperitScreens.kt |
| 示范存储与原生动作 | LearningStore.java、LearningService.java、PhoneActions.java |
| 手机工具源码、成品 | phone-package/ |
| 运行证据和截图 | r50-simple/evidence/ |
| 可恢复上游、旧覆盖层与依赖源码 | baseline/ 下 r46、r48、r49 三个原始源码交接包 |

恢复完整源码：在独立目录使用 r46 包的 prepare_source.py 恢复固定上游，再按 r48 → r49 → r50 顺序应用对应覆盖层与工具包。不要覆盖现有修改。Operit 固定 783ee301da88eaf197fb9f05ed3b70a607bf7408；terminal 固定 045868988e4091d613cb34b2941057703899b96e。完整 Gradle 路线仍需已有 JDK21、API36、NDK25.1/CMake3.22 环境，本轮没有验证该路线。

快速复原本次 APK：使用 SHA256 为 3fe5c3fcc61927772606d80c690f995ec19801ce39f125aa0cea6df0ed1c773c 的 r49 APK，Apktool 3.0.3 --no-src 解码到全新目录；运行 apply_apk_patch.py --base 原APK --decoded 新目录；使用 Apktool 构建，zipalign4，再用原私有签名材料签名。补丁包含重新组装的完整四个 DEX 组和新 classes45.dex；不能同时留下对应原始 classes18/20/22/29.dex。不生成新密钥。私钥不在交接包中。

build.py / run_ui_tests.py / sign_verify.py 是本轮实际运行脚本，保留了当前工作区的 r48/r49 依赖路径，换环境需映射这些路径。tools/ 的来源清单记录官方依赖 URL 与哈希。旧 MovnessHomeScreenTest.kt 针对已移除 Compose 语义节点，应从继承工程移除；本轮的测试入口是 tests/io/movness/ui/NativeUiTest.java 和 tests/phone-tools.cjs，未声称旧 Gradle 全测试通过。

## DSH 接力方案

下一张任务：M50-PHONE。先用已有获授权 ADB 和当前模型配置覆盖安装 r50；验证模型／聊天／配对仍在、键盘打开收起及返回草稿。再在一个无破坏性的真实 App 中示范、保存、回放，核对实际界面与每次 runId。最后完成一句目标 → 原 DSH 会话执行 → 文件取回 → 手机查看 → 续改留旧版，提交真实录屏、文件哈希、日志和准确状态。遇到问题在既定路线内修复并复验，不能拿测试夹具当实机验收。

持续开发沿用已有 Git、DSH Goal／Agent Bridge，而不是靠网页对话常驻。每轮只认源码提交、已签 APK、运行证据和下一任务；同一文件只允许一个写入者，另一侧负责独立验证。Windows 侧执行器需要当地已授权进程运行，本轮没有替用户启动或承诺跨会话 24 小时后台接力。已准备好交接，不等于调度器已运行。

评审上下一步最值得补的是一段不中断的真实跨端演示，并把最终文件直接带回对话。用一个具体创作目标显示手机获取信息、电脑产出、回到手机查看和自然语言续改，证据比继续添加配置入口更有价值。
