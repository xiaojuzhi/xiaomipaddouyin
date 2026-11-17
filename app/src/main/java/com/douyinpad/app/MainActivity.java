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
 * 使用第一版验证可用的加载方式（桌面版 Chrome UA + 跳转拦截返回 true）
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

        // ===== 播放内核 / 解码优化（解决直播卡顿，最关键）=====
        // 1. 强制硬件加速：视频走 GPU 硬解 H.264，而不是 wasm/软解
        settings.setRenderPriority(WebSettings.RenderPriority.HIGH);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        // 2. 允许加载图片、关闭网络图片拦截，减少页面重排
        settings.setBlockNetworkImage(false);
        settings.setLoadsImagesAutomatically(true);

        // 3. 关闭过度滚动动画和离屏缓存，减轻渲染线程负担，把性能让给视频解码
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setDrawingCacheEnabled(false);

        // 4. 允许自动播放 + 无需用户手势即可播放（直播进入即播，不卡在等待点击）
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(true);

        // 5. 黑色背景避免白屏闪烁 + 减少合成开销
        webView.setBackgroundColor(android.graphics.Color.BLACK);

        // 6. 关闭 WebView 的调试端口（正式版不需要，去掉还能省一点开销）
        //    （已移除 setWebContentsDebuggingEnabled）

        // ===== UI 缩放：统一放大，让所有页面观感一致 =====
        // 直播列表页在 130 下观感正好；精选/推荐视频流是 5 列密集网格，同字号下偏小。
        // 这里把全局文字缩放到 150，让视频流页文字明显变大，同时直播页仍可接受。
        settings.setTextZoom(150);

        // 关键：用桌面版 Chrome UA（参考 douyin-tv 成熟方案）。
        // 抖音网页版对桌面 UA 返回内容流（可看视频），对移动 UA 才跳"下载App"引导页。
        settings.setUserAgentString(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        );

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // 注入统一的 UI 放大 CSS + 自动关闭登录弹窗
                view.evaluateJavascript(UI_SCALE_SCRIPT, null);
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

    /**
     * 统一 UI 缩放脚本：
     * 1. 注入 CSS，放大卡片标题/作者/互动数据等文字（子串匹配类名，不依赖会变的哈希类名）
     * 2. 自动关闭登录弹窗
     * 主体文字已由原生 setTextZoom(150) 负责，这里只放大局部小字，避免过度放大。
     */
    private static final String UI_SCALE_SCRIPT =
        "javascript:(function(){" +
        "  function injectCss(){" +
        "    var s=document.getElementById('__dp_ui');" +
        "    if(s){return;}" +
        "    s=document.createElement('style');" +
        "    s.id='__dp_ui';" +
        "    var css='html{font-size:110% !important;}' +" +
        // 放大视频卡片标题、作者名、互动数据（通用选择器，不依赖哈希类名）
        "      ' [class*=\"title\"], [class*=\"Title\"]{font-size:1.12em !important; line-height:1.4 !important;}' +" +
        "      ' [class*=\"author\"], [class*=\"Author\"], [class*=\"nickname\"], [class*=\"Nickname\"]{font-size:1.08em !important;}' +" +
        "      ' [class*=\"count\"], [class*=\"Count\"], [class*=\"like\"], [class*=\"Like\"], [class*=\"comment\"], [class*=\"Comment\"], [class*=\"statistic\"], [class*=\"Statistic\"]{font-size:1.08em !important;}' +" +
        "      ' [class*=\"tag\"], [class*=\"Tag\"], [class*=\"badge\"], [class*=\"Badge\"], [class*=\"duration\"], [class*=\"Duration\"]{font-size:1.05em !important;}' ;" +
        "    s.textContent=css;" +
        "    document.head.appendChild(s);" +
        "  }" +
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
        "    return false;" +
        "  }" +
        "  injectCss();" +
        "  var tries=0;" +
        "  var timer=setInterval(function(){" +
        "    tries++;" +
        "    try{ closeLogin(); }catch(e){}" +
        "    if(tries>=6){ clearInterval(timer); }" +
        "  },1500);" +
        "})();";
}
