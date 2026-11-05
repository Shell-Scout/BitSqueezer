package ru.otlipni.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** После перезагрузки телефона снова включаем периодические проверки. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())) {
            Checks.schedule(c);
            if (Checks.sp(c).getBoolean("onboarded", false)) WatchService.ensure(c);
        }
    }
}
