package ru.otlipni.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.util.Calendar;
import java.util.List;

/**
 * Раз в ~3 часа заглядывает в историю блокировок и, если есть что сказать, присылает уведомление.
 * Никаких постоянно работающих служб — батарея не тратится.
 */
public class Checks {
    static SharedPreferences sp(Context c) { return c.getSharedPreferences("checks", Context.MODE_PRIVATE); }

    static void setForeground(Context c, boolean fg) {
        SharedPreferences.Editor ed = sp(c).edit().putBoolean("fg", fg);
        if (fg) ed.putLong("mark", System.currentTimeMillis()); // всё, что было до открытия приложения, уже показано в игре
        ed.apply();
    }

    static void setName(Context c, String name) { sp(c).edit().putString("name", name).apply(); }

    static void schedule(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am == null) return;
        PendingIntent pi = PendingIntent.getBroadcast(c, 10, new Intent(c, AlarmReceiver.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        long every = 3 * AlarmManager.INTERVAL_HOUR;
        am.setInexactRepeating(AlarmManager.RTC, System.currentTimeMillis() + every, every, pi);
        if (sp(c).getLong("mark", 0) == 0) sp(c).edit().putLong("mark", System.currentTimeMillis()).apply();
    }

    static List<long[]> fromWatch(Context c, long since) {
        List<long[]> out = new java.util.ArrayList<>();
        try {
            org.json.JSONArray a = new org.json.JSONArray(WatchService.since(c, since));
            for (int i = 0; i < a.length(); i++) out.add(new long[]{Math.max(since, a.getJSONArray(i).getLong(0)), a.getJSONArray(i).getLong(1)});
            long off = WatchService.sp(c).getLong("offAt", 0);
            if (off > 0) out.add(new long[]{Math.max(since, off), System.currentTimeMillis()});
        } catch (Exception ignored) { }
        return out;
    }

    static void run(Context c) {
        SharedPreferences s = sp(c);
        if (s.getBoolean("fg", false)) return;
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 9 || hour >= 22) return; // ночью не беспокоим
        long now = System.currentTimeMillis();
        long mark = s.getLong("mark", now);
        if (now - mark < 60 * 60 * 1000L) return;

        List<long[]> list = LockScanner.hasAccess(c) ? LockScanner.scan(c, mark) : fromWatch(c, mark);
        long slept = 0, locked = 0;
        for (long[] r : list) {
            long d = (r[1] - r[0]) / 1000;
            locked += d;
            if (d >= 60) slept += Math.min(d, 4 * 3600);
        }
        long screen = Math.max(0, (now - mark) / 1000 - locked);
        String name = s.getString("name", Tx.pet(c));

        if (screen >= 2 * 3600 && screen > slept) {
            Notif.post(c, Notif.ID_CHECK, Tx.screenTitle(c, screen), Tx.screenText(c, name));
            s.edit().putLong("mark", now).apply();
        } else if (slept >= 45 * 60) {
            Notif.post(c, Notif.ID_CHECK, Tx.sleptTitle(c, name, slept), Tx.sleptText(c));
            s.edit().putLong("mark", now).apply();
        }
    }
}
