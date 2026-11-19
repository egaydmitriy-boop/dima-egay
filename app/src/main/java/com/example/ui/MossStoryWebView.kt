package com.example.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject

class MossWebBridge(
    private val onNavigate: (route: String, deptFilter: String) -> Unit,
    private val onTriggerVoice: () -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun navigate(route: String, deptFilter: String?) {
        mainHandler.post {
            onNavigate(route, deptFilter.orEmpty())
        }
    }

    @JavascriptInterface
    fun triggerVoiceHotkey() {
        mainHandler.post {
            onTriggerVoice()
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MossStoryWebView(
    liveJsonPayload: String,
    onNavigate: (route: String, deptFilter: String) -> Unit,
    onTriggerVoice: () -> Unit,
    modifier: Modifier = Modifier
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var pageReady by remember { mutableStateOf(false) }

    LaunchedEffect(liveJsonPayload, pageReady) {
        val wv = webViewRef ?: return@LaunchedEffect
        if (pageReady && liveJsonPayload.isNotBlank()) {
            val quoted = JSONObject.quote(liveJsonPayload)
            wv.evaluateJavascript("window.updateMossData && window.updateMossData($quoted);", null)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.apply {
                removeJavascriptInterface("MossBridge")
                destroy()
            }
            webViewRef = null
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(Color.parseColor("#E7EBE8"))
                    setLayerType(View.LAYER_TYPE_HARDWARE, null)
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        allowFileAccess = true
                        allowContentAccess = true
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        useWideViewPort = true
                        loadWithOverviewMode = true
                    }
                    addJavascriptInterface(
                        MossWebBridge(onNavigate = onNavigate, onTriggerVoice = onTriggerVoice),
                        "MossBridge"
                    )
                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            pageReady = true
                            val quoted = JSONObject.quote(liveJsonPayload)
                            view?.evaluateJavascript(
                                "window.updateMossData && window.updateMossData($quoted);",
                                null
                            )
                        }
                    }
                    loadUrl("file:///android_asset/mossline_story.html")
                    webViewRef = this
                }
            }
        )
    }
}
