package ru.gran.edge2e;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Per-character language and custom Lore selections. */
public final class KnowledgeState {
    public static final String PREFS="gran2e_knowledge_v33";
    public static final String KEY="state";
    private final JSONArray languages=new JSONArray();
    private final JSONArray additionalLores=new JSONArray();

    public static KnowledgeState load(Context context){
        KnowledgeState s=new KnowledgeState();
        String raw=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(KEY,"");
        if(raw==null||raw.isEmpty())return s;
        try{JSONObject o=new JSONObject(raw);copy(o.optJSONArray("languages"),s.languages);copy(o.optJSONArray("additionalLores"),s.additionalLores);}catch(Exception ignored){}
        return s;
    }
    public void save(Context context){context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString(KEY,toJson().toString()).apply();}
    public JSONObject toJson(){JSONObject o=new JSONObject();try{o.put("languages",languages);o.put("additionalLores",additionalLores);}catch(Exception ignored){}return o;}

    public List<String> languages(){List<String> out=new ArrayList<>();for(int i=0;i<languages.length();i++){String v=languages.optString(i,"");if(!v.isEmpty())out.add(v);}return out;}
    public boolean hasLanguage(String slug){for(int i=0;i<languages.length();i++)if(eq(slug,languages.optString(i,"")))return true;return false;}
    public void addLanguage(String slug){if(slug==null||slug.isEmpty()||hasLanguage(slug))return;languages.put(slug);}
    public void removeLanguage(String slug){for(int i=0;i<languages.length();i++)if(eq(slug,languages.optString(i,""))){languages.remove(i);return;}}

    public List<String> lores(){List<String> out=new ArrayList<>();for(int i=0;i<additionalLores.length();i++){String v=additionalLores.optString(i,"").trim();if(!v.isEmpty())out.add(v);}return out;}
    public void addLore(String name){String v=name==null?"":name.trim();if(v.isEmpty())return;for(String old:lores())if(old.equalsIgnoreCase(v))return;additionalLores.put(v);}
    public void removeLore(String name){for(int i=0;i<additionalLores.length();i++)if(additionalLores.optString(i,"").equalsIgnoreCase(name)){additionalLores.remove(i);return;}}
    public void trimLanguages(int max){while(languages.length()>Math.max(0,max))languages.remove(languages.length()-1);}
    public void trimLores(int max){while(additionalLores.length()>Math.max(0,max))additionalLores.remove(additionalLores.length()-1);}

    private static boolean eq(String a,String b){return a!=null&&b!=null&&a.equalsIgnoreCase(b);}
    private static void copy(JSONArray from,JSONArray to){if(from==null)return;for(int i=0;i<from.length();i++)to.put(from.opt(i));}
}
