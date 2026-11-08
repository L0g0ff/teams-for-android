const fs = require('fs');
const vm = require('vm');
const source = fs.readFileSync(`${__dirname}/../app/src/main/res/raw/background_visibility.js`, 'utf8');

class Events extends EventTarget {
  constructor() {
    super();
    this.sent = [];
  }
  postMessage(value) { this.sent.push(value); }
  receive(value) { this.dispatchEvent(new MessageEvent('message', {data: value})); }
}

const bridge = new Events();
const document = new EventTarget();
let changes = 0;
document.addEventListener('visibilitychange', () => changes++);
const context = {document, TeamsAndroidLifecycle: bridge, Event};
context.window = context;
vm.runInNewContext(source, context);

if (bridge.sent.join() !== 'ready') throw new Error('document did not request native state');
if (document.hidden !== false || document.visibilityState !== 'visible') throw new Error('initial state');
bridge.receive('true');
if (document.hidden !== true || document.visibilityState !== 'hidden' || changes !== 1) throw new Error('background state');
bridge.receive('true');
if (changes !== 1) throw new Error('duplicate state emitted an event');
bridge.receive('false');
if (document.hidden !== false || document.visibilityState !== 'visible' || changes !== 2) throw new Error('foreground state');
bridge.receive('invalid');
if (changes !== 2) throw new Error('invalid state changed visibility');
console.log('BACKGROUND_VISIBILITY_OK');
