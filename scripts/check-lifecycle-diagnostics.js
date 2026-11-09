// Run: node scripts/check-lifecycle-diagnostics.js
const assert = require('node:assert');
const fs = require('node:fs');

class FakeDocument extends EventTarget {
  visibilityState = 'visible';
  wasDiscarded = false;
  hasFocus() { return this.visibilityState === 'visible'; }
}

globalThis.window = new EventTarget();
window.__teamsNotificationDiagnostics = { created: 2, bridgeSent: 1 };
globalThis.document = new FakeDocument();
Object.defineProperty(globalThis, 'navigator', {
  value: { onLine: true },
  configurable: true,
});
let interval;
globalThis.setInterval = (fn) => { interval = fn; return 1; };

eval(fs.readFileSync(__dirname + '/../app/src/main/res/raw/lifecycle_diagnostics.js', 'utf8'));

let report = JSON.parse(window.__teamsLifecycleReport());
assert.equal(report.visibility, 'visible');
assert.equal(report.online, true);
assert.deepEqual(report.notifications, { created: 2, bridgeSent: 1 });
assert.equal(report.events.length, 1);
assert.match(report.events[0], /script-start/);

document.visibilityState = 'hidden';
document.dispatchEvent(new Event('visibilitychange'));
document.dispatchEvent(new Event('freeze'));
window.dispatchEvent(new Event('pagehide'));
window.dispatchEvent(new Event('pageshow'));
Object.defineProperty(globalThis, 'navigator', { value: { onLine: false }, configurable: true });
window.dispatchEvent(new Event('offline'));
interval();

report = JSON.parse(window.__teamsLifecycleReport());
assert.equal(report.visibility, 'hidden');
assert.equal(report.online, false);
assert.ok(report.events.some((line) => line.includes('visibilitychange')));
assert.ok(report.events.some((line) => line.includes('freeze')));
assert.ok(report.events.some((line) => line.includes('pagehide')));
assert.ok(report.events.some((line) => line.includes('pageshow')));
assert.ok(report.events.some((line) => line.includes('offline')));
assert.ok(report.events.length <= 50);
console.log('LIFECYCLE_DIAGNOSTICS_OK');
