package com.ojhelper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 重启或应用升级后，系统会清掉已排的闹钟，需要重新排一遍。 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(final Context c, Intent i) {
        if (c == null || i == null) return;
        String a = i.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(a)
                && !"android.intent.action.MY_PACKAGE_REPLACED".equals(a)
                && !Intent.ACTION_TIME_CHANGED.equals(a)) {
            return;
        }
        final Context app = c.getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                Store s = null;
                try {
                    s = new Store(app);
                    Reminder.resync(app, s);
                } catch (Exception ignored) {
                } finally {
                    if (s != null) {
                        try {
                            s.close();
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        }).start();
    }
}