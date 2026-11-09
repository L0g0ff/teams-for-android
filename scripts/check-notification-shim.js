// Run: node scripts/check-notification-shim.js
const assert = require('node:assert');
const fs = require('node:fs');
const posted = [];
let reply;
globalThis.window = globalThis;
globalThis.document = { hidden: true, visibilityState: 'hidden', hasFocus: () => false };
class PermissionStatus { get state() { return 'denied'; } }
Object.defineProperty(globalThis, 'navigator', { value: { permissions: { query: async () => new PermissionStatus() } } });
window.TeamsAndroid = {
  postMessage: (m) => posted.push(JSON.parse(m)),
  addEventListener: (type, fn) => { assert.equal(type, 'message'); reply = fn; },
};
eval(fs.readFileSync(__dirname + '/../app/src/main/res/raw/notification_shim.js', 'utf8'));

(async () => {
  assert.equal(Notification.permission, 'granted');
  assert.equal(await Notification.requestPermission(), 'granted');
  assert.equal((await navigator.permissions.query({ name: 'notifications' })).state, 'granted');
  assert.equal((await navigator.permissions.query({ name: 'geolocation' })).state, 'denied');

  let clicked = 0, listened = 0;
  const n = new Notification('Alice', { body: 'Hi', tag: 'chat-1' });
  n.onclick = () => clicked++;
  n.addEventListener('click', () => listened++);
  assert.deepEqual(posted[0], { id: n.id, title: 'Alice', body: 'Hi' });

  reply({ data: String(n.id) });
  reply({ data: '999' });
  assert.equal(clicked, 1);
  assert.equal(listened, 1);

  let closed = 0;
  n.onclose = () => closed++;
  n.close();
  n.close();
  assert.equal(closed, 1);
  reply({ data: String(n.id) });
  assert.equal(clicked, 1, 'closed notification must not relay clicks');
  assert.equal(posted.length, 1);

  let errored = false;
  delete window.TeamsAndroid;
  new Notification('x').onerror = () => { errored = true; };
  await new Promise((r) => setTimeout(r, 1));
  assert.ok(errored, 'missing bridge must raise error');
  assert.deepEqual(window.__teamsNotificationDiagnostics, {
    permissionReads: 1,
    permissionRequests: 1,
    permissionQueries: 1,
    created: 2,
    bridgeSent: 1,
    bridgeMissing: 1,
    last: { visibility: 'hidden', focus: false },
  });
  console.log('NOTIFICATION_SHIM_OK');
})();
