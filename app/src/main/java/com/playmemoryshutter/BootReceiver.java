package com.playmemoryshutter;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "PlayMemoryShutter";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            SharedPreferences prefs = context.getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE);
            boolean autoStart = prefs.getBoolean(MainActivity.KEY_AUTO_START, false);

            if (autoStart) {
                int delaySeconds = prefs.getInt(MainActivity.KEY_DELAY_SECONDS, 10);
                Log.i(TAG, "Boot detected. Auto-start enabled. Scheduling service in " + delaySeconds + " seconds.");

                AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                Intent serviceIntent = new Intent(context, ShutterInterceptService.class);
                // Flag UPDATE_CURRENT required. API 10 does not require IMMUTABLE flags.
                PendingIntent pi = PendingIntent.getService(context, 0, serviceIntent, PendingIntent.FLAG_UPDATE_CURRENT);

                long triggerTime = System.currentTimeMillis() + (delaySeconds * 1000L);
                am.set(AlarmManager.RTC_WAKEUP, triggerTime, pi);
            } else {
                Log.i(TAG, "Boot detected, but auto-start is disabled.");
            }
        }
    }

    public static void cancelScheduledStart(Context context) {
        Log.i(TAG, "Canceling scheduled service start (if any)");
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent serviceIntent = new Intent(context, ShutterInterceptService.class);
        PendingIntent pi = PendingIntent.getService(context, 0, serviceIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        if (am != null) {
            am.cancel(pi);
        }
    }
}
