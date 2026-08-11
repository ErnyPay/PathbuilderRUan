package ru.gran.edge2e;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
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
    private static final int VERSION = 5;
    private final Context context;

    public RuleStore(Context context) {
        this(context.getApplicationContext(), prepareDatabase(context.getApplicationContext()));
    }

    private RuleStore(Context context, String ignored) {
        super(context, DB, null, VERSION);
        this.context = context;
        // Every rule-driven screen is Russian-first. Loading here guarantees that
        // catalogs, BUILD, PLAY and detail dialogs all share the complete generated
        // ru_names/ru_text corpus instead of falling back to the tiny core map.
        RuNames.init(context);
    }

    private static String prepareDatabase(Context context) {
        File target = context.getDatabasePath(DB);
        if (target.exists() && target.length() > 0) {
            SQLiteDatabase existing = null;
            try {
                existing = SQLiteDatabase.openDatabase(target.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
                if (existing.getVersion() == VERSION) return DB;
            } catch (Exception ignored) {
            } finally {
                if (existing != null) existing.close();
            }
            target.delete();
        }
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
        db.execSQL("CREATE TABLE rules (id TEXT PRIMARY KEY, name TEXT NOT NULL, category TEXT NOT NULL, subtype TEXT, level INTEGER NOT NULL, group_key TEXT, rarity TEXT, remaster INTEGER NOT NULL DEFAULT 0, source TEXT, json TEXT NOT NULL)");
        db.execSQL("CREATE INDEX idx_rules_category_level ON rules(category, level)");
        db.execSQL("CREATE INDEX idx_rules_subtype_level ON rules(subtype, level)");
        db.execSQL("CREATE INDEX idx_rules_group_level ON rules(group_key, level)");
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
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                JSONObject o = new JSONObject(line);
                insertRule(db, o);
            }
            db.setTransactionSuccessful();
        } catch (Exception ignored) {
        } finally {
            db.endTransaction();
        }
    }

    private void insertRule(SQLiteDatabase db, JSONObject o) {
        ContentValues values = new ContentValues();
        values.put("id", o.optString("id"));
        values.put("name", o.optString("name"));
        values.put("category", o.optString("category"));
        values.put("subtype", o.optString("subtype"));
        values.put("level", o.optInt("level", 0));
        values.put("group_key", o.optString("group"));
        values.put("rarity", o.optString("rarity"));
        values.put("remaster", o.optBoolean("remaster", false) ? 1 : 0);
        values.put("source", o.optString("source"));
        values.put("json", o.toString());
        db.insertWithOnConflict("rules", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public List<RuleItem> query(String category, int maxLevel, String text, int limit) {
        ArrayList<RuleItem> out = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        String q = text == null ? "" : text.trim();
        StringBuilder where = new StringBuilder("level<=?");
        ArrayList<String> args = new ArrayList<>();
        args.add(String.valueOf(Math.max(0, maxLevel)));
        if (category != null && !category.trim().isEmpty() && !"all".equalsIgnoreCase(category)) {
            where.append(" AND category=?");
            args.add(category.trim());
        }
        if (!q.isEmpty()) {
            where.append(" AND name LIKE ?");
            args.add("%" + q + "%");
        }
        try (Cursor c = db.query("rules", new String[]{"json"}, where.toString(), args.toArray(new String[0]), null, null, "level ASC,name COLLATE NOCASE ASC", String.valueOf(Math.max(1, limit)))) {
            while (c.moveToNext()) out.add(RuleItem.fromJson(new JSONObject(c.getString(0))));
        } catch (Exception ignored) { }
        return out;
    }

    public List<RuleItem> bySubtype(String category, String subtype, int maxLevel, String text, int limit) {
        ArrayList<RuleItem> out = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        String q = text == null ? "" : text.trim();
        StringBuilder where = new StringBuilder("category=? AND subtype=? AND level<=?");
        ArrayList<String> args = new ArrayList<>();
        args.add(category); args.add(subtype); args.add(String.valueOf(Math.max(0, maxLevel)));
        if (!q.isEmpty()) { where.append(" AND name LIKE ?"); args.add("%" + q + "%"); }
        try (Cursor c = db.query("rules", new String[]{"json"}, where.toString(), args.toArray(new String[0]), null, null, "level ASC,name COLLATE NOCASE ASC", String.valueOf(Math.max(1, limit)))) {
            while (c.moveToNext()) out.add(RuleItem.fromJson(new JSONObject(c.getString(0))));
        } catch (Exception ignored) { }
        return out;
    }

    public List<RuleItem> queryGroup(String category, String subtype, String group, int maxLevel, String text, int limit) {
        ArrayList<RuleItem> out = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        String q = text == null ? "" : text.trim();
        StringBuilder where = new StringBuilder("category=? AND subtype=? AND level<=?");
        ArrayList<String> args = new ArrayList<>();
        args.add(category); args.add(subtype); args.add(String.valueOf(Math.max(0, maxLevel)));
        if (group != null && !group.trim().isEmpty()) { where.append(" AND group_key=?"); args.add(group.trim()); }
        if (!q.isEmpty()) { where.append(" AND name LIKE ?"); args.add("%" + q + "%"); }
        try (Cursor c = db.query("rules", new String[]{"json"}, where.toString(), args.toArray(new String[0]), null, null, "level ASC,name COLLATE NOCASE ASC", String.valueOf(Math.max(1, limit)))) {
            while (c.moveToNext()) out.add(RuleItem.fromJson(new JSONObject(c.getString(0))));
        } catch (Exception ignored) { }
        return out;
    }

    public RuleItem findById(String id) {
        if (id == null || id.isEmpty()) return null;
        try (Cursor c = getReadableDatabase().query("rules", new String[]{"json"}, "id=?", new String[]{id}, null, null, null, "1")) {
            if (c.moveToFirst()) return RuleItem.fromJson(new JSONObject(c.getString(0)));
        } catch (Exception ignored) { }
        return null;
    }

    public RuleItem findExact(String category, String name) {
        if (name == null || name.isEmpty()) return null;
        String where = category == null || category.isEmpty() ? "name=? COLLATE NOCASE" : "category=? AND name=? COLLATE NOCASE";
        String[] args = category == null || category.isEmpty() ? new String[]{name} : new String[]{category, name};
        try (Cursor c = getReadableDatabase().query("rules", new String[]{"json"}, where, args, null, null, null, "1")) {
            if (c.moveToFirst()) return RuleItem.fromJson(new JSONObject(c.getString(0)));
        } catch (Exception ignored) { }
        return null;
    }

    public RuleItem findAnyExact(String name) { return findExact(null, name); }

    public RuleItem findFromUuid(String uuid) {
        if (uuid == null || uuid.isEmpty()) return null;
        int dot = uuid.lastIndexOf('.');
        String tail = dot >= 0 ? uuid.substring(dot + 1) : uuid;
        RuleItem byId = findById(tail);
        if (byId != null) return byId;
        return findAnyExact(tail.replace('-', ' '));
    }

    public int countCategory(String category) {
        if (category == null || category.isEmpty()) return 0;
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM rules WHERE category=?", new String[]{category})) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } catch (Exception ignored) { return 0; }
    }

    public int countAll() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM rules", null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } catch (Exception ignored) { return 0; }
    }
}
