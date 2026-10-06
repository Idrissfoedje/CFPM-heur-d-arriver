package com.gbeauto237.pointage

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewAssetLoader

class MainActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private var pendingRequest: PermissionRequest? = null

    private val cameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val r = pendingRequest
            pendingRequest = null
            if (granted) r?.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) else r?.deny()
        }

    private fun hasCamera() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        web = WebView(this)
        setContentView(web)

        // Demande la caméra dès le lancement
        if (!hasCamera()) cameraPermission.launch(Manifest.permission.CAMERA)

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true            // IndexedDB : sauvegarde des pointages
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            allowContentAccess = false
            useWideViewPort = false
            loadWithOverviewMode = false
            textZoom = 100
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }

        // Sert index.html en HTTPS virtuel => contexte sécurisé => getUserMedia autorisé
        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView, request: WebResourceRequest
            ): WebResourceResponse? = loader.shouldInterceptRequest(request.url)
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread {
                    val wantsCamera = request.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
                    if (!wantsCamera) { request.deny(); return@runOnUiThread }
                    if (hasCamera()) {
                        request.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
                    } else {
                        pendingRequest = request
                        cameraPermission.launch(Manifest.permission.CAMERA)
                    }
                }
            }

            // Pas de photo depuis la galerie : seule la caméra en direct est acceptée
            override fun onShowFileChooser(
                webView: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams
            ): Boolean {
                callback.onReceiveValue(null)
                Toast.makeText(this@MainActivity, "Utilisez la caméra de l'application.", Toast.LENGTH_SHORT).show()
                return true
            }

            override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult): Boolean {
                AlertDialog.Builder(this@MainActivity)
                    .setMessage(message)
                    .setPositiveButton("OK") { _, _ -> result.confirm() }
                    .setNegativeButton("Annuler") { _, _ -> result.cancel() }
                    .setOnCancelListener { result.cancel() }
                    .show()
                return true
            }

            override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
                AlertDialog.Builder(this@MainActivity)
                    .setMessage(message)
                    .setPositiveButton("OK") { _, _ -> result.confirm() }
                    .setOnCancelListener { result.confirm() }
                    .show()
                return true
            }
        }

        web.addJavascriptInterface(Bridge(), "AndroidBridge")
        web.loadUrl("https://appassets.androidplatform.net/assets/index.html")
    }

    /** Pont JavaScript -> Android : enregistre le CSV et les sauvegardes dans Téléchargements et le stockage local. */
    inner class Bridge {
        @JavascriptInterface
        fun saveCsv(fileName: String, content: String): Boolean {
            return saveFile(fileName, content, "text/csv")
        }

        @JavascriptInterface
        fun saveFile(fileName: String, content: String, mimeType: String): Boolean {
            return try {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("insert null")
                contentResolver.openOutputStream(uri)!!.use { it.write(content.toByteArray(Charsets.UTF_8)) }
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Exporté dans Téléchargements : $fileName", Toast.LENGTH_LONG).show()
                }
                true
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Export impossible", Toast.LENGTH_LONG).show()
                }
                false
            }
        }

        @JavascriptInterface
        fun saveHistoryBackup(jsonContent: String): Boolean {
            return try {
                val internalFile = java.io.File(filesDir, "pointage_cfpm_sauvegarde.json")
                internalFile.writeText(jsonContent, Charsets.UTF_8)
                val extDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!extDir.exists()) extDir.mkdirs()
                val extFile = java.io.File(extDir, "pointage_cfpm_sauvegarde_annuelle.json")
                extFile.writeText(jsonContent, Charsets.UTF_8)
                true
            } catch (e: Exception) {
                false
            }
        }

        @JavascriptInterface
        fun readHistoryBackup(): String {
            return try {
                val extDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val extFile = java.io.File(extDir, "pointage_cfpm_sauvegarde_annuelle.json")
                if (extFile.exists() && extFile.length() > 0) {
                    extFile.readText(Charsets.UTF_8)
                } else {
                    val internalFile = java.io.File(filesDir, "pointage_cfpm_sauvegarde.json")
                    if (internalFile.exists() && internalFile.length() > 0) {
                        internalFile.readText(Charsets.UTF_8)
                    } else ""
                }
            } catch (e: Exception) {
                ""
            }
        }
    }
}
