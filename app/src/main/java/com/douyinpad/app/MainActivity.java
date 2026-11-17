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

        // 7. 关闭滚动条绘制开销，减少滚动时的合成负担
        webView.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);

        // ===== UI 缩放 =====
        // 直播列表页在 130 下观感正好（文字相对字号，受 setTextZoom 影响），保持 130。
        // 精选/推荐视频流的卡片标题是固定像素字号，setTextZoom 对其无效，
        // 需要在 onPageFinished 里根据 URL 注入更强的 CSS 放大（见下）。
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
                // 根据页面类型注入不同脚本：
                // - 精选/推荐视频流页（密集网格、文字小）→ 强 CSS 放大
                // - 直播页（live.douyin.com）→ 保持原样，只做登录弹窗关闭
                boolean isLive = url.contains("live.douyin.com");
                boolean isVideoFeed = !isLive && (url.contains("jingxuan") || url.contains("recommend")
                        || url.contains("discover") || url.contains("/user/") || url.contains("/video/")
                        || url.endsWith("douyin.com") || url.endsWith("douyin.com/"));
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
     * 精选/推荐视频流专用脚本：强 CSS 放大 + 持续注入。
     * 精选页视频卡片标题是固定像素字号（setTextZoom 无效），必须用 CSS 强制放大。
     * 抖音是 SPA，页面会反复重绘覆盖注入的 style，所以用 MutationObserver + 定时器持续兜底。
     */
    private static final String VIDEO_FEED_SCRIPT =
        "javascript:(function(){" +
        "  var CSS_ID='__dp_feed_zoom';" +
        "  function buildCss(){" +
        "    return 'html{font-size:120% !important;}' +" +
        // 用 zoom 整体放大（对固定 px 也有效）
        "      ' body{zoom:1.3 !important;}' +" +
        // 放大卡片标题/作者/互动数据（子串匹配类名）
        "      ' [class*=\"title\"], [class*=\"Title\"]{font-size:1.25em !important; line-height:1.45 !important;}' +" +
        "      ' [class*=\"author\"], [class*=\"Author\"], [class*=\"nickname\"], [class*=\"Nickname\"], [class*=\"name\"]{font-size:1.2em !important;}' +" +
        "      ' [class*=\"count\"], [class*=\"Count\"], [class*=\"like\"], [class*=\"Like\"], [class*=\"comment\"], [class*=\"Comment\"], [class*=\"statistic\"], [class*=\"Statistic\"], [class*=\"data\"]{font-size:1.2em !important;}' +" +
        "      ' [class*=\"tag\"], [class*=\"Tag\"], [class*=\"badge\"], [class*=\"Badge\"], [class*=\"duration\"], [class*=\"Duration\"]{font-size:1.15em !important;}' ;" +
        "  }" +
        "  function ensureStyle(){" +
        "    var s=document.getElementById(CSS_ID);" +
        "    if(!s){ s=document.createElement('style'); s.id=CSS_ID; document.head.appendChild(s); }" +
        "    if(s.textContent.indexOf('zoom:1.3')<0){ s.textContent=buildCss(); }" +
        "  }" +
        "  function closeLogin(){" +
        "    var closes=document.querySelectorAll('[class*=\"close\"],[class*=\"Close\"]," +
        "[aria-label*=\"close\"],[class*=\"modal\"] [class*=\"close\"]," +
        "[class*=\"dialog\"] [class*=\"close\"]');" +
        "    for(var i=0;i<closes.length;i++){" +
        "      var c=closes[i]; var r=c.getBoundingClientRect();" +
        "      if(r.width>0 && r.height>0 && r.width<100 && r.height<100){ c.click(); return true; }" +
        "    }" +
        "    return false;" +
        "  }" +
        "  ensureStyle();" +
        "  // MutationObserver 监听 DOM 变化，防止 SPA 重绘覆盖注入的 style" +
        "  try{" +
        "    var mo=new MutationObserver(function(){ ensureStyle(); });" +
        "    mo.observe(document.head, {childList:true});" +
        "  }catch(e){}" +
        "  // 定时兜底：持续确保样式存在 + 关登录弹窗" +
        "  var t=0;" +
        "  var timer=setInterval(function(){" +
        "    ensureStyle(); try{ closeLogin(); }catch(e){}" +
        "    if(++t>20){ clearInterval(timer); }" +
        "  },2000);" +
        "})();";

    /**
     * 直播页脚本：保持 UI 原样（setTextZoom 130 已足够），只自动关闭登录弹窗。
     */
    private static final String AUTO_CLICK_SCRIPT =
        "javascript:(function(){" +
        "  function closeLogin(){" +
        "    var closes=document.querySelectorAll('[class*=\"close\"],[class*=\"Close\"]," +
        "[aria-label*=\"close\"],[class*=\"modal\"] [class*=\"close\"]," +
        "[class*=\"dialog\"] [class*=\"close\"]');" +
        "    for(var i=0;i<closes.length;i++){" +
        "      var c=closes[i]; var r=c.getBoundingClientRect();" +
        "      if(r.width>0 && r.height>0 && r.width<100 && r.height<100){ c.click(); return true; }" +
        "    }" +
        "    return false;" +
        "  }" +
        "  var t=0;" +
        "  var timer=setInterval(function(){" +
        "    try{ closeLogin(); }catch(e){}" +
        "    if(++t>8){ clearInterval(timer); }" +
        "  },1500);" +
        "})();";
}
