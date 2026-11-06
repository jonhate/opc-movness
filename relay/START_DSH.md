# 交给 DSH 的启动任务

下面整段可以直接作为已有 DSH Goal 的目标。先读本包中的 HANDOFF.md、STATUS.json、evidence，再开始工作。不要重新设计产品、另建任务调度器或反复询问已批准的 Operit 路线。

> 继续开发 Movness。主线是固定版本 Operit Android 二次开发，保留 Movness 的简洁交互和成果流程，电脑端优先复用成熟 DSH 远程插件。不要继续扩展天气卡或旧的逐步手动执行器。执行 tools/prepare_source.py 恢复代码，读取 STATUS.json 的实际验证状态；先完成仍未通过的构建和原生界面检查，再完成“首次配置模型后，一句话调用真实手机工具”的实机闭环。随后验证现有 DSH 版本与扫码远程插件的兼容性，做真实跨端任务与成果回传。复用已有 Goal、dsh-automation、Beads 和 Agent Bridge，先在现有允许目录里完成一次接单、执行、回执的通路测试。每个任务单独分支，只串行集成验收通过的改动；提交代码、命令退出码、必要截图/日志、文件哈希和下一任务。先自主排查同类错误，重复三次仍失败则记录阻塞并推进另一项已授权任务。重大产品路线变更、新付费服务、公开上架插件或部署服务、不可逆删除用户数据时停下，其余常规开发、构建、修错和验证连续推进。

## 启动顺序

1. 核对本包 SHA256SUMS.json；恢复源码到已有授权的项目目录。没有项目目录时使用既有 Agent Bridge 允许的 `D:\lj\dsh\apps\agent-bridge\work\chatgpt` 内的任务子目录，先核实实际白名单与目录存在，不修改权限白名单。
2. 恢复目录没有 git 元数据时，先按本机既有身份初始化本地仓库，提交恢复基线，再建立 `feat/movness-operit` 分支；基线提交与上游 SHA 分别记录，不擅自推送未核验远端。读取当前 DSH、Goal、dsh-automation、Beads 的版本、已有任务和运行状态。继续既有机制，不覆盖已有配置、不重复注册定时任务。
3. 若本机有已登录的 Codex CLI，在两个独立 git worktree 中分工：Codex 负责 Android 界面和模型/手机链路；DSH 负责 Windows、插件、Agent Bridge 和集成验收。同一工作树只能有一个写入者。
4. Codex CLI 未就绪时，DSH 直接推进上述任务，记录第二执行器尚未启动；不要把网页 ChatGPT 写成 24 小时后台服务。缺少账号交互时只报告确切所需动作，继续不依赖它的工作。
5. 从 TASKS.md 选第一个未完成任务，限定本轮验收范围。接单到 Beads，跨端状态按现有 Agent Bridge 合同上报。验收回执采用 result.schema.json 的字段。
6. 任务通过后由 DSH 集成并更新 STATUS.json、下一任务与构建产物；失败保留日志和可恢复分支。下一轮从最近验收提交继续，不能从旧压缩包覆盖新工程。

## Codex CLI 工作单模板

先检查 `codex --version`、`codex exec --help` 与本机登录状态。以下命令在独立工作树根目录执行，schema 和输出目录换为本包的实际绝对路径；由 DSH 填入具体任务，不要直接循环含糊的“继续优化”。

```text
codex exec --sandbox workspace-write --json --output-schema <本包/relay/result.schema.json> -o <本轮/result.json> "读取项目 AGENTS.md、HANDOFF.md 和当前任务。只完成 TASK_ID 指定工作，运行相关检查，输出证据与下一任务。"
```

stdout 写入本轮 events.jsonl；以进程退出码和结果文件联合判断，不把启动成功视为任务完成。当前 CLI 不使用旧的 `--full-auto` 参数。认证、权限、配额或网络阻塞要保留明确记录；不要启用跳过全部权限检查的参数。

参考：[OpenAI 非交互执行文档](https://developers.openai.com/codex/noninteractive)。

## 24 小时验证标准

实际完成一次启动、一次任务交接、一次进程中断恢复后，才能开始无人值守长测。记录开始/结束时间、真实 run_id、成功/失败/阻塞次数、恢复点、最后一次有效产物；“定时器已启动”不等于“24 小时已通过”。凭据、模型余额和系统休眠属于运行前检查，不使用新的付费资源。
