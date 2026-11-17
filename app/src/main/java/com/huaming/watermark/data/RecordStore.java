package com.huaming.watermark.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class RecordStore extends SQLiteOpenHelper {

    private static final String DB = "records.db";
    private static final int VERSION = 1;
    private static final String T = "records";

    public static class Record {
        public long id;
        public String uri;
        public String path;
        public long time;
        public String title;
        public String location;
        public String remark;
    }

    public RecordStore(Context ctx) {
        super(ctx.getApplicationContext(), DB, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + T + " ("
                + "_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "uri TEXT,"
                + "path TEXT,"
                + "time INTEGER,"
                + "title TEXT,"
                + "location TEXT,"
                + "remark TEXT)");
        db.execSQL("CREATE INDEX idx_time ON " + T + "(time DESC)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    public long insert(Record r) {
        ContentValues cv = new ContentValues();
        cv.put("uri", r.uri);
        cv.put("path", r.path);
        cv.put("time", r.time);
        cv.put("title", r.title);
        cv.put("location", r.location);
        cv.put("remark", r.remark);
        long id = getWritableDatabase().insert(T, null, cv);
        r.id = id;
        return id;
    }

    public List<Record> list(int limit) {
        List<Record> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query(T, null, null, null, null, null,
                "time DESC", limit > 0 ? String.valueOf(limit) : null);
        try {
            while (c.moveToNext()) {
                Record r = new Record();
                r.id = c.getLong(c.getColumnIndexOrThrow("_id"));
                r.uri = c.getString(c.getColumnIndexOrThrow("uri"));
                r.path = c.getString(c.getColumnIndexOrThrow("path"));
                r.time = c.getLong(c.getColumnIndexOrThrow("time"));
                r.title = c.getString(c.getColumnIndexOrThrow("title"));
                r.location = c.getString(c.getColumnIndexOrThrow("location"));
                r.remark = c.getString(c.getColumnIndexOrThrow("remark"));
                out.add(r);
            }
        } finally {
            c.close();
        }
        return out;
    }

    public Record byId(long id) {
        Cursor c = getReadableDatabase().query(T, null, "_id=?",
                new String[]{String.valueOf(id)}, null, null, null, "1");
        try {
            if (c.moveToFirst()) {
                Record r = new Record();
                r.id = c.getLong(c.getColumnIndexOrThrow("_id"));
                r.uri = c.getString(c.getColumnIndexOrThrow("uri"));
                r.path = c.getString(c.getColumnIndexOrThrow("path"));
                r.time = c.getLong(c.getColumnIndexOrThrow("time"));
                r.title = c.getString(c.getColumnIndexOrThrow("title"));
                r.location = c.getString(c.getColumnIndexOrThrow("location"));
                r.remark = c.getString(c.getColumnIndexOrThrow("remark"));
                return r;
            }
        } finally {
            c.close();
        }
        return null;
    }

    public void delete(long id) {
        getWritableDatabase().delete(T, "_id=?", new String[]{String.valueOf(id)});
    }

    public void updateLocation(long id, String location) {
        ContentValues cv = new ContentValues();
        cv.put("location", location);
        getWritableDatabase().update(T, cv, "_id=?", new String[]{String.valueOf(id)});
    }
}
