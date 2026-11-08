package nl.wapnet.teamswrapper;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Message;
import android.provider.Settings;
import android.view.KeyEvent;
import android.webkit.CookieManager;
import android.webkit.MimeTypeMap;
import android.webkit.ValueCallback;
import android.webkit.PermissionRequest;
import android.webkit.URLUtil;
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
    private static final int FILE_CHOOSER_REQUEST = 3;
    // At most one picker is open; a recreated Activity starts with null, so a late result is dropped.
    private ValueCallback<Uri[]> fileChooserCallback;
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
            WebViewCompat.addDocumentStartJavaScript(webView, rawScript(R.raw.preview_image_fix), OriginPolicy.teamsOrigins());
            if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                notifications = new Notifications(this);
                // Origin-restricted by WebView itself: other sites never see the bridge object.
                WebViewCompat.addWebMessageListener(webView, Notifications.BRIDGE, OriginPolicy.teamsOrigins(),
                        (view, message, origin, isMainFrame, reply) -> notifications.show(message.getData(), reply));
                WebViewCompat.addDocumentStartJavaScript(webView, rawScript(R.raw.notification_shim), OriginPolicy.teamsOrigins());
            }
        }

        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, length) ->
                download(url, userAgent, contentDisposition, mimeType));

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
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileChooserCallback != null) {
                    fileChooserCallback.onReceiveValue(null);
                }
                fileChooserCallback = callback;
                // System picker (ACTION_GET_CONTENT): grants read access per file, no storage permission.
                Intent pick = params.createIntent();
                if (params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) {
                    pick.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                }
                try {
                    startActivityForResult(pick, FILE_CHOOSER_REQUEST);
                } catch (ActivityNotFoundException e) {
                    fileChooserCallback = null;
                    Toast.makeText(MainActivity.this, R.string.link_no_app, Toast.LENGTH_SHORT).show();
                    return false;
                }
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
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != FILE_CHOOSER_REQUEST) {
            super.onActivityResult(requestCode, resultCode, data);
            return;
        }
        ValueCallback<Uri[]> callback = fileChooserCallback;
        fileChooserCallback = null;
        if (callback == null) {
            return;
        }
        Uri[] files = null;
        if (resultCode == RESULT_OK && data != null) {
            if (data.getClipData() != null) {
                files = new Uri[data.getClipData().getItemCount()];
                for (int i = 0; i < files.length; i++) {
                    files[i] = data.getClipData().getItemAt(i).getUri();
                }
            } else if (data.getData() != null) {
                files = new Uri[] {data.getData()};
            }
        }
        // null tells WebView the picker was cancelled, so Teams can open it again.
        callback.onReceiveValue(files);
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

    private String rawScript(int id) {
        try (java.io.InputStream in = getResources().openRawResource(id);
                java.util.Scanner all = new java.util.Scanner(in, "UTF-8").useDelimiter("\\A")) {
            return all.next();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Teams/SharePoint attachments via Android's DownloadManager, with the WebView session cookie
     * for that URL. DownloadManager shows progress, completion and failure in its own notification.
     */
    private void download(String url, String userAgent, String contentDisposition, String mimeType) {
        Uri uri = Uri.parse(url);
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            // blob:/data: files are generated inside the page; DownloadManager cannot fetch them.
            Toast.makeText(this, getString(R.string.download_unsupported, uri.getScheme()), Toast.LENGTH_LONG).show();
            return;
        }
        // SharePoint/OneDrive serve files from .../download.aspx with the real name only in filename*=.
        String name = FileName.fromContentDisposition(contentDisposition);
        if (name == null) {
            name = URLUtil.guessFileName(url, null, mimeType);
        }
        name = FileName.withExtension(name, MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType));
        DownloadManager.Request request = new DownloadManager.Request(uri)
                .addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url))
                .addRequestHeader("User-Agent", userAgent)
                .setMimeType(mimeType)
                .setTitle(name)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        if (Build.VERSION.SDK_INT >= 29) {
            // Public Downloads without a storage permission from Android 10 on.
            // ponytail: Android 8-9 keep DownloadManager's own location (open via the notification);
            // a public folder there would need WRITE_EXTERNAL_STORAGE.
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
        }
        getSystemService(DownloadManager.class).enqueue(request);
        Toast.makeText(this, getString(R.string.download_started, name), Toast.LENGTH_SHORT).show();
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

    // Broken <img> elements on the page (e.g. link-preview thumbnails) and what a refetch returns.
    // Broken <img> elements on the page (e.g. link-preview thumbnails): how the element is set up,
    // whether a fresh Image() of the same URL loads, and what a credentialed fetch returns.
    private static final String IMAGE_PROBE = "(async()=>{const r=[];"
            + "const load=(u,c)=>new Promise(ok=>{const i=new Image();if(c)i.crossOrigin=c;"
            + "i.onload=()=>ok('load '+i.naturalWidth+'x'+i.naturalHeight);i.onerror=()=>ok('error');"
            + "setTimeout(()=>ok('timeout'),3000);i.src=u});"
            + "for(const i of [...document.images].filter(i=>i.complete&&i.naturalWidth===0&&i.currentSrc).slice(0,3)){"
            + "const u=i.currentSrc,e={src:u.slice(0,120),attr:(i.getAttribute('src')||'').slice(0,40),"
            + "crossOrigin:i.crossOrigin,referrerPolicy:i.referrerPolicy,loading:i.loading,srcset:!!i.srcset};"
            + "e.image=await load(u,null);e.imageCredentials=await load(u,'use-credentials');"
            + "try{const f=await fetch(u,{credentials:'include'});e.fetch=f.status+' '+f.headers.get('content-type')}"
            + "catch(x){e.fetch='ERR '+x.name+': '+x.message}r.push(e)}"
            + "return JSON.stringify({broken:r.length,images:r},null,1)})()";

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
        List<Runnable> actions = new ArrayList<>();
        items.add(getString(R.string.diagnostics_media));
        actions.add(() -> runProbe(MEDIA_PROBE, R.string.diagnostics_media, true));
        items.add(getString(R.string.diagnostics_images));
        actions.add(() -> runProbe(IMAGE_PROBE, R.string.diagnostics_images, false));
        items.add(getString(R.string.diagnostics_notification));
        actions.add(() -> {
            // Same path as a Teams notification, 10 s later so the app can be sent to the background first.
            webView.evaluateJavascript(DELAYED_TEST_NOTIFICATION, null);
            Toast.makeText(this, R.string.diagnostics_notification, Toast.LENGTH_SHORT).show();
        });
        items.add(getString(R.string.diagnostics_app_settings));
        actions.add(this::openAppSettings);
        if (Build.VERSION.SDK_INT >= 31 && callAudio() != null) {
            items.add(getString(callAudio.onEarpiece() ? R.string.call_audio_to_speaker : R.string.call_audio_to_earpiece));
            actions.add(this::toggleCallAudio);
        }
        show(new AlertDialog.Builder(this)
                .setTitle(R.string.diagnostics_title)
                .setItems(items.toArray(new String[0]), (d, which) -> actions.get(which).run()));
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

    private void runProbe(String probe, int title, boolean withAudioState) {
        if (probeRunning) {
            return;
        }
        probeRunning = true;
        // evaluateJavascript cannot await; the probe stores its result and a second call reads it.
        webView.evaluateJavascript("window.__probe=null;" + probe + ".then(r=>window.__probe=r)", null);
        // ponytail: fixed wait; the image probe needs up to ~20 s for three images.
        webView.postDelayed(() -> webView.evaluateJavascript("window.__probe", json -> {
            probeRunning = false;
            showProbe(json, title, withAudioState);
        }), withAudioState ? 4000 : 20000);
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

    private void showProbe(String json, int title, boolean withAudioState) {
        String report;
        try {
            report = new org.json.JSONTokener(json).nextValue().toString();
        } catch (org.json.JSONException e) {
            report = json;
        }
        String text = withAudioState ? audioState() + "\n" + report : report;
        show(new AlertDialog.Builder(this)
                .setTitle(title)
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
        if (fileChooserCallback != null) {
            fileChooserCallback.onReceiveValue(null);
            fileChooserCallback = null;
        }
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
