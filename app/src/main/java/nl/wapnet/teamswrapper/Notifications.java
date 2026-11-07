package nl.wapnet.teamswrapper;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.webkit.JavaScriptReplyProxy;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Shows Teams Web notifications as Android notifications while the app process is alive.
 * ponytail: no delivery after Android kills the process; that needs Web Push, which WebView lacks.
 */
final class Notifications {
    static final String BRIDGE = "TeamsAndroid";
    private static final String CHANNEL = "teams";
    private static final String EXTRA_ID = "nl.wapnet.teamswrapper.NOTIFICATION_ID";
    private static final int PERMISSION_REQUEST = 2;
    private static final int MAX_TEXT = 500;

    private final Activity activity;
    private final NotificationManager manager;
    private JavaScriptReplyProxy page;

    Notifications(Activity activity) {
        this.activity = activity;
        manager = activity.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(
                CHANNEL, activity.getString(R.string.notification_channel), NotificationManager.IMPORTANCE_HIGH));
        // Ask at startup, while the app is on screen: the first message usually arrives in the
        // background, where the dialog cannot appear and that message would be lost.
        // Android itself stops showing the dialog after the user denies it twice.
        if (!allowed()) {
            activity.requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, PERMISSION_REQUEST);
        }
    }

    private boolean allowed() {
        return Build.VERSION.SDK_INT < 33
                || activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    void show(String json, JavaScriptReplyProxy reply) {
        page = reply;
        if (!allowed()) {
            return;
        }
        int id;
        String title;
        String body;
        try {
            JSONObject message = new JSONObject(json);
            id = message.getInt("id");
            title = clip(message.optString("title"));
            body = clip(message.optString("body"));
        } catch (JSONException e) {
            return;
        }
        Intent open = new Intent(activity, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_ID, id);
        PendingIntent tap = PendingIntent.getActivity(activity, id, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        manager.notify(id, new Notification.Builder(activity, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setContentIntent(tap)
                .setAutoCancel(true)
                .build());
    }

    /** Lets Teams open the chat that belongs to the tapped notification. */
    void clicked(Intent intent) {
        if (page != null && intent.hasExtra(EXTRA_ID)) {
            page.postMessage(String.valueOf(intent.getIntExtra(EXTRA_ID, 0)));
        }
    }

    void clearAll() {
        manager.cancelAll();
    }

    private static String clip(String text) {
        return text.length() > MAX_TEXT ? text.substring(0, MAX_TEXT) : text;
    }
}
