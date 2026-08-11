package ru.gran.edge2e;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class RuleItem {
    public final String id;
    public final String name;
    public final String category;
    public final String subtype;
    public final int level;
    public final String description;
    public final String source;
    public final String license;
    public final List<String> traits;
    public final List<String> prerequisites;
    public final JSONObject meta;

    public RuleItem(String id, String name, String category, String subtype, int level,
                    String description, String source, String license,
                    List<String> traits, List<String> prerequisites, JSONObject meta) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.subtype = subtype;
        this.level = level;
        this.description = description;
        this.source = source;
        this.license = license;
        this.traits = traits;
        this.prerequisites = prerequisites;
        this.meta = meta == null ? new JSONObject() : meta;
    }

    public static RuleItem fromJson(JSONObject o) {
        JSONObject meta = o.optJSONObject("meta");
        String description = o.optString("description");
        if (meta != null) {
            String ru = meta.optString("ruDescription", "").trim();
            if (!ru.isEmpty()) description = ru;
        }
        return new RuleItem(
                o.optString("id"),
                o.optString("name"),
                o.optString("category"),
                o.optString("subtype"),
                o.optInt("level", 0),
                description,
                o.optString("source"),
                o.optString("license"),
                strings(o.optJSONArray("traits")),
                strings(o.optJSONArray("prerequisites")),
                meta
        );
    }

    private static List<String> strings(JSONArray a) {
        List<String> out = new ArrayList<>();
        if (a == null) return out;
        for (int i = 0; i < a.length(); i++) out.add(a.optString(i));
        return out;
    }

    public String traitsLine() {
        return RuLabels.traits(traits);
    }
}
