package ru.gran.edge2e;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Imports a public Pathbuilder 2e shared build without changing RuneSheet's internal rule IDs. */
public final class PathbuilderImport {
    private PathbuilderImport() { }

    public static String buildId(String value) {
        if (value == null) return "";
        String s = value.trim();
        int q = s.indexOf('?');
        if (q >= 0) {
            String query = s.substring(q + 1);
            for (String part : query.split("&")) {
                int eq = part.indexOf('=');
                if (eq > 0 && "build".equalsIgnoreCase(part.substring(0, eq))) return part.substring(eq + 1).replaceAll("[^0-9]", "");
            }
        }
        return s.replaceAll("[^0-9]", "");
    }

    public static CharacterState fetch(Context context, String value) throws Exception {
        String id = buildId(value);
        if (id.isEmpty()) throw new IllegalArgumentException("Не найден ID персонажа");
        URL url = new URL("https://pathbuilder2e.com/json.php?id=" + id);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setRequestProperty("User-Agent", "RuneSheet-RU/7");
        c.setRequestProperty("Accept", "application/json");
        try {
            int code = c.getResponseCode();
            if (code < 200 || code >= 300) throw new IllegalStateException("Pathbuilder HTTP " + code);
            StringBuilder b = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                String line; while ((line = r.readLine()) != null) b.append(line);
            }
            JSONObject root = new JSONObject(b.toString());
            JSONObject build = root.optJSONObject("build");
            if (build == null) build = root;
            return convert(context, build);
        } finally { c.disconnect(); }
    }

    public static CharacterState convert(Context context, JSONObject build) {
        CharacterState s = new CharacterState();
        RuleStore store = new RuleStore(context);
        store.getReadableDatabase();

        s.name = build.optString("name", s.name);
        s.className = build.optString("class", build.optString("className", ""));
        s.ancestry = build.optString("ancestry", "");
        s.background = build.optString("background", "");
        s.level = Math.max(1, Math.min(20, build.optInt("level", 1)));

        String heritage = build.optString("heritage", "");
        RuleItem heritageItem = store.findExact("heritage", heritage);
        if (heritageItem != null) s.setChoice("base:heritage", heritageItem);

        int featIndex = 0;
        for (String name : firstStrings(build.opt("feats"))) {
            RuleItem item = store.findExact("feat", cleanName(name));
            if (item != null) s.setChoice("import:feat:" + (++featIndex), item);
        }

        for (String name : firstStrings(build.opt("spells"))) {
            RuleItem item = store.findExact("spell", cleanName(name));
            if (item != null && !s.hasArrayItem(s.spells, item.id)) s.toggleArrayItem(s.spells, item);
        }
        Object casters = build.opt("spellCasters");
        for (String name : firstStrings(casters)) {
            RuleItem item = store.findExact("spell", cleanName(name));
            if (item != null && !s.hasArrayItem(s.spells, item.id)) s.toggleArrayItem(s.spells, item);
        }

        for (String name : firstStrings(build.opt("equipment"))) {
            RuleItem item = store.findExact("equipment", cleanName(name));
            if (item == null) item = store.findAnyExact(cleanName(name));
            if (item != null && !s.hasArrayItem(s.inventory, item.id)) s.toggleArrayItem(s.inventory, item);
        }
        for (String name : firstStrings(build.opt("weapons"))) {
            RuleItem item = store.findAnyExact(cleanName(name));
            if (item != null && !s.hasArrayItem(s.inventory, item.id)) s.toggleArrayItem(s.inventory, item);
        }
        for (String name : firstStrings(build.opt("armor"))) {
            RuleItem item = store.findAnyExact(cleanName(name));
            if (item != null && !s.hasArrayItem(s.inventory, item.id)) s.toggleArrayItem(s.inventory, item);
        }

        return s;
    }

    private static String cleanName(String value) {
        if (value == null) return "";
        String s = value.trim();
        int paren = s.lastIndexOf(" (");
        if (paren > 0) s = s.substring(0, paren).trim();
        return s;
    }

    private static List<String> firstStrings(Object value) {
        ArrayList<String> out = new ArrayList<>();
        collect(value, out);
        return out;
    }

    private static void collect(Object value, List<String> out) {
        if (value == null || value == JSONObject.NULL) return;
        if (value instanceof JSONArray) {
            JSONArray a = (JSONArray) value;
            if (a.length() > 0 && a.opt(0) instanceof String) {
                String s = a.optString(0, "").trim();
                if (!s.isEmpty()) out.add(s);
                return;
            }
            for (int i = 0; i < a.length(); i++) collect(a.opt(i), out);
            return;
        }
        if (value instanceof JSONObject) {
            JSONObject o = (JSONObject) value;
            String[] preferred = {"name", "spell", "item"};
            for (String key : preferred) {
                String s = o.optString(key, "").trim();
                if (!s.isEmpty()) { out.add(s); return; }
            }
            Iterator<String> it = o.keys();
            while (it.hasNext()) collect(o.opt(it.next()), out);
        }
    }
}
