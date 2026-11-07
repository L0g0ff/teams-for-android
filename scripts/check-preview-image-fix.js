// Run: node scripts/check-preview-image-fix.js
const assert = require('node:assert');
const fs = require('node:fs');
let onError;
class HTMLImageElement { constructor(src) { this.src = src; this.currentSrc = src; this.crossOrigin = null; } }
globalThis.HTMLImageElement = HTMLImageElement;
globalThis.document = {
  addEventListener: (type, fn, capture) => { assert.equal(type, 'error'); assert.equal(capture, true); onError = fn; },
};
eval(fs.readFileSync(__dirname + '/../app/src/main/res/raw/preview_image_fix.js', 'utf8'));

const proxy = 'https://eu-prod.asyncgw.teams.microsoft.com/urlp/v1/x/url/image/Thumbnail?url=a';
const img = new HTMLImageElement(proxy);
onError({ target: img });
assert.equal(img.crossOrigin, 'use-credentials');
assert.equal(img.src, proxy);

img.src = 'unchanged';
onError({ target: img });
assert.equal(img.src, 'unchanged', 'retries only once');

for (const other of [
  'https://example.com/a.png',
  'https://eu-prod.asyncgw.teams.microsoft.com.evil.example/urlp/x',
  'http://eu-prod.asyncgw.teams.microsoft.com/urlp/x',
  'https://eu-prod.asyncgw.teams.microsoft.com/other/x',
]) {
  const i = new HTMLImageElement(other);
  onError({ target: i });
  assert.equal(i.crossOrigin, null, other);
}
onError({ target: { src: proxy } });
console.log('PREVIEW_IMAGE_FIX_OK');
