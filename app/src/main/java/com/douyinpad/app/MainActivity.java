package com.douyinpad.app;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoRuntimeSettings;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.geckoview.GeckoView;

/**
 * 抖音直播平板版 - GeckoView 火狐内核（独立完整内核，解决系统 WebView 直播卡顿）
 * 纯沉浸式全屏，桌面版 Chrome UA + 私有协议拦截 + 资源拦截
 */
public class MainActivity extends AppCompatActivity {

    private GeckoView geckoView;
    private GeckoSession session;
    private GeckoRuntime runtime;
    private boolean canGoBack = false;

    // 抖音网页版精选页入口
    private static final String DOUYIN_HOME = "https://www.douyin.com/jingxuan?from_nav=1";

    // 桌面版 Chrome UA（抖音对桌面 UA 返回内容流，对移动 UA 跳下载页）
    private static final String DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 系统级沉浸式全屏
        hideSystemBars();

        setContentView(R.layout.activity_main);
        geckoView = findViewById(R.id.geckoView);

        // 初始化 GeckoRuntime（全局共享，只需创建一次）
        runtime = GeckoRuntime.create(this);
        GeckoRuntimeSettings runtimeSettings = runtime.getSettings();
        // 启用 JavaScript
        runtimeSettings.setJavaScriptEnabled(true);
        // 允许自动播放（直播进入即播）
        runtimeSettings.setAutoplayDefault(GeckoRuntimeSettings.AUTOPLAY_DEFAULT_ALLOWED);
        // 关闭远程调试（正式版不需要）
        runtimeSettings.setRemoteDebuggingEnabled(false);

        // 创建会话并配置
        session = new GeckoSession();
        GeckoSessionSettings sessionSettings = session.getSettings();
        // 桌面版 Chrome UA
        sessionSettings.setUserAgentOverride(DESKTOP_UA);
        // 桌面模式 viewport
        sessionSettings.setDisplayMode(GeckoSessionSettings.DISPLAY_MODE_BROWSER);

        // 导航委托：拦截私有协议
        session.setNavigationDelegate(new GeckoSession.NavigationDelegate() {
            @Override
            public @Nullable GeckoResult<AllowOrDeny> onLoadRequest(
                    @NonNull GeckoSession session,
                    @NonNull LoadRequest request) {
                String url = request.uri;
                if (url == null) {
                    return GeckoResult.fromValue(AllowOrDeny.ALLOW);
                }
                // 拦截抖音私有协议（bytedance:// snssdk:// 等），否则报"网页无法打开"
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    return GeckoResult.fromValue(AllowOrDeny.DENY);
                }
                return GeckoResult.fromValue(AllowOrDeny.ALLOW);
            }

            @Override
            public void onCanGoBack(@NonNull GeckoSession session, boolean canGoBack) {
                MainActivity.this.canGoBack = canGoBack;
            }
        });

        // 页面加载完成注入脚本
        session.setProgressDelegate(new GeckoSession.ProgressDelegate() {
            @Override
            public void onPageStop(@NonNull GeckoSession session, boolean success) {
                if (success) {
                    injectScripts();
                }
            }
        });

        // 绑定到视图并加载
        geckoView.setSession(session);
        session.open(runtime);
        session.loadUri(DOUYIN_HOME);
    }

    /** 注入性能优化脚本（GeckoView 用 javascript: URI 方式注入） */
    private void injectScripts() {
        try {
            session.loadUri("javascript:" + PERF_SCRIPT);
        } catch (Exception ignored) {}
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

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemBars();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && canGoBack && session != null) {
            session.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (session != null) {
            session.close();
            session = null;
        }
        if (runtime != null) {
            runtime.shutdown();
            runtime = null;
        }
    }

    /**
     * 性能优化脚本（解决卡顿）：
     * 1. 定期清理远离视口的视频卡片 DOM，防止内存堆积
     * 2. 禁用礼物动画/入场特效/弹幕飘屏等 GPU 开销大的动画
     * 3. 视频仅预加载元数据，避免多个视频同时解码
     */
    private static final String PERF_SCRIPT =
        "(function(){" +
        "  if(window.__dp_perf) return; window.__dp_perf=true;" +
        "  function killAnimations(){" +
        "    var s=document.getElementById('__dp_perf_css');" +
        "    if(!s){" +
        "      s=document.createElement('style'); s.id='__dp_perf_css';" +
        "      s.textContent='" +
        "        [class*=\"gift\"], [class*=\"Gift\"], [class*=\"effect\"], [class*=\"Effect\"]," +
        "        [class*=\"particle\"], [class*=\"Particle\"], [class*=\"animation\"], [class*=\"Animation\"]" +
        "        { animation:none !important; transition:none !important; }" +
        "        video { max-width:100%; height:auto; }' ;" +
        "      document.head.appendChild(s);" +
        "    }" +
        "  }" +
        "  function optimizeVideos(){" +
        "    document.querySelectorAll('video').forEach(function(v){" +
        "      if(v.getAttribute('preload')!=='metadata') v.setAttribute('preload','metadata');" +
        "    });" +
        "  }" +
        "  var lastClean=0;" +
        "  function cleanupDom(){" +
        "    var now=Date.now();" +
        "    if(now-lastClean<10000) return; lastClean=now;" +
        "    try{" +
        "      var cards=document.querySelectorAll('[class*=\"feed\"], [class*=\"Feed\"], [class*=\"waterfall\"], [class*=\"Waterfall\"]');" +
        "      var limit=40;" +
        "      if(cards.length>limit){" +
        "        for(var i=0;i<cards.length-limit;i++){" +
        "          var el=cards[i]; var r=el.getBoundingClientRect();" +
        "          if(r.bottom<-2000 || r.top>window.innerHeight+2000){ el.remove(); }" +
        "        }" +
        "      }" +
        "    }catch(e){}" +
        "  }" +
        "  killAnimations(); optimizeVideos();" +
        "  setInterval(function(){ killAnimations(); optimizeVideos(); cleanupDom(); },5000);" +
        "})();";
}
