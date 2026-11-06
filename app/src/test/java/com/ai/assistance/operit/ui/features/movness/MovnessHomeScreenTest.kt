package com.ai.assistance.operit.ui.features.movness

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import android.graphics.Canvas
import androidx.compose.ui.text.input.TextFieldValue
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.InputProcessingState
import com.ai.assistance.operit.ui.features.chat.viewmodel.ChatViewModel
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Real Compose rendering with a fixture ViewModel; does not claim model/phone E2E coverage. */
@RunWith(MovnessUiTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w400dp-h850dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MovnessHomeScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val id = MutableStateFlow<String?>(null)
    private val draft = MutableStateFlow(TextFieldValue(""))
    private val errors = MutableStateFlow<String?>(null)
    private val vm = mock<ChatViewModel> {
        on { chatHistory } doReturn MutableStateFlow(emptyList<ChatMessage>())
        on { chatHistories } doReturn MutableStateFlow(emptyList<ChatHistory>())
        on { currentChatId } doReturn id
        on { currentChatIsLoading } doReturn MutableStateFlow(false)
        on { currentChatInputProcessingState } doReturn MutableStateFlow(InputProcessingState.Idle)
        on { userMessage } doReturn draft
        on { isConfigured } doReturn MutableStateFlow(true)
        on { isApiConfigInitialized } doReturn MutableStateFlow(true)
        on { errorMessage } doReturn errors
    }

    private fun show(onConfigure: () -> Unit = {}) {
        doAnswer { draft.value = it.getArgument(0); Unit }.whenever(vm).updateUserMessage(any())
        compose.setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                MovnessHomeScreen(vm, {}, onConfigure, {}, {})
            }
        }
    }

    @Test fun firstSendCreatesConversationAndSubmitsOnce() {
        doAnswer { id.value = "persisted-chat-46"; Unit }.whenever(vm).createNewChat(anyOrNull(), anyOrNull())
        show()
        compose.onNodeWithTag("movness.composer").performTextInput("整理刚才的笔记")
        compose.onNodeWithTag("movness.send").performClick()
        compose.waitForIdle()
        verify(vm, times(1)).createNewChat(null, null)
        verify(vm, times(1)).sendTextMessage(eq("整理刚才的笔记"), eq("persisted-chat-46"), any())
        assertEquals("", draft.value.text)
    }

    @Test fun modelSettingsIsAlwaysReachable() {
        var opened = 0
        show { opened++ }
        compose.onNodeWithContentDescription("模型设置").performClick()
        assertEquals(1, opened)
    }

    @Test fun renderHomeAndActualErrorState() {
        show()
        compose.onNodeWithText("Movness").assertIsDisplayed()
        capture("movness_r46_home.png")
        compose.runOnIdle { errors.value = "模型请求失败：HTTP 401，请在本机检查凭据。" }
        compose.onNodeWithText("需要处理").assertIsDisplayed()
        compose.onNodeWithText("模型请求失败：HTTP 401，请在本机检查凭据。").assertIsDisplayed()
        capture("movness_r46_error.png")
    }

    private fun capture(name: String) {
        val directory = System.getProperty("movness.capture") ?: return
        val file = File(directory, name)
        requireNotNull(file.parentFile).mkdirs()
        compose.runOnIdle {
            val root = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(bitmap))
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
