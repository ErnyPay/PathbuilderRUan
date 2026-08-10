package ru.gran.edge2e;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/** Multiple local character saves. Each profile snapshots character, stats and inventory JSON. */
public final class CharacterProfiles {
    private static final String PREFS = "gran2e_profiles_v3";
    private static final String KEY = "profiles";
    private static final String ACTIVE = "active";

    private CharacterProfiles() { }

    public static final class Profile {
        public final String id;
        public final String name;
        public final String summary;
        public final long savedAt;
        Profile(String id, String name, String summary, long savedAt) {
            this.id = id; this.name = name; this.summary = summary; this.savedAt = savedAt;
        }
    }

    public static String activeId(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(ACTIVE, "");
    }

    public static List<Profile> list(Context context) {
        JSONObject root = readRoot(context); List<Profile> out = new ArrayList<>();
        Iterator<String> it = root.keys();
        while (it.hasNext()) {
            String id = it.next(); JSONObject p = root.optJSONObject(id); if (p == null) continue;
            JSONObject c = parse(p.optString("character", "{}"));
            String name = c.optString("name", "Без имени");
            String cls = c.optString("className", ""); int level = Math.max(1, c.optInt("level", 1));
            String summary = "ур. " + level + (cls.isEmpty() ? "" : " • " + RuNames.shortName(cls));
            out.add(new Profile(id, name, summary, p.optLong("savedAt", 0L)));
        }
        Collections.sort(out, Comparator.comparingLong((Profile p) -> p.savedAt).reversed());
        return out;
    }

    public static String saveCurrent(Context context) {
        String id = activeId(context); if (id == null || id.isEmpty()) id = UUID.randomUUID().toString();
        saveCurrentAs(context, id); return id;
    }

    public static String saveCopy(Context context) {
        String id = UUID.randomUUID().toString(); saveCurrentAs(context, id); return id;
    }

    public static void saveCurrentAs(Context context, String id) {
        if (id == null || id.isEmpty()) return;
        JSONObject root = readRoot(context); JSONObject p = new JSONObject();
        try {
            p.put("character", raw(context, "gran2e_character", "state", new CharacterState().toJson().toString()));
            p.put("stats", raw(context, "gran2e_stats_v2", "stats", new StatsState().toJson().toString()));
            p.put("inventory", raw(context, "gran2e_inventory_v2", "inventory", new InventoryState().toJson().toString()));
            p.put("companions", raw(context, "gran2e_companions_v3", "state", "{}"));
            p.put("savedAt", System.currentTimeMillis());
            root.put(id, p);
            prefs(context).edit().putString(KEY, root.toString()).putString(ACTIVE, id).apply();
        } catch (Exception ignored) { }
    }

    public static boolean load(Context context, String id) {
        if (id == null || id.isEmpty()) return false;
        // Snapshot whatever was open before switching.
        String old = activeId(context); if (old != null && !old.isEmpty() && !old.equals(id)) saveCurrentAs(context, old);
        JSONObject p = readRoot(context).optJSONObject(id); if (p == null) return false;
        write(context, "gran2e_character", "state", p.optString("character", "{}"));
        write(context, "gran2e_stats_v2", "stats", p.optString("stats", "{}"));
        write(context, "gran2e_inventory_v2", "inventory", p.optString("inventory", "{}"));
        write(context, "gran2e_companions_v3", "state", p.optString("companions", "{}"));
        prefs(context).edit().putString(ACTIVE, id).apply();
        RuntimeBridge.invalidate();
        return true;
    }

    public static String createNew(Context context) {
        String old = activeId(context); if (old != null && !old.isEmpty()) saveCurrentAs(context, old);
        CharacterState c = new CharacterState(); c.save(context);
        StatsState s = new StatsState(); s.save(context);
        InventoryState i = new InventoryState(); i.save(context);
        context.getSharedPreferences("gran2e_companions_v3", Context.MODE_PRIVATE).edit().clear().apply();
        String id = UUID.randomUUID().toString(); prefs(context).edit().putString(ACTIVE, id).apply(); saveCurrentAs(context, id);
        RuntimeBridge.invalidate(); return id;
    }

    public static void delete(Context context, String id) {
        JSONObject root = readRoot(context); root.remove(id); SharedPreferences.Editor e = prefs(context).edit().putString(KEY, root.toString());
        if (id != null && id.equals(activeId(context))) e.remove(ACTIVE); e.apply();
    }

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }
    private static JSONObject readRoot(Context c) { try { return new JSONObject(prefs(c).getString(KEY, "{}")); } catch (Exception e) { return new JSONObject(); } }
    private static JSONObject parse(String raw) { try { return new JSONObject(raw); } catch (Exception e) { return new JSONObject(); } }
    private static String raw(Context c, String prefs, String key, String fallback) { String value = c.getSharedPreferences(prefs, Context.MODE_PRIVATE).getString(key, ""); return value == null || value.isEmpty() ? fallback : value; }
    private static void write(Context c, String prefs, String key, String value) { c.getSharedPreferences(prefs, Context.MODE_PRIVATE).edit().putString(key, value == null ? "{}" : value).commit(); }
}
