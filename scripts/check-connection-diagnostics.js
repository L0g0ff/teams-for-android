const fs = require('fs');
const root = `${__dirname}/..`;
const activity = fs.readFileSync(`${root}/app/src/main/java/nl/wapnet/teamswrapper/MainActivity.java`, 'utf8');
const manifest = fs.readFileSync(`${root}/app/src/main/AndroidManifest.xml`, 'utf8');

if (!manifest.includes('android.permission.ACCESS_NETWORK_STATE')) {
  throw new Error('manifest missing network-state permission');
}

for (const expected of [
  'diagnostics_connection',
  'showConnectionDiagnostics',
  'public void onReceivedError(WebView view, int errorCode, String description, String failingUrl)',
  'lastMainFrameError =',
  'WebView.getCurrentWebViewPackage()',
  'getActiveNetwork()',
  'NetworkCapabilities.TRANSPORT_VPN',
  'ClipData.newPlainText("Connection diagnostics", text)',
]) {
  if (!activity.includes(expected)) throw new Error(`connection diagnostics missing ${expected}`);
}
if (!activity.includes('hostAndPath(url)')) {
  throw new Error('main-frame error URL must omit query parameters');
}
const mainClient = activity.slice(
  activity.indexOf('webView.setWebViewClient'),
  activity.indexOf('webView.setWebChromeClient'),
);
if (mainClient.includes('public void onReceivedError(WebView view, WebResourceRequest request')) {
  throw new Error('modern override bypasses WebViewClient forwarding to the main-frame callback');
}
if (mainClient.includes('onPageStarted(WebView view, String url') && mainClient.includes('lastMainFrameError = "none"')) {
  throw new Error('latest main-frame error is reset on navigation');
}
console.log('CONNECTION_DIAGNOSTICS_OK');
