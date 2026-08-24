package imrankst1221.website.`in`.webview

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.webkit.DownloadListener
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

class MainActivity : Activity() {

    private lateinit var mWebView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var layoutSplash: RelativeLayout
    private lateinit var layoutNoInternet: RelativeLayout
    private lateinit var btnTryAgain: Button

    private var downloadId: Long = -1

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mWebView = findViewById(R.id.webview)
        progressBar = findViewById(R.id.progressBar)
        layoutSplash = findViewById(R.id.layout_splash)
        layoutNoInternet = findViewById(R.id.layout_no_internet)
        btnTryAgain = findViewById(R.id.btn_try_again)

        // НАСТРОЙКИ WEBVIEW
        mWebView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
        }

        // ОБРАБОТКА ССЫЛОК
        mWebView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                // Если ссылка на APK - скачиваем сами
                if (url.endsWith(".apk") || url.contains(".apk?")) {
                    downloadApk(url)
                    return true
                }
                view.loadUrl(url)
                return true
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                progressBar.visibility = android.view.View.GONE
                layoutSplash.visibility = android.view.View.GONE
            }
        }

        // ЗАГРУЗКА ФАЙЛОВ
        mWebView.setDownloadListener { url, _, _, _, _ ->
            if (url.endsWith(".apk") || url.contains(".apk?")) {
                downloadApk(url)
            } else {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Не удалось открыть ссылку", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // КНОПКА "ПОПРОБОВАТЬ СНОВА"
        btnTryAgain.setOnClickListener {
            layoutNoInternet.visibility = android.view.View.GONE
            layoutSplash.visibility = android.view.View.VISIBLE
            mWebView.loadUrl("https://xn----itbiqjeweaj4a.xn--p1ai/")
        }

        // ЗАГРУЖАЕМ САЙТ
        mWebView.loadUrl("https://xn----itbiqjeweaj4a.xn--p1ai/")
    }

    // ============================================
    // СКАЧАТЬ APK
    // ============================================
    private fun downloadApk(url: String) {
        try {
            // Проверяем разрешение (для Android 6+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                        100
                    )
                    Toast.makeText(this, "Разрешите доступ к хранилищу", Toast.LENGTH_LONG).show()
                    return
                }
            }

            Toast.makeText(this, "Загрузка обновления...", Toast.LENGTH_SHORT).show()

            val request = DownloadManager.Request(Uri.parse(url))
            request.setTitle("Обновление Питомец Тут")
            request.setDescription("Загрузка новой версии...")
            request.setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                "ПитомецТут_update.apk"
            )
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

            val downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            downloadId = downloadManager.enqueue(request)

            // Регистрируем приемник
            try {
                unregisterReceiver(downloadReceiver)
            } catch (e: Exception) {}
            registerReceiver(downloadReceiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Ошибка загрузки", Toast.LENGTH_SHORT).show()
        }
    }

    // ============================================
    // ПРИЕМНИК ЗАГРУЗКИ
    // ============================================
    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            try {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    Toast.makeText(context, "Загрузка завершена!", Toast.LENGTH_LONG).show()
                    installApk()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // ============================================
    // УСТАНОВКА APK
    // ============================================
    private fun installApk() {
        try {
            val file = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "ПитомецТут_update.apk"
            )

            if (!file.exists()) {
                Toast.makeText(this, "Файл обновления не найден!", Toast.LENGTH_SHORT).show()
                return
            }

            val intent = Intent(Intent.ACTION_VIEW)
            val apkUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            } else {
                Uri.fromFile(file)
            }

            intent.setDataAndType(apkUri, "application/vnd.android.package-archive")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(intent)

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Ошибка установки", Toast.LENGTH_SHORT).show()
        }
    }

    // ============================================
    // РАЗРЕШЕНИЯ
    // ============================================
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Разрешение получено!", Toast.LENGTH_SHORT).show()
        }
    }

    // ============================================
    // КНОПКА НАЗАД
    // ============================================
    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean {
        if (keyCode == android.view.KeyEvent.KEYCODE_BACK && mWebView.canGoBack()) {
            mWebView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    // ============================================
    // ЖИЗНЕННЫЙ ЦИКЛ
    // ============================================
    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(downloadReceiver)
        } catch (e: Exception) {}
    }
}
