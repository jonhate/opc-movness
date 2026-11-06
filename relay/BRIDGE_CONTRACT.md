# Agent Bridge 接力约定

沿用 2026-11-02 已完成一次真实 E2E3 的通道：Beads 管本地任务，Agent Bridge 管跨 Agent 事件，JSONL 事件链为事实记录。那次成功不代表当前在线或 24 小时已验收。

已知 Windows 根目录是 `D:\lj\dsh\apps\agent-bridge`，本地 REST/MCP 端口 8788、工作界面 8789。历史任务事件入口为私有仓库 agent-bridge 的 issue #14，状态快照为 #10。启动时以本机当前合同和服务状态为准，不复制旧访问令牌，不假定旧 Cloudflare 域名可用。

本包不包含自动投递脚本，也没有投递任何外部任务。启用时由 DSH 使用既有工具生成符合当前合同的事件。顶层沿用 schema_version、event_id、type、actor、occurred_at、source、visibility、payload；payload 至少填写 title、goal、mode、approval、target_dir、needs、constraints、acceptance。写任务 target_dir 必须存在且真实路径位于允许目录；不省略，不擅自扩大白名单。

每次工作单使用稳定 task_id/event_id。重试不得产生另一份相同工作。分别记录 queued、started、completed、reviewed，并保留真实 run_id、退出码、产物字节数/哈希和本包 result.schema.json 所列验证。GitHub 事件创建成功只能标记“已投递”。

网页 ChatGPT 不会因为 GitHub 新评论自动醒来。真正连续的第二执行器需要本机已授权的 Codex CLI 或已验证可执行该任务的现有自动化；DSH 可用 Goal 持续推进，网页会话只在被调用时工作。先验证当前通路，再开始长测，不重复部署隧道。
