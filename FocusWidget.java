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
    }

    @Override
    public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, Bundle o) {
        render(c, m, id);
    }

    @Override
    public void onDeleted(Context c, int[] ids) {
        SharedPreferences.Editor e = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        for (int id : ids) for (String k : new String[] { "m_", "s_", "c2_", "b_", "h_" }) e.remove(k + id);
        e.apply();
    }

    /** Réglages d'un widget : données, style, couleurs, fond, forme. */
    static class Cfg {
        int metric, style, bg, shape, col;
        static Cfg load(Context c, int id, int kind) {
            SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            Cfg f = new Cfg();
            f.metric = p.getInt("m_" + id, kind == 0 ? 0 : 2);
            f.style = Math.max(0, Math.min(6, p.getInt("s_" + id, kind == 3 ? 3 : 0)));
            f.col = Math.max(0, Math.min(15, p.getInt("c2_" + id, 3)));
            f.bg = p.getInt("b_" + id, 85);
            f.shape = p.getInt("h_" + id, 0);
            return f;
        }
        void save(Context c, int id) {
            c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("m_" + id, metric).putInt("s_" + id, style)
                .putInt("c2_" + id, col).putInt("b_" + id, bg).putInt("h_" + id, shape).apply();
        }
    }

    static int[] data(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        boolean fresh = today.equals(p.getString("date", ""));
        int[] r = new int[19];
        r[0] = fresh ? p.getInt("steps", 0) : 0; r[1] = Math.max(1, p.getInt("stepGoal", 8000)); r[2] = fresh ? p.getInt("kcal", 0) : 0; r[3] = Math.max(1, p.getInt("kcalGoal", 2000));
        java.util.Calendar cal = java.util.Calendar.getInstance(); r[4] = (cal.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7;
        int[] s7 = parse7(p.getString("s7", "")), k7 = parse7(p.getString("k7", ""));
        java.util.Calendar mon = java.util.Calendar.getInstance(); mon.add(java.util.Calendar.DAY_OF_YEAR, -r[4]);
        if (!p.getString("w7d", "").equals(new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(mon.getTime()))) { s7 = new int[7]; k7 = new int[7]; }
        s7[r[4]] = r[0]; k7[r[4]] = r[2];
        System.arraycopy(s7, 0, r, 5, 7); System.arraycopy(k7, 0, r, 12, 7);
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
    static final String[] THEME_NAMES = { "Blanc", "Couleur", "Dégradé", "Chrome", "Cartoon", "Futuriste", "Rétro", "iOS", "Papier", "Brutal", "Néon blanc", "Ligne", "Terminal", "Trame", "Givré", "Graphite" };
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

    static Bitmap draw(Cfg f, int[] v, int Wi, int Hi, float dp) {
        Bitmap bm = Bitmap.createBitmap(Math.max(8, Wi), Math.max(8, Hi), Bitmap.Config.ARGB_8888);
        Art A = new Art(); A.x = new Canvas(bm); A.f = f; A.d = v; A.W = Wi; A.H = Hi; A.dp = dp; A.T = theme(f.col);
        float W = Wi, H = Hi, u = Math.min(W, H), pad = Math.max(8 * dp, u * .1f); A.u = u; A.pad = pad;
        A.sh = f.bg < 25 && (A.T.bg.equals("glass") || A.T.bg.equals("chrome"));
        A.circle = f.shape == 1 && W < H * 1.3f && H < W * 1.3f; A.rad = A.T.bg.equals("glass") ? Math.min(u * .32f, 26 * dp) : Math.min(u * .26f, 30 * dp);
        A.background();
        float pad2 = (A.T.bg.equals("cartoon") || A.T.bg.equals("brutal")) ? pad + 3 * dp : pad; A.pad2 = pad2;
        Th T = A.T;
        M ms = new M(); ms.v = v[0]; ms.g = Math.max(1, v[1]); ms.l = "PAS"; ms.u = "pas"; ms.c0 = T.s0; ms.c1 = T.s1; ms.kd = 's'; ms.w7 = java.util.Arrays.copyOfRange(v, 5, 12);
        M mk = new M(); mk.v = v[2]; mk.g = Math.max(1, v[3]); mk.l = "KCAL"; mk.u = "kcal"; mk.c0 = T.k0; mk.c1 = T.k1; mk.kd = 'k'; mk.w7 = java.util.Arrays.copyOfRange(v, 12, 19);
        boolean both = f.metric == 2, small = u < 95 * dp;
        float iw = W - pad2 * 2, ih = H - pad2 * 2;
        if (!both) A.one(pad2, pad2, iw, ih, f.metric == 1 ? mk : ms, small);
        else if (f.style == 0 && W < H * 1.45f) {
            float cx = W / 2, cy = H / 2, r0 = u / 2 - pad * .35f, tl = r0 * .08f;
            A.ticks(cx, cy, r0, 60, tl, ms.c0, A.fr(ms));
            float r1 = r0 - tl * 1.7f - r0 * .1f, sw = Math.max(2 * dp, r0 * .07f); A.ring(cx, cy, r1, sw, mk, true);
            float w2 = (r1 - sw) * 1.7f; String t = small ? compact(v[0]) : fmt(v[0]);
            if (small) { float s = A.fit(t, r1 * .6f, w2, true); A.text(t, cx, cy + s * .36f, s, T.fg, Paint.Align.CENTER, true, 0); }
            else { A.icon('s', cx, cy - r1 * .44f, r1 * .28f, ms.c0); float s = A.fit(t, r1 * .44f, w2, true); A.text(t, cx, cy + s * .22f, s, T.fg, Paint.Align.CENTER, true, 0);
                String kt = fmt(v[2]) + " kcal"; float ks = r1 * .14f, tw = A.pt(ks, 0, false, T.ls).measureText(kt) + ks * 1.5f;
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
