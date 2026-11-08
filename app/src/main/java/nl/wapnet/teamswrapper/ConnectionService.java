package nl.wapnet.teamswrapper;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

/** Keeps Android 15+ network access available while the Teams WebView is backgrounded. */
public final class ConnectionService extends Service {
    private static final String CHANNEL = "teams_connection";
    // Keep the foreground-service notification outside Teams' web-provided id range.
    private static final int NOTIFICATION_ID = 0x5445414D; // "TEAM"

    static void start(Context context) {
        context.startForegroundService(new Intent(context, ConnectionService.class));
    }

    static void stop(Context context) {
        context.stopService(new Intent(context, ConnectionService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(
                CHANNEL, getString(R.string.connection_channel), NotificationManager.IMPORTANCE_LOW));

        Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent tap = PendingIntent.getActivity(this, NOTIFICATION_ID, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.connection_notification_title))
                .setContentText(getString(R.string.connection_notification_text))
                .setContentIntent(tap)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
