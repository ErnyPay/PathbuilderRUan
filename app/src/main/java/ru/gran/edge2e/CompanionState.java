package ru.gran.edge2e;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Lightweight local state for familiars, animal companions, eidolons and custom pets. */
public final class CompanionState {
    private static final String PREFS = "gran2e_companions_v3";
    private static final String KEY = "state";
    public final List<Companion> items = new ArrayList<>();

    public static final class Companion {
        public String id = UUID.randomUUID().toString();
        public String name = "Компаньон";
        public String type = "Компаньон";
        public int level = 1;
        public int hp = 10;
        public int maxHp = 10;
        public int ac = 15;
        public int attack = 5;
        public String damage = "1d6";
        public String notes = "";

        JSONObject toJson() {
            JSONObject o = new JSONObject();
            try { o.put("id",id);o.put("name",name);o.put("type",type);o.put("level",level);o.put("hp",hp);o.put("maxHp",maxHp);o.put("ac",ac);o.put("attack",attack);o.put("damage",damage);o.put("notes",notes); } catch (Exception ignored) { }
            return o;
        }
        static Companion fromJson(JSONObject o) {
            Companion c = new Companion(); if (o == null) return c;
            c.id=o.optString("id",c.id);c.name=o.optString("name",c.name);c.type=o.optString("type",c.type);c.level=Math.max(1,Math.min(20,o.optInt("level",1)));
            c.maxHp=Math.max(1,o.optInt("maxHp",10));c.hp=Math.max(0,Math.min(c.maxHp,o.optInt("hp",c.maxHp)));c.ac=o.optInt("ac",15);c.attack=o.optInt("attack",5);c.damage=o.optString("damage","1d6");c.notes=o.optString("notes","");
            return c;
        }
    }

    public static CompanionState load(Context context) {
        CompanionState out = new CompanionState(); String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "{}");
        try { JSONArray a = new JSONObject(raw).optJSONArray("items"); if (a != null) for (int i=0;i<a.length();i++) out.items.add(Companion.fromJson(a.optJSONObject(i))); } catch (Exception ignored) { }
        return out;
    }

    public void save(Context context) {
        JSONObject root = new JSONObject(); JSONArray a = new JSONArray(); for (Companion c : items) a.put(c.toJson());
        try { root.put("items",a); } catch (Exception ignored) { }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY,root.toString()).apply();
    }

    public Companion add(String type, int characterLevel) {
        Companion c = new Companion(); c.type = type == null || type.isEmpty() ? "Компаньон" : type; c.name = c.type; c.level = Math.max(1,Math.min(20,characterLevel));
        c.maxHp = 8 + c.level * 6; c.hp = c.maxHp; c.ac = 14 + c.level; c.attack = 4 + c.level; items.add(c); return c;
    }

    public void remove(String id) { items.removeIf(c -> c.id.equals(id)); }
}
