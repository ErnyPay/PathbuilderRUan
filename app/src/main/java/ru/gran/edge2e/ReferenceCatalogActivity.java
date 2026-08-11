package ru.gran.edge2e;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Gran 6.0 specialized catalog browser. One implementation renders distinct
 * reference-style selectors for ancestry, heritage, background, class, feats,
 * spells, weapons, armor, shields, equipment, runes, conditions and actions.
 */
public final class ReferenceCatalogActivity extends Activity {
    private static final int BG = Color.rgb(232,231,227);
    private static final int TOP = Color.rgb(55,57,59);
    private static final int PANEL = Color.rgb(250,250,248);
    private static final int PANEL_2 = Color.rgb(241,241,237);
    private static final int BORDER = Color.rgb(188,188,183);
    private static final int TEXT = Color.rgb(36,37,38);
    private static final int MUTED = Color.rgb(103,105,107);
    private static final int ACCENT = Color.rgb(121,31,44);
    private static final int GOOD = Color.rgb(45,125,76);
    private static final int BAD = Color.rgb(176,54,54);
    private static final int WARM = Color.rgb(174,111,39);

    private RuleStore store;
    private CharacterState state;
    private StatsState stats;
    private InventoryState inventory;
    private RuleRuntime.Snapshot runtime;
    private String mode;
    private String choiceKey;
    private String slotCategory;
    private String targetItemId;
    private int maxLevel;
    private EditText search;
    private LinearLayout list;
    private TextView status;
    private boolean showLocked;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(TOP);
        store = new RuleStore(this); store.getReadableDatabase();
        state = CharacterState.load(this); stats = StatsState.load(this); inventory = InventoryState.load(this);
        runtime = RuleRuntime.resolve(store,state,stats);
        mode = value("mode","reference");
        choiceKey = value("choiceKey","");
        slotCategory = value("slot","");
        targetItemId = value("targetItemId","");
        maxLevel = getIntent() == null ? state.level : getIntent().getIntExtra("maxLevel", state.level);
        setContentView(shell()); refresh();
    }

    private String value(String key,String fallback){String v=getIntent()==null?null:getIntent().getStringExtra(key);return v==null?fallback:v;}

    private View shell(){
        LinearLayout root=column(); root.setBackgroundColor(BG);
        LinearLayout top=row(); top.setGravity(Gravity.CENTER_VERTICAL); top.setPadding(dp(12),dp(9),dp(12),dp(9)); top.setBackgroundColor(TOP);
        TextView back=text("‹ НАЗАД",11,true); back.setTextColor(Color.WHITE); back.setPadding(0,dp(5),dp(12),dp(5)); back.setOnClickListener(v->finish()); top.addView(back);
        TextView title=text(title(),18,true); title.setTextColor(Color.WHITE); top.addView(title,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        root.addView(top,matchWrap());

        LinearLayout filters=column(); filters.setPadding(dp(10),dp(8),dp(10),dp(6)); filters.setBackgroundColor(PANEL_2);
        search=input(searchHint()); filters.addView(search,matchWrap(dp(2)));
        LinearLayout quick=row();
        Button clear=button("СБРОСИТЬ ПОИСК"); clear.setOnClickListener(v->{search.setText("");refresh();}); quick.addView(clear,weighted(dp(2)));
        if("feat".equals(mode)){
            Button locked=button("НЕДОСТУПНЫЕ: НЕТ"); locked.setOnClickListener(v->{showLocked=!showLocked;locked.setText(showLocked?"НЕДОСТУПНЫЕ: ДА":"НЕДОСТУПНЫЕ: НЕТ");refresh();}); quick.addView(locked,weighted(dp(2)));
        }
        filters.addView(quick,matchWrap(dp(1)));
        status=text("",11,false); status.setTextColor(MUTED); status.setPadding(dp(2),dp(3),dp(2),dp(2)); filters.addView(status);
        root.addView(filters,matchWrap());

        ScrollView scroll=new ScrollView(this); list=column(); list.setPadding(dp(9),dp(7),dp(9),dp(30)); scroll.addView(list); root.addView(scroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){refresh();}public void afterTextChanged(Editable e){}});
        return root;
    }

    private void refresh(){
        if(list==null)return; list.removeAllViews(); runtime=RuleRuntime.resolve(store,state,stats);
        String q=search==null?"":search.getText().toString().trim(); List<Row> rows=new ArrayList<>(); int available=0;
        for(RuleItem item:candidates()){
            if(!matches(item,q))continue;
            String reason=null;
            if("feat".equals(mode)&&!slotCategory.isEmpty()) reason=RuleEngine.blockReason(item,state,runtime,slotCategory,maxLevel);
            if(reason==null)available++;
            if(reason==null||showLocked||!"feat".equals(mode))rows.add(new Row(item,reason));
            if(rows.size()>=450)break;
        }
        Collections.sort(rows,(a,b)->{if((a.reason==null)!=(b.reason==null))return a.reason==null?-1:1;int l=Integer.compare(a.item.level,b.item.level);return l!=0?l:a.item.name.compareToIgnoreCase(b.item.name);});
        for(Row r:rows)list.addView(itemRow(r),matchWrap(dp(3)));
        if(rows.isEmpty()){LinearLayout p=panel();p.addView(text("Ничего не найдено",17,true));p.addView(note(emptyHint()));list.addView(p,matchWrap(dp(4)));}
        status.setText(statusText(rows.size(),available));
    }

    private List<RuleItem> candidates(){
        switch(mode){
            case "ancestry": return store.query("ancestry",20,"",400);
            case "heritage": return store.query("heritage",20,"",800);
            case "background": return store.query("background",20,"",900);
            case "class": return store.query("class",20,"",200);
            case "feat": return featCandidates();
            case "spell": return store.query("spell",Math.max(1,Math.min(10,maxLevel)),"",2600);
            case "weapon": return store.bySubtype("equipment","weapon",20,"",1800);
            case "armor": return store.bySubtype("equipment","armor",20,"",900);
            case "shield": return store.bySubtype("equipment","shield",20,"",500);
            case "equipment": return store.query("equipment",20,"",7000);
            case "condition": return store.query("condition",20,"",300);
            case "action": return store.query("action",20,"",1000);
            case "rune": {
                List<RuleItem> out=new ArrayList<>();
                for(RuleItem item:store.query("equipment",20,"",7000)){
                    String en=item.name.toLowerCase(Locale.ROOT),ru=RuNames.display(item.name).toLowerCase(Locale.ROOT),path=item.meta.optString("sourcePath","").toLowerCase(Locale.ROOT);
                    if(en.contains("rune")||ru.contains("руна")||path.contains("rune"))out.add(item);
                }
                return out;
            }
            default:return store.query("all",20,"",3000);
        }
    }

    private List<RuleItem> featCandidates(){
        List<RuleItem> out=new ArrayList<>(); Set<String> seen=new HashSet<>();
        if(slotCategory.isEmpty()){addAll(out,seen,store.query("feat",Math.max(1,maxLevel),"",6500));return out;}
        if("class".equals(slotCategory)){
            String group=RuleRuntime.slug(state.className);addAll(out,seen,store.queryGroup("feat","class",group,maxLevel,"",900));addAll(out,seen,store.queryGroup("feat","archetype","",maxLevel,"",2600));
        }else if("ancestry".equals(slotCategory)) addAll(out,seen,store.bySubtype("feat","ancestry",maxLevel,"",2200));
        else if("skill".equals(slotCategory)) addAll(out,seen,store.bySubtype("feat","skill",maxLevel,"",1000));
        else if("general".equals(slotCategory)){addAll(out,seen,store.bySubtype("feat","general",maxLevel,"",500));addAll(out,seen,store.bySubtype("feat","skill",maxLevel,"",1000));}
        else addAll(out,seen,store.query("feat",maxLevel,"",6500));
        return out;
    }

    private static void addAll(List<RuleItem> out,Set<String> seen,List<RuleItem> items){for(RuleItem i:items)if(seen.add(i.id))out.add(i);}

    private View itemRow(Row row){
        RuleItem item=row.item; LinearLayout p=panel(); p.setPadding(dp(11),dp(9),dp(11),dp(9));
        LinearLayout head=row(); head.setGravity(Gravity.CENTER_VERTICAL);
        TextView name=text(RuNames.display(item.name),16,true); name.setTextColor(row.reason==null?TEXT:MUTED); head.addView(name,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        if(item.level>0)head.addView(badge("УР. "+item.level,row.reason==null?ACCENT:MUTED)); p.addView(head);
        String meta=meta(item,row.reason); if(!meta.isEmpty())p.addView(note(meta));
        if(selected(item)){TextView yes=text("✓ ВЫБРАНО / ДОБАВЛЕНО",10,true);yes.setTextColor(GOOD);yes.setPadding(0,dp(4),0,0);p.addView(yes);}
        p.setOnClickListener(v->detail(item,row.reason)); return p;
    }

    private String meta(RuleItem item,String reason){
        StringBuilder b=new StringBuilder(); if(reason!=null)b.append("НЕДОСТУПНО: ").append(reason);
        if(!item.subtype.isEmpty()){if(b.length()>0)b.append(" • ");b.append(typeLabel(item.subtype));}
        String rarity=item.meta.optString("rarity","");if(!rarity.isEmpty()&&!"common".equalsIgnoreCase(rarity)){if(b.length()>0)b.append(" • ");b.append(rarity.toUpperCase(Locale.ROOT));}
        if(!item.traits.isEmpty()){if(b.length()>0)b.append("\n");b.append(item.traitsLine());}
        return b.toString();
    }

    private void detail(RuleItem item,String reason){
        StringBuilder body=new StringBuilder();
        if(item.level>0)body.append("Уровень: ").append(item.level).append("\n");
        if(!item.traits.isEmpty())body.append("Черты: ").append(item.traitsLine()).append("\n");
        if(!item.prerequisites.isEmpty())body.append("Требования: ").append(String.join("; ",item.prerequisites)).append("\n");
        if("spell".equals(item.category)){
            JSONArray tr=item.meta.optJSONArray("traditions");if(tr!=null)body.append("Традиции: ").append(join(tr)).append("\n");
            String time=item.meta.optString("time","");if(!time.isEmpty())body.append("Сотворение: ").append(time).append("\n");
            String range=item.meta.optString("range","");if(!range.isEmpty())body.append("Дистанция: ").append(range).append("\n");
        }
        if("equipment".equals(item.category)){
            Object price=item.meta.opt("price");if(price!=null)body.append("Цена: ").append(String.valueOf(price)).append("\n");
            if("weapon".equalsIgnoreCase(item.subtype))body.append("Урон: ").append(item.meta.optInt("damageDice",1)).append(item.meta.optString("damageDie","d4")).append(" ").append(item.meta.optString("damageType","")).append("\n");
            if("armor".equalsIgnoreCase(item.subtype))body.append("КД: +").append(item.meta.optInt("acBonus",0)).append(" • Лимит ЛОВ ").append(item.meta.optInt("dexCap",99)).append("\n");
        }
        if(reason!=null)body.append("\nНедоступно: ").append(reason).append("\n");
        if(!item.source.isEmpty())body.append("Источник: ").append(item.source).append("\n");
        body.append("\n").append(item.description==null?"":item.description);
        AlertDialog.Builder d=new AlertDialog.Builder(this).setTitle(RuNames.display(item.name)).setMessage(body.toString()).setNegativeButton("Закрыть",null);
        if(reason==null&&actionable())d.setPositiveButton(actionLabel(item),(x,w)->apply(item));
        d.show();
    }

    private boolean actionable(){return !"reference".equals(mode)&&!"action".equals(mode);}

    private String actionLabel(RuleItem item){
        if(selected(item)&&("spell".equals(mode)||isEquipmentMode()||"condition".equals(mode)))return "УБРАТЬ";
        if("rune".equals(mode))return "ПРИМЕНИТЬ РУНУ";
        if("spell".equals(mode))return "ДОБАВИТЬ";
        if(isEquipmentMode())return "ДОБАВИТЬ";
        return "ВЫБРАТЬ";
    }

    private void apply(RuleItem item){
        try{
            switch(mode){
                case "ancestry": clearNamed("ancestry",state.ancestry);state.ancestry=item.name;state.setChoice("base:heritage",null);break;
                case "heritage": state.setChoice("base:heritage",item);break;
                case "background": clearNamed("background",state.background);state.background=item.name;break;
                case "class": clearNamed("class",state.className);state.className=item.name;break;
                case "feat": if(!choiceKey.isEmpty())state.setChoice(choiceKey,item);break;
                case "spell": state.toggleArrayItem(state.spells,item);break;
                case "weapon": case "armor": case "shield": case "equipment": state.toggleArrayItem(state.inventory,item);if(state.hasArrayItem(state.inventory,item.id)&&inventory.quantity(item.id)<=0)inventory.setQuantity(item.id,1);if(!state.hasArrayItem(state.inventory,item.id))inventory.remove(item.id);inventory.save(this);break;
                case "condition": if(state.conditions.optInt(item.id,0)>0)state.conditions.remove(item.id);else state.conditions.put(item.id,1);break;
                case "rune": applyRune(item);break;
            }
            state.save(this);stats=StatsState.load(this);runtime=RuleRuntime.resolve(store,state,stats);toast("Сохранено");refresh();
        }catch(Exception e){toast("Не удалось сохранить выбор");}
    }

    private void applyRune(RuleItem rune){
        RuleItem target=store.findById(targetItemId);if(target==null){toast("Сначала открой руны из конкретного оружия или брони");return;}
        String current=ItemMods.propertiesText(this,target);String name=RuNames.shortName(rune.name);if(current.toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT)))return;
        ItemMods.setProperties(this,target.id,current.isEmpty()?name:current+", "+name);
    }

    private boolean selected(RuleItem item){
        if("ancestry".equals(mode))return item.name.equalsIgnoreCase(state.ancestry);
        if("heritage".equals(mode))return item.id.equals(state.choiceId("base:heritage"));
        if("background".equals(mode))return item.name.equalsIgnoreCase(state.background);
        if("class".equals(mode))return item.name.equalsIgnoreCase(state.className);
        if("feat".equals(mode)&&!choiceKey.isEmpty())return item.id.equals(state.choiceId(choiceKey));
        if("spell".equals(mode))return state.hasArrayItem(state.spells,item.id);
        if(isEquipmentMode())return state.hasArrayItem(state.inventory,item.id);
        if("condition".equals(mode))return state.conditions.optInt(item.id,0)>0;
        return false;
    }

    private boolean isEquipmentMode(){return "weapon".equals(mode)||"armor".equals(mode)||"shield".equals(mode)||"equipment".equals(mode);}
    private void clearNamed(String category,String name){if(name==null||name.isEmpty())return;RuleItem old=store.findExact(category,name);if(old!=null)state.clearRuleSelectionsFor(old.id);}

    private boolean matches(RuleItem item,String q){
        if(q==null||q.isEmpty())return true;String s=q.toLowerCase(Locale.ROOT);
        if(RuNames.matches(item.name,q))return true;
        if(item.description!=null&&item.description.toLowerCase(Locale.ROOT).contains(s))return true;
        String ru=item.meta.optString("ruDescription","");if(ru.toLowerCase(Locale.ROOT).contains(s))return true;
        for(String t:item.traits)if(t.toLowerCase(Locale.ROOT).contains(s))return true;
        return false;
    }

    private String title(){switch(mode){case"ancestry":return"РОД";case"heritage":return"НАСЛЕДИЕ";case"background":return"ПРЕДЫСТОРИЯ";case"class":return"КЛАСС";case"feat":return"ФИТЫ";case"spell":return"ЗАКЛИНАНИЯ";case"weapon":return"ОРУЖИЕ";case"armor":return"БРОНЯ";case"shield":return"ЩИТЫ";case"equipment":return"СНАРЯЖЕНИЕ";case"rune":return"РУНЫ";case"condition":return"СОСТОЯНИЯ";case"action":return"ДЕЙСТВИЯ";default:return"СПРАВОЧНИК";}}
    private String searchHint(){return"Поиск: "+title().toLowerCase(Locale.ROOT)+" — русский / английский";}
    private String emptyHint(){if("heritage".equals(mode)&&state.ancestry.isEmpty())return"Сначала выбери род.";return"Измени запрос или фильтр.";}
    private String statusText(int shown,int available){String base="Показано: "+shown;if("feat".equals(mode))base+=" • доступно: "+available;if("heritage".equals(mode)&&!state.ancestry.isEmpty())base+=" • род: "+RuNames.shortName(state.ancestry);return base;}
    private String typeLabel(String s){switch(s.toLowerCase(Locale.ROOT)){case"weapon":return"оружие";case"armor":return"броня";case"shield":return"щит";case"consumable":return"расходник";case"ammo":return"боеприпасы";case"archetype":return"архетип";case"ancestry":return"род";case"class":return"класс";case"skill":return"навык";case"general":return"общий";default:return s;}}
    private static String join(JSONArray a){List<String>x=new ArrayList<>();for(int i=0;i<a.length();i++){String s=a.optString(i,"");if(!s.isEmpty())x.add(s);}return String.join(", ",x);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}

    private static final class Row{final RuleItem item;final String reason;Row(RuleItem i,String r){item=i;reason=r;}}
    private LinearLayout panel(){LinearLayout l=column();l.setPadding(dp(10),dp(9),dp(10),dp(9));l.setBackground(round(PANEL,8,BORDER));return l;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);return l;}
    private TextView text(String s,int size,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(TEXT);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
    private TextView note(String s){TextView v=text(s,12,false);v.setTextColor(MUTED);v.setPadding(0,dp(4),0,0);return v;}
    private TextView badge(String s,int color){TextView v=text(s,10,true);v.setTextColor(Color.WHITE);v.setPadding(dp(7),dp(4),dp(7),dp(4));v.setGravity(Gravity.CENTER);v.setBackground(round(color,12,color));return v;}
    private EditText input(String hint){EditText e=new EditText(this);e.setHint(hint);e.setHintTextColor(MUTED);e.setTextColor(TEXT);e.setSingleLine(true);e.setBackground(round(PANEL,7,BORDER));e.setPadding(dp(10),dp(8),dp(10),dp(8));return e;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(11);b.setTextColor(ACCENT);b.setBackground(round(PANEL,7,BORDER));return b;}
    private GradientDrawable round(int color,int radius,int stroke){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));g.setStroke(dp(1),stroke);return g;}
    private LinearLayout.LayoutParams matchWrap(){return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);}
    private LinearLayout.LayoutParams matchWrap(int m){LinearLayout.LayoutParams p=matchWrap();p.setMargins(0,m,0,m);return p;}
    private LinearLayout.LayoutParams weighted(int m){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1);p.setMargins(m,m,m,m);return p;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
