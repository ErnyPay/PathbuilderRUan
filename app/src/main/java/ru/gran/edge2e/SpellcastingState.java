package ru.gran.edge2e;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Persistent per-character spell-slot / preparation / repertoire play state. */
public final class SpellcastingState {
    public static final String PREFS = "gran2e_spellcasting_v32";
    public static final String KEY = "state";

    private final JSONObject prepared = new JSONObject();
    private final JSONObject preparedSpent = new JSONObject();
    private final JSONObject spent = new JSONObject();
    private final JSONObject signatures = new JSONObject();
    private final JSONObject repertoire = new JSONObject(); // learned rank:id -> id\u001fname

    public static final class RepertoireSpell {
        public final int rank;
        public final String id;
        public final String name;
        RepertoireSpell(int rank, String id, String name) { this.rank=rank; this.id=id; this.name=name; }
    }

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
            copy(root.optJSONObject("repertoire"), s.repertoire);
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
            root.put("repertoire", repertoire);
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
            if (item == null) { prepared.remove(key); preparedSpent.remove(key); }
            else { prepared.put(key, item.id + "\u001f" + item.name); preparedSpent.remove(key); }
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
    public boolean spend(int rank, int max) { int n=spent(rank); if(n>=max)return false; setSpent(rank,n+1,max); return true; }
    public void restoreOne(int rank, int max) { setSpent(rank, spent(rank)-1, max); }

    public String signatureId(int rank) { return signatures.optString(String.valueOf(rank), ""); }
    public boolean isSignature(int rank, String id) { return id != null && id.equals(signatureId(rank)); }
    public void setSignature(int rank, String id) {
        try { if (id == null || id.isEmpty()) signatures.remove(String.valueOf(rank)); else signatures.put(String.valueOf(rank), id); }
        catch (Exception ignored) { }
    }

    public boolean hasRepertoire(int rank, String id) { return repertoire.has(repertoireKey(rank,id)); }
    public int repertoireCount(int rank) {
        int count=0; String prefix=rank+":"; Iterator<String> it=repertoire.keys();
        while(it.hasNext()) if(it.next().startsWith(prefix)) count++;
        return count;
    }
    public void addRepertoire(int rank, RuleItem item) {
        if(item==null || rank<0 || rank>10)return;
        try { repertoire.put(repertoireKey(rank,item.id), item.id+"\u001f"+item.name); } catch(Exception ignored){}
    }
    public void removeRepertoire(int rank, String id) {
        repertoire.remove(repertoireKey(rank,id));
        if(id!=null && id.equals(signatureId(rank))) setSignature(rank,null);
    }
    public List<RepertoireSpell> repertoire(int rank) {
        List<RepertoireSpell> out=new ArrayList<>(); String prefix=rank+":"; Iterator<String> it=repertoire.keys();
        while(it.hasNext()) {
            String key=it.next(); if(!key.startsWith(prefix))continue;
            String raw=repertoire.optString(key,""); int split=raw.indexOf('\u001f');
            String id=split>=0?raw.substring(0,split):raw; String name=split>=0?raw.substring(split+1):raw;
            out.add(new RepertoireSpell(rank,id,name));
        }
        out.sort((a,b)->a.name.compareToIgnoreCase(b.name)); return out;
    }

    public void dailyReset() { clear(preparedSpent); clear(spent); }

    public void sanitize(SpellcastingRules.Profile profile, int level) {
        if (profile == null) return;
        JSONArray remove = new JSONArray(); Iterator<String> it = prepared.keys();
        while (it.hasNext()) {
            String key=it.next(); int colon=key.indexOf(':'); if(colon<=0){remove.put(key);continue;}
            int rank=parse(key.substring(0,colon)), slot=parse(key.substring(colon+1));
            if(rank<1 || slot<0 || slot>=profile.totalPreparedSlots(level,rank))remove.put(key);
        }
        for(int i=0;i<remove.length();i++){String key=remove.optString(i);prepared.remove(key);preparedSpent.remove(key);}
        for(int rank=1;rank<=10;rank++) {
            setSpent(rank,spent(rank),profile.slots(level,rank));
            if(profile.slots(level,rank)==0)setSignature(rank,null);
        }
    }

    private static String slotKey(int rank,int slot){return rank+":"+slot;}
    private static String repertoireKey(int rank,String id){return rank+":"+(id==null?"":id);}
    private static int parse(String s){try{return Integer.parseInt(s);}catch(Exception e){return -1;}}
    private static void clear(JSONObject o){ArrayList<String> keys=new ArrayList<>();Iterator<String> it=o.keys();while(it.hasNext())keys.add(it.next());for(String k:keys)o.remove(k);}
    private static void copy(JSONObject from,JSONObject to)throws Exception{if(from==null)return;Iterator<String>it=from.keys();while(it.hasNext()){String k=it.next();to.put(k,from.get(k));}}
}
