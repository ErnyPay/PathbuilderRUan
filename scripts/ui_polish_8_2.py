#!/usr/bin/env python3
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/'app/src/main/java/ru/gran/edge2e'
PLAY=JAVA/'ReferencePlayActivity.java'
BUILD=JAVA/'ReferenceBuildActivity.java'
FRONT=JAVA/'FrontPageActivity.java'
SETUP=JAVA/'CharacterSetupActivity.java'


def method(path, marker, new):
    s=path.read_text(encoding='utf-8')
    start=s.find(marker)
    if start < 0:
        raise SystemExit(f'8.2 method not found: {path.name}: {marker}')
    brace=s.find('{', start)
    depth=0; ins=False; inc=False; esc=False; end=-1
    for i in range(brace, len(s)):
        c=s[i]
        if esc:
            esc=False; continue
        if c=='\\' and (ins or inc):
            esc=True; continue
        if c=='"' and not inc:
            ins=not ins; continue
        if c=="'" and not ins:
            inc=not inc; continue
        if ins or inc:
            continue
        if c=='{': depth+=1
        elif c=='}':
            depth-=1
            if depth==0:
                end=i+1; break
    if end < 0:
        raise SystemExit(f'8.2 closing brace not found: {path.name}: {marker}')
    path.write_text(s[:start]+new.strip()+s[end:], encoding='utf-8')


def insert_before(path, marker, addition):
    s=path.read_text(encoding='utf-8')
    if addition.strip() in s:
        return
    p=s.find(marker)
    if p < 0:
        raise SystemExit(f'8.2 insertion marker not found: {path.name}: {marker}')
    path.write_text(s[:p]+addition.strip()+'\n\n'+s[p:], encoding='utf-8')


def optional_replace(path, old, new):
    s=path.read_text(encoding='utf-8')
    if old in s:
        path.write_text(s.replace(old,new), encoding='utf-8')


# Shared typography: use Android system families only; no external/reference fonts.
for path in (PLAY, BUILD, FRONT, SETUP):
    method(path, 'private TextView text(', r'''private TextView text(String value,int sp,boolean bold){
        TextView v=new TextView(this);
        v.setText(value);v.setTextSize(sp);v.setTextColor(TEXT);
        v.setIncludeFontPadding(false);
        v.setLineSpacing(0f,1.0f);
        v.setTypeface(Typeface.create(bold?"sans-serif-condensed":"sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));
        return v;
    }''')

# PLAY: sheet-like hierarchy, not a framed card wall.
method(PLAY,'private LinearLayout panel()',r'''private LinearLayout panel(){
    LinearLayout l=column();l.setPadding(dp(7),dp(6),dp(7),dp(6));
    l.setBackground(round(PANEL,0,PANEL));return l;
}''')
method(PLAY,'private TextView section(',r'''private TextView section(String value){
    TextView v=text(value,10,true);v.setTextColor(Color.WHITE);
    v.setLetterSpacing(0.045f);v.setPadding(dp(8),dp(4),dp(8),dp(4));
    v.setBackground(round(ACCENT,0,ACCENT));
    LinearLayout.LayoutParams p=matchWrap(dp(3));v.setLayoutParams(p);return v;
}''')
method(PLAY,'private TextView pair(',r'''private TextView pair(String left,String right){
    TextView v=text(left.toUpperCase(Locale.ROOT)+"   "+right,11,false);
    v.setTextColor(TEXT);v.setPadding(dp(7),dp(5),dp(7),dp(5));
    v.setBackground(round(PANEL_2,0,PANEL_2));
    LinearLayout.LayoutParams p=matchWrap(dp(1));v.setLayoutParams(p);return v;
}''')
method(PLAY,'private TextView actionRow(',r'''private TextView actionRow(String left,String right){
    TextView v=text(left+(right==null||right.isEmpty()?"":"  ·  "+right),11,false);
    v.setPadding(dp(7),dp(6),dp(7),dp(6));v.setBackground(round(PANEL_2,0,PANEL_2));
    LinearLayout.LayoutParams p=matchWrap(dp(1));v.setLayoutParams(p);return v;
}''')
method(PLAY,'private TextView tab(',r'''private TextView tab(String value,boolean active){
    TextView v=text(value,10,true);v.setGravity(Gravity.CENTER);
    v.setTextColor(active?TOP:Color.rgb(232,232,229));v.setPadding(dp(10),dp(6),dp(10),dp(6));
    v.setBackground(round(active?Color.rgb(231,211,180):TOP_2,0,active?Color.rgb(231,211,180):TOP_2));
    return v;
}''')
method(PLAY,'private Button button(',r'''private Button button(String value){
    Button b=new Button(this);b.setText(value);b.setAllCaps(false);b.setTextSize(10);
    b.setTypeface(Typeface.create("sans-serif-condensed",Typeface.BOLD));
    b.setTextColor(ACCENT);b.setMinHeight(0);b.setMinimumHeight(0);b.setMinWidth(0);b.setMinimumWidth(0);
    b.setPadding(dp(8),dp(5),dp(8),dp(5));b.setBackground(round(PANEL_2,1,PANEL_2));return b;
}''')
method(PLAY,'private Button smallButton(',r'''private Button smallButton(String value){
    Button b=button(value);b.setTextSize(10);
    if("‹".equals(value)||"›".equals(value)){
        b.setTextSize(18);b.setTextColor(Color.rgb(231,211,180));b.setBackgroundColor(Color.TRANSPARENT);
        b.setPadding(dp(12),0,dp(12),0);
    }else{
        b.setMinWidth(dp(34));b.setPadding(dp(8),dp(4),dp(8),dp(4));
    }
    return b;
}''')
method(PLAY,'private View abilityCell(',r'''private View abilityCell(String label,int score,int mod){
    LinearLayout c=column();c.setGravity(Gravity.CENTER);c.setPadding(dp(2),dp(3),dp(2),dp(3));
    TextView l=text(label,9,true);l.setTextColor(MUTED);l.setGravity(Gravity.CENTER);
    TextView s=text(String.valueOf(score),17,true);s.setTextColor(ACCENT);s.setGravity(Gravity.CENTER);
    TextView m=text(signed(mod),9,false);m.setGravity(Gravity.CENTER);m.setTextColor(MUTED);
    c.addView(l);c.addView(s);c.addView(m);return c;
}''')
method(PLAY,'private View metricBox(',r'''private View metricBox(String label,String value){
    LinearLayout c=column();c.setGravity(Gravity.CENTER);c.setPadding(dp(3),dp(3),dp(3),dp(3));
    TextView l=text(label,9,true);l.setTextColor(MUTED);l.setGravity(Gravity.CENTER);
    TextView v=text(value,16,true);v.setTextColor(TEXT);v.setGravity(Gravity.CENTER);
    c.addView(l);c.addView(v);return c;
}''')
method(PLAY,'private View stepper(',r'''private View stepper(String label,int value,int min,int max,IntSet set){
    LinearLayout r=row();r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(dp(7),dp(3),dp(7),dp(3));
    TextView l=text(label,11,true);l.setTextColor(TEXT);r.addView(l,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
    Button minus=smallButton("−");TextView val=text(String.valueOf(value),14,true);val.setGravity(Gravity.CENTER);val.setMinWidth(dp(34));Button plus=smallButton("+");
    minus.setOnClickListener(v->{set.set(clamp(value-1,min,max));render();});plus.setOnClickListener(v->{set.set(clamp(value+1,min,max));render();});
    r.addView(minus);r.addView(val);r.addView(plus);return r;
}''')

insert_before(PLAY,'private View abilityCell(',r'''private View identityCell(String label,String value){
    LinearLayout c=column();c.setPadding(dp(7),dp(5),dp(7),dp(5));
    TextView l=text(label.toUpperCase(Locale.ROOT),8,true);l.setTextColor(MUTED);l.setLetterSpacing(0.04f);
    TextView v=text(value,12,true);v.setTextColor(TEXT);v.setPadding(0,dp(1),0,0);
    c.addView(l);c.addView(v);return c;
}''')

method(PLAY,'private LinearLayout characterPage()',r'''private LinearLayout characterPage(){
    LinearLayout col=page();col.addView(section("ПЕРСОНАЖ"));

    LinearLayout vitals=panel();LinearLayout line=row();line.setGravity(Gravity.CENTER_VERTICAL);
    TextView hp=text(state.hp+" / "+state.maxHp,25,true);hp.setTextColor(state.hp>Math.max(1,state.maxHp/3)?GOOD:BAD);
    LinearLayout hpBlock=column();TextView hpLabel=text("ОЗ",8,true);hpLabel.setTextColor(MUTED);hpBlock.addView(hpLabel);hpBlock.addView(hp);
    line.addView(hpBlock,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
    line.addView(metricBox("КД",String.valueOf(state.ac)),new LinearLayout.LayoutParams(dp(82),ViewGroup.LayoutParams.WRAP_CONTENT));
    line.addView(metricBox("СКОРОСТЬ",DerivedStats.speed(state,stats,ancestryItem(),equippedArmor())+" фт"),new LinearLayout.LayoutParams(dp(110),ViewGroup.LayoutParams.WRAP_CONTENT));
    vitals.addView(line);
    LinearLayout buttons=row();buttons.setGravity(Gravity.END);buttons.setPadding(0,dp(3),0,0);
    for(int delta:new int[]{-10,-1,1,10}){final int d=delta;Button b=smallButton((d>0?"+":"")+d);b.setContentDescription("hp-"+(d<0?"minus-"+(-d):"plus-"+d));b.setOnClickListener(v->{state.hp=clamp(state.hp+d,0,state.maxHp);state.save(this);CharacterProfiles.saveCurrent(this);render();});buttons.addView(b,wrapWrap(dp(2)));}
    vitals.addView(buttons);col.addView(vitals,matchWrap(dp(2)));

    LinearLayout identity=panel();LinearLayout a=row();a.addView(identityCell("Род",show(state.ancestry)),weighted(dp(1)));a.addView(identityCell("Наследие",show(state.choiceName("base:heritage"))),weighted(dp(1)));identity.addView(a);
    LinearLayout b=row();b.addView(identityCell("Предыстория",show(state.background)),weighted(dp(1)));b.addView(identityCell("Класс",show(state.className)),weighted(dp(1)));identity.addView(b);col.addView(identity,matchWrap(dp(2)));

    col.addView(section("ХАРАКТЕРИСТИКИ"));LinearLayout abilities=row();for(String[] ability:ABILITIES)abilities.addView(abilityCell(ability[1],stats.abilityScore(ability[0]),stats.ability(ability[0])),weighted(dp(1)));col.addView(abilities,matchWrap(dp(1)));

    col.addView(section("СПАСБРОСКИ · ВОСПРИЯТИЕ"));LinearLayout saves=panel();saves.addView(statsRow(new String[][]{{"СТОЙК.",signed(state.fortitude)},{"РЕФЛ.",signed(state.reflex)},{"ВОЛЯ",signed(state.will)},{"ВОСПР.",signed(state.perception)}}));col.addView(saves,matchWrap(dp(2)));

    col.addView(section("РЕСУРСЫ"));LinearLayout resources=panel();resources.addView(stepper("Очки героя",stats.heroPoints,0,3,value->{stats.heroPoints=value;stats.save(this);}));resources.addView(stepper("Фокус",stats.focus,0,Math.max(0,stats.maxFocus),value->{stats.focus=value;stats.save(this);}));resources.addView(stepper("Ранен",stats.wounded,0,9,value->{stats.wounded=value;stats.save(this);}));resources.addView(stepper("При смерти",stats.dying,0,4,value->{stats.dying=value;stats.save(this);}));col.addView(resources,matchWrap(dp(2)));

    if(activeConditionCount()>0){col.addView(section("АКТИВНЫЕ ЭФФЕКТЫ"));LinearLayout effects=panel();Iterator<String> it=state.conditions.keys();while(it.hasNext()){String id=it.next();RuleItem item=store.findById(id);int value=state.conditions.optInt(id,0);if(item!=null&&value>0)effects.addView(pair(RuNames.shortName(item.name),String.valueOf(value)));}col.addView(effects,matchWrap(dp(2)));}
    return col;
}''')

# Fix visibly unfinished/internal English labels in Russian UI.
optional_replace(PLAY,'curriculum-slot','дополнительная ячейка учебного плана')
method(PLAY,'private String translatedSubtype(',r'''private String translatedSubtype(String subtype){
    if(subtype==null||subtype.isEmpty())return "предмет";
    switch(subtype.toLowerCase(Locale.ROOT)){
        case "equipment":return "предмет";case "weapon":return "оружие";case "armor":return "броня";case "shield":return "щит";
        case "consumable":return "расходник";case "ammo":return "боеприпасы";case "class":return "классовый";
        case "ancestry":return "родовой";case "skill":return "навыковый";case "general":return "общий";case "archetype":return "архетип";
        default:return subtype;
    }
}''')

# BUILD: same typography and flat-sheet treatment.
method(BUILD,'private LinearLayout panel()',r'''private LinearLayout panel(){LinearLayout l=column();l.setPadding(dp(7),dp(6),dp(7),dp(6));l.setBackground(round(PANEL,0,PANEL));return l;}''')
method(BUILD,'private TextView actionRow(',r'''private TextView actionRow(String left,String right){TextView v=text(left+(right==null||right.isEmpty()?"":"  ·  "+right),11,false);v.setTextColor(TEXT);v.setPadding(dp(7),dp(6),dp(7),dp(6));v.setBackground(round(PANEL_2,0,PANEL_2));LinearLayout.LayoutParams p=matchWrap(dp(1));v.setLayoutParams(p);return v;}''')
method(BUILD,'private TextView sectionTitle(',r'''private TextView sectionTitle(String s){TextView v=text(s,10,true);v.setTextColor(Color.WHITE);v.setLetterSpacing(0.045f);v.setPadding(dp(8),dp(4),dp(8),dp(4));v.setBackground(round(ACCENT,0,ACCENT));LinearLayout.LayoutParams p=matchWrap(dp(3));v.setLayoutParams(p);return v;}''')
method(BUILD,'private TextView levelButton(',r'''private TextView levelButton(int level,boolean active,boolean reached){TextView v=text(String.valueOf(level),10,true);v.setGravity(Gravity.CENTER);v.setMinWidth(dp(29));v.setPadding(dp(7),dp(5),dp(7),dp(5));v.setTextColor(active?Color.WHITE:reached?ACCENT:MUTED);v.setBackground(round(active?ACCENT:PANEL_2,0,active?ACCENT:PANEL_2));return v;}''')
method(BUILD,'private TextView modeTab(',r'''private TextView modeTab(String label,boolean active){TextView v=text(label,10,true);v.setGravity(Gravity.CENTER);v.setTextColor(active?TOP:Color.WHITE);v.setPadding(dp(8),dp(6),dp(8),dp(6));v.setBackground(round(active?Color.rgb(231,211,180):TOP_2,0,active?Color.rgb(231,211,180):TOP_2));return v;}''')
method(BUILD,'private Button button(',r'''private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(10);b.setTypeface(Typeface.create("sans-serif-condensed",Typeface.BOLD));b.setTextColor(ACCENT);b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(dp(8),dp(5),dp(8),dp(5));b.setBackground(round(PANEL_2,1,PANEL_2));return b;}''')
method(BUILD,'private View abilityCell(',r'''private View abilityCell(String label,int score,int mod){LinearLayout box=column();box.setGravity(Gravity.CENTER);box.setPadding(dp(2),dp(3),dp(2),dp(3));TextView a=text(label,9,true);a.setTextColor(MUTED);a.setGravity(Gravity.CENTER);TextView s=text(String.valueOf(score),17,true);s.setTextColor(ACCENT);s.setGravity(Gravity.CENTER);TextView m=text((mod>=0?"+":"")+mod,9,false);m.setTextColor(MUTED);m.setGravity(Gravity.CENTER);box.addView(a);box.addView(s);box.addView(m);return box;}''')

# Home / setup: remove "settings form" frames and use the same type system.
for path in (FRONT, SETUP):
    method(path,'private LinearLayout panel()',r'''private LinearLayout panel(){LinearLayout l=column();l.setPadding(dp(8),dp(7),dp(8),dp(7));l.setBackground(round(PANEL,0,PANEL));return l;}''')

method(FRONT,'private TextView bigAction(',r'''private TextView bigAction(String a,String b,boolean primary){TextView v=text(a+(b==null||b.isEmpty()?"":"  ·  "+b),12,true);v.setTextColor(primary?Color.WHITE:TEXT);v.setPadding(dp(10),dp(8),dp(10),dp(8));v.setBackground(round(primary?ACCENT:PANEL_2,1,primary?ACCENT:PANEL_2));LinearLayout.LayoutParams p=matchWrap(dp(2));v.setLayoutParams(p);return v;}''')
method(FRONT,'private TextView action(',r'''private TextView action(String a,String b){TextView v=text(a+(b==null||b.isEmpty()?"":"  ·  "+b),11,false);v.setPadding(dp(8),dp(6),dp(8),dp(6));v.setBackground(round(PANEL_2,0,PANEL_2));LinearLayout.LayoutParams p=matchWrap(dp(1));v.setLayoutParams(p);return v;}''')
method(FRONT,'private TextView section(',r'''private TextView section(String s){TextView v=text(s,10,true);v.setTextColor(Color.WHITE);v.setLetterSpacing(0.045f);v.setPadding(dp(8),dp(4),dp(8),dp(4));v.setBackground(round(ACCENT,0,ACCENT));LinearLayout.LayoutParams p=matchWrap(dp(3));v.setLayoutParams(p);return v;}''')

method(SETUP,'private TextView action(',r'''private TextView action(String a,String b){TextView v=text(a+(b==null||b.isEmpty()?"":"  ·  "+b),11,false);v.setPadding(dp(7),dp(6),dp(7),dp(6));v.setBackground(round(PANEL2,0,PANEL2));LinearLayout.LayoutParams p=matchWrap(dp(1));v.setLayoutParams(p);return v;}''')
method(SETUP,'private TextView title(',r'''private TextView title(String s){TextView v=text(s,10,true);v.setTextColor(Color.WHITE);v.setLetterSpacing(0.045f);v.setPadding(dp(8),dp(4),dp(8),dp(4));v.setBackground(round(ACCENT,0,ACCENT));LinearLayout.LayoutParams p=matchWrap(dp(3));v.setLayoutParams(p);return v;}''')

# 8.1 is a mechanical compacting pass; 8.2 is the visual-quality pass.
gradle=ROOT/'app/build.gradle'
s=gradle.read_text(encoding='utf-8').replace('versionCode 810','versionCode 820').replace("versionName '8.1.0'","versionName '8.2.0'")
if "versionName '8.2.0'" not in s:
    raise SystemExit('8.2 version bump target not applied')
gradle.write_text(s,encoding='utf-8')
print('Gran 2e 8.2 visual polish: typography, borderless sheet, compact controls and Russian UI cleanup applied')
