package com.ojhelper;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/** 轻量偏好：显示方式、参与查询的平台。随应用卸载一起消失。 */
final class Prefs {

    private static final String NAME = "oj_prefs";

    private final SharedPreferences sp;

    Prefs(Context c) {
        sp = c.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    /** true = 按日期分组，false = 按平台分组。 */
    boolean dateMode() {
        return sp.getBoolean("date_mode", false);
    }

    void setDateMode(boolean v) {
        sp.edit().putBoolean("date_mode", v).apply();
    }

    /** 参与查询的平台；默认全选。 */
    Set<String> enabled() {
        Set<String> s = sp.getStringSet("enabled", null);
        if (s == null || s.isEmpty()) {
            s = new HashSet<String>();
            for (String oj : M.ALL_CONTEST) s.add(oj);
        }
        return new HashSet<String>(s);
    }

    void setEnabled(Set<String> s) {
        sp.edit().putStringSet("enabled", new HashSet<String>(s)).apply();
    }
}