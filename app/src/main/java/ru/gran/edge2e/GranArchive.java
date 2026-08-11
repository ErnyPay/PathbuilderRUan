package ru.gran.edge2e;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

/** Complete local character archive used by Gran 6.0 import/export. */
public final class GranArchive {
    private GranArchive() { }

    public static String exportCharacter(Context context) {
        JSONObject root = new JSONObject();
        try {
            root.put("format", "gran2e-archive-1");
            root.put("character", CharacterState.load(context).toJson());
            putPrefs(root, "stats", context, "gran2e_stats_v2", "stats");
            putPrefs(root, "inventoryState", context, "gran2e_inventory_v2", "inventory");
            putPrefs(root, "knowledge", context, "gran2e_knowledge_v33", "state");
            putPrefs(root, "companions", context, "gran2e_companions_v3", "state");
            putPrefs(root, "spellcasting", context, "gran2e_spellcasting_v32", "state");
            putPrefs(root, "itemMods", context, "gran2e_item_mods_v3", "mods");
        } catch (Exception ignored) { }
        return root.toString();
    }

    public static void importCharacter(Context context, String raw) throws Exception {
        JSONObject root = new JSONObject(raw);
        if (!"gran2e-archive-1".equals(root.optString("format", ""))) {
            CharacterState legacy = CharacterJson.fromString(raw);
            legacy.save(context);
            CharacterProfiles.saveCurrent(context);
            return;
        }
        JSONObject character = root.optJSONObject("character");
        if (character == null) throw new IllegalArgumentException("Archive has no character");
        CharacterState imported = CharacterJson.fromString(character.toString());
        imported.save(context);
        restorePrefs(root, "stats", context, "gran2e_stats_v2", "stats");
        restorePrefs(root, "inventoryState", context, "gran2e_inventory_v2", "inventory");
        restorePrefs(root, "knowledge", context, "gran2e_knowledge_v33", "state");
        restorePrefs(root, "companions", context, "gran2e_companions_v3", "state");
        restorePrefs(root, "spellcasting", context, "gran2e_spellcasting_v32", "state");
        restorePrefs(root, "itemMods", context, "gran2e_item_mods_v3", "mods");
        StatsState stats = StatsState.load(context);
        StatsState.recalculate(CharacterState.load(context));
        stats.save(context);
        CharacterProfiles.saveCurrent(context);
        RuntimeBridge.invalidate();
    }

    private static void putPrefs(JSONObject root, String name, Context context, String prefs, String key) throws Exception {
        String raw = context.getSharedPreferences(prefs, Context.MODE_PRIVATE).getString(key, "");
        if (raw == null || raw.trim().isEmpty()) root.put(name, new JSONObject());
        else root.put(name, new JSONObject(raw));
    }

    private static void restorePrefs(JSONObject root, String name, Context context, String prefs, String key) {
        JSONObject value = root.optJSONObject(name);
        SharedPreferences.Editor e = context.getSharedPreferences(prefs, Context.MODE_PRIVATE).edit();
        if (value == null || value.length() == 0) e.remove(key); else e.putString(key, value.toString());
        e.apply();
    }
}
