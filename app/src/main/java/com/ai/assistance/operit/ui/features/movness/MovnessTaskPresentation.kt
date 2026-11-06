package com.ai.assistance.operit.ui.features.movness

import com.ai.assistance.operit.data.model.InputProcessingState

/** Labels describe observed runtime state, never infer that a real-world action succeeded. */
data class MovnessTaskPresentation(val title: String, val detail: String, val working: Boolean) {
    companion object {
        fun from(state: InputProcessingState, loading: Boolean): MovnessTaskPresentation =
            when (state) {
                is InputProcessingState.Error -> MovnessTaskPresentation("需要处理", state.message, false)
                is InputProcessingState.ExecutingTool ->
                    MovnessTaskPresentation("正在行动", state.toolName, true)
                is InputProcessingState.ToolProgress ->
                    MovnessTaskPresentation("正在行动", state.message.ifBlank { state.toolName }, true)
                is InputProcessingState.ProcessingToolResult ->
                    MovnessTaskPresentation("正在检查返回结果", state.toolName, true)
                is InputProcessingState.Connecting ->
                    MovnessTaskPresentation("正在连接模型", state.message, true)
                is InputProcessingState.Receiving ->
                    MovnessTaskPresentation("正在回复", state.message, true)
                is InputProcessingState.Processing ->
                    MovnessTaskPresentation("正在处理", state.message, true)
                is InputProcessingState.ExecutingPlan ->
                    MovnessTaskPresentation("正在推进任务", state.message, true)
                is InputProcessingState.Summarizing ->
                    MovnessTaskPresentation("正在整理上下文", state.message, true)
                InputProcessingState.Completed ->
                    if (loading) MovnessTaskPresentation("正在收尾", "等待本轮执行结束", true)
                    else MovnessTaskPresentation("本轮回复已结束", "执行结果请查看下方记录", false)
                InputProcessingState.Idle ->
                    if (loading) MovnessTaskPresentation("任务已提交", "等待执行状态", true)
                    else MovnessTaskPresentation("随时可以开始", "说出目标，接着交给我", false)
            }
    }
}
