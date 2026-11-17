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

    // 抖音网页版精选页入口（jingxuan 是能直接播放内容流的正确入口，参考成熟开源项目 douyin-tv）
    private static final String DOUYIN_HOME = "https://www.douyin.com/jingxuan?from_nav=1";

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

        // 关键：用桌面版 Chrome UA（参考 douyin-tv 成熟方案）。
        // 抖音网页版对桌面 UA 返回内容流（可看视频），对移动 UA 才跳"下载App"引导页。
        settings.setUserAgentString(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
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
                String url = request.getUrl().toString();
                // 关键：拦截抖音的私有协议跳转（bytedance:// snssdk:// 等），
                // 否则 WebView 会报 net::ERR_UNKNOWN_URL_SCHEME 导致"网页无法打开"
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    view.loadUrl(url);
                    return true;
                }
                // 非 http/https 协议（bytedance:// 等）一律拦截，不加载
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

    /** 自动脚本：自动关闭登录弹窗，让内容流不被遮挡 */
    private static final String AUTO_CLICK_SCRIPT =
        "javascript:(function(){" +
        "  function closeLogin(){" +
        "    var closes=document.querySelectorAll('[class*=\"close\"],[class*=\"Close\"]," +
        "[aria-label*=\"close\"],[class*=\"modal\"] [class*=\"close\"]," +
        "[class*=\"dialog\"] [class*=\"close\"]');" +
        "    for(var i=0;i<closes.length;i++){" +
        "      var c=closes[i];" +
        "      var r=c.getBoundingClientRect();" +
        "      if(r.width>0 && r.height>0 && r.width<100 && r.height<100){" +
        "        c.click();" +
        "        return true;" +
        "      }" +
        "    }" +
        "    var masks=document.querySelectorAll('[class*=\"mask\"],[class*=\"Mask\"]," +
        "[class*=\"overlay\"],[class*=\"Overlay\"]');" +
        "    for(var j=0;j<masks.length;j++){" +
        "      var m=masks[j];" +
        "      var rm=m.getBoundingClientRect();" +
        "      if(rm.width>window.innerWidth*0.8 && rm.height>window.innerHeight*0.8){" +
        "        m.click();" +
        "        return true;" +
        "      }" +
        "    }" +
        "    return false;" +
        "  }" +
        "  var tries=0;" +
        "  var timer=setInterval(function(){" +
        "    tries++;" +
        "    try{ closeLogin(); }catch(e){}" +
        "    if(tries>=8){ clearInterval(timer); }" +
        "  },1500);" +
        "})();";
}
