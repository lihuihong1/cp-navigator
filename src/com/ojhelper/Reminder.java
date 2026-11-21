package com.ojhelper;

import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import java.util.List;

/**
 * 开赛提醒（方案 B）：先要权限，再排系统闹钟，到点由 AlarmReceiver 发通知。
 *
 * 权限顺序：
 *   1. Android 12+ 「闹钟和提醒」→ 不授权时退化成近似时间（可能晚几分钟）
 *   2. Android 13+ 通知权限 → 不授权就不排，因为排了也看不见
 */
final class Reminder {

    static final int REQ_NOTIF = 7001;

    /** 可选的提前量（分钟）。 */
    static final int[] LEADS = {0, 5, 10, 15, 30, 60};

    /** 等通知权限弹窗回来后再继续。 */
    private static Runnable afterNotif;

    /** 等从「闹钟和提醒」设置页回来后继续。 */
    private static M.Contest pendingCt;
    private static int pendingLead = -1;

    private Reminder() {
    }

    // ---------------- 权限 ----------------

    static boolean notifAllowed(Context c) {
        if (Build.VERSION.SDK_INT < 33) return true;
        return c.checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                == PackageManager.PERMISSION_GRANTED;
    }

    static boolean canExact(Context c) {
        if (Build.VERSION.SDK_INT < 31) return true;
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        return am != null && am.canScheduleExactAlarms();
    }

    static void openExactSettings(Context c) {
        if (Build.VERSION.SDK_INT < 31) return;
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:" + c.getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        } catch (Exception e) {
            Ui.toast(c, "请到系统设置里允许「闹钟和提醒」");
        }
    }

    // ---------------- 交互 ----------------

    /** 点铃铛：选提前量 / 关闭。 */
    static void pick(final MainActivity act, final Store store, final M.Contest ct,
                     final Runnable after) {
        long now = System.currentTimeMillis();
        if (ct.start <= now) {
            Ui.toast(act, ct.finished(now) ? "比赛已结束，无法设置提醒" : "比赛已开始，无法设置提醒");
            return;
        }
        final int cur = store.remindLead(ct.key());
        final int n = LEADS.length;
        final String[] items = new String[cur >= 0 ? n + 1 : n];
        int k = 0;
        if (cur >= 0) items[k++] = "关闭提醒";
        for (int i = 0; i < n; i++) {
            String t = LEADS[i] == 0 ? "比赛开始时" : "提前 " + LEADS[i] + " 分钟";
            items[k++] = LEADS[i] == cur ? "✓ " + t : t;
        }
        new AlertDialog.Builder(act)
                .setTitle("开赛提醒")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (cur >= 0 && which == 0) {
                            cancel(act, store, ct);
                            Ui.toast(act, "已关闭提醒");
                            if (after != null) after.run();
                            return;
                        }
                        set(act, store, ct, LEADS[cur >= 0 ? which - 1 : which], after);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 设置提醒（先检查权限）。 */
    static void set(final MainActivity act, final Store store, final M.Contest ct,
                    final int lead, final Runnable after) {
        long now = System.currentTimeMillis();
        if (ct.start <= now) {
            Ui.toast(act, ct.finished(now) ? "比赛已结束，无法设置提醒" : "比赛已开始，无法设置提醒");
            return;
        }
        final long at = ct.start - lead * 60000L;
        if (at <= now + 1000L) {
            Ui.toast(act, "已经过了提醒时间，可改选「比赛开始时」或更短的提前量");
            return;
        }
        if (!canExact(act)) {
            pendingCt = ct;
            pendingLead = lead;
            new AlertDialog.Builder(act)
                    .setTitle("需要「闹钟和提醒」权限")
                    .setMessage("Android 12 起，准时提醒要在系统设置里单独授权。\n\n"
                            + "「去设置」→ 找到「闹钟和提醒」→ 允许本应用，回来即可准点响。\n"
                            + "不授权也能设置，但可能晚几分钟。")
                    .setNegativeButton("用近似时间", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) {
                            pendingCt = null;
                            pendingLead = -1;
                            askNotif(act, store, ct, lead, after);
                        }
                    })
                    .setPositiveButton("去设置", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) {
                            openExactSettings(act);
                        }
                    })
                    .show();
            return;
        }
        askNotif(act, store, ct, lead, after);
    }

    private static void askNotif(final MainActivity act, final Store store, final M.Contest ct,
                                 final int lead, final Runnable after) {
        if (Build.VERSION.SDK_INT >= 33 && !notifAllowed(act)) {
            afterNotif = new Runnable() {
                @Override
                public void run() {
                    commit(act, store, ct, lead, after);
                }
            };
            act.requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, REQ_NOTIF);
            return;
        }
        commit(act, store, ct, lead, after);
    }

    /** MainActivity 转发通知权限结果。 */
    static void onNotifResult(MainActivity act, boolean granted) {
        Runnable r = afterNotif;
        afterNotif = null;
        if (!granted) {
            Ui.toast(act, "没有通知权限，提醒不会出现");
            return;
        }
        if (r != null) r.run();
    }

    /** MainActivity 每次 onResume 调用：从「闹钟和提醒」设置页回来时续上。 */
    static void onResume(MainActivity act, Store store) {
        M.Contest ct = pendingCt;
        int lead = pendingLead;
        pendingCt = null;
        pendingLead = -1;
        if (ct == null) return;
        if (canExact(act)) {
            set(act, store, ct, lead, null);
        } else {
            Ui.toast(act, "未授予「闹钟和提醒」，可再点一次铃铛用近似时间");
        }
    }

    private static void commit(MainActivity act, Store store, M.Contest ct, int lead,
                               Runnable after) {
        long at = ct.start - lead * 60000L;
        boolean exact = canExact(act);
        if (!schedule(act, ct, lead, at, exact)) {
            Ui.toast(act, "设置失败，请稍后重试");
            return;
        }
        store.putRemind(ct, lead, at);
        String when = lead == 0 ? "开赛时" : "提前 " + lead + " 分钟";
        Ui.toast(act, when + (exact ? "提醒已设置" : "提醒已设置（近似时间）"));
        if (after != null) after.run();
    }

    // ---------------- 闹钟排程 ----------------

    static void cancel(Context c, Store store, M.Contest ct) {
        store.deleteRemind(ct.key());
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null) {
            PendingIntent pi = pi(c, ct, PendingIntent.FLAG_NO_CREATE);
            if (pi != null) {
                am.cancel(pi);
                pi.cancel();
            }
        }
    }

    static void cancelAll(Context c, Store store) {
        List<Store.Rem> list = store.reminds();
        for (int i = 0; i < list.size(); i++) {
            Store.Rem r = list.get(i);
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am != null) {
                PendingIntent pi = pi(c, r.c, PendingIntent.FLAG_NO_CREATE);
                if (pi != null) {
                    am.cancel(pi);
                    pi.cancel();
                }
            }
            store.deleteRemind(r.c.key());
        }
    }

    /** 应用启动 / 开机后重新排一遍（系统升级或清缓存会丢掉已排的闹钟）。 */
    static void resync(Context c, Store store) {
        long now = System.currentTimeMillis();
        List<Store.Rem> list = store.reminds();
        boolean exact = canExact(c);
        for (int i = 0; i < list.size(); i++) {
            Store.Rem r = list.get(i);
            if (r.at <= now) {
                store.deleteRemind(r.c.key());
            } else {
                schedule(c, r.c, r.lead, r.at, exact);
            }
        }
    }

    private static boolean schedule(Context c, M.Contest ct, int lead, long at, boolean exact) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return false;
        PendingIntent pi = pi(c, ct, PendingIntent.FLAG_UPDATE_CURRENT);
        try {
            if (exact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            }
            return true;
        } catch (SecurityException e) {
            try {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
                return true;
            } catch (Exception e2) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private static PendingIntent pi(Context c, M.Contest ct, int flags) {
        Intent i = new Intent(c, AlarmReceiver.class);
        i.setAction(AlarmReceiver.ACTION);
        i.putExtra("key", ct.key());
        i.putExtra("oj", ct.oj);
        i.putExtra("title", ct.title);
        i.putExtra("start", ct.start);
        i.putExtra("end", ct.end);
        i.putExtra("url", ct.url);
        int f = flags | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        return PendingIntent.getBroadcast(c, ct.key().hashCode(), i, f);
    }
}