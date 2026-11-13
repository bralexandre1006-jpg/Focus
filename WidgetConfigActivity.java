package com.alexandre.focus;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

/** Réglages d'un widget : ouvert par un appui long sur le widget, puis « Modifier ». */
public class WidgetConfigActivity extends Activity {
    int id = AppWidgetManager.INVALID_APPWIDGET_ID, kind = 1;
    FocusWidget.Cfg cfg;
    ImageView preview;
    TextView bgVal;
    float dp;
    LinearLayout root;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Bundle ex = getIntent().getExtras();
        if (ex != null) id = ex.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        Intent res = new Intent(); res.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        setResult(RESULT_CANCELED, res);
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return; }
        AppWidgetManager m = AppWidgetManager.getInstance(this);
        kind = FocusWidget.kindOfId(m, id);
        cfg = FocusWidget.Cfg.load(this, id, kind);
        FocusWidget.fonts(this);
        dp = getResources().getDisplayMetrics().density;
        getWindow().setStatusBarColor(0xFF0B0B0F);
        getWindow().setNavigationBarColor(0xFF0B0B0F);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(0xFF0B0B0F);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = px(20);
        root.setPadding(p, px(36), p, px(28));
        sv.addView(root);

        TextView t = label("Widget Focus", 28, Color.WHITE, true); root.addView(t);
        TextView st = label("Choisis ce que le widget affiche et son style.", 15, 0xFF8E8E9E, false);
        st.setPadding(0, px(4), 0, px(16)); root.addView(st);

        FrameLayout box = new FrameLayout(this);
        GradientDrawable bgd = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[] { 0xFF2A2A30, 0xFF0E0E12 });
        bgd.setCornerRadius(px(22)); box.setBackground(bgd);
        box.setPadding(px(18), px(18), px(18), px(18));
        preview = new ImageView(this);
        preview.setAdjustViewBounds(true);
        box.addView(preview, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        root.addView(box, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(kind == 2 ? 130 : kind == 0 ? 150 : 220)));

        section("Données");
        choices(new String[] { "Pas", "Repas", "Pas + brûlées" }, cfg.metric, i -> cfg.metric = i);
        section("Style");
        java.util.List<String> all = new java.util.ArrayList<>(java.util.Arrays.asList("Cadran", "Jauge", "Chiffre", "Semaine", "Batterie", "Points", "Gros chiffre"));
        all.addAll(java.util.Arrays.asList(FocusWidget.DESIGN_NAMES));
        int nr = (all.size() + 2) / 3; int[][] sm = new int[nr][]; String[][] sn = new String[nr][];
        for (int r = 0; r < nr; r++) { int c0 = r * 3, c1 = Math.min(all.size(), c0 + 3); sm[r] = new int[c1 - c0]; sn[r] = new String[c1 - c0]; for (int j = c0; j < c1; j++) { sm[r][j - c0] = j; sn[r][j - c0] = all.get(j); } }
        groupStart();
        for (int r = 0; r < nr; r++) { final int[] sr = sm[r]; int sel = -1; for (int j = 0; j < sr.length; j++) if (sr[j] == cfg.style) sel = j; choices(sn[r], sel, i -> cfg.style = sr[i]); }
        groupEnd();
        section("Calories (style Sommet)");
        groupStart();
        String[][] kn = { { "Aucune", "Gros", "Discret" }, { "Sous les pas", "Barre", "Pastille" }, { "Anneau", "Bas droite", "Côte à côte" } };
        for (int r = 0; r < 3; r++) { final int kb = r * 3; choices(kn[r], cfg.kv >= kb && cfg.kv < kb + 3 ? cfg.kv - kb : -1, i -> cfg.kv = kb + i); }
        groupEnd();
        section("Thème");
        groupStart();
        for (int r = 0; r * 4 < FocusWidget.THEME_NAMES.length; r++) { final int tb = r * 4; String[] n = java.util.Arrays.copyOfRange(FocusWidget.THEME_NAMES, tb, Math.min(FocusWidget.THEME_NAMES.length, tb + 4)); choices(n, cfg.col >= tb && cfg.col < tb + 4 ? cfg.col - tb : -1, i -> cfg.col = tb + i); }
        groupEnd();
        if (kind == 0 || kind == 1) {
            section("Forme du fond");
            choices(new String[] { "Arrondi", "Rond" }, cfg.shape, i -> cfg.shape = i);
        }
        section("Fond");
        bgVal = label("", 14, 0xFF8E8E9E, true);
        root.addView(bgVal);
        SeekBar sb = new SeekBar(this);
        sb.setMax(100); sb.setProgress(cfg.bg);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int v, boolean u) { cfg.bg = v; refresh(); }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        sb.setPadding(px(4), px(12), px(4), px(12));
        root.addView(sb);

        TextView ok = label("Valider", 17, 0xFF0B0B0F, true);
        ok.setGravity(Gravity.CENTER);
        GradientDrawable okb = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[] { 0xFFD7FF3A, 0xFF7BE07E });
        okb.setCornerRadius(px(18)); ok.setBackground(okb);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(56));
        lp.topMargin = px(22);
        ok.setOnClickListener(v -> {
            cfg.save(this, id);
            FocusWidget.render(this, AppWidgetManager.getInstance(this), id);
            Intent r = new Intent(); r.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
            setResult(RESULT_OK, r); finish();
        });
        root.addView(ok, lp);
        setContentView(sv);
        refresh();
    }

    interface Pick { void on(int i); }

    int px(float v) { return Math.round(v * dp); }

    TextView label(String s, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(size); t.setTextColor(color);
        t.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        return t;
    }

    void section(String s) {
        TextView t = label(s.toUpperCase(), 12, 0xFF8E8E9E, true);
        t.setLetterSpacing(.1f); t.setPadding(px(2), px(18), 0, px(8));
        root.addView(t);
    }

    java.util.List<TextView[]> group;
    java.util.List<java.util.List<TextView[]>> groups = new java.util.ArrayList<>();
    void groupStart() { group = new java.util.ArrayList<>(); groups.add(group); }
    void groupEnd() { group = null; }
    void choices(String[] opts, int sel, Pick pick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView[] all = new TextView[opts.length];
        for (int i = 0; i < opts.length; i++) {
            final int k = i;
            TextView c = label(opts[i], opts.length > 3 ? 12 : 14, Color.WHITE, true);
            c.setTypeface(FocusWidget.T8);
            c.setGravity(Gravity.CENTER); c.setMaxLines(1);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, px(46), 1);
            if (i > 0) lp.leftMargin = px(8);
            row.addView(c, lp); all[i] = c;
            c.setOnClickListener(v -> { pick.on(k); for (java.util.List<TextView[]> g : groups) if (g.contains(all)) for (TextView[] o : g) style(o, -1); style(all, k); refresh(); });
        }
        style(all, sel);
        if (group != null) { LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); rl.topMargin = group.isEmpty() ? 0 : px(8); group.add(all); root.addView(row, rl); return; }
        root.addView(row);
    }

    void style(TextView[] all, int sel) {
        for (int i = 0; i < all.length; i++) {
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(px(14));
            g.setColor(i == sel ? Color.WHITE : 0xFF1C1C23);
            all[i].setBackground(g);
            all[i].setTextColor(i == sel ? 0xFF0B0B0F : Color.WHITE);
        }
    }

    void refresh() {
        bgVal.setText(cfg.bg == 0 ? "Transparent" : cfg.bg + " %");
        int[] s = FocusWidget.sizeDp(AppWidgetManager.getInstance(this), id, kind);
        float k = Math.min(dp, 640f / Math.max(s[0], s[1]));
        preview.setImageBitmap(FocusWidget.draw(cfg, FocusWidget.data(this), Math.round(s[0] * k), Math.round(s[1] * k), k));
    }
}
