package com.alexandre.focus;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

/** Minuteur de repos : notification en cours (Live Update / Dynamic Island) et alerte sonore à la fin. */
public class RestTimer {
    static final int LIVE_ID = 9501, END_ID = 9502;
    static final String LIVE_CH = "rest_live";
    static final String[] SOUNDS = { "bip", "cloche", "gong", "sifflet", "arcade" };

    static NotificationManager nm(Context c) { return (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE); }

    static Uri soundUri(Context c, String s) {
        if ("systeme".equals(s)) return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        for (String k : SOUNDS) if (k.equals(s)) return Uri.parse("android.resource://" + c.getPackageName() + "/raw/rest_" + k);
        return null;
    }

    static String endChannel(Context c, String s) {
        String id = "rest_end_" + s;
        if (Build.VERSION.SDK_INT >= 26 && nm(c).getNotificationChannel(id) == null) {
            NotificationChannel ch = new NotificationChannel(id, "Fin du repos (" + s + ")", NotificationManager.IMPORTANCE_HIGH);
            Uri u = soundUri(c, s);
            if (u == null) ch.setSound(null, null);
            else ch.setSound(u, new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            ch.enableVibration(true); ch.setVibrationPattern(new long[] { 0, 250, 120, 250 });
            nm(c).createNotificationChannel(ch);
        }
        return id;
    }

    static PendingIntent openApp(Context c) {
        Intent i = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
        if (i == null) i = new Intent();
        i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(c, 9500, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    static PendingIntent endIntent(Context c, String label, String sound) {
        Intent i = new Intent(c, End.class).putExtra("label", label).putExtra("sound", sound);
        return PendingIntent.getBroadcast(c, 9502, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    static int icon(Context c) {
        int r = c.getResources().getIdentifier("ic_stat_focus", "drawable", c.getPackageName());
        return r != 0 ? r : c.getApplicationInfo().icon;
    }

    static void call(Object o, String m, Class<?> t, Object v) {
        try { o.getClass().getMethod(m, t).invoke(o, v); } catch (Throwable ignored) {}
    }

    static void start(Context c, long end, String label, String sound) {
        if (Build.VERSION.SDK_INT >= 26 && nm(c).getNotificationChannel(LIVE_CH) == null) {
            NotificationChannel ch = new NotificationChannel(LIVE_CH, "Minuteur de repos", NotificationManager.IMPORTANCE_DEFAULT);
            ch.setSound(null, null); ch.enableVibration(false); ch.setShowBadge(false);
            nm(c).createNotificationChannel(ch);
        }
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, LIVE_CH) : new Notification.Builder(c);
        b.setSmallIcon(icon(c)).setContentTitle("Repos").setContentText(label).setOngoing(true).setOnlyAlertOnce(true)
            .setWhen(end).setShowWhen(true).setUsesChronometer(true).setCategory(Notification.CATEGORY_STOPWATCH)
            .setContentIntent(openApp(c)).setColor(0xFFD7FF3A).setVisibility(Notification.VISIBILITY_PUBLIC);
        if (Build.VERSION.SDK_INT >= 24) b.setChronometerCountDown(true);
        if (Build.VERSION.SDK_INT >= 31) b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        // Android 16 : Live Update ; sans texte court, la pastille affiche le compte à rebours
        call(b, "setRequestPromotedOngoing", boolean.class, true);
        Notification n = b.build();
        n.extras.putBoolean("android.requestPromotedOngoing", true);
        try { nm(c).notify(LIVE_ID, n); } catch (SecurityException ignored) {}

        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = endIntent(c, label, sound);
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, end, pi);
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, end, pi);
        } catch (SecurityException e) { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, end, pi); }
    }

    static void stop(Context c) {
        nm(c).cancel(LIVE_ID);
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        am.cancel(endIntent(c, "", ""));
    }

    static MediaPlayer mp;
    static void preview(Context c, String s) {
        try {
            if (mp != null) { mp.release(); mp = null; }
            Uri u = soundUri(c, s);
            if (u == null) return;
            mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            mp.setDataSource(c, u);
            mp.setOnPreparedListener(MediaPlayer::start);
            mp.setOnCompletionListener(p -> { p.release(); if (mp == p) mp = null; });
            mp.prepareAsync();
        } catch (Throwable ignored) {}
    }

    public static class End extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent i) {
            String sound = i.getStringExtra("sound");
            if (sound == null) sound = "bip";
            String label = i.getStringExtra("label");
            nm(c).cancel(LIVE_ID);
            Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, endChannel(c, sound)) : new Notification.Builder(c);
            b.setSmallIcon(icon(c)).setContentTitle("Repos terminé 💪").setContentText(label == null ? "On repart" : "On repart : " + label)
                .setAutoCancel(true).setContentIntent(openApp(c)).setColor(0xFFD7FF3A).setCategory(Notification.CATEGORY_ALARM)
                .setTimeoutAfter(60000);
            try { nm(c).notify(END_ID, b.build()); } catch (SecurityException ignored) {}
        }
    }
}
