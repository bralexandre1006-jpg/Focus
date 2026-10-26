package com.alexandre.focus;

import android.Manifest;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.SystemClock;

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
