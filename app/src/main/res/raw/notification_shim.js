// Bridges Teams Web notifications to Android. Android WebView has no Notification API of its own.
(() => {
  const bridge = () => window.TeamsAndroid;
  const live = new Map();
  let nextId = 0;
  let listening = false;

  const send = (message) => {
    const b = bridge();
    if (!b) return false;
    if (!listening) {
      listening = true;
      // Android reports a tapped notification by its id; Teams opens the chat from onclick.
      b.addEventListener('message', (e) => {
        const n = live.get(Number(e.data));
        if (n) n.dispatchEvent(new Event('click'));
      });
    }
    b.postMessage(JSON.stringify(message));
    return true;
  };

  class AndroidNotification extends EventTarget {
    constructor(title, options = {}) {
      super();
      this.id = ++nextId;
      this.title = String(title);
      this.body = String(options.body || '');
      this.tag = String(options.tag || '');
      this.data = options.data;
      this.onclick = this.onclose = this.onerror = this.onshow = null;
      live.set(this.id, this);
      // ponytail: keeps the 50 newest for click relay, like Teams for Linux.
      if (live.size > 50) live.delete(live.keys().next().value);
      const shown = send({ id: this.id, title: this.title, body: this.body });
      setTimeout(() => this.dispatchEvent(new Event(shown ? 'show' : 'error')), 0);
    }

    dispatchEvent(event) {
      const handler = this['on' + event.type];
      if (typeof handler === 'function') handler.call(this, event);
      return super.dispatchEvent(event);
    }

    close() {
      // Android keeps its notification until the app is opened; Teams closes its own quickly.
      if (live.delete(this.id)) this.dispatchEvent(new Event('close'));
    }

    static get permission() { return 'granted'; }
    static get maxActions() { return 0; }
    static requestPermission(callback) {
      if (typeof callback === 'function') callback('granted');
      return Promise.resolve('granted');
    }
  }

  Object.defineProperty(window, 'Notification', { value: AndroidNotification, configurable: true, writable: true });

  // WebView answers "denied" for notifications; Teams would then never create one.
  const permissions = navigator.permissions;
  if (permissions && permissions.query) {
    const query = permissions.query.bind(permissions);
    permissions.query = (d) => query(d).then((s) => {
      if (d && d.name === 'notifications') Object.defineProperty(s, 'state', { value: 'granted' });
      return s;
    });
  }
})();
