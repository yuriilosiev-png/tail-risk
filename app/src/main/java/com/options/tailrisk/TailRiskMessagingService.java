package com.options.tailrisk;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Пуши о фиксации. Worker шлёт data-only сообщения с полями title и body
 * (как в Crypto Options): так onMessageReceived вызывается и в фоне,
 * а баннер рисует наш код — MIUI его не глушит.
 */
public class TailRiskMessagingService extends FirebaseMessagingService {

    public static final String PREFS_NAME = "TailRiskPrefs";
    public static final String KEY_TOKEN  = "fcm_token";
    static final String CHANNEL_ID   = "tail_alerts";
    static final String CHANNEL_NAME = "Take-profit alerts";
    private static final AtomicInteger counter = new AtomicInteger(1000);

    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_TOKEN, token).apply();
        MainActivity a = MainActivity.getInstance();
        if (a != null) a.injectFcmToken(token);
    }

    @Override
    public void onMessageReceived(RemoteMessage msg) {
        super.onMessageReceived(msg);
        String title = null, body = null;
        Map<String, String> data = msg.getData();
        if (data != null) { title = data.get("title"); body = data.get("body"); }
        if (title == null && msg.getNotification() != null) {
            title = msg.getNotification().getTitle();
            body = msg.getNotification().getBody();
        }
        if (title == null) title = "Tail Risk";
        if (body == null) body = "";
        show(title, body);
    }

    @android.annotation.TargetApi(26)
    static NotificationChannel buildChannel() {
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Take-profit level alerts");
        ch.enableVibration(true);
        ch.setVibrationPattern(new long[]{0, 250, 250, 250});
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        ch.setShowBadge(true);
        ch.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
            new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build());
        return ch;
    }

    private void show(String title, String body) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) nm.createNotificationChannel(buildChannel());

        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif)
            .setColor(0xFFF2A65A)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(pi);
        nm.notify(counter.incrementAndGet(), b.build());
    }
}
