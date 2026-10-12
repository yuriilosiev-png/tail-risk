package com.options.tailrisk;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.messaging.FirebaseMessaging;

import java.lang.ref.WeakReference;

/**
 * Оболочка «Хвостового риска»: WebView с GitHub Pages + мост FCM для пушей.
 * Логика та же, что в Crypto Options (P2): токен FCM передаётся в страницу
 * через window.onFcmToken(token), страница регистрирует его на Cloudflare Worker.
 */
public class MainActivity extends Activity {

    private static final String TAG = "TailRiskMain";
    private static final int REQ_POST_NOTIFICATIONS = 1001;
    private static final String APP_URL = "https://yuriilosiev-png.github.io/tail-risk/index.html";
    // Цвет фона приложения (--bg) — под него красим системные панели
    private static final int BAR_COLOR = Color.parseColor("#0E1A1D");

    private WebView webView;
    private static WeakReference<MainActivity> sInstance;
    // Токен, полученный до загрузки страницы, — передаём в onPageFinished
    private volatile String pendingFcmToken = null;

    public static MainActivity getInstance() {
        return sInstance != null ? sInstance.get() : null;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sInstance = new WeakReference<>(this);

        // Системные панели в цвет приложения, без полноэкранного режима:
        // нижняя навигация приложения не прячется под жесты системы
        getWindow().setStatusBarColor(BAR_COLOR);
        getWindow().setNavigationBarColor(BAR_COLOR);

        createNotificationChannel();
        requestNotificationPermission();

        webView = new WebView(this);
        webView.setBackgroundColor(BAR_COLOR);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        // Страница грузится по https — доступ к файлам устройства не нужен
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setBuiltInZoomControls(false);
        s.setSupportZoom(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setUserAgentString(s.getUserAgentString() + " TailRiskApp/1.0");

        webView.addJavascriptInterface(new AndroidBridge(this), "AndroidBridge");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                // Невалидный сертификат — отказ (защита от подмены соединения)
                handler.cancel();
            }
            @Override
            public void onPageFinished(WebView view, String url) {
                if (pendingFcmToken != null) injectFcmToken(pendingFcmToken);
            }
        });
        webView.setWebChromeClient(new WebChromeClient());
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        webView.loadUrl(APP_URL);

        fetchFcmToken();
    }

    // Токен из кеша (быстро) + актуальный у Firebase (может ротироваться)
    private void fetchFcmToken() {
        SharedPreferences prefs = getSharedPreferences(TailRiskMessagingService.PREFS_NAME, Context.MODE_PRIVATE);
        String cached = prefs.getString(TailRiskMessagingService.KEY_TOKEN, null);
        if (cached != null) {
            pendingFcmToken = cached;
            injectFcmToken(cached);
        }
        FirebaseMessaging.getInstance().getToken()
            .addOnSuccessListener(token -> {
                if (token == null) return;
                pendingFcmToken = token;
                getSharedPreferences(TailRiskMessagingService.PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putString(TailRiskMessagingService.KEY_TOKEN, token).apply();
                injectFcmToken(token);
            })
            .addOnFailureListener(e -> Log.w(TAG, "FCM token fetch failed: " + e.getMessage()));
    }

    // Передаёт токен в JS: window.onFcmToken(token)
    public void injectFcmToken(String token) {
        if (token == null || webView == null) return;
        final String safe = token.replace("\\", "\\\\").replace("'", "\\'");
        webView.post(() -> webView.evaluateJavascript(
            "window.__fcmToken='" + safe + "';" +
            "if(typeof window.onFcmToken==='function'){window.onFcmToken('" + safe + "');}", null));
    }

    // JS-мост: AndroidBridge.requestFcmToken() вызывается кнопкой «Включить уведомления»
    public static class AndroidBridge {
        private final WeakReference<MainActivity> ref;
        public AndroidBridge(MainActivity a) { ref = new WeakReference<>(a); }

        @JavascriptInterface
        public void requestFcmToken() {
            final MainActivity a = ref.get();
            if (a == null) return;
            a.runOnUiThread(a::requestNotificationPermission);
            FirebaseMessaging.getInstance().getToken()
                .addOnSuccessListener(token -> { if (token != null) a.injectFcmToken(token); })
                .addOnFailureListener(e -> {
                    final String msg = String.valueOf(e.getMessage()).replace("'", " ");
                    if (a.webView != null) a.webView.post(() -> a.webView.evaluateJavascript(
                        "if(typeof window.onFcmTokenError==='function'){window.onFcmTokenError('" + msg + "');}", null));
                });
        }

        @JavascriptInterface
        public String getVersion() { return "1.0"; }
    }

    // Android 13+: без этого запроса система не покажет ни одного уведомления
    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                   != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                new String[]{ Manifest.permission.POST_NOTIFICATIONS }, REQ_POST_NOTIFICATIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_POST_NOTIFICATIONS && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            fetchFcmToken();
        }
    }

    // Параметры канала должны совпадать с TailRiskMessagingService:
    // созданный канал Android менять из кода не даёт
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(TailRiskMessagingService.buildChannel());
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onResume() { super.onResume(); if (webView != null) webView.onResume(); }

    @Override
    protected void onPause() { super.onPause(); if (webView != null) webView.onPause(); }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        sInstance = null;
        if (webView != null) webView.destroy();
    }
}
