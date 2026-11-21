package com.ojhelper;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.provider.CalendarContract;

/** 把比赛写进系统日历。走 ACTION_INSERT，由系统日历弹出编辑页，不需要任何权限。 */
final class Cal {

    private Cal() {}

    static void add(Context c, M.Contest ct) {
        long now = System.currentTimeMillis();
        if (ct.start <= now) {
            // 只允许给"还没开始"的比赛加日历：已开始/已结束的加了也没有提醒意义
            Ui.toast(c, ct.finished(now) ? "比赛已结束，无法添加日历" : "比赛已开始，无法添加日历");
            return;
        }
        Intent i = new Intent(Intent.ACTION_INSERT);
        i.setData(CalendarContract.Events.CONTENT_URI);
        i.putExtra(CalendarContract.Events.TITLE, "[" + M.ojShort(ct.oj) + "] " + ct.title);
        i.putExtra(CalendarContract.Events.DESCRIPTION,
                M.ojName(ct.oj) + " 比赛\n" + ct.url);
        i.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, ct.start);
        i.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, ct.end);
        i.putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, false);
        i.putExtra(CalendarContract.Events.EVENT_TIMEZONE, "Asia/Shanghai");
        i.putExtra(CalendarContract.Events.HAS_ALARM, 1);
        i.putExtra(CalendarContract.Reminders.MINUTES, 15);
        if (!(c instanceof android.app.Activity)) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            c.startActivity(i);
        } catch (ActivityNotFoundException e) {
            Ui.toast(c, "没有找到可用的日历应用");
        } catch (SecurityException e) {
            Ui.toast(c, "没有日历写入权限");
        } catch (Exception e) {
            Ui.toast(c, "打开日历失败：" + e.getClass().getSimpleName());
        }
    }
}