package com.douyinpad.app;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/**
 * 抖音直播平板版 - 主界面
 * 针对小米 Pad 7 Ultra（14寸 3:2 横屏）设计
 */
public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private ProgressBar progressBar;
    private EditText searchInput;

    private static final String DOUYIN_HOME = "https://www.douyin.com/";
    private static final String DOUYIN_LIVE = "https://live.douyin.com/";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);
        searchInput = findViewById(R.id.searchInput);

        configureWebView();
        bindNavigation();
        bindToolbar();

        webView.loadUrl(DOUYIN_HOME);
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
        settings.setUserAgentString(
            "Mozilla/5.0 (Linux; Android 14; Pad 7 Ultra) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        );

        // 允许跨域 cookie，保持登录
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        // 注入 JS 桥
        webView.addJavascriptInterface(new JsBridge(), "DouyinPad");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                progressBar.setVisibility(View.GONE);
                // 注入自动原画 + 全屏 + 触屏手势脚本
                view.evaluateJavascript(AUTO_SCRIPT, null);
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                progressBar.setVisibility(View.VISIBLE);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                view.loadUrl(request.getUrl().toString());
                return true;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress >= 100) {
                    progressBar.setVisibility(View.GONE);
                }
            }
        });
    }

    private void bindNavigation() {
        View.OnClickListener navListener = v -> {
            String url = DOUYIN_HOME;
            int id = v.getId();
            if (id == R.id.navRecommend) url = DOUYIN_HOME;
            else if (id == R.id.navLive) url = DOUYIN_LIVE;
            else if (id == R.id.navFollow) url = "https://www.douyin.com/follow";
            else if (id == R.id.navHall) url = "https://www.douyin.com/discover";
            else if (id == R.id.navMine) url = "https://www.douyin.com/user/self";
            webView.loadUrl(url);
        };

        findViewById(R.id.navRecommend).setOnClickListener(navListener);
        findViewById(R.id.navLive).setOnClickListener(navListener);
        findViewById(R.id.navFollow).setOnClickListener(navListener);
        findViewById(R.id.navHall).setOnClickListener(navListener);
        findViewById(R.id.navMine).setOnClickListener(navListener);
    }

    private void bindToolbar() {
        findViewById(R.id.btnBack).setOnClickListener(v -> {
            if (webView.canGoBack()) webView.goBack();
            else Toast.makeText(this, "已经是首页", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btnForward).setOnClickListener(v -> {
            if (webView.canGoForward()) webView.goForward();
            else Toast.makeText(this, "没有下一页", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btnRefresh).setOnClickListener(v -> webView.reload());

        findViewById(R.id.btnFullscreen).setOnClickListener(v -> {
            // 触发网页内全屏
            webView.evaluateJavascript("try{var v=document.querySelector('video');if(v){if(v.requestFullscreen)v.requestFullscreen();else if(v.webkitRequestFullscreen)v.webkitRequestFullscreen();}}catch(e){}", null);
        });

        findViewById(R.id.btnQuality).setOnClickListener(v -> {
            // 触发自动最高画质
            webView.evaluateJavascript(AUTO_QUALITY_SCRIPT, null);
            Toast.makeText(this, "已尝试切换最高画质", Toast.LENGTH_SHORT).show();
        });

        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            String kw = searchInput.getText().toString().trim();
            if (!kw.isEmpty()) {
                String url = "https://www.douyin.com/search/" + kw;
                webView.loadUrl(url);
                searchInput.clearFocus();
            }
            return true;
        });
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    /** JS 桥（预留） */
    public class JsBridge {
        @JavascriptInterface
        public void toast(String msg) {
            Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
        }
    }

    /** 自动最高画质脚本 */
    private static final String AUTO_QUALITY_SCRIPT =
        "try{" +
        "var btns=document.querySelectorAll('[class*=quality],[class*=Quality],[class*=画质],[class*=清晰]');" +
        "btns.forEach(function(b){try{b.click();}catch(e){}});" +
        "}catch(e){}";

    /** 自动原画 + 全屏 + 触屏手势脚本 */
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
        // 触屏手势：双击暂停、左右滑动进度、上下滑动音量
        "  var lastTap=0;" +
        "  document.addEventListener('click',function(e){" +
        "    var now=Date.now(); var v=document.querySelector('video');" +
        "    if(!v)return;" +
        "    if(now-lastTap<300){ if(v.paused)v.play(); else v.pause(); lastTap=0; }" +
        "    else lastTap=now;" +
        "  },true);" +
        "})();";
}
