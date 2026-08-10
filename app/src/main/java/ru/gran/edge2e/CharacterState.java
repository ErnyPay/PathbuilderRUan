package ru.gran.edge2e;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public final class CharacterState {
    private static final String PREFS = "gran2e_character";
    private static final String KEY = "state";

    public String name = "Новый герой";
    public String className = "";
    public String ancestry = "";
    public String background = "";
    public int level = 1;
    public int maxHp = 10;
    public int hp = 10;
    public int tempHp = 0;
    public int ac = 10;
    public int perception = 0;
    public int fortitude = 0;
    public int reflex = 0;
    public int will = 0;
    public final JSONObject skillRanks = new JSONObject();
    public final JSONObject choices = new JSONObject();
    public final JSONObject conditions = new JSONObject();
    public final JSONArray inventory = new JSONArray();
    public final JSONArray spells = new JSONArray();

    public static CharacterState load(Context context) {
        CharacterState s = new CharacterState();
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = p.getString(KEY, "");
        if (raw == null || raw.isEmpty()) return s;
        try {
            JSONObject o = new JSONObject(raw);
            s.name = o.optString("name", s.name);
            s.className = o.optString("className", "");
            s.ancestry = o.optString("ancestry", "");
            s.background = o.optString("background", "");
            s.level = clamp(o.optInt("level", 1), 1, 20);
            s.maxHp = Math.max(1, o.optInt("maxHp", 10));
            s.hp = clamp(o.optInt("hp", s.maxHp), 0, s.maxHp);
            s.tempHp = Math.max(0, o.optInt("tempHp", 0));
            s.ac = o.optInt("ac", 10);
            s.perception = o.optInt("perception", 0);
            s.fortitude = o.optInt("fortitude", 0);
            s.reflex = o.optInt("reflex", 0);
            s.will = o.optInt("will", 0);
            copyObject(o.optJSONObject("skillRanks"), s.skillRanks);
            copyObject(o.optJSONObject("choices"), s.choices);
            copyObject(o.optJSONObject("conditions"), s.conditions);
            copyArray(o.optJSONArray("inventory"), s.inventory);
            copyArray(o.optJSONArray("spells"), s.spells);
        } catch (Exception ignored) {
        }
        return s;
    }

    public void save(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, toJson().toString()).apply();
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("name", name);
            o.put("className", className);
            o.put("ancestry", ancestry);
            o.put("background", background);
            o.put("level", level);
            o.put("maxHp", maxHp);
            o.put("hp", hp);
            o.put("tempHp", tempHp);
            o.put("ac", ac);
            o.put("perception", perception);
            o.put("fortitude", fortitude);
            o.put("reflex", reflex);
            o.put("will", will);
            o.put("skillRanks", skillRanks);
            o.put("choices", choices);
            o.put("conditions", conditions);
            o.put("inventory", inventory);
            o.put("spells", spells);
            o.put("statsV2", StatsState.currentJson());
            o.put("inventoryStateV2", InventoryState.currentJson());
        } catch (JSONException ignored) {
        }
        return o;
    }

    public void setChoice(String key, RuleItem item) {
        try {
            if (item == null) choices.remove(key);
            else choices.put(key, item.id + "\u001f" + item.name);
        } catch (JSONException ignored) {
        }
    }

    public String choiceName(String key) {
        String v = choices.optString(key, "");
        int split = v.indexOf('\u001f');
        return split >= 0 ? v.substring(split + 1) : v;
    }

    public Set<String> selectedNames() {
        Set<String> out = new HashSet<>();
        Iterator<String> it = choices.keys();
        while (it.hasNext()) {
            String n = choiceName(it.next());
            if (!n.isEmpty()) out.add(n.toLowerCase());
        }
        for (int i = 0; i < inventory.length(); i++) {
            String v = inventory.optString(i, "");
            int split = v.indexOf('\u001f');
            out.add((split >= 0 ? v.substring(split + 1) : v).toLowerCase());
        }
        return out;
    }

    public int rank(String skill) {
        return Math.min(skillRanks.optInt(skill.toLowerCase(), 0), maxSkillRankForLevel());
    }

    public void setRank(String skill, int rank) {
        try { skillRanks.put(skill.toLowerCase(), clamp(rank, 0, maxSkillRankForLevel())); }
        catch (JSONException ignored) { }
    }

    public int maxSkillRankForLevel() {
        if (level >= 15) return 4;
        if (level >= 7) return 3;
        if (level >= 3) return 2;
        return 1;
    }

    public boolean hasArrayItem(JSONArray array, String id) {
        for (int i = 0; i < array.length(); i++) {
            String v = array.optString(i, "");
            if (v.startsWith(id + "\u001f") || v.equals(id)) return true;
        }
        return false;
    }

    public void toggleArrayItem(JSONArray array, RuleItem item) {
        for (int i = 0; i < array.length(); i++) {
            String v = array.optString(i, "");
            if (v.startsWith(item.id + "\u001f") || v.equals(item.id)) {
                array.remove(i);
                return;
            }
        }
        array.put(item.id + "\u001f" + item.name);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void copyObject(JSONObject from, JSONObject to) throws JSONException {
        if (from == null) return;
        Iterator<String> it = from.keys();
        while (it.hasNext()) {
            String k = it.next();
            to.put(k, from.get(k));
        }
    }

    private static void copyArray(JSONArray from, JSONArray to) {
        if (from == null) return;
        for (int i = 0; i < from.length(); i++) to.put(from.opt(i));
    }
}
