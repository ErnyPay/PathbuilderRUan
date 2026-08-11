package ru.gran.edge2e;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

/** Persistent per-character spell-slot / preparation / repertoire play state. */
public final class SpellcastingState {
    public static final String PREFS = "gran2e_spellcasting_v32";
    public static final String KEY = "state";

    private final JSONObject prepared = new JSONObject();       // rank:slot -> id\u001fname
    private final JSONObject preparedSpent = new JSONObject();  // rank:slot -> true
    private final JSONObject spent = new JSONObject();          // rank -> spent spontaneous slots
    private final JSONObject signatures = new JSONObject();     // rank -> spell id

    public static SpellcastingState load(Context context) {
        SpellcastingState s = new SpellcastingState();
        String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "");
        if (raw == null || raw.isEmpty()) return s;
        try {
            JSONObject root = new JSONObject(raw);
            copy(root.optJSONObject("prepared"), s.prepared);
            copy(root.optJSONObject("preparedSpent"), s.preparedSpent);
            copy(root.optJSONObject("spent"), s.spent);
            copy(root.optJSONObject("signatures"), s.signatures);
        } catch (Exception ignored) { }
        return s;
    }

    public void save(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, toJson().toString()).apply();
    }

    public JSONObject toJson() {
        JSONObject root = new JSONObject();
        try {
            root.put("prepared", prepared);
            root.put("preparedSpent", preparedSpent);
            root.put("spent", spent);
            root.put("signatures", signatures);
        } catch (Exception ignored) { }
        return root;
    }

    public String preparedId(int rank, int slot) {
        String raw = prepared.optString(slotKey(rank, slot), "");
        int split = raw.indexOf('\u001f');
        return split >= 0 ? raw.substring(0, split) : raw;
    }

    public String preparedName(int rank, int slot) {
        String raw = prepared.optString(slotKey(rank, slot), "");
        int split = raw.indexOf('\u001f');
        return split >= 0 ? raw.substring(split + 1) : raw;
    }

    public void prepare(int rank, int slot, RuleItem item) {
        String key = slotKey(rank, slot);
        try {
            if (item == null) {
                prepared.remove(key);
                preparedSpent.remove(key);
            } else {
                prepared.put(key, item.id + "\u001f" + item.name);
                preparedSpent.remove(key);
            }
        } catch (Exception ignored) { }
    }

    public boolean preparedSpent(int rank, int slot) { return preparedSpent.optBoolean(slotKey(rank, slot), false); }
    public void setPreparedSpent(int rank, int slot, boolean value) {
        try { if (value) preparedSpent.put(slotKey(rank, slot), true); else preparedSpent.remove(slotKey(rank, slot)); }
        catch (Exception ignored) { }
    }

    public int spent(int rank) { return Math.max(0, spent.optInt(String.valueOf(rank), 0)); }
    public void setSpent(int rank, int value, int max) {
        try {
            int v = Math.max(0, Math.min(Math.max(0, max), value));
            if (v == 0) spent.remove(String.valueOf(rank)); else spent.put(String.valueOf(rank), v);
        } catch (Exception ignored) { }
    }

    public boolean spend(int rank, int max) {
        int n = spent(rank);
        if (n >= max) return false;
        setSpent(rank, n + 1, max);
        return true;
    }

    public void restoreOne(int rank, int max) { setSpent(rank, spent(rank) - 1, max); }

    public String signatureId(int rank) { return signatures.optString(String.valueOf(rank), ""); }
    public boolean isSignature(int rank, String id) { return id != null && id.equals(signatureId(rank)); }
    public void setSignature(int rank, String id) {
        try { if (id == null || id.isEmpty()) signatures.remove(String.valueOf(rank)); else signatures.put(String.valueOf(rank), id); }
        catch (Exception ignored) { }
    }

    public void dailyReset() {
        clear(preparedSpent);
        clear(spent);
    }

    /** Drop stale slots after level/class changes without touching the spellbook/repertoire. */
    public void sanitize(SpellcastingRules.Profile profile, int level) {
        if (profile == null) return;
        JSONArray remove = new JSONArray();
        java.util.Iterator<String> it = prepared.keys();
        while (it.hasNext()) {
            String key = it.next();
            int colon = key.indexOf(':');
            if (colon <= 0) { remove.put(key); continue; }
            int rank = parse(key.substring(0, colon));
            int slot = parse(key.substring(colon + 1));
            if (rank < 1 || slot < 0 || slot >= profile.totalPreparedSlots(level, rank)) remove.put(key);
        }
        for (int i = 0; i < remove.length(); i++) {
            String key = remove.optString(i); prepared.remove(key); preparedSpent.remove(key);
        }
        for (int rank = 1; rank <= 10; rank++) setSpent(rank, spent(rank), profile.slots(level, rank));
    }

    private static String slotKey(int rank, int slot) { return rank + ":" + slot; }
    private static int parse(String s) { try { return Integer.parseInt(s); } catch (Exception e) { return -1; } }
    private static void clear(JSONObject o) { java.util.ArrayList<String> keys = new java.util.ArrayList<>(); java.util.Iterator<String> it=o.keys(); while(it.hasNext()) keys.add(it.next()); for(String k:keys)o.remove(k); }
    private static void copy(JSONObject from, JSONObject to) throws Exception { if (from == null) return; java.util.Iterator<String> it=from.keys(); while(it.hasNext()){String k=it.next();to.put(k,from.get(k));} }
}
