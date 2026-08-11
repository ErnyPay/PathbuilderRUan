package ru.gran.edge2e;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** Gran 6.0 reference-style secondary tools and data management hub. */
public final class ReferenceMoreActivity extends Activity {
    private static final int BG=Color.rgb(232,231,227),TOP=Color.rgb(55,57,59),PANEL=Color.rgb(250,250,248),PANEL2=Color.rgb(241,241,237),BORDER=Color.rgb(188,188,183),TEXT=Color.rgb(36,37,38),MUTED=Color.rgb(103,105,107),ACCENT=Color.rgb(121,31,44),GOOD=Color.rgb(45,125,76),WARM=Color.rgb(174,111,39);
    private RuleStore store; private CharacterState state; private StatsState stats; private KnowledgeState knowledge; private LinearLayout body;

    @Override protected void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(TOP);store=new RuleStore(this);store.getReadableDatabase();load();setContentView(shell());render();}
    @Override protected void onResume(){super.onResume();if(store!=null){load();render();}}
    private void load(){state=CharacterState.load(this);stats=StatsState.load(this);knowledge=KnowledgeState.load(this);KnowledgeRules.sanitize(store,state,stats,knowledge);knowledge.save(this);}

    private View shell(){
        LinearLayout root=column();root.setBackgroundColor(BG);
        LinearLayout top=row();top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(12),dp(9),dp(12),dp(9));top.setBackgroundColor(TOP);
        TextView back=text("‹ НАЗАД",11,true);back.setTextColor(Color.WHITE);back.setPadding(0,dp(5),dp(12),dp(5));back.setOnClickListener(v->finish());top.addView(back);
        TextView title=text("ЕЩЁ / ИНСТРУМЕНТЫ",18,true);title.setTextColor(Color.WHITE);top.addView(title,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        root.addView(top,matchWrap());ScrollView sv=new ScrollView(this);body=column();body.setPadding(dp(10),dp(8),dp(10),dp(30));sv.addView(body);root.addView(sv,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));return root;
    }

    private void render(){if(body==null)return;body.removeAllViews();
        body.addView(section("БИБЛИОТЕКА И ВЫБОРЫ"));LinearLayout library=panel();
        addCatalog(library,"РОДЫ","ancestry");addCatalog(library,"НАСЛЕДИЯ","heritage");addCatalog(library,"ПРЕДЫСТОРИИ","background");addCatalog(library,"КЛАССЫ","class");addCatalog(library,"ФИТЫ","feat");addCatalog(library,"ЗАКЛИНАНИЯ","spell");
        body.addView(library,matchWrap(dp(4)));

        body.addView(section("СНАРЯЖЕНИЕ"));LinearLayout gear=panel();
        addCatalog(gear,"ОРУЖИЕ","weapon");addCatalog(gear,"БРОНЯ","armor");addCatalog(gear,"ЩИТЫ","shield");addCatalog(gear,"ПРЕДМЕТЫ И РАСХОДНИКИ","equipment");addCatalog(gear,"СОСТОЯНИЯ","condition");addCatalog(gear,"ДЕЙСТВИЯ","action");
        body.addView(gear,matchWrap(dp(4)));

        body.addView(section("ЯЗЫКИ И LORE"));body.addView(knowledgePanel(),matchWrap(dp(4)));

        body.addView(section("ПЕРСОНАЖ И ДАННЫЕ"));LinearLayout data=panel();
        addAction(data,"КОПИРОВАТЬ JSON ПЕРСОНАЖА","экспорт в буфер обмена",this::copyJson);
        addAction(data,"ИМПОРТИРОВАТЬ JSON","вставить сохранение Gran",this::importJson);
        addAction(data,"СОХРАНЕНИЯ И ПЕРСОНАЖИ","вернуться к списку персонажей",()->{startActivity(new Intent(this,FrontPageActivity.class));finish();});
        addAction(data,"PF2.RU — ТЕКСТ И ПРАВИЛА","открыть русский справочник",()->{try{startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://pf2.ru")));}catch(Exception e){toast("Не удалось открыть браузер");}});
        addAction(data,"ЛИЦЕНЗИИ И ИСТОЧНИКИ","ORC / OGL / русские данные",this::licenses);
        body.addView(data,matchWrap(dp(4)));
    }

    private View knowledgePanel(){
        LinearLayout p=panel();
        List<String> granted=KnowledgeRules.grantedLanguages(store,state,stats);int slots=KnowledgeRules.languageSlots(store,state,stats);
        p.addView(pair("Языки от правил",granted.isEmpty()?"—":joinLanguageLabels(granted)));
        p.addView(pair("Дополнительные языки",knowledge.languages().size()+" / "+slots));
        for(String slug:knowledge.languages()){
            TextView r=actionRow("✓ "+KnowledgeRules.label(slug),"нажать, чтобы удалить");r.setOnClickListener(v->{knowledge.removeLanguage(slug);knowledge.save(this);render();});p.addView(r);
        }
        TextView addLang=actionRow("+ ДОБАВИТЬ ЯЗЫК","доступные по роду, Интеллекту и фитам");addLang.setOnClickListener(v->chooseLanguage());p.addView(addLang);
        List<KnowledgeRules.Lore> lores=KnowledgeRules.lores(store,state,stats,knowledge);p.addView(divider());p.addView(pair("Lore",String.valueOf(lores.size())));
        for(KnowledgeRules.Lore lore:lores){TextView r=actionRow(lore.name,KnowledgeRules.rankLabel(lore.rank)+" • "+(lore.bonus>=0?"+":"")+lore.bonus+" • "+lore.source);if("Additional Lore".equals(lore.source))r.setOnClickListener(v->{knowledge.removeLore(lore.name);knowledge.save(this);render();});p.addView(r);}
        TextView addLore=actionRow("+ ДОБАВИТЬ LORE","для фита Additional Lore");addLore.setOnClickListener(v->addLore());p.addView(addLore);return p;
    }

    private void chooseLanguage(){
        List<KnowledgeRules.Language> allowed=KnowledgeRules.allowedLanguages(store,state,stats);int slots=KnowledgeRules.languageSlots(store,state,stats);
        if(knowledge.languages().size()>=slots){new AlertDialog.Builder(this).setTitle("Языки").setMessage("Все доступные дополнительные языковые слоты уже заполнены.").setPositiveButton("Закрыть",null).show();return;}
        java.util.ArrayList<KnowledgeRules.Language> open=new java.util.ArrayList<>();for(KnowledgeRules.Language l:allowed)if(!knowledge.hasLanguage(l.slug))open.add(l);
        String[] labels=new String[open.size()];for(int i=0;i<labels.length;i++)labels[i]=open.get(i).label+("uncommon".equals(open.get(i).rarity)?" • необычный":"");
        new AlertDialog.Builder(this).setTitle("ВЫБОР ЯЗЫКА").setItems(labels,(d,w)->{knowledge.addLanguage(open.get(w).slug);knowledge.save(this);render();}).setNegativeButton("Отмена",null).show();
    }

    private void addLore(){
        int max=KnowledgeRules.additionalLoreCount(store,state);if(knowledge.lores().size()>=max){new AlertDialog.Builder(this).setTitle("Lore").setMessage(max==0?"Дополнительный Lore появляется из соответствующих фитов/правил.":"Все доступные слоты Additional Lore уже заполнены.").setPositiveButton("Закрыть",null).show();return;}
        EditText e=input("Название Lore");new AlertDialog.Builder(this).setTitle("ДОБАВИТЬ LORE").setView(e).setPositiveButton("Добавить",(d,w)->{knowledge.addLore(e.getText().toString());knowledge.save(this);render();}).setNegativeButton("Отмена",null).show();
    }

    private void addCatalog(LinearLayout p,String label,String mode){TextView r=actionRow(label,catalogMeta(mode));r.setOnClickListener(v->{Intent i=new Intent(this,ReferenceCatalogActivity.class);i.putExtra("mode",mode);if("spell".equals(mode))i.putExtra("maxLevel",Math.min(10,Math.max(1,(state.level+1)/2)));else i.putExtra("maxLevel",state.level);startActivity(i);});p.addView(r);}
    private String catalogMeta(String mode){switch(mode){case"feat":return store.countCategory("feat")+" записей • фильтры по уровню";case"spell":return store.countCategory("spell")+" заклинаний";case"equipment":return store.countCategory("equipment")+" предметов";case"condition":return store.countCategory("condition")+" состояний";case"action":return store.countCategory("action")+" действий";default:return"открыть специализированный выбор";}}

    private void copyJson(){ClipboardManager c=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);c.setPrimaryClip(ClipData.newPlainText("Gran character",state.toJson().toString()));toast("JSON скопирован");}
    private void importJson(){
        EditText e=input("Вставь JSON персонажа");e.setSingleLine(false);e.setMinLines(8);ClipboardManager c=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);if(c.hasPrimaryClip()&&c.getPrimaryClip()!=null&&c.getPrimaryClip().getItemCount()>0){CharSequence s=c.getPrimaryClip().getItemAt(0).coerceToText(this);if(s!=null)e.setText(s);}
        new AlertDialog.Builder(this).setTitle("ИМПОРТ JSON").setView(e).setPositiveButton("Импортировать",(d,w)->{try{CharacterState imported=CharacterJson.fromString(e.getText().toString());imported.save(this);CharacterProfiles.saveCurrent(this);load();render();toast("Персонаж импортирован");}catch(Exception ex){toast("Некорректный JSON");}}).setNegativeButton("Отмена",null).show();
    }

    private void licenses(){String msg="Игровая механика и открытые данные: ORC/OGL и открытые PF2e-источники.\n\nРусская терминология и тексты: локальный русский слой и PF2.RU-интеграция.\n\nPathbuilder используется как эталон пользовательских сценариев; код Gran и интерфейсная реализация самостоятельные.";new AlertDialog.Builder(this).setTitle("ЛИЦЕНЗИИ И ИСТОЧНИКИ").setMessage(msg).setPositiveButton("Закрыть",null).show();}
    private String joinLanguageLabels(List<String> slugs){java.util.ArrayList<String>x=new java.util.ArrayList<>();for(String s:slugs)x.add(KnowledgeRules.label(s));return String.join(", ",x);}

    private void addAction(LinearLayout p,String title,String meta,Runnable r){TextView v=actionRow(title,meta);v.setOnClickListener(x->r.run());p.addView(v);}
    private TextView actionRow(String left,String right){TextView v=text(left+"\n"+right,14,false);v.setTextColor(TEXT);v.setPadding(dp(10),dp(8),dp(10),dp(8));v.setBackground(round(PANEL2,6,BORDER));LinearLayout.LayoutParams p=matchWrap(dp(2));v.setLayoutParams(p);return v;}
    private View pair(String a,String b){LinearLayout r=row();r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(dp(4),dp(6),dp(4),dp(6));TextView l=text(a,13,true);r.addView(l,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));TextView v=text(b,13,false);v.setTextColor(MUTED);v.setGravity(Gravity.END);v.setMaxWidth(dp(220));r.addView(v);return r;}
    private TextView section(String s){TextView v=text(s,12,true);v.setTextColor(ACCENT);v.setPadding(dp(3),dp(10),dp(3),dp(5));return v;}
    private View divider(){View v=new View(this);v.setBackgroundColor(BORDER);v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(1)));return v;}
    private LinearLayout panel(){LinearLayout l=column();l.setPadding(dp(9),dp(8),dp(9),dp(8));l.setBackground(round(PANEL,8,BORDER));return l;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);return l;}
    private TextView text(String s,int size,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(TEXT);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
    private EditText input(String h){EditText e=new EditText(this);e.setHint(h);e.setHintTextColor(MUTED);e.setTextColor(TEXT);e.setBackground(round(PANEL2,6,BORDER));e.setPadding(dp(10),dp(8),dp(10),dp(8));return e;}
    private GradientDrawable round(int c,int radius,int stroke){GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(dp(radius));g.setStroke(dp(1),stroke);return g;}
    private LinearLayout.LayoutParams matchWrap(){return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);}private LinearLayout.LayoutParams matchWrap(int m){LinearLayout.LayoutParams p=matchWrap();p.setMargins(0,m,0,m);return p;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
}
