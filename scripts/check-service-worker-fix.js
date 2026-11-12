const assert = require('assert');
const fs = require('fs');

const root = `${__dirname}/..`;
const activity = fs.readFileSync(`${root}/app/src/main/java/nl/wapnet/teamswrapper/MainActivity.java`, 'utf8');
const worker = fs.readFileSync(`${root}/app/src/main/res/raw/noop_service_worker.js`, 'utf8');
const workflow = fs.readFileSync(`${root}/.github/workflows/build.yml`, 'utf8');

const handlers = {};
global.self = {
  addEventListener: (name, handler) => { handlers[name] = handler; },
  skipWaiting: () => Promise.resolve(),
  clients: { claim: () => Promise.resolve() },
};
eval(worker);

assert.deepStrictEqual(Object.keys(handlers).sort(), ['activate', 'install']);
assert(!handlers.fetch, 'no-op worker must not intercept navigation');
assert(activity.includes('ServiceWorkerController.getInstance().setServiceWorkerClient'));
assert(activity.includes('"teams.cloud.microsoft".equalsIgnoreCase(url.getHost())'));
assert(activity.includes('"/serviceworker.js".equals(url.getPath())'));
assert(activity.includes('WebViewFeature.DELETE_BROWSING_DATA'));
assert(activity.includes('WebStorageCompat.deleteBrowsingDataForSite'));
assert(activity.includes('pendingLoadUrl = initialUrl'));
assert(activity.includes('pendingLoadUrl = link'));
assert(activity.includes('String url = pendingLoadUrl'));
assert(activity.includes('R.raw.noop_service_worker'));
assert(workflow.includes('node scripts/check-service-worker-fix.js'));
console.log('SERVICE_WORKER_FIX_OK');
