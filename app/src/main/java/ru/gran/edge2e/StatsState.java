package ru.gran.edge2e;

import android.content.Context;

import org.json.JSONObject;

public final class StatsState {
    private static final String PREFS = "gran2e_stats_v2";
    private static final String KEY = "stats";
    private static StatsState current;

    private final JSONObject attributes = new JSONObject();
    public String equippedArmorId = "";
    public boolean shieldRaised = false;
    public int heroPoints = 1;
    public int focus = 0;
    public int maxFocus = 0;
    public int dying = 0;
    public int wounded = 0;

    public StatsState() {
        for (String key : new String[]{"str","dex","con","int","wis","cha"}) setAbility(key, 0);
    }

    public int ability(String key) { return attributes.optInt(key, 0); }

    public void setAbility(String key, int value) {
        try { attributes.put(key, Math.max(-5, Math.min(10, value))); }
        catch (Exception ignored) { }
    }

    public static StatsState load(Context context) {
        String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "");
        StatsState loaded;
        if (raw == null || raw.isEmpty()) loaded = new StatsState();
        else {
            try { loaded = fromJson(new JSONObject(raw)); }
            catch (Exception ignored) { loaded = new StatsState(); }
        }
        current = loaded;
        return loaded;
    }

    public static StatsState fromJson(JSONObject o) {
        StatsState s = new StatsState();
        if (o == null) return s;
        JSONObject a = o.optJSONObject("attributes");
        if (a != null) for (String k : new String[]{"str","dex","con","int","wis","cha"}) s.setAbility(k, a.optInt(k, 0));
        s.equippedArmorId = o.optString("equippedArmorId", "");
        s.shieldRaised = o.optBoolean("shieldRaised", false);
        s.heroPoints = Math.max(0, Math.min(3, o.optInt("heroPoints", 1)));
        s.focus = Math.max(0, o.optInt("focus", 0));
        s.maxFocus = Math.max(0, Math.min(3, o.optInt("maxFocus", 0)));
        s.focus = Math.min(s.focus, s.maxFocus);
        s.dying = Math.max(0, Math.min(4, o.optInt("dying", 0)));
        s.wounded = Math.max(0, o.optInt("wounded", 0));
        return s;
    }

    public static JSONObject currentJson() {
        return current == null ? new JSONObject() : current.toJson();
    }

    public static void restoreCurrent(JSONObject o) {
        if (o == null) return;
        StatsState restored = fromJson(o);
        if (current == null) {
            current = restored;
            return;
        }
        for (String key : new String[]{"str","dex","con","int","wis","cha"}) current.setAbility(key, restored.ability(key));
        current.equippedArmorId = restored.equippedArmorId;
        current.shieldRaised = restored.shieldRaised;
        current.heroPoints = restored.heroPoints;
        current.focus = restored.focus;
        current.maxFocus = restored.maxFocus;
        current.dying = restored.dying;
        current.wounded = restored.wounded;
    }

    public void save(Context context) {
        current = this;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, toJson().toString()).apply();
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("attributes", attributes);
            o.put("equippedArmorId", equippedArmorId);
            o.put("shieldRaised", shieldRaised);
            o.put("heroPoints", heroPoints);
            o.put("focus", focus);
            o.put("maxFocus", maxFocus);
            o.put("dying", dying);
            o.put("wounded", wounded);
        } catch (Exception ignored) { }
        return o;
    }
}
