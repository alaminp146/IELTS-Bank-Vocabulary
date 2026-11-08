package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

  private var fileChooserCallback: ValueCallback<Array<Uri>>? = null

  private val fileChooserLauncher = registerForActivityResult(
    ActivityResultContracts.StartActivityForResult()
  ) { result ->
    if (result.resultCode == RESULT_OK) {
      val data: Intent? = result.data
      val clipData = data?.clipData
      val uriList = mutableListOf<Uri>()

      if (clipData != null) {
        for (i in 0 until clipData.itemCount) {
          uriList.add(clipData.getItemAt(i).uri)
        }
      } else if (data?.data != null) {
        uriList.add(data.data!!)
      }

      fileChooserCallback?.onReceiveValue(uriList.toTypedArray())
    } else {
      fileChooserCallback?.onReceiveValue(null)
    }
    fileChooserCallback = null
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        val context = LocalContext.current
        val notificationPermissionLauncher = rememberLauncherForActivityResult(
          ActivityResultContracts.RequestPermission()
        ) { /* Granted or dismissed */ }

        LaunchedEffect(Unit) {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
              ) != PackageManager.PERMISSION_GRANTED
            ) {
              notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
          }
        }

        Surface(
          modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
        ) {
          StickyVocabWebViewScreen(
            onLaunchFileChooser = { callback, intent ->
              fileChooserCallback?.onReceiveValue(null)
              fileChooserCallback = callback
              try {
                fileChooserLauncher.launch(intent)
              } catch (e: Exception) {
                fileChooserCallback?.onReceiveValue(null)
                fileChooserCallback = null
              }
            },
            onRequestNotificationPermission = {
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
              }
            }
          )
        }
      }
    }
  }
}

class AndroidBridge(
  private val context: Context,
  private val onRequestPermission: () -> Unit
) {
  @JavascriptInterface
  fun scheduleDailyReminder(hour: Int, minute: Int, enabled: Boolean, previewWords: String) {
    if (enabled && hour in 0..23 && minute in 0..59) {
      DailyReminderReceiver.scheduleReminder(context, hour, minute, previewWords)
    } else {
      DailyReminderReceiver.cancelReminder(context)
    }
  }

  @JavascriptInterface
  fun testNotification(title: String, message: String) {
    DailyReminderReceiver.sendImmediateNotification(context, title, message)
  }

  @JavascriptInterface
  fun requestNotificationPermission() {
    onRequestPermission()
  }

  @JavascriptInterface
  fun isNotificationPermissionGranted(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
      ) == PackageManager.PERMISSION_GRANTED
    } else {
      true
    }
  }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun StickyVocabWebViewScreen(
  onLaunchFileChooser: (ValueCallback<Array<Uri>>, Intent) -> Unit,
  onRequestNotificationPermission: () -> Unit,
  modifier: Modifier = Modifier
) {
  var webViewRef by remember { mutableStateOf<WebView?>(null) }
  var canGoBack by remember { mutableStateOf(false) }

  BackHandler(enabled = canGoBack) {
    webViewRef?.let {
      if (it.canGoBack()) {
        it.goBack()
      }
    }
  }

  AndroidView(
    modifier = modifier
      .fillMaxSize()
      .testTag("sticky_vocab_webview"),
    factory = { context ->
      WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.MATCH_PARENT
        )

        // Hardware acceleration for high FPS and smooth scrolling
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS

        settings.apply {
          javaScriptEnabled = true
          domStorageEnabled = true
          databaseEnabled = true
          allowFileAccess = true
          allowContentAccess = true
          useWideViewPort = true
          loadWithOverviewMode = true
          displayZoomControls = false
          builtInZoomControls = false
          cacheMode = WebSettings.LOAD_DEFAULT
          mediaPlaybackRequiresUserGesture = false
        }

        addJavascriptInterface(
          AndroidBridge(context, onRequestNotificationPermission),
          "AndroidBridge"
        )

        webViewClient = object : WebViewClient() {
          override fun shouldOverrideUrlLoading(
            view: WebView?,
            request: WebResourceRequest?
          ): Boolean {
            val url = request?.url?.toString() ?: return false
            if (url.startsWith("file:///android_asset/") || url.startsWith("data:")) {
              return false
            }
            return try {
              val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
              context.startActivity(intent)
              true
            } catch (e: Exception) {
              false
            }
          }

          override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            canGoBack = view?.canGoBack() == true
          }
        }

        webChromeClient = object : WebChromeClient() {
          override fun onShowFileChooser(
            webView: WebView?,
            filePathCallback: ValueCallback<Array<Uri>>?,
            fileChooserParams: FileChooserParams?
          ): Boolean {
            if (filePathCallback == null) return false
            val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
              type = "*/*"
              addCategory(Intent.CATEGORY_OPENABLE)
            }
            onLaunchFileChooser(filePathCallback, intent)
            return true
          }
        }

        loadUrl("file:///android_asset/index.html")
        webViewRef = this
      }
    },
    update = { webView ->
      webViewRef = webView
      canGoBack = webView.canGoBack()
    }
  )

  DisposableEffect(Unit) {
    onDispose {
      webViewRef?.destroy()
    }
  }
}
