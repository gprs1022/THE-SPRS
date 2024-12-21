package com.sprs.thesprs

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri

import android.os.Bundle
import android.view.View
import android.webkit.*
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    // Register activity for file upload
    private val uploadFile =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (filePathCallback != null) {
                val resultData = result.data
                val resultUris = if (resultData != null && result.resultCode == RESULT_OK) {
                    resultData.data?.let { arrayOf(it) }
                } else null
                filePathCallback?.onReceiveValue(resultUris)
                filePathCallback = null
            }
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Request all necessary permissions if not granted
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest, REQUEST_ALL_PERMISSIONS)
        }

        // Initialize UI components
        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)

        // Configure edge-to-edge display
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Configure WebView settings
        val webSettings: WebSettings = webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true // Enable DOM storage
        webSettings.cacheMode = WebSettings.LOAD_DEFAULT // Use default caching
        webSettings.allowFileAccess = true // Enable file access for caching
        webSettings.useWideViewPort = true // Enable responsive scaling
        webSettings.loadWithOverviewMode = true // Fit content to the screen
        webSettings.mediaPlaybackRequiresUserGesture = false // Enable autoplay
        webSettings.setGeolocationEnabled(true) // Enable geolocation

        // Enable hardware acceleration for smoother performance
        webView.setLayerType(WebView.LAYER_TYPE_HARDWARE, null)

        // Set WebChromeClient for permissions and file uploads
        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.let {
                    val grantedResources = it.resources.filter { resource ->
                        when (resource) {
                            PermissionRequest.RESOURCE_AUDIO_CAPTURE -> {
                                ContextCompat.checkSelfPermission(
                                    this@MainActivity,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                            }
                            else -> false
                        }
                    }.toTypedArray()

                    if (grantedResources.isNotEmpty()) {
                        runOnUiThread { it.grant(grantedResources) }
                    } else {
                        runOnUiThread { it.deny() }
                    }
                }
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                if (ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    // Grant geolocation permissions
                    callback?.invoke(origin, true, false)
                } else {
                    // Deny geolocation permissions
                    callback?.invoke(origin, false, false)
                }
            }

            override fun onShowFileChooser(
                webView: WebView?, filePathCallback: ValueCallback<Array<Uri>>?, fileChooserParams: FileChooserParams?
            ): Boolean {
                this@MainActivity.filePathCallback = filePathCallback
                val intent = fileChooserParams?.createIntent()
                try {
                    intent?.let { uploadFile.launch(it) }
                } catch (e: Exception) {
                    this@MainActivity.filePathCallback = null
                    Toast.makeText(this@MainActivity, "File chooser error", Toast.LENGTH_SHORT).show()
                    return false
                }
                return true
            }
        }

        // Set WebViewClient for handling external links, unsupported schemes, and progress bar
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url.toString()

                // Handle URLs with unsupported schemes
                if (url.startsWith("mailto:") || url.startsWith("tel:") || url.startsWith("sms:")) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(intent)
                    return true // Do not load these URLs in WebView
                }

                // Handle external links
                if (!Uri.parse(url).host.equals("thesprs.com")) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(intent)
                    return true
                }

                // For all other cases, load the URL in the WebView
                view?.loadUrl(url)
                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                progressBar.visibility = View.VISIBLE // Show progress bar
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progressBar.visibility = View.GONE // Hide progress bar
            }

            override fun onReceivedError(
                view: WebView?, request: WebResourceRequest?, error: WebResourceError?
            ) {
                // Handle webpage errors gracefully
                Toast.makeText(this@MainActivity, "Failed to load page", Toast.LENGTH_SHORT).show()
            }
        }

        // Load the initial URL
        webView.loadUrl("https://thesprs.com/")
    }

    // Handle permission results
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_ALL_PERMISSIONS) {
            val deniedPermissions = permissions.zip(grantResults.toTypedArray())
                .filter { it.second != PackageManager.PERMISSION_GRANTED }
                .map { it.first }

            if (deniedPermissions.isEmpty()) {
                Toast.makeText(this, "All permissions granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Permissions denied: ${deniedPermissions.joinToString()}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Handle back button navigation within WebView
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack() // Navigate to the previous page in WebView history
        } else {
            super.onBackPressed() // Exit the activity if no WebView history is available
        }
    }

    companion object {
        private const val REQUEST_ALL_PERMISSIONS = 1002
    }
}
