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
import android.os.SystemClock;

import java.util.Date;

/** Met à jour les widgets toutes les 2 minutes avec le compteur de pas du téléphone, même app fermée. */
public class WidgetTicker extends BroadcastReceiver {
    static final long EVERY = 2 * 60 * 1000L;

    static boolean hasWidgets(Context c) {
        android.appwidget.AppWidgetManager m = android.appwidget.AppWidgetManager.getInstance(c);
        for (Class<?> k : FocusWidget.ALL) if (m.getAppWidgetIds(new android.content.ComponentName(c, k)).length > 0) return true;
        return false;
    }

    static void schedule(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = PendingIntent.getBroadcast(c, 4343, new Intent(c, WidgetTicker.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        if (!hasWidgets(c)) { am.cancel(pi); return; }
        long t = SystemClock.elapsedRealtime() + EVERY;
        // Réveil non forcé : tourne quand l'écran est allumé, ne vide pas la batterie écran éteint
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExact(AlarmManager.ELAPSED_REALTIME, t, pi);
            else am.set(AlarmManager.ELAPSED_REALTIME, t, pi);
        } catch (SecurityException e) { am.set(AlarmManager.ELAPSED_REALTIME, t, pi); }
    }

    /** Recalcule les pas du jour à partir du total matériel et de la dernière valeur connue. */
    static void apply(Context c, long total) {
        SharedPreferences p = c.getSharedPreferences(FocusWidget.PREFS, Context.MODE_PRIVATE);
        String today = MidnightReceiver.dayKey(new Date());
        SharedPreferences.Editor e = p.edit();
        long refT = p.getLong("refTotal", -1); int refS = p.getInt("steps", 0);
        int steps;
        if (!today.equals(p.getString("date", ""))) {
            long mid = c.getSharedPreferences(MidnightReceiver.PREFS, Context.MODE_PRIVATE).getLong("mid_" + today, -1);
            steps = mid >= 0 && total >= mid ? (int) (total - mid) : 0;
            e.putString("date", today).putInt("kcal", 0);
        } else if (refT < 0) steps = refS;
        else if (total >= refT) steps = refS + (int) (total - refT);
        else steps = refS + (int) total;
        e.putInt("steps", Math.max(0, steps)).putLong("refTotal", total).apply();
        FocusWidget.push(c);
    }

    /** Repli : l'enregistreur de pas Google Play, qui compte aussi en arrière-plan. */
    static void fromRecording(Context c, PendingResult pending) {
        try {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            long end = cal.getTimeInMillis() / 1000;
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0); cal.set(java.util.Calendar.MINUTE, 0); cal.set(java.util.Calendar.SECOND, 0);
            com.google.android.gms.fitness.request.LocalDataReadRequest req = new com.google.android.gms.fitness.request.LocalDataReadRequest.Builder()
                .aggregate(com.google.android.gms.fitness.data.LocalDataType.TYPE_STEP_COUNT_DELTA)
                .bucketByTime(1, java.util.concurrent.TimeUnit.DAYS).setTimeRange(cal.getTimeInMillis() / 1000, end, java.util.concurrent.TimeUnit.SECONDS).build();
            com.google.android.gms.fitness.FitnessLocal.getLocalRecordingClient(c).readData(req)
                .addOnSuccessListener(r -> {
                    int v = 0;
                    for (com.google.android.gms.fitness.data.LocalBucket b : r.getBuckets())
                        for (com.google.android.gms.fitness.data.LocalDataSet ds : b.getDataSets())
                            for (com.google.android.gms.fitness.data.LocalDataPoint dp : ds.getDataPoints())
                                for (com.google.android.gms.fitness.data.LocalField f : dp.getDataType().getFields()) v += dp.getValue(f).asInt();
                    SharedPreferences p = c.getSharedPreferences(FocusWidget.PREFS, Context.MODE_PRIVATE);
                    String today = MidnightReceiver.dayKey(new Date());
                    boolean same = today.equals(p.getString("date", ""));
                    if (!same || v > p.getInt("steps", 0)) {
                        SharedPreferences.Editor e = p.edit().putInt("steps", v).putString("date", today).remove("refTotal");
                        if (!same) e.putInt("kcal", 0);
                        e.apply();
                    }
                    FocusWidget.push(c); pending.finish();
                })
                .addOnFailureListener(x -> { FocusWidget.push(c); pending.finish(); });
        } catch (Throwable t) { try { FocusWidget.push(c); } catch (Throwable ignored) {} pending.finish(); }
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        final Context app = ctx.getApplicationContext();
        schedule(app);
        final SensorManager sm = (SensorManager) app.getSystemService(Context.SENSOR_SERVICE);
        final Sensor sensor = sm != null ? sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) : null;
        if (sensor == null) { FocusWidget.push(app); return; }
        final PendingResult pending = goAsync();
        final Handler h = new Handler(Looper.getMainLooper());
        final boolean[] done = { false };
        final SensorEventListener l = new SensorEventListener() {
            @Override public void onSensorChanged(SensorEvent ev) {
                if (done[0]) return; done[0] = true; sm.unregisterListener(this);
                try { apply(app, (long) ev.values[0]); } catch (Throwable ignored) {}
                pending.finish();
            }
            @Override public void onAccuracyChanged(Sensor s, int a) {}
        };
        try { sm.registerListener(l, sensor, SensorManager.SENSOR_DELAY_NORMAL, h); }
        catch (Exception ex) { pending.finish(); return; }
        h.postDelayed(() -> { if (done[0]) return; done[0] = true; sm.unregisterListener(l); fromRecording(app, pending); }, 6000);
    }
}
