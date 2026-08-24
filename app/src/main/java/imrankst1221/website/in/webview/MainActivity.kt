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
import android.os.Handler
import android.os.Looper
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : Activity() {

    private lateinit var mWebView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var layoutSplash: RelativeLayout
    private lateinit var layoutNoInternet: RelativeLayout
    private lateinit var btnTryAgain: Button

    private var downloadId: Long = -1
    private var apkUrl: String = ""
    private var isUpdateCheckDone = false

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
            checkForUpdates()
        }

        // ЗАПУСКАЕМ ПРОВЕРКУ ОБНОВЛЕНИЙ
        checkForUpdates()
    }

    // ============================================
    // 1. ПРОВЕРКА ОБНОВЛЕНИЙ НА СЕРВЕРЕ
    // ============================================
    private fun checkForUpdates() {
        // Загружаем сайт
        mWebView.loadUrl("https://xn----itbiqjeweaj4a.xn--p1ai/")

        // Проверяем версии в фоновом потоке
        Thread {
            try {
                val currentVersion = getAppVersionCode()

                val url = URL("https://xn----itbiqjeweaj4a.xn--p1ai/version.json")
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.requestMethod = "GET"

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().readText()
                    val json = JSONObject(response)

                    val latestVersion = json.getInt("versionCode")
                    apkUrl = json.getString("apkUrl")

                    if (latestVersion > currentVersion) {
                        runOnUiThread {
                            showUpdateDialog()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    // ============================================
    // 2. ПОЛУЧИТЬ ТЕКУЩУЮ ВЕРСИЮ
    // ============================================
    private fun getAppVersionCode(): Int {
        return try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode
            }
        } catch (e: Exception) {
            1
        }
    }

    // ============================================
    // 3. ДИАЛОГ ОБНОВЛЕНИЯ
    // ============================================
    private fun showUpdateDialog() {
        AlertDialog.Builder(this)
            .setTitle("📱 Доступно обновление!")
            .setMessage("Вышла новая версия приложения. Хотите установить её сейчас?")
            .setPositiveButton("Обновить") { _, _ ->
                downloadApk(apkUrl)
            }
            .setNegativeButton("Позже") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    // ============================================
    // 4. СКАЧАТЬ APK
    // ============================================
    private fun downloadApk(url: String) {
        try {
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

            // Регистрируем приемник ДО загрузки
            try {
                unregisterReceiver(downloadReceiver)
            } catch (e: Exception) {}
            registerReceiver(downloadReceiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Ошибка загрузки: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ============================================
    // 5. ПРИЕМНИК ДЛЯ ЗАГРУЗКИ
    // ============================================
    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            try {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    Toast.makeText(context, "Загрузка завершена!", Toast.LENGTH_LONG).show()
                    Handler(Looper.getMainLooper()).postDelayed({
                        installApk()
                    }, 1000)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // ============================================
    // 6. УСТАНОВКА APK
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
            Toast.makeText(this, "Ошибка установки: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ============================================
    // 7. РАЗРЕШЕНИЯ
    // ============================================
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Разрешение получено!", Toast.LENGTH_SHORT).show()
        }
    }

    // ============================================
    // 8. КНОПКА НАЗАД
    // ============================================
    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean {
        if (keyCode == android.view.KeyEvent.KEYCODE_BACK && mWebView.canGoBack()) {
            mWebView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    // ============================================
    // 9. ЖИЗНЕННЫЙ ЦИКЛ (исправлено)
    // ============================================
    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(downloadReceiver)
        } catch (e: Exception) {
            // Приемник уже отключен
        }
    }
}
