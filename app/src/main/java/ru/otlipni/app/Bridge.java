package ru.otlipni.app;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Base64;
import android.webkit.JavascriptInterface;

import java.io.OutputStream;

/** Мост: эти методы доступны из JavaScript как window.Native.* */
public class Bridge {
    private final Activity a;
    private boolean askedNotif = false;

    Bridge(Activity a) { this.a = a; }

    @JavascriptInterface
    public boolean hasUsageAccess() { return LockScanner.hasAccess(a); }

    @JavascriptInterface
    public void startWatch() {
        Checks.sp(a).edit().putBoolean("onboarded", true).apply();
        WatchService.ensure(a);
    }

    @JavascriptInterface
    public String takeIntervals(double since) { return WatchService.since(a, (long) since); }

    @JavascriptInterface
    public void openAppSettings() {
        a.runOnUiThread(() -> {
            try {
                Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                i.setData(Uri.parse("package:" + a.getPackageName()));
                a.startActivity(i);
            } catch (Exception ignored) { }
        });
    }

    @JavascriptInterface
    public void openUsageAccess() {
        a.runOnUiThread(() -> {
            try {
                Intent i = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
                i.setData(Uri.parse("package:" + a.getPackageName()));
                a.startActivity(i);
            } catch (Exception e) {
                try { a.startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)); } catch (Exception ignored) { }
            }
        });
    }

    @JavascriptInterface
    public String scanLocked(double since) {
        return LockScanner.toJson(LockScanner.scan(a, (long) since));
    }

    @JavascriptInterface
    public void setPetName(String name) {
        String old = Checks.sp(a).getString("name", "");
        Checks.setName(a, name);
        if (!name.equals(old)) WatchService.ensure(a); // обновить текст в шторке
    }

    @JavascriptInterface
    public void setLang(String lang) {
        String old = Checks.sp(a).getString("lang", "");
        Checks.sp(a).edit().putString("lang", lang).apply();
        if (!lang.equals(old) && Checks.sp(a).getBoolean("onboarded", false)) WatchService.ensure(a);
    }

    @JavascriptInterface
    public void scheduleChecks() { Checks.schedule(a); }

    @JavascriptInterface
    public void vibrate(int ms) {
        try {
            Vibrator v = (Vibrator) a.getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator())
                v.vibrate(VibrationEffect.createOneShot(Math.max(1, Math.min(ms, 1000)), VibrationEffect.DEFAULT_AMPLITUDE));
        } catch (Exception ignored) { }
    }

    @JavascriptInterface
    public void requestNotifications() {
        if (askedNotif) return;
        askedNotif = true;
        if (Build.VERSION.SDK_INT >= 33
                && a.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            a.runOnUiThread(() -> a.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1));
        }
    }

    @JavascriptInterface
    public void shareText(String text) {
        a.runOnUiThread(() -> {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, text);
            a.startActivity(Intent.createChooser(i, Tx.share(a)));
        });
    }

    @JavascriptInterface
    public void shareImage(String base64, String text) {
        if (Build.VERSION.SDK_INT < 29) { shareText(text); return; }
        try {
            byte[] data = Base64.decode(base64, Base64.DEFAULT);
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Images.Media.DISPLAY_NAME, "otlipni_" + System.currentTimeMillis() + ".png");
            cv.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            cv.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Otlipni");
            Uri uri = a.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
            if (uri == null) { shareText(text); return; }
            try (OutputStream os = a.getContentResolver().openOutputStream(uri)) {
                if (os != null) os.write(data);
            }
            a.runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("image/png");
                i.putExtra(Intent.EXTRA_STREAM, uri);
                i.putExtra(Intent.EXTRA_TEXT, text);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                a.startActivity(Intent.createChooser(i, Tx.share(a)));
            });
        } catch (Exception e) {
            shareText(text);
        }
    }
}
