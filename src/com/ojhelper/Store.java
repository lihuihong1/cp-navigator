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
    private static final int VER = 1;

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
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        // 第一版没有历史迁移；后续版本必须在此按 oldV 逐级 ALTER，禁止 drop
        if (oldV < 1) onCreate(db);
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

    void clearAll() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("fav", null, null);
        db.delete("acc", null, null);
    }
}