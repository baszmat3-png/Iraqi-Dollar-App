package com.app.wrapper;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Message;
import android.util.Base64;
import android.webkit.*;
import androidx.webkit.WebViewAssetLoader;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.credentials.*;
import androidx.credentials.exceptions.*;
import com.google.android.libraries.identity.googleid.*;
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

        @JavascriptInterface public void googleSignIn(String clientId) {
            runOnUiThread(() -> {
                try {
                    CredentialManager cm = CredentialManager.create(MainActivity.this);
                    GetCredentialRequest rq = new GetCredentialRequest.Builder()
                        .addCredentialOption(new GetSignInWithGoogleOption.Builder(clientId).build()).build();
                    cm.getCredentialAsync(MainActivity.this, rq, null, ContextCompat.getMainExecutor(MainActivity.this),
                        new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                            @Override public void onResult(GetCredentialResponse r) {
                                Credential c = r.getCredential();
                                if (c instanceof CustomCredential && GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(c.getType())) {
                                    String t = GoogleIdTokenCredential.createFrom(((CustomCredential) c).getData()).getIdToken();
                                    googleResult(t, "");
                                } else googleResult("", "unsupported");
                            }
                            @Override public void onError(GetCredentialException e) {
                                googleResult("", e instanceof GetCredentialCancellationException ? "cancel"
                                    : (e.getType() + " " + (e.getMessage() == null ? "" : e.getMessage())));
                            }
                        });
                } catch (Exception e) { googleResult("", String.valueOf(e.getMessage())); }
            });
        }

        @JavascriptInterface public void sseStart(String id, String url) {
            new Thread(() -> {
                try {
                    if (!url.startsWith(BuildConfig.APP_URL)) throw new IOException("blocked");
                    HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                    c.setConnectTimeout(20000); c.setReadTimeout(0);
                    c.setRequestProperty("Accept", "text/event-stream");
                    c.setRequestProperty("Cache-Control", "no-cache");
                    streams.put(id, c);
                    if (c.getResponseCode() != 200) throw new IOException("status");
                    BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
                    String line, ev = "message"; StringBuilder data = new StringBuilder();
                    while ((line = br.readLine()) != null) {
                        if (line.isEmpty()) {
                            if (data.length() > 0) emit(id, ev, data.toString());
                            ev = "message"; data.setLength(0);
                        } else if (line.startsWith("event:")) ev = line.substring(6).trim();
                        else if (line.startsWith("data:")) { if (data.length() > 0) data.append('\n'); data.append(line.substring(5).replaceFirst("^ ", "")); }
                    }
                } catch (Exception e) { }
                if (streams.remove(id) != null) emit(id, "error", "");
            }).start();
        }

        @JavascriptInterface public void sseClose(String id) {
            HttpURLConnection c = streams.remove(id);
            if (c != null) new Thread(c::disconnect).start();
        }

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

    private final LinkedHashMap<String, Object[]> imgCache = new LinkedHashMap<>(64, 0.75f, true);
    private int imgCacheBytes = 0;

    private WebResourceResponse proxyGet(String url) {
        boolean cacheable = url.contains("avatar") && url.contains("v=");
        if (cacheable) synchronized (imgCache) {
            Object[] hit = imgCache.get(url);
            if (hit != null) return new WebResourceResponse((String) hit[0], null, new ByteArrayInputStream((byte[]) hit[1]));
        }
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(20000); c.setReadTimeout(30000);
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            String ct = c.getContentType();
            String mime = ct == null ? "application/octet-stream" : ct.split(";")[0].trim();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            if (in != null) { byte[] buf = new byte[16384]; int n; while ((n = in.read(buf)) > 0) bo.write(buf, 0, n); in.close(); }
            byte[] data = bo.toByteArray();
            if (cacheable && code == 200) synchronized (imgCache) {
                imgCache.put(url, new Object[]{mime, data});
                imgCacheBytes += data.length;
                Iterator<Map.Entry<String, Object[]>> it = imgCache.entrySet().iterator();
                while (imgCacheBytes > 24_000_000 && it.hasNext()) { imgCacheBytes -= ((byte[]) it.next().getValue()[1]).length; it.remove(); }
            }
            WebResourceResponse r = new WebResourceResponse(mime, "UTF-8", new ByteArrayInputStream(data));
            r.setStatusCodeAndReasonPhrase(code, code >= 400 ? "Error" : "OK");
            Map<String, String> h = new HashMap<>();
            h.put("Access-Control-Allow-Origin", "*");
            r.setResponseHeaders(h);
            return r;
        } catch (Exception e) {
            WebResourceResponse r = new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream(new byte[0]));
            r.setStatusCodeAndReasonPhrase(504, "Gateway Timeout");
            return r;
        }
    }

    private final Map<String, HttpURLConnection> streams = new ConcurrentHashMap<>();

    private void googleResult(String token, String err) {
        web.evaluateJavascript("window.__nbGoogle&&window.__nbGoogle(" + JSONObject.quote(token) + "," + JSONObject.quote(err) + ")", null);
    }

    private void emit(String id, String ev, String data) {
        final String js = "window.__nbSse&&window.__nbSse(" + JSONObject.quote(id) + "," + JSONObject.quote(ev) + "," + JSONObject.quote(data) + ")";
        web.post(() -> web.evaluateJavascript(js, null));
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        setContentView(web);
        // targetSdk 35+: الواجهة تمتد خلف أشرطة النظام، فنضيف حشوة بمقدارها
        ViewCompat.setOnApplyWindowInsetsListener(web, (v, wi) -> {
            Insets i = wi.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(i.left, i.top, i.right, i.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
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
                Uri u = r.getUrl();
                String path = u.getPath() == null ? "" : u.getPath();
                if (HOST.equals(u.getHost()) && "GET".equals(r.getMethod())
                        && (path.startsWith("/api/") || path.equals("/config.json"))) {
                    return proxyGet(BuildConfig.APP_URL + path + (u.getEncodedQuery() == null ? "" : "?" + u.getEncodedQuery()));
                }
                return loader.shouldInterceptRequest(u);
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
        web.loadUrl("https://" + HOST + "/" + BuildConfig.START);
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
