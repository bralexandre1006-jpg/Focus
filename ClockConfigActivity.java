package com.alexandre.focus;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Choix du style du widget Horloge : flèches autour d'un aperçu en direct. */
public class ClockConfigActivity extends Activity {
    int id = AppWidgetManager.INVALID_APPWIDGET_ID, d;
    boolean gl;
    TextView[] gb = new TextView[2];
    FrameLayout box;
    TextView name, num;
    float dp;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Bundle ex = getIntent().getExtras();
        if (ex != null) id = ex.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        Intent res = new Intent(); res.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        setResult(RESULT_CANCELED, res);
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return; }
        d = ClockWidget.design(this, id); gl = ClockWidget.glass(this, id);
        dp = getResources().getDisplayMetrics().density;
        getWindow().setStatusBarColor(0xFF0B0B0F);
        getWindow().setNavigationBarColor(0xFF0B0B0F);
        FocusWidget.fonts(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0B0B0F);
        root.setPadding(px(20), px(36), px(20), px(28));

        TextView t = label("Widget Horloge", 28, Color.WHITE); t.setTypeface(FocusWidget.T8); root.addView(t);
        TextView st = label("Un appui sur le widget ouvre l'app Horloge.", 15, 0xFF8E8E9E);
        st.setPadding(0, px(4), 0, px(22)); root.addView(st);

        LinearLayout car = new LinearLayout(this);
        car.setOrientation(LinearLayout.HORIZONTAL); car.setGravity(Gravity.CENTER_VERTICAL);
        car.addView(arrow("‹", -1), new LinearLayout.LayoutParams(px(44), px(44)));
        box = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[] { 0xFF2A2A30, 0xFF0E0E12 });
        bg.setCornerRadius(px(22)); box.setBackground(bg); box.setPadding(px(10), px(10), px(10), px(10));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0, px(170), 1);
        blp.leftMargin = px(8); blp.rightMargin = px(8);
        car.addView(box, blp);
        car.addView(arrow("›", 1), new LinearLayout.LayoutParams(px(44), px(44)));
        root.addView(car);

        name = label("", 18, Color.WHITE); name.setTypeface(FocusWidget.T8); name.setGravity(Gravity.CENTER); name.setPadding(0, px(16), 0, 0); root.addView(name);
        num = label("", 13, 0xFF8E8E9E); num.setGravity(Gravity.CENTER); root.addView(num);

        TextView fl = label("FOND", 12, 0xFF8E8E9E); fl.setLetterSpacing(.1f); fl.setPadding(px(2), px(22), 0, px(8)); root.addView(fl);
        LinearLayout gr = new LinearLayout(this); gr.setOrientation(LinearLayout.HORIZONTAL);
        String[] gn = { "Aucun", "Liquid glass" };
        for (int i = 0; i < 2; i++) {
            final boolean v = i == 1; TextView c = label(gn[i], 14, Color.WHITE); c.setTypeface(FocusWidget.T8); c.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, px(46), 1); if (i > 0) cl.leftMargin = px(8);
            c.setOnClickListener(x -> { gl = v; refresh(); }); gr.addView(c, cl); gb[i] = c;
        }
        root.addView(gr);

        TextView ok = label("Valider", 17, 0xFF0B0B0F); ok.setTypeface(FocusWidget.T8); ok.setGravity(Gravity.CENTER);
        GradientDrawable okb = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[] { 0xFFD7FF3A, 0xFF7BE07E });
        okb.setCornerRadius(px(18)); ok.setBackground(okb);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(56)); lp.topMargin = px(28);
        ok.setOnClickListener(v -> {
            getSharedPreferences(ClockWidget.PREFS, MODE_PRIVATE).edit().putInt("d_" + id, d).putBoolean("g_" + id, gl).apply();
            ClockWidget.render(this, AppWidgetManager.getInstance(this), id);
            Intent r = new Intent(); r.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
            setResult(RESULT_OK, r); finish();
        });
        root.addView(ok, lp);
        setContentView(root);
        refresh();
    }

    int px(float v) { return Math.round(v * dp); }

    TextView label(String s, float size, int color) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color); return t;
    }

    TextView arrow(String s, int dir) {
        TextView a = label(s, 28, Color.WHITE); a.setGravity(Gravity.CENTER); a.setPadding(0, 0, 0, px(4));
        GradientDrawable g = new GradientDrawable(); g.setShape(GradientDrawable.OVAL); g.setColor(0xFF1C1C23); a.setBackground(g);
        a.setOnClickListener(v -> { int n = ClockWidget.NAMES.length; d = ((d + dir) % n + n) % n; refresh(); });
        return a;
    }

    void refresh() {
        box.removeAllViews();
        try {
            android.view.View w = LayoutInflater.from(this).inflate(ClockWidget.layout(this, d), box, false);
            if (gl) w.setBackgroundResource(R.drawable.clk_liquid);
            box.addView(w);
        } catch (Exception ignored) {}
        for (int i = 0; i < 2; i++) {
            GradientDrawable g = new GradientDrawable(); g.setCornerRadius(px(14)); boolean on = (i == 1) == gl;
            g.setColor(on ? Color.WHITE : 0xFF1C1C23); gb[i].setBackground(g); gb[i].setTextColor(on ? 0xFF0B0B0F : Color.WHITE);
        }
        name.setText(ClockWidget.NAMES[d]);
        num.setText((d + 1) + " / " + ClockWidget.NAMES.length);
    }
}
