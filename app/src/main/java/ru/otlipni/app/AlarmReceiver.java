package ru.otlipni.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        try { Checks.run(c); } catch (Exception ignored) { }
    }
}
