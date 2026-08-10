package ru.gran.edge2e;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
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
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public final class MainActivityV2 extends Activity {
    private static final int BG = Color.rgb(15, 22, 29);
    private static final int HEADER = Color.rgb(10, 16, 22);
    private static final int SURFACE = Color.rgb(27, 38, 48);
    private static final int SURFACE_2 = Color.rgb(38, 51, 62);
    private static final int BORDER = Color.rgb(64, 80, 93);
    private static final int TEXT = Color.rgb(240, 239, 232);
    private static final int MUTED = Color.rgb(174, 184, 191);
    private static final int ACCENT = Color.rgb(219, 158, 69);
    private static final int GOOD = Color.rgb(87, 188, 133);
    private static final int BAD = Color.rgb(213, 86, 84);

    private static final String[][] ABILITIES = {
            {"str", "СИЛ"}, {"dex", "ЛОВ"}, {"con", "ТЕЛ"},
            {"int", "ИНТ"}, {"wis", "МДР"}, {"cha", "ХАР"}
    };
    private static final String[][] SKILLS = {
            {"acrobatics", "Акробатика"}, {"arcana", "Аркана"}, {"athletics", "Атлетика"},
            {"crafting", "Ремесло"}, {"deception", "Обман"}, {"diplomacy", "Дипломатия"},
            {"intimidation", "Запугивание"}, {"medicine", "Медицина"}, {"nature", "Природа"},
            {"occultism", "Оккультизм"}, {"performance", "Выступление"}, {"religion", "Религия"},
            {"society", "Общество"}, {"stealth", "Скрытность"}, {"survival", "Выживание"},
            {"thievery", "Воровство"}
    };
    private static final String[] RANKS = {"Нет", "Обучен", "Эксперт", "Мастер", "Легенда"};

    private RuleStore store;
    private CharacterState state;
    private StatsState stats;
    private InventoryState inventory;
    private FrameLayout content;
    private TextView summary;
    private String screen = "build";
    private final Random random = new Random();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new RuleStore(this);
        store.getReadableDatabase();
        state = CharacterState.load(this);
        stats = StatsState.load(this);
        inventory = InventoryState.load(this);
        syncDerived(false);
        setContentView(createShell());
        render();
    }

    private View createShell() {
        LinearLayout root = column(); root.setBackgroundColor(BG);
        LinearLayout head = column(); head.setPadding(dp(16), dp(10), dp(16), dp(8)); head.setBackgroundColor(HEADER);
        TextView title = text("ГРАНЬ 2e", 22, true); title.setTextColor(ACCENT); head.addView(title);
        summary = text("", 13, false); summary.setTextColor(MUTED); head.addView(summary);
        root.addView(head, matchWrap());

        HorizontalScrollView navScroll = new HorizontalScrollView(this); navScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout nav = row(); nav.setPadding(dp(6), dp(4), dp(6), dp(6));
        addNav(nav, "BUILD", "build"); addNav(nav, "ЛИСТ", "sheet"); addNav(nav, "БОЙ", "combat");
        addNav(nav, "АТАКА", "attack"); addNav(nav, "ЗАЩИТА", "defense"); addNav(nav, "НАВЫКИ", "skills");
        addNav(nav, "ЗАКЛИНАНИЯ", "spells"); addNav(nav, "СНАРЯЖЕНИЕ", "equipment"); addNav(nav, "СПРАВОЧНИК", "reference");
        navScroll.addView(nav); root.addView(navScroll, matchWrap());
        content = new FrameLayout(this); root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        return root;
    }

    private void addNav(LinearLayout nav, String label, String target) {
        TextView v = chip(label, screen.equals(target));
        v.setOnClickListener(x -> { screen = target; render(); });
        nav.addView(v, wrapWrap(dp(4)));
    }

    private void render() {
        syncDerived(false); updateSummary();
        switch (screen) {
            case "sheet": showSheet(); break;
            case "combat": showCombat(); break;
            case "attack": showAttack(); break;
            case "defense": showDefense(); break;
            case "skills": showSkills(); break;
            case "spells": showSpells(); break;
            case "equipment": showEquipment(); break;
            case "reference": showReference(); break;
            default: showBuild();
        }
    }

    private void syncDerived(boolean save) {
        RuleItem cls = classItem(); RuleItem ancestry = ancestryItem(); RuleItem armor = equippedArmor();
        state.maxHp = DerivedStats.hp(state, stats, ancestry, cls);
        state.hp = clamp(state.hp, 0, state.maxHp);
        state.ac = DerivedStats.ac(state, stats, cls, armor);
        state.fortitude = DerivedStats.save(state, stats, cls, "fortitude");
        state.reflex = DerivedStats.save(state, stats, cls, "reflex");
        state.will = DerivedStats.save(state, stats, cls, "will");
        state.perception = DerivedStats.perception(state, stats, cls);
        if (save) saveAll();
    }

    private void updateSummary() {
        String cls = state.className.isEmpty() ? "класс не выбран" : RuNames.shortName(state.className);
        summary.setText(state.name + "  •  ур. " + state.level + "  •  " + cls + "  •  ОЗ " + state.hp + "/" + state.maxHp + "  •  КД " + state.ac + "  •  база " + store.count());
    }

    // BUILD
    private void showBuild() {
        LinearLayout col = page();
        col.addView(sectionTitle("ПЛАНИРОВЩИК 1–20"));
        col.addView(note("Выбирай решения в любом порядке. Недоступные фиты скрываются по уровню, классу/роду и проверяемым требованиям; изменение раннего выбора пересчитывает последующие."));

        LinearLayout identity = card();
        identity.addView(selector("Род", state.ancestry, "ancestry", "base:ancestry"));
        identity.addView(selector("Наследие", state.choiceName("base:heritage"), "heritage", "base:heritage"));
        identity.addView(selector("Предыстория", state.background, "background", "base:background"));
        identity.addView(selector("Класс", state.className, "class", "base:class"));
        col.addView(identity, matchWrap(dp(7)));

        col.addView(sectionTitle("МОДИФИКАТОРЫ ХАРАКТЕРИСТИК"));
        col.addView(note("Укажи итоговые модификаторы после бустов рода, предыстории, класса и свободных бустов. От них автоматически считаются ОЗ, КД, спасброски, навыки и атаки."));
        LinearLayout attrs = card();
        LinearLayout row1 = row(), row2 = row();
        for (int i = 0; i < ABILITIES.length; i++) {
            String key = ABILITIES[i][0], label = ABILITIES[i][1];
            View box = abilityBox(key, label);
            (i < 3 ? row1 : row2).addView(box, weighted(dp(3)));
        }
        attrs.addView(row1); attrs.addView(row2); col.addView(attrs, matchWrap(dp(7)));
        addBoostHints(col);

        RuleItem cls = classItem();
        for (int level = 1; level <= 20; level++) {
            LinearLayout levelCard = card();
            TextView lt = text("УРОВЕНЬ " + level, 18, true); lt.setTextColor(level <= state.level ? ACCENT : MUTED); levelCard.addView(lt);
            boolean any = false;
            List<String> features = featuresAt(cls, level);
            for (String feature : features) { levelCard.addView(staticRow("Особенность класса", RuNames.shortName(feature))); any = true; }
            if (RuleEngine.classHasSlot(cls, "classFeatLevels", level, new int[]{2,4,6,8,10,12,14,16,18,20})) {
                levelCard.addView(choiceSlot(level, "Классовый / архетипный фит", "class")); any = true;
            }
            if (RuleEngine.classHasSlot(cls, "ancestryFeatLevels", level, new int[]{1,5,9,13,17})) {
                levelCard.addView(choiceSlot(level, "Фит рода", "ancestry")); any = true;
            }
            if (RuleEngine.classHasSlot(cls, "skillFeatLevels", level, new int[]{2,4,6,8,10,12,14,16,18,20})) {
                levelCard.addView(choiceSlot(level, "Фит навыка", "skill")); any = true;
            }
            if (RuleEngine.classHasSlot(cls, "generalFeatLevels", level, new int[]{3,7,11,15,19})) {
                levelCard.addView(choiceSlot(level, "Общий фит", "general")); any = true;
            }
            if (RuleEngine.classHasSlot(cls, "skillIncreaseLevels", level, new int[]{3,5,7,9,11,13,15,17,19})) {
                TextView skill = actionRow("Повышение навыка", "Открыть навыки"); skill.setOnClickListener(v -> { screen = "skills"; render(); }); levelCard.addView(skill); any = true;
            }
            if (!any) levelCard.addView(note("Нет отдельного выбора на этом уровне."));
            col.addView(levelCard, matchWrap(dp(6)));
        }
        setContent(scroll(col));
    }

    private View selector(String label, String current, String category, String key) {
        String shown = current == null || current.isEmpty() ? "Выбрать" : RuNames.shortName(current);
        TextView v = actionRow(label, shown);
        v.setOnClickListener(x -> showPicker(category, 20, "", 20, null, item -> {
            if ("class".equals(category)) state.className = item.name;
            else if ("ancestry".equals(category)) state.ancestry = item.name;
            else if ("background".equals(category)) { state.background = item.name; applyBackgroundSkills(item); }
            else state.setChoice(key, item);
            revalidate(); syncDerived(true); render();
        }));
        return v;
    }

    private View choiceSlot(int level, String label, String slotCategory) {
        String key = "L" + level + ":" + slotCategory;
        String chosen = state.choiceName(key);
        TextView v = actionRow(label, chosen.isEmpty() ? "Выбрать" : RuNames.shortName(chosen));
        v.setOnClickListener(x -> showPicker("feat", level, slotCategory, level, key, item -> {
            state.setChoice(key, item); revalidate(); syncDerived(true); render();
        }));
        v.setOnLongClickListener(x -> { state.setChoice(key, null); revalidate(); syncDerived(true); render(); return true; });
        return v;
    }

    private View abilityBox(String key, String label) {
        LinearLayout c = column(); c.setGravity(Gravity.CENTER); c.setPadding(dp(4), dp(6), dp(4), dp(6));
        TextView l = text(label, 13, true); l.setTextColor(MUTED); l.setGravity(Gravity.CENTER); c.addView(l);
        TextView value = text(signed(stats.ability(key)), 24, true); value.setTextColor(ACCENT); value.setGravity(Gravity.CENTER); c.addView(value);
        LinearLayout controls = row();
        Button minus = miniButton("−"), plus = miniButton("+");
        minus.setOnClickListener(v -> { stats.setAbility(key, stats.ability(key) - 1); syncDerived(true); render(); });
        plus.setOnClickListener(v -> { stats.setAbility(key, stats.ability(key) + 1); syncDerived(true); render(); });
        controls.addView(minus, weighted()); controls.addView(plus, weighted()); c.addView(controls);
        c.setBackground(round(SURFACE_2, 9, BORDER, 1)); return c;
    }

    private void addBoostHints(LinearLayout col) {
        RuleItem a = ancestryItem(), b = backgroundItem(), cls = classItem();
        LinearLayout hints = card();
        if (a != null) hints.addView(note("Род: ОЗ " + a.meta.optInt("hp", 0) + " • скорость " + a.meta.optInt("speed", 25) + " • бусты " + boostsText(a.meta.optJSONArray("boosts"))));
        if (b != null) hints.addView(note("Предыстория: бусты " + boostsText(b.meta.optJSONArray("boosts")) + " • навыки " + joinJson(b.meta.optJSONArray("trainedSkills"))));
        if (cls != null) hints.addView(note("Ключевая характеристика класса: " + joinJson(cls.meta.optJSONArray("keyAbility")) + " • ОЗ/ур. " + cls.meta.optInt("hp", 0)));
        if (a == null && b == null && cls == null) hints.addView(note("Выбери род, предысторию и класс — здесь появятся их данные для бустов."));
        col.addView(hints, matchWrap(dp(5)));
    }

    // SHEET
    private void showSheet() {
        syncDerived(true);
        LinearLayout col = page(); col.addView(sectionTitle("ЛИСТ ПЕРСОНАЖА"));
        LinearLayout identity = card();
        EditText name = input(state.name, "Имя персонажа"); identity.addView(name);
        name.setOnFocusChangeListener((v, focus) -> { if (!focus) { state.name = name.getText().toString().trim(); saveAll(); updateSummary(); } });
        identity.addView(levelStepper());
        identity.addView(staticRow("Род", state.ancestry.isEmpty() ? "—" : RuNames.shortName(state.ancestry)));
        identity.addView(staticRow("Предыстория", state.background.isEmpty() ? "—" : RuNames.shortName(state.background)));
        identity.addView(staticRow("Класс", state.className.isEmpty() ? "—" : RuNames.shortName(state.className)));
        col.addView(identity, matchWrap(dp(7)));

        LinearLayout derived = card();
        derived.addView(metricRow("ОЗ", state.hp + " / " + state.maxHp));
        derived.addView(metricRow("КД", String.valueOf(state.ac)));
        derived.addView(metricRow("Скорость", DerivedStats.speed(stats, ancestryItem(), equippedArmor()) + " фт"));
        derived.addView(metricRow("Восприятие", signed(state.perception)));
        derived.addView(metricRow("Стойкость", signed(state.fortitude)));
        derived.addView(metricRow("Рефлекс", signed(state.reflex)));
        derived.addView(metricRow("Воля", signed(state.will)));
        RuleItem cls = classItem(); if (cls != null && cls.meta.optInt("spellcasting", 0) > 0) {
            derived.addView(metricRow("Атака заклинанием", signed(DerivedStats.spellAttack(state, stats, cls))));
            derived.addView(metricRow("КС заклинания", String.valueOf(DerivedStats.spellDc(state, stats, cls))));
        }
        col.addView(derived, matchWrap(dp(7)));

        col.addView(sectionTitle("ХАРАКТЕРИСТИКИ"));
        LinearLayout attrs = card(); LinearLayout r = row();
        for (String[] a : ABILITIES) { TextView x = text(a[1] + "\n" + signed(stats.ability(a[0])), 15, true); x.setGravity(Gravity.CENTER); x.setTextColor(ACCENT); r.addView(x, weighted()); }
        attrs.addView(r); col.addView(attrs, matchWrap(dp(6)));

        col.addView(sectionTitle("АВТОМАТИЧЕСКИЕ ОСОБЕННОСТИ КЛАССА"));
        LinearLayout features = card(); int featureCount = 0;
        if (cls != null) {
            JSONArray f = cls.meta.optJSONArray("features");
            if (f != null) for (int i = 0; i < f.length(); i++) {
                JSONObject o = f.optJSONObject(i); if (o == null || o.optInt("level", 1) > state.level) continue;
                features.addView(staticRow("Ур. " + o.optInt("level", 1), RuNames.shortName(o.optString("name")))); featureCount++;
            }
        }
        if (featureCount == 0) features.addView(note("Выбери класс, чтобы увидеть прогрессию."));
        col.addView(features, matchWrap(dp(6)));

        LinearLayout io = row(); Button export = button("Экспорт JSON"), imp = button("Импорт JSON");
        export.setOnClickListener(v -> exportCharacter()); imp.setOnClickListener(v -> importCharacter());
        io.addView(export, weighted(dp(3))); io.addView(imp, weighted(dp(3))); col.addView(io, matchWrap(dp(6)));
        setContent(scroll(col));
    }

    private View levelStepper() {
        LinearLayout r = row(); r.setGravity(Gravity.CENTER_VERTICAL); r.setPadding(0, dp(5), 0, dp(5));
        TextView label = text("Уровень", 15, true), value = text(String.valueOf(state.level), 21, true); value.setGravity(Gravity.CENTER); value.setTextColor(ACCENT);
        Button minus = miniButton("−"), plus = miniButton("+");
        minus.setOnClickListener(v -> { state.level = clamp(state.level - 1, 1, 20); syncDerived(true); render(); });
        plus.setOnClickListener(v -> { state.level = clamp(state.level + 1, 1, 20); syncDerived(true); render(); });
        r.addView(label, weighted()); r.addView(minus, fixed(dp(48))); r.addView(value, fixed(dp(56))); r.addView(plus, fixed(dp(48))); return r;
    }

    // COMBAT
    private void showCombat() {
        syncDerived(true);
        LinearLayout col = page(); col.addView(sectionTitle("ИГРОВОЙ РЕЖИМ"));
        LinearLayout hp = card();
        TextView hpText = text("ОЗ  " + state.hp + " / " + state.maxHp + (state.tempHp > 0 ? "  +" + state.tempHp + " врем." : ""), 28, true);
        hpText.setTextColor(state.hp > Math.max(1, state.maxHp / 3) ? GOOD : BAD); hp.addView(hpText);
        LinearLayout hpButtons = row();
        for (int d : new int[]{-10,-1,1,10}) { Button b = miniButton((d > 0 ? "+" : "") + d); b.setOnClickListener(v -> { changeHp(d); }); hpButtons.addView(b, weighted(dp(2))); }
        hp.addView(hpButtons);
        hp.addView(intStepper("Временные ОЗ", () -> state.tempHp, v -> state.tempHp = Math.max(0, v), 0, 999));
        col.addView(hp, matchWrap(dp(7)));

        LinearLayout resources = card();
        resources.addView(intStepper("Очки героя", () -> stats.heroPoints, v -> stats.heroPoints = clamp(v, 0, 3), 0, 3));
        resources.addView(intStepper("Фокус", () -> stats.focus, v -> stats.focus = clamp(v, 0, stats.maxFocus), 0, 3));
        resources.addView(intStepper("Макс. фокус", () -> stats.maxFocus, v -> { stats.maxFocus = clamp(v, 0, 3); stats.focus = Math.min(stats.focus, stats.maxFocus); }, 0, 3));
        resources.addView(intStepper("Ранен", () -> stats.wounded, v -> stats.wounded = Math.max(0, v), 0, 9));
        resources.addView(intStepper("При смерти", () -> stats.dying, v -> stats.dying = clamp(v, 0, 4), 0, 4));
        TextView shield = actionRow("Щит", stats.shieldRaised ? "Поднят (+2 КД)" : "Опущен");
        shield.setOnClickListener(v -> { stats.shieldRaised = !stats.shieldRaised; syncDerived(true); render(); }); resources.addView(shield);
        col.addView(resources, matchWrap(dp(7)));

        col.addView(sectionTitle("СОСТОЯНИЯ")); LinearLayout conditions = card();
        for (RuleItem item : store.query("condition", 99, "", 80)) {
            int val = state.conditions.optInt(item.id, 0); TextView c = chip(RuNames.shortName(item.name) + (val > 0 ? " " + val : ""), val > 0);
            c.setOnClickListener(v -> { int n = (state.conditions.optInt(item.id, 0) + 1) % 5; try { if (n == 0) state.conditions.remove(item.id); else state.conditions.put(item.id, n); } catch (Exception ignored) {} saveAll(); render(); });
            c.setOnLongClickListener(v -> { showRuleDetail(item, null, "Закрыть"); return true; }); conditions.addView(c, matchWrap(dp(2)));
        }
        col.addView(conditions, matchWrap(dp(7)));

        col.addView(sectionTitle("РАСХОДНИКИ")); LinearLayout consumables = card(); int consumableCount = 0;
        for (int i = 0; i < state.inventory.length(); i++) {
            RuleItem item = store.findById(storedId(state.inventory.optString(i, "")));
            if (item == null || !("consumable".equalsIgnoreCase(item.subtype) || "ammo".equalsIgnoreCase(item.subtype))) continue;
            consumableCount++; int qty = inventory.quantity(item.id);
            TextView c = actionRow(RuNames.shortName(item.name), "Количество: " + qty + " • нажми, чтобы потратить");
            c.setOnClickListener(v -> { if (inventory.quantity(item.id) > 0) inventory.change(item.id, -1); inventory.save(this); render(); }); consumables.addView(c);
        }
        if (consumableCount == 0) consumables.addView(note("Добавь расходники в Снаряжении.")); col.addView(consumables, matchWrap(dp(7)));

        col.addView(sectionTitle("БЫСТРЫЕ БРОСКИ")); LinearLayout dice = card(); LinearLayout dr = row();
        for (int sides : new int[]{4,6,8,10,12,20,100}) { Button b = miniButton("d" + sides); b.setOnClickListener(v -> toast("d" + sides + " → " + (random.nextInt(sides) + 1))); dr.addView(b, weighted(dp(1))); } dice.addView(dr); col.addView(dice, matchWrap(dp(7)));
        setContent(scroll(col));
    }

    private void changeHp(int delta) { state.hp = clamp(state.hp + delta, 0, state.maxHp); saveAll(); render(); }

    // ATTACK
    private void showAttack() {
        syncDerived(true); LinearLayout col = page(); col.addView(sectionTitle("АТАКА"));
        RuleItem cls = classItem(); int weapons = 0;
        for (int i = 0; i < state.inventory.length(); i++) {
            RuleItem item = store.findById(storedId(state.inventory.optString(i, "")));
            if (item == null || !"weapon".equalsIgnoreCase(item.subtype)) continue; weapons++;
            int attack = DerivedStats.attack(state, stats, cls, item); String damage = DerivedStats.damage(stats, item);
            LinearLayout c = card(); c.addView(text(RuNames.display(item.name), 18, true));
            c.addView(note(item.meta.optString("itemCategory") + " • " + item.traitsLine()));
            c.addView(metricRow("Бонус атаки", signed(attack))); c.addView(metricRow("Урон", damage));
            LinearLayout buttons = row(); Button strike = button("Атака d20"), dmg = button("Урон");
            strike.setOnClickListener(v -> { int die = random.nextInt(20) + 1; toast(RuNames.shortName(item.name) + ": " + die + signedInline(attack) + " = " + (die + attack)); });
            dmg.setOnClickListener(v -> toast(RuNames.shortName(item.name) + ": " + damage + " → " + rollFormula(damage)));
            buttons.addView(strike, weighted(dp(3))); buttons.addView(dmg, weighted(dp(3))); c.addView(buttons); col.addView(c, matchWrap(dp(6)));
        }
        if (weapons == 0) { Button add = button("Добавить оружие"); add.setOnClickListener(v -> { screen = "equipment"; render(); }); col.addView(add, matchWrap(dp(7))); }
        setContent(scroll(col));
    }

    // DEFENSE
    private void showDefense() {
        syncDerived(true); LinearLayout col = page(); col.addView(sectionTitle("ЗАЩИТА"));
        LinearLayout c = card(); c.addView(bigStat("КД", state.ac)); c.addView(bigStat("СТОЙКОСТЬ", state.fortitude)); c.addView(bigStat("РЕФЛЕКС", state.reflex)); c.addView(bigStat("ВОЛЯ", state.will)); c.addView(bigStat("ВОСПРИЯТИЕ", state.perception));
        c.addView(metricRow("Броня", equippedArmor() == null ? "Без брони" : RuNames.shortName(equippedArmor().name)));
        c.addView(metricRow("Щит", stats.shieldRaised ? "Поднят" : "Опущен")); col.addView(c, matchWrap(dp(7)));
        setContent(scroll(col));
    }

    // SKILLS
    private void showSkills() {
        LinearLayout col = page(); col.addView(sectionTitle("НАВЫКИ"));
        col.addView(note("Нажми на ранг: Нет → Обучен → Эксперт → Мастер → Легенда. Бонус считается как характеристика + уровень + ранг мастерства."));
        for (String[] skill : SKILLS) {
            int rank = state.rank(skill[0]); int bonus = DerivedStats.skill(state, stats, skill[0]);
            LinearLayout c = card(); LinearLayout r = row(); r.setGravity(Gravity.CENTER_VERTICAL);
            TextView name = text(skill[1], 16, true), b = text(signed(bonus), 23, true); b.setTextColor(ACCENT); b.setGravity(Gravity.END); r.addView(name, weighted()); r.addView(b, fixed(dp(72))); c.addView(r);
            TextView rankView = chip(RANKS[rank], rank > 0); rankView.setOnClickListener(v -> { state.setRank(skill[0], (state.rank(skill[0]) + 1) % 5); revalidate(); saveAll(); render(); }); c.addView(rankView, matchWrap(dp(2)));
            Button roll = button("Проверка d20"); roll.setOnClickListener(v -> { int die = random.nextInt(20) + 1; toast(skill[1] + ": " + die + signedInline(bonus) + " = " + (die + bonus)); }); c.addView(roll);
            col.addView(c, matchWrap(dp(4)));
        }
        setContent(scroll(col));
    }

    // SPELLS
    private void showSpells() {
        LinearLayout outer = page(); outer.addView(sectionTitle("ЗАКЛИНАНИЯ")); RuleItem cls = classItem();
        if (cls != null && cls.meta.optInt("spellcasting", 0) > 0) outer.addView(note("Атака заклинанием " + signed(DerivedStats.spellAttack(state, stats, cls)) + " • КС " + DerivedStats.spellDc(state, stats, cls)));
        EditText search = input("", "Поиск заклинания"); outer.addView(search, matchWrap(dp(5))); LinearLayout list = column(); outer.addView(list);
        Runnable refresh = () -> {
            list.removeAllViews();
            if (state.spells.length() > 0) {
                list.addView(sectionTitle("ПОДГОТОВЛЕНО / ИЗВЕСТНО"));
                for (int i = 0; i < state.spells.length(); i++) {
                    RuleItem item = store.findById(storedId(state.spells.optString(i, ""))); if (item == null) continue;
                    TextView r = actionRow("✓ " + RuNames.shortName(item.name), spellMeta(item));
                    r.setOnClickListener(v -> showRuleDetail(item, () -> { state.toggleArrayItem(state.spells, item); saveAll(); render(); }, "Убрать")); list.addView(r);
                }
            }
            list.addView(sectionTitle("КАТАЛОГ")); int maxRank = Math.max(1, Math.min(10, (state.level + 1) / 2));
            for (RuleItem item : localizedQuery("spell", maxRank, search.getText().toString(), 180)) {
                boolean has = state.hasArrayItem(state.spells, item.id); TextView r = actionRow((has ? "✓ " : "+ ") + RuNames.shortName(item.name), spellMeta(item));
                r.setOnClickListener(v -> showRuleDetail(item, () -> { state.toggleArrayItem(state.spells, item); saveAll(); render(); }, has ? "Убрать" : "Добавить")); list.addView(r);
            }
        };
        search.addTextChangedListener(watcher(refresh)); refresh.run(); setContent(scroll(outer));
    }

    private String spellMeta(RuleItem item) {
        JSONArray traditions = item.meta.optJSONArray("traditions"); String tr = joinJson(traditions); String time = item.meta.optString("time", "");
        return "ранг " + item.level + (tr.isEmpty() ? "" : " • " + tr) + (time.isEmpty() ? "" : " • " + time + " действия");
    }

    // EQUIPMENT
    private void showEquipment() {
        LinearLayout outer = page(); outer.addView(sectionTitle("СНАРЯЖЕНИЕ"));
        LinearLayout money = card(); money.addView(metricRow("Монеты", inventory.pp + " зм • " + inventory.gp + " зм • " + inventory.sp + " см • " + inventory.cp + " мм"));
        LinearLayout mr = row(); Button mg = miniButton("−1 зм"), pg = miniButton("+1 зм"); mg.setOnClickListener(v -> { inventory.gp = Math.max(0, inventory.gp - 1); inventory.save(this); render(); }); pg.setOnClickListener(v -> { inventory.gp++; inventory.save(this); render(); }); mr.addView(mg, weighted(dp(2))); mr.addView(pg, weighted(dp(2))); money.addView(mr); outer.addView(money, matchWrap(dp(6)));
        EditText search = input("", "Поиск предмета, оружия, брони или руны"); outer.addView(search, matchWrap(dp(5))); LinearLayout list = column(); outer.addView(list);
        Runnable refresh = () -> {
            list.removeAllViews(); list.addView(sectionTitle("ИНВЕНТАРЬ"));
            if (state.inventory.length() == 0) list.addView(note("Инвентарь пуст."));
            for (int i = 0; i < state.inventory.length(); i++) {
                RuleItem item = store.findById(storedId(state.inventory.optString(i, ""))); if (item == null) continue;
                int q = inventory.quantity(item.id); String extra = q > 1 ? " ×" + q : "";
                TextView r = actionRow("✓ " + RuNames.shortName(item.name) + extra, item.subtype + (item.id.equals(stats.equippedArmorId) ? " • ЭКИПИРОВАНО" : ""));
                r.setOnClickListener(v -> equipmentDetail(item)); list.addView(r);
            }
            list.addView(sectionTitle("КАТАЛОГ"));
            for (RuleItem item : localizedQuery("equipment", 30, search.getText().toString(), 180)) {
                boolean has = state.hasArrayItem(state.inventory, item.id); TextView r = actionRow((has ? "✓ " : "+ ") + RuNames.shortName(item.name), equipmentMeta(item));
                r.setOnClickListener(v -> equipmentDetail(item)); list.addView(r);
            }
        };
        search.addTextChangedListener(watcher(refresh)); refresh.run(); setContent(scroll(outer));
    }

    private void equipmentDetail(RuleItem item) {
        boolean has = state.hasArrayItem(state.inventory, item.id); LinearLayout body = column(); body.setPadding(dp(8), dp(4), dp(8), dp(4));
        body.addView(note(equipmentMeta(item))); if (!item.traits.isEmpty()) body.addView(note(item.traitsLine())); body.addView(text(item.description.isEmpty() ? "Описание отсутствует." : item.description, 14, false));
        if (has) {
            body.addView(intStepper("Количество", () -> inventory.quantity(item.id), v -> inventory.setQuantity(item.id, Math.max(1, v)), 1, 999));
            if ("armor".equalsIgnoreCase(item.subtype)) {
                TextView equip = actionRow("Броня", item.id.equals(stats.equippedArmorId) ? "Снять" : "Экипировать");
                equip.setOnClickListener(v -> { stats.equippedArmorId = item.id.equals(stats.equippedArmorId) ? "" : item.id; syncDerived(true); render(); }); body.addView(equip);
            }
        }
        AlertDialog.Builder b = new AlertDialog.Builder(this).setTitle(RuNames.display(item.name)).setView(scroll(body)).setNegativeButton("Назад", null);
        b.setPositiveButton(has ? "Убрать" : "Добавить", (d,w) -> {
            if (has) { state.toggleArrayItem(state.inventory, item); inventory.remove(item.id); if (item.id.equals(stats.equippedArmorId)) stats.equippedArmorId = ""; }
            else { state.toggleArrayItem(state.inventory, item); inventory.setQuantity(item.id, Math.max(1, item.meta.optInt("quantity", 1))); }
            syncDerived(true); inventory.save(this); render();
        }); b.show();
    }

    private String equipmentMeta(RuleItem item) {
        StringBuilder s = new StringBuilder(item.subtype);
        if (item.level > 0) s.append(" • ур. ").append(item.level);
        if ("weapon".equalsIgnoreCase(item.subtype)) s.append(" • ").append(item.meta.optInt("damageDice",1)).append(item.meta.optString("damageDie","d4")).append(" ").append(item.meta.optString("damageType",""));
        if ("armor".equalsIgnoreCase(item.subtype)) s.append(" • КД +").append(item.meta.optInt("acBonus",0)).append(" • предел ЛОВ ").append(item.meta.optInt("dexCap",99));
        if (item.meta.optInt("potency",0) > 0) s.append(" • +").append(item.meta.optInt("potency",0)).append(" potency");
        if (item.meta.optInt("striking",0) > 0) s.append(" • striking ").append(item.meta.optInt("striking",0));
        return s.toString();
    }

    // REFERENCE
    private void showReference() {
        LinearLayout outer = page(); outer.addView(sectionTitle("СПРАВОЧНИК")); outer.addView(note("Локальная база без сети: " + store.count() + " записей. Поиск работает по английским данным и по встроенным русским названиям основных элементов."));
        EditText search = input("", "Поиск по названию"); outer.addView(search, matchWrap(dp(5))); LinearLayout list = column(); outer.addView(list);
        Runnable refresh = () -> {
            list.removeAllViews();
            for (RuleItem item : localizedQuery("all", 99, search.getText().toString(), 220)) {
                TextView r = actionRow(RuNames.display(item.name), item.category + (item.subtype.isEmpty() ? "" : " / " + item.subtype) + (item.level > 0 ? " • ур. " + item.level : ""));
                r.setOnClickListener(v -> showRuleDetail(item, null, "Закрыть")); list.addView(r);
            }
        };
        search.addTextChangedListener(watcher(refresh)); refresh.run(); setContent(scroll(outer));
    }

    // PICKERS / RULES
    private void showPicker(String category, int maxLevel, String slotCategory, int slotLevel, String choiceKey, Selection selection) {
        LinearLayout outer = column(); outer.setPadding(dp(10), dp(6), dp(10), dp(6)); EditText search = input("", "Поиск"); outer.addView(search, matchWrap(dp(5))); TextView status = note(""); outer.addView(status);
        ScrollView sv = new ScrollView(this); LinearLayout list = column(); sv.addView(list); outer.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(500)));
        AlertDialog.Builder builder = new AlertDialog.Builder(this).setTitle("Выбор").setView(outer).setNegativeButton("Закрыть", null);
        if (choiceKey != null) builder.setNeutralButton("Очистить", (d,w) -> { state.setChoice(choiceKey, null); revalidate(); syncDerived(true); render(); });
        AlertDialog dialog = builder.create();
        Runnable refresh = () -> {
            list.removeAllViews(); int shown = 0, locked = 0;
            for (RuleItem item : localizedQuery(category, maxLevel, search.getText().toString(), 600)) {
                if (!slotCategory.isEmpty() && !RuleEngine.canChoose(item, state, slotCategory, slotLevel)) { locked++; continue; }
                TextView r = actionRow(RuNames.display(item.name), (item.level > 0 ? "ур. " + item.level + " • " : "") + item.traitsLine());
                r.setOnClickListener(v -> showRuleDetail(item, () -> { selection.onSelect(item); dialog.dismiss(); }, "Выбрать")); list.addView(r); shown++; if (shown >= 170) break;
            }
            status.setText("Доступно: " + shown + (locked > 0 ? " • скрыто по требованиям: " + locked : ""));
        };
        search.addTextChangedListener(watcher(refresh)); dialog.setOnShowListener(d -> refresh.run()); dialog.show();
    }

    private List<RuleItem> localizedQuery(String category, int maxLevel, String query, int limit) {
        String q = query == null ? "" : query.trim(); List<RuleItem> direct = store.query(category, maxLevel, q, limit);
        if (q.isEmpty() || direct.size() >= Math.min(limit, 50)) return direct;
        List<RuleItem> result = new ArrayList<>(direct); java.util.HashSet<String> seen = new java.util.HashSet<>(); for (RuleItem i : direct) seen.add(i.id);
        for (RuleItem item : store.query(category, maxLevel, "", Math.max(800, limit))) {
            if (seen.contains(item.id) || !RuNames.matches(item.name, q)) continue; result.add(item); seen.add(item.id); if (result.size() >= limit) break;
        }
        return result;
    }

    private void showRuleDetail(RuleItem item, Runnable positive, String positiveLabel) {
        LinearLayout body = column(); body.setPadding(dp(8), dp(4), dp(8), dp(4));
        if (item.level > 0) body.addView(note("Уровень " + item.level)); if (!item.traits.isEmpty()) body.addView(note(item.traitsLine()));
        if (!item.prerequisites.isEmpty()) body.addView(note("Требования: " + String.join("; ", item.prerequisites)));
        if ("spell".equals(item.category)) body.addView(note(spellLongMeta(item)));
        if (!item.source.isEmpty()) body.addView(note("Источник: " + item.source + (item.license.isEmpty() ? "" : " • " + item.license)));
        TextView desc = text(item.description.isEmpty() ? "Описание отсутствует." : item.description, 14, false); desc.setPadding(0, dp(8), 0, dp(8)); body.addView(desc);
        AlertDialog.Builder b = new AlertDialog.Builder(this).setTitle(RuNames.display(item.name)).setView(scroll(body)).setNegativeButton("Назад", null);
        if (positive != null) b.setPositiveButton(positiveLabel, (d,w) -> positive.run()); else b.setPositiveButton("Закрыть", null); b.show();
    }

    private String spellLongMeta(RuleItem item) {
        StringBuilder s = new StringBuilder("Обычаи: ").append(joinJson(item.meta.optJSONArray("traditions")));
        String time = item.meta.optString("time", ""), range = item.meta.optString("range", ""), target = item.meta.optString("target", ""), duration = item.meta.optString("duration", "");
        if (!time.isEmpty()) s.append("\nСотворение: ").append(time); if (!range.isEmpty()) s.append("\nДистанция: ").append(range); if (!target.isEmpty()) s.append("\nЦель: ").append(target); if (!duration.isEmpty()) s.append("\nДлительность: ").append(duration); return s.toString();
    }

    private void revalidate() {
        List<String> remove = new ArrayList<>(); Iterator<String> keys = state.choices.keys();
        while (keys.hasNext()) {
            String key = keys.next(); if (!key.startsWith("L")) continue; String id = storedId(state.choices.optString(key, "")); RuleItem item = store.findById(id);
            int colon = key.indexOf(':'); int level = 1; try { level = Integer.parseInt(key.substring(1, colon)); } catch (Exception ignored) {} String slot = colon >= 0 ? key.substring(colon + 1) : "";
            if (!RuleEngine.canChoose(item, state, slot, level)) remove.add(key);
        }
        for (String key : remove) state.choices.remove(key);
    }

    private void applyBackgroundSkills(RuleItem bg) {
        JSONArray a = bg.meta.optJSONArray("trainedSkills"); if (a == null) return;
        for (int i = 0; i < a.length(); i++) state.setRank(a.optString(i), Math.max(1, state.rank(a.optString(i))));
    }

    private List<String> featuresAt(RuleItem cls, int level) {
        List<String> out = new ArrayList<>(); if (cls == null) return out; JSONArray a = cls.meta.optJSONArray("features"); if (a == null) return out;
        for (int i = 0; i < a.length(); i++) { JSONObject o = a.optJSONObject(i); if (o != null && o.optInt("level",1) == level) out.add(o.optString("name")); } return out;
    }

    // IMPORT / EXPORT
    private void exportCharacter() {
        JSONObject root = new JSONObject(); try { root.put("format", "gran2e-1"); root.put("character", state.toJson()); root.put("stats", stats.toJson()); root.put("inventoryState", inventory.toJson()); } catch (Exception ignored) {}
        String raw = root.toString(); ClipboardManager cm = (ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE); cm.setPrimaryClip(ClipData.newPlainText("Gran 2e character", raw));
        EditText box = input(raw, "JSON"); box.setSelectAllOnFocus(true); new AlertDialog.Builder(this).setTitle("JSON скопирован").setView(box).setPositiveButton("Готово", null).show();
    }

    private void importCharacter() {
        EditText box = input("", "Вставьте JSON персонажа"); new AlertDialog.Builder(this).setTitle("Импорт персонажа").setView(box).setNegativeButton("Отмена", null).setPositiveButton("Импорт", (d,w) -> {
            try { JSONObject root = new JSONObject(box.getText().toString()); JSONObject c = root.optJSONObject("character"); state = CharacterJson.fromString((c == null ? root : c).toString()); revalidate(); syncDerived(true); render(); } catch (Exception e) { toast("Некорректный JSON"); }
        }).show();
    }

    // DATA HELPERS
    private RuleItem classItem() { return state.className.isEmpty() ? null : store.findExact("class", state.className); }
    private RuleItem ancestryItem() { return state.ancestry.isEmpty() ? null : store.findExact("ancestry", state.ancestry); }
    private RuleItem backgroundItem() { return state.background.isEmpty() ? null : store.findExact("background", state.background); }
    private RuleItem equippedArmor() { return stats.equippedArmorId.isEmpty() ? null : store.findById(stats.equippedArmorId); }
    private void saveAll() { state.save(this); stats.save(this); inventory.save(this); }

    private String boostsText(JSONArray outer) {
        if (outer == null || outer.length() == 0) return "—"; List<String> groups = new ArrayList<>();
        for (int i = 0; i < outer.length(); i++) { JSONArray a = outer.optJSONArray(i); groups.add(a == null ? "" : joinJson(a)); } return String.join(" / ", groups);
    }
    private String joinJson(JSONArray a) { if (a == null) return ""; List<String> out = new ArrayList<>(); for (int i=0;i<a.length();i++) out.add(a.optString(i)); return String.join(", ", out); }

    // DICE
    private int rollFormula(String formula) {
        if (formula == null) return 0; String f = formula.toLowerCase(Locale.ROOT).replace(" ", ""); java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)d(\\d+)([+-]\\d+)?").matcher(f);
        if (!m.find()) return 0; int dice = parseInt(m.group(1),1), sides = parseInt(m.group(2),4), mod = parseInt(m.group(3),0), total = mod; for (int i=0;i<dice;i++) total += random.nextInt(Math.max(1,sides))+1; return total;
    }

    // UI HELPERS
    private LinearLayout page() { LinearLayout l = column(); l.setPadding(dp(10), dp(8), dp(10), dp(30)); return l; }
    private void setContent(View view) { content.removeAllViews(); content.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)); }
    private ScrollView scroll(View child) { ScrollView s = new ScrollView(this); s.setFillViewport(true); s.addView(child); return s; }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); return l; }
    private LinearLayout card() { LinearLayout l = column(); l.setPadding(dp(12), dp(10), dp(12), dp(10)); l.setBackground(round(SURFACE, 12, BORDER, 1)); return l; }
    private TextView sectionTitle(String s) { TextView v = text(s, 14, true); v.setTextColor(ACCENT); v.setPadding(dp(4), dp(10), dp(4), dp(6)); return v; }
    private TextView note(String s) { TextView v = text(s, 13, false); v.setTextColor(MUTED); v.setPadding(dp(4), dp(4), dp(4), dp(6)); return v; }
    private TextView staticRow(String title, String value) { return actionRow(title, value); }
    private TextView actionRow(String title, String value) { TextView v = text(title + "\n" + value, 15, false); v.setPadding(dp(12), dp(10), dp(12), dp(10)); v.setBackground(round(SURFACE_2, 9, BORDER, 1)); return v; }
    private View metricRow(String label, String value) { LinearLayout r = row(); r.setGravity(Gravity.CENTER_VERTICAL); r.setPadding(dp(2), dp(6), dp(2), dp(6)); TextView l=text(label,14,true), val=text(value,18,true); l.setTextColor(MUTED); val.setTextColor(ACCENT); val.setGravity(Gravity.END); r.addView(l,weighted()); r.addView(val,weighted()); return r; }
    private View bigStat(String label, int value) { LinearLayout r=row(); r.setGravity(Gravity.CENTER_VERTICAL); TextView l=text(label,14,true),v=text(signed(value),28,true); l.setTextColor(MUTED); v.setTextColor(ACCENT); v.setGravity(Gravity.END); r.addView(l,weighted()); r.addView(v,weighted()); return r; }
    private TextView chip(String s, boolean active) { TextView v=text(s,13,true); v.setGravity(Gravity.CENTER); v.setPadding(dp(12),dp(8),dp(12),dp(8)); v.setTextColor(active?Color.BLACK:TEXT); v.setBackground(round(active?ACCENT:SURFACE_2,18,active?ACCENT:BORDER,1)); return v; }
    private EditText input(String value, String hint) { EditText e=new EditText(this); e.setText(value); e.setHint(hint); e.setTextColor(TEXT); e.setHintTextColor(MUTED); e.setPadding(dp(10),dp(8),dp(10),dp(8)); e.setBackground(round(SURFACE_2,8,BORDER,1)); return e; }
    private Button button(String s) { Button b=new Button(this); b.setText(s); b.setTextColor(TEXT); b.setTextSize(12); b.setAllCaps(false); return b; }
    private Button miniButton(String s) { Button b=button(s); b.setMinWidth(0); b.setMinimumWidth(0); return b; }
    private TextView text(String s,int sp,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(TEXT);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
    private GradientDrawable round(int color,int radius,int strokeColor,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));if(stroke>0)d.setStroke(dp(stroke),strokeColor);return d;}
    private View intStepper(String label,IntGetter getter,IntSetter setter,int min,int max){LinearLayout r=row();r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(0,dp(4),0,dp(4));TextView l=text(label,14,true),val=text(String.valueOf(getter.get()),18,true);val.setGravity(Gravity.CENTER);val.setTextColor(ACCENT);Button minus=miniButton("−"),plus=miniButton("+");minus.setOnClickListener(v->{setter.set(clamp(getter.get()-1,min,max));saveAll();render();});plus.setOnClickListener(v->{setter.set(clamp(getter.get()+1,min,max));saveAll();render();});r.addView(l,weighted());r.addView(minus,fixed(dp(45)));r.addView(val,fixed(dp(55)));r.addView(plus,fixed(dp(45)));return r;}
    private TextWatcher watcher(Runnable r){return new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){r.run();}public void afterTextChanged(Editable e){}};}
    private LinearLayout.LayoutParams matchWrap(){return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);} private LinearLayout.LayoutParams matchWrap(int m){LinearLayout.LayoutParams p=matchWrap();p.setMargins(0,m,0,m);return p;}
    private LinearLayout.LayoutParams wrapWrap(int m){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);p.setMargins(m,0,m,0);return p;} private LinearLayout.LayoutParams weighted(){return new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f);} private LinearLayout.LayoutParams weighted(int m){LinearLayout.LayoutParams p=weighted();p.setMargins(m,m,m,m);return p;} private LinearLayout.LayoutParams fixed(int w){return new LinearLayout.LayoutParams(w,ViewGroup.LayoutParams.WRAP_CONTENT);}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);} private static int clamp(int v,int min,int max){return Math.max(min,Math.min(max,v));} private static int parseInt(String s,int f){try{return Integer.parseInt(s);}catch(Exception e){return f;}} private static String storedId(String raw){int i=raw.indexOf('\u001f');return i>=0?raw.substring(0,i):raw;} private static String signed(int v){return (v>=0?"+":"")+v;} private static String signedInline(int v){return v==0?"":(v>0?"+":"")+v;} private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    private interface Selection{void onSelect(RuleItem item);} private interface IntGetter{int get();} private interface IntSetter{void set(int value);}
}
