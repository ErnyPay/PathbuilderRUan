package ru.gran.edge2e;

import android.content.Context;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;

public final class InventoryState {
    private static final String PREFS = "gran2e_inventory_v2";
    private static final String KEY = "inventory";
    private static InventoryState current;

    private final JSONObject quantities = new JSONObject();
    /** Whole item stack -> container item id. Empty means carried directly. */
    private final JSONObject containers = new JSONObject();
    /** Container id -> true when worn in its normal usage; false when merely held/stowed. */
    private final JSONObject containerWorn = new JSONObject();
    public int pp = 0;
    public int gp = 15;
    public int sp = 0;
    public int cp = 0;

    public static InventoryState load(Context context) {
        String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "");
        InventoryState loaded;
        if (raw == null || raw.isEmpty()) loaded = new InventoryState();
        else {
            try { loaded = fromJson(new JSONObject(raw)); }
            catch (Exception ignored) { loaded = new InventoryState(); }
        }
        current = loaded;
        return loaded;
    }

    public static InventoryState fromJson(JSONObject o) {
        InventoryState s = new InventoryState();
        if (o == null) return s;
        copyInts(o.optJSONObject("quantities"), s.quantities);
        copyStrings(o.optJSONObject("containers"), s.containers);
        JSONObject worn=o.optJSONObject("containerWorn");
        if(worn!=null){Iterator<String>it=worn.keys();while(it.hasNext()){String k=it.next();try{s.containerWorn.put(k,worn.optBoolean(k,true));}catch(Exception ignored){}}}
        s.pp = Math.max(0, o.optInt("pp", 0));
        s.gp = Math.max(0, o.optInt("gp", 15));
        s.sp = Math.max(0, o.optInt("sp", 0));
        s.cp = Math.max(0, o.optInt("cp", 0));
        return s;
    }

    public static JSONObject currentJson() { return current == null ? new JSONObject() : current.toJson(); }

    public static void restoreCurrent(JSONObject o) {
        if (o == null) return;
        InventoryState restored = fromJson(o);
        if (current == null) { current = restored; return; }
        clear(current.quantities); clear(current.containers); clear(current.containerWorn);
        copyInts(restored.quantities,current.quantities); copyStrings(restored.containers,current.containers);
        Iterator<String>w=restored.containerWorn.keys();while(w.hasNext()){String k=w.next();try{current.containerWorn.put(k,restored.containerWorn.optBoolean(k,true));}catch(Exception ignored){}}
        current.pp = restored.pp; current.gp = restored.gp; current.sp = restored.sp; current.cp = restored.cp;
    }

    public int quantity(String id) { return Math.max(0, quantities.optInt(id, 1)); }
    public void setQuantity(String id, int value) {
        try { if (value <= 0) quantities.remove(id); else quantities.put(id, Math.min(9999, value)); }
        catch (Exception ignored) { }
    }
    public void change(String id, int delta) { setQuantity(id, Math.max(0, quantity(id) + delta)); }

    public String containerFor(String itemId){return containers.optString(itemId,"");}
    public void assignContainer(String itemId,String containerId){
        if(itemId==null||itemId.isEmpty())return;
        try{if(containerId==null||containerId.isEmpty()||itemId.equals(containerId))containers.remove(itemId);else containers.put(itemId,containerId);}catch(Exception ignored){}
    }
    public boolean isContainerWorn(String id){return !containerWorn.has(id)||containerWorn.optBoolean(id,true);}
    public void setContainerWorn(String id,boolean worn){try{if(worn)containerWorn.remove(id);else containerWorn.put(id,false);}catch(Exception ignored){}}

    public void remove(String id) {
        quantities.remove(id); containers.remove(id); containerWorn.remove(id);
        ArrayList<String> children=new ArrayList<>();Iterator<String>it=containers.keys();while(it.hasNext()){String child=it.next();if(id.equals(containers.optString(child,"")))children.add(child);}for(String child:children)containers.remove(child);
    }

    public void sanitize(java.util.Set<String> inventoryIds, java.util.Set<String> containerIds){
        ArrayList<String>remove=new ArrayList<>();Iterator<String>it=containers.keys();while(it.hasNext()){
            String item=it.next(),container=containers.optString(item,"");
            if(!inventoryIds.contains(item)||!containerIds.contains(container)||item.equals(container))remove.add(item);
        }for(String key:remove)containers.remove(key);
        ArrayList<String>wornRemove=new ArrayList<>();Iterator<String>w=containerWorn.keys();while(w.hasNext()){String id=w.next();if(!containerIds.contains(id))wornRemove.add(id);}for(String id:wornRemove)containerWorn.remove(id);
    }

    public void save(Context context) {
        current = this;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, toJson().toString()).apply();
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("quantities", quantities); o.put("containers",containers); o.put("containerWorn",containerWorn);
            o.put("pp", pp); o.put("gp", gp); o.put("sp", sp); o.put("cp", cp);
        } catch (Exception ignored) { }
        return o;
    }

    private static void clear(JSONObject o){ArrayList<String>keys=new ArrayList<>();Iterator<String>it=o.keys();while(it.hasNext())keys.add(it.next());for(String k:keys)o.remove(k);}
    private static void copyInts(JSONObject from,JSONObject to){if(from==null)return;Iterator<String>it=from.keys();while(it.hasNext()){String k=it.next();try{to.put(k,from.optInt(k,1));}catch(Exception ignored){}}}
    private static void copyStrings(JSONObject from,JSONObject to){if(from==null)return;Iterator<String>it=from.keys();while(it.hasNext()){String k=it.next(),v=from.optString(k,"");try{if(!v.isEmpty())to.put(k,v);}catch(Exception ignored){}}}
}
