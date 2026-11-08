(() => {
  const bridge = window.TeamsAndroidLifecycle;
  if (!bridge) return;

  let backgrounded = false;
  Object.defineProperty(document, 'hidden', {configurable: true, get: () => backgrounded});
  Object.defineProperty(document, 'visibilityState', {
    configurable: true,
    get: () => backgrounded ? 'hidden' : 'visible'
  });
  Object.defineProperty(document, 'hasFocus', {
    configurable: true,
    value: () => !backgrounded
  });

  bridge.addEventListener('message', event => {
    if (event.data !== 'true' && event.data !== 'false') return;
    const next = event.data === 'true';
    if (next === backgrounded) return;
    backgrounded = next;
    window.dispatchEvent(new Event(backgrounded ? 'blur' : 'focus'));
    document.dispatchEvent(new Event('visibilitychange'));
  });
  bridge.postMessage('ready');
})();
