package com.ai.assistance.operit.ui.features.movness

import com.ai.assistance.operit.data.model.InputProcessingState
import org.junit.Assert.*
import org.junit.Test

class MovnessTaskPresentationTest {
    @Test fun aStartedToolDoesNotClaimCompletion() {
        val p = MovnessTaskPresentation.from(InputProcessingState.ExecutingTool("phone_agent"), true)
        assertEquals("正在行动", p.title)
        assertEquals("phone_agent", p.detail)
        assertTrue(p.working)
        assertFalse(p.title.contains("成功"))
    }
    @Test fun finalReplyDoesNotClaimRealWorldSuccess() {
        val p = MovnessTaskPresentation.from(InputProcessingState.Completed, false)
        assertEquals("本轮回复已结束", p.title)
        assertFalse(p.working)
        assertFalse(p.title.contains("任务完成"))
    }
    @Test fun completionStateWhileRuntimeIsBusyStillShowsWorking() {
        assertTrue(MovnessTaskPresentation.from(InputProcessingState.Completed, true).working)
    }
    @Test fun errorsRemainVisibleAndNeverSpinForever() {
        val p = MovnessTaskPresentation.from(InputProcessingState.Error("API 401"), true)
        assertEquals("API 401", p.detail)
        assertFalse(p.working)
    }
    @Test fun idleWithActiveRequestMeansSubmittedNotSucceeded() {
        val p = MovnessTaskPresentation.from(InputProcessingState.Idle, true)
        assertEquals("任务已提交", p.title)
        assertTrue(p.working)
    }
}
