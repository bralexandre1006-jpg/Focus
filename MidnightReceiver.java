package com.alexandre.focus;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

/**
 * Chaque nuit a 00:00, note la valeur du compteur de pas du telephone.
 * Focus s'en sert pour attribuer chaque pas au bon jour, meme si l'app etait fermee.
 */
public class MidnightReceiver extends BroadcastReceiver {

    public static final String PREFS = "focus_steps";
    public static final String ACTION = "com.alexandre.focus.MIDNIGHT";

    static String dayKey(Date d) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(d);
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        final Context app = ctx.getApplicationContext();
        String action = intent != null ? intent.getAction() : null;

        // Redemarrage ou mise a jour de l'app : on reprogramme simplement l'alarme
        if (!ACTION.equals(action)) {
            schedule(app);
            try { WidgetTicker.schedule(app); } catch (Throwable t) {}
            return;
        }

        final String key = "mid_" + dayKey(new Date());
        schedule(app); // prochaine nuit

        final SensorManager sm = (SensorManager) app.getSystemService(Context.SENSOR_SERVICE);
        final Sensor sensor = sm != null ? sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) : null;
        if (sensor == null) return;

        final PendingResult pending = goAsync();
        final Handler handler = new Handler(Looper.getMainLooper());
        final boolean[] done = { false };

        final SensorEventListener listener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                if (done[0]) return;
                done[0] = true;
                sm.unregisterListener(this);
                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putLong(key, (long) event.values[0]).apply();
                prune(app);
                pending.finish();
            }

            @Override
            public void onAccuracyChanged(Sensor s, int accuracy) {}
        };

        try {
            sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL, handler);
        } catch (Exception e) {
            pending.finish();
            return;
        }

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (done[0]) return;
                done[0] = true;
                sm.unregisterListener(listener);
                pending.finish();
            }
        }, 9000);
    }

    /** Programme l'alarme pour la prochaine minuit (00:00:05). */
    public static void schedule(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent i = new Intent(c, MidnightReceiver.class).setAction(ACTION);
        PendingIntent pi = PendingIntent.getBroadcast(c, 4242, i,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 5);
        cal.set(Calendar.MILLISECOND, 0);
        long t = cal.getTimeInMillis();

        try {
            boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi);
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi);
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi);
        }
    }

    /** Garde seulement les 60 derniers jours. */
    static void prune(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -60);
        String limit = "mid_" + dayKey(cal.getTime());
        SharedPreferences.Editor ed = p.edit();
        for (Map.Entry<String, ?> e : p.getAll().entrySet()) {
            if (e.getKey().startsWith("mid_") && e.getKey().compareTo(limit) < 0) ed.remove(e.getKey());
        }
        ed.apply();
    }
}
