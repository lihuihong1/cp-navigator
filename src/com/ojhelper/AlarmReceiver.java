package com.ojhelper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 闹钟到点：发通知，并把这条提醒从库里删掉。 */
public class AlarmReceiver extends BroadcastReceiver {

    static final String ACTION = "com.ojhelper.ALARM_REMIND";

    @Override
    public void onReceive(Context c, Intent i) {
        if (c == null || i == null) return;
        String key = i.getStringExtra("key");
        if (key == null) return;
        String oj = i.getStringExtra("oj");
        String title = i.getStringExtra("title");
        String url = i.getStringExtra("url");
        long start = i.getLongExtra("start", 0L);
        long end = i.getLongExtra("end", 0L);
        int lead = i.getIntExtra("lead", 0);
        Notif.show(c, key, oj == null ? "" : oj, title == null ? "" : title,
                start, end, url, lead);
        try {
            Store s = new Store(c);
            s.deleteRemind(key);
            s.close();
        } catch (Exception ignored) {
            // 删不掉也无所谓：通知已经发出，下次 resync 会清
        }
    }
}