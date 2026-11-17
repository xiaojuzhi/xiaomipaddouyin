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
        // 开启 WebView 远程调试（仅 debug 排查用，正式可去掉）
        WebView.setWebContentsDebuggingEnabled(true);
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

        // ===== 播放内核优化（解决直播卡顿）=====
        // 关键：抖音直播/视频用 H.264/H.265 解码，强制走硬件加速
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(true);
        // 开启硬件加速视频解码（Android 5.0+ 默认开启，这里显式声明确保生效）
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        // 避免页面加载时的白屏闪烁
        webView.setBackgroundColor(android.graphics.Color.BLACK);
        // 关闭过度滚动动画，减少渲染负担
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        // 开启 WebView 的硬件加速离屏渲染（提升视频流畅度）
        webView.setDrawingCacheEnabled(false);

        // ===== UI 放大（适合平板触屏，字更大、按钮更好点）=====
        // 整体文字放大 30%
        settings.setTextZoom(130);

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
                // 根据页面类型区分 UI 缩放：
                // 精选/推荐视频流页面（5列密集网格、文字小）需要更强放大，
                // 直播页（live.douyin.com / 直播列表）保持 130 现状。
                // 关键：live.douyin.com 开头的直播页一定不走视频流增强，避免误伤直播页。
                boolean isLive = url.contains("live.douyin.com");
                boolean isVideoFeed = !isLive && (url.contains("jingxuan") || url.contains("recommend")
                        || url.contains("discover") || url.contains("/user/") || url.contains("/video/"));

                // 用原生 setTextZoom（可靠、一定生效）动态调整文字缩放
                view.getSettings().setTextZoom(isVideoFeed ? 160 : 130);

                // 视频流页面额外注入 CSS 放大标题/互动数据并放宽网格
                String script = isVideoFeed ? VIDEO_FEED_SCRIPT : AUTO_CLICK_SCRIPT;
                view.evaluateJavascript(script, null);
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
     * 精选/推荐视频流页面专用脚本：
     * 该页面是 5 列密集网格，卡片标题和互动数据文字太小。
     * 策略：整体 zoom 放大 + 放大卡片标题/作者/互动数据字号 + 放大缩略图角标，
     * 让观感接近直播列表页（4列大卡片、字更大更清楚）。
     */
    private static final String VIDEO_FEED_SCRIPT =
        "javascript:(function(){" +
        "  function zoomFeed(){" +
        "    var s=document.getElementById('__dp_feed');" +
        "    if(s){return;}" +
        "    s=document.createElement('style');" +
        "    s.id='__dp_feed';" +
        // 注意：主体文字放大已由原生 setTextZoom(160) 负责，这里只放大标题/互动数据等局部元素，
        // 不再用 body{zoom}，避免和 setTextZoom 叠加导致过度放大。
        "    var css='html{font-size:115% !important;}' +" +
        // 放大视频卡片标题、作者名、互动数据（通用选择器，不依赖哈希类名）
        "      ' [class*=\"title\"], [class*=\"Title\"]{font-size:1.15em !important; line-height:1.4 !important;}' +" +
        "      ' [class*=\"author\"], [class*=\"Author\"], [class*=\"nickname\"], [class*=\"Nickname\"], [class*=\"name\"]{font-size:1.1em !important;}' +" +
        "      ' [class*=\"count\"], [class*=\"Count\"], [class*=\"like\"], [class*=\"Like\"], [class*=\"comment\"], [class*=\"Comment\"], [class*=\"statistic\"], [class*=\"Statistic\"], [class*=\"data\"]{font-size:1.1em !important;}' +" +
        // 缩略图上的角标（时长、播放量）也放大
        "      ' [class*=\"tag\"], [class*=\"Tag\"], [class*=\"badge\"], [class*=\"Badge\"], [class*=\"duration\"], [class*=\"Duration\"], [class*=\"hot\"], [class*=\"Hot\"]{font-size:1.08em !important;}' ;" +
        "    s.textContent=css;" +
        "    document.head.appendChild(s);" +
        "  }" +
        "  zoomFeed();" +
        "})();";

    /** 自动脚本：注入UI放大CSS + 自动关闭登录弹窗 */
    private static final String AUTO_CLICK_SCRIPT =
        "javascript:(function(){" +
        "  function zoomUI(){" +
        "    var s=document.getElementById('__dp_zoom');" +
        "    if(s){return;}" +
        "    s=document.createElement('style');" +
        "    s.id='__dp_zoom';" +
        "    s.textContent='html{font-size:120% !important;}' +" +
        "      ' body{zoom:1.1;}' +" +
        "      ' [class*=\"video\"], [class*=\"card\"], [class*=\"item\"]{min-height:auto;}' ;" +
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
        "  zoomUI();" +
        "  var tries=0;" +
        "  var timer=setInterval(function(){" +
        "    tries++;" +
        "    try{ closeLogin(); }catch(e){}" +
        "    if(tries>=8){ clearInterval(timer); }" +
        "  },1500);" +
        "})();";
}
