package com.alexandre.focus;

import android.Manifest;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.SystemClock;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.fitness.FitnessLocal;
import com.google.android.gms.fitness.LocalRecordingClient;
import com.google.android.gms.fitness.data.LocalBucket;
import com.google.android.gms.fitness.data.LocalDataPoint;
import com.google.android.gms.fitness.data.LocalDataSet;
import com.google.android.gms.fitness.data.LocalDataType;
import com.google.android.gms.fitness.data.LocalField;
import com.google.android.gms.fitness.request.LocalDataReadRequest;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

/**
 * Lit le compteur de pas materiel (TYPE_STEP_COUNTER).
 * Ce compteur tourne en permanence dans le telephone, meme app fermee,
 * et donne le total de pas depuis le dernier redemarrage.
 */
@CapacitorPlugin(
    name = "StepCounter",
    permissions = { @Permission(strings = { Manifest.permission.ACTIVITY_RECOGNITION }, alias = "activity") }
)
public class StepCounterPlugin extends Plugin implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor stepSensor;
    private boolean listening = false;

    @Override
    public void load() {
        sensorManager = (SensorManager) getContext().getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        }
    }

    @PluginMethod
    public void isAvailable(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("available", stepSensor != null);
        call.resolve(ret);
    }

    @PluginMethod
    public void start(PluginCall call) {
        if (stepSensor == null) {
            call.reject("Capteur de pas absent");
            return;
        }
        if (getPermissionState("activity") != PermissionState.GRANTED) {
            requestPermissionForAlias("activity", call, "activityPermsCallback");
            return;
        }
        listen();
        call.resolve();
    }

    @PermissionCallback
    private void activityPermsCallback(PluginCall call) {
        if (getPermissionState("activity") == PermissionState.GRANTED) {
            listen();
            call.resolve();
        } else {
            call.reject("Permission activite physique refusee");
        }
    }

    private void listen() {
        if (sensorManager == null || stepSensor == null) return;
        sensorManager.unregisterListener(this);
        sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL);
        listening = true;
        MidnightReceiver.schedule(getContext());
        subscribeRecording();
    }

    // Enregistreur de pas Google Play : compte en arrière-plan même quand Focus est fermé
    private boolean recordingAvailable() {
        try {
            return GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(getContext(),
                LocalRecordingClient.LOCAL_RECORDING_CLIENT_MIN_VERSION_CODE) == ConnectionResult.SUCCESS;
        } catch (Throwable t) { return false; }
    }

    private void subscribeRecording() {
        if (!recordingAvailable()) return;
        try { FitnessLocal.getLocalRecordingClient(getContext()).subscribe(LocalDataType.TYPE_STEP_COUNT_DELTA); } catch (Throwable t) {}
    }

    @PluginMethod
    public void getDailySteps(PluginCall call) {
        final JSObject ret = new JSObject();
        final JSObject days = new JSObject();
        if (!recordingAvailable() || getPermissionState("activity") != PermissionState.GRANTED) {
            ret.put("available", false); ret.put("days", days); call.resolve(ret); return;
        }
        try {
            subscribeRecording();
            int n = Math.max(1, Math.min(9, call.getInt("days", 3)));
            Calendar c = Calendar.getInstance();
            long end = c.getTimeInMillis() / 1000;
            c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
            c.add(Calendar.DAY_OF_YEAR, -(n - 1));
            long start = c.getTimeInMillis() / 1000;
            LocalDataReadRequest req = new LocalDataReadRequest.Builder()
                .aggregate(LocalDataType.TYPE_STEP_COUNT_DELTA)
                .bucketByTime(1, TimeUnit.DAYS)
                .setTimeRange(start, end, TimeUnit.SECONDS)
                .build();
            final SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            FitnessLocal.getLocalRecordingClient(getContext()).readData(req)
                .addOnSuccessListener(resp -> {
                    for (LocalBucket b : resp.getBuckets()) {
                        for (LocalDataSet ds : b.getDataSets()) {
                            for (LocalDataPoint dp : ds.getDataPoints()) {
                                String k = f.format(new Date(dp.getStartTime(TimeUnit.MILLISECONDS)));
                                int v = 0;
                                for (LocalField fl : dp.getDataType().getFields()) v += dp.getValue(fl).asInt();
                                int prev = days.has(k) ? days.optInt(k) : 0;
                                days.put(k, prev + v);
                            }
                        }
                    }
                    ret.put("available", true); ret.put("days", days); call.resolve(ret);
                })
                .addOnFailureListener(e -> { ret.put("available", false); ret.put("days", days); call.resolve(ret); });
        } catch (Throwable t) {
            ret.put("available", false); ret.put("days", days); call.resolve(ret);
        }
    }

    /** Valeurs du compteur relevees chaque nuit a 00:00, par jour (yyyy-MM-dd). */
    @PluginMethod
    public void getSnapshots(PluginCall call) {
        JSObject snaps = new JSObject();
        android.content.SharedPreferences p = getContext().getSharedPreferences(MidnightReceiver.PREFS, Context.MODE_PRIVATE);
        for (java.util.Map.Entry<String, ?> e : p.getAll().entrySet()) {
            if (e.getKey().startsWith("mid_") && e.getValue() instanceof Long) {
                snaps.put(e.getKey().substring(4), (long) (Long) e.getValue());
            }
        }
        JSObject ret = new JSObject();
        ret.put("snapshots", snaps);
        call.resolve(ret);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_STEP_COUNTER) return;
        JSObject ret = new JSObject();
        ret.put("total", (long) event.values[0]);
        // Heure du dernier demarrage du telephone (le compteur repart de 0 a ce moment-la)
        ret.put("bootTime", System.currentTimeMillis() - SystemClock.elapsedRealtime());
        notifyListeners("steps", ret);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    protected void handleOnResume() {
        // Re-enregistrer force une lecture immediate du total au retour dans l'app
        if (listening) listen();
    }

    @Override
    protected void handleOnDestroy() {
        if (sensorManager != null) sensorManager.unregisterListener(this);
    }
}
