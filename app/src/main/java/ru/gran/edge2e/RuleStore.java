package ru.gran.edge2e;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class RuleStore extends SQLiteOpenHelper {
    private static final String DB = "rules.db";
    private static final int VERSION = 2;
    private final Context context;

    public RuleStore(Context context) {
        this(context.getApplicationContext(), prepareDatabase(context.getApplicationContext()));
    }

    private RuleStore(Context context, String ignored) {
        super(context, DB, null, VERSION);
        this.context = context;
    }

    private static String prepareDatabase(Context context) {
        File target = context.getDatabasePath(DB);
        if (target.exists() && target.length() > 0) return DB;
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (InputStream in = context.getAssets().open("rules.db");
             FileOutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
            out.getFD().sync();
        } catch (Exception ignored) {
            if (target.exists()) target.delete();
        }
        return DB;
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE rules (id TEXT PRIMARY KEY, name TEXT NOT NULL, category TEXT NOT NULL, subtype TEXT, level INTEGER NOT NULL, json TEXT NOT NULL)");
        db.execSQL("CREATE INDEX idx_rules_category_level ON rules(category, level)");
        db.execSQL("CREATE INDEX idx_rules_name ON rules(name COLLATE NOCASE)");
        importSeed(db);
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS rules");
        onCreate(db);
    }

    private void importSeed(SQLiteDatabase db) {
        InputStream stream;
        try { stream = context.getAssets().open("seed_rules.jsonl"); }
        catch (Exception e) { return; }
        db.beginTransaction();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            ContentValues v = new ContentValues();
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                JSONObject o = new JSONObject(line);
                v.clear();
                v.put("id", o.optString("id"));
                v.put("name", o.optString("name"));
                v.put("category", o.optString("category"));
                v.put("subtype", o.optString("subtype"));
                v.put("level", o.optInt("level", 0));
                v.put("json", line);
                db.insertWithOnConflict("rules", null, v, SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful();
        } catch (Exception ignored) {
        } finally {
            db.endTransaction();
        }
    }

    public int count() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM rules", null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    public RuleItem findById(String id) {
        if (id == null || id.isEmpty()) return null;
        try (Cursor c = getReadableDatabase().query("rules", new String[]{"json"}, "id=?", new String[]{id}, null, null, null, "1")) {
            if (c.moveToFirst()) return parse(c.getString(0));
        }
        return null;
    }

    public RuleItem findExact(String category, String name) {
        if (name == null || name.isEmpty()) return null;
        try (Cursor c = getReadableDatabase().query("rules", new String[]{"json"}, "category=? AND name=? COLLATE NOCASE", new String[]{category, name}, null, null, null, "1")) {
            if (c.moveToFirst()) return parse(c.getString(0));
        }
        return null;
    }

    public List<RuleItem> query(String category, int maxLevel, String search, int limit) {
        List<RuleItem> out = new ArrayList<>();
        StringBuilder where = new StringBuilder("level<=?");
        List<String> args = new ArrayList<>();
        args.add(String.valueOf(maxLevel));
        if (category != null && !category.isEmpty() && !"all".equals(category)) {
            where.append(" AND category=?");
            args.add(category);
        }
        if (search != null && !search.trim().isEmpty()) {
            where.append(" AND name LIKE ? COLLATE NOCASE");
            args.add("%" + search.trim() + "%");
        }
        try (Cursor c = getReadableDatabase().query("rules", new String[]{"json"}, where.toString(), args.toArray(new String[0]), null, null, "level ASC, name COLLATE NOCASE ASC", String.valueOf(limit))) {
            while (c.moveToNext()) {
                RuleItem item = parse(c.getString(0));
                if (item != null) out.add(item);
            }
        }
        return out;
    }

    public List<RuleItem> bySubtype(String category, String subtype, int maxLevel, String search, int limit) {
        List<RuleItem> base = query(category, maxLevel, search, Math.max(limit * 3, limit));
        List<RuleItem> out = new ArrayList<>();
        for (RuleItem item : base) {
            if (subtype == null || subtype.isEmpty() || subtype.equalsIgnoreCase(item.subtype)) out.add(item);
            if (out.size() >= limit) break;
        }
        return out;
    }

    private RuleItem parse(String raw) {
        try { return RuleItem.fromJson(new JSONObject(raw)); }
        catch (Exception ignored) { return null; }
    }
}
