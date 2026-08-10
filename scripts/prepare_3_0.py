#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
V2 = ROOT / 'app/src/main/java/ru/gran/edge2e/MainActivityV2.java'
V3 = ROOT / 'app/src/main/java/ru/gran/edge2e/MainActivityV3.java'


def sub_once(text, pattern, repl, label):
    out, n = re.subn(pattern, repl, text, count=1, flags=re.S)
    if n != 1:
        raise SystemExit(f'3.0 patch missing anchor: {label}')
    return out


def patch_v3():
    s = V3.read_text(encoding='utf-8')
    shell = r'''    private View shell() {
        LinearLayout root = column();
        root.setBackgroundColor(BG);

        LinearLayout top = column();
        top.setPadding(dp(14), dp(9), dp(14), dp(8));
        top.setBackgroundColor(HEADER);
        LinearLayout titleLine = row();
        titleLine.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("ГРАНЬ 2e", 21, true);
        title.setTextColor(Color.WHITE);
        titleLine.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView mode = text("СБОРКА • ПЛАН 1–20", 11, true);
        mode.setTextColor(Color.rgb(242, 211, 183));
        titleLine.addView(mode);
        top.addView(titleLine);
        subtitle = text("", 12, false);
        subtitle.setTextColor(Color.rgb(235, 218, 210));
        top.addView(subtitle);
        root.addView(top, matchWrap());

        LinearLayout modes = row();
        modes.setPadding(dp(6), dp(5), dp(6), dp(5));
        modes.setBackgroundColor(HEADER_DARK);
        TextView buildMode = tab("СБОРКА", true);
        TextView playMode = tab("ИГРА", false);
        TextView heroes = tab("ГЕРОИ", false);
        playMode.setOnClickListener(v -> {
            Intent i = new Intent(this, MainActivityV2.class); i.putExtra("screen", "sheet"); startActivity(i);
        });
        heroes.setOnClickListener(v -> {
            Intent i = new Intent(this, MainActivityV2.class); i.putExtra("screen", "profiles"); startActivity(i);
        });
        modes.addView(buildMode, wrapWrap(dp(2)));
        modes.addView(playMode, wrapWrap(dp(2)));
        modes.addView(heroes, wrapWrap(dp(2)));
        root.addView(modes, matchWrap());

        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setBackgroundColor(Color.rgb(104, 30, 45));
        LinearLayout nav = row();
        nav.setPadding(dp(5), dp(4), dp(5), dp(4));
        nav(nav, "ПЛАН 1–20", "build");
        nav(nav, "НАВЫКИ", "skills");
        nav(nav, "СПРАВОЧНИК", "reference");
        hsv.addView(nav);
        root.addView(hsv, matchWrap());

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        return root;
    }

'''
    s = sub_once(s, r'    private View shell\(\) \{.*?\n    private void nav\(', shell + '    private void nav(', 'V3 shell')
    V3.write_text(s, encoding='utf-8')


def patch_v2():
    s = V2.read_text(encoding='utf-8')
    s = s.replace('    private InventoryState inventory;\n', '    private InventoryState inventory;\n    private CompanionState companions;\n', 1)
    s = s.replace('        inventory = InventoryState.load(this);\n', '        inventory = InventoryState.load(this);\n        companions = CompanionState.load(this);\n', 1)

    shell = r'''    private View createShell() {
        LinearLayout root = column(); root.setBackgroundColor(BG);
        LinearLayout head = column(); head.setPadding(dp(16), dp(10), dp(16), dp(8)); head.setBackgroundColor(HEADER);
        TextView title = text("ГРАНЬ 2e", 22, true); title.setTextColor(Color.WHITE); head.addView(title);
        summary = text("", 13, false); summary.setTextColor(Color.rgb(235, 218, 210)); head.addView(summary);
        root.addView(head, matchWrap());

        LinearLayout modes = row(); modes.setPadding(dp(6), dp(5), dp(6), dp(5)); modes.setBackgroundColor(HEADER_DARK);
        TextView buildMode = modeTab("СБОРКА", false), playMode = modeTab("ИГРА", true), heroes = modeTab("ГЕРОИ", false);
        buildMode.setOnClickListener(v -> { startActivity(new android.content.Intent(this, MainActivityV3.class)); finish(); });
        heroes.setOnClickListener(v -> { screen = "profiles"; render(); });
        modes.addView(buildMode, wrapWrap(dp(2))); modes.addView(playMode, wrapWrap(dp(2))); modes.addView(heroes, wrapWrap(dp(2)));
        root.addView(modes, matchWrap());

        HorizontalScrollView navScroll = new HorizontalScrollView(this); navScroll.setHorizontalScrollBarEnabled(false); navScroll.setBackgroundColor(HEADER_DARK);
        LinearLayout nav = row(); nav.setPadding(dp(6), dp(4), dp(6), dp(6));
        addNav(nav, "ПЕРСОНАЖ", "sheet"); addNav(nav, "АТАКИ", "attack"); addNav(nav, "ЗАЩИТА", "defense");
        addNav(nav, "НАВЫКИ", "skills"); addNav(nav, "ФИТЫ", "feats"); addNav(nav, "ЗАКЛИНАНИЯ", "spells");
        addNav(nav, "СНАРЯЖЕНИЕ", "equipment"); addNav(nav, "ПИТОМЦЫ", "pets"); addNav(nav, "ЭФФЕКТЫ", "effects");
        navScroll.addView(nav); root.addView(navScroll, matchWrap());
        content = new FrameLayout(this); root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        return root;
    }

'''
    s = sub_once(s, r'    private View createShell\(\) \{.*?\n    private void addNav\(', shell + '    private void addNav(', 'V2 shell')

    render = r'''    private void render() {
        syncDerived(false); updateSummary();
        switch (screen) {
            case "profiles": showProfilesPage(); break;
            case "sheet": showSheet(); break;
            case "attack": showAttack(); break;
            case "defense": showDefense(); break;
            case "skills": showSkills(); break;
            case "feats": showFeats(); break;
            case "spells": showSpells(); break;
            case "equipment": showEquipment(); break;
            case "pets": showPets(); break;
            case "effects": showEffects(); break;
            default: showSheet();
        }
    }

'''
    s = sub_once(s, r'    private void render\(\) \{.*?\n    private void syncDerived\(', render + '    private void syncDerived(', 'V2 render')
    s = s.replace('sectionTitle("ЛИСТ ПЕРСОНАЖА")', 'sectionTitle("ПЕРСОНАЖ")')

    extra = r'''
    // REFERENCE-LIKE PLAY TABS -------------------------------------------------
    private void showFeats() {
        LinearLayout col = page(); col.addView(sectionTitle("ФИТЫ И ОСОБЕННОСТИ"));
        RuleRuntime.Snapshot snap = RuntimeBridge.snapshot(state, stats);
        if (snap == null) { col.addView(note("Движок правил недоступен.")); setContent(scroll(col)); return; }
        LinearLayout chosen = card(); int count = 0;
        for (RuleItem item : snap.allItems()) {
            if (!("feat".equals(item.category) || "class-feature".equals(item.category))) continue;
            count++;
            String meta = ("class-feature".equals(item.category) ? "особенность" : RuNames.shortName(item.subtype)) + (item.level > 0 ? " • ур. " + item.level : "");
            TextView row = actionRow((snap.isAutomatic(item.id) ? "✓ " : "◆ ") + RuNames.shortName(item.name), meta);
            row.setOnClickListener(v -> showRuleDetail(item, null, "Закрыть")); chosen.addView(row, matchWrap(dp(2)));
        }
        if (count == 0) chosen.addView(note("Выбранные фиты и автоматические особенности появятся после сборки персонажа."));
        col.addView(chosen, matchWrap(dp(6)));
        Button build = button("Изменить сборку"); build.setOnClickListener(v -> { startActivity(new android.content.Intent(this, MainActivityV3.class)); finish(); }); col.addView(build, matchWrap(dp(5)));
        setContent(scroll(col));
    }

    private void showEffects() {
        LinearLayout col = page(); col.addView(sectionTitle("ЭФФЕКТЫ И СОСТОЯНИЯ"));
        col.addView(note("Нажатие меняет значение состояния. Долгое нажатие открывает полное правило."));
        LinearLayout conditions = card();
        for (RuleItem item : store.query("condition", 99, "", 100)) {
            int val = state.conditions.optInt(item.id, 0);
            TextView c = actionRow((val > 0 ? "● " : "○ ") + RuNames.shortName(item.name), val > 0 ? "значение " + val + " • нажми для изменения" : "не активно");
            c.setOnClickListener(v -> { int n=(state.conditions.optInt(item.id,0)+1)%5; try { if(n==0) state.conditions.remove(item.id); else state.conditions.put(item.id,n); } catch(Exception ignored){} saveAll(); render(); });
            c.setOnLongClickListener(v -> { showRuleDetail(item, null, "Закрыть"); return true; }); conditions.addView(c, matchWrap(dp(2)));
        }
        col.addView(conditions, matchWrap(dp(6)));

        RuleRuntime.Snapshot snap = RuntimeBridge.snapshot(state, stats);
        if (snap != null) {
            col.addView(sectionTitle("АКТИВНЫЕ ЭФФЕКТЫ СБОРКИ")); LinearLayout effects = card(); int count=0;
            for (RuleItem item : snap.allItems()) {
                JSONArray elems = item.meta.optJSONArray("ruleElements"); if (elems == null || elems.length()==0) continue;
                if ("class".equals(item.category) || "ancestry".equals(item.category) || "background".equals(item.category)) continue;
                count++; TextView r=actionRow(RuNames.shortName(item.name), elems.length()+" правил • "+RuNames.shortName(item.subtype));
                r.setOnClickListener(v -> showRuleDetail(item, null, "Закрыть")); effects.addView(r, matchWrap(dp(2))); if(count>=60) break;
            }
            if(count==0) effects.addView(note("Активные эффекты появятся из особенностей, фитов и экипировки.")); col.addView(effects, matchWrap(dp(6)));
        }
        setContent(scroll(col));
    }

    private void showPets() {
        companions = CompanionState.load(this);
        LinearLayout col = page(); col.addView(sectionTitle("ПИТОМЦЫ И КОМПАНЬОНЫ"));
        RuleRuntime.Snapshot snap = RuntimeBridge.snapshot(state, stats); LinearLayout detected = card(); int detectedCount=0;
        if (snap != null) for (RuleItem item : snap.allItems()) {
            String n=item.name.toLowerCase(Locale.ROOT); String traits=item.traitsLine().toLowerCase(Locale.ROOT);
            if (!(n.contains("familiar") || n.contains("animal companion") || n.contains("eidolon") || traits.contains("familiar") || traits.contains("eidolon"))) continue;
            detectedCount++; TextView r=actionRow("Связано со сборкой: "+RuNames.shortName(item.name), RuNames.shortName(item.subtype)); r.setOnClickListener(v -> showRuleDetail(item,null,"Закрыть")); detected.addView(r);
        }
        if(detectedCount>0) col.addView(detected, matchWrap(dp(5)));

        for (CompanionState.Companion pet : companions.items) {
            LinearLayout c=card(); TextView title=text(pet.name,19,true); title.setTextColor(ACCENT); c.addView(title); c.addView(note(pet.type+" • ур. "+pet.level));
            c.addView(metricRow("ОЗ", pet.hp+" / "+pet.maxHp)); c.addView(metricRow("КД", String.valueOf(pet.ac))); c.addView(metricRow("Атака", signed(pet.attack))); c.addView(metricRow("Урон", pet.damage));
            LinearLayout hp=row(); Button m=miniButton("−5 ОЗ"), p=miniButton("+5 ОЗ"), edit=button("Изменить");
            m.setOnClickListener(v->{pet.hp=clamp(pet.hp-5,0,pet.maxHp);companions.save(this);render();}); p.setOnClickListener(v->{pet.hp=clamp(pet.hp+5,0,pet.maxHp);companions.save(this);render();}); edit.setOnClickListener(v->editCompanion(pet));
            hp.addView(m,weighted(dp(2))); hp.addView(p,weighted(dp(2))); c.addView(hp); c.addView(edit); col.addView(c,matchWrap(dp(6)));
        }
        if(companions.items.isEmpty()) col.addView(note("Здесь можно вести животного-компаньона, фамильяра или эйдолона прямо во время игры."));
        LinearLayout add=row(); for(String type:new String[]{"Животный компаньон","Фамильяр","Эйдолон"}) { Button b=button("+ "+type); b.setOnClickListener(v->{CompanionState.Companion pet=companions.add(type,state.level);companions.save(this);editCompanion(pet);}); add.addView(b,weighted(dp(2))); } col.addView(add,matchWrap(dp(6)));
        setContent(scroll(col));
    }

    private void editCompanion(CompanionState.Companion pet) {
        LinearLayout body=column(); body.setPadding(dp(8),dp(4),dp(8),dp(4));
        EditText name=input(pet.name,"Имя"); EditText type=input(pet.type,"Тип"); EditText hp=input(String.valueOf(pet.maxHp),"Макс. ОЗ"); EditText ac=input(String.valueOf(pet.ac),"КД"); EditText attack=input(String.valueOf(pet.attack),"Атака"); EditText damage=input(pet.damage,"Урон, например 1d8+3"); EditText notes=input(pet.notes,"Заметка");
        body.addView(name);body.addView(type);body.addView(hp);body.addView(ac);body.addView(attack);body.addView(damage);body.addView(notes);
        new AlertDialog.Builder(this).setTitle("Компаньон").setView(scroll(body)).setNegativeButton("Отмена",null).setNeutralButton("Удалить",(d,w)->{companions.remove(pet.id);companions.save(this);render();}).setPositiveButton("Сохранить",(d,w)->{
            pet.name=name.getText().toString().trim().isEmpty()?"Компаньон":name.getText().toString().trim(); pet.type=type.getText().toString().trim(); pet.maxHp=Math.max(1,parseInt(hp.getText().toString(),pet.maxHp)); pet.hp=Math.min(pet.hp,pet.maxHp); pet.ac=parseInt(ac.getText().toString(),pet.ac); pet.attack=parseInt(attack.getText().toString(),pet.attack); pet.damage=damage.getText().toString().trim(); pet.notes=notes.getText().toString(); companions.save(this); render();
        }).show();
    }

    private void showProfilesPage() {
        CharacterProfiles.saveCurrent(this);
        LinearLayout col=page(); col.addView(sectionTitle("МОИ ПЕРСОНАЖИ")); col.addView(note("Каждый герой хранит свою сборку, характеристики, инвентарь и питомцев."));
        String active=CharacterProfiles.activeId(this); List<CharacterProfiles.Profile> profiles=CharacterProfiles.list(this);
        LinearLayout list=card();
        for(CharacterProfiles.Profile p:profiles) {
            TextView r=actionRow((p.id.equals(active)?"● ":"")+p.name,p.summary+(p.id.equals(active)?" • открыт":""));
            r.setOnClickListener(v->{ if(CharacterProfiles.load(this,p.id)){ reloadAll(); screen="sheet"; render(); } });
            r.setOnLongClickListener(v->{ if(p.id.equals(CharacterProfiles.activeId(this))) { toast("Открытого героя сначала переключи на другого."); } else { CharacterProfiles.delete(this,p.id); render(); } return true; }); list.addView(r,matchWrap(dp(2)));
        }
        col.addView(list,matchWrap(dp(6)));
        LinearLayout actions=row(); Button fresh=button("Новый"), save=button("Сохранить"), copy=button("Копия");
        fresh.setOnClickListener(v->{CharacterProfiles.createNew(this);reloadAll();screen="sheet";render();}); save.setOnClickListener(v->{CharacterProfiles.saveCurrent(this);toast("Персонаж сохранён");render();}); copy.setOnClickListener(v->{CharacterProfiles.saveCopy(this);toast("Создана копия");render();});
        actions.addView(fresh,weighted(dp(2)));actions.addView(save,weighted(dp(2)));actions.addView(copy,weighted(dp(2)));col.addView(actions,matchWrap(dp(5))); setContent(scroll(col));
    }

    private void reloadAll() {
        state=CharacterState.load(this); stats=StatsState.load(this); inventory=InventoryState.load(this); companions=CompanionState.load(this); RuntimeBridge.invalidate(); syncDerived(false);
    }

    private TextView modeTab(String label, boolean active) {
        TextView v=text(label,12,true); v.setGravity(Gravity.CENTER); v.setPadding(dp(13),dp(8),dp(13),dp(8));
        v.setTextColor(active?HEADER_DARK:Color.WHITE); v.setBackground(round(active?Color.rgb(242,211,183):HEADER_DARK,5,active?Color.rgb(242,211,183):Color.rgb(119,58,70),1)); return v;
    }

'''
    marker = '    // DATA HELPERS\n'
    if marker not in s:
        raise SystemExit('3.0 patch missing anchor: DATA HELPERS')
    s = s.replace(marker, extra + marker, 1)
    V2.write_text(s, encoding='utf-8')


patch_v3()
patch_v2()
print('Prepared Gran 2e 3.0 reference-style BUILD/PLAY UX')
