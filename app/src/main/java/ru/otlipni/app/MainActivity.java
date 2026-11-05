package ru.otlipni.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    private WebView web;
    private boolean loaded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Notif.ensureChannels(this);
        Checks.schedule(this);

        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#0D1024"));
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        // звуки из assets читаются скриптом (для быстрых щелчков рулетки)
        s.setAllowFileAccessFromFileURLs(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setTextZoom(100);
        web.addJavascriptInterface(new Bridge(this), "Native");
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if ("file".equals(u.getScheme())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) { }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) { loaded = true; }
        });
        setContentView(web);
        web.loadUrl("file:///android_asset/www/index.html");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Checks.setForeground(this, true);
        if (Checks.sp(this).getBoolean("onboarded", false)) WatchService.ensure(this);
        // при возвращении в приложение — посчитать, сколько питомцы спали
        if (web != null && loaded) web.evaluateJavascript("window.onNativeResume&&window.onNativeResume()", null);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Checks.setForeground(this, false);
    }

    @Override
    protected void onDestroy() {
        if (web != null) { web.destroy(); web = null; }
        super.onDestroy();
    }
}
