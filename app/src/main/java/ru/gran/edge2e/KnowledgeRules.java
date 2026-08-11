package ru.gran.edge2e;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** PF2e language slots/access and Lore projection for BUILD/PLAY. */
public final class KnowledgeRules {
    private KnowledgeRules(){}

    public static final class Language {
        public final String slug, label, rarity;
        Language(String slug,String label,String rarity){this.slug=slug;this.label=label;this.rarity=rarity;}
    }
    public static final class Lore {
        public final String name, source;
        public final int rank, bonus;
        Lore(String name,String source,int rank,int bonus){this.name=name;this.source=source;this.rank=rank;this.bonus=bonus;}
    }

    private static final String[] COMMON={"draconic","dwarven","elven","fey","gnomish","goblin","halfling","jotun","orcish","sakvroth","taldane"};
    private static final String[] UNCOMMON={"adlet","aklo","alghollthu","amurrun","arboreal","boggard","calda","caligni","chthonian","cyclops","daemonic","diabolic","ekujae","empyrean","hallit","iruxi","kelish","kholo","kibwani","kitsune","lirgeni","muan","mwangi","mzunu","nagaji","necril","ocotan","osiriani","petran","protean","pyric","requian","shadowtongue","shoanti","skald","sphinx","sussuran","tang","tengu","thalassic","tien","tripkee","utopian","vanara","varisian","vudrani","xanmba","wayang","ysoki"};
    private static final Map<String,String> RU=new LinkedHashMap<>();
    static{
        RU.put("common","Всеобщий");RU.put("taldane","Талданский");RU.put("draconic","Драконий");RU.put("dwarven","Дварфийский");RU.put("elven","Эльфийский");RU.put("fey","Фейский");RU.put("gnomish","Гномий");RU.put("goblin","Гоблинский");RU.put("halfling","Полуросличий");RU.put("jotun","Йотунский");RU.put("orcish","Орочий");RU.put("sakvroth","Сакврот");
        RU.put("aklo","Акло");RU.put("alghollthu","Алголлту");RU.put("amurrun","Амуррун");RU.put("arboreal","Арбореальный");RU.put("boggard","Боггардский");RU.put("chthonian","Хтонический");RU.put("daemonic","Демонический");RU.put("diabolic","Дьявольский");RU.put("empyrean","Эмпирейский");RU.put("iruxi","Ирукси");RU.put("kelish","Келешский");RU.put("kholo","Холо");RU.put("kitsune","Кицунэ");RU.put("mwangi","Мвангийский");RU.put("nagaji","Нагажи");RU.put("necril","Некрил");RU.put("osiriani","Осирианский");RU.put("petran","Петран");RU.put("protean","Протейский");RU.put("shoanti","Шоантийский");RU.put("tengu","Тэнгу");RU.put("tien","Тяньский");RU.put("varisian","Варисийский");RU.put("vudrani","Вудранский");RU.put("ysoki","Йсоки");
    }

    public static List<String> grantedLanguages(RuleStore store,CharacterState state,StatsState stats){
        LinkedHashSet<String> out=new LinkedHashSet<>();RuleItem ancestry=ancestry(store,state);
        if(ancestry!=null)add(out,ancestry.meta.optJSONArray("languages"));
        RuleRuntime.Snapshot runtime=RuntimeBridge.snapshot(state,stats);
        if(runtime!=null)for(RuleItem item:runtime.allItems())add(out,item.meta.optJSONArray("grantedLanguages"));
        return new ArrayList<>(out);
    }

    public static int languageSlots(RuleStore store,CharacterState state,StatsState stats){
        int slots=0;RuleItem ancestry=ancestry(store,state);if(ancestry!=null)slots+=Math.max(0,ancestry.meta.optInt("additionalLanguages",0));
        slots+=Math.max(0,stats==null?0:stats.ability("int"));
        int multilingual=0;
        java.util.Iterator<String> it=state.choices.keys();
        while(it.hasNext()){
            String key=it.next();RuleItem item=store.findById(state.choiceId(key));if(item==null)continue;
            if("Multilingual".equalsIgnoreCase(item.name))multilingual++;
            else slots+=Math.max(0,item.meta.optInt("languageSlots",0));
        }
        if(multilingual>0){
            slots+=2*multilingual;
            RuleRuntime.Snapshot runtime=RuntimeBridge.snapshot(state,stats);int society=runtime==null?state.rank("society"):runtime.rank(state,"society");
            if(society>=3)slots++;if(society>=4)slots++;
        }
        return Math.max(0,slots);
    }

    public static List<Language> allowedLanguages(RuleStore store,CharacterState state,StatsState stats){
        LinkedHashSet<String> slugs=new LinkedHashSet<>();RuleItem ancestry=ancestry(store,state);
        if(ancestry!=null){JSONArray access=ancestry.meta.optJSONArray("additionalLanguageChoices");if(access!=null&&access.length()>0)add(slugs,access);else slugs.addAll(Arrays.asList(COMMON));}
        else slugs.addAll(Arrays.asList(COMMON));
        if(countSelected(store,state,"Multilingual")>0)slugs.addAll(Arrays.asList(UNCOMMON));
        Set<String> granted=new LinkedHashSet<>(grantedLanguages(store,state,stats));
        if(granted.contains("common"))granted.add("taldane");
        List<Language> out=new ArrayList<>();for(String slug:slugs){if(granted.contains(slug)||"common".equals(slug))continue;out.add(new Language(slug,label(slug),contains(UNCOMMON,slug)?"uncommon":"common"));}
        out.sort((a,b)->a.label.compareToIgnoreCase(b.label));return out;
    }

    public static void sanitize(RuleStore store,CharacterState state,StatsState stats,KnowledgeState knowledge){
        int max=languageSlots(store,state,stats);Set<String> allowed=new LinkedHashSet<>();for(Language l:allowedLanguages(store,state,stats))allowed.add(l.slug);
        for(String current:new ArrayList<>(knowledge.languages()))if(!allowed.contains(current))knowledge.removeLanguage(current);
        knowledge.trimLanguages(max);knowledge.trimLores(additionalLoreCount(store,state));
    }

    public static int additionalLoreCount(RuleStore store,CharacterState state){return countSelected(store,state,"Additional Lore");}

    public static List<Lore> lores(RuleStore store,CharacterState state,StatsState stats,KnowledgeState knowledge){
        List<Lore> out=new ArrayList<>();Set<String> seen=new LinkedHashSet<>();RuleItem bg=state.background.isEmpty()?null:store.findExact("background",state.background);
        if(bg!=null){JSONArray a=bg.meta.optJSONArray("lore");if(a!=null)for(int i=0;i<a.length();i++){String name=a.optString(i,"").trim();if(!name.isEmpty()&&seen.add(name.toLowerCase(Locale.ROOT)))out.add(new Lore(name,RuNames.shortName(bg.name),1,loreBonus(state,stats,1)));}}
        int rank=additionalLoreRank(state.level);for(String name:knowledge.lores())if(seen.add(name.toLowerCase(Locale.ROOT)))out.add(new Lore(name,"Additional Lore",rank,loreBonus(state,stats,rank)));
        out.sort((a,b)->a.name.compareToIgnoreCase(b.name));return out;
    }

    public static int additionalLoreRank(int level){if(level>=15)return 4;if(level>=7)return 3;if(level>=3)return 2;return 1;}
    public static int loreBonus(CharacterState state,StatsState stats,int rank){return (stats==null?0:stats.ability("int"))+DerivedStats.proficiency(rank,state==null?1:state.level);}
    public static String rankLabel(int rank){switch(rank){case 1:return "Обучен";case 2:return "Эксперт";case 3:return "Мастер";case 4:return "Легенда";default:return "Нет";}}
    public static String label(String slug){String s=slug==null?"":slug.toLowerCase(Locale.ROOT);String ru=RU.get(s);if(ru!=null)return ru;String[] p=s.split("-");StringBuilder b=new StringBuilder();for(String x:p){if(x.isEmpty())continue;if(b.length()>0)b.append(' ');b.append(Character.toUpperCase(x.charAt(0))).append(x.substring(1));}return b.toString();}

    private static RuleItem ancestry(RuleStore store,CharacterState state){return state==null||state.ancestry.isEmpty()?null:store.findExact("ancestry",state.ancestry);}
    private static int countSelected(RuleStore store,CharacterState state,String name){int count=0;java.util.Iterator<String>it=state.choices.keys();while(it.hasNext()){RuleItem item=store.findById(state.choiceId(it.next()));if(item!=null&&name.equalsIgnoreCase(item.name))count++;}return count;}
    private static void add(Set<String> out,JSONArray a){if(a==null)return;for(int i=0;i<a.length();i++){String v=a.optString(i,"").trim().toLowerCase(Locale.ROOT);if(!v.isEmpty())out.add(v);}}
    private static boolean contains(String[] array,String value){for(String s:array)if(s.equals(value))return true;return false;}
}
