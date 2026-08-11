package ru.gran.edge2e;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Iterator;

public final class CharacterJson {
    private CharacterJson() { }

    public static CharacterState fromString(String raw) throws Exception {
        JSONObject o = new JSONObject(raw);
        CharacterState s = new CharacterState();
        s.name = o.optString("name", s.name);
        s.className = o.optString("className", "");
        s.ancestry = o.optString("ancestry", "");
        s.background = o.optString("background", "");
        s.level = Math.max(1, Math.min(20, o.optInt("level", 1)));
        s.maxHp = Math.max(1, o.optInt("maxHp", 10));
        s.hp = Math.max(0, Math.min(s.maxHp, o.optInt("hp", s.maxHp)));
        s.tempHp = Math.max(0, o.optInt("tempHp", 0));
        s.ac = o.optInt("ac", 10);
        s.perception = o.optInt("perception", 0);
        s.fortitude = o.optInt("fortitude", 0);
        s.reflex = o.optInt("reflex", 0);
        s.will = o.optInt("will", 0);
        copy(o.optJSONObject("skillRanks"), s.skillRanks);
        copy(o.optJSONObject("choices"), s.choices);
        copy(o.optJSONObject("choiceMeta"), s.choiceMeta);
        copy(o.optJSONObject("ruleSelections"), s.ruleSelections);
        copy(o.optJSONObject("conditions"), s.conditions);
        copy(o.optJSONArray("inventory"), s.inventory);
        copy(o.optJSONArray("spells"), s.spells);
        return s;
    }

    private static void copy(JSONObject from, JSONObject to) throws Exception {
        if (from == null) return;
        Iterator<String> it = from.keys();
        while (it.hasNext()) {
            String k = it.next();
            to.put(k, from.get(k));
        }
    }

    private static void copy(JSONArray from, JSONArray to) {
        if (from == null) return;
        for (int i = 0; i < from.length(); i++) to.put(from.opt(i));
    }
}
