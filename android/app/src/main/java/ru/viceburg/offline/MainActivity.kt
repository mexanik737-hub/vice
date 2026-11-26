package ru.viceburg.offline

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.MimeTypeMap
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.Toast
import android.view.View
import androidx.documentfile.provider.DocumentFile
import org.json.JSONObject
import org.json.JSONTokener
import java.io.FilterInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var shell: LinearLayout
    private var customVideoView: View? = null
    private var customVideoCallback: WebChromeClient.CustomViewCallback? = null
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private val prefs by lazy { getSharedPreferences("viceburg", MODE_PRIVATE) }
    private val mediaTree: Uri? get() = prefs.getString(KEY_MEDIA_TREE, null)?.let(Uri::parse)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.WHITE
        window.navigationBarColor = Color.rgb(16, 16, 20)
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(16, 16, 20))
        }
        shell = root
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            root.setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
        }
        web = WebView(this)
        configureWebView()
        root.addView(web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        web.loadUrl("https://viceburg.local/game/index.html")
    }

    private fun configureWebView() {
        web.addJavascriptInterface(AppBridge(), "ViceburgAndroid")
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false
            allowContentAccess = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = true
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                fileChooserCallback?.onReceiveValue(null)
                fileChooserCallback = filePathCallback ?: return false
                return try {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivityForResult(intent, REQUEST_GAME_FILE)
                    true
                } catch (_: Exception) {
                    fileChooserCallback?.onReceiveValue(null)
                    fileChooserCallback = null
                    toast("Не удалось открыть выбор файла")
                    true
                }
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (view == null || callback == null) return
                if (customVideoView != null) {
                    callback.onCustomViewHidden()
                    return
                }
                customVideoView = view
                customVideoCallback = callback
                shell.visibility = View.GONE
                (window.decorView as ViewGroup).addView(
                    view,
                    ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                )
            }

            override fun onHideCustomView() {
                hideCustomVideo()
            }
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse? =
                interceptAppResource(request.url, request.requestHeaders)

            @Suppress("DEPRECATION")
            override fun shouldInterceptRequest(view: WebView?, url: String?): WebResourceResponse? =
                url?.let { interceptAppResource(Uri.parse(it), emptyMap()) }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean {
                // Only the synthetic local origin is navigable; no external URL is opened.
                return request.url.scheme != "https" || request.url.host != APP_HOST
            }
        }
    }

    private fun interceptAppResource(uri: Uri, headers: Map<String, String>): WebResourceResponse? {
        if (uri.scheme != "https" || uri.host != APP_HOST) return null
        if (uri.path == GAME_PATH) {
            return WebResourceResponse("text/html", "UTF-8", assets.open("game/index.html"))
        }
        return interceptMedia(uri, headers) ?: notFound()
    }

    private fun interceptMedia(uri: Uri, headers: Map<String, String>): WebResourceResponse? {
        val tree = mediaTree ?: return null
        val rawPath = (uri.path ?: return null).replace('\\', '/')
        val marker = "/Media/"
        val start = rawPath.indexOf(marker, ignoreCase = true)
        if (start < 0) return null
        val relative = rawPath.substring(start + marker.length)
        val parts = relative.split('/').filter { it.isNotEmpty() }
        if (parts.isEmpty() || parts.any { it == "." || it == ".." }) return notFound()
        return try {
            var node = DocumentFile.fromTreeUri(this, tree) ?: return notFound()
            for (part in parts) node = node.findFile(part) ?: return notFound()
            if (!node.isFile) return notFound()
            openSafResponse(node.uri, headers)
        } catch (_: Exception) {
            notFound()
        }
    }

    private fun openSafResponse(uri: Uri, requestHeaders: Map<String, String>): WebResourceResponse {
        val detectedType = contentResolver.getType(uri)?.substringBefore(';')?.lowercase(Locale.ROOT)
        val extensionType = mimeFromName(uri.lastPathSegment)
        // Some SAF providers report application/octet-stream for known media extensions.
        val type = if (detectedType.isNullOrBlank() || detectedType == "application/octet-stream") extensionType else detectedType
        val range = requestHeaders.entries.firstOrNull { it.key.equals("Range", true) }?.value
        val afd = contentResolver.openAssetFileDescriptor(uri, "r")
            ?: return WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", emptyMap(), null)
        val size = afd.length
        if (range == null || size < 0) {
            val stream = afd.createInputStream()
            return WebResourceResponse(type, null, 200, "OK", mapOf("Accept-Ranges" to "bytes"), ClosingInputStream(stream, afd))
        }
        val match = Regex("bytes=(\\d*)-(\\d*)").matchEntire(range.trim())
        if (match == null) {
            afd.close()
            return WebResourceResponse(type, null, 416, "Range Not Satisfiable", mapOf("Content-Range" to "bytes */$size"), null)
        }
        val startText = match.groupValues[1]
        val endText = match.groupValues[2]
        val start: Long
        val requestedEnd: Long
        if (startText.isEmpty()) {
            val suffixLength = endText.toLongOrNull()
            if (suffixLength == null || suffixLength <= 0) {
                afd.close()
                return WebResourceResponse(type, null, 416, "Range Not Satisfiable", mapOf("Content-Range" to "bytes */$size"), null)
            }
            start = (size - suffixLength).coerceAtLeast(0)
            requestedEnd = size - 1
        } else {
            start = startText.toLongOrNull() ?: 0L
            requestedEnd = endText.toLongOrNull() ?: (size - 1)
        }
        if (start >= size || requestedEnd < start) {
            afd.close()
            return WebResourceResponse(type, null, 416, "Range Not Satisfiable", mapOf("Content-Range" to "bytes */$size"), null)
        }
        val end = minOf(requestedEnd, size - 1)
        val input = afd.createInputStream()
        skipFully(input, start)
        val limited = LimitedInputStream(input, end - start + 1)
        val stream = ClosingInputStream(limited, afd)
        val responseHeaders = mapOf(
            "Accept-Ranges" to "bytes",
            "Content-Length" to (end - start + 1).toString(),
            "Content-Range" to "bytes $start-$end/$size"
        )
        return WebResourceResponse(type, null, 206, "Partial Content", responseHeaders, stream)
    }

    private fun notFound() = WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", emptyMap(), null)

    private fun mimeFromName(name: String?): String {
        val ext = name?.substringAfterLast('.', "")?.lowercase(Locale.ROOT).orEmpty()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    private inner class AppBridge {
        @JavascriptInterface
        fun selectMedia() = runOnUiThread { pickMediaTree() }

        @JavascriptInterface
        fun requestExportSave() = runOnUiThread { exportSave() }

        @JavascriptInterface
        fun requestImportSave() = runOnUiThread { importSave() }

        @JavascriptInterface
        fun mediaStatus(): String = if (mediaTree == null) "Media не выбрана" else "Media подключена"
    }

    private fun pickMediaTree() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        mediaTree?.let { intent.putExtra("android.provider.extra.INITIAL_URI", it) }
        startActivityForResult(intent, REQUEST_MEDIA)
    }

    private fun exportSave() {
        web.evaluateJavascript("JSON.stringify({format:'viceburg-localstorage-v1',savedAt:new Date().toISOString(),entries:Object.fromEntries(Object.keys(localStorage).map(k=>[k,localStorage.getItem(k)]))})") { encoded ->
            try {
                val json = JSONTokener(encoded).nextValue() as? String ?: throw IllegalStateException("empty save")
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                    putExtra(Intent.EXTRA_TITLE, "viceburg-save.json")
                }
                pendingExport = json
                startActivityForResult(intent, REQUEST_EXPORT)
            } catch (_: Exception) { toast("Не удалось подготовить сохранение") }
        }
    }

    private var pendingExport: String? = null

    private fun importSave() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_IMPORT)
    }

    @Deprecated("SAF result callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_GAME_FILE && resultCode != Activity.RESULT_OK) {
            fileChooserCallback?.onReceiveValue(null)
            fileChooserCallback = null
            return
        }
        if (requestCode == REQUEST_GAME_FILE) {
            val callback = fileChooserCallback ?: return
            fileChooserCallback = null
            callback.onReceiveValue(data?.data?.let { arrayOf(it) })
            return
        }
        if (resultCode != Activity.RESULT_OK) return
        val uri = data?.data ?: return
        when (requestCode) {
            REQUEST_MEDIA -> {
                val flags = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                contentResolver.takePersistableUriPermission(uri, flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
                prefs.edit().putString(KEY_MEDIA_TREE, uri.toString()).apply()
                web.evaluateJavascript("window.dispatchEvent(new Event('viceburg-media-change'))", null)
                toast("Папка Media подключена")
            }
            REQUEST_EXPORT -> {
                try {
                    val content = pendingExport ?: return
                    contentResolver.openOutputStream(uri, "w")!!.bufferedWriter(Charsets.UTF_8).use { it.write(content) }
                    pendingExport = null
                    toast("Сохранение экспортировано")
                } catch (_: Exception) { toast("Не удалось записать файл сохранения") }
            }
            REQUEST_IMPORT -> {
                try {
                    val input = contentResolver.openInputStream(uri) ?: throw IllegalStateException("empty save")
                    val bytes = input.use { readBounded(it, MAX_SAVE_BYTES) }
                    val json = bytes.toString(Charsets.UTF_8)
                    val root = JSONObject(json)
                    require(root.optString("format") == "viceburg-localstorage-v1") { "unsupported format" }
                    val entries = root.getJSONObject("entries")
                    val keys = entries.keys()
                    while (keys.hasNext()) require(entries.opt(keys.next()) is String) { "invalid save entry" }
                    val encoded = JSONObject.quote(entries.toString())
                    web.evaluateJavascript("(()=>{const p=JSON.parse($encoded);const old=Object.fromEntries(Object.keys(localStorage).map(k=>[k,localStorage.getItem(k)]));try{localStorage.clear();for(const [k,v] of Object.entries(p))localStorage.setItem(k,v);localStorage.setItem('__viceburg_import_pending','1');sessionStorage.clear();setTimeout(()=>location.reload(),250);return true}catch(e){try{localStorage.clear();for(const [k,v] of Object.entries(old))localStorage.setItem(k,v)}catch(_){}return false}})()") { result ->
                        if (result == "true") toast("Сохранение импортировано") else toast("Не удалось восстановить сохранение")
                    }
                } catch (_: Exception) { toast("Файл сохранения не распознан") }
            }
        }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun readBounded(input: InputStream, maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) return output.toByteArray()
            require(output.size() + count <= maxBytes) { "save too large" }
            output.write(buffer, 0, count)
        }
    }

    private fun hideCustomVideo() {
        val view = customVideoView ?: return
        (window.decorView as ViewGroup).removeView(view)
        customVideoView = null
        shell.visibility = View.VISIBLE
        customVideoCallback?.onCustomViewHidden()
        customVideoCallback = null
    }

    override fun onBackPressed() {
        if (customVideoView != null) hideCustomVideo()
        else if (::web.isInitialized && web.canGoBack()) web.goBack()
        else super.onBackPressed()
    }

    override fun onDestroy() {
        if (customVideoView != null) hideCustomVideo()
        fileChooserCallback?.onReceiveValue(null)
        fileChooserCallback = null
        if (::web.isInitialized) { web.stopLoading(); web.destroy() }
        super.onDestroy()
    }

    private class ClosingInputStream(input: InputStream, private val afd: android.content.res.AssetFileDescriptor) : FilterInputStream(input) {
        override fun close() { try { super.close() } finally { afd.close() } }
    }

    private class LimitedInputStream(input: InputStream, private var remaining: Long) : FilterInputStream(input) {
        override fun read(): Int { if (remaining <= 0) return -1; val v = super.read(); if (v >= 0) remaining--; return v }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (remaining <= 0) return -1
            val n = super.read(buffer, offset, minOf(length.toLong(), remaining).toInt())
            if (n > 0) remaining -= n
            return n
        }
    }

    private fun skipFully(input: InputStream, amount: Long) {
        var left = amount
        while (left > 0) {
            val skipped = input.skip(left)
            if (skipped > 0) left -= skipped else if (input.read() == -1) throw IllegalStateException("short media stream") else left--
        }
    }

    companion object {
        private const val APP_HOST = "viceburg.local"
        private const val GAME_PATH = "/game/index.html"
        private const val KEY_MEDIA_TREE = "media_tree_uri"
        private const val REQUEST_MEDIA = 1001
        private const val REQUEST_EXPORT = 1002
        private const val REQUEST_IMPORT = 1003
        private const val REQUEST_GAME_FILE = 1004
        private const val MAX_SAVE_BYTES = 8 * 1024 * 1024
    }
}
