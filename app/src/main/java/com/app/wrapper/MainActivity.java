package com.app.wrapper;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Message;
import android.util.Base64;
import android.webkit.*;
import androidx.webkit.WebViewAssetLoader;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private static final String HOST = "appassets.androidplatform.net";
    private static final int REQ_FILE = 1;
    private WebView web;
    private ValueCallback<Uri[]> filePicker;
    private final ExecutorService pool = Executors.newFixedThreadPool(6);

    class Bridge {
        @JavascriptInterface public String server() { return BuildConfig.APP_URL; }

        @JavascriptInterface public void request(String id, String method, String url, String headers, String bodyB64) {
            pool.execute(() -> {
                int status = -1; String hb = "", bb = "";
                try {
                    if (!url.startsWith(BuildConfig.APP_URL)) throw new IOException("blocked");
                    HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                    c.setConnectTimeout(20000); c.setReadTimeout(60000);
                    c.setRequestMethod(method);
                    JSONObject h = new JSONObject(headers);
                    for (Iterator<String> it = h.keys(); it.hasNext();) {
                        String k = it.next();
                        if (k.equalsIgnoreCase("content-length") || k.equalsIgnoreCase("host")) continue;
                        c.setRequestProperty(k, h.getString(k));
                    }
                    byte[] body = Base64.decode(bodyB64, Base64.DEFAULT);
                    if (body.length > 0 && !method.equals("GET") && !method.equals("HEAD")) {
                        c.setDoOutput(true);
                        try (OutputStream o = c.getOutputStream()) { o.write(body); }
                    }
                    status = c.getResponseCode();
                    InputStream in = status >= 400 ? c.getErrorStream() : c.getInputStream();
                    ByteArrayOutputStream bo = new ByteArrayOutputStream();
                    if (in != null) { byte[] buf = new byte[16384]; int r; while ((r = in.read(buf)) > 0) bo.write(buf, 0, r); in.close(); }
                    bb = Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP);
                    JSONObject rh = new JSONObject();
                    for (Map.Entry<String, List<String>> e : c.getHeaderFields().entrySet()) {
                        String k = e.getKey();
                        if (k == null || k.equalsIgnoreCase("content-encoding") || k.equalsIgnoreCase("content-length")) continue;
                        rh.put(k, android.text.TextUtils.join(", ", e.getValue()));
                    }
                    hb = Base64.encodeToString(rh.toString().getBytes("UTF-8"), Base64.NO_WRAP);
                } catch (Exception e) { status = -1; }
                final String js = "window.__nb('" + id + "'," + status + ",'" + hb + "','" + bb + "')";
                web.post(() -> web.evaluateJavascript(js, null));
            });
        }
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setSupportMultipleWindows(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        web.addJavascriptInterface(new Bridge(), "Native");

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
            .setDomain(HOST)
            .addPathHandler("/", new WebViewAssetLoader.AssetsPathHandler(this))
            .build();

        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                return loader.shouldInterceptRequest(r.getUrl());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if (HOST.equals(u.getHost())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception e) {}
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (filePicker != null) filePicker.onReceiveValue(null);
                filePicker = cb;
                try { startActivityForResult(p.createIntent(), REQ_FILE); }
                catch (Exception e) { filePicker = null; return false; }
                return true;
            }
            @Override public boolean onCreateWindow(WebView v, boolean d, boolean g, Message m) {
                WebView tmp = new WebView(MainActivity.this);
                tmp.setWebViewClient(new WebViewClient() {
                    @Override public boolean shouldOverrideUrlLoading(WebView w, WebResourceRequest r) {
                        try { startActivity(new Intent(Intent.ACTION_VIEW, r.getUrl())); } catch (Exception e) {}
                        return true;
                    }
                });
                ((WebView.WebViewTransport) m.obj).setWebView(tmp);
                m.sendToTarget();
                return true;
            }
        });
        web.setDownloadListener((url, ua, cd, mime, len) ->
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))));
        web.loadUrl("https://" + HOST + "/index.html");
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        if (req == REQ_FILE && filePicker != null) {
            filePicker.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data));
            filePicker = null;
        } else super.onActivityResult(req, res, data);
    }

    @Override public void onBackPressed() {
        if (web.canGoBack()) web.goBack(); else super.onBackPressed();
    }
}
