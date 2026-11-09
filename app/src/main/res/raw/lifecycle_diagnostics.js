// Records why Teams stops receiving messages after Android backgrounds the WebView.
(() => {
  const events = [];
  let lastTick = performance.now();

  const record = (name) => {
    events.push(`${new Date().toISOString()} ${name} visibility=${document.visibilityState}`
      + ` online=${navigator.onLine} focus=${document.hasFocus()}`);
    // ponytail: ten minutes of timer-gap events is enough for one reproduction.
    if (events.length > 50) events.shift();
  };

  for (const name of ['visibilitychange', 'freeze', 'resume']) {
    document.addEventListener(name, () => record(name));
  }
  for (const name of ['pagehide', 'pageshow', 'online', 'offline']) {
    window.addEventListener(name, () => record(name));
  }
  setInterval(() => {
    const now = performance.now();
    const gap = now - lastTick;
    lastTick = now;
    if (gap > 15000) record(`timer-gap=${Math.round(gap)}ms`);
  }, 5000);

  window.__teamsLifecycleReport = () => JSON.stringify({
    now: new Date().toISOString(),
    visibility: document.visibilityState,
    online: navigator.onLine,
    focus: document.hasFocus(),
    discarded: document.wasDiscarded,
    notifications: window.__teamsNotificationDiagnostics || null,
    events,
  }, null, 1);
  record('script-start');
})();
