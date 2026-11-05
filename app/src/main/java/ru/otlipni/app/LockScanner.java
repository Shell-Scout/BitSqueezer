package ru.otlipni.app;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Process;

import java.util.ArrayList;
import java.util.List;

/**
 * Достаёт из истории использования отрезки, когда экран был заблокирован.
 * Сон начинается при выключении экрана и заканчивается при разблокировке.
 * Если просто глянуть на экран блокировки и не разблокировать — сон продолжается.
 */
public class LockScanner {
    // коды событий из UsageEvents.Event (Android 9+)
    private static final int SCREEN_INTERACTIVE = 15;
    private static final int SCREEN_NON_INTERACTIVE = 16;
    private static final int KEYGUARD_SHOWN = 17;
    private static final int KEYGUARD_HIDDEN = 18;

    static boolean hasAccess(Context c) {
        AppOpsManager ops = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
        if (ops == null) return false;
        int mode = android.os.Build.VERSION.SDK_INT >= 29
                ? ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName())
                : ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    /** Список отрезков [начало, конец] в миллисекундах. Незакрытый отрезок закрывается моментом now. */
    static List<long[]> scan(Context c, long since) {
        List<long[]> out = new ArrayList<>();
        if (!hasAccess(c)) return out;
        UsageStatsManager usm = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) return out;
        long now = System.currentTimeMillis();
        SharedPreferences sp = c.getSharedPreferences("scan", Context.MODE_PRIVATE);
        boolean kgSeen = sp.getBoolean("kgSeen", false);

        UsageEvents ev = usm.queryEvents(since, now);
        UsageEvents.Event e = new UsageEvents.Event();
        long off = -1;
        boolean sawKg = false;
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e);
            int t = e.getEventType();
            long ts = e.getTimeStamp();
            if (t == SCREEN_NON_INTERACTIVE) {
                if (off < 0) off = ts;
            } else if (t == KEYGUARD_SHOWN) {
                sawKg = true;
                if (off < 0) off = ts;
            } else if (t == KEYGUARD_HIDDEN) {
                sawKg = true;
                if (off >= 0) { out.add(new long[]{off, ts}); off = -1; }
            } else if (t == SCREEN_INTERACTIVE) {
                // на телефонах совсем без блокировки событий keyguard нет — тогда сон кончается при включении экрана
                if (!kgSeen && !sawKg && off >= 0) { out.add(new long[]{off, ts}); off = -1; }
            }
        }
        if (off >= 0) out.add(new long[]{off, now});
        if (sawKg && !kgSeen) sp.edit().putBoolean("kgSeen", true).apply();
        return out;
    }

    static String toJson(List<long[]> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('[').append(list.get(i)[0]).append(',').append(list.get(i)[1]).append(']');
        }
        return sb.append(']').toString();
    }
}
