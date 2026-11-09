const fs = require('fs');
const root = `${__dirname}/..`;
const manifest = fs.readFileSync(`${root}/app/src/main/AndroidManifest.xml`, 'utf8');
const activity = fs.readFileSync(`${root}/app/src/main/java/nl/wapnet/teamswrapper/MainActivity.java`, 'utf8');
const service = fs.readFileSync(`${root}/app/src/main/java/nl/wapnet/teamswrapper/ConnectionService.java`, 'utf8');

for (const expected of [
  'android.permission.FOREGROUND_SERVICE',
  'android.permission.FOREGROUND_SERVICE_SPECIAL_USE',
  'android:foregroundServiceType="specialUse"',
  'android:exported="false"',
  'android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE',
]) {
  if (!manifest.includes(expected)) throw new Error(`manifest missing ${expected}`);
}
if (!activity.includes('ConnectionService.start(this)')) throw new Error('Activity does not start connection service');
for (const expected of ['startForeground(', 'START_NOT_STICKY', 'setOngoing(true)', 'PendingIntent.getActivity']) {
  if (!service.includes(expected)) throw new Error(`service missing ${expected}`);
}
console.log('BACKGROUND_SERVICE_OK');
