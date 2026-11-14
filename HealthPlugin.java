package com.alexandre.focus;

import android.content.Intent;
import android.os.Build;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.health.connect.client.HealthConnectClient;
import androidx.health.connect.client.PermissionController;
import androidx.health.connect.client.records.WeightRecord;
import androidx.health.connect.client.request.ReadRecordsRequest;
import androidx.health.connect.client.response.ReadRecordsResponse;
import androidx.health.connect.client.time.TimeRangeFilter;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlin.coroutines.intrinsics.IntrinsicsKt;
import kotlin.jvm.JvmClassMappingKt;

/** Poids lus dans Health Connect (ex. balance Tefal Goodvibes), en lecture seule. */
@CapacitorPlugin(name = "Health")
public class HealthPlugin extends Plugin {
    static final String READ_WEIGHT = "android.permission.health.READ_WEIGHT";
    static final String PROVIDER = "com.google.android.apps.healthdata";

    interface Done { void ok(Object r); }

    /** Appelle une fonction « suspend » Kotlin depuis Java. */
    static Continuation<Object> cont(PluginCall call, Done d) {
        return new Continuation<Object>() {
            @Override public CoroutineContext getContext() { return EmptyCoroutineContext.INSTANCE; }
            @Override public void resumeWith(Object r) {
                if (r instanceof kotlin.Result.Failure) { Throwable t = ((kotlin.Result.Failure) r).exception; call.reject(t != null ? String.valueOf(t.getMessage()) : "erreur"); }
                else { try { d.ok(r); } catch (Throwable t) { call.reject(String.valueOf(t.getMessage())); } }
            }
        };
    }
    static void run(Object ret, Continuation<Object> c) { if (ret != IntrinsicsKt.getCOROUTINE_SUSPENDED()) c.resumeWith(ret); }

    boolean available() {
        return Build.VERSION.SDK_INT >= 26 && HealthConnectClient.getSdkStatus(getContext(), PROVIDER) == HealthConnectClient.SDK_AVAILABLE;
    }

    @PluginMethod
    public void status(PluginCall call) {
        JSObject o = new JSObject();
        if (!available()) { o.put("available", false); o.put("granted", false); call.resolve(o); return; }
        try {
            HealthConnectClient hc = HealthConnectClient.getOrCreate(getContext());
            Continuation<Object> c = cont(call, r -> { o.put("available", true); o.put("granted", ((Set<?>) r).contains(READ_WEIGHT)); call.resolve(o); });
            run(hc.getPermissionController().getGrantedPermissions(c), c);
        } catch (Throwable t) { call.reject(String.valueOf(t.getMessage())); }
    }

    @PluginMethod
    public void request(PluginCall call) {
        if (!available()) { call.reject("indisponible"); return; }
        try {
            ActivityResultContract<Set<String>, Set<String>> ct = PermissionController.createRequestPermissionResultContract(PROVIDER);
            Intent i = ct.createIntent(getContext(), new HashSet<>(Collections.singleton(READ_WEIGHT)));
            startActivityForResult(call, i, "permDone");
        } catch (Throwable t) { call.reject(String.valueOf(t.getMessage())); }
    }

    @ActivityCallback
    private void permDone(PluginCall call, ActivityResult res) {
        if (call == null) return;
        Set<String> got = PermissionController.createRequestPermissionResultContract(PROVIDER).parseResult(res.getResultCode(), res.getData());
        JSObject o = new JSObject(); o.put("granted", got != null && got.contains(READ_WEIGHT)); call.resolve(o);
    }

    /** Ouvre l'écran Health Connect du téléphone (données et autorisations). */
    @PluginMethod
    public void openSettings(PluginCall call) {
        String[] acts = { "android.health.connect.action.HEALTH_HOME_SETTINGS", "androidx.health.ACTION_HEALTH_CONNECT_SETTINGS", "android.health.connect.action.MANAGE_HEALTH_DATA" };
        for (String a : acts) {
            try { Intent i = new Intent(a).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); getActivity().startActivity(i); call.resolve(); return; } catch (Throwable ignored) {}
        }
        call.reject("introuvable");
    }

    /** Pesées depuis « since » (ms), de la plus récente à la plus ancienne. */
    @PluginMethod
    public void readWeights(PluginCall call) {
        if (!available()) { call.reject("indisponible"); return; }
        try {
            long since = (long) call.getDouble("since", 0.0).doubleValue();
            if (since <= 0) since = System.currentTimeMillis() - 60L * 86400000L;
            HealthConnectClient hc = HealthConnectClient.getOrCreate(getContext());
            ReadRecordsRequest<WeightRecord> req = new ReadRecordsRequest<>(JvmClassMappingKt.getKotlinClass(WeightRecord.class),
                TimeRangeFilter.after(Instant.ofEpochMilli(since)), Collections.emptySet(), false, 500, null);
            Continuation<Object> c = cont(call, r -> {
                JSArray a = new JSArray();
                for (Object x : ((ReadRecordsResponse<?>) r).getRecords()) {
                    WeightRecord w = (WeightRecord) x; JSObject o = new JSObject();
                    o.put("t", w.getTime().toEpochMilli()); if (w.getZoneOffset() != null) o.put("off", w.getZoneOffset().getTotalSeconds());
                    try { o.put("src", w.getMetadata().getDataOrigin().getPackageName()); } catch (Throwable ignored) {} o.put("kg", w.getWeight().getKilograms()); a.put(o);
                }
                JSObject out = new JSObject(); out.put("items", a); call.resolve(out);
            });
            run(hc.readRecords(req, c), c);
        } catch (Throwable t) { call.reject(String.valueOf(t.getMessage())); }
    }
}
