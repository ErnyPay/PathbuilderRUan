package ru.gran.edge2e;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Gran 6.0 reference-style equipment details, container placement and rune editing. */
public final class ReferenceItemActivity extends Activity {
    private static final int BG=Color.rgb(232,231,227),TOP=Color.rgb(55,57,59),PANEL=Color.rgb(250,250,248),PANEL2=Color.rgb(241,241,237),BORDER=Color.rgb(188,188,183),TEXT=Color.rgb(36,37,38),MUTED=Color.rgb(103,105,107),ACCENT=Color.rgb(121,31,44),GOOD=Color.rgb(45,125,76),BAD=Color.rgb(176,54,54),WARM=Color.rgb(174,111,39);
    private RuleStore store;private CharacterState state;private StatsState stats;private InventoryState inventory;private RuleItem item;private LinearLayout body;

    @Override protected void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(TOP);store=new RuleStore(this);store.getReadableDatabase();load();setContentView(shell());render();}
    @Override protected void onResume(){super.onResume();if(store!=null){load();render();}}
    private void load(){state=CharacterState.load(this);stats=StatsState.load(this);inventory=InventoryState.load(this);String id=getIntent()==null?"":getIntent().getStringExtra("itemId");item=store.findById(id==null?"":id);}

    private View shell(){LinearLayout root=column();root.setBackgroundColor(BG);LinearLayout top=row();top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(12),dp(9),dp(12),dp(9));top.setBackgroundColor(TOP);TextView back=text("‹ СНАРЯЖЕНИЕ",11,true);back.setTextColor(Color.WHITE);back.setPadding(0,dp(5),dp(12),dp(5));back.setOnClickListener(v->finish());top.addView(back);TextView title=text("ПРЕДМЕТ",18,true);title.setTextColor(Color.WHITE);top.addView(title,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));root.addView(top,matchWrap());ScrollView sv=new ScrollView(this);body=column();body.setPadding(dp(10),dp(8),dp(10),dp(30));sv.addView(body);root.addView(sv,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));return root;}

    private void render(){if(body==null)return;body.removeAllViews();if(item==null){body.addView(panelMessage("Предмет не найден."));return;}
        LinearLayout head=panel();TextView name=text(RuNames.display(item.name),22,true);name.setTextColor(ACCENT);head.addView(name);head.addView(note(item.subtype+(item.level>0?" • ур. "+item.level:"")+(item.traits.isEmpty()?"":"\n"+item.traitsLine())));body.addView(head,matchWrap(dp(4)));

        body.addView(section("СОСТОЯНИЕ ПРЕДМЕТА"));LinearLayout statePanel=panel();statePanel.addView(stepper("Количество",inventory.quantity(item.id),1,9999,v->{inventory.setQuantity(item.id,v);inventory.save(this);}));
        if(BulkRules.isContainer(item)){TextView worn=actionRow("Использование контейнера",inventory.isContainerWorn(item.id)?"НАДЕТ / ИСПОЛЬЗУЕТСЯ":"НЕ НАДЕТ");worn.setOnClickListener(v->{inventory.setContainerWorn(item.id,!inventory.isContainerWorn(item.id));inventory.save(this);render();});statePanel.addView(worn);}
        TextView container=actionRow("Расположение",containerLabel());container.setOnClickListener(v->chooseContainer());statePanel.addView(container);body.addView(statePanel,matchWrap(dp(4)));

        if("weapon".equalsIgnoreCase(item.subtype)){body.addView(section("ОРУЖИЕ"));LinearLayout weapon=panel();weapon.addView(pair("Атака",signed(DerivedStats.attack(state,stats,classItem(),item))));weapon.addView(pair("Урон",DerivedStats.damage(stats,item)));weapon.addView(pair("Группа",item.meta.optString("group","—")));weapon.addView(pair("Дистанция",String.valueOf(item.meta.opt("range"))));body.addView(weapon,matchWrap(dp(4)));body.addView(runesPanel(true),matchWrap(dp(4)));}
        else if("armor".equalsIgnoreCase(item.subtype)){body.addView(section("БРОНЯ"));LinearLayout armor=panel();armor.addView(pair("Бонус КД",signed(item.meta.optInt("acBonus",0))));armor.addView(pair("Лимит Ловкости",String.valueOf(item.meta.optInt("dexCap",99))));armor.addView(pair("Штраф проверки",String.valueOf(item.meta.optInt("checkPenalty",0))));armor.addView(pair("Штраф скорости",String.valueOf(item.meta.optInt("speedPenalty",0))));body.addView(armor,matchWrap(dp(4)));body.addView(runesPanel(false),matchWrap(dp(4)));}
        else if("shield".equalsIgnoreCase(item.subtype)){body.addView(section("ЩИТ"));LinearLayout shield=panel();shield.addView(pair("Бонус КД",signed(item.meta.optInt("acBonus",0))));shield.addView(pair("Твёрдость",String.valueOf(item.meta.optInt("hardness",0))));Object hp=item.meta.opt("hp");shield.addView(pair("ОЗ",String.valueOf(hp)));body.addView(shield,matchWrap(dp(4)));}

        body.addView(section("ПРАВИЛА"));LinearLayout info=panel();if(!item.source.isEmpty())info.addView(pair("Источник",item.source));if(item.description!=null&&!item.description.isEmpty())info.addView(note(item.description));body.addView(info,matchWrap(dp(4)));
        Button remove=button("УДАЛИТЬ ИЗ ИНВЕНТАРЯ");remove.setTextColor(BAD);remove.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Удалить предмет?").setMessage(RuNames.display(item.name)).setNegativeButton("Отмена",null).setPositiveButton("Удалить",(d,w)->{removeItem();finish();}).show());body.addView(remove,matchWrap(dp(8)));
    }

    private View runesPanel(boolean weapon){LinearLayout p=panel();body.addView(section("РУНЫ"));int potency=ItemMods.potency(this,item),striking=ItemMods.striking(this,item);p.addView(stepper("Руна мощи",potency,0,4,v->{ItemMods.setPotency(this,item.id,v);StatsState.recalculate(state);}));if(weapon)p.addView(stepper("Разящая руна",striking,0,3,v->{ItemMods.setStriking(this,item.id,v);StatsState.recalculate(state);}));p.addView(pair("Руны свойств",ItemMods.propertiesText(this,item).isEmpty()?"—":ItemMods.propertiesText(this,item)));TextView add=actionRow("+ ВЫБРАТЬ РУНУ СВОЙСТВА","открыть каталог рун");add.setOnClickListener(v->{Intent i=new Intent(this,ReferenceCatalogActivity.class);i.putExtra("mode","rune");i.putExtra("targetItemId",item.id);i.putExtra("maxLevel",state.level);startActivity(i);});p.addView(add);TextView clear=actionRow("ОЧИСТИТЬ РУНЫ СВОЙСТВ","оставить фундаментальные");clear.setOnClickListener(v->{ItemMods.setProperties(this,item.id,"");render();});p.addView(clear);return p;}

    private void chooseContainer(){List<RuleItem> containers=new ArrayList<>();for(int i=0;i<state.inventory.length();i++){String raw=state.inventory.optString(i,"");String id=storedId(raw);RuleItem x=store.findById(id);if(x!=null&&BulkRules.isContainer(x)&&!x.id.equals(item.id))containers.add(x);}String[] labels=new String[containers.size()+1];labels[0]="Нести напрямую";for(int i=0;i<containers.size();i++)labels[i+1]=RuNames.display(containers.get(i).name);new AlertDialog.Builder(this).setTitle("РАСПОЛОЖЕНИЕ ПРЕДМЕТА").setItems(labels,(d,w)->{inventory.assignContainer(item.id,w==0?"":containers.get(w-1).id);inventory.save(this);render();}).setNegativeButton("Отмена",null).show();}
    private String containerLabel(){String id=inventory.containerFor(item.id);if(id.isEmpty())return"несётся напрямую";RuleItem c=store.findById(id);return c==null?"контейнер":RuNames.display(c.name);}
    private void removeItem(){for(int i=0;i<state.inventory.length();i++){String raw=state.inventory.optString(i,"");if(storedId(raw).equals(item.id)){state.inventory.remove(i);break;}}inventory.remove(item.id);inventory.save(this);ItemMods.clear(this,item.id);state.save(this);}
    private RuleItem classItem(){return state.className.isEmpty()?null:store.findExact("class",state.className);}private static String storedId(String raw){int i=raw==null?-1:raw.indexOf('\u001f');return i>=0?raw.substring(0,i):raw;}

    private View stepper(String label,int value,int min,int max,IntSetter setter){LinearLayout r=row();r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(dp(4),dp(5),dp(4),dp(5));TextView l=text(label,14,true);r.addView(l,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));Button minus=smallButton("−");TextView val=text(String.valueOf(value),17,true);val.setGravity(Gravity.CENTER);val.setMinWidth(dp(48));Button plus=smallButton("+");minus.setOnClickListener(v->{int n=Math.max(min,value-1);setter.set(n);render();});plus.setOnClickListener(v->{int n=Math.min(max,value+1);setter.set(n);render();});r.addView(minus);r.addView(val);r.addView(plus);return r;}
    private interface IntSetter{void set(int value);}
    private View pair(String a,String b){LinearLayout r=row();r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(dp(4),dp(6),dp(4),dp(6));TextView l=text(a,13,true);r.addView(l,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));TextView v=text(b,13,false);v.setTextColor(MUTED);v.setGravity(Gravity.END);v.setMaxWidth(dp(220));r.addView(v);return r;}
    private TextView actionRow(String a,String b){TextView v=text(a+"\n"+b,14,false);v.setPadding(dp(10),dp(8),dp(10),dp(8));v.setBackground(round(PANEL2,6,BORDER));LinearLayout.LayoutParams p=matchWrap(dp(2));v.setLayoutParams(p);return v;}private TextView section(String s){TextView v=text(s,12,true);v.setTextColor(ACCENT);v.setPadding(dp(3),dp(10),dp(3),dp(5));return v;}
    private View panelMessage(String s){LinearLayout p=panel();p.addView(text(s,17,true));return p;}private LinearLayout panel(){LinearLayout l=column();l.setPadding(dp(9),dp(8),dp(9),dp(8));l.setBackground(round(PANEL,8,BORDER));return l;}private TextView note(String s){TextView v=text(s,12,false);v.setTextColor(MUTED);v.setPadding(0,dp(4),0,0);return v;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);return l;}private TextView text(String s,int size,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(TEXT);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(12);b.setTextColor(ACCENT);b.setBackground(round(PANEL2,7,BORDER));return b;}private Button smallButton(String s){Button b=button(s);b.setTextSize(17);b.setMinWidth(dp(46));b.setMinimumHeight(0);b.setMinHeight(dp(38));return b;}
    private GradientDrawable round(int c,int radius,int stroke){GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(dp(radius));g.setStroke(dp(1),stroke);return g;}private LinearLayout.LayoutParams matchWrap(){return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);}private LinearLayout.LayoutParams matchWrap(int m){LinearLayout.LayoutParams p=matchWrap();p.setMargins(0,m,0,m);return p;}private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}private static String signed(int v){return(v>=0?"+":"")+v;}private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
}
