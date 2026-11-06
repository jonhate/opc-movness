# Movness 电脑协作 · r48

运行于 Movness r46 的增量 ToolPkg，不是新的 APK。先使用已经构建的 r46，导入 `Movness-r48-computer-results.toolpkg`；工具箱会注册“电脑连接”。电脑沿用现有 DSH 和 `@copylee/dsh-remote-control@0.2.9`。

首次：电脑生成二维码 → 手机拍摄识别 → 电脑批准 → 手机选择已有会话。选择会话时界面说明并授予八项电脑工具的常规调用权限；后续由模型直接调用，不需要逐步点击“分析／执行”。本轮未在实机验证 Android 加载、相机、加密存储或权限授予。

普通任务使用 `computer_submit` → `computer_wait`；`accepted` 仅指已接收，`reply_ready` 仅指收到电脑输出。文件、外部动作和目标完成仍需相应证据。停止电脑使用 `computer_cancel`，范围是选定的 DSH 会话。r46 首页的停止只停止手机当前轮，尚未联动电脑停止。

网络结果不确定时，`computer_recover_submission` 只核对队列与事件里的原请求编号，不会自动重发。实测发现 DSH 0.2.2-rc.1 的快速重试可在输入已取出、尚未落入 user/message 时重复入队，所以不能只依赖请求编号声称绝不重复。查不到接收记录时保持“提交结果待确认”，稍后再核对。

测试固定使用 Node 24.22.1、DSH 0.2.2-rc.1、remote-control 0.2.9。真实 DSH 进程负责写文件，模型输出是明确的本地 Anthropic SSE 协议夹具；不是实际模型推理，不是手机或 Windows 联调。云环境禁止枚举网卡，测试用 test/loopback-adapter.mjs 只注入回环适配器；没有修改上游插件，也没有验证实际局域网或公网隧道。

新增成果：`computer_fetch_file` 使用项目相对路径取回完整文件，传输前后检查版本；原始字节落盘后才返回 `saved_to_phone`。`computer_results` 可离线列出本机成果。界面中提供 Markdown／文本预览、其他应用打开和系统分享。已有 r47 配对数据保留；升级后在电脑连接中“选择会话与授权”，选择原会话以允许新增两项成果工具。

单文件上限5MiB；文件变化、字节数不符或本机保存失败时不发布“已保存”成果。同一来源文件的相同内容不重复列出；续改产生新内容时保留旧文件。成果保存于应用私有目录，通过 r46 已有 FileProvider 按次分享，不需要新增广泛存储权限。

开发目录维持 `examples/movness_computer` 和旁边 `examples/types`，可直接将源码包的 examples 合入 r46 恢复后的工程。构建与验证：

```text
npm ci --ignore-scripts
python -m venv .venv
.venv/bin/python scripts/verify.py --cache <绝对缓存目录>
.venv/bin/python scripts/package.py --output <绝对路径/Movness-r48-computer-results.toolpkg>
```

Windows 使用 `.venv/Scripts/python.exe`；需要含 jdk.compiler 的 JDK17，以及 java 可执行文件。verify.py 一次执行类型检查、原生构建及夹具、相关测试和打包脚本；原生编译依赖按SHA256下载。直接运行 npm test 时，需要先构建原生夹具，并设置 MOVNESS_NATIVE_TEST_CLASSPATH 为缓存内 runtime-classpath.txt 的内容；优先使用统一 verify.py 入口。

真实DSH联调包含两轮任务：实际write写出分镜、取回原始字节、第二轮实际read／write续改、分别保存两个版本，再撤销电脑授权并离线读取成果。模型协议端点和网卡枚举仍是明确的测试夹具；原生存储／multipart解析代码在JVM执行，Android包装层、手机Java回调和系统分享没有实机通过记录。

DSH 通过已授权 ADB 可执行 `python scripts/install_adb.py <绝对路径/Movness-r48-computer-results.toolpkg> --receipt <安装回执.json>`。脚本检查单台已授权设备、复制哈希和应用加载日志；广播发出不会直接算加载成功。脚本仅做帮助入口检查，尚未在设备执行；首次系统授权和电脑批准必须由本人完成。

完整结果与下一工作单见随包 HANDOFF.md、STATUS.json、relay/TASKS.md。没有启动24小时开发或外部任务。
