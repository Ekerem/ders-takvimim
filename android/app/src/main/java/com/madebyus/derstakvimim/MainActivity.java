package com.madebyus.derstakvimim;

import android.app.Activity;
import android.content.Intent;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.CalendarContract;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * Uygulama internet varsa GitHub Pages'teki güncel sürümü açar,
 * yoksa APK'nın içine gömülü kopyayı (assets/index.html) açar.
 * Takvime erişim CalendarBridge üzerinden (JS'te window.AndroidApp).
 */
public class MainActivity extends Activity {

    private static final String LOCAL = "file:///android_asset/index.html";
    private WebView web;
    private CalendarBridge bridge;
    private boolean fellBack = false;
    private boolean observing = false;
    private final Handler main = new Handler(Looper.getMainLooper());

    // Takvim başka bir yerden (ör. Google Takvim uygulaması, eşitleme) değişince sayfaya haber ver.
    private final ContentObserver observer = new ContentObserver(main) {
        @Override
        public void onChange(boolean selfChange) {
            js("window.onCalendarChanged&&window.onCalendarChanged()");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        bridge = new CalendarBridge(this);
        web.addJavascriptInterface(bridge, "AndroidApp");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                String u = req.getUrl().toString();
                if (u.startsWith(LOCAL) || u.startsWith(BuildConfig.REMOTE_URL)) {
                    if (u.startsWith(BuildConfig.REMOTE_URL)) fellBack = false;
                    return false;
                }
                // APK indirme bağlantısı gibi dış adresler tarayıcıda açılır.
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, req.getUrl()));
                } catch (Exception ignored) {
                }
                return true;
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError err) {
                if (req.isForMainFrame()) fallback();
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest req, WebResourceResponse res) {
                if (req.isForMainFrame()) fallback();
            }
        });

        startObserving();

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState);
        } else {
            web.loadUrl(BuildConfig.REMOTE_URL + "?app=" + System.currentTimeMillis());
        }
    }

    private void startObserving() {
        if (observing || !bridge.granted()) return;
        try {
            getContentResolver().registerContentObserver(CalendarContract.Events.CONTENT_URI, true, observer);
            observing = true;
        } catch (Exception ignored) {
        }
    }

    private void js(String code) {
        main.post(() -> {
            if (web != null) web.evaluateJavascript(code, null);
        });
    }

    private void fallback() {
        if (!fellBack) {
            fellBack = true;
            web.loadUrl(LOCAL);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != CalendarBridge.PERMISSION_REQUEST) return;
        boolean ok = bridge.granted();
        if (ok) startObserving();
        js("window.onCalendarPermission&&window.onCalendarPermission(" + ok + ")");
    }

    @Override
    protected void onResume() {
        super.onResume();
        startObserving();
        js("window.onAppResume&&window.onAppResume()");
    }

    @Override
    protected void onDestroy() {
        if (observing) getContentResolver().unregisterContentObserver(observer);
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        // Açık bir pencere varsa önce onu kapat, yoksa uygulamadan çık.
        web.evaluateJavascript(
                "(function(){var b=document.querySelector('.backdrop:not([hidden])');if(b){b.hidden=true;return 1}return 0})()",
                result -> {
                    if ("1".equals(result)) return;
                    if (web.canGoBack()) web.goBack();
                    else finish();
                });
    }
}
