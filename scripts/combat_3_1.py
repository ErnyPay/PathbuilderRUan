#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
V2 = ROOT / 'app/src/main/java/ru/gran/edge2e/MainActivityV2.java'

s = V2.read_text(encoding='utf-8')
pattern = re.compile(r'''    // ATTACK\n    private void showAttack\(\) \{.*?\n    // SKILLS''', re.S)
replacement = r'''    // ATTACK
    private void showAttack() {
        syncDerived(true);
        LinearLayout col = page();
        col.addView(sectionTitle("АТАКИ"));
        col.addView(note("Три значения — первая, вторая и третья атака хода. MAP считается автоматически; agile использует 0 / −4 / −8."));
        RuleItem cls = classItem();
        int rows = 0;

        for (int i = 0; i < state.inventory.length(); i++) {
            RuleItem item = store.findById(storedId(state.inventory.optString(i, "")));
            if (item == null || !"weapon".equalsIgnoreCase(item.subtype)) continue;
            rows++;
            col.addView(weaponStrikeCard(item, cls), matchWrap(dp(6)));
        }

        for (CombatRules.Strike strike : CombatRules.grantedStrikes(state, stats)) {
            rows++;
            col.addView(grantedStrikeCard(strike, cls), matchWrap(dp(6)));
        }

        // Every PF2e character can Strike unarmed even when no explicit Strike Rule Element exists.
        if (rows == 0) col.addView(basicUnarmedCard(cls), matchWrap(dp(6)));
        Button gear = button("Открыть снаряжение");
        gear.setOnClickListener(v -> { screen = "equipment"; render(); });
        col.addView(gear, matchWrap(dp(6)));
        setContent(scroll(col));
    }

    private View weaponStrikeCard(RuleItem item, RuleItem cls) {
        int attack = DerivedStats.attack(state, stats, cls, item);
        String damage = DerivedStats.damage(stats, item);
        boolean agile = DerivedStats.hasTrait(item, "agile");
        LinearLayout c = card();
        c.addView(text(RuNames.display(item.name), 19, true));
        String traits = item.traitsLine();
        c.addView(note((traits.isEmpty() ? item.meta.optString("itemCategory", "оружие") : traits) + " • MAP " + mapText(agile)));
        c.addView(metricRow("Бонус атаки", signed(attack)));
        c.addView(metricRow("Урон", damage));
        c.addView(attackButtons(RuNames.shortName(item.name), attack, agile));
        c.addView(damageButtons(RuNames.shortName(item.name), damage));
        return c;
    }

    private View grantedStrikeCard(CombatRules.Strike strike, RuleItem cls) {
        int attack = CombatRules.strikeAttack(state, stats, cls, strike);
        String damage = strike.damageFormula(stats);
        LinearLayout c = card();
        c.addView(text(RuNames.shortName(strike.label), 19, true));
        String traits = String.join(" • ", strike.traits);
        if (strike.range > 0) traits += (traits.isEmpty() ? "" : " • ") + "дистанция " + strike.range + " фт";
        c.addView(note((traits.isEmpty() ? "безоружная атака" : traits) + " • MAP " + mapText(strike.agile())));
        c.addView(metricRow("Бонус атаки", signed(attack)));
        c.addView(metricRow("Урон", damage));
        c.addView(attackButtons(RuNames.shortName(strike.label), attack, strike.agile()));
        c.addView(damageButtons(RuNames.shortName(strike.label), damage));
        return c;
    }

    private View basicUnarmedCard(RuleItem cls) {
        int rank = DerivedStats.classMapRank(cls, "attacks", "unarmed", 0);
        RuleRuntime.Snapshot runtime = RuntimeBridge.snapshot(state, stats);
        if (runtime != null) rank = Math.max(rank, runtime.proficiency("attack:unarmed", rank));
        int attack = Math.max(stats.ability("str"), stats.ability("dex")) + DerivedStats.proficiency(rank, state.level);
        String damage = "1d4" + (stats.ability("str") == 0 ? "" : signedInline(stats.ability("str"))) + " дробящий";
        LinearLayout c = card();
        c.addView(text("Кулак", 19, true));
        c.addView(note("agile • finesse • nonlethal • unarmed • MAP 0 / −4 / −8"));
        c.addView(metricRow("Бонус атаки", signed(attack)));
        c.addView(metricRow("Урон", damage));
        c.addView(attackButtons("Кулак", attack, true));
        c.addView(damageButtons("Кулак", damage));
        return c;
    }

    private View attackButtons(String name, int attack, boolean agile) {
        LinearLayout buttons = row();
        for (int n = 1; n <= 3; n++) {
            final int number = n;
            final int bonus = attack + CombatRules.mapPenalty(agile, n);
            Button b = button(number + "-я " + signed(bonus));
            b.setOnClickListener(v -> {
                int die = random.nextInt(20) + 1;
                toast(name + " • атака " + number + ": " + die + signedInline(bonus) + " = " + (die + bonus));
            });
            buttons.addView(b, weighted(dp(2)));
        }
        return buttons;
    }

    private View damageButtons(String name, String formula) {
        LinearLayout buttons = row();
        Button normal = button("Урон");
        Button critical = button("Критический урон");
        normal.setOnClickListener(v -> toast(name + ": " + formula + " → " + rollFormula(formula)));
        critical.setOnClickListener(v -> {
            int rolled = rollFormula(formula);
            toast(name + " • крит: 2 × " + rolled + " = " + (rolled * 2));
        });
        buttons.addView(normal, weighted(dp(2)));
        buttons.addView(critical, weighted(dp(2)));
        return buttons;
    }

    private String mapText(boolean agile) { return agile ? "0 / −4 / −8" : "0 / −5 / −10"; }

    // DEFENSE
    private void showDefense() {
        syncDerived(true);
        LinearLayout col = page();
        col.addView(sectionTitle("ЗАЩИТА"));
        LinearLayout c = card();
        c.addView(bigStat("КД", state.ac));
        c.addView(bigStat("СТОЙКОСТЬ", state.fortitude));
        c.addView(bigStat("РЕФЛЕКС", state.reflex));
        c.addView(bigStat("ВОЛЯ", state.will));
        c.addView(bigStat("ВОСПРИЯТИЕ", state.perception));
        c.addView(metricRow("Броня", equippedArmor() == null ? "Без брони" : RuNames.shortName(equippedArmor().name)));
        c.addView(metricRow("Щит", stats.shieldRaised ? "Поднят" : "Опущен"));
        col.addView(c, matchWrap(dp(7)));

        List<CombatRules.Iwr> iwr = CombatRules.defenses(state, stats);
        addIwrSection(col, "СОПРОТИВЛЕНИЯ", "resistance", iwr);
        addIwrSection(col, "СЛАБОСТИ", "weakness", iwr);
        addIwrSection(col, "ИММУНИТЕТЫ", "immunity", iwr);
        setContent(scroll(col));
    }

    private void addIwrSection(LinearLayout col, String title, String kind, List<CombatRules.Iwr> all) {
        LinearLayout rows = card();
        int count = 0;
        for (CombatRules.Iwr entry : all) {
            if (!kind.equals(entry.kind)) continue;
            count++;
            String value = "immunity".equals(kind) ? "иммунитет" : String.valueOf(entry.value);
            String detail = value + " • " + RuNames.shortName(entry.source) + (entry.note.isEmpty() ? "" : " • " + entry.note);
            rows.addView(staticRow(combatTypeLabel(entry.type), detail));
        }
        if (count > 0) {
            col.addView(sectionTitle(title));
            col.addView(rows, matchWrap(dp(6)));
        }
    }

    private String combatTypeLabel(String type) {
        switch (type) {
            case "acid": return "Кислота";
            case "bleed": return "Кровотечение";
            case "bludgeoning": return "Дробящий";
            case "cold": return "Холод";
            case "electricity": return "Электричество";
            case "fire": return "Огонь";
            case "force": return "Сила";
            case "mental": return "Ментальный";
            case "piercing": return "Колющий";
            case "poison": return "Яд";
            case "precision": return "Точный урон";
            case "slashing": return "Рубящий";
            case "sonic": return "Звук";
            case "spirit": return "Духовный";
            case "vitality": return "Жизненная энергия";
            case "void": return "Пустота";
            case "physical": return "Физический";
            case "all-damage": return "Весь урон";
            case "critical-hits": return "Критические попадания";
            default: return RuNames.shortName(type.replace('-', ' '));
        }
    }

    // SKILLS'''

s, n = pattern.subn(replacement, s, count=1)
if n != 1:
    raise SystemExit('combat 3.1: ATTACK/DEFENSE block not found after 3.0 patches')
V2.write_text(s, encoding='utf-8')
print('Applied Gran 2e 3.1 PLAY combat: MAP, crits, Strike and IWR')
