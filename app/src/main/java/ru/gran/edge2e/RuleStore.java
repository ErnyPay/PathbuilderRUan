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
        try (BufferedReader r = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            ContentValues v = new ContentValues();
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                JSONObject o = new JSONObject(line);
                JSONObject meta = o.optJSONObject("meta");
                v.clear();
                v.put("id", o.optString("id"));
                v.put("name", o.optString("name"));
                v.put("category", o.optString("category"));
                v.put("subtype", o.optString("subtype"));
                v.put("level", o.optInt("level", 0));
                v.put("group_key", meta == null ? "" : meta.optString("groupKey", ""));
                v.put("rarity", meta == null ? "common" : meta.optString("rarity", "common"));
                v.put("remaster", meta != null && meta.optBoolean("remaster", false) ? 1 : 0);
                v.put("source", o.optString("source", ""));
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

    public int countCategory(String category) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM rules WHERE category=?", new String[]{category})) {
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

    /** Finds a named rules object when a GrantItem UUID does not carry a usable local id. */
    public RuleItem findAnyExact(String name) {
        if (name == null || name.isEmpty()) return null;
        String order = "CASE category WHEN 'class-feature' THEN 0 WHEN 'feat' THEN 1 WHEN 'action' THEN 2 WHEN 'spell' THEN 3 WHEN 'condition' THEN 4 ELSE 5 END, level ASC";
        try (Cursor c = getReadableDatabase().query("rules", new String[]{"json"}, "name=? COLLATE NOCASE", new String[]{name}, null, null, order, "1")) {
            if (c.moveToFirst()) return parse(c.getString(0));
        }
        return null;
    }

    /** Resolve common Foundry UUID forms used by GrantItem and class/background feature lists. */
    public RuleItem findFromUuid(String uuid) {
        if (uuid == null || uuid.isEmpty()) return null;
        String value = uuid.trim();
        int marker = value.lastIndexOf(".Item.");
        String token = marker >= 0 ? value.substring(marker + 6) : value;
        token = token.replace("%20", " ");
        RuleItem byId = findById(token);
        if (byId != null) return byId;
        RuleItem byName = findAnyExact(token);
        if (byName != null) return byName;
        // A small number of source UUIDs end with a slug rather than an id/name.
        String wanted = token.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        try (Cursor c = getReadableDatabase().rawQuery("SELECT json FROM rules WHERE lower(replace(name,' ','-'))=? LIMIT 1", new String[]{wanted})) {
            if (c.moveToFirst()) return parse(c.getString(0));
        }
        return null;
    }

    public List<RuleItem> query(String category, int maxLevel, String search, int limit) {
        List<RuleItem> out = new ArrayList<>();
        CharacterState character = CharacterState.load(context);
        boolean heritageContext = "heritage".equals(category);
        boolean spellContext = "spell".equals(category);
        String spellTradition = fixedTraditionFor(character.className);
        int rawLimit = (heritageContext || (spellContext && spellTradition != null)) ? Math.max(limit * 6, 1200) : limit;

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
        try (Cursor c = getReadableDatabase().query("rules", new String[]{"json"}, where.toString(), args.toArray(new String[0]), null, null, "level ASC, name COLLATE NOCASE ASC", String.valueOf(rawLimit))) {
            while (c.moveToNext()) {
                RuleItem item = parse(c.getString(0));
                if (item == null) continue;
                if (heritageContext && !heritageAllowed(item, character)) continue;
                if (spellContext && spellTradition != null && !spellHasTradition(item, spellTradition)) continue;
                out.add(item);
                if (out.size() >= limit) break;
            }
        }
        return out;
    }

    public List<RuleItem> queryGroup(String category, String subtype, String groupKey, int maxLevel, String search, int limit) {
        List<RuleItem> out = new ArrayList<>();
        StringBuilder where = new StringBuilder("level<=?");
        List<String> args = new ArrayList<>();
        args.add(String.valueOf(maxLevel));
        if (category != null && !category.isEmpty()) { where.append(" AND category=?"); args.add(category); }
        if (subtype != null && !subtype.isEmpty()) { where.append(" AND subtype=?"); args.add(subtype); }
        if (groupKey != null && !groupKey.isEmpty()) { where.append(" AND group_key=?"); args.add(groupKey); }
        if (search != null && !search.trim().isEmpty()) { where.append(" AND name LIKE ? COLLATE NOCASE"); args.add("%" + search.trim() + "%"); }
        try (Cursor c = getReadableDatabase().query("rules", new String[]{"json"}, where.toString(), args.toArray(new String[0]), null, null, "level ASC, name COLLATE NOCASE ASC", String.valueOf(limit))) {
            while (c.moveToNext()) {
                RuleItem item = parse(c.getString(0));
                if (item != null) out.add(item);
            }
        }
        return out;
    }

    private boolean heritageAllowed(RuleItem item, CharacterState character) {
        if (character.ancestry == null || character.ancestry.isEmpty()) return false;
        if (item.meta.optBoolean("versatile", false)) return true;
        String ancestry = item.meta.optString("ancestry", "");
        return !ancestry.isEmpty() && ancestry.equalsIgnoreCase(character.ancestry);
    }

    private boolean spellHasTradition(RuleItem item, String tradition) {
        JSONArray traditions = item.meta.optJSONArray("traditions");
        if (traditions == null) return false;
        for (int i = 0; i < traditions.length(); i++) {
            if (tradition.equalsIgnoreCase(traditions.optString(i))) return true;
        }
        return false;
    }

    private String fixedTraditionFor(String className) {
        if (className == null) return null;
        RuleItem cls = findExact("class", className);
        if (cls == null) return null;
        JSONArray traditions = cls.meta.optJSONArray("traditions");
        if (traditions == null || traditions.length() != 1) return null;
        String value = traditions.optString(0, "");
        return value.isEmpty() ? null : value;
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
