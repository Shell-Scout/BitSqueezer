package ru.otlipni.app;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import org.json.JSONArray;

/**
 * Лёгкая фоновая служба: слушает только «экран выключен» и «телефон разблокирован».
 * Сон = от выключения экрана до разблокировки. Глянул на экран блокировки — сон продолжается.
 * Батарею почти не тратит: ничего не считает в фоне, просто ждёт системных событий.
 */
public class WatchService extends Service {
    static final String CH = "status";
    static final int ID = 1;
    private BroadcastReceiver rx;

    static SharedPreferences sp(Context c) { return c.getSharedPreferences("watch", MODE_PRIVATE); }

    static void ensure(Context c) {
        try {
            Intent i = new Intent(c, WatchService.class);
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
        } catch (Exception ignored) { }
    }

    /** Отрезки сна, закончившиеся после since: JSON [[start,end],...] */
    static synchronized String since(Context c, long since) {
        try {
            JSONArray all = new JSONArray(sp(c).getString("list", "[]"));
            JSONArray out = new JSONArray();
            for (int i = 0; i < all.length(); i++) {
                JSONArray r = all.getJSONArray(i);
                if (r.getLong(1) > since) out.put(r);
            }
            return out.toString();
        } catch (Exception e) { return "[]"; }
    }

    static synchronized void add(Context c, long s, long e) {
        try {
            JSONArray all = new JSONArray(sp(c).getString("list", "[]"));
            JSONArray keep = new JSONArray();
            long cut = System.currentTimeMillis() - 8L * 24 * 3600 * 1000;
            for (int i = 0; i < all.length(); i++) {
                JSONArray r = all.getJSONArray(i);
                if (r.getLong(1) > cut) keep.put(r);
            }
            JSONArray n = new JSONArray(); n.put(s); n.put(e);
            keep.put(n);
            sp(c).edit().putString("list", keep.toString()).apply();
        } catch (Exception ignored) { }
    }

    private void close(long now) {
        SharedPreferences s = sp(this);
        long off = s.getLong("offAt", 0);
        if (off <= 0) return;
        s.edit().putLong("offAt", 0).putLong("lastSec", (now - off) / 1000).apply();
        if (now - off >= 1000) add(this, off, now);
        update();
    }

    private void update() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(ID, build(this));
    }

    static Notification build(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm != null) {
            NotificationChannel ch = new NotificationChannel(CH, Tx.chStatus(c), NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        SharedPreferences s = sp(c);
        String name = Checks.sp(c).getString("name", Tx.pet(c));
        long off = s.getLong("offAt", 0);
        Notification.Builder b = new Notification.Builder(c, CH)
                .setSmallIcon(R.drawable.ic_stat)
                .setOngoing(true)
                .setContentIntent(Notif.open(c));
        if (off > 0) {
            b.setContentTitle(Tx.sleeping(c, name)).setContentText(Tx.sleepingText(c))
                    .setWhen(off).setShowWhen(true).setUsesChronometer(true);
        } else {
            long last = s.getLong("lastSec", 0);
            b.setContentTitle(Tx.awake(c, name))
                    .setContentText(Tx.awakeText(c, last))
                    .setShowWhen(false);
        }
        return b.build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification n = build(this);
        try {
            if (Build.VERSION.SDK_INT >= 34) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            else startForeground(ID, n);
        } catch (Exception e) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (rx == null) {
            rx = new BroadcastReceiver() {
                @Override
                public void onReceive(Context c, Intent in) {
                    String a = in.getAction();
                    long now = System.currentTimeMillis();
                    SharedPreferences s = sp(c);
                    if (Intent.ACTION_SCREEN_OFF.equals(a)) {
                        if (s.getLong("offAt", 0) == 0) { s.edit().putLong("offAt", now).apply(); update(); }
                    } else if (Intent.ACTION_USER_PRESENT.equals(a)) {
                        close(now);
                    } else if (Intent.ACTION_SCREEN_ON.equals(a)) {
                        KeyguardManager km = (KeyguardManager) c.getSystemService(KEYGUARD_SERVICE);
                        if (km != null && !km.isKeyguardLocked()) close(now); // телефон совсем без блокировки
                    }
                }
            };
            IntentFilter f = new IntentFilter();
            f.addAction(Intent.ACTION_SCREEN_OFF);
            f.addAction(Intent.ACTION_SCREEN_ON);
            f.addAction(Intent.ACTION_USER_PRESENT);
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(rx, f, Context.RECEIVER_EXPORTED);
            else registerReceiver(rx, f);
        }
        // служба перезапустилась, а телефон уже разблокирован — момент разблокировки неизвестен, такой сон не считаем
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        boolean unlocked = pm != null && pm.isInteractive() && km != null && !km.isKeyguardLocked();
        if (unlocked && sp(this).getLong("offAt", 0) > 0) sp(this).edit().putLong("offAt", 0).apply();
        if (pm != null && !pm.isInteractive() && sp(this).getLong("offAt", 0) == 0)
            sp(this).edit().putLong("offAt", System.currentTimeMillis()).apply();
        update();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (rx != null) { try { unregisterReceiver(rx); } catch (Exception ignored) { } rx = null; }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
