#!/usr/bin/env python3
from pathlib import Path
import re
ROOT=Path(__file__).resolve().parents[1]
BUILD=ROOT/'app/src/main/java/ru/gran/edge2e/ReferenceBuildActivity.java'
GRADLE=ROOT/'app/build.gradle'

def method(path,marker,new):
 s=path.read_text(encoding='utf-8');start=s.find(marker)
 if start<0: raise SystemExit(f'9.1 method not found: {marker}')
 brace=s.find('{',start);depth=0;ins=inc=esc=False;end=-1
 for i in range(brace,len(s)):
  c=s[i]
  if esc: esc=False;continue
  if c=='\\' and (ins or inc): esc=True;continue
  if c=='"' and not inc: ins=not ins;continue
  if c=="'" and not ins: inc=not inc;continue
  if ins or inc: continue
  if c=='{': depth+=1
  elif c=='}':
   depth-=1
   if depth==0: end=i+1;break
 if end<0: raise SystemExit(f'9.1 closing brace not found: {marker}')
 path.write_text(s[:start]+new.strip()+s[end:],encoding='utf-8')

def insert_before(path,marker,addition):
 s=path.read_text(encoding='utf-8');p=s.find(marker)
 if p<0: raise SystemExit(f'9.1 insertion marker not found: {marker}')
 path.write_text(s[:p]+addition.strip()+'\n\n'+s[p:],encoding='utf-8')

s=BUILD.read_text(encoding='utf-8')
if 'private String section = "levels";' in s:s=s.replace('private String section = "levels";','private String section = "base";')
BUILD.write_text(s,encoding='utf-8')

method(BUILD,'private View shell()',r'''private View shell(){
 LinearLayout root=column();root.setBackgroundColor(BG);LinearLayout top=row();top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(7),dp(4),dp(7),dp(4));top.setBackgroundColor(TOP);
 TextView menu=text("☰",20,true);menu.setTextColor(Color.WHITE);menu.setGravity(Gravity.CENTER);menu.setPadding(dp(8),dp(5),dp(12),dp(5));menu.setContentDescription("build-menu");menu.setOnClickListener(v->showBuildDrawer());top.addView(menu);
 LinearLayout id=column();headerName=text("",17,true);headerName.setTextColor(Color.WHITE);headerName.setContentDescription("build-name");headerName.setOnClickListener(v->editName());id.addView(headerName);headerStats=text("",9,false);headerStats.setTextColor(Color.rgb(204,205,206));id.addView(headerStats);top.addView(id,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
 TextView play=text("ИГРА",10,true);play.setTextColor(Color.rgb(236,205,169));play.setPadding(dp(10),dp(7),dp(8),dp(7));play.setContentDescription("build-play");play.setOnClickListener(v->{Intent i=new Intent(this,ReferencePlayActivity.class);i.putExtra("screen","character");startActivity(i);});top.addView(play);
 TextView more=text("⋮",20,true);more.setTextColor(Color.WHITE);more.setGravity(Gravity.CENTER);more.setPadding(dp(8),dp(3),dp(5),dp(3));more.setContentDescription("build-more");more.setOnClickListener(v->startActivity(new Intent(this,ReferenceMoreActivity.class)));top.addView(more);root.addView(top,matchWrap());
 HorizontalScrollView modes=new HorizontalScrollView(this);modes.setHorizontalScrollBarEnabled(false);modes.setBackgroundColor(TOP_2);modeNav=row();modeNav.setPadding(dp(3),dp(2),dp(3),dp(2));String[][] tabs={{"ОСНОВА","base"},{"ХАРАКТЕРИСТИКИ","abilities"},{"НАВЫКИ","skills"},{"ФИТЫ","feats"},{"ЗАКЛИНАНИЯ","spells"},{"СНАРЯЖЕНИЕ","gear"},{"УРОВНИ","levels"}};for(String[] spec:tabs){TextView t=modeTab(spec[0],spec[1].equals(section));String target=spec[1];t.setOnClickListener(v->{section=target;render();});modeNav.addView(t,wrapWrap(dp(1)));}modes.addView(modeNav);root.addView(modes,matchWrap());
 HorizontalScrollView levelScroll=new HorizontalScrollView(this);levelScroll.setHorizontalScrollBarEnabled(false);levelScroll.setBackgroundColor(PANEL_2);levelNav=row();levelNav.setPadding(dp(4),dp(3),dp(4),dp(3));levelScroll.addView(levelNav);root.addView(levelScroll,matchWrap());content=new FrameLayout(this);root.addView(content,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));return root;
}''')

method(BUILD,'private void render()',r'''private void render(){
 if(content==null)return;stats=StatsState.load(this);runtime=RuleRuntime.resolve(store,state,stats);headerName.setText(state.name==null||state.name.trim().isEmpty()?"Новый герой":state.name);String cls=state.className.isEmpty()?"класс не выбран":RuNames.shortName(state.className);int[] done=completion();headerStats.setText("СБОРКА • ур. "+state.level+" • "+cls+" • "+done[0]+"/"+done[1]);rebuildLevelNavigation();content.removeAllViews();View page;switch(section){case "base":page=baseBuildPage();break;case "abilities":page=abilitiesBuildPage();break;case "skills":page=skillsPage();break;case "feats":page=featsBuildPage();break;case "spells":page=spellsBuildPage();break;case "gear":page=gearBuildPage();break;default:page=levelPage(selectedLevel);}content.addView(scroll(page));refreshModeTabs();
}''')

method(BUILD,'private void refreshModeTabs()',r'''private void refreshModeTabs(){
 if(modeNav==null)return;modeNav.removeAllViews();String[][] tabs={{"ОСНОВА","base"},{"ХАРАКТЕРИСТИКИ","abilities"},{"НАВЫКИ","skills"},{"ФИТЫ","feats"},{"ЗАКЛИНАНИЯ","spells"},{"СНАРЯЖЕНИЕ","gear"},{"УРОВНИ","levels"}};for(String[] spec:tabs){TextView t=modeTab(spec[0],spec[1].equals(section));String target=spec[1];t.setOnClickListener(v->{section=target;render();});modeNav.addView(t,wrapWrap(dp(1)));}
}''')

method(BUILD,'private void showBuildDrawer()',r'''private void showBuildDrawer(){
 String[] items={"Основа персонажа","Характеристики","Навыки","Фиты","Заклинания","Снаряжение","Уровни 1–20","Лист персонажа","Персонажи","Ещё"};new AlertDialog.Builder(this).setTitle(state.name==null||state.name.trim().isEmpty()?"Новый герой":state.name).setItems(items,(d,which)->{String[] target={"base","abilities","skills","feats","spells","gear","levels"};if(which<target.length){section=target[which];render();return;}if(which==7){startActivity(new Intent(this,ReferencePlayActivity.class));return;}if(which==8){startActivity(new Intent(this,FrontPageActivity.class));finish();return;}startActivity(new Intent(this,ReferenceMoreActivity.class));}).show();
}''')

insert_before(BUILD,'private LinearLayout levelPage(',r'''private LinearLayout baseBuildPage(){
 LinearLayout col=page();col.addView(sectionTitle("ОСНОВА ПЕРСОНАЖА"));LinearLayout identity=panel();TextView name=actionRow("ИМЯ",state.name==null||state.name.trim().isEmpty()?"ВВЕСТИ ИМЯ":state.name);name.setOnClickListener(v->editName());identity.addView(name);LinearLayout r1=row();r1.addView(baseChoice("РОД",state.ancestry,"ancestry"),weighted(dp(1)));r1.addView(baseChoice("НАСЛЕДИЕ",state.choiceName("base:heritage"),"heritage"),weighted(dp(1)));identity.addView(r1);LinearLayout r2=row();r2.addView(baseChoice("ПРЕДЫСТОРИЯ",state.background,"background"),weighted(dp(1)));r2.addView(baseChoice("КЛАСС",state.className,"class"),weighted(dp(1)));identity.addView(r2);col.addView(identity,matchWrap(dp(4)));List<RuleRuntime.ChoicePrompt> prompts=runtime.choices();if(!prompts.isEmpty()){col.addView(sectionTitle("КЛЮЧЕВЫЕ ВЫБОРЫ"));LinearLayout p=panel();int shown=0;for(RuleRuntime.ChoicePrompt prompt:prompts){p.addView(ruleChoiceRow(prompt));if(++shown>=16)break;}col.addView(p,matchWrap(dp(4)));}return col;
}
private LinearLayout abilitiesBuildPage(){
 LinearLayout col=page();col.addView(sectionTitle("ХАРАКТЕРИСТИКИ"));LinearLayout abilities=row();for(String[] a:ABILITIES)abilities.addView(abilityCell(a[1],stats.abilityScore(a[0]),stats.ability(a[0])),weighted(dp(2)));col.addView(abilities,matchWrap(dp(4)));LinearLayout p=panel();p.addView(note("Итоговые значения учитывают род, предысторию, класс, повышения уровней и выбранные правила."));TextView edit=actionRow("ПОВЫШЕНИЯ ХАРАКТЕРИСТИК","открыть доступные выборы");edit.setOnClickListener(v->showAbilityPrompts());p.addView(edit);col.addView(p,matchWrap(dp(4)));return col;
}
private LinearLayout featsBuildPage(){
 LinearLayout col=page();col.addView(sectionTitle("ФИТЫ • УРОВЕНЬ "+selectedLevel));LinearLayout p=panel();int rows=0;RuleItem cls=classItem();for(RuleItem item:runtime.allItems())if(runtime.isAutomatic(item.id)&&runtime.automaticLevel(item.id)==selectedLevel){TextView auto=actionRow("✓  "+RuNames.shortName(item.name),"особенность уровня");auto.setOnClickListener(v->ruleDetail(item,null));p.addView(auto);rows++;}if(hasFeatSlot(cls,"class",selectedLevel)){p.addView(featSlot(selectedLevel,"КЛАССОВЫЙ / АРХЕТИПНЫЙ ФИТ","class"));rows++;}if(hasFeatSlot(cls,"ancestry",selectedLevel)){p.addView(featSlot(selectedLevel,"ФИТ РОДА","ancestry"));rows++;}if(hasFeatSlot(cls,"skill",selectedLevel)){p.addView(featSlot(selectedLevel,"ФИТ НАВЫКА","skill"));rows++;}if(hasFeatSlot(cls,"general",selectedLevel)){p.addView(featSlot(selectedLevel,"ОБЩИЙ ФИТ","general"));rows++;}if(rows==0)p.addView(note("На выбранном уровне нет отдельного слота фита."));col.addView(p,matchWrap(dp(4)));return col;
}
private LinearLayout spellsBuildPage(){
 LinearLayout col=page();col.addView(sectionTitle("ЗАКЛИНАНИЯ"));SpellcastingRules.Profile profile=SpellcastingRules.resolve(state,runtime);if(profile==null){LinearLayout p=panel();p.addView(text("Источник заклинаний не выбран",16,true));p.addView(note("Выбери класс или особенность с заклинаниями."));col.addView(p,matchWrap(dp(4)));return col;}RuleItem cls=classItem();int atk=DerivedStats.spellAttack(state,stats,cls);LinearLayout head=panel();LinearLayout metrics=row();metrics.addView(actionRow("КС",String.valueOf(DerivedStats.spellDc(state,stats,cls))),weighted(dp(1)));metrics.addView(actionRow("АТАКА",(atk>=0?"+":"")+atk),weighted(dp(1)));metrics.addView(actionRow("ТРАДИЦИЯ",SpellcastingRules.traditionLabel(profile.tradition)),weighted(dp(1)));head.addView(metrics);head.addView(note(RuNames.shortName(profile.source)+" • "+SpellcastingRules.modeLabel(profile)));col.addView(head,matchWrap(dp(4)));col.addView(sectionTitle("РАНГИ"));LinearLayout ranks=panel();for(int rank=1;rank<=profile.maxRank(state.level);rank++){int slots=SpellcastingRules.PREPARED.equals(profile.mode)?profile.totalPreparedSlots(state.level,rank):profile.slots(state.level,rank);if(slots<=0)continue;ranks.addView(actionRow("РАНГ "+rank,slots+" сл."));}col.addView(ranks,matchWrap(dp(4)));LinearLayout actions=panel();TextView prepare=actionRow("ПОДГОТОВКА / РЕПЕРТУАР","открыть рабочий экран заклинаний");prepare.setOnClickListener(v->{Intent i=new Intent(this,ReferencePlayActivity.class);i.putExtra("screen","spells");startActivity(i);});actions.addView(prepare);TextView catalog=actionRow("КАТАЛОГ ЗАКЛИНАНИЙ","поиск по локальной базе");catalog.setOnClickListener(v->{Intent i=new Intent(this,ReferenceCatalogActivity.class);i.putExtra("mode","spell");i.putExtra("maxLevel",Math.max(1,profile.maxRank(state.level)));startActivity(i);});actions.addView(catalog);col.addView(actions,matchWrap(dp(4)));return col;
}
private LinearLayout gearBuildPage(){
 LinearLayout col=page();col.addView(sectionTitle("СНАРЯЖЕНИЕ"));InventoryState inv=InventoryState.load(this);int weapons=0,armor=0,shields=0,other=0;for(RuleItem item:store.query("equipment",20,"",7000)){if(!state.hasArrayItem(state.inventory,item.id))continue;if("weapon".equalsIgnoreCase(item.subtype))weapons++;else if("armor".equalsIgnoreCase(item.subtype))armor++;else if("shield".equalsIgnoreCase(item.subtype))shields++;else other++;}LinearLayout summary=panel();LinearLayout counts=row();counts.addView(actionRow("ОРУЖИЕ",String.valueOf(weapons)),weighted(dp(1)));counts.addView(actionRow("БРОНЯ",String.valueOf(armor)),weighted(dp(1)));counts.addView(actionRow("ЩИТЫ",String.valueOf(shields)),weighted(dp(1)));counts.addView(actionRow("ПРОЧЕЕ",String.valueOf(other)),weighted(dp(1)));summary.addView(counts);col.addView(summary,matchWrap(dp(4)));String[][] groups={{"ОРУЖИЕ","weapon"},{"БРОНЯ","armor"},{"ЩИТЫ","shield"},{"ПРЕДМЕТЫ","equipment"},{"РУНЫ","rune"}};for(String[] g:groups){LinearLayout p=panel();TextView r=actionRow(g[0],"открыть каталог");String mode=g[1];r.setOnClickListener(v->{Intent i=new Intent(this,ReferenceCatalogActivity.class);i.putExtra("mode",mode);i.putExtra("maxLevel",state.level);startActivity(i);});p.addView(r);col.addView(p,matchWrap(dp(2)));}LinearLayout open=panel();TextView play=actionRow("ИНВЕНТАРЬ ПЕРСОНАЖА",inv.gp+" зм • открыть управление");play.setOnClickListener(v->{Intent i=new Intent(this,ReferencePlayActivity.class);i.putExtra("screen","gear");startActivity(i);});open.addView(play);col.addView(open,matchWrap(dp(4)));return col;
}''')

s=GRADLE.read_text(encoding='utf-8');s=re.sub(r"versionCode\s+\d+","versionCode 910",s);s=re.sub(r"versionName\s+'[^']+'","versionName '9.1.0'",s);GRADLE.write_text(s,encoding='utf-8')
print('Gran 2e 9.1 category BUILD applied')
