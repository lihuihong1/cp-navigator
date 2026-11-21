package com.ojhelper;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/** 本地存储：收藏 + 各 OJ 统计。全部落在应用私有目录，卸载即清空、不参与云备份。 */
final class Store extends SQLiteOpenHelper {

    private static final String DB = "oj.db";
    private static final int VER = 2;

    private static final String CREATE_REMIND = "CREATE TABLE remind ("
            + "key TEXT PRIMARY KEY,"
            + "oj TEXT NOT NULL,"
            + "sid TEXT NOT NULL,"
            + "title TEXT NOT NULL,"
            + "start INTEGER NOT NULL,"
            + "end INTEGER NOT NULL,"
            + "url TEXT NOT NULL,"
            + "lead INTEGER NOT NULL,"
            + "at INTEGER NOT NULL)";

    static final class Acc {
        String oj = "";
        String handle = "";
        Integer solved;
        Double rating;
        String extra = "";
        long updated;
        String lastError = "";
    }

    Store(Context c) {
        super(c.getApplicationContext(), DB, null, VER);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE fav ("
                + "key TEXT PRIMARY KEY,"
                + "oj TEXT NOT NULL,"
                + "sid TEXT NOT NULL,"
                + "title TEXT NOT NULL,"
                + "start INTEGER NOT NULL,"
                + "end INTEGER NOT NULL,"
                + "url TEXT NOT NULL,"
                + "created INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE acc ("
                + "oj TEXT PRIMARY KEY,"
                + "handle TEXT NOT NULL DEFAULT '',"
                + "solved INTEGER,"          // NULL = 不支持/无数据
                + "rating REAL,"             // NULL = 无排位分
                + "extra TEXT NOT NULL DEFAULT '',"
                + "updated INTEGER NOT NULL DEFAULT 0,"
                + "last_error TEXT NOT NULL DEFAULT '')");
        db.execSQL(CREATE_REMIND);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        // 按 oldV 逐级迁移，禁止 drop（drop 会让用户升级后丢收藏与成绩）
        if (oldV < 1) {
            onCreate(db);
            return;
        }
        if (oldV < 2) db.execSQL(CREATE_REMIND);
    }

    // ---------------- 收藏 ----------------

    void addFav(M.Contest c) {
        ContentValues v = new ContentValues();
        v.put("key", c.key());
        v.put("oj", c.oj);
        v.put("sid", c.sid);
        v.put("title", c.title);
        v.put("start", c.start);
        v.put("end", c.end);
        v.put("url", c.url);
        v.put("created", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("fav", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    void removeFav(String key) {
        getWritableDatabase().delete("fav", "key=?", new String[]{key});
    }

    boolean isFav(String key) {
        Cursor c = getReadableDatabase().rawQuery("SELECT 1 FROM fav WHERE key=? LIMIT 1", new String[]{key});
        try {
            return c.moveToFirst();
        } finally {
            c.close();
        }
    }

    List<M.Contest> favorites() {
        List<M.Contest> out = new ArrayList<M.Contest>();
        Cursor c = getReadableDatabase().rawQuery("SELECT oj,sid,title,start,end,url FROM fav", null);
        try {
            while (c.moveToNext()) {
                M.Contest x = new M.Contest();
                x.oj = c.getString(0);
                x.sid = c.getString(1);
                x.title = c.getString(2);
                x.start = c.getLong(3);
                x.end = c.getLong(4);
                x.url = c.getString(5);
                out.add(x);
            }
        } finally {
            c.close();
        }
        return M.sortByStart(out);
    }

    int favCount() {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM fav", null);
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    // ---------------- 统计缓存 ----------------

    Acc loadAcc(String oj) {
        Acc a = new Acc();
        a.oj = oj;
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT handle,solved,rating,extra,updated,last_error FROM acc WHERE oj=?", new String[]{oj});
        try {
            if (c.moveToFirst()) {
                a.handle = c.isNull(0) ? "" : c.getString(0);
                a.solved = c.isNull(1) ? null : Integer.valueOf(c.getInt(1));
                a.rating = c.isNull(2) ? null : Double.valueOf(c.getDouble(2));
                a.extra = c.isNull(3) ? "" : c.getString(3);
                a.updated = c.getLong(4);
                a.lastError = c.isNull(5) ? "" : c.getString(5);
            }
        } finally {
            c.close();
        }
        return a;
    }

    void saveAcc(Acc a) {
        ContentValues v = new ContentValues();
        v.put("oj", a.oj);
        v.put("handle", a.handle == null ? "" : a.handle);
        if (a.solved == null) v.putNull("solved"); else v.put("solved", a.solved.intValue());
        if (a.rating == null) v.putNull("rating"); else v.put("rating", a.rating.doubleValue());
        v.put("extra", a.extra == null ? "" : a.extra);
        v.put("updated", a.updated);
        v.put("last_error", a.lastError == null ? "" : a.lastError);
        getWritableDatabase().insertWithOnConflict("acc", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    // ---------------- 开赛提醒 ----------------

    static final class Rem {
        final M.Contest c = new M.Contest();
        int lead;
        long at;
    }

    void putRemind(M.Contest c, int lead, long at) {
        ContentValues v = new ContentValues();
        v.put("key", c.key());
        v.put("oj", c.oj);
        v.put("sid", c.sid);
        v.put("title", c.title);
        v.put("start", c.start);
        v.put("end", c.end);
        v.put("url", c.url);
        v.put("lead", lead);
        v.put("at", at);
        getWritableDatabase().insertWithOnConflict("remind", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    void deleteRemind(String key) {
        getWritableDatabase().delete("remind", "key=?", new String[]{key});
    }

    /** 已设置的提前分钟数；没有返回 -1。 */
    int remindLead(String key) {
        Cursor c = getReadableDatabase().rawQuery("SELECT lead FROM remind WHERE key=?", new String[]{key});
        try {
            return c.moveToFirst() ? c.getInt(0) : -1;
        } finally {
            c.close();
        }
    }

    List<Rem> reminds() {
        List<Rem> out = new ArrayList<Rem>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT oj,sid,title,start,end,url,lead,at FROM remind ORDER BY at", null);
        try {
            while (c.moveToNext()) {
                Rem r = new Rem();
                r.c.oj = c.getString(0);
                r.c.sid = c.getString(1);
                r.c.title = c.getString(2);
                r.c.start = c.getLong(3);
                r.c.end = c.getLong(4);
                r.c.url = c.getString(5);
                r.lead = c.getInt(6);
                r.at = c.getLong(7);
                out.add(r);
            }
        } finally {
            c.close();
        }
        return out;
    }

    void clearAll() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("fav", null, null);
        db.delete("acc", null, null);
        db.delete("remind", null, null);
    }
}