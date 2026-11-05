package nl.wapnet.teamswrapper;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import java.util.ArrayList;
import java.util.List;

public final class MainActivity extends Activity {
    private static final String TEAMS_URL = "https://teams.cloud.microsoft/";
    private static final int MEDIA_PERMISSION_REQUEST = 1;
    private WebView webView;
    // Android WebView always answers permissions.query with "prompt" for camera/microphone, because
    // the embedding app decides per request. Teams reads that as "blocked by the browser" and never
    // calls getUserMedia. Report "granted" instead; the real decision still happens in
    // onPermissionRequest and the Android runtime dialog.
    private static final String MEDIA_PERMISSION_STATE_FIX = "(()=>{const p=navigator.permissions;"
            + "if(!p||!p.query)return;const q=p.query.bind(p);"
            + "p.query=d=>q(d).then(s=>{if(d&&(d.name==='camera'||d.name==='microphone')&&s.state==='prompt')"
            + "Object.defineProperty(s,'state',{value:'granted'});return s})})()";
    private static final String[] MEDIA_PERMISSIONS =
            {Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO};
    // Teams fires audio and video requests concurrently; all wait on one Android dialog.
    private final List<PermissionRequest> pendingMediaRequests = new ArrayList<>();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        WebView.setWebContentsDebuggingEnabled(getResources().getBoolean(R.bool.webview_debugging));
        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setUseWideViewPort(true);
        // Remote call audio must start without a fresh tap.
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setUserAgentString(UserAgent.desktop(settings.getUserAgentString()));

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, true);

        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(webView, MEDIA_PERMISSION_STATE_FIX, OriginPolicy.mediaOrigins());
        }

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (OriginPolicy.isMediaSettingsLink(url)) {
                    openAppSettings();
                }
                // Other ms-settings: targets are Windows-only; never hand them to another app.
                return OriginPolicy.isWindowsSettingsLink(url);
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                handleMediaRequest(request);
            }

            @Override
            public void onPermissionRequestCanceled(PermissionRequest request) {
                pendingMediaRequests.remove(request);
            }
        });
        webView.loadUrl(TEAMS_URL);
    }

    private void handleMediaRequest(PermissionRequest request) {
        String origin = request.getOrigin().toString();
        if (!OriginPolicy.mayCapture(origin)) {
            denyVisibly(request, origin);
            return;
        }
        for (String resource : request.getResources()) {
            if (androidPermission(resource) == null) {
                denyVisibly(request, resource);
                return;
            }
        }
        List<String> missing = new ArrayList<>();
        for (String permission : MEDIA_PERMISSIONS) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                missing.add(permission);
            }
        }
        if (missing.isEmpty()) {
            request.grant(request.getResources());
            return;
        }
        pendingMediaRequests.add(request);
        if (pendingMediaRequests.size() == 1) {
            // A call needs both; one dialog avoids a second concurrent request being cancelled by Android.
            requestPermissions(missing.toArray(new String[0]), MEDIA_PERMISSION_REQUEST);
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        if (code != MEDIA_PERMISSION_REQUEST) {
            return;
        }
        for (PermissionRequest request : pendingMediaRequests) {
            List<String> granted = new ArrayList<>();
            for (String resource : request.getResources()) {
                if (checkSelfPermission(androidPermission(resource)) == PackageManager.PERMISSION_GRANTED) {
                    granted.add(resource);
                }
            }
            if (granted.isEmpty()) {
                request.deny();
            } else {
                request.grant(granted.toArray(new String[0]));
            }
        }
        pendingMediaRequests.clear();
    }

    /** Wrapper-side denials are shown so a failing call can be told apart from a Teams-side block. */
    private void denyVisibly(PermissionRequest request, String reason) {
        request.deny();
        Toast.makeText(this, getString(R.string.media_denied, reason), Toast.LENGTH_LONG).show();
    }

    private static String androidPermission(String resource) {
        switch (resource) {
            case PermissionRequest.RESOURCE_VIDEO_CAPTURE:
                return Manifest.permission.CAMERA;
            case PermissionRequest.RESOURCE_AUDIO_CAPTURE:
                return Manifest.permission.RECORD_AUDIO;
            default:
                return null;
        }
    }

    private void openAppSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", getPackageName(), null)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.media_settings_unavailable, Toast.LENGTH_LONG).show();
        }
    }

    // PR test builds only: volume-up runs a media probe inside Teams and shows a copyable report.
    private static final String MEDIA_PROBE = "(async()=>{const r={secure:isSecureContext,ua:navigator.userAgent};"
            + "for(const n of['camera','microphone'])try{r[n]=(await navigator.permissions.query({name:n})).state}catch(e){r[n]='ERR '+e}"
            + "for(const c of[{audio:true},{video:true},{audio:true,video:true}])try{const s=await navigator.mediaDevices.getUserMedia(c);"
            + "r[JSON.stringify(c)]=s.getTracks().map(t=>t.kind+':'+t.label);s.getTracks().forEach(t=>t.stop())}"
            + "catch(e){r[JSON.stringify(c)]='ERR '+e.name+': '+e.message}"
            + "r.devices=(await navigator.mediaDevices.enumerateDevices()).map(d=>d.kind+':'+(d.label||'?'));"
            + "return JSON.stringify(r,null,1)})()";

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode != KeyEvent.KEYCODE_VOLUME_UP || !getResources().getBoolean(R.bool.webview_debugging)) {
            return super.onKeyDown(keyCode, event);
        }
        // evaluateJavascript cannot await; the probe stores its result and a second call reads it.
        webView.evaluateJavascript("window.__probe=null;" + MEDIA_PROBE + ".then(r=>window.__probe=r)", null);
        webView.postDelayed(() -> webView.evaluateJavascript("window.__probe", this::showProbe), 4000);
        return true;
    }

    private void showProbe(String json) {
        String report;
        try {
            report = new org.json.JSONTokener(json).nextValue().toString();
        } catch (org.json.JSONException e) {
            report = json;
        }
        String text = report;
        new AlertDialog.Builder(this)
                .setTitle("Media probe")
                .setMessage(text)
                .setPositiveButton("Copy", (d, w) -> getSystemService(ClipboardManager.class)
                        .setPrimaryClip(ClipData.newPlainText("Media probe", text)))
                .setNegativeButton("Close", null)
                .show();
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onPause() {
        CookieManager.getInstance().flush();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        for (PermissionRequest request : pendingMediaRequests) {
            request.deny();
        }
        pendingMediaRequests.clear();
        webView.destroy();
        super.onDestroy();
    }
}
