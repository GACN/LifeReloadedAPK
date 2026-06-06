package com.gaclove.lifereloadedapk;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.JavascriptInterface;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.view.Window;
import android.view.WindowManager;
import android.view.View;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {
    private WebView webView;
    private Handler mainHandler;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        enterFullscreen();
        mainHandler = new Handler(Looper.getMainLooper());
        webView = new WebView(this);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        setContentView(webView);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        if (Build.VERSION.SDK_INT >= 21) s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        webView.setWebViewClient(new WebViewClient());
        webView.setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener() {
            @Override public void onSystemUiVisibilityChange(int visibility) { enterFullscreen(); }
        });
        webView.addJavascriptInterface(new NativeBridge(), "LifeNative");
        webView.loadUrl("file:///android_asset/index.html");
    }

    private void enterFullscreen() {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (Build.VERSION.SDK_INT >= 19) {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            );
        }
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) enterFullscreen();
    }

    @Override protected void onResume() {
        super.onResume();
        enterFullscreen();
    }

    public class NativeBridge {
        private SharedPreferences prefs() { return getSharedPreferences("life_reloaded_apk", MODE_PRIVATE); }
        @JavascriptInterface public String getItem(String key) { return prefs().getString(key, ""); }
        @JavascriptInterface public void setItem(String key, String value) { prefs().edit().putString(key, value).apply(); }
        @JavascriptInterface public void removeItem(String key) { prefs().edit().remove(key).apply(); }
        @JavascriptInterface public String listKeys() { return prefs().getAll().keySet().toString(); }
        @JavascriptInterface public void setOrientation(String mode) {
            if ("landscape".equals(mode)) setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            else if ("portrait".equals(mode)) setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            else setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }
        @JavascriptInterface public void apiChat(final String requestId, final String baseUrl, final String apiKey, final String model, final String payloadJson) {
            new Thread(new Runnable(){ public void run(){
                String out=""; boolean ok=false;
                try {
                    String base = baseUrl == null ? "" : baseUrl.trim();
                    if (base.endsWith("/")) base = base.substring(0, base.length()-1);
                    URL url = new URL(base + "/chat/completions");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    if (base.length() == 0) throw new Exception("Base URL 为空");
                    if (apiKey == null || apiKey.trim().length() == 0) throw new Exception("API Key 为空");
                    if (payloadJson == null || payloadJson.length() == 0) throw new Exception("请求体为空");
                    conn.setConnectTimeout(15000); conn.setReadTimeout(60000);
                    conn.setRequestMethod("POST"); conn.setDoOutput(true);
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                    OutputStream os = conn.getOutputStream(); os.write(payloadJson.getBytes("UTF-8")); os.close();
                    int code = conn.getResponseCode();
                    InputStream is = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
                    BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                    StringBuilder sb = new StringBuilder(); String line;
                    while((line=br.readLine())!=null) sb.append(line).append('\n');
                    br.close(); out = sb.toString(); ok = code >= 200 && code < 300;
                    if(!ok) out = "HTTP " + code + ": " + out;
                } catch(Exception e) { out = e.toString(); }
                final String js = "window.NativeAPI && window.NativeAPI._resolve(" + quote(requestId) + "," + ok + "," + quote(out) + ")";
                mainHandler.post(new Runnable(){ public void run(){ webView.evaluateJavascript(js, null); }});
            }}).start();
        }
        private String quote(String s) {
            if (s == null) s = "";
            return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "") + "\"";
        }
    }
}
