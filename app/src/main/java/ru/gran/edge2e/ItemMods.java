package ru.gran.edge2e;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Character-owned rune configuration layered over immutable rules-catalog equipment. */
public final class ItemMods {
    private static final String PREFS = "gran2e_item_mods_v3";
    private static final String KEY = "mods";
    private ItemMods() { }

    private static JSONObject root(Context c) { try { return new JSONObject(c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(KEY,"{}")); } catch(Exception e){ return new JSONObject(); } }
    private static JSONObject item(Context c,String id) { JSONObject o=root(c).optJSONObject(id); return o==null?new JSONObject():o; }
    private static void write(Context c,String id,JSONObject value) { JSONObject r=root(c); try { if(value.length()==0) r.remove(id); else r.put(id,value); } catch(Exception ignored){} c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString(KEY,r.toString()).apply(); }

    public static int potency(Context c, RuleItem item) { if(c==null||item==null)return item==null?0:item.meta.optInt("potency",0); return Math.max(item.meta.optInt("potency",0), item(c,item.id).optInt("potency",0)); }
    public static int striking(Context c, RuleItem item) { if(c==null||item==null)return item==null?0:item.meta.optInt("striking",0); return Math.max(item.meta.optInt("striking",0), item(c,item.id).optInt("striking",0)); }
    public static void setPotency(Context c,String id,int v){JSONObject o=item(c,id);try{o.put("potency",Math.max(0,Math.min(4,v)));}catch(Exception ignored){}write(c,id,o);}
    public static void setStriking(Context c,String id,int v){JSONObject o=item(c,id);try{o.put("striking",Math.max(0,Math.min(3,v)));}catch(Exception ignored){}write(c,id,o);}

    public static List<String> properties(Context c, RuleItem item) {
        List<String> out=new ArrayList<>(); if(item!=null){JSONArray base=item.meta.optJSONArray("propertyRunes");if(base!=null)for(int i=0;i<base.length();i++){String s=base.optString(i,"");if(!s.isEmpty()&&!out.contains(s))out.add(s);}}
        if(c!=null&&item!=null){JSONArray a=item(c,item.id).optJSONArray("properties");if(a!=null)for(int i=0;i<a.length();i++){String s=a.optString(i,"");if(!s.isEmpty()&&!out.contains(s))out.add(s);}}
        return out;
    }
    public static void setProperties(Context c,String id,String csv){JSONObject o=item(c,id);JSONArray a=new JSONArray();if(csv!=null)for(String s:csv.split(",")){String v=s.trim();if(!v.isEmpty())a.put(v);}try{o.put("properties",a);}catch(Exception ignored){}write(c,id,o);}
    public static String propertiesText(Context c,RuleItem item){return String.join(", ",properties(c,item));}
    public static void clear(Context c,String id){JSONObject r=root(c);r.remove(id);c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString(KEY,r.toString()).apply();}
}
