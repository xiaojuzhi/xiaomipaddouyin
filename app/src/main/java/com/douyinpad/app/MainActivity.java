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
 * 使用第一版验证可用的加载方式（Pad 7 Ultra UA + 跳转拦截返回 true）
 */
public class MainActivity extends AppCompatActivity {

    private WebView webView;

    private static final String DOUYIN_HOME = "https://www.douyin.com/";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 系统级沉浸式全屏
        hideSystemBars();

        setContentView(R.layout.activity_main);
        webView = findViewById(R.id.webView);

        configureWebView();
        webView.loadUrl(DOUYIN_HOME);
    }

    /** 隐藏状态栏和导航栏（真正的平板全屏） */
    private void hideSystemBars() {
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        Window window = getWindow();
        // 让布局铺满整个屏幕（延伸到系统栏区域之下）
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

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

    /** 窗口重新获得焦点时再次强制全屏（防止系统栏重新显示） */
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemBars();
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

        // 硬件加速
        settings.setRenderPriority(WebSettings.RenderPriority.HIGH);
        settings.setBlockNetworkImage(false);
        settings.setLoadsImagesAutomatically(true);

        // 第一版验证可用的 UA（不要改成 Pixel 等，会导致白屏/下载页）
        settings.setUserAgentString(
            "Mozilla/5.0 (Linux; Android 14; Pad 7 Ultra) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        );

        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // 自动触发一次交互，激活抖音首页内容（相当于"点一下"）
                view.evaluateJavascript(AUTO_CLICK_SCRIPT, null);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // 第一版方式：loadUrl + 返回 true
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

    /** 自动点击脚本：优先点击「推荐」标签，找不到则点击中央激活首页 */
    private static final String AUTO_CLICK_SCRIPT =
        "javascript:(function(){" +
        "  function clickText(keywords){" +
        "    var all=document.querySelectorAll('a,div,span,li,p,button');" +
        "    for(var i=0;i<all.length;i++){" +
        "      var t=(all[i].innerText||all[i].textContent||'').trim();" +
        "      if(t==='推荐'||t==='精彩'||t==='关注'){" +
        "        all[i].click();" +
        "        return true;" +
        "      }" +
        "    }" +
        "    return false;" +
        "  }" +
        "  setTimeout(function(){" +
        "    try{" +
        "      if(!clickText()){" +
        "        var x=window.innerWidth/2, y=window.innerHeight/2;" +
        "        var el=document.elementFromPoint(x,y);" +
        "        if(el){el.click();}" +
        "      }" +
        "    }catch(e){}" +
        "  },2000);" +
        "})();";
}
