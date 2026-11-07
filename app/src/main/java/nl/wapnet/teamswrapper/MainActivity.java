package nl.wapnet.teamswrapper;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
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
    private Notifications notifications;
    private boolean volumeUpDown;
    private boolean volumeDownDown;
    private boolean probeRunning;
    private Dialog openDialog;
    private CallAudio callAudio;
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
        // target=_blank links (chat links) arrive in onCreateWindow instead of replacing Teams.
        settings.setSupportMultipleWindows(true);

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, true);

        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(webView, MEDIA_PERMISSION_STATE_FIX, OriginPolicy.teamsOrigins());
            if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                notifications = new Notifications(this);
                // Origin-restricted by WebView itself: other sites never see the bridge object.
                WebViewCompat.addWebMessageListener(webView, Notifications.BRIDGE, OriginPolicy.teamsOrigins(),
                        (view, message, origin, isMainFrame, reply) -> notifications.show(message.getData(), reply));
                WebViewCompat.addDocumentStartJavaScript(webView, Notifications.shim(this), OriginPolicy.teamsOrigins());
            }
        }

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.regionMatches(true, 0, "msteams:", 0, 8)) {
                    // Teams' launcher page hands meetings to the "desktop app" via msteams:; stay in-app.
                    String link = OriginPolicy.deepLink(url);
                    if (link != null) {
                        view.loadUrl(link);
                    }
                    return true;
                }
                if (OriginPolicy.isMediaSettingsLink(url)) {
                    openAppSettings();
                }
                if (OriginPolicy.isWindowsSettingsLink(url)) {
                    // Other ms-settings: targets are Windows-only; never hand them to another app.
                    return true;
                }
                String scheme = request.getUrl().getScheme();
                if ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme)) {
                    // ponytail: same-window navigations stay in-app so redirects to a tenant's own
                    // sign-in server keep working; chat links use new windows (onCreateWindow).
                    return false;
                }
                openExternally(url);
                return true;
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message result) {
                // A throwaway WebView only catches the URL of the new window; it never renders it.
                WebView catcher = new WebView(view.getContext());
                catcher.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView popup, WebResourceRequest request) {
                        String url = request.getUrl().toString();
                        if (OriginPolicy.opensInApp(url)) {
                            webView.loadUrl(url);
                        } else {
                            openExternally(url);
                        }
                        popup.post(popup::destroy);
                        return true;
                    }
                });
                ((WebView.WebViewTransport) result.obj).setWebView(catcher);
                result.sendToTarget();
                return true;
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                handleMediaRequest(request);
            }

            @Override
            public void onPermissionRequestCanceled(PermissionRequest request) {
                pendingMediaRequests.remove(request);
            }
        });
        String link = OriginPolicy.deepLink(getIntent().getDataString());
        webView.loadUrl(link != null ? link : TEAMS_URL);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // singleTask: a link from another app reuses this Activity and its signed-in WebView.
        String link = OriginPolicy.deepLink(intent.getDataString());
        if (link != null) {
            webView.loadUrl(link);
        }
        if (notifications != null) {
            notifications.clicked(intent);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (notifications != null) {
            notifications.clearAll();
        }
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

    /** Hands http(s), mailto: and tel: links to Android; other schemes are dropped. */
    private void openExternally(String url) {
        if (!OriginPolicy.opensExternally(url)) {
            Toast.makeText(this, R.string.link_blocked, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.link_no_app, Toast.LENGTH_SHORT).show();
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

    // Diagnostics menu (volume up + down together): media probe and a delayed test notification.
    private static final String MEDIA_PROBE = "(async()=>{const r={secure:isSecureContext,ua:navigator.userAgent};"
            + "for(const n of['camera','microphone'])try{r[n]=(await navigator.permissions.query({name:n})).state}catch(e){r[n]='ERR '+e}"
            + "for(const c of[{audio:true},{video:true},{audio:true,video:true}])try{const s=await navigator.mediaDevices.getUserMedia(c);"
            + "r[JSON.stringify(c)]=s.getTracks().map(t=>t.kind+':'+t.label);s.getTracks().forEach(t=>t.stop())}"
            + "catch(e){r[JSON.stringify(c)]='ERR '+e.name+': '+e.message}"
            + "r.devices=(await navigator.mediaDevices.enumerateDevices()).map(d=>d.kind+':'+(d.label||'?'));"
            + "return JSON.stringify(r,null,1)})()";

    private static final String DELAYED_TEST_NOTIFICATION = "setTimeout(()=>{"
            + "const n=new Notification('Teams for Android test',{body:'Delayed test notification'});"
            + "n.onclick=()=>alert('Test notification tap reached Teams')},10000)";

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            volumeUpDown = true;
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            volumeDownDown = true;
        } else {
            return super.onKeyDown(keyCode, event);
        }
        if (volumeUpDown && volumeDownDown) {
            // Holding the keys repeats onKeyDown; only the first press opens the menu.
            if (event.getRepeatCount() == 0) {
                showDiagnosticsMenu();
            }
            return true;
        }
        // A single volume key keeps changing the volume as usual.
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            volumeUpDown = false;
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            volumeDownDown = false;
        }
        return super.onKeyUp(keyCode, event);
    }

    private void showDiagnosticsMenu() {
        if (openDialog != null && openDialog.isShowing()) {
            return;
        }
        List<String> items = new ArrayList<>();
        items.add(getString(R.string.diagnostics_media));
        items.add(getString(R.string.diagnostics_notification));
        items.add(getString(R.string.diagnostics_app_settings));
        if (Build.VERSION.SDK_INT >= 31 && callAudio() != null) {
            items.add(getString(callAudio.onEarpiece() ? R.string.call_audio_to_speaker : R.string.call_audio_to_earpiece));
        }
        show(new AlertDialog.Builder(this)
                .setTitle(R.string.diagnostics_title)
                .setItems(items.toArray(new String[0]), (d, which) -> {
                    if (which == 3) {
                        toggleCallAudio();
                    } else if (which == 0) {
                        runMediaProbe();
                    } else if (which == 1) {
                        // Same path as a Teams notification, 10 s later so the app can be sent to the background first.
                        webView.evaluateJavascript(DELAYED_TEST_NOTIFICATION, null);
                        Toast.makeText(this, R.string.diagnostics_notification, Toast.LENGTH_SHORT).show();
                    } else if (which == 2) {
                        openAppSettings();
                    }
                }));
    }

    /** Null on Android 11 and older, and on devices without an earpiece. */
    private CallAudio callAudio() {
        if (Build.VERSION.SDK_INT < 31) {
            return null;
        }
        if (callAudio == null) {
            callAudio = new CallAudio(getSystemService(android.media.AudioManager.class));
        }
        return callAudio.hasEarpiece() ? callAudio : null;
    }

    private void toggleCallAudio() {
        if (Build.VERSION.SDK_INT < 31 || callAudio == null) {
            return;
        }
        boolean ok = callAudio.toggle();
        int message = !ok ? R.string.call_audio_failed
                : callAudio.onEarpiece() ? R.string.call_audio_on_earpiece : R.string.call_audio_on_speaker;
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void runMediaProbe() {
        if (probeRunning) {
            return;
        }
        probeRunning = true;
        // evaluateJavascript cannot await; the probe stores its result and a second call reads it.
        webView.evaluateJavascript("window.__probe=null;" + MEDIA_PROBE + ".then(r=>window.__probe=r)", null);
        webView.postDelayed(() -> webView.evaluateJavascript("window.__probe", json -> {
            probeRunning = false;
            showProbe(json);
        }), 4000);
    }

    private void show(AlertDialog.Builder builder) {
        if (openDialog != null && openDialog.isShowing()) {
            openDialog.dismiss();
        }
        openDialog = builder.show();
    }

    /** Native call-audio routing, which the page cannot see. */
    private String audioState() {
        android.media.AudioManager audio = getSystemService(android.media.AudioManager.class);
        StringBuilder out = new StringBuilder("mode=").append(audio.getMode())
                .append(" (3=IN_COMMUNICATION)\nspeakerphoneOn=").append(audio.isSpeakerphoneOn())
                .append("\nvoiceCallVolume=").append(audio.getStreamVolume(android.media.AudioManager.STREAM_VOICE_CALL))
                .append('/').append(audio.getStreamMaxVolume(android.media.AudioManager.STREAM_VOICE_CALL));
        if (Build.VERSION.SDK_INT >= 31) {
            android.media.AudioDeviceInfo current = audio.getCommunicationDevice();
            out.append("\ncommunicationDevice=").append(current == null ? "none" : current.getType());
            out.append("\navailable=");
            for (android.media.AudioDeviceInfo device : audio.getAvailableCommunicationDevices()) {
                out.append(device.getType()).append(' ');
            }
            out.append("(1=earpiece 2=speaker)");
        }
        return out.toString();
    }

    private void showProbe(String json) {
        String report;
        try {
            report = new org.json.JSONTokener(json).nextValue().toString();
        } catch (org.json.JSONException e) {
            report = json;
        }
        String text = audioState() + "\n" + report;
        show(new AlertDialog.Builder(this)
                .setTitle(R.string.diagnostics_media)
                .setMessage(text)
                .setPositiveButton(R.string.diagnostics_copy, (d, w) -> getSystemService(ClipboardManager.class)
                        .setPrimaryClip(ClipData.newPlainText("Media diagnostics", text)))
                .setNegativeButton(R.string.diagnostics_close, null));
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
        if (openDialog != null) {
            openDialog.dismiss();
        }
        if (Build.VERSION.SDK_INT >= 31 && callAudio != null) {
            callAudio.restore();
        }
        webView.destroy();
        super.onDestroy();
    }
}
