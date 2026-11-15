package com.alexandre.focus;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.AlarmClock;
import android.widget.RemoteViews;

/** Widget Horloge : 20 montres à aiguilles (res/layout/clock_N.xml), l'heure vit seule (AnalogClock), un appui ouvre l'app Horloge. */
public class ClockWidget extends AppWidgetProvider {
    static final String PREFS = "focus_clock";
    static final String[] NAMES = { "Noir classique", "Blanc", "Sport lime", "Chrono", "Doré", "Bauhaus", "Néon", "Points", "Bleu nuit", "Militaire",
        "Rouge", "Minimal", "Radar", "Pilote", "Rose", "Graphite", "Gare", "Arc-en-ciel", "Lune", "Verre" };

    static int layout(Context c, int d) { return c.getResources().getIdentifier("clock_" + d, "layout", c.getPackageName()); }

    static int design(Context c, int id) {
        return Math.max(0, Math.min(NAMES.length - 1, c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("d_" + id, 0)));
    }

    static boolean glass(Context c, int id) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("g_" + id, false); }

    static void render(Context c, AppWidgetManager m, int id) {
        RemoteViews v = new RemoteViews(c.getPackageName(), layout(c, design(c, id)));
        if (glass(c, id)) v.setInt(R.id.c_root, "setBackgroundResource", R.drawable.clk_liquid);
        Intent i = new Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        v.setOnClickPendingIntent(R.id.c_root, PendingIntent.getActivity(c, 8000 + id, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        m.updateAppWidget(id, v);
    }

    static void pushAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        for (int id : m.getAppWidgetIds(new ComponentName(c, ClockWidget.class))) render(c, m, id);
    }

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) { for (int id : ids) render(c, m, id); }

    @Override
    public void onDeleted(Context c, int[] ids) {
        SharedPreferences.Editor e = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        for (int id : ids) { e.remove("d_" + id); e.remove("g_" + id); }
        e.apply();
    }
}
