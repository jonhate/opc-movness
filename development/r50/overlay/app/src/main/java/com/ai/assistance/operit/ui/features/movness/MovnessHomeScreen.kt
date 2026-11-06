package com.ai.assistance.operit.ui.features.movness
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.viewinterop.AndroidView
import com.ai.assistance.operit.ui.features.chat.viewmodel.ChatViewModel
import io.movness.ui.MovnessView
import io.movness.ui.UpstreamBackend
/** Movness interaction; the existing ViewModel retains execution and persistence. */
@Composable
@Suppress("UNUSED_PARAMETER")
fun MovnessHomeScreen(viewModel: ChatViewModel, onOpenWorkspace: () -> Unit,
    onConfigureModel: () -> Unit, onConfigurePermissions: () -> Unit, onOpenPackages: () -> Unit) {
    AndroidView(factory = { context -> MovnessView(context, UpstreamBackend(viewModel),
        { onOpenPackages() }, { onConfigurePermissions() }) },
        modifier = Modifier.fillMaxSize(), update = { it.refresh() })
}
