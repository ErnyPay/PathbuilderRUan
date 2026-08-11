#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
PLAY = ROOT / 'app/src/main/java/ru/gran/edge2e/ReferencePlayActivity.java'
BUILD = ROOT / 'app/src/main/java/ru/gran/edge2e/ReferenceBuildActivity.java'
GRADLE = ROOT / 'app/build.gradle'
MARKER = 'GRAN_REFERENCE_PARITY_7_0'


def method_span(src: str, signature: str):
    start = src.find(signature)
    if start < 0:
        raise SystemExit(f'7.0 missing method signature: {signature}')
    brace = src.find('{', start)
    if brace < 0:
        raise SystemExit(f'7.0 missing opening brace: {signature}')
    depth = 0
    i = brace
    state = 'code'
    while i < len(src):
        ch = src[i]
        nxt = src[i + 1] if i + 1 < len(src) else ''
        if state == 'code':
            if ch == '"': state = 'string'
            elif ch == "'": state = 'char'
            elif ch == '/' and nxt == '/': state = 'line'; i += 1
            elif ch == '/' and nxt == '*': state = 'block'; i += 1
            elif ch == '{': depth += 1
            elif ch == '}':
                depth -= 1
                if depth == 0: return start, i + 1
        elif state == 'string':
            if ch == '\\': i += 1
            elif ch == '"': state = 'code'
        elif state == 'char':
            if ch == '\\': i += 1
            elif ch == "'": state = 'code'
        elif state == 'line':
            if ch == '\n': state = 'code'
        elif state == 'block':
            if ch == '*' and nxt == '/': state = 'code'; i += 1
        i += 1
    raise SystemExit(f'7.0 unterminated method: {signature}')


def replace_method(src: str, signature: str, replacement: str):
    a, b = method_span(src, signature)
    return src[:a] + replacement.strip('\n') + src[b:]


def append_helpers(src: str, helpers: str):
    if MARKER in src: return src
    pos = src.rfind('\n}')
    if pos < 0: raise SystemExit('7.0 class closing brace missing')
    return src[:pos] + '\n\n' + helpers.strip('\n') + '\n' + src[pos:]


PLAY_SHELL = r'''
    private View shell() {
        LinearLayout root = column(); root.setBackgroundColor(BG);
        LinearLayout top = column(); top.setBackgroundColor(TOP);
        LinearLayout titleLine = row(); titleLine.setGravity(Gravity.CENTER_VERTICAL); titleLine.setPadding(dp(8), dp(5), dp(8), dp(4));
        TextView back = text("‹", 24, true); back.setTextColor(Color.WHITE); back.setGravity(Gravity.CENTER); back.setMinWidth(dp(42));
        back.setOnClickListener(v -> { CharacterProfiles.saveCurrent(this); startActivity(new Intent(this, FrontPageActivity.class)); finish(); }); titleLine.addView(back);
        LinearLayout identity = column(); headerName = text("", 17, true); headerName.setTextColor(Color.WHITE); identity.addView(headerName); headerStats = text("", 10, false); headerStats.setTextColor(Color.rgb(210, 211, 212)); identity.addView(headerStats); titleLine.addView(identity, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView build = topAction("СБОРКА"); build.setOnClickListener(v -> startActivity(new Intent(this, ReferenceBuildActivity.class))); titleLine.addView(build);
        TextView more = topAction("⋮"); more.setTextSize(22); more.setOnClickListener(v -> startActivity(new Intent(this, ReferenceMoreActivity.class))); titleLine.addView(more); top.addView(titleLine); root.addView(top, matchWrap());
        LinearLayout level = row(); level.setGravity(Gravity.CENTER_VERTICAL); level.setPadding(dp(6), dp(3), dp(6), dp(3)); level.setBackgroundColor(PANEL_2);
        Button prev = compactButton("‹"); prev.setOnClickListener(v -> changeLevel(-1)); level.addView(prev, fixed(dp(48))); TextView levelText = text("УРОВЕНЬ " + state.level, 12, true); levelText.setGravity(Gravity.CENTER); levelText.setTextColor(ACCENT); level.addView(levelText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1)); Button next = compactButton("›"); next.setOnClickListener(v -> changeLevel(1)); level.addView(next, fixed(dp(48))); root.addView(level, matchWrap());
        HorizontalScrollView scroll = new HorizontalScrollView(this); scroll.setHorizontalScrollBarEnabled(false); scroll.setBackgroundColor(TOP_2); LinearLayout nav = row(); nav.setPadding(dp(2), dp(2), dp(2), dp(2));
        for (String[] spec : TABS) { TextView t = tab(spec[0], spec[1].equals(screen)); String target = spec[1]; t.setOnClickListener(v -> { screen = target; render(); }); nav.addView(t, wrapWrap(dp(1))); }
        scroll.addView(nav); root.addView(scroll, matchWrap()); content = new FrameLayout(this); root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1)); return root;
    }'''

PLAY_RENDER = r'''
    private void render() {
        if (content == null) return; runtime = RuleRuntime.resolve(store, state, stats); syncDerived(); String cls = state.className.isEmpty() ? "Класс не выбран" : RuNames.shortName(state.className); String ancestry = state.ancestry.isEmpty() ? "Род не выбран" : RuNames.shortName(state.ancestry); headerName.setText(state.name == null || state.name.trim().isEmpty() ? "Новый персонаж" : state.name); headerStats.setText("ур. " + state.level + " • " + ancestry + " • " + cls); content.removeAllViews(); View page;
        switch (screen) { case "attacks": page = attacksPage(); break; case "defenses": page = defensesPage(); break; case "skills": page = skillsPage(); break; case "feats": page = featsPage(); break; case "spells": page = spellsPage(); break; case "gear": page = gearPage(); break; case "pets": page = petsPage(); break; case "effects": page = effectsPage(); break; default: page = characterPage(); }
        content.addView(scroll(page)); refreshTabStrip();
    }'''

PLAY_CHARACTER = r'''
    private LinearLayout characterPage() {
        LinearLayout col = referencePage(); col.addView(referenceBand("ПЕРСОНАЖ")); LinearLayout vital = referencePanel(); LinearLayout hp = row(); hp.setGravity(Gravity.CENTER_VERTICAL); LinearLayout hpBlock = column(); TextView hpValue = text(state.hp + " / " + state.maxHp, 26, true); hpValue.setTextColor(state.hp > Math.max(1, state.maxHp / 3) ? GOOD : BAD); hpBlock.addView(hpValue); TextView hpLabel = text("ОЗ" + (state.tempHp > 0 ? "  •  временные " + state.tempHp : ""), 10, true); hpLabel.setTextColor(MUTED); hpBlock.addView(hpLabel); hp.addView(hpBlock, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1)); hp.addView(referenceMetric("КД", String.valueOf(state.ac)), weighted(dp(2))); hp.addView(referenceMetric("СКОР.", DerivedStats.speed(stats, ancestryItem(), equippedArmor()) + " фт"), weighted(dp(2))); vital.addView(hp);
        LinearLayout hpButtons = row(); for (int delta : new int[]{-10, -1, 1, 10}) { final int d = delta; Button b = compactButton((d > 0 ? "+" : "") + d); b.setOnClickListener(v -> { state.hp = clamp(state.hp + d, 0, state.maxHp); state.save(this); render(); }); hpButtons.addView(b, weighted(dp(2))); } vital.addView(hpButtons); col.addView(vital, matchWrap(dp(3)));
        LinearLayout identity = referencePanel(); identity.addView(referencePair("Род / наследие", show(state.ancestry) + " • " + show(state.choiceName("base:heritage")))); identity.addView(referencePair("Предыстория", show(state.background))); identity.addView(referencePair("Класс", show(state.className))); col.addView(identity, matchWrap(dp(3)));
        col.addView(referenceBand("ХАРАКТЕРИСТИКИ")); LinearLayout abilities = row(); for (String[] ability : ABILITIES) abilities.addView(referenceAbility(ability[1], stats.abilityScore(ability[0]), stats.ability(ability[0])), weighted(dp(1))); col.addView(abilities, matchWrap(dp(2)));
        col.addView(referenceBand("СПАСБРОСКИ • ВОСПРИЯТИЕ")); LinearLayout saves = row(); saves.addView(referenceMetric("СТОЙК.", signed(state.fortitude)), weighted(dp(1))); saves.addView(referenceMetric("РЕФЛ.", signed(state.reflex)), weighted(dp(1))); saves.addView(referenceMetric("ВОЛЯ", signed(state.will)), weighted(dp(1))); saves.addView(referenceMetric("ВОСПР.", signed(state.perception)), weighted(dp(1))); col.addView(saves, matchWrap(dp(2)));
        col.addView(referenceBand("РЕСУРСЫ")); LinearLayout resources = referencePanel(); resources.addView(stepper("Очки героя", stats.heroPoints, 0, 3, value -> { stats.heroPoints = value; stats.save(this); })); resources.addView(stepper("Фокус", stats.focus, 0, Math.max(0, stats.maxFocus), value -> { stats.focus = value; stats.save(this); })); resources.addView(stepper("Ранен", stats.wounded, 0, 9, value -> { stats.wounded = value; stats.save(this); })); resources.addView(stepper("При смерти", stats.dying, 0, 4, value -> { stats.dying = value; stats.save(this); })); col.addView(resources, matchWrap(dp(3)));
        if (activeConditionCount() > 0) { col.addView(referenceBand("АКТИВНЫЕ СОСТОЯНИЯ")); LinearLayout effects = referencePanel(); Iterator<String> it = state.conditions.keys(); while (it.hasNext()) { String id = it.next(); RuleItem item = store.findById(id); int value = state.conditions.optInt(id,0); if (item != null && value > 0) effects.addView(referencePair(RuNames.shortName(item.name), String.valueOf(value))); } TextView open = referenceAction("Открыть эффекты", "изменить состояния"); open.setOnClickListener(v -> { screen = "effects"; render(); }); effects.addView(open); col.addView(effects, matchWrap(dp(3))); }
        return col;
    }'''

PLAY_ATTACKS = r'''
    private LinearLayout attacksPage() {
        LinearLayout col = referencePage(); col.addView(referenceBand("АТАКИ")); RuleItem cls = classItem(); int count = 0;
        for (RuleItem item : inventoryItems()) { if (!"weapon".equalsIgnoreCase(item.subtype)) continue; count++; int attack = DerivedStats.attack(state, stats, cls, item); String damage = DerivedStats.damage(stats, item); boolean agile = hasTrait(item, "agile"); int p2 = agile ? -4 : -5, p3 = agile ? -8 : -10; LinearLayout card = referencePanel(); LinearLayout head = row(); head.setGravity(Gravity.CENTER_VERTICAL); TextView name = text(RuNames.shortName(item.name), 17, true); name.setTextColor(ACCENT); head.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1)); TextView dmg = text(damage, 16, true); dmg.setTextColor(TEXT); head.addView(dmg); card.addView(head); if (!item.traits.isEmpty()) card.addView(referenceMeta(item.traitsLine())); LinearLayout rolls = row(); int[] bonuses = {attack, attack + p2, attack + p3}; for (int i=0;i<bonuses.length;i++) { final int bonus=bonuses[i]; Button b=attackButton((i+1)+"", signed(bonus)); b.setOnClickListener(v -> rollD20(RuNames.shortName(item.name), bonus)); rolls.addView(b, weighted(dp(1))); } card.addView(rolls); LinearLayout actions = row(); Button damageButton = compactButton("УРОН  " + damage); damageButton.setOnClickListener(v -> toast(RuNames.shortName(item.name) + ": " + damage)); actions.addView(damageButton, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1)); Button edit = compactButton("РЕД."); edit.setOnClickListener(v -> openItem(item)); actions.addView(edit, fixed(dp(72))); card.addView(actions); col.addView(card, matchWrap(dp(3))); }
        if (count == 0) { LinearLayout empty=referencePanel(); empty.addView(text("Нет оружия",16,true)); empty.addView(referenceMeta("Добавь оружие в разделе «Снаряжение».")); Button add=compactButton("ДОБАВИТЬ ОРУЖИЕ"); add.setOnClickListener(v -> openCatalog("weapon")); empty.addView(add); col.addView(empty,matchWrap(dp(3))); } return col;
    }'''

PLAY_DEFENSES = r'''
    private LinearLayout defensesPage() {
        LinearLayout col = referencePage(); col.addView(referenceBand("ЗАЩИТА")); LinearLayout top = row(); top.addView(referenceMetric("КД", String.valueOf(state.ac)), weighted(dp(1))); top.addView(referenceMetric("СТОЙК.", signed(state.fortitude)), weighted(dp(1))); top.addView(referenceMetric("РЕФЛ.", signed(state.reflex)), weighted(dp(1))); top.addView(referenceMetric("ВОЛЯ", signed(state.will)), weighted(dp(1))); col.addView(top,matchWrap(dp(2)));
        col.addView(referenceBand("БРОНЯ")); RuleItem armor=equippedArmor(); LinearLayout armorCard=referencePanel(); if (armor==null) { armorCard.addView(referenceMeta("Без брони")); Button add=compactButton("ВЫБРАТЬ БРОНЮ"); add.setOnClickListener(v -> openCatalog("armor")); armorCard.addView(add); } else { TextView n=text(RuNames.shortName(armor.name),17,true); n.setTextColor(ACCENT); armorCard.addView(n); armorCard.addView(referencePair("Бонус КД / лимит ЛОВ", signed(armor.meta.optInt("acBonus",0)) + " / " + armor.meta.optInt("dexCap",99))); armorCard.addView(referencePair("Штраф скорости", String.valueOf(armor.meta.optInt("speedPenalty",0)))); if(!armor.traits.isEmpty())armorCard.addView(referenceMeta(armor.traitsLine())); armorCard.setOnClickListener(v -> openItem(armor)); } col.addView(armorCard,matchWrap(dp(3)));
        col.addView(referenceBand("ЩИТ")); RuleItem shieldItem=firstSubtype("shield"); LinearLayout shield=referencePanel(); TextView sn=text(shieldItem==null?"Щит не выбран":RuNames.shortName(shieldItem.name),17,true); sn.setTextColor(ACCENT); shield.addView(sn); TextView raised=referenceAction("Поднять щит",stats.shieldRaised?"ПОДНЯТ • бонус КД активен":"ОПУЩЕН"); raised.setOnClickListener(v -> { stats.shieldRaised=!stats.shieldRaised; stats.save(this); syncDerived(); state.save(this); render(); }); shield.addView(raised); if(shieldItem!=null){ shield.addView(referencePair("Твёрдость",String.valueOf(shieldItem.meta.optInt("hardness",0)))); shield.addView(referencePair("ОЗ щита",String.valueOf(shieldItem.meta.optInt("hp",0)))); } else { Button add=compactButton("ДОБАВИТЬ ЩИТ"); add.setOnClickListener(v -> openCatalog("shield")); shield.addView(add); } col.addView(shield,matchWrap(dp(3)));
        col.addView(referenceBand("СОСТОЯНИЯ")); LinearLayout active=referencePanel(); int n=0; Iterator<String> it=state.conditions.keys(); while(it.hasNext()){String id=it.next();int value=state.conditions.optInt(id,0);RuleItem item=store.findById(id);if(value<=0||item==null)continue;n++;active.addView(conditionRow(item,value));} if(n==0)active.addView(referenceMeta("Нет активных состояний.")); TextView manage=referenceAction("Управление состояниями","открыть Эффекты");manage.setOnClickListener(v->{screen="effects";render();});active.addView(manage);col.addView(active,matchWrap(dp(3))); return col;
    }'''

PLAY_SKILLS = r'''
    private LinearLayout skillsPage() {
        LinearLayout col=referencePage(); col.addView(referenceBand("НАВЫКИ")); LinearLayout table=referencePanel(); for(String[] skill:SKILLS){ int rank=runtime.rank(state,skill[0]); int bonus=DerivedStats.skill(state,stats,skill[0]); LinearLayout r=row(); r.setGravity(Gravity.CENTER_VERTICAL); r.setPadding(dp(6),dp(5),dp(6),dp(5)); TextView name=text(skill[1],14,true); r.addView(name,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1)); TextView rankView=text(shortRank(rank),10,true); rankView.setGravity(Gravity.CENTER); rankView.setTextColor(rank>0?ACCENT:MUTED); rankView.setMinWidth(dp(64)); r.addView(rankView); Button roll=compactButton(signed(bonus)); roll.setOnClickListener(v->rollD20(skill[1],bonus)); r.addView(roll,fixed(dp(68))); table.addView(r); table.addView(divider()); } col.addView(table,matchWrap(dp(3))); return col;
    }'''

PLAY_FEATS = r'''
    private LinearLayout featsPage() {
        LinearLayout col=referencePage(); col.addView(referenceBand("ФИТЫ И ОСОБЕННОСТИ")); Set<String> seen=new HashSet<>(); int total=0; for(int level=1;level<=state.level;level++){ LinearLayout group=referencePanel(); int count=0; for(RuleItem item:runtime.allItems()){ if(seen.contains(item.id))continue; boolean feat="feat".equals(item.category); boolean feature="class-feature".equals(item.category); if(!feat&&!feature)continue; int at=feature?runtime.automaticLevel(item.id):Math.max(1,item.level); if(at!=level)continue; seen.add(item.id); count++; total++; TextView row=referenceAction((feature?"◆ ":"• ")+RuNames.shortName(item.name),feature?"особенность класса":featMeta(item)); row.setOnClickListener(v->ruleDetail(item)); group.addView(row); } if(count>0){col.addView(referenceBand("УРОВЕНЬ "+level));col.addView(group,matchWrap(dp(2)));} } if(total==0){LinearLayout empty=referencePanel();empty.addView(referenceMeta("Выбранных фитов пока нет."));col.addView(empty,matchWrap(dp(3)));} LinearLayout actions=row(); Button build=compactButton("В СБОРКУ");build.setOnClickListener(v->startActivity(new Intent(this,ReferenceBuildActivity.class)));actions.addView(build,weighted(dp(1)));Button browse=compactButton("ВСЕ ФИТЫ");browse.setOnClickListener(v->openCatalog("feat"));actions.addView(browse,weighted(dp(1)));col.addView(actions,matchWrap(dp(3)));return col;
    }'''

PLAY_GEAR = r'''
    private LinearLayout gearPage() {
        LinearLayout col=referencePage(); col.addView(referenceBand("СНАРЯЖЕНИЕ")); BulkRules.Summary bulk=BulkRules.calculate(store,state,stats,inventory); LinearLayout money=row(); money.addView(referenceMetric("ЗМ",String.valueOf(inventory.gp)),weighted(dp(1))); money.addView(referenceMetric("СМ",String.valueOf(inventory.sp)),weighted(dp(1))); money.addView(referenceMetric("ММ",String.valueOf(inventory.cp)),weighted(dp(1))); money.addView(referenceMetric("НАГР.",BulkRules.label(bulk.totalLight)),weighted(dp(1))); col.addView(money,matchWrap(dp(2))); col.addView(referenceMeta("Нагрузка: "+bulk.status())); addInventorySection(col,"ОРУЖИЕ","weapon"); addInventorySection(col,"БРОНЯ И ЩИТЫ","armor,shield"); addInventorySection(col,"ПРЕДМЕТЫ","other"); if(!bulk.containers.isEmpty()){col.addView(referenceBand("КОНТЕЙНЕРЫ"));LinearLayout containers=referencePanel();for(BulkRules.ContainerLoad load:bulk.containers)containers.addView(referencePair(RuNames.shortName(load.item.name),BulkRules.label(load.countedContentsLight)+(load.capacityLight>0?" / "+BulkRules.label(load.capacityLight):"")+(load.overCapacity?" • ПЕРЕПОЛНЕН":"")));col.addView(containers,matchWrap(dp(3)));} LinearLayout actions=row();Button w=compactButton("+ ОРУЖИЕ");w.setOnClickListener(v->openCatalog("weapon"));actions.addView(w,weighted(dp(1)));Button a=compactButton("+ БРОНЯ");a.setOnClickListener(v->openCatalog("armor"));actions.addView(a,weighted(dp(1)));Button e=compactButton("+ ПРЕДМЕТ");e.setOnClickListener(v->openCatalog("equipment"));actions.addView(e,weighted(dp(1)));col.addView(actions,matchWrap(dp(3)));return col;
    }'''

PLAY_PETS = r'''
    private LinearLayout petsPage() {
        LinearLayout col=referencePage(); col.addView(referenceBand("ПИТОМЦЫ")); String[] types={"Животный-компаньон","Фамильяр","Эйдолон","Конструкт","Последователь"}; for(String type:types){int count=0;LinearLayout group=referencePanel();for(CompanionState.Companion c:companions.items){if(!type.equalsIgnoreCase(c.type))continue;count++;group.addView(companionCard(c));}if(count>0){col.addView(referenceBand(type.toUpperCase(Locale.ROOT)));col.addView(group,matchWrap(dp(3)));}} LinearLayout add=referencePanel();add.addView(referenceMeta("Спутник открывается отдельным игровым листом."));for(String type:types){Button b=compactButton("+ "+type);b.setOnClickListener(v->{CompanionState.Companion c=companions.add(type,state.level);companions.save(this);if(c!=null)openCompanion(c.id);else render();});add.addView(b,matchWrap(dp(1)));}col.addView(add,matchWrap(dp(3)));return col;
    }'''

PLAY_EFFECTS = r'''
    private LinearLayout effectsPage() {
        LinearLayout col=referencePage(); col.addView(referenceBand("ЭФФЕКТЫ И СОСТОЯНИЯ")); LinearLayout active=referencePanel();int count=0;Iterator<String>it=state.conditions.keys();while(it.hasNext()){String id=it.next();RuleItem item=store.findById(id);int value=state.conditions.optInt(id,0);if(item==null||value<=0)continue;count++;active.addView(conditionRow(item,value));}if(count==0)active.addView(referenceMeta("Активных состояний нет."));col.addView(active,matchWrap(dp(3)));Button add=compactButton("+ ДОБАВИТЬ СОСТОЯНИЕ");add.setOnClickListener(v->openCatalog("condition"));col.addView(add,matchWrap(dp(3)));return col;
    }'''

PLAY_HELPERS = r'''
    // GRAN_REFERENCE_PARITY_7_0
    private TextView topAction(String value){TextView v=text(value,10,true);v.setTextColor(Color.rgb(236,205,169));v.setGravity(Gravity.CENTER);v.setPadding(dp(8),dp(6),dp(8),dp(6));return v;}
    private void changeLevel(int delta){int next=clamp(state.level+delta,1,20);if(next==state.level)return;state.level=next;state.save(this);StatsState.recalculate(state);loadState();setContentView(shell());render();}
    private LinearLayout referencePage(){LinearLayout l=column();l.setPadding(dp(6),dp(5),dp(6),dp(24));return l;}
    private LinearLayout referencePanel(){LinearLayout l=column();l.setPadding(dp(7),dp(6),dp(7),dp(6));l.setBackground(round(PANEL,2,BORDER));return l;}
    private TextView referenceBand(String value){TextView v=text(value,11,true);v.setTextColor(Color.WHITE);v.setPadding(dp(8),dp(5),dp(8),dp(5));v.setBackgroundColor(ACCENT);LinearLayout.LayoutParams p=matchWrap();p.setMargins(0,dp(3),0,dp(2));v.setLayoutParams(p);return v;}
    private TextView referenceMeta(String value){TextView v=text(value,11,false);v.setTextColor(MUTED);v.setPadding(dp(5),dp(3),dp(5),dp(4));return v;}
    private TextView referencePair(String left,String right){TextView v=text(left+"    "+right,13,false);v.setPadding(dp(6),dp(6),dp(6),dp(6));v.setBackground(round(PANEL_2,1,BORDER));LinearLayout.LayoutParams p=matchWrap(dp(1));v.setLayoutParams(p);return v;}
    private TextView referenceAction(String left,String right){TextView v=text(left+"\n"+right,13,false);v.setPadding(dp(7),dp(6),dp(7),dp(6));v.setBackground(round(PANEL_2,1,BORDER));LinearLayout.LayoutParams p=matchWrap(dp(1));v.setLayoutParams(p);return v;}
    private View referenceMetric(String label,String value){LinearLayout c=column();c.setGravity(Gravity.CENTER);c.setPadding(dp(3),dp(6),dp(3),dp(6));c.setBackground(round(PANEL,1,BORDER));TextView l=text(label,9,true);l.setTextColor(MUTED);l.setGravity(Gravity.CENTER);TextView v=text(value,17,true);v.setTextColor(ACCENT);v.setGravity(Gravity.CENTER);c.addView(l);c.addView(v);return c;}
    private View referenceAbility(String label,int score,int mod){LinearLayout c=column();c.setGravity(Gravity.CENTER);c.setPadding(dp(2),dp(5),dp(2),dp(5));c.setBackground(round(PANEL,1,BORDER));TextView l=text(label,9,true);l.setTextColor(MUTED);l.setGravity(Gravity.CENTER);TextView s=text(String.valueOf(score),16,true);s.setTextColor(TEXT);s.setGravity(Gravity.CENTER);TextView m=text(signed(mod),10,true);m.setTextColor(ACCENT);m.setGravity(Gravity.CENTER);c.addView(l);c.addView(s);c.addView(m);return c;}
    private Button compactButton(String value){Button b=button(value);b.setTextSize(11);b.setMinimumHeight(0);b.setMinHeight(dp(38));b.setPadding(dp(5),dp(4),dp(5),dp(4));return b;}
    private Button attackButton(String label,String bonus){Button b=compactButton(label+"  "+bonus);b.setTextColor(ACCENT);return b;}
    private String shortRank(int rank){switch(rank){case 1:return"ОБУЧ.";case 2:return"ЭКСП.";case 3:return"МАСТ.";case 4:return"ЛЕГ.";default:return"—";}}
    private void openCatalog(String mode){Intent i=new Intent(this,ReferenceCatalogActivity.class);i.putExtra("mode",mode);i.putExtra("maxLevel",state.level);startActivity(i);}
    private void openItem(RuleItem item){if(item==null)return;Intent i=new Intent(this,ReferenceItemActivity.class);i.putExtra("itemId",item.id);startActivity(i);}
    private void openCompanion(String id){Intent i=new Intent(this,ReferenceCompanionActivity.class);i.putExtra("companionId",id);startActivity(i);}
    private void addInventorySection(LinearLayout col,String title,String filter){LinearLayout group=referencePanel();int count=0;for(RuleItem item:inventoryItems()){boolean match;if("other".equals(filter))match=!"weapon".equalsIgnoreCase(item.subtype)&&!"armor".equalsIgnoreCase(item.subtype)&&!"shield".equalsIgnoreCase(item.subtype);else match=(","+filter.toLowerCase(Locale.ROOT)+",").contains(","+(item.subtype==null?"":item.subtype.toLowerCase(Locale.ROOT))+",");if(!match)continue;count++;int qty=inventory.quantity(item.id);String container=inventory.containerFor(item.id);String meta=translatedSubtype(item.subtype)+" • "+BulkRules.itemBulkLabel(item,qty)+(qty>1?" • ×"+qty:"")+(!container.isEmpty()?" • в контейнере":"");TextView row=referenceAction(RuNames.shortName(item.name),meta);row.setOnClickListener(v->openItem(item));group.addView(row);}if(count>0){col.addView(referenceBand(title));col.addView(group,matchWrap(dp(2)));}}
'''

BUILD_SHELL = r'''
    private View shell() {
        LinearLayout root=column(); root.setBackgroundColor(BG); LinearLayout top=column(); top.setBackgroundColor(TOP); LinearLayout line=row(); line.setGravity(Gravity.CENTER_VERTICAL); line.setPadding(dp(8),dp(5),dp(8),dp(4)); TextView back=text("‹",24,true);back.setTextColor(Color.WHITE);back.setGravity(Gravity.CENTER);back.setMinWidth(dp(42));back.setOnClickListener(v->{CharacterProfiles.saveCurrent(this);startActivity(new Intent(this,FrontPageActivity.class));finish();});line.addView(back); LinearLayout id=column();headerName=text("",17,true);headerName.setTextColor(Color.WHITE);id.addView(headerName);headerStats=text("",10,false);headerStats.setTextColor(Color.rgb(210,211,212));id.addView(headerStats);line.addView(id,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1)); TextView play=buildTopAction("ИГРА");play.setOnClickListener(v->{Intent i=new Intent(this,ReferencePlayActivity.class);i.putExtra("screen","character");startActivity(i);});line.addView(play); TextView more=buildTopAction("⋮");more.setTextSize(22);more.setOnClickListener(v->startActivity(new Intent(this,ReferenceMoreActivity.class)));line.addView(more);top.addView(line);root.addView(top,matchWrap());
        LinearLayout mode=row();mode.setBackgroundColor(TOP_2);mode.setPadding(dp(3),dp(2),dp(3),dp(2));TextView levels=modeTab("УРОВНИ",true),skills=modeTab("НАВЫКИ",false),reference=modeTab("СПРАВОЧНИК",false);levels.setOnClickListener(v->{section="levels";render();});skills.setOnClickListener(v->{section="skills";render();});reference.setOnClickListener(v->{section="reference";render();});mode.addView(levels,weighted(dp(1)));mode.addView(skills,weighted(dp(1)));mode.addView(reference,weighted(dp(1)));root.addView(mode,matchWrap());
        LinearLayout arrows=row();arrows.setGravity(Gravity.CENTER_VERTICAL);arrows.setPadding(dp(5),dp(3),dp(5),dp(2));arrows.setBackgroundColor(PANEL_2);Button prev=compactBuildButton("‹");prev.setOnClickListener(v->{selectedLevel=Math.max(1,selectedLevel-1);section="levels";render();});arrows.addView(prev,fixed(dp(48)));TextView lv=text("УРОВЕНЬ",10,true);lv.setGravity(Gravity.CENTER);lv.setTextColor(MUTED);arrows.addView(lv,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));Button next=compactBuildButton("›");next.setOnClickListener(v->{selectedLevel=Math.min(20,selectedLevel+1);section="levels";render();});arrows.addView(next,fixed(dp(48)));root.addView(arrows,matchWrap()); HorizontalScrollView levelScroll=new HorizontalScrollView(this);levelScroll.setHorizontalScrollBarEnabled(false);levelScroll.setBackgroundColor(PANEL_2);levelNav=row();levelNav.setPadding(dp(4),dp(2),dp(4),dp(4));levelScroll.addView(levelNav);root.addView(levelScroll,matchWrap()); content=new FrameLayout(this);root.addView(content,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));return root;
    }'''

BUILD_RENDER = r'''
    private void render() {
        if(content==null)return;stats=StatsState.load(this);runtime=RuleRuntime.resolve(store,state,stats);headerName.setText(state.name==null||state.name.trim().isEmpty()?"Новый персонаж":state.name);String cls=state.className.isEmpty()?"Класс не выбран":RuNames.shortName(state.className);int[]done=completion();headerStats.setText("СБОРКА • ур. "+state.level+" • "+cls+" • "+done[0]+"/"+done[1]);rebuildLevelNavigation();content.removeAllViews();View page="skills".equals(section)?skillsPage():"reference".equals(section)?referencePage():levelPage(selectedLevel);content.addView(scroll(page));refreshModeTabs();
    }'''

BUILD_LEVEL = r'''
    private LinearLayout levelPage(int level) {
        LinearLayout col=referenceBuildPage(); LinearLayout header=referenceBuildPanel();LinearLayout h=row();h.setGravity(Gravity.CENTER_VERTICAL);TextView title=text("УРОВЕНЬ "+level,20,true);title.setTextColor(ACCENT);h.addView(title,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));h.addView(badge(level==state.level?"ТЕКУЩИЙ":level<state.level?"ПРОЙДЕН":"БУДУЩИЙ",level<=state.level?GOOD:MUTED));header.addView(h);if(level!=state.level){Button set=compactBuildButton("СДЕЛАТЬ ТЕКУЩИМ");set.setOnClickListener(v->{state.level=targetLevel(level);saveAndRevalidate();selectedLevel=state.level;});header.addView(set);}col.addView(header,matchWrap(dp(2)));
        if(level==1){col.addView(referenceBuildBand("ОСНОВА ПЕРСОНАЖА"));LinearLayout base=referenceBuildPanel();base.addView(baseChoice("РОД",state.ancestry,"ancestry"));base.addView(baseChoice("НАСЛЕДИЕ",state.choiceName("base:heritage"),"heritage"));base.addView(baseChoice("ПРЕДЫСТОРИЯ",state.background,"background"));base.addView(baseChoice("КЛАСС",state.className,"class"));col.addView(base,matchWrap(dp(2)));col.addView(referenceBuildBand("ХАРАКТЕРИСТИКИ"));LinearLayout abilities=row();for(String[]a:ABILITIES)abilities.addView(abilityCell(a[1],stats.abilityScore(a[0]),stats.ability(a[0])),weighted(dp(1)));col.addView(abilities,matchWrap(dp(2)));}
        List<RuleRuntime.ChoicePrompt>prompts=runtime.choices();if(!prompts.isEmpty()){col.addView(referenceBuildBand("ОБЯЗАТЕЛЬНЫЕ ВЫБОРЫ"));LinearLayout choices=referenceBuildPanel();int shown=0;for(RuleRuntime.ChoicePrompt prompt:prompts){choices.addView(ruleChoiceRow(prompt));if(++shown>=24)break;}col.addView(choices,matchWrap(dp(2)));}
        col.addView(referenceBuildBand("ФИТЫ И ОСОБЕННОСТИ"));LinearLayout features=referenceBuildPanel();int rows=0;for(RuleItem item:runtime.allItems()){if(!runtime.isAutomatic(item.id)||runtime.automaticLevel(item.id)!=level)continue;TextView auto=referenceBuildAction("✓  "+RuNames.shortName(item.name),"автоматически");auto.setOnClickListener(v->ruleDetail(item,null));features.addView(auto);rows++;}RuleItem cls=classItem();if(hasFeatSlot(cls,"class",level)){features.addView(featSlot(level,"КЛАССОВЫЙ / АРХЕТИПНЫЙ ФИТ","class"));rows++;}if(hasFeatSlot(cls,"ancestry",level)){features.addView(featSlot(level,"ФИТ РОДА","ancestry"));rows++;}if(hasFeatSlot(cls,"skill",level)){features.addView(featSlot(level,"ФИТ НАВЫКА","skill"));rows++;}if(hasFeatSlot(cls,"general",level)){features.addView(featSlot(level,"ОБЩИЙ ФИТ","general"));rows++;}if(hasSkillIncreaseSlot(cls,level)){TextView skill=referenceBuildAction("↑  ПОВЫШЕНИЕ НАВЫКА","открыть навыки");skill.setOnClickListener(v->{section="skills";render();});features.addView(skill);rows++;}if(isAbilityBoostLevel(level)){TextView boosts=referenceBuildAction("◆  ПОВЫШЕНИЯ ХАРАКТЕРИСТИК",level==1?"выборы создания персонажа":"4 повышения");boosts.setOnClickListener(v->showAbilityPrompts());features.addView(boosts);rows++;}if(rows==0)features.addView(note("На этом уровне нет отдельных решений."));col.addView(features,matchWrap(dp(2))); return col;
    }'''

BUILD_REFERENCE = r'''
    private LinearLayout referencePage() {
        LinearLayout col=referenceBuildPage();col.addView(referenceBuildBand("СПРАВОЧНИК И ВЫБОРЫ"));LinearLayout groups=referenceBuildPanel(); String[][] entries={{"РОДЫ","ancestry"},{"НАСЛЕДИЯ","heritage"},{"ПРЕДЫСТОРИИ","background"},{"КЛАССЫ","class"},{"ФИТЫ","feat"},{"ЗАКЛИНАНИЯ","spell"},{"ОРУЖИЕ","weapon"},{"БРОНЯ","armor"},{"ЩИТЫ","shield"},{"СНАРЯЖЕНИЕ","equipment"},{"СОСТОЯНИЯ","condition"},{"ДЕЙСТВИЯ","action"}}; for(String[]entry:entries){TextView row=referenceBuildAction(entry[0],"открыть полный каталог");String mode=entry[1];row.setOnClickListener(v->{Intent i=new Intent(this,ReferenceCatalogActivity.class);i.putExtra("mode",mode);i.putExtra("maxLevel",state.level);startActivity(i);});groups.addView(row);}TextView more=referenceBuildAction("ЕЩЁ","языки, Lore, спутники, импорт / экспорт, источники");more.setOnClickListener(v->startActivity(new Intent(this,ReferenceMoreActivity.class)));groups.addView(more);col.addView(groups,matchWrap(dp(2)));return col;
    }'''

BUILD_HELPERS = r'''
    // GRAN_REFERENCE_PARITY_7_0
    private TextView buildTopAction(String value){TextView v=text(value,10,true);v.setTextColor(Color.rgb(236,205,169));v.setGravity(Gravity.CENTER);v.setPadding(dp(8),dp(6),dp(8),dp(6));return v;}
    private Button compactBuildButton(String value){Button b=button(value);b.setTextSize(11);b.setMinimumHeight(0);b.setMinHeight(dp(36));b.setPadding(dp(5),dp(4),dp(5),dp(4));return b;}
    private LinearLayout referenceBuildPage(){LinearLayout l=column();l.setPadding(dp(6),dp(5),dp(6),dp(24));return l;}
    private LinearLayout referenceBuildPanel(){LinearLayout l=column();l.setPadding(dp(7),dp(6),dp(7),dp(6));l.setBackground(round(PANEL,2,BORDER));return l;}
    private TextView referenceBuildBand(String value){TextView v=text(value,11,true);v.setTextColor(Color.WHITE);v.setPadding(dp(8),dp(5),dp(8),dp(5));v.setBackgroundColor(ACCENT);LinearLayout.LayoutParams p=matchWrap();p.setMargins(0,dp(3),0,dp(2));v.setLayoutParams(p);return v;}
    private TextView referenceBuildAction(String left,String right){TextView v=text(left+"\n"+right,13,false);v.setPadding(dp(7),dp(6),dp(7),dp(6));v.setBackground(round(PANEL_2,1,BORDER));LinearLayout.LayoutParams p=matchWrap(dp(1));v.setLayoutParams(p);return v;}
'''


def patch_play():
    src=PLAY.read_text(encoding='utf-8')
    for sig,repl in [('    private View shell()',PLAY_SHELL),('    private void render()',PLAY_RENDER),('    private LinearLayout characterPage()',PLAY_CHARACTER),('    private LinearLayout attacksPage()',PLAY_ATTACKS),('    private LinearLayout defensesPage()',PLAY_DEFENSES),('    private LinearLayout skillsPage()',PLAY_SKILLS),('    private LinearLayout featsPage()',PLAY_FEATS),('    private LinearLayout gearPage()',PLAY_GEAR),('    private LinearLayout petsPage()',PLAY_PETS),('    private LinearLayout effectsPage()',PLAY_EFFECTS)]: src=replace_method(src,sig,repl)
    src=src.replace('"signature" : "репертуар"','"ключевое" : "репертуар"').replace('"BULK"','"НАГР."')
    src=append_helpers(src,PLAY_HELPERS); PLAY.write_text(src,encoding='utf-8')


def patch_build():
    src=BUILD.read_text(encoding='utf-8')
    for sig,repl in [('    private View shell()',BUILD_SHELL),('    private void render()',BUILD_RENDER),('    private LinearLayout levelPage(int level)',BUILD_LEVEL),('    private LinearLayout referencePage()',BUILD_REFERENCE)]: src=replace_method(src,sig,repl)
    src=append_helpers(src,BUILD_HELPERS); BUILD.write_text(src,encoding='utf-8')


def patch_gradle():
    s=GRADLE.read_text(encoding='utf-8'); s=re.sub(r'versionCode\s+\d+','versionCode 700',s); s=re.sub(r"versionName\s+'[^']+'","versionName '7.0.0'",s); GRADLE.write_text(s,encoding='utf-8')


def main():
    patch_play(); patch_build(); patch_gradle(); print('Applied Gran 7.0 reference-parity shell and dense workflows')


if __name__=='__main__': main()
