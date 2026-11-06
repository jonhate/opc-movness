# 构建和检查

适用于已授权开发者；用户只需把交接包交给 DSH，无须亲手逐条操作。

## 恢复

Python 3.12+，在本包根目录执行：

```text
python tools/prepare_source.py --verify-only
python tools/prepare_source.py --dest <空的工作目录>
```

脚本校验 SHA256 后恢复固定 Operit、固定 terminal 子模块、Movness 改动和三份厂商依赖，不包含 SDK、个人凭据或签名私钥。目标已有代码则拒绝覆盖。已开始开发的工作树从其最新提交继续，不重新恢复。

JDK 21、Android API 36、Build Tools 35.0.0、NDK 25.1.8937393、CMake 3.22.1；上游 wrapper 固定 Gradle 8.13 并包含 SHA256。先复用本机已有 SDK；缺什么补什么。新目录不要放在空间不足的 C 盘。首次构建会下载语音模型和 C++ 源码，查看磁盘余量后进行；使用两并发 worker，不同时跑两个 Gradle 构建。

在工作树的 local.properties 填写本机 `sdk.dir`，不提交到 git。Linux 使用 `./gradlew`，Windows 使用 `gradlew.bat`。Windows 原生构建尚未在本轮验证；需要主机 C++ 编译器的模块，应复用本机已有的官方工具链或 WSL，记录实际环境，不擅自删模块。

在当前 shell 设置 `OBX_DISABLE_ANALYTICS=true`，禁用 ObjectBox 可选构建统计，不绕过网络控制。

## 验证

下面 `<交接包>` 换为绝对路径，schema 和日志同样使用本地路径。先跑界面和状态相关检查，再打包：

```text
gradlew.bat --no-daemon --no-build-cache --max-workers=2 "-Dorg.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8" -I <交接包>/tools/movness-toolchain.init.gradle :app:testDebugUnitTest --tests com.ai.assistance.operit.ui.features.movness.*
gradlew.bat --no-daemon --no-build-cache --max-workers=2 "-Dorg.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8" -I <交接包>/tools/movness-toolchain.init.gradle :app:assembleDebug
```

界面检查使用真实 Compose 控件和 Robolectric 原生绘制，但 ViewModel 是测试夹具；不能声称它证明了真实模型推理、手机工具执行或 Windows 联动。该检查的原生图位于工作树相邻 evidence 目录；JUnit 报告位于 `app/build/test-results/testDebugUnitTest/`。

APK 位于 `app/build/outputs/apk/debug/`。用 SDK 的 apksigner 验签并记录 SHA256、包名、版本、签名指纹，再安装到授权设备。包名 `io.movness.app.operit.preview` 与旧版不同。旧 r45 继续保留；本轮没有迁移其历史、模型配置或成果，不要卸载它。

Gradle 默认生成本机 debug 签名。交付的预览 APK 会用已有 Movness 预览私钥重新签名，保持签名连续性；这份私钥已单独保存于既有 `Movness_r44_PRIVATE_preview_signing.zip`，不在公开源码包内。后续安装到用户设备的 APK 同样使用这份既有私钥，不要重新生成签名或卸载用户旧应用。开发者本机临时构建可保留本机 debug 签名，但不能拿它覆盖已交付预览版。正式参赛发行前再统一发行配置，不覆盖用户现有 Operit。

交付签名脚本为 `tools/sign_preview.py`；其参数为 `--apk`、`--out`、`--keystore`、`--apksigner`、`--aapt`、`--evidence`。两个密码仅通过本机环境变量 MOVNESS_PREVIEW_STORE_PASSWORD、MOVNESS_PREVIEW_KEY_PASSWORD 提供，不写入命令参数或公开文件。脚本核验签名指纹、包名、版本及启动器名称，再保存哈希与验签结果。

## 已排查的环境问题

- ObjectBox 5.3.0 的 BasicBuildTracker 支持环境变量 `OBX_DISABLE_ANALYTICS`；本轮已核实它的实现并在日志中看到 Analytics disabled。
- Python 普通解压 ZIP 会把 Linux NDK 的 symlink 解成文本，出现 `clang-14: not found`。使用官方 sdkmanager 安装 SDK/NDK；若使用归档，恢复 symlink 与执行权限。不要删功能或换一份不匹配的 so 来掩盖错误。
- Robolectric 的 Java SPI 与 SSHD 类加载冲突由专用测试 runner 隔离。Mockito 在 JDK 21 下需要测试进程显式加载 Byte Buddy agent；已写入本包构建 init 脚本，不涉及应用运行时。
- 目录 `legacy/` 只包含 r45 旧线源码与证据供查阅，不是新的 Android 编译入口。

本轮只记录了 Linux 上的真实构建。增量构建仍观察到 MNN 的 C++ 文件重新编译；后续若处理构建耗时，应先核对其配置阶段的生成文件时间戳，保留现有功能和真实日志。
