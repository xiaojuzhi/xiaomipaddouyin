package com.douyinpad.app;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;

/**
 * 抖音直播平板版 - 纯沉浸式全屏
 * 无任何控件，抖音网页铺满整个屏幕
 */
public class MainActivity extends AppCompatActivity {

    private WebView webView;

    private static final String DOUYIN_HOME = "https://www.douyin.com/";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 系统级沉浸式全屏：隐藏状态栏 + 导航栏
        hideSystemBars();

        setContentView(R.layout.activity_main);
        webView = findViewById(R.id.webView);

        configureWebView();
        webView.loadUrl(DOUYIN_HOME);
    }

    /** 隐藏状态栏和导航栏，实现真正的系统级全屏 */
    private void hideSystemBars() {
        // 隐藏顶部状态栏（ActionBar）
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        Window window = getWindow();
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        );

        // 沉浸式全屏（兼容新老 API）
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false);
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                );
            }
        } else {
            window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            );
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        // ===== 播放器内核性能优化 =====
        // 开启硬件加速（视频解码用 GPU，不卡顿）
        settings.setRenderPriority(WebSettings.RenderPriority.HIGH);
        // 开启缓存，二次进入更快
        settings.setAppCacheEnabled(true);
        settings.setAppCachePath(getCacheDir().getAbsolutePath());
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        // 开启并发线程，加快页面加载
        settings.setBlockNetworkImage(false);
        // 视频优先：不拦截媒体
        settings.setLoadsImagesAutomatically(true);
        // 平滑缩放，避免大屏抖动
        settings.setEnableSmoothTransition(true);
        // 禁用文字自动缩放，保证布局稳定
        settings.setTextZoom(100);

        settings.setUserAgentString(
            "Mozilla/5.0 (Linux; Android 14; Pad 7 Ultra) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        );

        // 开启 WebView 硬件加速层
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        // 允许 cookie，保持登录
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // 注入自动最高画质脚本
                view.evaluateJavascript(AUTO_SCRIPT, null);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                view.loadUrl(request.getUrl().toString());
                return true;
            }
        });

        webView.setWebChromeClient(new WebChromeClient());
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    /** 自动最高画质脚本 */
    private static final String AUTO_SCRIPT =
        "javascript:(function(){" +
        "  if(window.__douyinPadInjected)return; window.__douyinPadInjected=true;" +
        "  function tryMaxQuality(){" +
        "    try{" +
        "      var menu=document.querySelector('[class*=quality],[class*=Quality]');" +
        "      if(menu){menu.click();}" +
        "      setTimeout(function(){" +
        "        var opts=document.querySelectorAll('[class*=quality-menu] li,[class*=Quality] li,li[class*=quality]');" +
        "        if(opts&&opts.length){opts[opts.length-1].click();}" +
        "      },300);" +
        "    }catch(e){}" +
        "  }" +
        "  setTimeout(tryMaxQuality,2000);" +
        "  setTimeout(tryMaxQuality,5000);" +
        "})();";
}
