package com.example.gesturetalk

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.gesturetalk.databinding.ActivityGuideOnlineBinding
import com.example.gesturetalk.ui.NavTransitions.openBottomNavTab
import com.example.gesturetalk.ui.NavTransitions.setupGestureTalkBottomNav

class GuideOnlineActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGuideOnlineBinding
    private val startUrl = "https://spreadthesign.ru/"

    private val mainHandler = Handler(Looper.getMainLooper())
    private var pageFinishedAtMs = 0L
    private var splashHidden = false
    private var readyPollAttempts = 0

    private val pollReadyRunnable = object : Runnable {
        override fun run() {
            pollDictionaryReady()
        }
    }

    private val forceHideSplashRunnable = Runnable {
        hideSplash()
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGuideOnlineBinding.inflate(layoutInflater)
        setContentView(binding.root)

        com.example.gesturetalk.utils.AchievementsManager.updateDaysStreak(this)
        com.example.gesturetalk.utils.AchievementsManager.incrementGuideOpens(this)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.webView.canGoBack()) {
                    binding.webView.goBack()
                } else {
                    finish()
                }
            }
        })

        setupWebView()
        setupBottomNav()

        if (savedInstanceState == null) {
            showSplash()
            binding.webView.loadUrl(startUrl)
        } else {
            binding.splashLayout.visibility = View.GONE
            binding.webView.visibility = View.VISIBLE
            splashHidden = true
            binding.webView.restoreState(savedInstanceState)
        }
    }

    private fun setupWebView() {
        binding.webView.visibility = View.INVISIBLE
        binding.webView.setBackgroundColor(ContextCompat.getColor(this, R.color.surface_light))
        binding.webView.isFocusableInTouchMode = true
        binding.webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = false
            displayZoomControls = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportZoom(false)
        }

        binding.webView.webChromeClient = WebChromeClient()

        binding.webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val host = request?.url?.host ?: return false
                return !SpreadTheSignHosts.isAllowed(host)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                if (view == null) return
                injectVideoFix(view)
                if (isDictionaryHomeUrl(url)) {
                    cancelSplashTimers()
                    splashHidden = false
                    readyPollAttempts = 0
                    showSplash()
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (view == null) return
                injectVideoFix(view)

                if (!isDictionaryHomeUrl(url)) {
                    hideSplash()
                    return
                }

                pageFinishedAtMs = System.currentTimeMillis()
                readyPollAttempts = 0
                scheduleSplashReadyCheck()
            }
        }
    }

    private fun isDictionaryHomeUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return true
        return try {
            val uri = android.net.Uri.parse(url)
            val host = uri.host?.lowercase() ?: return false
            if (!SpreadTheSignHosts.isAllowed(host)) return false
            val path = uri.path?.trim('/') ?: ""
            path.isEmpty()
        } catch (_: Exception) {
            false
        }
    }

    private fun showSplash() {
        binding.splashLayout.visibility = View.VISIBLE
        binding.webView.visibility = View.INVISIBLE
    }

    private fun hideSplash() {
        if (splashHidden) return
        splashHidden = true
        cancelSplashTimers()
        binding.splashLayout.visibility = View.GONE
        binding.webView.visibility = View.VISIBLE
    }

    private fun cancelSplashTimers() {
        mainHandler.removeCallbacks(pollReadyRunnable)
        mainHandler.removeCallbacks(forceHideSplashRunnable)
    }

    private fun scheduleSplashReadyCheck() {
        cancelSplashTimers()
        mainHandler.post(pollReadyRunnable)
        mainHandler.postDelayed(forceHideSplashRunnable, MAX_SPLASH_WAIT_MS)
    }

    private fun pollDictionaryReady() {
        if (splashHidden) return

        val elapsed = System.currentTimeMillis() - pageFinishedAtMs
        if (elapsed < MIN_SPLASH_AFTER_FINISH_MS) {
            mainHandler.postDelayed(pollReadyRunnable, POLL_INTERVAL_MS)
            return
        }

        binding.webView.evaluateJavascript(DICTIONARY_READY_JS) { raw ->
            val ready = raw?.contains("true", ignoreCase = true) == true
            if (ready) {
                hideSplash()
            } else {
                readyPollAttempts++
                if (readyPollAttempts * POLL_INTERVAL_MS >= MAX_READY_POLL_MS) {
                    hideSplash()
                } else {
                    mainHandler.postDelayed(pollReadyRunnable, POLL_INTERVAL_MS)
                }
            }
        }
    }

    private fun injectVideoFix(view: WebView) {
        view.evaluateJavascript(VIDEO_FIX_JS, null)
    }

    private fun setupBottomNav() {
        setupGestureTalkBottomNav(binding.bottomNav, R.id.nav_guide_online) { nav ->
            nav.setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.nav_guide_online -> true
                    R.id.nav_ai -> {
                        openBottomNavTab(AskAiActivity::class.java, R.id.nav_ai, R.id.nav_guide_online)
                        true
                    }
                    R.id.nav_recognition -> {
                        openBottomNavTab(MainActivity::class.java, R.id.nav_recognition, R.id.nav_guide_online)
                        true
                    }
                    R.id.nav_stats -> {
                        openBottomNavTab(StatsActivity::class.java, R.id.nav_stats, R.id.nav_guide_online)
                        true
                    }
                    R.id.nav_profile -> {
                        openBottomNavTab(ProfileActivity::class.java, R.id.nav_profile, R.id.nav_guide_online)
                        true
                    }
                    else -> false
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        binding.bottomNav.selectedItemId = R.id.nav_guide_online
        binding.webView.onResume()
    }

    override fun onPause() {
        binding.webView.onPause()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.webView.saveState(outState)
    }

    override fun onDestroy() {
        cancelSplashTimers()
        binding.webView.apply {
            stopLoading()
            (parent as? ViewGroup)?.removeView(this)
            destroy()
        }
        super.onDestroy()
    }

    private object SpreadTheSignHosts {
        fun isAllowed(hostRaw: String): Boolean {
            val host = hostRaw.lowercase()
            return host == "spreadthesign.ru" ||
                host.endsWith(".spreadthesign.ru") ||
                host == "spreadthesign.com" ||
                host.endsWith(".spreadthesign.com")
        }
    }

    companion object {
        private const val MIN_SPLASH_AFTER_FINISH_MS = 2_200L
        private const val POLL_INTERVAL_MS = 280L
        private const val MAX_READY_POLL_MS = 5_500L
        private const val MAX_SPLASH_WAIT_MS = 7_500L

        private val DICTIONARY_READY_JS = """
            (function() {
              try {
                var text = (document.body && document.body.innerText) ? document.body.innerText : '';
                var letterCount = 0;
                var nodes = document.querySelectorAll('a, button, span, div, li');
                for (var i = 0; i < nodes.length; i++) {
                  var s = (nodes[i].textContent || '').trim();
                  if (s.length === 1 && /[\u0400-\u04FFA-Za-z]/.test(s)) letterCount++;
                }
                var hasTabs = text.indexOf('Словарь') !== -1 && text.indexOf('О нас') !== -1;
                var hasLandingOnly = text.indexOf('Выбрать жест') !== -1 && letterCount < 8;
                if (hasLandingOnly) return 'false';
                if (letterCount >= 12) return 'true';
                if (hasTabs && letterCount >= 8) return 'true';
                return 'false';
              } catch (e) {
                return 'false';
              }
            })();
        """.trimIndent()

        private val VIDEO_FIX_JS = """
            (function() {
              try {
                function apply(el) {
                  if (!el) return;
                  if (el.dataset && el.dataset.gtHidden === '1') return;
                  el.dataset.gtHidden = '1';
                  el.style.backgroundColor = '#FFFFFF';
                  el.style.visibility = 'hidden';
                  el.style.opacity = '0';
                  var show = function() {
                    el.style.visibility = 'visible';
                    el.style.opacity = '1';
                  };
                  el.addEventListener('loadeddata', show, { once: true });
                  el.addEventListener('canplay', show, { once: true });
                  setTimeout(show, 6000);
                }
                document.querySelectorAll('video').forEach(apply);
                if (!window.__gtVideoObserver) {
                  var obs = new MutationObserver(function(mutations) {
                    try {
                      mutations.forEach(function(m) {
                        if (m.addedNodes && m.addedNodes.length) {
                          m.addedNodes.forEach(function(n) {
                            if (n && n.querySelectorAll) {
                              n.querySelectorAll('video').forEach(apply);
                            }
                          });
                        }
                      });
                    } catch (e) {}
                  });
                  obs.observe(document.documentElement, { childList: true, subtree: true });
                  window.__gtVideoObserver = obs;
                }
              } catch (e) {}
            })();
        """.trimIndent()
    }
}
