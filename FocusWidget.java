package com.alexandre.focus;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.RemoteViews;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Widgets d'écran d'accueil : pas et calories du jour, dessinés en image pour avoir plusieurs styles. */
public class FocusWidget extends AppWidgetProvider {
    static final String PREFS = "focus_widget";
    // 0 = 1x1, 1 = 2x2, 2 = 4x1, 3 = 4x2
    static int kindOf(String cls) {
        if (cls.endsWith("$Small")) return 0;
        if (cls.endsWith("$Wide")) return 2;
        if (cls.endsWith("$Large")) return 3;
        return 1;
    }
    public static class Small extends FocusWidget {}
    public static class Wide extends FocusWidget {}
    public static class Large extends FocusWidget {}
    static final Class<?>[] ALL = { FocusWidget.class, Small.class, Wide.class, Large.class };

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) render(c, m, id);
        WidgetTicker.schedule(c);
        // relit le capteur de pas : l'app peut être fermée depuis longtemps
        try { c.sendBroadcast(new Intent(c, WidgetTicker.class)); } catch (Throwable ignored) {}
    }

    @Override
    public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, Bundle o) {
        render(c, m, id);
    }

    @Override
    public void onDeleted(Context c, int[] ids) {
        SharedPreferences.Editor e = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        for (int id : ids) for (String k : new String[] { "m_", "s_", "c2_", "b_", "h_", "kv_", "kp_" }) e.remove(k + id);
        e.apply();
    }

    /** Réglages d'un widget : données, style, couleurs, fond, forme. */
    static class Cfg {
        int metric, style, bg, shape, col, kv, kp;
        static Cfg load(Context c, int id, int kind) {
            SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            Cfg f = new Cfg();
            f.metric = p.getInt("m_" + id, kind == 0 ? 0 : 2);
            f.style = Math.max(0, Math.min(49, p.getInt("s_" + id, kind == 3 ? 3 : 0)));
            f.col = Math.max(0, Math.min(16, p.getInt("c2_" + id, 3)));
            f.bg = p.getInt("b_" + id, 85);
            f.shape = p.getInt("h_" + id, 0);
            f.kv = Math.max(0, Math.min(8, p.getInt("kv_" + id, 1)));
            f.kp = p.getInt("kp_" + id, 0) == 1 ? 1 : 0;
            return f;
        }
        void save(Context c, int id) {
            c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("m_" + id, metric).putInt("s_" + id, style)
                .putInt("c2_" + id, col).putInt("b_" + id, bg).putInt("h_" + id, shape).putInt("kv_" + id, kv).putInt("kp_" + id, kp).apply();
        }
    }

    static int[] data(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        boolean fresh = today.equals(p.getString("date", ""));
        int[] r = new int[60];
        float kpp = p.getFloat("kpp", 0.04f);
        r[0] = fresh ? p.getInt("steps", 0) : 0; r[1] = Math.max(1, p.getInt("stepGoal", 8000)); r[2] = fresh ? p.getInt("kcal", 0) : 0; r[3] = Math.max(1, p.getInt("kcalGoal", 2000));
        java.util.Calendar cal = java.util.Calendar.getInstance(); r[4] = (cal.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7;
        int[] s7 = parse7(p.getString("s7", "")), k7 = parse7(p.getString("k7", ""));
        java.util.Calendar mon = java.util.Calendar.getInstance(); mon.add(java.util.Calendar.DAY_OF_YEAR, -r[4]);
        if (!p.getString("w7d", "").equals(new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(mon.getTime()))) { s7 = new int[7]; k7 = new int[7]; }
        s7[r[4]] = r[0]; k7[r[4]] = r[2];
        System.arraycopy(s7, 0, r, 5, 7); System.arraycopy(k7, 0, r, 12, 7);
        // calories brûlées par la marche, pour l'affichage « Pas + kcal »
        r[19] = Math.round(r[0] * kpp); r[20] = Math.max(1, Math.round(r[1] * kpp));
        for (int i = 0; i < 7; i++) r[21 + i] = Math.round(s7[i] * kpp);
        int dom = cal.get(java.util.Calendar.DAY_OF_MONTH); r[28] = dom;
        String moKey = new SimpleDateFormat("yyyy-MM", Locale.US).format(cal.getTime());
        String[] mo = moKey.equals(p.getString("moKey", "")) ? p.getString("mo", "").split(",") : new String[0];
        for (int i = 0; i < dom && i < 31; i++) try { r[29 + i] = i < mo.length ? Integer.parseInt(mo[i].trim()) : 0; } catch (Exception e) {}
        r[28 + dom] = r[0];
        return r;
    }

    static int kindOfId(AppWidgetManager m, int id) {
        AppWidgetProviderInfo i = m.getAppWidgetInfo(id);
        return i == null ? 1 : kindOf(i.provider.getClassName());
    }

    /** Taille du widget en dp (portrait). */
    static int[] sizeDp(AppWidgetManager m, int id, int kind) {
        Bundle o = m.getAppWidgetOptions(id);
        int w = o == null ? 0 : o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
        int h = o == null ? 0 : o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
        int[] def = kind == 0 ? new int[] { 70, 70 } : kind == 2 ? new int[] { 300, 70 } : kind == 3 ? new int[] { 300, 150 } : new int[] { 150, 150 };
        if (w <= 0) w = def[0];
        if (h <= 0) h = def[1];
        return new int[] { w, h };
    }

    static void render(Context c, AppWidgetManager m, int id) {
        fonts(c);
        int kind = kindOfId(m, id);
        int[] s = sizeDp(m, id, kind);
        float d = c.getResources().getDisplayMetrics().density;
        float k = Math.min(d * 1.15f, 1500f / Math.max(s[0], s[1]));
        Bitmap b = draw(Cfg.load(c, id, kind), data(c), Math.round(s[0] * k), Math.round(s[1] * k), k);
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.focus_widget_img);
        v.setImageViewBitmap(R.id.w_img, b);
        Intent i = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
        if (i != null) v.setOnClickPendingIntent(R.id.w_root, PendingIntent.getActivity(c, 0, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        m.updateAppWidget(id, v);
    }

    static void push(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        for (Class<?> k : ALL) for (int id : m.getAppWidgetIds(new ComponentName(c, k))) render(c, m, id);
    }

    /* ---------- dessin ---------- */
    static Typeface T8, T7;
    static void fonts(Context c) {
        if (T8 != null) return;
        try { T8 = Typeface.createFromAsset(c.getAssets(), "fonts/jakarta800.ttf"); T7 = Typeface.createFromAsset(c.getAssets(), "fonts/jakarta700.ttf"); }
        catch (Throwable t) { T8 = Typeface.create("sans-serif", Typeface.BOLD); T7 = Typeface.create("sans-serif-medium", Typeface.NORMAL); }
    }
    static String fmt(int n) { return String.format(Locale.FRANCE, "%,d", n).replace(' ', ' ').replace(' ', ' '); }
    static String compact(int n) {
        if (n < 1000) return String.valueOf(n);
        return n < 10000 ? String.format(Locale.FRANCE, "%.1fk", n / 1000f) : Math.round(n / 1000f) + "k";
    }
    static int al(int c, float a) { return (c & 0xFFFFFF) | (Math.round(Math.max(0, Math.min(1, a)) * 255) << 24); }
    static int rgba(int r, int g, int b, float a) { return Color.argb(Math.round(Math.max(0, Math.min(1, a)) * 255), r, g, b); }

    /** Thème : couleurs du texte, des pistes, des deux métriques, et décor du fond. */
    static class Th {
        int fg, mut, tr, s0, s1, k0, k1, base; String bg; boolean ol, noOlText, light, ios; float ls; int glow;
        Th(int fg, int mut, int tr, int s0, int s1, int k0, int k1, String bg) { this.fg = fg; this.mut = mut; this.tr = tr; this.s0 = s0; this.s1 = s1; this.k0 = k0; this.k1 = k1; this.bg = bg; }
        int a(float x) { return al(base, x); }
    }
    static final String[] THEME_NAMES = { "Blanc", "Couleur", "Dégradé", "Chrome", "Cartoon", "Futuriste", "Rétro", "iOS", "Papier", "Brutal", "Néon blanc", "Ligne", "Terminal", "Trame", "Givré", "Graphite", "Liquid glass" };
    static Th theme(int i) {
        final int W = 0xFFFFFFFF, M55 = 0x8CFFFFFF, T12 = 0x1FFFFFFF;
        Th t;
        switch (i) {
            case 1: t = new Th(W, M55, T12, 0xFFD7FF3A, 0xFFD7FF3A, 0xFFFF6B4A, 0xFFFF6B4A, "glass"); break;
            case 2: t = new Th(W, M55, T12, 0xFFD7FF3A, 0xFF22D3EE, 0xFFFFB347, 0xFFFF3D7F, "glass"); break;
            case 3: t = new Th(W, M55, T12, W, 0xFF7E7E88, 0xFFF2F2F5, 0xFF76767F, "chrome"); break;
            case 4: t = new Th(W, 0xFF1B1B1F, 0x47000000, 0xFFFFE14D, 0xFFFFE14D, 0xFFFF5E7E, 0xFFFF5E7E, "cartoon"); t.ol = true; break;
            case 5: t = new Th(0xFFE8FDFF, 0xB300F0FF, 0x2400F0FF, 0xFF00F0FF, 0xFF7C5CFF, 0xFFFF2BD6, 0xFF7C5CFF, "futur"); t.ls = .06f; t.glow = 0xCC00F0FF; break;
            case 6: t = new Th(0xFF3B2414, 0x9E3B2414, 0x243B2414, 0xFFE8772E, 0xFFF4B942, 0xFFC8413B, 0xFFE8772E, "retro"); break;
            case 7: t = new Th(W, 0xFF8E8E93, 0x1AFFFFFF, 0xFFB7FF2A, 0xFF5BE12C, 0xFFFF2D55, 0xFFFF6B8B, "ios"); t.ios = true; break;
            case 8: t = new Th(0xFF111111, 0x80000000, 0x1A000000, 0xFF111111, 0xFF111111, 0xFF111111, 0xFF111111, "papier"); t.light = true; break;
            case 9: t = new Th(0xFF111111, 0x99000000, 0x1F000000, 0xFF111111, 0xFF111111, 0xFF111111, 0xFF111111, "brutal"); t.light = true; t.ol = true; t.noOlText = true; break;
            case 10: t = new Th(W, 0x99FFFFFF, 0x1AFFFFFF, W, W, W, W, "neonw"); t.glow = 0xE6FFFFFF; break;
            case 11: t = new Th(W, 0x99FFFFFF, 0x24FFFFFF, W, W, W, W, "ligne"); break;
            case 12: t = new Th(0xFFF2F2F2, 0x80FFFFFF, 0x1FFFFFFF, 0xFFF2F2F2, 0xFFF2F2F2, 0xFFF2F2F2, 0xFFF2F2F2, "terminal"); t.ls = .08f; break;
            case 13: t = new Th(W, 0x99FFFFFF, 0x24FFFFFF, W, W, W, W, "halftone"); break;
            case 14: t = new Th(W, 0xB3FFFFFF, 0x33FFFFFF, W, W, W, W, "givre"); break;
            case 15: t = new Th(0xFFF4F4F6, 0x80FFFFFF, 0x1AFFFFFF, W, 0xFF9A9AA2, 0xFFE6E6EA, 0xFF8A8A92, "graphite"); break;
            case 16: t = new Th(W, 0x99FFFFFF, 0x24FFFFFF, W, W, W, W, "liquid"); break;
            default: t = new Th(W, M55, T12, W, W, W, W, "glass");
        }
        t.base = t.bg.equals("retro") ? 0xFF3B2414 : t.light ? 0xFF000000 : 0xFFFFFFFF;
        return t;
    }

    static class M { int v, g; String l, u; int c0, c1; int[] w7; char kd; }

    static class Art {
        Canvas x; Cfg f; Th T; int[] d; float W, H, dp, u, pad, pad2; boolean sh, circle; float rad;
        Paint pt(float sz, int col, boolean b8, float ls) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); p.setTypeface(b8 ? T8 : T7); p.setTextSize(sz); p.setColor(col); p.setLetterSpacing(ls); return p;
        }
        float fit(String t, float sz, float mw, boolean b8) {
            Paint p = pt(sz, 0, b8, 0); while (sz > 5 && p.measureText(t) > mw) { sz *= .95f; p.setTextSize(sz); } return sz;
        }
        void text(String t, float X, float Y, float sz, int col, Paint.Align a, boolean b8, float ls) {
            Paint p = pt(sz, col, b8, ls + T.ls); p.setTextAlign(a);
            if (T.ol && !T.noOlText && col != T.mut) { Paint o = pt(sz, 0xFF1B1B1F, b8, ls + T.ls); o.setTextAlign(a); o.setStyle(Paint.Style.STROKE); o.setStrokeJoin(Paint.Join.ROUND); o.setStrokeWidth(sz * .16f); x.drawText(t, X, Y, o); }
            if (T.glow != 0 && col == T.fg) p.setShadowLayer(sz * .35f, 0, 0, T.glow);
            else if (sh) p.setShadowLayer(Math.max(1, sz * .15f), 0, sz * .04f, 0x8C000000);
            x.drawText(t, X, Y, p);
        }
        void text(String t, float X, float Y, float sz, int col) { text(t, X, Y, sz, col, Paint.Align.LEFT, true, 0); }
        Shader lin(int a, int b, float x0, float y0, float x1, float y1) { return new LinearGradient(x0, y0, x1, y1, a, b, Shader.TileMode.CLAMP); }
        float fr(M m) { return Math.max(0f, Math.min(1f, m.v / (float) m.g)); }
        Paint fill(int c) { Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); p.setColor(c); return p; }
        Paint stroke(int c, float w) { Paint p = fill(c); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(w); return p; }
        Path shapeP(float i) {
            Path p = new Path();
            if (circle) p.addCircle(W / 2, H / 2, u / 2 - i, Path.Direction.CW);
            else { float r = Math.max(2, rad - i); p.addRoundRect(new RectF(i, i, W - i, H - i), r, r, Path.Direction.CW); }
            return p;
        }
        void rr(float l, float t, float w, float h, float r, Paint p) { x.drawRoundRect(new RectF(l, t, l + w, t + h), r, r, p); }

        void background() {
            float A = f.bg / 100f;
            switch (T.bg) {
                case "cartoon": case "brutal": {
                    boolean car = T.bg.equals("cartoon"); float o = 4 * dp, r = car ? rad : rad * .6f;
                    Paint sh2 = fill(car ? 0xFF1B1B1F : 0xFF111111);
                    if (circle) x.drawCircle(W / 2 + o * .5f, H / 2 + o * .5f, u / 2 - o, sh2); else rr(o, o, W - o - 1 - o, H - o - 1 - o, r, sh2);
                    Path p = new Path();
                    if (circle) p.addCircle(W / 2 - o * .3f, H / 2 - o * .3f, u / 2 - o, Path.Direction.CW);
                    else p.addRoundRect(new RectF(1.5f * dp, 1.5f * dp, W - o, H - o), r, r, Path.Direction.CW);
                    Paint fp = fill(0xFFFFFFFF); if (car) fp.setShader(lin(0xFF8B6CFF, 0xFF6A4BFF, 0, 0, 0, H));
                    x.drawPath(p, fp); x.drawPath(p, stroke(car ? 0xFF1B1B1F : 0xFF111111, 3 * dp));
                    if (car) { x.save(); x.clipPath(p); x.save(); x.rotate((float) Math.toDegrees(-.3), W * .28f, H * .16f); x.drawOval(new RectF(W * .06f, H * .09f, W * .5f, H * .23f), fill(0x2EFFFFFF)); x.restore();
                        for (int i = 0; i < 14; i++) x.drawCircle((i * 97) % W, (i * 53) % H, u * .025f, fill(0x14FFFFFF)); x.restore(); }
                    break; }
                case "futur": {
                    Path p = shapeP(1); Paint fp = fill(0); fp.setShader(lin(0xF5040A18, 0xF50A041C, 0, 0, W, H)); x.drawPath(p, fp);
                    x.save(); x.clipPath(p); Paint g = stroke(0x1200F0FF, 1); float gs = u / 8;
                    for (float gx = gs / 2; gx < W; gx += gs) x.drawLine(gx, 0, gx, H, g);
                    for (float gy = gs / 2; gy < H; gy += gs) x.drawLine(0, gy, W, gy, g);
                    Paint sc = fill(0); sc.setShader(lin(0x0000F0FF, 0x1400F0FF, 0, 0, 0, H)); x.drawRect(0, H * .6f, W, H, sc); x.restore();
                    x.drawPath(p, stroke(0x5900F0FF, Math.max(1, dp)));
                    float L = u * .16f, m2 = pad * .45f; Paint c = stroke(0xFF00F0FF, 2 * dp); c.setShadowLayer(6 * dp, 0, 0, 0xFF00F0FF); c.setStrokeCap(Paint.Cap.SQUARE);
                    float[][] cs = { { m2, m2, 1, 1 }, { W - m2, m2, -1, 1 }, { m2, H - m2, 1, -1 }, { W - m2, H - m2, -1, -1 } };
                    for (float[] k : cs) { Path cp = new Path(); cp.moveTo(k[0], k[1] + k[3] * L); cp.lineTo(k[0], k[1]); cp.lineTo(k[0] + k[2] * L, k[1]); x.drawPath(cp, c); }
                    break; }
                case "retro": {
                    Path p = shapeP(0); x.drawPath(p, fill(0xFFF3E6CC)); x.save(); x.clipPath(p);
                    int[] cols = { 0xFFC8413B, 0xFFE8772E, 0xFFF4B942 }; float bw = u * .06f;
                    for (int i = 0; i < 3; i++) x.drawRect(0, H - bw * (3 - i), W, H - bw * (3 - i) + bw * .8f, fill(cols[i]));
                    Paint dt = fill(0x0D3B2414); for (int i = 0; i < 300; i++) x.drawRect((i * 137) % W, (i * 71) % H, (i * 137) % W + 1.2f * dp, (i * 71) % H + 1.2f * dp, dt);
                    x.restore(); x.drawPath(shapeP(dp), stroke(0xFF3B2414, 2 * dp)); break; }
                case "papier": { Paint fp = fill(0); fp.setShader(lin(0xFFFAFAF8, 0xFFECECEA, 0, 0, 0, H)); x.drawPath(shapeP(0), fp); x.drawPath(shapeP(.5f), stroke(0x14000000, Math.max(1, dp))); break; }
                case "neonw": { Path p = shapeP(2 * dp); x.drawPath(p, fill(0xFF050505)); Paint s2 = stroke(Color.WHITE, 1.5f * dp); s2.setShadowLayer(10 * dp, 0, 0, Color.WHITE); x.drawPath(p, s2); break; }
                case "ligne": { Paint s2 = stroke(0x8CFFFFFF, Math.max(1, dp)); s2.setPathEffect(new DashPathEffect(new float[] { 5 * dp, 4 * dp }, 0)); x.drawPath(shapeP(dp), s2); break; }
                case "terminal": {
                    Path p = shapeP(0); x.drawPath(p, fill(0xFF000000)); x.save(); x.clipPath(p); Paint ln = fill(0x0BFFFFFF);
                    for (float y = 0; y < H; y += 3 * dp) x.drawRect(0, y, W, y + dp, ln);
                    Paint vg = fill(0); vg.setShader(new RadialGradient(W / 2, H / 2, u * .8f, new int[] { 0x0FFFFFFF, 0x0FFFFFFF, 0x00000000 }, new float[] { 0, .25f, 1 }, Shader.TileMode.CLAMP)); x.drawRect(0, 0, W, H, vg);
                    x.restore(); x.drawPath(shapeP(.5f), stroke(0x4DFFFFFF, Math.max(1, dp))); break; }
                case "halftone": {
                    Path p = shapeP(0); x.drawPath(p, fill(0xFF000000)); x.save(); x.clipPath(p); float g = u / 14; Paint dt = fill(0x29FFFFFF);
                    for (float yy = g / 2; yy < H; yy += g) for (float xx = g / 2; xx < W; xx += g) { float k = (xx / W + yy / H) / 2; x.drawCircle(xx, yy, g * .42f * k * k, dt); }
                    x.restore(); x.drawPath(shapeP(.5f), stroke(0x38FFFFFF, Math.max(1, dp))); break; }
                case "givre": {
                    Path p = shapeP(0); Paint fp = fill(0); fp.setShader(lin(0x38FFFFFF, 0x14FFFFFF, 0, 0, W, H)); x.drawPath(p, fp);
                    x.save(); x.clipPath(p); Paint hp = fill(0); hp.setShader(lin(0x2EFFFFFF, 0x00FFFFFF, 0, 0, 0, H * .5f)); x.drawRect(0, 0, W, H * .5f, hp); x.restore();
                    Paint s2 = stroke(0, 1.2f * dp); s2.setShader(lin(0xBFFFFFFF, 0x26FFFFFF, 0, 0, W, H)); x.drawPath(shapeP(.6f * dp), s2); break; }
                case "graphite": {
                    Path p = shapeP(0); Paint fp = fill(0); fp.setShader(lin(0xFF3C3C42, 0xFF121214, 0, 0, W * .4f, H)); x.drawPath(p, fp);
                    x.save(); x.clipPath(p); Paint a1 = fill(0x09FFFFFF), a2 = fill(0x14000000);
                    for (int i = 0; i < 700; i++) { float px = (i * 131.7f) % W, py = (i * 71.3f) % H; x.drawRect(px, py, px + dp, py + dp, i % 2 == 1 ? a1 : a2); }
                    x.restore(); Paint s2 = stroke(0, Math.max(1, dp)); s2.setShader(lin(0x59FFFFFF, 0x0DFFFFFF, 0, 0, W, H)); x.drawPath(shapeP(.5f), s2); break; }
                case "ios": x.drawPath(shapeP(0), fill(rgba(28, 28, 30, Math.max(.85f, A)))); break;
                case "liquid": {
                    // verre liquide : voile sombre léger, liseré clair, reflets internes
                    float lw = Math.max(1.2f, 1.3f * dp), i = lw / 2; Path p = shapeP(i);
                    x.drawPath(p, fill(rgba(18, 18, 20, .42f * A)));
                    x.save(); x.clipPath(p);
                    Paint top = fill(0xFFFFFFFF); top.setShader(lin(rgba(255, 255, 255, .09f), 0x00FFFFFF, 0, 0, 0, H * .45f)); x.drawRect(0, 0, W, H * .45f, top);
                    Paint rim = stroke(0xFFFFFFFF, 7 * dp); rim.setMaskFilter(new android.graphics.BlurMaskFilter(6 * dp, android.graphics.BlurMaskFilter.Blur.NORMAL));
                    rim.setShader(new LinearGradient(0, 0, W, H, new int[] { 0x2EFFFFFF, 0x08FFFFFF, 0x1FFFFFFF }, new float[] { 0, .5f, 1 }, Shader.TileMode.CLAMP)); x.drawPath(p, rim);
                    x.restore();
                    Paint bd = stroke(0xFFFFFFFF, lw); bd.setShader(new LinearGradient(0, 0, W, H, new int[] { 0x99FFFFFF, 0x2EFFFFFF, 0x1AFFFFFF, 0x66FFFFFF }, new float[] { 0, .35f, .65f, 1 }, Shader.TileMode.CLAMP));
                    x.drawPath(p, bd);
                    break; }
                case "glass":
                    if (f.bg > 0) {
                        float lw = Math.max(1, dp * .8f), i = lw / 2; Path p = shapeP(i);
                        x.drawPath(p, fill(rgba(34, 34, 36, .62f * A)));
                        Paint bd = stroke(0, lw); bd.setShader(new LinearGradient(0, 0, W * .5f, H, new int[] { rgba(255, 255, 255, .26f * A), rgba(255, 255, 255, .12f * A), rgba(255, 255, 255, .16f * A) }, new float[] { 0, .6f, 1 }, Shader.TileMode.CLAMP));
                        x.drawPath(p, bd);
                    }
                    break;
                default:
                    if (f.bg > 0) {
                        float lw = Math.max(1.2f, 1.4f * dp), i = lw / 2; Path p = shapeP(i);
                        Paint fp = fill(0); fp.setShader(lin(rgba(46, 46, 52, A), rgba(8, 8, 10, A), 0, 0, W * .3f, H)); x.drawPath(p, fp);
                        if (T.bg.equals("chrome")) { x.save(); x.clipPath(p);
                            Paint gl = fill(0); gl.setShader(new RadialGradient(W * .15f, H * .05f, u * .9f, rgba(255, 255, 255, .1f * A), 0x00FFFFFF, Shader.TileMode.CLAMP)); x.drawRect(0, 0, W, H, gl);
                            Paint hp = fill(0); hp.setShader(lin(rgba(255, 255, 255, .07f * A), 0x00FFFFFF, 0, 0, 0, H * .45f)); x.drawRect(0, 0, W, H * .45f, hp); x.restore(); }
                        Paint bev = stroke(0, lw); bev.setShader(new LinearGradient(0, 0, W, H, new int[] { rgba(255, 255, 255, .55f * A + .1f), 0x14FFFFFF, 0x0AFFFFFF, rgba(255, 255, 255, .28f * A + .05f) }, new float[] { 0, .35f, .7f, 1 }, Shader.TileMode.CLAMP));
                        x.drawPath(p, bev);
                    }
            }
        }

        void icon(char kind, float cx, float cy, float sz, int col) {
            x.save(); x.translate(cx - sz / 2, cy - sz / 2); x.scale(sz / 24, sz / 24);
            Paint p = stroke(col, 2); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND);
            Path q = new Path();
            if (kind == 's') { q.moveTo(3, 15.5f); q.cubicTo(3, 12, 5, 11, 7.5f, 11); q.lineTo(10, 8.5f); q.cubicTo(11, 7.5f, 12.5f, 8, 13, 9); q.lineTo(14.5f, 11.5f); q.cubicTo(17, 12.5f, 21, 13.5f, 21, 16); q.lineTo(21, 17.5f); q.lineTo(3, 17.5f); q.close();
                q.moveTo(8, 14); q.lineTo(10, 14); q.moveTo(11, 12.5f); q.lineTo(12.5f, 12.5f); }
            else { q.moveTo(12, 21); q.cubicTo(7.5f, 21, 5, 18, 5, 14.5f); q.cubicTo(5, 10.5f, 9, 8.5f, 9.5f, 4); q.cubicTo(12.5f, 6, 14, 8.5f, 14, 11); q.cubicTo(15, 10, 15.5f, 9, 15.5f, 8); q.cubicTo(17.8f, 9.8f, 19, 12.3f, 19, 14.5f); q.cubicTo(19, 18, 16.5f, 21, 12, 21); q.close(); }
            x.drawPath(q, p); x.restore();
        }
        void chip(char kind, float cx, float cy, float r, int col) {
            Paint p = fill(0); p.setShader(lin(T.a(.16f), T.a(.03f), cx, cy - r, cx, cy + r)); x.drawCircle(cx, cy, r, p);
            Paint s2 = stroke(0, Math.max(1, dp * .9f)); s2.setShader(lin(T.a(.5f), T.a(.08f), cx - r, cy - r, cx + r, cy + r)); x.drawCircle(cx, cy, r, s2);
            icon(kind, cx, cy, r * 1.15f, col);
        }
        void ticks(float cx, float cy, float r, int n, float len, int col, float frac) {
            for (int i = 0; i < n; i++) {
                double t = -Math.PI / 2 + i * Math.PI * 2 / n; float L = i % 5 == 0 ? len * 1.7f : len;
                Paint p = stroke(i / (float) n < frac ? col : T.a(.18f), Math.max(1, dp * (i % 5 == 0 ? 1.3f : .8f)));
                x.drawLine(cx + (float) Math.cos(t) * r, cy + (float) Math.sin(t) * r, cx + (float) Math.cos(t) * (r - L), cy + (float) Math.sin(t) * (r - L), p);
            }
        }
        String km(int v) { return String.format(Locale.FRANCE, "%.1f km", v * 0.00075f); }
        void labl(M m, String l, float X, float Y, float sz, int col, Paint.Align a, float ls) {
            float isz = sz * 1.35f;
            if (a == Paint.Align.CENTER) { Paint p = pt(sz, col, false, ls + T.ls); float tw = p.measureText(l) + isz * 1.15f; icon(m.kd, X - tw / 2 + isz / 2, Y - sz * .36f, isz, col); text(l, X - tw / 2 + isz * 1.15f, Y, sz, col, Paint.Align.LEFT, false, ls); }
            else if (a == Paint.Align.RIGHT) text(l, X, Y, sz, col, Paint.Align.RIGHT, false, ls);
            else { icon(m.kd, X + isz / 2, Y - sz * .36f, isz, col); text(l, X + isz * 1.15f, Y, sz, col, Paint.Align.LEFT, false, ls); }
        }
        void ring(float cx, float cy, float r, float sw, M m, boolean glow) {
            RectF o = new RectF(cx - r, cy - r, cx + r, cy + r);
            if (T.ol) { Paint b = stroke(T.light ? 0xFF111111 : 0xFF1B1B1F, sw + 5 * dp); x.drawCircle(cx, cy, r, b); }
            x.drawCircle(cx, cy, r, stroke(T.ios ? al(m.c0, .25f) : T.tr, sw));
            float a = fr(m); if (a <= 0) return; if (T.ios || T.ol) glow = false;
            Paint p = stroke(m.c0, sw); p.setStrokeCap(Paint.Cap.ROUND);
            if (m.c0 != m.c1) { SweepGradient g = new SweepGradient(cx, cy, new int[] { m.c1, m.c0, m.c0 }, new float[] { 0, Math.max(.05f, a), 1 });
                Matrix mx = new Matrix(); mx.setRotate(-90, cx, cy); g.setLocalMatrix(mx); p.setShader(g); }
            if (glow) p.setShadowLayer(sw * 1.1f, 0, 0, al(m.c0, .6f));
            x.drawArc(o, -90, Math.max(2.3f, a * 360), false, p);
            double ea = Math.toRadians(-90 + a * 360);
            x.drawCircle(cx + (float) Math.cos(ea) * r, cy + (float) Math.sin(ea) * r, sw * .22f, fill(T.fg));
        }
        void gauge(float cx, float cy, float r, float sw, M m) {
            float a = fr(m);
            for (int i = 0; i <= 24; i++) {
                double th = Math.PI * .75 + Math.PI * 1.5 * i / 24; float in = r - sw * 1.15f, out = r - sw * .75f - (i % 6 == 0 ? sw * .45f : 0);
                Paint t = stroke(i / 24f <= a ? T.a(.45f) : T.a(.14f), Math.max(1, sw * .12f)); t.setStrokeCap(Paint.Cap.ROUND);
                x.drawLine(cx + (float) Math.cos(th) * (in - sw * .5f), cy + (float) Math.sin(th) * (in - sw * .5f), cx + (float) Math.cos(th) * (out - sw * .9f), cy + (float) Math.sin(th) * (out - sw * .9f), t);
            }
            RectF o = new RectF(cx - r, cy - r, cx + r, cy + r);
            Paint tr = stroke(T.tr, sw); tr.setStrokeCap(Paint.Cap.ROUND); x.drawArc(o, 135, 270, false, tr);
            if (a > 0) { Paint p = stroke(m.c0, sw); p.setStrokeCap(Paint.Cap.ROUND); if (f.col == 2) p.setShader(lin(m.c0, m.c1, cx - r, cy, cx + r, cy)); p.setShadowLayer(sw, 0, 0, al(m.c0, .53f)); x.drawArc(o, 135, 270 * Math.max(.02f, a), false, p); }
            double ea = Math.toRadians(135 + 270 * a); float ex = cx + (float) Math.cos(ea) * r, ey = cy + (float) Math.sin(ea) * r;
            x.drawCircle(ex, ey, sw * .62f, fill(T.fg)); x.drawCircle(ex, ey, sw * .3f, fill(m.c0));
        }

        void one(float X, float Y, float w, float h, M m, boolean small) {
            boolean wideR = w > h * 1.45f; String big = small ? compact(m.v) : fmt(m.v), pc = Math.round(m.v * 100f / m.g) + "%";
            Paint.Align C = Paint.Align.CENTER, Lf = Paint.Align.LEFT, Rt = Paint.Align.RIGHT;
            if (f.style == 0) {
                if (wideR) { float r = h * .4f, cx = X + r, cy = Y + h / 2; chip(m.kd, cx, cy, r, m.c0); float rx = X + w - h * .42f, rr = h * .34f, sw = rr * .2f; ring(rx, cy, rr, sw, m, true);
                    text(pc, rx, cy + rr * .16f, rr * .44f, T.fg, C, true, 0);
                    float tx = cx + r + pad * .6f, mw = rx - rr - sw - tx - pad * .4f, s = fit(fmt(m.v), h * .4f, mw, true);
                    text(fmt(m.v), tx, cy + s * .08f, s, T.fg); text("/ " + fmt(m.g) + " " + m.u, tx, cy + s * .08f + h * .2f, Math.max(8 * dp, h * .13f), T.mut, Lf, false, 0); }
                else { float cx = X + w / 2, cy = Y + h / 2, r0 = Math.min(w, h) / 2, tl = r0 * .09f; ticks(cx, cy, r0, 60, tl, m.c0, fr(m));
                    float rr = r0 - tl * 1.7f - r0 * .12f, sw = Math.max(2 * dp, r0 * .075f); ring(cx, cy, rr, sw, m, true); float iw = (rr - sw) * 1.7f;
                    if (small) { float s = fit(big, rr * .62f, iw, true); text(big, cx, cy + s * .36f, s, T.fg, C, true, 0); }
                    else { icon(m.kd, cx, cy - rr * .42f, rr * .3f, m.c0); float s = fit(big, rr * .46f, iw, true); text(big, cx, cy + s * .24f, s, T.fg, C, true, 0);
                        text(m.kd == 's' ? km(m.v) : pc + " de " + fmt(m.g), cx, cy + s * .24f + rr * .28f, rr * .14f, T.mut, C, false, 0); } }
            } else if (f.style == 1) {
                if (wideR) { float r = h * .4f, sw = r * .2f, cx = X + r + sw, cy = Y + h * .55f; gauge(cx, cy, r, sw, m); text(pc, cx, cy + r * .16f, r * .38f, T.fg, C, true, 0);
                    float tx = cx + r + sw + pad * .5f, mw = X + w - tx, s = fit(fmt(m.v), h * .36f, mw, true);
                    text(fmt(m.v), tx, Y + h / 2 + s * .1f, s, T.fg); labl(m, m.l, tx, Y + h / 2 + s * .1f + h * .17f, Math.max(9 * dp, h * .12f), T.mut, Lf, .1f); }
                else { float r = Math.min(w, h * 1.08f) / 2 - pad * .5f, sw = r * .15f, cx = X + w / 2, cy = Y + h * .52f; gauge(cx, cy, r, sw, m);
                    float s = fit(big, r * (small ? .5f : .42f), r * 1.35f, true); text(big, cx, cy + s * .3f, s, T.fg, C, true, 0);
                    if (!small) labl(m, m.l, cx, cy + r * .62f, r * .15f, T.mut, C, .14f); }
            } else if (f.style == 2) {
                float lbl = small ? 0 : Math.max(9 * dp, Math.min(h * .11f, w * .07f)), s = fit(big, h * (small ? .5f : .56f), w - pad * .4f, true);
                float cx = X + w / 2, base = Y + h / 2 + s * .36f - lbl * .6f, top = base - s * .74f;
                Paint p = pt(s, T.a(.14f), true, -.02f); p.setTextAlign(C); if (sh) p.setShadowLayer(s * .15f, 0, s * .04f, 0x8C000000); x.drawText(big, cx, base, p);
                Paint st = pt(s, T.a(.35f), true, -.02f); st.setTextAlign(C); st.setStyle(Paint.Style.STROKE); st.setStrokeWidth(Math.max(1, s * .02f)); x.drawText(big, cx, base, st);
                x.save(); float fh = (s * .74f) * fr(m); x.clipRect(X, base - fh, X + w, base + s * .05f);
                Paint fp = pt(s, f.col == 0 ? T.fg : m.c0, true, -.02f); fp.setTextAlign(C); if (f.col != 0) fp.setShader(lin(m.c1, m.c0, 0, top, 0, base));
                fp.setShadowLayer(s * .2f, 0, 0, al(f.col == 0 ? Color.WHITE : m.c0, .4f)); x.drawText(big, cx, base, fp); x.restore();
                if (lbl > 0) labl(m, m.l + " · " + pc, cx, base + lbl * 1.7f, lbl, T.mut, C, .12f);
            } else if (f.style == 3) {
                int[] vals = m.w7; int mx = Math.max(1, m.g); for (int v : vals) mx = Math.max(mx, v); int ti = d[4];
                String[] days = { "L", "M", "M", "J", "V", "S", "D" };
                float bx, by, bw, bh, nx, ny, ns;
                if (wideR) { ns = fit(big, h * .42f, w * .38f, true); nx = X; ny = Y + h / 2 + ns * .12f; bx = X + w * .44f; bw = w * .56f; by = Y + h * .04f; bh = h * .92f; }
                else { ns = fit(big, h * (small ? .24f : .2f), w, true); nx = X; ny = Y + ns * .82f; bx = X; bw = w; by = Y + ns * 1.2f + (small ? 0 : h * .04f); bh = Y + h - by - (small ? 0 : h * .1f); }
                text(big, nx, ny, ns, T.fg);
                if (!small) labl(m, wideR ? m.l : m.l + " · AUJOURD'HUI", nx, ny + (wideR ? h * .22f : ns * .48f), Math.max(8 * dp, Math.min(h * .075f, ns * .32f)), T.mut, Lf, .1f);
                float gap = bw * .04f, cw = (bw - gap * 6) / 7, gy = by + bh * (1 - m.g / (float) mx);
                Paint dl = stroke(T.a(.3f), Math.max(1, dp)); dl.setPathEffect(new DashPathEffect(new float[] { 3 * dp, 3 * dp }, 0)); x.drawLine(bx, gy, bx + bw, gy, dl);
                for (int i = 0; i < 7; i++) {
                    int v = vals[i]; float hh = Math.max(cw * .9f, bh * v / mx), xx = bx + i * (cw + gap), yy = by + bh - hh;
                    Paint b = fill(0);
                    if (i == ti) { b.setColor(m.c0); if (f.col == 2) b.setShader(lin(m.c0, m.c1, 0, yy, 0, by + bh)); b.setShadowLayer(cw * .8f, 0, 0, al(m.c0, .6f)); }
                    else b.setColor(i > ti ? T.a(.06f) : v >= m.g ? T.a(.55f) : T.a(.22f));
                    rr(xx, yy, cw, hh, cw / 2, b);
                    if (!small && !wideR) text(days[i], xx + cw / 2, by + bh + h * .085f, Math.max(7 * dp, h * .06f), i == ti ? T.fg : T.mut, C, false, 0);
                }
            } else if (f.style == 4) {
                float bh = Math.min(h * (small ? .42f : .5f), w * .42f), bw = w - bh * .16f - pad * .1f, bx = X, by = Y + (h - bh) / 2, r = bh * .32f, a = fr(m), nub = bh * .12f;
                rr(bx, by, bw, bh, r, fill(T.a(.08f))); rr(bx, by, bw, bh, r, stroke(T.a(.3f), Math.max(1.5f * dp, bh * .04f)));
                rr(bx + bw + bh * .05f, by + bh * .32f, nub, bh * .36f, nub * .4f, fill(T.a(.3f)));
                float ins = bh * .09f, fw = (bw - ins * 2) * a;
                if (a > 0) { Paint fp = fill(m.c0); if (f.col == 2) fp.setShader(lin(m.c0, m.c1, bx, 0, bx + bw, 0)); fp.setShadowLayer(bh * .3f, 0, 0, al(m.c0, .47f)); rr(bx + ins, by + ins, Math.max(r, fw), bh - ins * 2, r * .7f, fp); }
                String t = small ? compact(m.v) : fmt(m.v); float s = fit(t, bh * .5f, bw * .7f, true), tx = bx + bw / 2, ty = by + bh / 2 + s * .36f;
                Paint tp = pt(s, T.fg, true, 0); tp.setTextAlign(C); x.drawText(t, tx, ty, tp);
                x.save(); x.clipRect(bx + ins, by, bx + ins + fw, by + bh); tp.setColor(T.light ? Color.WHITE : 0xFF0B0B0F); x.drawText(t, tx, ty, tp); x.restore();
                if (!small) { float ls = Math.max(8 * dp, Math.min(bh * .2f, h * .1f)); labl(m, m.l, bx, by - ls * .6f, ls, T.mut, Lf, .12f); text(pc, bx + bw, by - ls * .6f, ls, T.fg, Rt, true, 0); }
            } else if (f.style == 6) {
                float ls = small ? 0 : Math.max(8 * dp, Math.min(h * .13f, w * .08f));
                if (ls > 0) labl(m, m.l, X, Y + ls, ls, T.mut, Lf, .12f);
                float s = fit(big, h * (small ? .62f : .6f), w, true), base = Y + (ls > 0 ? ls * 1.4f : 0) + (h - (ls > 0 ? ls * 1.4f : 0)) * .5f + s * .36f;
                text(big, X + (small ? w / 2 : 0), base, s, T.fg, small ? C : Lf, true, -.03f);
                float bh = Math.max(3 * dp, s * .09f), by = Math.min(Y + h - bh, base + s * .18f), a = fr(m);
                rr(X, by, w, bh, bh / 2, fill(T.tr));
                if (a > 0) { Paint fp = fill(m.c0); if (m.c0 != m.c1) fp.setShader(lin(m.c0, m.c1, X, 0, X + w, 0)); rr(X, by, Math.max(bh, w * a), bh, bh / 2, fp);
                    if (T.ol) rr(X, by, w, bh, bh / 2, stroke(0xFF1B1B1F, 2 * dp)); }
                if (!small) text(pc, X + w, Y + ls, ls, T.fg, Rt, true, 0);
            } else {
                float a = fr(m); int cols, rows; float gx, gy, gw, gh, nsz;
                if (wideR) { nsz = fit(big, h * .36f, w * .34f, true); text(big, X, Y + h / 2 + nsz * .05f, nsz, T.fg);
                    labl(m, m.l, X, Y + h / 2 + nsz * .05f + h * .2f, Math.max(8 * dp, h * .12f), T.mut, Lf, .1f);
                    gx = X + w * .4f; gw = w * .6f; gy = Y + h * .14f; gh = h * .72f; rows = 3; cols = 12; }
                else { nsz = fit(big, h * (small ? .26f : .22f), w, true); text(big, X, Y + nsz * .82f, nsz, T.fg);
                    gx = X; gw = w; gy = Y + nsz * 1.25f; gh = Y + h - gy - (small ? 0 : h * .02f); cols = small ? 5 : 8; rows = Math.max(2, Math.round(cols * gh / gw)); }
                int N = cols * rows, on = Math.round(a * N); float cs = Math.min(gw / cols, gh / rows), rad2 = cs * .34f;
                for (int i = 0; i < N; i++) {
                    float cx = gx + cs * (i % cols) + cs / 2, cy = gy + cs * (i / cols) + cs / 2, k = i / (float) (N - 1);
                    Paint dt = fill(i < on ? (f.col == 2 ? (k < .5f ? m.c0 : m.c1) : m.c0) : T.a(.12f));
                    if (i == on - 1) dt.setShadowLayer(cs * .8f, 0, 0, m.c0);
                    x.drawCircle(cx, cy, rad2, dt);
                }
            }
        }
    }

    static int[] parse7(String s) {
        int[] r = new int[7];
        if (s == null) return r;
        String[] p = s.split(",");
        for (int i = 0; i < 7 && i < p.length; i++) try { r[i] = Integer.parseInt(p[i].trim()); } catch (Exception e) {}
        return r;
    }


    /* ---------- designs supplémentaires (styles 7 à 34) ---------- */
    static final String[] DESIGN_NAMES = { "Orbite", "Segments", "Compteur", "Sommet", "Empreintes", "Minimal", "Barres", "Odomètre", "Sablier", "Aiguille",
        "Montre", "3 anneaux", "Matrice", "Vague", "Spirale", "Route", "Escalier", "Vertical", "Pourcent", "Tachymètre", "Étoiles", "Code-barres", "Ticket",
        "Semaine +", "Thermomètre", "Piste", "Règle", "Onde",
        "Saturne", "Lune", "Soleil", "Mois", "Courbe", "Chrono", "Boussole", "Chargement", "Terminal", "Sonar", "Vinyle", "Flip", "Galaxie", "Saturne long", "Semaine long" };
    static boolean wideDesign(int id) { return id == 6 || id == 7 || (id >= 25 && id <= 27) || id >= 41; }

    static class Dz {
        int[] mo = new int[0]; boolean small, kc; Canvas x; float W, H, k, f; int v, g, ke, kg, kv, kp, ti, fg, mut, inv, glowC; int[] w7; String lbl, unit, sub, pct; Th T;
        int a(float al) { return T.a(al); }
        Paint fill(int c) { Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); p.setColor(c); return p; }
        Paint stroke(int c, float w) { Paint p = fill(c); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(w); return p; }
        Paint round(Paint p) { p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND); return p; }
        Paint glow(Paint p, float b) { p.setShadowLayer(b * k, 0, 0, glowC); return p; }
        void rr(float l, float t, float w, float h, float r, Paint p) { x.drawRoundRect(new RectF(l, t, l + w, t + h), r, r, p); }
        RectF ov(float cx, float cy, float rx, float ry) { return new RectF(cx - rx, cy - ry, cx + rx, cy + ry); }
        float t(String s, float X, float Y, float sz, int col, Paint.Align al, boolean b8, float ls, float maxW) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); p.setTypeface(b8 ? T8 : T7); p.setTextSize(sz); p.setColor(col); p.setTextAlign(al);
            if (ls != 0) p.setLetterSpacing(ls / sz);
            if (maxW > 0) while (sz > 4 && p.measureText(s) > maxW) { sz *= .94f; p.setTextSize(sz); if (ls != 0) p.setLetterSpacing(ls / sz); }
            x.drawText(s, X, Y, p); return sz;
        }
        void t(String s, float X, float Y, float sz, int col) { t(s, X, Y, sz, col, Paint.Align.CENTER, true, 0, W * .9f); }
        void tl(String s, float X, float Y, float sz, int col, boolean b8) { t(s, X, Y, sz, col, Paint.Align.LEFT, b8, 0, W - X - 10 * k); }
        void shoe(float cx, float cy, float sz, int col, float lw) {
            x.save(); x.translate(cx - sz / 2, cy - sz / 2); x.scale(sz / 24, sz / 24);
            Path q = new Path(); q.moveTo(3, 15.5f); q.cubicTo(3, 12, 5, 11, 7.5f, 11); q.lineTo(10, 8.5f); q.cubicTo(11, 7.5f, 12.5f, 8, 13, 9); q.lineTo(14.5f, 11.5f); q.cubicTo(17, 12.5f, 21, 13.5f, 21, 16); q.lineTo(21, 17.5f); q.lineTo(3, 17.5f); q.close();
            q.moveTo(8, 14); q.lineTo(10, 14); q.moveTo(11, 12.5f); q.lineTo(12.5f, 12.5f);
            x.drawPath(q, round(stroke(col, lw))); x.restore();
        }
        void planet(float px, float py, float R) {
            Paint g = fill(0); g.setShader(new android.graphics.RadialGradient(px - R * .4f, py - R * .5f, R * 1.3f, new int[] { 0xFFFFFFFF, 0xFF9A9AA2, 0xFF2A2A30 }, new float[] { 0, .5f, 1 }, Shader.TileMode.CLAMP));
            x.drawCircle(px, py, R, g);
        }
        float tw(String s, float sz, boolean b8) { Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); p.setTypeface(b8 ? T8 : T7); p.setTextSize(sz); return p.measureText(s); }
        void halo(String s, float X, float Y, float sz, int col, boolean b8, Paint.Align al) {
            Paint o = new Paint(Paint.ANTI_ALIAS_FLAG); o.setTypeface(b8 ? T8 : T7); o.setTextSize(sz); o.setTextAlign(al); o.setColor(0xD90B0B0F);
            o.setStyle(Paint.Style.STROKE); o.setStrokeWidth(sz * .22f); o.setStrokeJoin(Paint.Join.ROUND); x.drawText(s, X, Y, o);
            t(s, X, Y, sz, col, al, b8, 0, 0);
        }
        void flame(float cx, float cy, float sz, int col) {
            x.save(); x.translate(cx - sz / 2, cy - sz / 2); x.scale(sz / 24, sz / 24);
            Path q = new Path(); q.moveTo(12, 3); q.cubicTo(13, 7, 18, 9, 18, 14); q.arcTo(new RectF(6, 8, 18, 20), 0, 180, false);
            q.cubicTo(6, 11, 8, 9, 9, 8); q.cubicTo(9, 10, 10, 11, 11, 11); q.cubicTo(11, 8, 10, 6, 12, 3); q.close();
            x.drawPath(q, round(stroke(col, 2.2f))); x.restore();
        }
        /** Calories mangées sur le design Sommet ; kv = disposition choisie dans les réglages du widget. */
        void sommetKcal() {
            String n = fmt(ke), gl = "/ " + fmt(kg) + " kcal"; float kf = Math.min(1, ke / (float) kg), by = H - 16 * k;
            if (small) { float ky = H - 14 * k, ks = t(n, 16 * k, ky, 26 * k, fg, Paint.Align.LEFT, true, 0, W * .5f); t("kcal", 16 * k + tw(n, ks, true) + 4 * k, ky, 11 * k, mut, Paint.Align.LEFT, false, 0, 0); return; }
            // à droite : décale le bloc pour qu'il finisse à la même marge que le bord gauche
            float bw = kv == 1 ? tw(n, 20 * k, true) + 4 * k + tw(gl, 10.5f * k, false)
                : kv == 2 ? 17 * k + tw(n, 13 * k, true) + 3 * k + tw(gl, 10 * k, false)
                : kv == 5 ? tw(n + " kcal", 12 * k, true) + 34 * k - 4 * k
                : kv == 6 ? 35 * k + Math.max(tw(n, 15 * k, true), tw(gl, 9 * k, false)) : -1;
            boolean shift = kp == 1 && bw > 0; if (shift) { x.save(); x.translate(W - 32 * k - bw, 0); }
            sommetKcalAt(n, gl, kf, by);
            if (shift) x.restore();
        }
        void sommetKcalAt(String n, String gl, float kf, float by) {
            switch (kv) {
                case 1: { float w = tw(n, 20 * k, true); t(n, 16 * k, by, 20 * k, fg, Paint.Align.LEFT, true, 0, 0); t(gl, 16 * k + w + 4 * k, by, 10.5f * k, mut, Paint.Align.LEFT, false, 0, 0); break; }
                case 2: { flame(23 * k, by - 5 * k, 13 * k, mut); float w = tw(n, 13 * k, true); t(n, 33 * k, by, 13 * k, fg, Paint.Align.LEFT, true, 0, 0); t(gl, 33 * k + w + 3 * k, by, 10 * k, mut, Paint.Align.LEFT, false, 0, 0); break; }
                case 3: { flame(21 * k, 61 * k, 11 * k, fg); t(n + " kcal", 30 * k, 65 * k, 9.5f * k, fg, Paint.Align.LEFT, true, 0, W * .46f - 28 * k); break; }
                case 4: { float bw = W - 32 * k; rr(16 * k, by - 2 * k, bw, 5 * k, 2.5f * k, fill(a(.12f))); rr(16 * k, by - 2 * k, Math.max(5 * k, bw * kf), 5 * k, 2.5f * k, fill(fg));
                    float w = tw(n, 14 * k, true); t(n, 16 * k, by - 9 * k, 14 * k, fg, Paint.Align.LEFT, true, 0, 0); t(gl, 16 * k + w + 3 * k, by - 9 * k, 9.5f * k, mut, Paint.Align.LEFT, false, 0, 0); break; }
                case 5: { String tx = n + " kcal"; float pw = tw(tx, 12 * k, true) + 34 * k; rr(12 * k, by - 17 * k, pw, 24 * k, 12 * k, fill(0xBF141418)); rr(12 * k, by - 17 * k, pw, 24 * k, 12 * k, stroke(a(.14f), k));
                    flame(25 * k, by - 5 * k, 12 * k, fg); t(tx, 34 * k, by - k, 12 * k, fg, Paint.Align.LEFT, true, 0, 0); break; }
                case 6: { float ccx = 30 * k, ccy = by - 12 * k, r = 13 * k; x.drawCircle(ccx, ccy, r, stroke(a(.14f), 3.5f * k));
                    x.drawArc(ov(ccx, ccy, r, r), -90, Math.max(1, kf * 360), false, round(stroke(fg, 3.5f * k))); flame(ccx, ccy, 12 * k, fg);
                    t(n, ccx + r + 8 * k, ccy + k, 15 * k, fg, Paint.Align.LEFT, true, 0, 0); t(gl, ccx + r + 8 * k, ccy + 13 * k, 9 * k, mut, Paint.Align.LEFT, false, 0, 0); break; }
                case 7: { float uw = tw("kcal", 10 * k, false); t("kcal", W - 16 * k, by, 10 * k, mut, Paint.Align.RIGHT, false, 0, 0); t(n, W - 16 * k - uw - 4 * k, by, 18 * k, fg, Paint.Align.RIGHT, true, 0, 0);
                    t(Math.round(kf * 100) + " % de " + fmt(kg), W - 16 * k, by - 21 * k, 9 * k, mut, Paint.Align.RIGHT, false, 0, 0); break; }
                case 8: { halo(fmt(v), 16 * k, 34 * k, 22 * k, fg, true, Paint.Align.LEFT); halo("pas", 16 * k, 48 * k, 10 * k, mut, false, Paint.Align.LEFT);
                    x.drawRect(W * .5f, 18 * k, W * .5f + k, 52 * k, fill(a(.18f)));
                    halo(n, W * .5f + 9 * k, 34 * k, 22 * k, fg, true, Paint.Align.LEFT); halo(gl, W * .5f + 9 * k, 48 * k, 10 * k, mut, false, Paint.Align.LEFT); break; }
            }
        }
        String digits() { String d = String.valueOf(v); while (d.length() < 5) d = "0" + d; return d; }
        void odoCell(float xx, float y0, float cw, float ch, String c, boolean lead) {
            Paint g = fill(0); g.setShader(new LinearGradient(0, y0, 0, y0 + ch, new int[] { a(.16f), a(.07f), a(.03f), a(.1f) }, new float[] { 0, .5f, .5f, 1 }, Shader.TileMode.CLAMP));
            rr(xx, y0, cw, ch, 5 * k, g); rr(xx, y0, cw, ch, 5 * k, stroke(a(.12f), Math.max(1, k * .5f)));
            x.drawRect(xx, y0 + ch / 2 - .5f * k, xx + cw, y0 + ch / 2 + .5f * k, fill(0x99000000));
            t(c, xx + cw / 2, y0 + ch * .74f, ch * .62f, lead ? a(.25f) : fg, Paint.Align.CENTER, true, 0, 0);
        }

        void draw(int id) {
            float cx = W / 2, cy = H / 2;
            switch (id) {
                case 0: { // Orbite
                    float oy = H * .47f, rx = W * .4f, ry = H * .14f;
                    x.save(); x.translate(cx, oy); x.rotate((float) Math.toDegrees(-.22));
                    x.drawOval(ov(0, 0, rx, ry), stroke(a(.14f), 2 * k));
                    x.drawArc(ov(0, 0, rx, ry), 90, Math.max(1, f * 360), false, glow(round(stroke(fg, 2.6f * k)), 8));
                    double a1 = Math.toRadians(90 + f * 360); float px = (float) Math.cos(a1) * rx, py = (float) Math.sin(a1) * ry;
                    Paint pl = fill(0); pl.setShader(new android.graphics.RadialGradient(px - 3 * k, py - 3 * k, 8 * k, fg, a(.5f), Shader.TileMode.CLAMP)); x.drawCircle(px, py, 6.5f * k, pl);
                    x.restore();
                    t(fmt(v), cx, oy + 12 * k, 30 * k, fg); t(pct + "  ·  " + sub, cx, H - 20 * k, 11 * k, mut, Paint.Align.CENTER, false, .5f * k, W * .9f); break; }
                case 1: { // Segments
                    float r = W * .38f; int n = 20; float on = f * n;
                    for (int i = 0; i < n; i++) { float a = -90 + (i + .5f) * 18, sp = 18 * .36f;
                        x.drawArc(ov(cx, cy, r, r), a - sp, sp * 2, false, round(stroke(i < (int) on ? fg : i < on ? a(.55f) : a(.1f), 9 * k))); }
                    shoe(cx, cy - 22 * k, 16 * k, a(.7f), 2); t(fmt(v), cx, cy + 10 * k, 27 * k, fg); t("/ " + fmt(g), cx, cy + 27 * k, 11 * k, mut, Paint.Align.CENTER, false, 0, 0); break; }
                case 2: { // Compteur
                    String d = digits(); int lead = d.length() - String.valueOf(v).length();
                    float cw = W * .15f * Math.min(1, 5f / d.length()), ch = cw * 1.45f, gap = 4 * k, tw = d.length() * cw + (d.length() - 1) * gap, x0 = (W - tw) / 2, y0 = H * .3f;
                    t(lbl, cx, y0 - 12 * k, 10 * k, mut, Paint.Align.CENTER, true, 3 * k, 0);
                    for (int i = 0; i < d.length(); i++) odoCell(x0 + i * (cw + gap), y0, cw, ch, d.substring(i, i + 1), i < lead);
                    float by = y0 + ch + 18 * k, bw = W * .7f; rr((W - bw) / 2, by, bw, 4 * k, 2 * k, fill(a(.1f))); rr((W - bw) / 2, by, Math.max(4 * k, bw * f), 4 * k, 2 * k, fill(fg));
                    t(pct + " de " + fmt(g), cx, by + 20 * k, 11 * k, mut, Paint.Align.CENTER, false, 0, 0); break; }
                case 3: { // Sommet
                    float[][] P = { { -.02f, .9f }, { .16f, .64f }, { .28f, .72f }, { .46f, .36f }, { .57f, .48f }, { .7f, .26f }, { .84f, .54f }, { 1.02f, .86f } };
                    for (float[] q : P) { q[0] *= W; q[1] *= H; }
                    Path path = new Path(); path.moveTo(P[0][0], P[0][1]); for (int i = 1; i < P.length; i++) path.lineTo(P[i][0], P[i][1]);
                    Path fp = new Path(path); fp.lineTo(W + 4 * k, H + 4 * k); fp.lineTo(-4 * k, H + 4 * k); fp.close();
                    Paint mf = fill(0); mf.setShader(new LinearGradient(0, H * .26f, 0, H, a(.13f), a(.04f), Shader.TileMode.CLAMP)); x.drawPath(fp, mf);
                    Path back = new Path(); back.moveTo(W * .3f, H + 4 * k); back.lineTo(W * .62f, H * .58f); back.lineTo(W * .78f, H * .7f); back.lineTo(W + 4 * k, H * .62f); back.lineTo(W + 4 * k, H + 4 * k); back.close(); x.drawPath(back, fill(a(.04f)));
                    x.drawPath(path, round(stroke(a(.22f), 2 * k)));
                    int peak = 5; float L = 0; float[] lens = new float[peak]; for (int i = 1; i <= peak; i++) { L += (float) Math.hypot(P[i][0] - P[i - 1][0], P[i][1] - P[i - 1][1]); lens[i - 1] = L; }
                    float tg = L * f, ex = P[0][0], ey = P[0][1]; Path done = new Path(); done.moveTo(ex, ey);
                    for (int i = 1; i <= peak; i++) { float l0 = i > 1 ? lens[i - 2] : 0, seg = lens[i - 1] - l0;
                        if (tg >= lens[i - 1]) { ex = P[i][0]; ey = P[i][1]; done.lineTo(ex, ey); }
                        else { float q = (tg - l0) / seg; ex = P[i - 1][0] + (P[i][0] - P[i - 1][0]) * q; ey = P[i - 1][1] + (P[i][1] - P[i - 1][1]) * q; done.lineTo(ex, ey); break; } }
                    x.drawPath(done, glow(round(stroke(fg, 3 * k)), 8)); x.drawCircle(ex, ey, 5 * k, fill(fg));
                    float[] pk = P[peak]; x.drawLine(pk[0], pk[1], pk[0], pk[1] - 16 * k, stroke(fg, 1.5f * k));
                    Path fl = new Path(); fl.moveTo(pk[0], pk[1] - 16 * k); fl.lineTo(pk[0] + 10 * k, pk[1] - 12 * k); fl.lineTo(pk[0], pk[1] - 8 * k); fl.close(); x.drawPath(fl, fill(fg));
                    if (small) t(compact(v), 14 * k, 46 * k, 40 * k, fg, Paint.Align.LEFT, true, -k, W * .58f);
                    else if (kc || kv != 8) { tl(fmt(v), 16 * k, 34 * k, 24 * k, fg, true); tl(unit + " · " + sub, 16 * k, 49 * k, 10.5f * k, mut, false); }
                    if (!kc && kv > 0) sommetKcal();
                    break; }
                case 4: { // Empreintes
                    int n = 10, on = Math.round(f * n);
                    for (int i = 0; i < n; i++) { float tt = i / (float) (n - 1), px = W * (.16f + .68f * tt), py = H * (.78f - .2f * (float) Math.sin(tt * Math.PI)) + (i % 2 == 1 ? -7 : 7) * k;
                        x.save(); x.translate(px, py); x.rotate((float) Math.toDegrees(Math.PI / 2 - .5 * Math.cos(tt * Math.PI) + (i % 2 == 1 ? .12 : -.12)));
                        Paint p = fill(i < on ? fg : a(.12f)); if (i == on - 1) glow(p, 10);
                        x.drawOval(ov(0, 0, 3.4f * k, 5.6f * k), p); x.drawOval(ov(0, -8.6f * k, 2.4f * k, 2.6f * k), p); x.restore(); }
                    t(fmt(v), cx, H * .36f, 32 * k, fg); t(lbl + " · " + pct, cx, H * .36f + 17 * k, 10 * k, mut, Paint.Align.CENTER, true, 2 * k, 0); break; }
                case 5: { // Minimal
                    float p = 18 * k; t("AUJOURD'HUI", p, p + 8 * k, 10 * k, mut, Paint.Align.LEFT, true, 2.5f * k, 0);
                    t(fmt(v), p - 2 * k, H * .62f, 46 * k, fg, Paint.Align.LEFT, true, -2 * k, W - 2 * p);
                    float ly = H - p - 14 * k; x.drawLine(p, ly, W - p, ly, stroke(a(.15f), 1.2f * k));
                    x.drawLine(p, ly, p + (W - 2 * p) * f, ly, round(stroke(fg, 2.4f * k)));
                    tl(unit, p, H - p + 2 * k, 11 * k, mut, false); t(sub, W - p, H - p + 2 * k, 11 * k, mut, Paint.Align.RIGHT, false, 0, 0); break; }
                case 6: { // Barres (long)
                    float p = 18 * k; shoe(p + 12 * k, cy, 22 * k, fg, 1.8f);
                    t(fmt(v), p + 32 * k, cy + 6 * k, 28 * k, fg, Paint.Align.LEFT, true, 0, W * .5f - p - 34 * k);
                    t("/ " + fmt(g) + " " + unit, p + 32 * k, cy + 22 * k, 10.5f * k, mut, Paint.Align.LEFT, false, 0, W * .5f - p - 34 * k);
                    float bx = W * .55f, bw = W * .45f - p; int n = 24; float cw = bw / n;
                    for (int i = 0; i < n; i++) { boolean on = i < f * n; float hh = (10 + 18 * Math.abs((float) Math.sin(i * 1.7 + 1))) * k * (on ? 1 : .6f); hh = Math.min(hh, H - 16 * k);
                        rr(bx + i * cw + cw * .2f, cy - hh / 2, cw * .6f, hh, cw * .3f, fill(on ? fg : a(.12f))); }
                    break; }
                case 7: { // Odomètre (long)
                    float p = 18 * k; String d = digits(); int lead = d.length() - String.valueOf(v).length(); float cw = 22 * k, ch = 34 * k, gap = 3 * k, y0 = (H - ch) / 2;
                    for (int i = 0; i < d.length(); i++) odoCell(p + i * (cw + gap), y0, cw, ch, d.substring(i, i + 1), i < lead);
                    float tx = p + d.length() * (cw + gap) + 10 * k;
                    t(lbl, tx, cy - 4 * k, 10 * k, mut, Paint.Align.LEFT, true, 2.5f * k, 0); t(pct + " · " + sub, tx, cy + 12 * k, 11 * k, fg, Paint.Align.LEFT, false, 0, W - tx - 60 * k);
                    float rx = W - p - 20 * k; x.drawCircle(rx, cy, 18 * k, stroke(a(.1f), 4 * k)); x.drawArc(ov(rx, cy, 18 * k, 18 * k), -90, Math.max(1, f * 360), false, round(stroke(fg, 4 * k))); shoe(rx, cy, 16 * k, fg, 1.8f); break; }
                case 8: { // Sablier
                    float sx = W * .3f, top = H * .2f, bot = H * .8f, w = W * .17f, mid = (top + bot) / 2;
                    Path up = new Path(); up.moveTo(sx - w, top); up.lineTo(sx + w, top); up.lineTo(sx + 3 * k, mid); up.lineTo(sx - 3 * k, mid); up.close();
                    Path dn = new Path(); dn.moveTo(sx - 3 * k, mid); dn.lineTo(sx + 3 * k, mid); dn.lineTo(sx + w, bot); dn.lineTo(sx - w, bot); dn.close();
                    x.save(); x.clipPath(up); x.drawRect(0, top + (mid - top) * f, W, H, fill(fg)); x.restore();
                    x.save(); x.clipPath(dn); x.drawRect(0, bot - (bot - mid) * f, W, H, fill(fg)); x.restore();
                    Paint s = stroke(a(.5f), 1.5f * k); x.drawPath(up, s); x.drawPath(dn, s);
                    x.drawRect(sx - w - 4 * k, top - 4 * k, sx + w + 4 * k, top - k, fill(fg)); x.drawRect(sx - w - 4 * k, bot + k, sx + w + 4 * k, bot + 4 * k, fill(fg));
                    tl(fmt(v), W * .56f, cy, 22 * k, fg, true); tl(unit + " · " + pct, W * .56f, cy + 16 * k, 10 * k, mut, false); break; }
                case 9: { // Aiguille
                    float gy = H * .6f, r = W * .36f;
                    for (int i = 0; i <= 40; i++) { double a = Math.PI * (1 + i / 40.0); float L = (i % 5 != 0 ? 5 : 10) * k;
                        x.drawLine(cx + (float) Math.cos(a) * r, gy + (float) Math.sin(a) * r, cx + (float) Math.cos(a) * (r - L), gy + (float) Math.sin(a) * (r - L), stroke(i / 40f <= f ? fg : a(.12f), (i % 5 != 0 ? 1.2f : 2.2f) * k)); }
                    double a = Math.PI * (1 + f); x.drawLine(cx, gy, cx + (float) Math.cos(a) * r * .8f, gy + (float) Math.sin(a) * r * .8f, glow(round(stroke(fg, 2.5f * k)), 6));
                    x.drawCircle(cx, gy, 5 * k, fill(fg)); t(fmt(v), cx, H * .86f, 22 * k, fg);
                    t("0", cx - r, gy + 14 * k, 9 * k, mut, Paint.Align.CENTER, true, 0, 0); t(compact(g), cx + r, gy + 14 * k, 9 * k, mut, Paint.Align.CENTER, true, 0, 0); break; }
                case 10: { // Montre
                    float r = W * .38f;
                    for (int i = 0; i < 12; i++) { double a = i * Math.PI / 6; x.drawCircle(cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r, (i % 3 != 0 ? 1.6f : 2.6f) * k, fill(i % 3 != 0 ? mut : fg)); }
                    t(lbl, cx, cy - r * .42f, 9 * k, mut, Paint.Align.CENTER, true, 2 * k, 0);
                    double a1 = -Math.PI / 2 + f * Math.PI * 2; x.drawLine(cx, cy, cx + (float) Math.cos(a1) * r * .78f, cy + (float) Math.sin(a1) * r * .78f, round(stroke(fg, 3 * k)));
                    double a2 = -Math.PI / 2 + (v % 1000) / 1000.0 * Math.PI * 2; x.drawLine(cx, cy, cx + (float) Math.cos(a2) * r * .55f, cy + (float) Math.sin(a2) * r * .55f, round(stroke(a(.45f), 2 * k)));
                    x.drawCircle(cx, cy, 4 * k, fill(fg)); t(fmt(v), cx, cy + r * .58f, 14 * k, fg); break; }
                case 11: { // 3 anneaux : aujourd'hui, hier, moyenne de la semaine
                    float yv = ti > 0 ? Math.min(1, w7[ti - 1] / (float) g) : 0; int sum = 0; for (int i = 0; i <= ti; i++) sum += w7[i];
                    float[][] R = { { f, .4f }, { yv, .3f }, { Math.min(1, sum / (float) (ti + 1) / g), .2f } };
                    for (int i = 0; i < 3; i++) { float r = W * R[i][1]; x.drawCircle(cx, cy, r, stroke(a(.08f), 7 * k));
                        if (R[i][0] > 0) x.drawArc(ov(cx, cy, r, r), -90, Math.max(1, R[i][0] * 360), false, round(stroke(a(1 - i * .28f), 7 * k))); }
                    t(compact(v), cx, cy + 5 * k, 14 * k, fg); break; }
                case 12: { // Matrice
                    int n = 10; float p = 20 * k, top = 46 * k, cs = (W - 2 * p) / n; int rows = Math.max(1, (int) ((H - top - p) / cs)), on = Math.round(f * 100);
                    tl(fmt(v), p, 32 * k, 20 * k, fg, true); t(pct, W - p, 32 * k, 11 * k, mut, Paint.Align.RIGHT, true, 0, 0);
                    for (int i = 0; i < n * rows; i++) { int q = Math.round(i * 100f / (n * rows)); x.drawCircle(p + (i % n + .5f) * cs, top + (i / n + .5f) * cs, cs * .3f, fill(q < on ? fg : a(.12f))); }
                    break; }
                case 13: { // Vague
                    float r = W * .38f, lv = cy + r - 2 * r * f; Path circ = new Path(); circ.addCircle(cx, cy, r, Path.Direction.CW);
                    x.save(); x.clipPath(circ); x.drawRect(0, 0, W, H, fill(a(.05f)));
                    Path front = null;
                    for (int j = 0; j < 2; j++) { float o = j == 0 ? 0 : 1.6f; Path wv = new Path(); wv.moveTo(0, H);
                        for (float px = 0; px <= W; px += 2) wv.lineTo(px, lv + (float) Math.sin(px / W * 9 + o) * 4 * k); wv.lineTo(W, H); wv.close();
                        x.drawPath(wv, fill(j == 0 ? a(.3f) : fg)); front = wv; }
                    x.restore(); x.drawCircle(cx, cy, r, stroke(a(.35f), 1.5f * k));
                    t(fmt(v), cx, cy + 6 * k, 24 * k, fg); t(pct, cx, cy + 22 * k, 10 * k, fg);
                    x.save(); x.clipPath(circ); x.clipPath(front); t(fmt(v), cx, cy + 6 * k, 24 * k, inv); t(pct, cx, cy + 22 * k, 10 * k, inv); x.restore(); break; }
                case 14: { // Spirale
                    float turns = 2.6f; int N = 300; Path tr = new Path(), pr = new Path(); float ex = 0, ey = 0;
                    for (int i = 0; i <= N; i++) { float tt = i / (float) N; double a = -Math.PI / 2 + tt * turns * Math.PI * 2; float r = W * (.08f + .32f * tt), px = cx + (float) Math.cos(a) * r, py = cy + (float) Math.sin(a) * r;
                        if (i == 0) { tr.moveTo(px, py); pr.moveTo(px, py); } else { tr.lineTo(px, py); if (tt <= f) pr.lineTo(px, py); } if (tt <= f) { ex = px; ey = py; } }
                    x.drawPath(tr, round(stroke(a(.12f), 3 * k))); x.drawPath(pr, glow(round(stroke(fg, 3 * k)), 6)); x.drawCircle(ex, ey, 4.5f * k, fill(fg));
                    t(fmt(v), W - 14 * k, H - 14 * k, 13 * k, fg, Paint.Align.RIGHT, true, 0, 0); break; }
                case 15: { // Route
                    float hz = H * .42f;
                    Path road = new Path(); road.moveTo(cx - 3 * k, hz); road.lineTo(cx + 3 * k, hz); road.lineTo(W * .9f, H); road.lineTo(W * .1f, H); road.close(); x.drawPath(road, fill(a(.06f)));
                    Paint ed = stroke(a(.4f), 1.2f * k); x.drawLine(cx - 3 * k, hz, W * .1f, H, ed); x.drawLine(cx + 3 * k, hz, W * .9f, H, ed);
                    for (int i = 0; i < 7; i++) { float t0 = (float) Math.pow(i / 7.0, 1.8), t1 = (float) Math.pow((i + .5) / 7.0, 1.8), y0 = hz + (H - hz) * t0, y1 = hz + (H - hz) * t1;
                        Path q = new Path(); q.moveTo(cx - .4f * k - t0 * 3 * k, y0); q.lineTo(cx + .4f * k + t0 * 3 * k, y0); q.lineTo(cx + .4f * k + t1 * 3 * k, y1); q.lineTo(cx - .4f * k - t1 * 3 * k, y1); q.close();
                        x.drawPath(q, fill((1 - i / 7f) <= f ? a(.35f) : fg)); }
                    x.drawCircle(cx, hz, 3 * k, glow(fill(fg), 10)); t(fmt(v), cx, 30 * k, 20 * k, fg); t(sub, cx, 44 * k, 10 * k, mut, Paint.Align.CENTER, false, 0, 0); break; }
                case 16: { // Escalier
                    int n = 8, on = Math.round(f * n); float p = 16 * k, sw = (W - 2 * p) / n, sh = (H * .62f) / n;
                    for (int i = 0; i < n; i++) rr(p + i * sw, H - p - (i + 1) * sh, sw - 2 * k, (i + 1) * sh, 3 * k, fill(i < on ? a(.35f + .65f * i / n) : a(.07f)));
                    tl(fmt(v), p, 30 * k, 20 * k, fg, true); tl("marche " + on + "/" + n, p, 44 * k, 10 * k, mut, false); break; }
                case 17: { // Vertical
                    String d = String.valueOf(v); float s = (H - 30 * k) / Math.max(4, d.length());
                    for (int i = 0; i < d.length(); i++) t(d.substring(i, i + 1), W * .3f, 22 * k + s * (i + .82f), s * 1.05f, fg, Paint.Align.CENTER, true, 0, 0);
                    float th = H - 40 * k; rr(W * .62f, 20 * k, 6 * k, th, 3 * k, fill(a(.12f))); rr(W * .62f, 20 * k + th * (1 - f), 6 * k, Math.max(6 * k, th * f), 3 * k, fill(fg));
                    tl(pct, W * .75f, cy, 12 * k, fg, true); t(lbl, W * .75f, cy + 14 * k, 9 * k, mut, Paint.Align.LEFT, true, 2 * k, 0); break; }
                case 18: { // Pourcent
                    t(String.valueOf(Math.round(f * 100)), W * .48f, H * .62f, 64 * k, fg, Paint.Align.CENTER, true, -3 * k, W * .7f); t("%", W * .84f, H * .42f, 18 * k, mut);
                    t(fmt(v) + " / " + fmt(g) + " " + unit, cx, H * .84f, 10.5f * k, mut, Paint.Align.CENTER, false, 0, W * .9f); break; }
                case 19: { // Tachymètre
                    float gy = H * .62f, r = W * .4f;
                    for (int i = 0; i < 9; i++) { float a0 = 180 + i * 20 + 1.7f; Paint p = stroke((i + 1) / 9f <= f + .001f ? a(.4f + .6f * i / 8) : a(.08f), 14 * k); x.drawArc(ov(cx, gy, r, r), a0, 20 - 3.4f, false, p); }
                    t(fmt(v), cx, gy, 22 * k, fg); t(lbl, cx, gy + 15 * k, 9 * k, mut, Paint.Align.CENTER, true, 2.5f * k, 0); break; }
                case 20: { // Étoiles
                    float[][] P = { { .18f, .7f }, { .3f, .48f }, { .46f, .58f }, { .58f, .32f }, { .72f, .44f }, { .84f, .22f }, { .7f, .75f }, { .88f, .62f } };
                    for (float[] q : P) { q[0] *= W; q[1] *= H; } int on = Math.round(f * P.length);
                    for (int i = 1; i < P.length; i++) x.drawLine(P[i - 1][0], P[i - 1][1], P[i][0], P[i][1], stroke(i < on ? a(.7f) : a(.1f), 1.2f * k));
                    for (int i = 0; i < P.length; i++) { Paint p = fill(i < on ? fg : a(.2f)); if (i < on) glow(p, 6); x.drawCircle(P[i][0], P[i][1], (i < on ? 3.4f : 2.4f) * k, p); }
                    tl(fmt(v), 16 * k, 30 * k, 20 * k, fg, true); tl(on + "/" + P.length + " étoiles", 16 * k, 44 * k, 10 * k, mut, false); break; }
                case 21: { // Code-barres
                    float p = 18 * k; int n = 46; float bw = (W - 2 * p) / n; float[] ws = { .3f, .55f, .8f, .4f };
                    for (int i = 0; i < n; i++) { float w = ws[(i * 7 + 3) % 4] * bw; x.drawRect(p + i * bw, 24 * k, p + i * bw + w, 24 * k + H * .5f, fill(i / (float) n < f ? fg : a(.14f))); }
                    t(fmt(v), cx, H * .5f + 48 * k, 18 * k, fg, Paint.Align.CENTER, true, 4 * k, W * .9f); break; }
                case 22: { // Ticket
                    float p = 18 * k;
                    if (small) {
                        t("TICKET", cx, 40 * k, 20 * k, mut, Paint.Align.CENTER, true, 3 * k, W * .8f);
                        Paint ds = stroke(a(.3f), 2 * k); ds.setPathEffect(new DashPathEffect(new float[] { 6 * k, 5 * k }, 0));
                        x.drawLine(p, 56 * k, W - p, 56 * k, ds); x.drawLine(p, H - 44 * k, W - p, H - 44 * k, ds);
                        t(compact(v), cx, cy + 16 * k, 48 * k, fg, Paint.Align.CENTER, true, -k, W - 2 * p);
                        t(pct, cx, H - 16 * k, 20 * k, fg, Paint.Align.CENTER, true, 0, W * .8f); break; } t("TICKET DU JOUR", cx, 30 * k, 9 * k, mut, Paint.Align.CENTER, true, 2.5f * k, 0);
                    Paint ds = stroke(a(.25f), k); ds.setPathEffect(new DashPathEffect(new float[] { 3 * k, 3 * k }, 0));
                    x.drawLine(p, 42 * k, W - p, 42 * k, ds); x.drawLine(p, H - 46 * k, W - p, H - 46 * k, ds);
                    String[][] rows = { { unit.equals("pas") ? "Pas" : "Kcal", fmt(v) }, { unit.equals("pas") ? "Distance" : "Objectif", sub }, { "Progression", pct } };
                    for (int i = 0; i < 3; i++) { float y = 64 * k + i * 18 * k; tl(rows[i][0], p, y, 11 * k, mut, false); t(rows[i][1], W - p, y, 11 * k, fg, Paint.Align.RIGHT, true, 0, 0); }
                    t("TOTAL  " + fmt(v), cx, H - 22 * k, 13 * k, fg, Paint.Align.CENTER, true, k, W * .9f); break; }
                case 23: { // Semaine +
                    float p = 18 * k, top = 50 * k, bh = H - top - 30 * k, bw = (W - 2 * p) / 7; int mx = Math.max(1, g); for (int q : w7) mx = Math.max(mx, q);
                    tl(fmt(v), p, 32 * k, 20 * k, fg, true);
                    Paint ds = stroke(a(.3f), k); ds.setPathEffect(new DashPathEffect(new float[] { 2 * k, 3 * k }, 0)); float gy = top + bh * (1 - g / (float) mx); x.drawLine(p, gy, W - p, gy, ds);
                    String days = "LMMJVSD";
                    for (int i = 0; i < 7; i++) { float h = Math.max(4 * k, bh * w7[i] / mx); rr(p + i * bw + bw * .22f, top + bh - h, bw * .56f, h, bw * .28f, fill(i == ti ? fg : i > ti ? a(.07f) : a(.35f)));
                        t(days.substring(i, i + 1), p + i * bw + bw / 2, H - 14 * k, 9 * k, i == ti ? fg : mut, Paint.Align.CENTER, true, 0, 0); }
                    break; }
                case 24: { // Thermomètre
                    float sx = W * .28f, top = 22 * k, bot = H - 40 * k, w = 12 * k;
                    rr(sx - w / 2, top, w, bot - top, w / 2, fill(a(.08f))); x.drawCircle(sx, bot + 8 * k, 13 * k, fill(a(.08f)));
                    Paint fp = glow(fill(fg), 8); rr(sx - w / 2 + 3 * k, top + 3 * k + (bot - top) * (1 - f), w - 6 * k, Math.max(0, (bot - top) * f), w / 2, fp); x.drawCircle(sx, bot + 8 * k, 9 * k, fp);
                    for (int i = 0; i <= 4; i++) { float y = top + (bot - top) * i / 4; x.drawRect(sx + w, y, sx + w + 6 * k, y + k, fill(mut)); }
                    tl(fmt(v), W * .48f, cy, 22 * k, fg, true); tl(unit + " · " + pct, W * .48f, cy + 15 * k, 10 * k, mut, false); break; }
                case 25: { // Piste (long)
                    float p = 16 * k, tw = W * .5f, th = H - 2 * p, tx = W - p - tw, r = th / 2;
                    Path tr = new Path(); tr.addRoundRect(new RectF(tx, p, tx + tw, p + th), r, r, Path.Direction.CW);
                    x.drawPath(tr, stroke(a(.1f), 6 * k)); float per = 2 * (tw - 2 * r) + (float) (2 * Math.PI * r);
                    Paint pp = glow(round(stroke(fg, 6 * k)), 6); pp.setPathEffect(new DashPathEffect(new float[] { Math.max(.1f, per * f), per }, 0)); x.drawPath(tr, pp);
                    t(fmt(v), p + 4 * k, cy + 4 * k, 26 * k, fg, Paint.Align.LEFT, true, 0, tx - p - 12 * k);
                    t(unit + " · " + pct, p + 4 * k, cy + 20 * k, 10 * k, mut, Paint.Align.LEFT, false, 0, tx - p - 12 * k); break; }
                case 26: { // Règle (long)
                    float p = 18 * k, y = H * .62f, L = W - 2 * p;
                    for (int i = 0; i <= 80; i++) { float xx = p + L * i / 80; boolean big = i % 10 == 0; float th = (big ? 14 : i % 5 != 0 ? 6 : 10) * k;
                        x.drawRect(xx, y - th, xx + (big ? 1.6f : 1) * k, y, fill(i / 80f <= f ? fg : a(.2f)));
                        if (big) t(compact(Math.round(g * i / 80f)), xx, y + 12 * k, 8.5f * k, mut, Paint.Align.CENTER, false, 0, 0); }
                    float mx = p + L * f; Path tri = new Path(); tri.moveTo(mx, y - 16 * k); tri.lineTo(mx - 5 * k, y - 24 * k); tri.lineTo(mx + 5 * k, y - 24 * k); tri.close(); x.drawPath(tri, fill(fg));
                    t(fmt(v) + " " + unit, p, 28 * k, 16 * k, fg, Paint.Align.LEFT, true, 0, W * .7f); t(pct, W - p, 28 * k, 12 * k, mut, Paint.Align.RIGHT, true, 0, 0); break; }
                case 28: { // Saturne
                    float sy = H * .46f, R = W * .2f; RectF o = ov(0, 0, R * 2, R * .55f);
                    x.save(); x.translate(cx, sy); x.rotate(-20f); x.drawArc(o, 180, 180, false, stroke(a(.12f), 5 * k));
                    if (f > 0) x.drawArc(o, 180, Math.min(f, .5f) * 360, false, glow(round(stroke(fg, 5 * k)), 6)); x.restore();
                    planet(cx, sy, R);
                    x.save(); x.translate(cx, sy); x.rotate(-20f); x.drawArc(o, 0, 180, false, stroke(a(.12f), 5 * k));
                    if (f > .5f) x.drawArc(o, 0, (f - .5f) * 360, false, glow(round(stroke(fg, 5 * k)), 6)); x.restore();
                    t(fmt(v), cx, H - 26 * k, 22 * k, fg); t(lbl + " · " + pct, cx, H - 12 * k, 9 * k, mut, Paint.Align.CENTER, true, 2 * k, W * .9f); break; }
                case 29: { // Lune
                    float my = H * .42f, R = W * .25f; x.drawCircle(cx, my, R, fill(a(.07f)));
                    Path c0 = new Path(); c0.addCircle(cx, my, R, Path.Direction.CW); x.save(); x.clipPath(c0);
                    float ex = R * (1 - 2 * f); Path lit = new Path(); lit.arcTo(ov(cx, my, R, R), -90, 180); lit.arcTo(ov(cx, my, Math.max(.5f, Math.abs(ex)), R), 90, ex > 0 ? -180 : 180); lit.close();
                    Paint lp = fill(0); lp.setShader(new android.graphics.RadialGradient(cx - R * .3f, my - R * .3f, R, fg, a(.75f), Shader.TileMode.CLAMP)); x.drawPath(lit, lp);
                    Paint cr = fill(0x1F000000); x.drawCircle(cx + .3f * R, my - .2f * R, .16f * R, cr); x.drawCircle(cx - .1f * R, my + .3f * R, .1f * R, cr); x.drawCircle(cx + .1f * R, my + .05f * R, .07f * R, cr); x.restore();
                    x.drawCircle(cx, my, R, glow(stroke(a(.2f), k), 14));
                    t(fmt(v), cx, H - 26 * k, 22 * k, fg); t(f >= 1 ? "PLEINE LUNE" : "LUNE À " + pct, cx, H - 12 * k, 9 * k, mut, Paint.Align.CENTER, true, 2 * k, W * .9f); break; }
                case 30: { // Soleil
                    float hz = H * .66f, R = W * .17f, sy = hz + R - (R * 2.3f) * f;
                    for (int i = 0; i < 12; i++) { double a = Math.PI + i * Math.PI / 11; x.drawLine(cx + (float) Math.cos(a) * R * 1.35f, sy + (float) Math.sin(a) * R * 1.35f, cx + (float) Math.cos(a) * R * 1.75f, sy + (float) Math.sin(a) * R * 1.75f, round(stroke(a(.12f + .25f * f), 2 * k))); }
                    x.save(); x.clipRect(0, 0, W, hz); x.drawCircle(cx, sy, R, glow(fill(fg), 18)); x.restore();
                    x.drawRect(14 * k, hz, W - 14 * k, hz + 1.5f * k, fill(a(.5f)));
                    for (int i = 1; i < 4; i++) x.drawRect(W * (.2f + i * .06f), hz + i * 5 * k, W * (.2f + i * .06f) + W * (.6f - i * .12f), hz + i * 5 * k + 1.2f * k, fill(a(.25f - i * .06f)));
                    t(fmt(v), cx, H - 16 * k, 20 * k, fg); t(pct, W - 14 * k, 24 * k, 11 * k, mut, Paint.Align.RIGHT, true, 0, 0); break; }
                case 31: { // Mois
                    float p = 16 * k, top = 44 * k; int n = Math.max(1, mo.length); float cs = Math.min((W - 2 * p) / 7, (H - top - 10 * k) / 5);
                    tl(fmt(v), p, 30 * k, 18 * k, fg, true);
                    t(new SimpleDateFormat("MMM", Locale.FRANCE).format(new Date()).toUpperCase(Locale.FRANCE), W - p, 30 * k, 10 * k, mut, Paint.Align.RIGHT, true, 2 * k, 0);
                    for (int i = 0; i < n; i++) { float q = Math.min(1, mo[i] / (float) g), l = p + (i % 7) * cs + 1.5f * k, tp = top + (i / 7) * cs + 1.5f * k;
                        rr(l, tp, cs - 3 * k, cs - 3 * k, 3 * k, fill(mo[i] >= g ? fg : a(.07f + .45f * q))); if (i == n - 1) rr(l, tp, cs - 3 * k, cs - 3 * k, 3 * k, stroke(fg, 1.5f * k)); }
                    break; }
                case 32: { // Courbe semaine
                    float p = 18 * k, top = 52 * k, bot = H - 28 * k; int mx = g; for (int q : w7) mx = Math.max(mx, q); float m = mx * 1.1f; int n = ti + 1;
                    tl(fmt(v), p, 32 * k, 20 * k, fg, true);
                    Paint ds = stroke(a(.25f), k); ds.setPathEffect(new DashPathEffect(new float[] { 2 * k, 3 * k }, 0)); float gy = bot - (bot - top) * g / m; x.drawLine(p, gy, W - p, gy, ds);
                    Path ln = new Path(); float[][] P = new float[n][2];
                    for (int i = 0; i < n; i++) { P[i][0] = p + i * (W - 2 * p) / 6; P[i][1] = bot - (bot - top) * w7[i] / m; if (i == 0) ln.moveTo(P[i][0], P[i][1]); else ln.lineTo(P[i][0], P[i][1]); }
                    Path ar = new Path(ln); ar.lineTo(P[n - 1][0], bot); ar.lineTo(p, bot); ar.close(); Paint ap = fill(0); ap.setShader(new LinearGradient(0, top, 0, bot, a(.22f), a(0), Shader.TileMode.CLAMP)); x.drawPath(ar, ap);
                    if (n > 1) x.drawPath(ln, glow(round(stroke(fg, 2.4f * k)), 6));
                    for (int i = 0; i < n; i++) x.drawCircle(P[i][0], P[i][1], (i == ti ? 4.5f : 2.5f) * k, fill(i == ti ? fg : a(.4f)));
                    String days = "LMMJVSD"; for (int i = 0; i < 7; i++) t(days.substring(i, i + 1), p + i * (W - 2 * p) / 6, H - 12 * k, 9 * k, i == ti ? fg : mut, Paint.Align.CENTER, true, 0, 0);
                    break; }
                case 33: { // Chrono
                    float sy = H * .56f, r = W * .34f;
                    rr(cx - 7 * k, sy - r - 16 * k, 14 * k, 8 * k, 2 * k, fill(fg)); x.drawRect(cx - 2 * k, sy - r - 9 * k, cx + 2 * k, sy - r, fill(fg));
                    x.drawCircle(cx, sy, r, stroke(a(.3f), 2 * k)); x.drawArc(ov(cx, sy, r - 5 * k, r - 5 * k), -90, f * 360, true, fill(a(.14f)));
                    for (int i = 0; i < 60; i++) { double a = i * Math.PI / 30; float L = (i % 5 != 0 ? 3 : 6) * k, r0 = r - 4 * k;
                        x.drawLine(cx + (float) Math.cos(a) * r0, sy + (float) Math.sin(a) * r0, cx + (float) Math.cos(a) * (r0 - L), sy + (float) Math.sin(a) * (r0 - L), stroke(i % 5 != 0 ? a(.3f) : fg, k)); }
                    t(fmt(v), cx, sy + 6 * k, 18 * k, fg); t(pct, cx, sy + 20 * k, 9 * k, mut, Paint.Align.CENTER, true, 0, 0); break; }
                case 34: { // Boussole
                    float sy = H * .46f, r = W * .32f; x.drawCircle(cx, sy, r, stroke(a(.25f), 1.5f * k)); String[] C = { "N", "E", "S", "O" };
                    for (int i = 0; i < 4; i++) { double a = -Math.PI / 2 + i * Math.PI / 2; t(C[i], cx + (float) Math.cos(a) * (r - 11 * k), sy + (float) Math.sin(a) * (r - 11 * k) + 3.5f * k, 9 * k, i == 0 ? fg : mut, Paint.Align.CENTER, true, 0, 0); }
                    x.save(); x.translate(cx, sy); x.rotate(f * 360);
                    Path nd = new Path(); nd.moveTo(0, -r * .62f); nd.lineTo(5 * k, 0); nd.lineTo(-5 * k, 0); nd.close(); x.drawPath(nd, glow(fill(fg), 8));
                    Path sd = new Path(); sd.moveTo(0, r * .62f); sd.lineTo(5 * k, 0); sd.lineTo(-5 * k, 0); sd.close(); x.drawPath(sd, fill(a(.25f))); x.restore();
                    x.drawCircle(cx, sy, 3 * k, fill(inv)); t(fmt(v) + " " + unit, cx, H - 14 * k, 14 * k, fg); break; }
                case 35: { // Chargement
                    float p = 18 * k; t("CHARGEMENT…", p, 34 * k, 10 * k, mut, Paint.Align.LEFT, true, 2.5f * k, 0);
                    int n = 10, on = Math.round(f * n); float cw = (W - 2 * p) / n, by = H * .44f;
                    for (int i = 0; i < n; i++) rr(p + i * cw + 1.5f * k, by, cw - 3 * k, 26 * k, 3 * k, fill(i < on ? fg : a(.1f)));
                    rr(p - 3 * k, by - 3 * k, W - 2 * p + 6 * k, 32 * k, 6 * k, stroke(a(.35f), 1.2f * k));
                    tl(pct, p, H - 18 * k, 22 * k, fg, true); t(fmt(v), W - p, H - 18 * k, 12 * k, mut, Paint.Align.RIGHT, false, 0, 0); break; }
                case 36: { // Terminal
                    float p = 16 * k; int on = Math.round(f * 8); StringBuilder bar = new StringBuilder("  "); for (int i = 0; i < 8; i++) bar.append(i < on ? '█' : '░');
                    String[] L = { "> focus --" + unit, "  " + fmt(v), bar.toString(), "  " + pct + " · " + sub, "> _" }; float[] Y = { 32, 64, 88, 108, 134 };
                    for (int i = 0; i < 5; i++) { Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG); tp.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)); tp.setTextSize((i == 1 ? 22 : 11) * k);
                        tp.setColor(i == 0 || i == 4 ? mut : fg); while (tp.getTextSize() > 4 && tp.measureText(L[i]) > W - 2 * p) tp.setTextSize(tp.getTextSize() * .94f); x.drawText(L[i], p, Y[i] * k, tp); }
                    break; }
                case 37: { // Sonar
                    for (int i = 1; i <= 4; i++) x.drawCircle(cx, cy, W * .1f * i, stroke(a(.06f + i * .04f), k));
                    Paint cl = stroke(a(.08f), k); x.drawLine(cx - W * .4f, cy, cx + W * .4f, cy, cl); x.drawLine(cx, cy - W * .4f, cx, cy + W * .4f, cl);
                    double a = -Math.PI / 2 + f * Math.PI * 2; android.graphics.SweepGradient sw = new android.graphics.SweepGradient(cx, cy, new int[] { a(0), a(.35f), a(0), a(0) }, new float[] { 0, .14f, .15f, 1 });
                    Matrix mx = new Matrix(); mx.setRotate((float) Math.toDegrees(a - .9), cx, cy); sw.setLocalMatrix(mx); Paint sp = fill(0); sp.setShader(sw); x.drawCircle(cx, cy, W * .4f, sp);
                    x.drawLine(cx, cy, cx + (float) Math.cos(a) * W * .4f, cy + (float) Math.sin(a) * W * .4f, glow(stroke(fg, 2 * k), 6));
                    t(fmt(v), cx, H - 12 * k, 13 * k, fg); break; }
                case 38: { // Vinyle
                    float sy = H * .48f, r = W * .36f; x.drawCircle(cx, sy, r, fill(T.light ? 0xFF1A1A1E : 0xFF0A0A0C));
                    for (int i = 0; i < 9; i++) x.drawCircle(cx, sy, r * (.45f + i * .06f), stroke(0x0FFFFFFF, k));
                    if (f > 0) x.drawArc(ov(cx, sy, r * .97f, r * .97f), -90, Math.max(1, f * 360), false, glow(round(stroke(T.light ? Color.WHITE : fg, 3 * k)), 6));
                    x.drawCircle(cx, sy, r * .36f, fill(T.light ? Color.WHITE : fg)); int lc = 0xFF111111;
                    t(compact(v), cx, sy + 4 * k, 13 * k, lc, Paint.Align.CENTER, true, 0, r * .6f); t(lbl, cx, sy + 14 * k, 6.5f * k, 0xFF555555, Paint.Align.CENTER, true, k, 0);
                    x.drawCircle(cx, sy - 12 * k, 1.5f * k, fill(lc)); break; }
                case 39: { // Flip
                    String d = String.valueOf(v); int n = d.length(); float gap = 4 * k, cw = (W - 28 * k - gap * (n - 1)) / n, ch = H * .5f, y0 = H * .2f, ts = Math.min(ch * .7f, cw * 1.2f);
                    for (int i = 0; i < n; i++) { float xx = 14 * k + i * (cw + gap);
                        rr(xx, y0, cw, ch / 2 - k, 5 * k, fill(a(.1f))); rr(xx, y0 + ch / 2 + k, cw, ch / 2 - k, 5 * k, fill(a(.07f)));
                        t(d.substring(i, i + 1), xx + cw / 2, y0 + ch * .5f + ts * .36f, ts, fg, Paint.Align.CENTER, true, 0, 0);
                        x.drawRect(xx, y0 + ch / 2 - k, xx + cw, y0 + ch / 2 + k, fill(0x99000000)); }
                    t(lbl + " AUJOURD'HUI", cx, H - 22 * k, 9 * k, mut, Paint.Align.CENTER, true, 2.5f * k, W * .9f); break; }
                case 40: { // Galaxie
                    float gy = H * .45f; int N = 140, on = Math.round(f * N);
                    for (int i = 0; i < N; i++) { float tt = i / (float) N; double a = tt * 9 + (i % 2) * Math.PI; float r = W * (.04f + .36f * tt);
                        x.drawCircle(cx + (float) Math.cos(a) * r, gy + (float) Math.sin(a) * r * .6f, (i < on ? 1.6f : 1.1f) * k, fill(i < on ? a(.5f + .5f * (1 - tt)) : a(.08f))); }
                    x.drawCircle(cx, gy, 3.5f * k, glow(fill(fg), 14));
                    t(fmt(v), cx, H - 22 * k, 20 * k, fg); t(pct + " DE LA GALAXIE", cx, H - 9 * k, 8 * k, mut, Paint.Align.CENTER, true, 1.5f * k, W * .9f); break; }
                case 41: { // Saturne long
                    float p = 18 * k, sx = W - p - H * .55f, R = H * .2f; RectF o = ov(0, 0, R * 2.6f, R * .6f);
                    x.save(); x.translate(sx, cy); x.rotate(-14.3f); x.drawOval(o, stroke(a(.12f), 4 * k)); x.restore();
                    planet(sx, cy, R);
                    x.save(); x.translate(sx, cy); x.rotate(-14.3f); if (f > 0) x.drawArc(o, 180, Math.max(1, f * 360), false, glow(round(stroke(fg, 4 * k)), 6)); x.restore();
                    x.save(); x.clipRect(0, 0, W, cy); planet(sx, cy, R); x.restore();
                    t(fmt(v), p, cy + 6 * k, 28 * k, fg, Paint.Align.LEFT, true, 0, sx - R * 2.8f - p);
                    t(unit + " · " + pct + " · " + sub, p, cy + 22 * k, 10.5f * k, mut, Paint.Align.LEFT, false, 0, sx - R * 2.8f - p); break; }
                case 42: { // Semaine long
                    float p = 18 * k, bx = W * .42f, bw = W - bx - p, cw = bw / 7, top = 14 * k, bot = H - 24 * k; int mx = g; for (int q : w7) mx = Math.max(mx, q);
                    t(fmt(v), p, cy + 4 * k, 28 * k, fg, Paint.Align.LEFT, true, 0, bx - p - 8 * k); t(unit + " aujourd'hui", p, cy + 20 * k, 10 * k, mut, Paint.Align.LEFT, false, 0, bx - p - 8 * k);
                    String days = "LMMJVSD";
                    for (int i = 0; i < 7; i++) { float h = Math.max(3 * k, (bot - top) * w7[i] / mx); rr(bx + i * cw + cw * .25f, bot - h, cw * .5f, h, cw * .25f, fill(i == ti ? fg : i > ti ? a(.07f) : w7[i] >= g ? a(.55f) : a(.25f)));
                        t(days.substring(i, i + 1), bx + i * cw + cw / 2, H - 9 * k, 8.5f * k, i == ti ? fg : mut, Paint.Align.CENTER, true, 0, 0); }
                    break; }
                default: { // Onde (long)
                    float p = 18 * k, lw = W * f;
                    for (int j = 0; j < 2; j++) { float o = j == 0 ? 0 : 2; Path wv = new Path(); wv.moveTo(0, H);
                        for (float px = 0; px <= lw; px += 2) wv.lineTo(px, H * .55f + (float) Math.sin(px / W * 14 + o) * 5 * k); wv.lineTo(lw, H); wv.close(); x.drawPath(wv, fill(a(j == 0 ? .08f : .14f))); }
                    Path ln = new Path(); for (float px = 0; px <= lw; px += 2) { float py = H * .55f + (float) Math.sin(px / W * 14 + 2) * 5 * k; if (px == 0) ln.moveTo(px, py); else ln.lineTo(px, py); }
                    x.drawPath(ln, glow(round(stroke(fg, 2 * k)), 6));
                    t(fmt(v), p, H * .42f, 24 * k, fg, Paint.Align.LEFT, true, 0, W * .7f); t(pct, W - p, H * .42f, 13 * k, fg, Paint.Align.RIGHT, true, 0, 0); }
            }
        }
    }

    static void drawDesign(Art A, Cfg f, int[] v) {
        int id = f.style - 7; Th T = A.T; Dz z = new Dz(); z.T = T;
        boolean kc = f.metric == 1;
        z.kc = kc; z.kv = f.kv; z.kp = f.kp; z.ke = v[2]; z.kg = Math.max(1, v[3]);
        z.v = kc ? v[2] : v[0]; z.g = Math.max(1, kc ? v[3] : v[1]); z.ti = Math.max(0, Math.min(6, v[4]));
        z.w7 = kc ? java.util.Arrays.copyOfRange(v, 12, 19) : java.util.Arrays.copyOfRange(v, 5, 12);
        z.f = Math.max(0, Math.min(1, z.v / (float) z.g)); z.pct = Math.round(z.f * 100) + " %";
        z.lbl = kc ? "KCAL" : "PAS"; z.unit = kc ? "kcal" : "pas";
        z.sub = kc ? "/ " + fmt(z.g) : String.format(Locale.FRANCE, "%.1f km", z.v * 0.00075f);
        z.small = Math.min(A.W, A.H) < 100 * A.dp; z.fg = T.fg; z.mut = T.mut; z.inv = T.light ? Color.WHITE : 0xFF0B0B0F; z.glowC = al(T.fg, .7f);
        Canvas c = A.x; c.save(); c.clipPath(A.shapeP(0));
        if (wideDesign(id)) { z.W = A.W; z.H = A.H; z.k = Math.min(A.H / 84f, A.W / 360f); }
        else { float s = Math.min(A.W, A.H); c.translate((A.W - s) / 2, (A.H - s) / 2); z.W = s; z.H = s; z.k = s / 170f; }
        z.x = c; z.draw(id); c.restore();
    }

    static Bitmap draw(Cfg f, int[] v, int Wi, int Hi, float dp) {
        Bitmap bm = Bitmap.createBitmap(Math.max(8, Wi), Math.max(8, Hi), Bitmap.Config.ARGB_8888);
        Art A = new Art(); A.x = new Canvas(bm); A.f = f; A.d = v; A.W = Wi; A.H = Hi; A.dp = dp; A.T = theme(f.col);
        float W = Wi, H = Hi, u = Math.min(W, H), pad = Math.max(8 * dp, u * .1f); A.u = u; A.pad = pad;
        A.sh = f.bg < 25 && (A.T.bg.equals("glass") || A.T.bg.equals("chrome") || A.T.bg.equals("liquid"));
        A.circle = f.shape == 1 && W < H * 1.3f && H < W * 1.3f; A.rad = A.T.bg.equals("liquid") ? Math.min(u * .2f, 24 * dp) : A.T.bg.equals("glass") ? Math.min(u * .32f, 26 * dp) : Math.min(u * .26f, 30 * dp);
        A.background();
        if (f.style >= 7) { drawDesign(A, f, v); return bm; }
        float pad2 = (A.T.bg.equals("cartoon") || A.T.bg.equals("brutal")) ? pad + 3 * dp : pad; A.pad2 = pad2;
        Th T = A.T;
        M ms = new M(); ms.v = v[0]; ms.g = Math.max(1, v[1]); ms.l = "PAS"; ms.u = "pas"; ms.c0 = T.s0; ms.c1 = T.s1; ms.kd = 's'; ms.w7 = java.util.Arrays.copyOfRange(v, 5, 12);
        boolean both = f.metric == 2, small = u < 95 * dp;
        M mk = new M(); mk.v = both ? v[19] : v[2]; mk.g = Math.max(1, both ? v[20] : v[3]); mk.l = both ? "BRÛLÉES" : "KCAL"; mk.u = "kcal"; mk.c0 = T.k0; mk.c1 = T.k1; mk.kd = 'k'; mk.w7 = both ? java.util.Arrays.copyOfRange(v, 21, 28) : java.util.Arrays.copyOfRange(v, 12, 19);
        float iw = W - pad2 * 2, ih = H - pad2 * 2;
        if (!both) A.one(pad2, pad2, iw, ih, f.metric == 1 ? mk : ms, small);
        else if (f.style == 0 && W < H * 1.45f) {
            float cx = W / 2, cy = H / 2, r0 = u / 2 - pad * .35f, tl = r0 * .08f;
            A.ticks(cx, cy, r0, 60, tl, ms.c0, A.fr(ms));
            float r1 = r0 - tl * 1.7f - r0 * .1f, sw = Math.max(2 * dp, r0 * .07f); A.ring(cx, cy, r1, sw, mk, true);
            float w2 = (r1 - sw) * 1.7f; String t = small ? compact(v[0]) : fmt(v[0]);
            if (small) { float s = A.fit(t, r1 * .6f, w2, true); A.text(t, cx, cy + s * .36f, s, T.fg, Paint.Align.CENTER, true, 0); }
            else { A.icon('s', cx, cy - r1 * .44f, r1 * .28f, ms.c0); float s = A.fit(t, r1 * .44f, w2, true); A.text(t, cx, cy + s * .22f, s, T.fg, Paint.Align.CENTER, true, 0);
                String kt = fmt(v[19]) + " kcal"; float ks = r1 * .14f, tw = A.pt(ks, 0, false, T.ls).measureText(kt) + ks * 1.5f;
                A.icon('k', cx - tw / 2 + ks * .6f, cy + s * .22f + r1 * .3f - ks * .36f, ks * 1.3f, mk.c0); A.text(kt, cx - tw / 2 + ks * 1.5f, cy + s * .22f + r1 * .3f, ks, T.mut, Paint.Align.LEFT, false, 0); }
        } else {
            boolean side = W > H * 1.15f; float g = pad * .7f;
            float aw = side ? (iw - g) / 2 : iw, ah = side ? ih : (ih - g) / 2;
            if (side) A.x.drawLine(pad2 + aw + g / 2, pad2 + ih * .15f, pad2 + aw + g / 2, pad2 + ih * .85f, A.stroke(0x14FFFFFF, Math.max(1, dp)));
            boolean sm = small || (Math.min(aw, ah) < 70 * dp && f.style != 3);
            A.one(pad2, pad2, aw, ah, ms, sm);
            A.one(side ? pad2 + aw + g : pad2, side ? pad2 : pad2 + ah + g, aw, ah, mk, sm);
        }
        return bm;
    }
}
