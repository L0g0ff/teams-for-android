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
  'request.isForMainFrame()',
  'lastMainFrameError =',
  'WebView.getCurrentWebViewPackage()',
  'getActiveNetwork()',
  'NetworkCapabilities.TRANSPORT_VPN',
  'ClipData.newPlainText("Connection diagnostics", text)',
]) {
  if (!activity.includes(expected)) throw new Error(`connection diagnostics missing ${expected}`);
}
if (!activity.includes('hostAndPath(request.getUrl().toString())')) {
  throw new Error('main-frame error URL must omit query parameters');
}
if (activity.includes('onPageStarted(WebView view, String url') && activity.includes('lastMainFrameError = "none"')) {
  throw new Error('latest main-frame error is reset on navigation');
}
console.log('CONNECTION_DIAGNOSTICS_OK');
