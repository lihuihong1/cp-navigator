package com.ojhelper;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

/** 开赛提醒的通知（channel set / 响铃 / 震动 / 点开跳转）。 */
final class Notif {

    static final String CHANNEL = "remind";

    private Notif() {
    }

    /** 应用启动时调用一次；重复调用无副作用。 */
    static void init(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null || nm.getNotificationChannel(CHANNEL) != null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL, "开赛提醒", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("比赛开始前的提醒");
        ch.enableVibration(true);
        ch.setVibrationPattern(new long[]{0, 300, 180, 300});
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        ch.setShowBadge(true);
        // 用闹钟音量：免打扰下也更容易响
        Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (sound != null) {
            AudioAttributes att = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            ch.setSound(sound, att);
        }
        nm.createNotificationChannel(ch);
    }

    static void show(Context c, String key, String oj, String title, long start, long end,
                     String url, int lead) {
        if (Build.VERSION.SDK_INT >= 33 && !Reminder.notifAllowed(c)) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= 24 && !nm.areNotificationsEnabled()) return;
        init(c);

        String when = lead <= 0 ? "比赛马上开始" : "比赛将在 " + lead + " 分钟后开始";
        String line = "[" + M.ojName(oj) + "] " + title + "\n" + M.stamp(start) + " · " + M.duration(end - start);

        Intent open = new Intent(c, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int f = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent pi = PendingIntent.getActivity(c, key.hashCode(), open, f);

        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            b = new Notification.Builder(c, CHANNEL);
        } else {
            b = new Notification.Builder(c);
            b.setPriority(Notification.PRIORITY_HIGH);
            b.setDefaults(Notification.DEFAULT_VIBRATE);
            Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (sound != null) b.setSound(sound);
        }
        b.setSmallIcon(R.drawable.ic_notify)
                .setColor(0xFF2563EB)
                .setContentTitle(when)
                .setContentText(line.replace('\n', ' '))
                .setStyle(new Notification.BigTextStyle().bigText(line))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setShowWhen(false)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setVisibility(Notification.VISIBILITY_PUBLIC);

        if (url != null && url.length() > 0) {
            Intent go = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            go.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent gpi = PendingIntent.getActivity(c, key.hashCode() + 1, go, f);
            b.addAction(new Notification.Action.Builder(
                    android.graphics.drawable.Icon.createWithResource(c, R.drawable.ic_notify),
                    "查看比赛", gpi).build());
        }
        try {
            nm.notify(key.hashCode(), b.build());
        } catch (Exception ignored) {
        }
    }
}