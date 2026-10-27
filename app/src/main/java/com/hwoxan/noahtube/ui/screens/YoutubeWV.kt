package com.hwoxan.noahtube.ui.screens

import android.app.Activity
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.multiplatform.webview.web.LoadingState
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.rememberWebViewNavigator
import com.multiplatform.webview.web.rememberWebViewState
import com.hwoxan.noahtube.R
import com.hwoxan.noahtube.ui.YoutubeVM
import com.hwoxan.noahtube.ui.components.UpdateDialog
import com.hwoxan.noahtube.utils.ExitBridge
import com.hwoxan.noahtube.utils.NetworkBridge
import com.hwoxan.noahtube.utils.fetchScripts
import com.hwoxan.noahtube.utils.getUpdate
import com.hwoxan.noahtube.utils.permHandler
import com.hwoxan.noahtube.utils.readRaw

@Composable
fun YoutubeWV(youtubeVM: YoutubeVM = viewModel()) {

    val context = LocalContext.current
    val activity = context as Activity

    val state = rememberWebViewState("https://www.youtube.com/tv")
    val navigator = rememberWebViewNavigator()

    val jsScript = youtubeVM.scriptData
    val updateData = youtubeVM.updateData

    val loadingState = state.loadingState
    val exitTrigger = remember { mutableStateOf(false) }

    // Translate native back-presses to 'escape' button press
    BackHandler {
        if (state.loadingState is LoadingState.Finished) {
            // Mapeia o botão voltar para uma ação de 'voltar' no player do YouTube
            navigator.evaluateJavaScript("window.history.back();")
        } else {
            exitTrigger.value = true
        }
    }

    // Fetch scripts and updates at launch
    LaunchedEffect(Unit) {
        youtubeVM.setScript(fetchScripts() )
        getUpdate(context, navigator) { update ->
            if (update != null) youtubeVM.setUpdate(update)
        }
    }

    if (loadingState == LoadingState.Finished && jsScript != null)
        navigator.evaluateJavaScript(jsScript)
    // If any update found, show the dialog.
    if (updateData != null) UpdateDialog(updateData, navigator)
    
    // CSS extra para limpeza de interface
    LaunchedEffect(loadingState) {
        if (loadingState is LoadingState.Finished) {
            val hideUI = "document.head.appendChild(document.createElement('style')).innerHTML = '.promoted-sparkles-text-search-item, .ytd-masthead, .ytd-guide-renderer { display: none !important; }';"
            navigator.evaluateJavaScript(hideUI)
        }
    }

    // If exit button is pressed, 'finish the activity' aka 'exit the app'.
    if (exitTrigger.value) activity.finish()

    // This is the loading screen
    val loading = state.loadingState as? LoadingState.Loading
    if (loading != null) SplashLoading(loading.progress)

    WebView(
        modifier = Modifier.fillMaxSize(),
        state = state,
        navigator = navigator,
        platformWebViewParams = permHandler(context),
        captureBackPresses = false,
        onCreated = { webView ->

            (activity.window).setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )

            // Set up cookies
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(webView, true)
            cookieManager.flush()

            state.webSettings.apply {
                // This user agent provides native like experience.
                // "PS4" for 4K. "Wired" for previews.
                customUserAgentString = "Mozilla/5.0 Cobalt/25 (Sony, PS4, Wired)"
                isJavaScriptEnabled = true

                androidWebSettings.apply {
                    //isDebugInspectorInfoEnabled = true
                    useWideViewPort = true
                    domStorageEnabled = true
                    hideDefaultVideoPoster = true
                    mediaPlaybackRequiresUserGesture = false
                }
            }

            webView.apply {

                // Bridges the exit button click on the website to handle it natively.
                addJavascriptInterface(ExitBridge(exitTrigger), "ExitBridge")

                /*
                Youtube's content security policy doesn't allow calling fetch on
                3rd party websites (eg. SponsorBlock api). This bridge counters that
                handling the requests on the native side. */
                addJavascriptInterface(NetworkBridge(navigator), "NetworkBridge")

                // Enables hardware acceleration
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
                // Set the zoom to 25% to fit the screen. Side-effect of viewport spoofing.
                setInitialScale(25)

                // Hide scrollbars
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
            }
        }
    )
}