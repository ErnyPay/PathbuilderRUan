package ru.gran.edge2e;

import android.content.Context;

import org.json.JSONObject;

public final class StatsState {
    private static final String PREFS = "gran2e_stats_v2";
    private static final String KEY = "stats";
    private static StatsState current;

    private final JSONObject attributes = new JSONObject();
    private final JSONObject abilityScores = new JSONObject();
    private transient Context attachedContext;
    public String equippedArmorId = "";
    public boolean shieldRaised = false;
    public int heroPoints = 1;
    public int focus = 0;
    public int maxFocus = 0;
    public int dying = 0;
    public int wounded = 0;

    public StatsState() {
        for (String key : new String[]{"str","dex","con","int","wis","cha"}) {
            setAbility(key, 0);
            try { abilityScores.put(key, 10); } catch (Exception ignored) { }
        }
        current = this;
    }

    public int ability(String key) { return attributes.optInt(key, 0); }
    public int abilityScore(String key) { return abilityScores.optInt(key, 10); }
    public Context context() { return attachedContext; }

    public static int currentAbility(String key) {
        return current == null ? 0 : current.ability(key);
    }

    public void setAbility(String key, int value) {
        try { attributes.put(key, Math.max(-5, Math.min(10, value))); }
        catch (Exception ignored) { }
        current = this;
    }

    public void setAbilityScore(String key, int score) {
        int clamped = Math.max(1, Math.min(30, score));
        try { abilityScores.put(key, clamped); }
        catch (Exception ignored) { }
        setAbility(key, Math.floorDiv(clamped - 10, 2));
    }

    public static StatsState load(Context context) {
        String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "");
        StatsState state;
        if (raw == null || raw.isEmpty()) state = new StatsState();
        else {
            try { state = fromJson(new JSONObject(raw)); }
            catch (Exception ignored) { state = new StatsState(); }
        }
        state.attachedContext = context.getApplicationContext();
        current = state;
        try { AbilityPlanner.apply(context.getApplicationContext(), CharacterState.load(context), state); }
        catch (Exception ignored) { }
        current = state;
        return state;
    }

    /** Called after any character choice is saved so dependent numbers change immediately. */
    public static void recalculate(CharacterState character) {
        if (current == null || current.attachedContext == null || character == null) return;
        try {
            AbilityPlanner.apply(current.attachedContext, character, current);
            RuntimeBridge.invalidate();
        } catch (Exception ignored) { }
        current = current;
    }

    public static StatsState fromJson(JSONObject o) {
        StatsState s = new StatsState();
        if (o == null) return s;
        JSONObject a = o.optJSONObject("attributes");
        JSONObject scores = o.optJSONObject("abilityScores");
        for (String k : new String[]{"str","dex","con","int","wis","cha"}) {
            if (scores != null && scores.has(k)) s.setAbilityScore(k, scores.optInt(k, 10));
            else if (a != null) s.setAbility(k, a.optInt(k, 0));
        }
        s.equippedArmorId = o.optString("equippedArmorId", "");
        s.shieldRaised = o.optBoolean("shieldRaised", false);
        s.heroPoints = Math.max(0, Math.min(3, o.optInt("heroPoints", 1)));
        s.focus = Math.max(0, o.optInt("focus", 0));
        s.maxFocus = Math.max(0, Math.min(3, o.optInt("maxFocus", 0)));
        s.focus = Math.min(s.focus, s.maxFocus);
        s.dying = Math.max(0, Math.min(4, o.optInt("dying", 0)));
        s.wounded = Math.max(0, o.optInt("wounded", 0));
        current = s;
        return s;
    }

    public void save(Context context) {
        attachedContext = context.getApplicationContext();
        saveAttached();
    }

    public void saveAttached() {
        current = this;
        if (attachedContext == null) return;
        attachedContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, toJson().toString()).apply();
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("attributes", attributes);
            o.put("abilityScores", abilityScores);
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
