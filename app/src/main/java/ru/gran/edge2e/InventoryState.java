package ru.gran.edge2e;

import android.content.Context;

import org.json.JSONObject;

public final class InventoryState {
    private static final String PREFS = "gran2e_inventory_v2";
    private static final String KEY = "inventory";
    private final JSONObject quantities = new JSONObject();
    public int pp = 0;
    public int gp = 15;
    public int sp = 0;
    public int cp = 0;

    public static InventoryState load(Context context) {
        String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "");
        if (raw == null || raw.isEmpty()) return new InventoryState();
        try { return fromJson(new JSONObject(raw)); }
        catch (Exception ignored) { return new InventoryState(); }
    }

    public static InventoryState fromJson(JSONObject o) {
        InventoryState s = new InventoryState();
        if (o == null) return s;
        JSONObject q = o.optJSONObject("quantities");
        if (q != null) {
            java.util.Iterator<String> it = q.keys();
            while (it.hasNext()) {
                String k = it.next();
                s.setQuantity(k, q.optInt(k, 1));
            }
        }
        s.pp = Math.max(0, o.optInt("pp", 0));
        s.gp = Math.max(0, o.optInt("gp", 15));
        s.sp = Math.max(0, o.optInt("sp", 0));
        s.cp = Math.max(0, o.optInt("cp", 0));
        return s;
    }

    public int quantity(String id) { return Math.max(0, quantities.optInt(id, 1)); }

    public void setQuantity(String id, int value) {
        try {
            if (value <= 0) quantities.remove(id);
            else quantities.put(id, Math.min(9999, value));
        } catch (Exception ignored) { }
    }

    public void change(String id, int delta) { setQuantity(id, Math.max(0, quantity(id) + delta)); }
    public void remove(String id) { quantities.remove(id); }

    public void save(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, toJson().toString()).apply();
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("quantities", quantities);
            o.put("pp", pp); o.put("gp", gp); o.put("sp", sp); o.put("cp", cp);
        } catch (Exception ignored) { }
        return o;
    }
}
