#!/usr/bin/env python3
from pathlib import Path
import re

ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/'app/src/main/java/ru/gran/edge2e'
PLAY=JAVA/'ReferencePlayActivity.java'
BUILD=JAVA/'ReferenceBuildActivity.java'
FRONT=JAVA/'FrontPageActivity.java'


def method(path, marker, new):
    s=path.read_text(encoding='utf-8')
    start=s.find(marker)
    if start < 0: raise SystemExit(f'9.0 method not found: {path.name}: {marker}')
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
    if end < 0: raise SystemExit(f'9.0 closing brace not found: {path.name}: {marker}')
    path.write_text(s[:start]+new.strip()+s[end:],encoding='utf-8')


def replace(path,old,new,required=True):
    s=path.read_text(encoding='utf-8')
    if old not in s:
        if required: raise SystemExit(f'9.0 replace target not found: {path.name}: {old[:100]}')
        return
    path.write_text(s.replace(old,new),encoding='utf-8')


def insert_before(path,marker,addition):
    s=path.read_text(encoding='utf-8')
    if addition.strip() in s: return
    p=s.find(marker)
    if p < 0: raise SystemExit(f'9.0 insertion marker not found: {path.name}: {marker}')
    path.write_text(s[:p]+addition.strip()+"\n\n"+s[p:],encoding='utf-8')

# HOME: primary flow now matches the reference model: create a build, then edit it
# in BUILD. The old guided wizard stays only as a migration/testing surface.
replace(FRONT,
    'newBuild.setOnClickListener(v -> { CharacterProfiles.createNew(this); startActivity(new Intent(this, CharacterSetupActivity.class)); });',
    'newBuild.setOnClickListener(v -> { CharacterProfiles.createNew(this); startActivity(new Intent(this, ReferenceBuildActivity.class)); });')
replace(FRONT,
    'if (which == 0) { if (CharacterProfiles.load(this, p.id)) startActivity(new Intent(this, CharacterSetupActivity.class)); }',
    'if (which == 0) { if (CharacterProfiles.load(this, p.id)) startActivity(new Intent(this, ReferenceBuildActivity.class)); }')
replace(FRONT,'String[] actions = {"Продолжить создание", "Сохранить копию", "Экспортировать", "Удалить"};',
              'String[] actions = {"Открыть сборку", "Сохранить копию", "Экспортировать", "Удалить"};')
method(FRONT,'private View shell()',r'''private View shell(){
    LinearLayout root=column();root.setBackgroundColor(BG);
    LinearLayout top=column();top.setPadding(dp(14),dp(10),dp(14),dp(9));top.setBackgroundColor(TOP);
    TextView title=text("ГРАНЬ 2e",24,true);title.setTextColor(Color.WHITE);title.setLetterSpacing(0.06f);top.addView(title);
    TextView sub=text("PATHFINDER 2E • СОЗДАНИЕ И ИГРА",10,true);sub.setTextColor(Color.rgb(224,200,169));top.addView(sub);root.addView(top,matchWrap());
    ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);body=column();body.setPadding(dp(7),dp(7),dp(7),dp(24));scroll.addView(body);
    root.addView(scroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));return root;
}''')
method(FRONT,'private TextView bigAction(',r'''private TextView bigAction(String a,String b,boolean primary){
    TextView v=text(a+"\n"+b,13,true);v.setTextColor(primary?Color.WHITE:TEXT);v.setGravity(Gravity.CENTER_VERTICAL);
    v.setPadding(dp(12),dp(11),dp(12),dp(11));v.setBackground(round(primary?ACCENT:PANEL_2,0,primary?ACCENT:PANEL_2));
    LinearLayout.LayoutParams p=matchWrap(dp(1));v.setLayoutParams(p);return v;
}''')
method(FRONT,'private LinearLayout panel()',r'''private LinearLayout panel(){LinearLayout l=column();l.setPadding(dp(7),dp(6),dp(7),dp(6));l.setBackground(round(PANEL,0,PANEL));return l;}''')

# PLAY: observable reference structure is two fixed top bars followed by a pager.
replace(PLAY,'private TextView levelLabel;','private TextView levelLabel;\n    private LinearLayout tabNav;')
method(PLAY,'private View shell()',r'''private View shell(){
    LinearLayout root=column();root.setBackgroundColor(BG);
    LinearLayout top=row();top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(7),dp(4),dp(7),dp(4));top.setBackgroundColor(TOP);
    TextView menu=text("☰",20,true);menu.setTextColor(Color.WHITE);menu.setGravity(Gravity.CENTER);menu.setPadding(dp(8),dp(5),dp(12),dp(5));menu.setContentDescription("play-menu");menu.setOnClickListener(v->showPlayDrawer());top.addView(menu);
    LinearLayout identity=column();headerName=text("",17,true);headerName.setTextColor(Color.WHITE);identity.addView(headerName);headerStats=text("",9,false);headerStats.setTextColor(Color.rgb(204,205,206));identity.addView(headerStats);top.addView(identity,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
    TextView build=text("СБОРКА",10,true);build.setTextColor(Color.rgb(236,205,169));build.setPadding(dp(10),dp(7),dp(8),dp(7));build.setContentDescription("play-build");build.setOnClickListener(v->startActivity(new Intent(this,ReferenceBuildActivity.class)));top.addView(build);
    TextView more=text("⋮",20,true);more.setTextColor(Color.WHITE);more.setGravity(Gravity.CENTER);more.setPadding(dp(8),dp(3),dp(5),dp(3));more.setContentDescription("play-more");more.setOnClickListener(v->startActivity(new Intent(this,ReferenceMoreActivity.class)));top.addView(more);root.addView(top,matchWrap());
    LinearLayout navLine=row();navLine.setGravity(Gravity.CENTER_VERTICAL);navLine.setBackgroundColor(TOP_2);navLine.setPadding(dp(3),dp(2),dp(3),dp(2));
    Button prev=smallButton("‹");prev.setContentDescription("play-level-prev");prev.setOnClickListener(v->changeLevel(-1));navLine.addView(prev,fixed(dp(38)));
    levelLabel=text("УР. "+state.level,9,true);levelLabel.setTextColor(Color.rgb(236,205,169));levelLabel.setGravity(Gravity.CENTER);levelLabel.setPadding(dp(3),0,dp(3),0);navLine.addView(levelLabel,fixed(dp(52)));
    Button next=smallButton("›");next.setContentDescription("play-level-next");next.setOnClickListener(v->changeLevel(1));navLine.addView(next,fixed(dp(38)));
    HorizontalScrollView hs=new HorizontalScrollView(this);hs.setHorizontalScrollBarEnabled(false);hs.setFillViewport(false);tabNav=row();tabNav.setPadding(dp(2),dp(2),dp(2),dp(2));
    for(String[] spec:TABS){TextView t=tab(spec[0],spec[1].equals(screen));String target=spec[1];t.setContentDescription("play-tab-"+target);t.setOnClickListener(v->{screen=target;render();});tabNav.addView(t,wrapWrap(dp(1)));}
    hs.addView(tabNav);navLine.addView(hs,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));root.addView(navLine,matchWrap());
    content=new FrameLayout(this);root.addView(content,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));return root;
}''')
method(PLAY,'private void refreshTabStrip()',r'''private void refreshTabStrip(){
    if(tabNav==null)return;tabNav.removeAllViews();
    for(String[] spec:TABS){TextView t=tab(spec[0],spec[1].equals(screen));String target=spec[1];t.setContentDescription("play-tab-"+target);t.setOnClickListener(v->{screen=target;render();});tabNav.addView(t,wrapWrap(dp(1)));}
}''')
insert_before(PLAY,'private LinearLayout characterPage()',r'''private void showPlayDrawer(){
    String[] items={"Персонаж","Атаки","Защита","Навыки","Фиты","Заклинания","Снаряжение","Питомцы","Эффекты","Сборка","Персонажи","Ещё"};
    new AlertDialog.Builder(this).setTitle(state.name==null||state.name.trim().isEmpty()?"Новый герой":state.name).setItems(items,(d,which)->{
        if(which<9){screen=TABS[which][1];render();return;}if(which==9){startActivity(new Intent(this,ReferenceBuildActivity.class));return;}
        if(which==10){startActivity(new Intent(this,FrontPageActivity.class));finish();return;}startActivity(new Intent(this,ReferenceMoreActivity.class));
    }).show();
}''')
method(PLAY,'private TextView tab(',r'''private TextView tab(String value,boolean active){
    TextView v=text(value,10,true);v.setGravity(Gravity.CENTER);v.setSingleLine(true);v.setTextColor(active?TOP:Color.rgb(232,232,229));
    v.setPadding(dp(9),dp(6),dp(9),dp(6));v.setBackground(round(active?Color.rgb(231,211,180):TOP_2,0,active?Color.rgb(231,211,180):TOP_2));return v;
}''')
method(PLAY,'private TextView actionRow(',r'''private TextView actionRow(String left,String right){
    String value=left+(right==null||right.isEmpty()?"":"\n"+right);TextView v=text(value,11,false);v.setTextColor(TEXT);v.setPadding(dp(8),dp(6),dp(8),dp(6));
    v.setBackground(round(PANEL_2,0,PANEL_2));LinearLayout.LayoutParams p=matchWrap(dp(1));v.setLayoutParams(p);return v;
}''')
method(PLAY,'private TextView pair(',r'''private TextView pair(String left,String right){
    TextView v=text(left.toUpperCase(Locale.ROOT)+"\n"+right,10,false);v.setTextColor(TEXT);v.setPadding(dp(7),dp(5),dp(7),dp(5));v.setBackground(round(PANEL_2,0,PANEL_2));
    LinearLayout.LayoutParams p=matchWrap(dp(1));v.setLayoutParams(p);return v;
}''')
method(PLAY,'private TextView section(',r'''private TextView section(String value){
    TextView v=text(value,10,true);v.setTextColor(Color.WHITE);v.setLetterSpacing(0.04f);v.setPadding(dp(8),dp(4),dp(8),dp(4));v.setBackground(round(ACCENT,0,ACCENT));
    LinearLayout.LayoutParams p=matchWrap(dp(2));v.setLayoutParams(p);return v;
}''')

# BUILD: same reference navigation language, level strip and full browser rows.
replace(BUILD,'private LinearLayout levelNav;','private LinearLayout levelNav;\n    private LinearLayout modeNav;')
method(BUILD,'private View shell()',r'''private View shell(){
    LinearLayout root=column();root.setBackgroundColor(BG);
    LinearLayout top=row();top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(7),dp(4),dp(7),dp(4));top.setBackgroundColor(TOP);
    TextView menu=text("☰",20,true);menu.setTextColor(Color.WHITE);menu.setGravity(Gravity.CENTER);menu.setPadding(dp(8),dp(5),dp(12),dp(5));menu.setContentDescription("build-menu");menu.setOnClickListener(v->showBuildDrawer());top.addView(menu);
    LinearLayout id=column();headerName=text("",17,true);headerName.setTextColor(Color.WHITE);headerName.setContentDescription("build-name");headerName.setOnClickListener(v->editName());id.addView(headerName);headerStats=text("",9,false);headerStats.setTextColor(Color.rgb(204,205,206));id.addView(headerStats);top.addView(id,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
    TextView play=text("ИГРА",10,true);play.setTextColor(Color.rgb(236,205,169));play.setPadding(dp(10),dp(7),dp(8),dp(7));play.setContentDescription("build-play");play.setOnClickListener(v->{Intent i=new Intent(this,ReferencePlayActivity.class);i.putExtra("screen","character");startActivity(i);});top.addView(play);
    TextView more=text("⋮",20,true);more.setTextColor(Color.WHITE);more.setGravity(Gravity.CENTER);more.setPadding(dp(8),dp(3),dp(5),dp(3));more.setContentDescription("build-more");more.setOnClickListener(v->startActivity(new Intent(this,ReferenceMoreActivity.class)));top.addView(more);root.addView(top,matchWrap());
    HorizontalScrollView modes=new HorizontalScrollView(this);modes.setHorizontalScrollBarEnabled(false);modes.setBackgroundColor(TOP_2);modeNav=row();modeNav.setPadding(dp(3),dp(2),dp(3),dp(2));
    String[][] tabs={{"УРОВНИ","levels"},{"НАВЫКИ","skills"},{"СПРАВОЧНИК","reference"}};for(String[] spec:tabs){TextView t=modeTab(spec[0],spec[1].equals(section));String target=spec[1];t.setOnClickListener(v->{section=target;render();});modeNav.addView(t,wrapWrap(dp(1)));}modes.addView(modeNav);root.addView(modes,matchWrap());
    HorizontalScrollView levelScroll=new HorizontalScrollView(this);levelScroll.setHorizontalScrollBarEnabled(false);levelScroll.setBackgroundColor(PANEL_2);levelNav=row();levelNav.setPadding(dp(4),dp(3),dp(4),dp(3));levelScroll.addView(levelNav);root.addView(levelScroll,matchWrap());
    content=new FrameLayout(this);root.addView(content,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));return root;
}''')
method(BUILD,'private void refreshModeTabs()',r'''private void refreshModeTabs(){
    if(modeNav==null)return;modeNav.removeAllViews();String[][] tabs={{"УРОВНИ","levels"},{"НАВЫКИ","skills"},{"СПРАВОЧНИК","reference"}};
    for(String[] spec:tabs){TextView t=modeTab(spec[0],spec[1].equals(section));String target=spec[1];t.setOnClickListener(v->{section=target;render();});modeNav.addView(t,wrapWrap(dp(1)));}
}''')
insert_before(BUILD,'private LinearLayout levelPage(',r'''private void showBuildDrawer(){
    String[] items={"Уровни 1–20","Навыки","Справочник","Лист персонажа","Заклинания","Снаряжение","Персонажи","Ещё"};
    new AlertDialog.Builder(this).setTitle(state.name==null||state.name.trim().isEmpty()?"Новый герой":state.name).setItems(items,(d,which)->{
        if(which==0){section="levels";render();return;}if(which==1){section="skills";render();return;}if(which==2){section="reference";render();return;}
        if(which==3){startActivity(new Intent(this,ReferencePlayActivity.class));return;}if(which==4){Intent i=new Intent(this,ReferencePlayActivity.class);i.putExtra("screen","spells");startActivity(i);return;}
        if(which==5){Intent i=new Intent(this,ReferencePlayActivity.class);i.putExtra("screen","gear");startActivity(i);return;}if(which==6){startActivity(new Intent(this,FrontPageActivity.class));finish();return;}startActivity(new Intent(this,ReferenceMoreActivity.class));
    }).show();
}''')
method(BUILD,'private TextView actionRow(',r'''private TextView actionRow(String left,String right){
    TextView v=text(left+(right==null||right.isEmpty()?"":"\n"+right),11,false);v.setTextColor(TEXT);v.setPadding(dp(8),dp(6),dp(8),dp(6));v.setBackground(round(PANEL_2,0,PANEL_2));
    LinearLayout.LayoutParams p=matchWrap(dp(1));v.setLayoutParams(p);return v;
}''')
method(BUILD,'private TextView modeTab(',r'''private TextView modeTab(String label,boolean active){TextView v=text(label,10,true);v.setGravity(Gravity.CENTER);v.setSingleLine(true);v.setTextColor(active?TOP:Color.WHITE);v.setPadding(dp(12),dp(6),dp(12),dp(6));v.setBackground(round(active?Color.rgb(231,211,180):TOP_2,0,active?Color.rgb(231,211,180):TOP_2));return v;}''')
method(BUILD,'private TextView levelButton(',r'''private TextView levelButton(int level,boolean active,boolean reached){TextView v=text(String.valueOf(level),10,true);v.setGravity(Gravity.CENTER);v.setMinWidth(dp(31));v.setPadding(dp(7),dp(5),dp(7),dp(5));v.setTextColor(active?Color.WHITE:reached?ACCENT:MUTED);v.setBackground(round(active?ACCENT:PANEL_2,0,active?ACCENT:PANEL_2));return v;}''')
insert_before(BUILD,'private void showBaseBrowser(',r'''private View baseBrowserRow(RuleItem item){
    LinearLayout box=column();box.setPadding(dp(9),dp(7),dp(9),dp(7));box.setBackground(round(PANEL_2,0,PANEL_2));
    LinearLayout head=row();head.setGravity(Gravity.CENTER_VERTICAL);TextView name=text(RuNames.shortName(item.name),14,true);name.setTextColor(ACCENT);head.addView(name,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));if(item.level>0){TextView lv=badge("УР. "+item.level,TOP_2);head.addView(lv);}box.addView(head);
    String meta=item.traitsLine();if(meta==null)meta="";if(!item.source.isEmpty())meta+=(meta.isEmpty()?"":" • ")+item.source;if(!meta.isEmpty()){TextView m=text(meta,9,false);m.setTextColor(MUTED);m.setPadding(0,dp(2),0,0);box.addView(m);}
    if(item.description!=null&&!item.description.trim().isEmpty()){String d=item.description.replace('\n',' ').trim();if(d.length()>190)d=d.substring(0,190)+"…";TextView desc=text(d,10,false);desc.setTextColor(TEXT);desc.setPadding(0,dp(3),0,0);box.addView(desc);}return box;
}''')
method(BUILD,'private void showBaseBrowser(',r'''private void showBaseBrowser(String title,String category,String hint,Selection selection){
    LinearLayout outer=column();outer.setPadding(dp(7),dp(4),dp(7),dp(4));EditText search=input(hint);outer.addView(search);ScrollView sv=new ScrollView(this);LinearLayout list=column();sv.addView(list);outer.addView(sv,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(570)));
    AlertDialog dialog=new AlertDialog.Builder(this).setTitle(title).setView(outer).setNegativeButton("Закрыть",null).create();Runnable refresh=()->{list.removeAllViews();String q=search.getText().toString();int shown=0;for(RuleItem item:store.query(category,20,"",1000)){if(!matches(item,q))continue;View r=baseBrowserRow(item);r.setOnClickListener(v->{selection.select(item);dialog.dismiss();});r.setOnLongClickListener(v->{ruleDetail(item,null);return true;});list.addView(r,matchWrap(dp(1)));if(++shown>=220)break;}if(shown==0)list.addView(note("Ничего не найдено."));};search.addTextChangedListener(watcher(refresh));refresh.run();dialog.show();
}''')

gradle=ROOT/'app/build.gradle'
s=gradle.read_text(encoding='utf-8')
s=re.sub(r'versionCode\s+\d+','versionCode 900',s)
s=re.sub(r"versionName\s+'[^']+'","versionName '9.0.0'",s)
gradle.write_text(s,encoding='utf-8')
print('Gran 2e 9.0 reference-interface parity shell applied')
