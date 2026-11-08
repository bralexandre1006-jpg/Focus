package com.alexandre.focus;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Widget d'écran d'accueil : pas et calories du jour, envoyés par l'app. */
public class FocusWidget extends AppWidgetProvider {
    static final String PREFS = "focus_widget";

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) m.updateAppWidget(id, build(c));
    }

    static String fmt(int n) {
        return String.format(Locale.FRANCE, "%,d", n).replace(' ', ' ').replace(' ', ' ');
    }

    static RemoteViews build(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        // Après minuit, tant que l'app n'a rien envoyé, on repart de zéro
        boolean fresh = today.equals(p.getString("date", ""));
        int steps = fresh ? p.getInt("steps", 0) : 0, sg = p.getInt("stepGoal", 8000);
        int kcal = fresh ? p.getInt("kcal", 0) : 0, kg = p.getInt("kcalGoal", 2000);
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.focus_widget);
        v.setTextViewText(R.id.w_steps, fmt(steps));
        v.setTextViewText(R.id.w_steps_goal, " / " + fmt(sg) + " pas");
        v.setProgressBar(R.id.w_steps_bar, 100, sg > 0 ? Math.min(100, steps * 100 / sg) : 0, false);
        v.setTextViewText(R.id.w_kcal, fmt(kcal));
        v.setTextViewText(R.id.w_kcal_goal, " / " + fmt(kg) + " kcal");
        v.setProgressBar(R.id.w_kcal_bar, 100, kg > 0 ? Math.min(100, kcal * 100 / kg) : 0, false);
        Intent i = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
        if (i != null) v.setOnClickPendingIntent(R.id.w_root,
            PendingIntent.getActivity(c, 0, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        return v;
    }

    static void push(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, FocusWidget.class));
        for (int id : ids) m.updateAppWidget(id, build(c));
    }
}
