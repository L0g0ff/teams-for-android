// Link-preview thumbnails come from Teams' URL-preview proxy on another site
// (*.asyncgw.teams.microsoft.com/urlp/). In Android WebView a plain <img> request to it
// fails, while the same URL loads as a credentialed CORS request (measured on device).
// Retry such images once in CORS mode; the proxy allows the Teams origin with credentials.
(() => {
  const PROXY = /^https:\/\/[a-z0-9-]+\.asyncgw\.teams\.microsoft\.com\/urlp\//i;
  document.addEventListener('error', (event) => {
    const img = event.target;
    if (!(img instanceof HTMLImageElement) || img.crossOrigin !== null) return;
    const src = img.currentSrc || img.src;
    if (!PROXY.test(src)) return;
    img.crossOrigin = 'use-credentials';
    img.src = src;
  }, true);
})();
