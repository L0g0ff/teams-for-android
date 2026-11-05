package nl.wapnet.teamswrapper;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

public final class MainActivity extends Activity {
    private static final String TEAMS_URL = "https://teams.cloud.microsoft/";
    private static final int MEDIA_PERMISSION_REQUEST = 1;
    private WebView webView;
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
