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

/** Widget Horloge : 28 styles (res/layout/clock_N.xml), l'heure vit seule (TextClock / AnalogClock), un appui ouvre l'app Horloge. */
public class ClockWidget extends AppWidgetProvider {
    static final String PREFS = "focus_clock";
    static final String[] NAMES = { "Gros chiffres", "Heure et date", "Empilé", "Fin", "Jour", "Large", "Accent", "Secondes", "12 heures", "Calendrier",
        "Néon", "Pastille", "Verre", "Carte", "Lime", "Terminal", "Rétro", "Analogique", "Analogique minimal", "Analogique néon",
        "Montre classique", "Montre sport", "Chronographe", "Montre dorée", "Bauhaus", "Cercle néon", "Points", "Montre verre" };

    static int layout(Context c, int d) { return c.getResources().getIdentifier("clock_" + d, "layout", c.getPackageName()); }

    static int design(Context c, int id) {
        return Math.max(0, Math.min(NAMES.length - 1, c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("d_" + id, 0)));
    }

    static void render(Context c, AppWidgetManager m, int id) {
        RemoteViews v = new RemoteViews(c.getPackageName(), layout(c, design(c, id)));
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
        for (int id : ids) e.remove("d_" + id);
        e.apply();
    }
}
