#!/usr/bin/env python3
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/'app/src/main/java/ru/gran/edge2e'
PLAY=JAVA/'ReferencePlayActivity.java'
BUILD=JAVA/'ReferenceBuildActivity.java'
FRONT=JAVA/'FrontPageActivity.java'
SETUP=JAVA/'CharacterSetupActivity.java'

def replace(path,old,new,required=True):
    s=path.read_text(encoding='utf-8')
    if old not in s:
        if required: raise SystemExit(f'UI target not found: {path.name}: {old[:80]}')
        return
    path.write_text(s.replace(old,new),encoding='utf-8')

def method(path,marker,new):
    s=path.read_text(encoding='utf-8'); start=s.find(marker)
    if start<0: raise SystemExit(f'UI method not found: {path.name}: {marker}')
    brace=s.find('{',start); depth=0; ins=False; inc=False; esc=False; end=-1
    for i in range(brace,len(s)):
        c=s[i]
        if esc: esc=False; continue
        if c=='\\' and (ins or inc): esc=True; continue
        if c=='"' and not inc: ins=not ins; continue
        if c=="'" and not ins: inc=not inc; continue
        if ins or inc: continue
        if c=='{': depth+=1
        elif c=='}':
            depth-=1
            if depth==0: end=i+1; break
    if end<0: raise SystemExit(f'UI closing brace not found: {path.name}: {marker}')
    path.write_text(s[:start]+new.strip()+s[end:],encoding='utf-8')

def recolor(path):
    s=path.read_text(encoding='utf-8')
    for a,b in {
      'Color.rgb(232, 231, 227)':'Color.rgb(244, 242, 236)','Color.rgb(232,231,227)':'Color.rgb(244,242,236)',
      'Color.rgb(238, 236, 231)':'Color.rgb(244, 242, 236)','Color.rgb(238,236,231)':'Color.rgb(244,242,236)',
      'Color.rgb(55, 57, 59)':'Color.rgb(42, 43, 44)','Color.rgb(55,57,59)':'Color.rgb(42,43,44)',
      'Color.rgb(48, 50, 52)':'Color.rgb(42, 43, 44)','Color.rgb(48,50,52)':'Color.rgb(42,43,44)',
      'Color.rgb(72, 74, 76)':'Color.rgb(54, 55, 56)','Color.rgb(72,74,76)':'Color.rgb(54,55,56)',
      'Color.rgb(62, 64, 66)':'Color.rgb(54, 55, 56)','Color.rgb(62,64,66)':'Color.rgb(54,55,56)',
      'Color.rgb(250, 250, 248)':'Color.rgb(255, 254, 250)','Color.rgb(250,250,248)':'Color.rgb(255,254,250)',
      'Color.rgb(251, 250, 247)':'Color.rgb(255, 254, 250)','Color.rgb(251,250,247)':'Color.rgb(255,254,250)',
      'Color.rgb(241, 241, 237)':'Color.rgb(247, 245, 239)','Color.rgb(241,241,237)':'Color.rgb(247,245,239)',
      'Color.rgb(243, 241, 236)':'Color.rgb(247, 245, 239)','Color.rgb(243,241,236)':'Color.rgb(247,245,239)',
      'Color.rgb(188, 188, 183)':'Color.rgb(213, 208, 199)','Color.rgb(188,188,183)':'Color.rgb(213,208,199)',
      'Color.rgb(190, 185, 177)':'Color.rgb(213, 208, 199)','Color.rgb(190,185,177)':'Color.rgb(213,208,199)',
      'Color.rgb(121, 31, 44)':'Color.rgb(116, 27, 39)','Color.rgb(121,31,44)':'Color.rgb(116,27,39)',
      'Color.rgb(126, 31, 46)':'Color.rgb(116, 27, 39)','Color.rgb(126,31,46)':'Color.rgb(116,27,39)'
    }.items(): s=s.replace(a,b)
    path.write_text(s,encoding='utf-8')

for p in (PLAY,BUILD,FRONT,SETUP): recolor(p)

# PLAY: turn the card wall into a compact character sheet.
replace(PLAY,'top.setPadding(dp(12), dp(7), dp(12), dp(7));','top.setPadding(dp(10), dp(4), dp(10), dp(4));')
replace(PLAY,'headerName = text("", 18, true);','headerName = text("", 17, true);')
replace(PLAY,'headerStats = text("", 11, false);','headerStats = text("", 10, false);')
replace(PLAY,'levelLine.setPadding(0,dp(4),0,0);','levelLine.setPadding(0,dp(2),0,0);')
method(PLAY,'private LinearLayout page()','private LinearLayout page(){LinearLayout l=column();l.setPadding(dp(6),dp(4),dp(6),dp(20));return l;}')
method(PLAY,'private LinearLayout panel()','private LinearLayout panel(){LinearLayout l=column();l.setPadding(dp(5),dp(4),dp(5),dp(4));l.setBackground(round(PANEL,2,BORDER));return l;}')
method(PLAY,'private TextView section(','private TextView section(String value){TextView v=text(value,10,true);v.setTextColor(Color.WHITE);v.setPadding(dp(7),dp(4),dp(7),dp(4));v.setBackground(round(ACCENT,1,ACCENT));LinearLayout.LayoutParams p=matchWrap(dp(2));v.setLayoutParams(p);return v;}')
method(PLAY,'private TextView note(','private TextView note(String value){TextView v=text(value,11,false);v.setTextColor(MUTED);v.setPadding(dp(5),dp(3),dp(5),dp(3));return v;}')
method(PLAY,'private TextView pair(','private TextView pair(String left,String right){TextView v=text(left.toUpperCase(Locale.ROOT)+"  "+right,11,false);v.setPadding(dp(7),dp(5),dp(7),dp(5));v.setBackground(round(PANEL_2,1,PANEL_2));LinearLayout.LayoutParams p=matchWrap(dp(1));v.setLayoutParams(p);return v;}')
method(PLAY,'private TextView actionRow(','private TextView actionRow(String left,String right){TextView v=text(left+(right==null||right.isEmpty()?"":"  ·  "+right),12,false);v.setPadding(dp(7),dp(6),dp(7),dp(6));v.setBackground(round(PANEL_2,1,PANEL_2));LinearLayout.LayoutParams p=matchWrap(dp(1));v.setLayoutParams(p);return v;}')
method(PLAY,'private TextView tab(','private TextView tab(String value,boolean active){TextView v=text(value,10,true);v.setTextColor(active?TOP:Color.rgb(236,236,234));v.setPadding(dp(9),dp(5),dp(9),dp(5));v.setBackground(round(active?Color.rgb(231,211,180):TOP_2,0,active?Color.rgb(231,211,180):TOP_2));return v;}')
method(PLAY,'private View abilityCell(','private View abilityCell(String label,int score,int mod){LinearLayout c=column();c.setGravity(Gravity.CENTER);c.setPadding(dp(2),dp(4),dp(2),dp(4));c.setBackground(round(PANEL,1,BORDER));TextView l=text(label,9,true);l.setTextColor(MUTED);l.setGravity(Gravity.CENTER);TextView s=text(String.valueOf(score),15,true);s.setTextColor(ACCENT);s.setGravity(Gravity.CENTER);TextView m=text(signed(mod),9,false);m.setGravity(Gravity.CENTER);m.setTextColor(MUTED);c.addView(l);c.addView(s);c.addView(m);return c;}')
method(PLAY,'private View metricBox(','private View metricBox(String label,String value){LinearLayout c=column();c.setGravity(Gravity.CENTER);c.setPadding(dp(3),dp(4),dp(3),dp(4));TextView l=text(label,9,true);l.setTextColor(MUTED);l.setGravity(Gravity.CENTER);TextView v=text(value,16,true);v.setTextColor(ACCENT);v.setGravity(Gravity.CENTER);c.addView(l);c.addView(v);return c;}')
method(PLAY,'private Button button(','private Button button(String value){Button b=new Button(this);b.setText(value);b.setAllCaps(false);b.setTextSize(11);b.setTextColor(ACCENT);b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(dp(6),dp(5),dp(6),dp(5));b.setBackground(round(PANEL_2,2,BORDER));return b;}')
method(PLAY,'private Button smallButton(','private Button smallButton(String value){Button b=button(value);b.setTextSize(10);b.setMinWidth(dp(36));return b;}')
method(PLAY,'private LinearLayout characterPage()',r'''private LinearLayout characterPage(){
    LinearLayout col=page();col.addView(section("ПЕРСОНАЖ"));
    LinearLayout vitals=panel();LinearLayout line=row();line.setGravity(Gravity.CENTER_VERTICAL);
    TextView hp=text("ОЗ  "+state.hp+"/"+state.maxHp+(state.tempHp>0?"  +"+state.tempHp:""),21,true);hp.setTextColor(state.hp>Math.max(1,state.maxHp/3)?GOOD:BAD);line.addView(hp,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));line.addView(badge("КД "+state.ac,ACCENT));TextView sp=text("  "+DerivedStats.speed(state,stats,ancestryItem(),equippedArmor())+" фт",10,true);sp.setTextColor(MUTED);line.addView(sp);vitals.addView(line);
    LinearLayout buttons=row();buttons.setPadding(0,dp(3),0,0);for(int delta:new int[]{-10,-1,1,10}){final int d=delta;Button b=smallButton((d>0?"+":"")+d);b.setContentDescription("hp-"+(d<0?"minus-"+(-d):"plus-"+d));b.setOnClickListener(v->{state.hp=clamp(state.hp+d,0,state.maxHp);state.save(this);CharacterProfiles.saveCurrent(this);render();});buttons.addView(b,weighted(dp(1)));}vitals.addView(buttons);col.addView(vitals,matchWrap(dp(2)));
    LinearLayout identity=panel();LinearLayout a=row();a.addView(pair("Род",show(state.ancestry)),weighted(dp(1)));a.addView(pair("Наследие",show(state.choiceName("base:heritage"))),weighted(dp(1)));identity.addView(a);LinearLayout b=row();b.addView(pair("Предыстория",show(state.background)),weighted(dp(1)));b.addView(pair("Класс",show(state.className)),weighted(dp(1)));identity.addView(b);col.addView(identity,matchWrap(dp(2)));
    col.addView(section("ХАРАКТЕРИСТИКИ"));LinearLayout abilities=row();for(String[] ability:ABILITIES)abilities.addView(abilityCell(ability[1],stats.abilityScore(ability[0]),stats.ability(ability[0])),weighted(dp(1)));col.addView(abilities,matchWrap(dp(1)));
    col.addView(section("СПАСБРОСКИ · ВОСПРИЯТИЕ"));LinearLayout saves=panel();saves.addView(statsRow(new String[][]{{"СТОЙК.",signed(state.fortitude)},{"РЕФЛ.",signed(state.reflex)},{"ВОЛЯ",signed(state.will)},{"ВОСПР.",signed(state.perception)}}));col.addView(saves,matchWrap(dp(2)));
    col.addView(section("РЕСУРСЫ"));LinearLayout resources=panel();resources.addView(stepper("Очки героя",stats.heroPoints,0,3,value->{stats.heroPoints=value;stats.save(this);}));resources.addView(stepper("Фокус",stats.focus,0,Math.max(0,stats.maxFocus),value->{stats.focus=value;stats.save(this);}));resources.addView(stepper("Ранен",stats.wounded,0,9,value->{stats.wounded=value;stats.save(this);}));resources.addView(stepper("При смерти",stats.dying,0,4,value->{stats.dying=value;stats.save(this);}));col.addView(resources,matchWrap(dp(2)));
    if(activeConditionCount()>0){col.addView(section("АКТИВНЫЕ ЭФФЕКТЫ"));LinearLayout effects=panel();Iterator<String> it=state.conditions.keys();while(it.hasNext()){String id=it.next();RuleItem item=store.findById(id);int value=state.conditions.optInt(id,0);if(item!=null&&value>0)effects.addView(pair(RuNames.shortName(item.name),String.valueOf(value)));}col.addView(effects,matchWrap(dp(2)));}return col;
}''')

# BUILD: compact level editor instead of a long form of boxed fields.
replace(BUILD,'headerName = text("", 18, true);','headerName = text("", 17, true);')
replace(BUILD,'headerStats = text("", 11, false);','headerStats = text("", 10, false);')
replace(BUILD,'TextView name = text("УРОВЕНЬ " + level, 23, true);','TextView name = text("УРОВЕНЬ " + level, 18, true);')
method(BUILD,'private TextView levelButton(','private TextView levelButton(int level,boolean active,boolean reached){TextView v=text(String.valueOf(level),10,true);v.setGravity(Gravity.CENTER);v.setMinWidth(dp(32));v.setPadding(dp(7),dp(5),dp(7),dp(5));v.setTextColor(active?Color.WHITE:reached?ACCENT:MUTED);v.setBackground(round(active?ACCENT:PANEL,1,active?ACCENT:BORDER));return v;}')
method(BUILD,'private TextView modeTab(','private TextView modeTab(String label,boolean active){TextView v=text(label,10,true);v.setGravity(Gravity.CENTER);v.setTextColor(active?TOP:Color.WHITE);v.setPadding(dp(8),dp(5),dp(8),dp(5));v.setBackground(round(active?Color.rgb(231,211,180):TOP_2,0,active?Color.rgb(231,211,180):TOP_2));return v;}')
method(BUILD,'private TextView sectionTitle(','private TextView sectionTitle(String s){TextView v=text(s,10,true);v.setTextColor(Color.WHITE);v.setPadding(dp(7),dp(4),dp(7),dp(4));v.setBackground(round(ACCENT,1,ACCENT));LinearLayout.LayoutParams p=matchWrap(dp(2));v.setLayoutParams(p);return v;}')
method(BUILD,'private TextView actionRow(','private TextView actionRow(String left,String right){TextView v=text(left+(right==null||right.isEmpty()?"":"  ·  "+right),12,false);v.setTextColor(TEXT);v.setPadding(dp(7),dp(6),dp(7),dp(6));v.setBackground(round(PANEL_2,1,PANEL_2));LinearLayout.LayoutParams p=matchWrap(dp(1));v.setLayoutParams(p);return v;}')
method(BUILD,'private LinearLayout panel()','private LinearLayout panel(){LinearLayout l=column();l.setPadding(dp(5),dp(4),dp(5),dp(4));l.setBackground(round(PANEL,2,BORDER));return l;}')
method(BUILD,'private LinearLayout page()','private LinearLayout page(){LinearLayout l=column();l.setPadding(dp(6),dp(4),dp(6),dp(20));return l;}')
method(BUILD,'private View abilityCell(','private View abilityCell(String label,int score,int mod){LinearLayout box=column();box.setGravity(Gravity.CENTER);box.setPadding(dp(2),dp(4),dp(2),dp(4));box.setBackground(round(PANEL,1,BORDER));TextView a=text(label,9,true);a.setTextColor(MUTED);a.setGravity(Gravity.CENTER);TextView s=text(String.valueOf(score),15,true);s.setTextColor(ACCENT);s.setGravity(Gravity.CENTER);TextView m=text((mod>=0?"+":"")+mod,9,false);m.setTextColor(MUTED);m.setGravity(Gravity.CENTER);box.addView(a);box.addView(s);box.addView(m);return box;}')

# HOME + creator: same visual language from first launch onward.
replace(FRONT,'getWindow().setStatusBarColor(ACCENT);','getWindow().setStatusBarColor(TOP);')
replace(FRONT,'top.setPadding(dp(18), dp(15), dp(18), dp(13));','top.setPadding(dp(14), dp(8), dp(14), dp(7));')
replace(FRONT,'TextView title = text("ГРАНЬ 2e", 25, true);','TextView title = text("ГРАНЬ 2e", 22, true);')
replace(FRONT,'body = column(); body.setPadding(dp(12), dp(10), dp(12), dp(28));','body = column(); body.setPadding(dp(8), dp(6), dp(8), dp(22));')
method(FRONT,'private TextView bigAction(','private TextView bigAction(String a,String b,boolean primary){TextView v=text(a+"  ·  "+b,13,true);v.setTextColor(primary?Color.WHITE:TEXT);v.setPadding(dp(10),dp(9),dp(10),dp(9));v.setBackground(round(primary?ACCENT:PANEL_2,2,primary?ACCENT:BORDER));LinearLayout.LayoutParams p=matchWrap(dp(2));v.setLayoutParams(p);return v;}')
method(FRONT,'private TextView action(','private TextView action(String a,String b){TextView v=text(a+"  ·  "+b,12,false);v.setPadding(dp(8),dp(6),dp(8),dp(6));v.setBackground(round(PANEL_2,1,PANEL_2));LinearLayout.LayoutParams p=matchWrap(dp(1));v.setLayoutParams(p);return v;}')
method(FRONT,'private TextView section(','private TextView section(String s){TextView v=text(s,10,true);v.setTextColor(Color.WHITE);v.setPadding(dp(7),dp(4),dp(7),dp(4));v.setBackground(round(ACCENT,1,ACCENT));LinearLayout.LayoutParams p=matchWrap(dp(2));v.setLayoutParams(p);return v;}')
method(FRONT,'private LinearLayout panel()','private LinearLayout panel(){LinearLayout l=column();l.setPadding(dp(6),dp(5),dp(6),dp(5));l.setBackground(round(PANEL,2,BORDER));return l;}')

replace(SETUP,'getWindow().setStatusBarColor(ACCENT);','getWindow().setStatusBarColor(TOP);')
replace(SETUP,'top.setPadding(dp(12),dp(9),dp(12),dp(8));','top.setPadding(dp(10),dp(5),dp(10),dp(5));')
replace(SETUP,'body=column();body.setPadding(dp(12),dp(10),dp(12),dp(22));','body=column();body.setPadding(dp(8),dp(6),dp(8),dp(18));')
method(SETUP,'private View pair(','private View pair(String a,String b){LinearLayout r=row();r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(dp(6),dp(5),dp(6),dp(5));TextView l=text(a.toUpperCase(Locale.ROOT),10,true);l.setTextColor(MUTED);r.addView(l,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));TextView v=text(b,12,true);v.setTextColor(TEXT);v.setGravity(Gravity.END);v.setMaxWidth(dp(250));r.addView(v);return r;}')
method(SETUP,'private TextView action(','private TextView action(String a,String b){TextView v=text(a+(b==null||b.isEmpty()?"":"  ·  "+b),12,false);v.setPadding(dp(7),dp(6),dp(7),dp(6));v.setBackground(round(PANEL2,1,PANEL2));LinearLayout.LayoutParams p=matchWrap(dp(1));v.setLayoutParams(p);return v;}')
method(SETUP,'private TextView title(','private TextView title(String s){TextView v=text(s,11,true);v.setTextColor(Color.WHITE);v.setPadding(dp(7),dp(5),dp(7),dp(5));v.setBackground(round(ACCENT,1,ACCENT));LinearLayout.LayoutParams p=matchWrap(dp(2));v.setLayoutParams(p);return v;}')
method(SETUP,'private LinearLayout panel()','private LinearLayout panel(){LinearLayout l=column();l.setPadding(dp(6),dp(5),dp(6),dp(5));l.setBackground(round(PANEL,2,BORDER));return l;}')

# Bump after 8.0 payload installation.
gradle=ROOT/'app/build.gradle';s=gradle.read_text(encoding='utf-8').replace('versionCode 800','versionCode 810').replace("versionName '8.0.0'","versionName '8.1.0'");gradle.write_text(s,encoding='utf-8')
print('Gran 2e 8.1 compact reference UI rebuild applied')
