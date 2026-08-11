package ru.gran.edge2e;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * Gran 4.2 PLAY shell rebuilt from the observable screen structure of the reference APK.
 * The implementation and visuals are original; the reference is used as a behavioral layout spec.
 */
public final class ReferencePlayActivity extends Activity {
    private static final int BG = Color.rgb(232, 231, 227);
    private static final int TOP = Color.rgb(55, 57, 59);
    private static final int TOP_2 = Color.rgb(72, 74, 76);
    private static final int PANEL = Color.rgb(250, 250, 248);
    private static final int PANEL_2 = Color.rgb(241, 241, 237);
    private static final int BORDER = Color.rgb(188, 188, 183);
    private static final int TEXT = Color.rgb(36, 37, 38);
    private static final int MUTED = Color.rgb(103, 105, 107);
    private static final int ACCENT = Color.rgb(121, 31, 44);
    private static final int GOOD = Color.rgb(45, 125, 76);
    private static final int BAD = Color.rgb(176, 54, 54);
    private static final int WARM = Color.rgb(174, 111, 39);

    private static final String[][] TABS = {
            {"Персонаж", "character"}, {"Атаки", "attacks"}, {"Защита", "defenses"},
            {"Навыки", "skills"}, {"Фиты", "feats"}, {"Заклинания", "spells"},
            {"Снаряжение", "gear"}, {"Питомцы", "pets"}, {"Эффекты", "effects"}
    };
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
    private static final String[] RANKS = {"—", "ОБУЧЕН", "ЭКСПЕРТ", "МАСТЕР", "ЛЕГЕНДА"};

    private RuleStore store;
    private CharacterState state;
    private StatsState stats;
    private InventoryState inventory;
    private CompanionState companions;
    private SpellcastingState spellState;
    private RuleRuntime.Snapshot runtime;
    private FrameLayout content;
    private TextView headerName;
    private TextView headerStats;
    private String screen = "character";
    private final Random random = new Random();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(TOP);
        loadState();
        String requested = getIntent() == null ? "" : getIntent().getStringExtra("screen");
        if (requested != null && !requested.isEmpty()) screen = normalizeScreen(requested);
        setContentView(shell());
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        if (store != null) {
            loadState();
            render();
        }
    }

    private void loadState() {
        if (store == null) { store = new RuleStore(this); store.getReadableDatabase(); }
        state = CharacterState.load(this);
        stats = StatsState.load(this);
        inventory = InventoryState.load(this);
        companions = CompanionState.load(this);
        spellState = SpellcastingState.load(this);
        runtime = RuleRuntime.resolve(store, state, stats);
        syncDerived();
    }

    private String normalizeScreen(String value) {
        if ("sheet".equals(value) || "combat".equals(value) || "build".equals(value)) return "character";
        if ("attack".equals(value)) return "attacks";
        if ("defense".equals(value)) return "defenses";
        if ("equipment".equals(value)) return "gear";
        for (String[] t : TABS) if (t[1].equals(value)) return value;
        return "character";
    }

    private View shell() {
        LinearLayout root = column(); root.setBackgroundColor(BG);
        LinearLayout top = column(); top.setPadding(dp(12), dp(7), dp(12), dp(7)); top.setBackgroundColor(TOP);
        LinearLayout line = row(); line.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("‹ ПЕРСОНАЖИ", 11, true); back.setTextColor(Color.WHITE); back.setPadding(0, dp(5), dp(12), dp(5));
        back.setOnClickListener(v -> { startActivity(new Intent(this, FrontPageActivity.class)); finish(); });
        line.addView(back);
        headerName = text("", 18, true); headerName.setTextColor(Color.WHITE); line.addView(headerName, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView build = text("СБОРКА", 11, true); build.setTextColor(Color.rgb(236, 205, 169)); build.setPadding(dp(12), dp(5), 0, dp(5));
        build.setOnClickListener(v -> startActivity(new Intent(this, MainActivityV3.class)));
        line.addView(build); top.addView(line);
        headerStats = text("", 11, false); headerStats.setTextColor(Color.rgb(211, 212, 213)); top.addView(headerStats);
        root.addView(top, matchWrap());

        HorizontalScrollView scroll = new HorizontalScrollView(this); scroll.setHorizontalScrollBarEnabled(false); scroll.setBackgroundColor(TOP_2);
        LinearLayout nav = row(); nav.setPadding(dp(4), dp(3), dp(4), dp(3));
        for (String[] tab : TABS) {
            TextView t = tab(tab[0], tab[1].equals(screen));
            String target = tab[1];
            t.setOnClickListener(v -> { screen = target; render(); });
            nav.addView(t, wrapWrap(dp(2)));
        }
        scroll.addView(nav); root.addView(scroll, matchWrap());
        content = new FrameLayout(this); root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        return root;
    }

    private void render() {
        if (content == null) return;
        runtime = RuleRuntime.resolve(store, state, stats);
        syncDerived();
        String cls = state.className.isEmpty() ? "класс не выбран" : RuNames.shortName(state.className);
        headerName.setText(state.name == null || state.name.trim().isEmpty() ? "Новый герой" : state.name);
        headerStats.setText("ур. " + state.level + " • " + cls + " • ОЗ " + state.hp + "/" + state.maxHp + " • КД " + state.ac);
        content.removeAllViews();
        View page;
        switch (screen) {
            case "attacks": page = attacksPage(); break;
            case "defenses": page = defensesPage(); break;
            case "skills": page = skillsPage(); break;
            case "feats": page = featsPage(); break;
            case "spells": page = spellsPage(); break;
            case "gear": page = gearPage(); break;
            case "pets": page = petsPage(); break;
            case "effects": page = effectsPage(); break;
            default: page = characterPage();
        }
        content.addView(scroll(page));
        refreshTabStrip();
    }

    private void refreshTabStrip() {
        View parent = content == null ? null : content.getParent();
        if (!(parent instanceof LinearLayout)) return;
        LinearLayout root = (LinearLayout) parent;
        if (root.getChildCount() < 2 || !(root.getChildAt(1) instanceof HorizontalScrollView)) return;
        HorizontalScrollView hs = (HorizontalScrollView) root.getChildAt(1);
        if (!(hs.getChildAt(0) instanceof LinearLayout)) return;
        LinearLayout nav = (LinearLayout) hs.getChildAt(0); nav.removeAllViews();
        for (String[] spec : TABS) {
            TextView t = tab(spec[0], spec[1].equals(screen)); String target = spec[1];
            t.setOnClickListener(v -> { screen = target; render(); }); nav.addView(t, wrapWrap(dp(2)));
        }
    }

    private LinearLayout characterPage() {
        LinearLayout col = page();
        col.addView(section("ПЕРСОНАЖ"));
        LinearLayout hp = panel();
        LinearLayout hpLine = row(); hpLine.setGravity(Gravity.CENTER_VERTICAL);
        TextView hpText = text("ОЗ  " + state.hp + " / " + state.maxHp + (state.tempHp > 0 ? "  +" + state.tempHp + " врем." : ""), 24, true);
        hpText.setTextColor(state.hp > Math.max(1, state.maxHp / 3) ? GOOD : BAD);
        hpLine.addView(hpText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        hpLine.addView(badge("КД " + state.ac, ACCENT)); hp.addView(hpLine);
        LinearLayout hpButtons = row();
        for (int delta : new int[]{-10, -1, 1, 10}) {
            final int d = delta; Button b = smallButton((d > 0 ? "+" : "") + d); b.setOnClickListener(v -> { state.hp = clamp(state.hp + d, 0, state.maxHp); state.save(this); render(); }); hpButtons.addView(b, weighted(dp(2)));
        }
        hp.addView(hpButtons); col.addView(hp, matchWrap(dp(4)));

        LinearLayout identity = panel();
        identity.addView(pair("Род", show(state.ancestry)));
        identity.addView(pair("Наследие", show(state.choiceName("base:heritage"))));
        identity.addView(pair("Предыстория", show(state.background)));
        identity.addView(pair("Класс", show(state.className)));
        identity.addView(pair("Скорость", DerivedStats.speed(stats, ancestryItem(), equippedArmor()) + " фт"));
        col.addView(identity, matchWrap(dp(4)));

        col.addView(section("ХАРАКТЕРИСТИКИ"));
        LinearLayout abilities = row();
        for (String[] ability : ABILITIES) abilities.addView(abilityCell(ability[1], stats.abilityScore(ability[0]), stats.ability(ability[0])), weighted(dp(2)));
        col.addView(abilities, matchWrap(dp(3)));

        col.addView(section("СПАСБРОСКИ И ВОСПРИЯТИЕ"));
        LinearLayout saves = panel();
        saves.addView(statsRow(new String[][]{{"СТОЙК.", signed(state.fortitude)}, {"РЕФЛ.", signed(state.reflex)}, {"ВОЛЯ", signed(state.will)}, {"ВОСПР.", signed(state.perception)}}));
        col.addView(saves, matchWrap(dp(4)));

        col.addView(section("РЕСУРСЫ"));
        LinearLayout resources = panel();
        resources.addView(stepper("Очки героя", stats.heroPoints, 0, 3, value -> { stats.heroPoints = value; stats.save(this); }));
        resources.addView(stepper("Фокус", stats.focus, 0, Math.max(0, stats.maxFocus), value -> { stats.focus = value; stats.save(this); }));
        resources.addView(stepper("Ранен", stats.wounded, 0, 9, value -> { stats.wounded = value; stats.save(this); }));
        resources.addView(stepper("При смерти", stats.dying, 0, 4, value -> { stats.dying = value; stats.save(this); }));
        col.addView(resources, matchWrap(dp(4)));

        if (activeConditionCount() > 0) {
            col.addView(section("АКТИВНЫЕ ЭФФЕКТЫ"));
            LinearLayout effects = panel();
            Iterator<String> it = state.conditions.keys();
            while (it.hasNext()) {
                String id = it.next(); RuleItem item = store.findById(id); int value = state.conditions.optInt(id, 0);
                if (item != null && value > 0) effects.addView(pair(RuNames.shortName(item.name), String.valueOf(value)));
            }
            col.addView(effects, matchWrap(dp(4)));
        }
        return col;
    }

    private LinearLayout attacksPage() {
        LinearLayout col = page(); col.addView(section("АТАКИ"));
        RuleItem cls = classItem(); int count = 0;
        for (RuleItem item : inventoryItems()) {
            if (!"weapon".equalsIgnoreCase(item.subtype)) continue;
            count++;
            int attack = DerivedStats.attack(state, stats, cls, item); String damage = DerivedStats.damage(stats, item);
            LinearLayout card = panel();
            TextView name = text(RuNames.display(item.name), 18, true); name.setTextColor(ACCENT); card.addView(name);
            if (!item.traits.isEmpty()) card.addView(note(item.traitsLine()));
            LinearLayout main = row(); main.setGravity(Gravity.CENTER_VERTICAL);
            main.addView(metricBox("АТАКА", signed(attack)), weighted(dp(2)));
            main.addView(metricBox("УРОН", damage), weighted(dp(2))); card.addView(main);
            LinearLayout map = row();
            int[] penalties = {0, -5, -10};
            for (int i = 0; i < penalties.length; i++) {
                final int bonus = attack + penalties[i]; Button b = button((i == 0 ? "1-я " : (i + 1) + "-я ") + signed(bonus));
                b.setOnClickListener(v -> rollD20(RuNames.shortName(item.name), bonus)); map.addView(b, weighted(dp(2)));
            }
            card.addView(map);
            TextView dmg = actionRow("Бросить урон", damage); dmg.setOnClickListener(v -> toast(RuNames.shortName(item.name) + ": " + damage)); card.addView(dmg);
            card.setOnLongClickListener(v -> { ruleDetail(item); return true; });
            col.addView(card, matchWrap(dp(5)));
        }
        if (count == 0) {
            LinearLayout empty = panel(); empty.addView(text("Оружие не экипировано", 17, true)); empty.addView(note("Добавь оружие в разделе «Снаряжение»."));
            Button add = button("ОТКРЫТЬ СНАРЯЖЕНИЕ"); add.setOnClickListener(v -> { screen = "gear"; render(); }); empty.addView(add); col.addView(empty, matchWrap(dp(5)));
        }
        col.addView(section("БОЕВЫЕ ПАРАМЕТРЫ"));
        LinearLayout combat = panel(); combat.addView(pair("КД", String.valueOf(state.ac))); combat.addView(pair("Восприятие", signed(state.perception)));
        combat.addView(pair("Щит", stats.shieldRaised ? "поднят" : "опущен")); col.addView(combat, matchWrap(dp(4)));
        return col;
    }

    private LinearLayout defensesPage() {
        LinearLayout col = page(); col.addView(section("ЗАЩИТА"));
        LinearLayout saves = panel(); saves.addView(statsRow(new String[][]{{"КД", String.valueOf(state.ac)}, {"СТОЙК.", signed(state.fortitude)}, {"РЕФЛ.", signed(state.reflex)}, {"ВОЛЯ", signed(state.will)}})); col.addView(saves, matchWrap(dp(4)));

        col.addView(section("БРОНЯ"));
        RuleItem armor = equippedArmor(); LinearLayout armorCard = panel();
        if (armor == null) armorCard.addView(note("Без брони"));
        else {
            armorCard.addView(text(RuNames.display(armor.name), 18, true));
            armorCard.addView(pair("Бонус КД", signed(armor.meta.optInt("acBonus", 0))));
            armorCard.addView(pair("Лимит Ловкости", String.valueOf(armor.meta.optInt("dexCap", 99))));
            armorCard.addView(pair("Штраф скорости", String.valueOf(armor.meta.optInt("speedPenalty", 0))));
            if (!armor.traits.isEmpty()) armorCard.addView(note(armor.traitsLine()));
        }
        col.addView(armorCard, matchWrap(dp(4)));

        col.addView(section("ЩИТ"));
        LinearLayout shield = panel(); RuleItem shieldItem = firstSubtype("shield");
        shield.addView(text(shieldItem == null ? "Щит" : RuNames.display(shieldItem.name), 18, true));
        TextView raised = actionRow("Состояние", stats.shieldRaised ? "ПОДНЯТ • +2 КД" : "ОПУЩЕН");
        raised.setOnClickListener(v -> { stats.shieldRaised = !stats.shieldRaised; stats.save(this); syncDerived(); state.save(this); render(); }); shield.addView(raised);
        if (shieldItem != null) {
            shield.addView(pair("Твёрдость", String.valueOf(shieldItem.meta.optInt("hardness", 0))));
            shield.addView(pair("ОЗ щита", String.valueOf(shieldItem.meta.optInt("hp", 0))));
        }
        col.addView(shield, matchWrap(dp(4)));

        col.addView(section("СОСТОЯНИЯ")); LinearLayout active = panel(); int n = 0;
        Iterator<String> it = state.conditions.keys();
        while (it.hasNext()) {
            String id = it.next(); int value = state.conditions.optInt(id, 0); RuleItem item = store.findById(id);
            if (value <= 0 || item == null) continue; n++; active.addView(pair(RuNames.shortName(item.name), String.valueOf(value)));
        }
        if (n == 0) active.addView(note("Нет активных состояний."));
        TextView manage = actionRow("Управление состояниями", "открыть Эффекты"); manage.setOnClickListener(v -> { screen = "effects"; render(); }); active.addView(manage); col.addView(active, matchWrap(dp(4)));
        return col;
    }

    private LinearLayout skillsPage() {
        LinearLayout col = page(); col.addView(section("НАВЫКИ"));
        LinearLayout table = panel();
        for (String[] skill : SKILLS) {
            int rank = runtime.rank(state, skill[0]); int bonus = DerivedStats.skill(state, stats, skill[0]);
            LinearLayout r = row(); r.setGravity(Gravity.CENTER_VERTICAL); r.setPadding(dp(8), dp(7), dp(8), dp(7));
            TextView name = text(skill[1], 15, true); r.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            TextView rankView = text(RANKS[Math.max(0, Math.min(4, rank))], 10, true); rankView.setTextColor(rank > 0 ? GOOD : MUTED); rankView.setGravity(Gravity.CENTER); rankView.setMinWidth(dp(82)); r.addView(rankView);
            Button roll = smallButton(signed(bonus)); roll.setOnClickListener(v -> rollD20(skill[1], bonus)); r.addView(roll, fixed(dp(68)));
            table.addView(r); table.addView(divider());
        }
        col.addView(table, matchWrap(dp(4))); return col;
    }

    private LinearLayout featsPage() {
        LinearLayout col = page(); col.addView(section("ФИТЫ"));
        LinearLayout feats = panel(); int count = 0; Set<String> seen = new HashSet<>();
        for (RuleItem item : runtime.allItems()) {
            if (!"feat".equals(item.category)) continue;
            if (!seen.add(item.id)) continue; count++;
            TextView row = actionRow(RuNames.shortName(item.name), featMeta(item)); row.setOnClickListener(v -> ruleDetail(item)); feats.addView(row);
        }
        if (count == 0) feats.addView(note("Выбранных фитов пока нет.")); col.addView(feats, matchWrap(dp(4)));

        col.addView(section("ОСОБЕННОСТИ")); LinearLayout specials = panel(); int specialCount = 0;
        for (RuleItem item : runtime.allItems()) {
            if (!"class-feature".equals(item.category)) continue; specialCount++;
            int level = runtime.automaticLevel(item.id); TextView row = actionRow(RuNames.shortName(item.name), level > 0 ? "ур. " + level : item.source); row.setOnClickListener(v -> ruleDetail(item)); specials.addView(row);
        }
        if (specialCount == 0) specials.addView(note("Автоматические особенности появятся после выбора класса.")); col.addView(specials, matchWrap(dp(4)));
        return col;
    }

    private LinearLayout spellsPage() {
        LinearLayout col = page(); col.addView(section("ЗАКЛИНАНИЯ"));
        SpellcastingRules.Profile profile = SpellcastingRules.resolve(state, runtime);
        if (profile == null) {
            LinearLayout empty = panel(); empty.addView(text("Нет активного источника заклинаний", 17, true)); empty.addView(note("Выбери класс/особенность с заклинаниями в СБОРКЕ.")); col.addView(empty, matchWrap(dp(4))); return col;
        }
        LinearLayout head = panel(); head.addView(pair("Источник", RuNames.shortName(profile.source))); head.addView(pair("Тип", SpellcastingRules.modeLabel(profile))); head.addView(pair("Традиция", SpellcastingRules.traditionLabel(profile.tradition)));
        RuleItem cls = classItem(); head.addView(pair("Атака заклинанием", signed(DerivedStats.spellAttack(state, stats, cls)))); head.addView(pair("КС заклинаний", String.valueOf(DerivedStats.spellDc(state, stats, cls))));
        if (!profile.note.isEmpty()) head.addView(note(profile.note)); col.addView(head, matchWrap(dp(4)));

        addFocusAndRitualSections(col);
        if (!profile.supported) return col;
        spellState.sanitize(profile, state.level);
        for (int rank = 1; rank <= profile.maxRank(state.level); rank++) {
            int slots = SpellcastingRules.PREPARED.equals(profile.mode) ? profile.totalPreparedSlots(state.level, rank) : profile.slots(state.level, rank);
            if (slots <= 0) continue;
            col.addView(section("РАНГ " + rank)); LinearLayout block = panel();
            if (SpellcastingRules.PREPARED.equals(profile.mode)) {
                for (int slot = 0; slot < slots; slot++) {
                    final int rr = rank, ss = slot; String name = spellState.preparedName(rank, slot); boolean spent = spellState.preparedSpent(rank, slot);
                    TextView row = actionRow("Слот " + (slot + 1), name.isEmpty() ? "подготовить" : (spent ? "ИСПОЛЬЗОВАНО • " : "") + RuNames.shortName(name));
                    row.setTextColor(spent ? MUTED : TEXT);
                    row.setOnClickListener(v -> {
                        if (spellState.preparedId(rr, ss).isEmpty()) showSpellPicker(profile, rr, item -> { spellState.prepare(rr, ss, item); spellState.save(this); render(); });
                        else { spellState.setPreparedSpent(rr, ss, !spellState.preparedSpent(rr, ss)); spellState.save(this); render(); }
                    });
                    row.setOnLongClickListener(v -> { spellState.prepare(rr, ss, null); spellState.save(this); render(); return true; }); block.addView(row);
                }
            } else {
                int spent = spellState.spent(rank); block.addView(pair("Слоты", (slots - spent) + " / " + slots));
                LinearLayout spend = row(); Button use = button("ИСПОЛЬЗОВАТЬ СЛОТ"), restore = button("ВОССТАНОВИТЬ");
                final int rr = rank; use.setOnClickListener(v -> { spellState.spend(rr, slots); spellState.save(this); render(); }); restore.setOnClickListener(v -> { spellState.restoreOne(rr, slots); spellState.save(this); render(); }); spend.addView(use, weighted(dp(2))); spend.addView(restore, weighted(dp(2))); block.addView(spend);
                List<SpellcastingState.RepertoireSpell> rep = spellState.repertoire(rank);
                for (SpellcastingState.RepertoireSpell sp : rep) {
                    TextView row = actionRow((spellState.isSignature(rank, sp.id) ? "★ " : "") + RuNames.shortName(sp.name), spellState.isSignature(rank, sp.id) ? "signature" : "репертуар");
                    row.setOnClickListener(v -> { spellState.setSignature(rr, sp.id); spellState.save(this); render(); }); block.addView(row);
                }
                TextView add = actionRow("+ Добавить заклинание", "в репертуар ранга " + rank); add.setOnClickListener(v -> showSpellPicker(profile, rr, item -> { spellState.addRepertoire(rr, item); spellState.save(this); render(); })); block.addView(add);
            }
            col.addView(block, matchWrap(dp(4)));
        }
        return col;
    }

    private void addFocusAndRitualSections(LinearLayout col) {
        List<RuleItem> focus = new ArrayList<>(), rituals = new ArrayList<>();
        for (int i = 0; i < state.spells.length(); i++) {
            RuleItem item = store.findById(storedId(state.spells.optString(i, ""))); if (item == null) continue;
            if (hasTrait(item, "focus") || item.meta.optBoolean("focus", false)) focus.add(item);
            if (hasTrait(item, "ritual") || "ritual".equalsIgnoreCase(item.subtype)) rituals.add(item);
        }
        if (!focus.isEmpty()) {
            col.addView(section("ФОКУСНЫЕ ЗАКЛИНАНИЯ")); LinearLayout p = panel();
            p.addView(pair("Очки фокуса", stats.focus + " / " + stats.maxFocus));
            for (RuleItem item : focus) { TextView r = actionRow(RuNames.shortName(item.name), "фокусное"); r.setOnClickListener(v -> ruleDetail(item)); p.addView(r); }
            col.addView(p, matchWrap(dp(4)));
        }
        if (!rituals.isEmpty()) {
            col.addView(section("РИТУАЛЫ")); LinearLayout p = panel(); for (RuleItem item : rituals) { TextView r = actionRow(RuNames.shortName(item.name), "ритуал"); r.setOnClickListener(v -> ruleDetail(item)); p.addView(r); } col.addView(p, matchWrap(dp(4)));
        }
    }

    private LinearLayout gearPage() {
        LinearLayout col = page(); col.addView(section("СНАРЯЖЕНИЕ"));
        BulkRules.Summary bulk = BulkRules.calculate(store, state, stats, inventory);
        LinearLayout summary = panel(); summary.addView(statsRow(new String[][]{{"ЗМ", String.valueOf(inventory.gp)}, {"СМ", String.valueOf(inventory.sp)}, {"ММ", String.valueOf(inventory.cp)}, {"BULK", BulkRules.label(bulk.totalLight)}}));
        summary.addView(pair("Нагрузка", bulk.status())); col.addView(summary, matchWrap(dp(4)));

        col.addView(section("ОСНОВНОЙ ИНВЕНТАРЬ")); LinearLayout list = panel(); List<RuleItem> items = inventoryItems();
        if (items.isEmpty()) list.addView(note("Инвентарь пуст."));
        for (RuleItem item : items) {
            int qty = inventory.quantity(item.id); String container = inventory.containerFor(item.id);
            String meta = translatedSubtype(item.subtype) + " • " + BulkRules.itemBulkLabel(item, qty) + (qty > 1 ? " • ×" + qty : "") + (!container.isEmpty() ? " • в контейнере" : "");
            TextView row = actionRow(RuNames.shortName(item.name), meta); row.setOnClickListener(v -> equipmentDialog(item)); list.addView(row);
        }
        TextView add = actionRow("+ ДОБАВИТЬ ПРЕДМЕТ", "оружие, броня, расходники, инструменты"); add.setOnClickListener(v -> showEquipmentPicker()); list.addView(add); col.addView(list, matchWrap(dp(4)));

        if (!bulk.containers.isEmpty()) {
            col.addView(section("КОНТЕЙНЕРЫ")); LinearLayout containers = panel();
            for (BulkRules.ContainerLoad load : bulk.containers) {
                containers.addView(pair(RuNames.shortName(load.item.name), BulkRules.label(load.countedContentsLight) + (load.capacityLight > 0 ? " / " + BulkRules.label(load.capacityLight) : "") + (load.overCapacity ? " • ПЕРЕПОЛНЕН" : "")));
            }
            col.addView(containers, matchWrap(dp(4)));
        }
        return col;
    }

    private LinearLayout petsPage() {
        LinearLayout col = page(); col.addView(section("ПИТОМЦЫ"));
        LinearLayout help = panel(); help.addView(note("Спутники разделены по типам так же, как в эталонном PLAY: животные-компаньоны, фамильяры, эйдолоны, конструкты и последователи.")); col.addView(help, matchWrap(dp(4)));
        String[] types = {"Животный-компаньон", "Фамильяр", "Эйдолон", "Конструкт", "Последователь"};
        for (String type : types) {
            col.addView(section(type.toUpperCase(Locale.ROOT)));
            LinearLayout group = panel(); int count = 0;
            for (CompanionState.Companion c : companions.items) {
                if (!type.equalsIgnoreCase(c.type)) continue; count++; group.addView(companionCard(c));
            }
            if (count == 0) group.addView(note("Нет активного спутника этого типа."));
            Button add = button("+ ДОБАВИТЬ"); add.setOnClickListener(v -> { companions.add(type, state.level); companions.save(this); render(); }); group.addView(add); col.addView(group, matchWrap(dp(4)));
        }
        return col;
    }

    private View companionCard(CompanionState.Companion c) {
        LinearLayout card = column(); card.setPadding(dp(8), dp(7), dp(8), dp(7)); card.setBackground(round(PANEL_2, 7, BORDER));
        LinearLayout head = row(); head.setGravity(Gravity.CENTER_VERTICAL); TextView name = text(c.name, 16, true); name.setTextColor(ACCENT); head.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1)); head.addView(badge("ур. " + c.level, TOP_2)); card.addView(head);
        card.addView(statsRow(new String[][]{{"ОЗ", c.hp + "/" + c.maxHp}, {"КД", String.valueOf(c.ac)}, {"АТК", signed(c.attack)}, {"УРОН", c.damage}}));
        LinearLayout hp = row(); Button minus = smallButton("−1 ОЗ"), plus = smallButton("+1 ОЗ"), hit = smallButton("АТАКА");
        minus.setOnClickListener(v -> { c.hp = Math.max(0, c.hp - 1); companions.save(this); render(); }); plus.setOnClickListener(v -> { c.hp = Math.min(c.maxHp, c.hp + 1); companions.save(this); render(); }); hit.setOnClickListener(v -> rollD20(c.name, c.attack));
        hp.addView(minus, weighted(dp(1))); hp.addView(plus, weighted(dp(1))); hp.addView(hit, weighted(dp(1))); card.addView(hp);
        card.setOnLongClickListener(v -> { new AlertDialog.Builder(this).setTitle("Удалить " + c.name + "?").setNegativeButton("Отмена", null).setPositiveButton("Удалить", (d,w) -> { companions.remove(c.id); companions.save(this); render(); }).show(); return true; }); return card;
    }

    private LinearLayout effectsPage() {
        LinearLayout col = page(); col.addView(section("ЭФФЕКТЫ И СОСТОЯНИЯ"));
        LinearLayout active = panel(); int count = 0;
        Iterator<String> it = state.conditions.keys();
        while (it.hasNext()) {
            String id = it.next(); RuleItem item = store.findById(id); int value = state.conditions.optInt(id, 0); if (item == null || value <= 0) continue; count++;
            active.addView(conditionRow(item, value));
        }
        if (count == 0) active.addView(note("Активных состояний нет.")); col.addView(active, matchWrap(dp(4)));
        Button add = button("+ ДОБАВИТЬ СОСТОЯНИЕ"); add.setOnClickListener(v -> showConditionPicker()); col.addView(add, matchWrap(dp(4)));
        return col;
    }

    private View conditionRow(RuleItem item, int value) {
        LinearLayout r = row(); r.setGravity(Gravity.CENTER_VERTICAL); r.setPadding(dp(7), dp(6), dp(7), dp(6));
        TextView name = text(RuNames.shortName(item.name), 15, true); r.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button minus = smallButton("−"), val = smallButton(String.valueOf(value)), plus = smallButton("+");
        minus.setOnClickListener(v -> setCondition(item.id, value - 1)); val.setOnClickListener(v -> ruleDetail(item)); plus.setOnClickListener(v -> setCondition(item.id, value + 1)); r.addView(minus); r.addView(val); r.addView(plus); return r;
    }

    private void setCondition(String id, int value) {
        try { if (value <= 0) state.conditions.remove(id); else state.conditions.put(id, Math.min(9, value)); } catch (Exception ignored) { }
        state.save(this); render();
    }

    private void showConditionPicker() {
        List<RuleItem> options = store.query("condition", 99, "", 100); String[] labels = new String[options.size()]; for (int i=0;i<labels.length;i++) labels[i]=RuNames.shortName(options.get(i).name);
        new AlertDialog.Builder(this).setTitle("Добавить состояние").setItems(labels, (d, which) -> setCondition(options.get(which).id, Math.max(1, state.conditions.optInt(options.get(which).id, 0) + 1))).setNegativeButton("Закрыть", null).show();
    }

    private interface ItemPick { void choose(RuleItem item); }

    private void showSpellPicker(SpellcastingRules.Profile profile, int rank, ItemPick pick) {
        LinearLayout body = column(); body.setPadding(dp(8), dp(5), dp(8), dp(5)); EditText search = input("Поиск заклинания"); body.addView(search); ScrollView sv = new ScrollView(this); LinearLayout list = column(); sv.addView(list); body.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(520)));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Заклинание — ранг " + rank).setView(body).setNegativeButton("Закрыть", null).create();
        Runnable refresh = () -> {
            list.removeAllViews(); String q = search.getText().toString().toLowerCase(Locale.ROOT); int shown = 0;
            for (RuleItem item : store.query("spell", rank, "", 900)) {
                if (!SpellcastingRules.spellAllowed(profile, item, rank)) continue; String display = RuNames.shortName(item.name);
                if (!q.isEmpty() && !display.toLowerCase(Locale.ROOT).contains(q) && !item.name.toLowerCase(Locale.ROOT).contains(q)) continue;
                TextView r = actionRow(display, "ранг " + item.level + " • " + item.traitsLine()); r.setOnClickListener(v -> { pick.choose(item); dialog.dismiss(); }); r.setOnLongClickListener(v -> { ruleDetail(item); return true; }); list.addView(r); if (++shown >= 220) break;
            }
            if (shown == 0) list.addView(note("Нет подходящих заклинаний."));
        };
        search.addTextChangedListener(watcher(refresh)); refresh.run(); dialog.show();
    }

    private void showEquipmentPicker() {
        LinearLayout body = column(); body.setPadding(dp(8), dp(5), dp(8), dp(5)); EditText search = input("Поиск предмета"); body.addView(search); ScrollView sv = new ScrollView(this); LinearLayout list = column(); sv.addView(list); body.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(520)));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Каталог снаряжения").setView(body).setNegativeButton("Закрыть", null).create();
        Runnable refresh = () -> {
            list.removeAllViews(); String q = search.getText().toString().toLowerCase(Locale.ROOT); int shown = 0;
            for (RuleItem item : store.query("equipment", 20, "", 1200)) {
                String display = RuNames.shortName(item.name); if (!q.isEmpty() && !display.toLowerCase(Locale.ROOT).contains(q) && !item.name.toLowerCase(Locale.ROOT).contains(q)) continue;
                TextView r = actionRow(display, translatedSubtype(item.subtype) + " • Bulk " + BulkRules.itemBulkLabel(item, 1)); r.setOnClickListener(v -> { if (!containsInventory(item.id)) state.inventory.put(item.id + "\u001f" + item.name); inventory.setQuantity(item.id, Math.max(1, inventory.quantity(item.id))); state.save(this); inventory.save(this); dialog.dismiss(); render(); }); r.setOnLongClickListener(v -> { ruleDetail(item); return true; }); list.addView(r); if (++shown >= 240) break;
            }
            if (shown == 0) list.addView(note("Ничего не найдено."));
        };
        search.addTextChangedListener(watcher(refresh)); refresh.run(); dialog.show();
    }

    private void equipmentDialog(RuleItem item) {
        LinearLayout body = column(); body.setPadding(dp(8), dp(5), dp(8), dp(5)); body.addView(text(RuNames.display(item.name), 18, true)); body.addView(note(translatedSubtype(item.subtype) + " • Bulk " + BulkRules.itemBulkLabel(item, inventory.quantity(item.id))));
        body.addView(stepper("Количество", inventory.quantity(item.id), 1, 999, value -> { inventory.setQuantity(item.id, value); inventory.save(this); }));
        if ("armor".equalsIgnoreCase(item.subtype)) {
            TextView equip = actionRow("Броня", item.id.equals(stats.equippedArmorId) ? "ЭКИПИРОВАНО" : "экипировать");
            equip.setOnClickListener(v -> { stats.equippedArmorId = item.id.equals(stats.equippedArmorId) ? "" : item.id; stats.save(this); syncDerived(); state.save(this); render(); }); body.addView(equip);
        }
        new AlertDialog.Builder(this).setView(body).setNeutralButton("Описание", (d,w) -> ruleDetail(item)).setNegativeButton("Закрыть", null).setPositiveButton("Убрать", (d,w) -> { removeInventory(item.id); inventory.remove(item.id); state.save(this); inventory.save(this); render(); }).show();
    }

    private void ruleDetail(RuleItem item) {
        StringBuilder b = new StringBuilder(); if (item.level > 0) b.append("Уровень: ").append(item.level).append("\n"); if (!item.traits.isEmpty()) b.append("Черты: ").append(item.traitsLine()).append("\n"); if (!item.prerequisites.isEmpty()) b.append("Требования: ").append(String.join("; ", item.prerequisites)).append("\n"); if (!item.source.isEmpty()) b.append("Источник: ").append(item.source).append("\n"); b.append("\n").append(item.description == null ? "" : item.description);
        new AlertDialog.Builder(this).setTitle(RuNames.display(item.name)).setMessage(b.toString()).setPositiveButton("Закрыть", null).show();
    }

    private View stepper(String label, int value, int min, int max, IntSet set) {
        LinearLayout r = row(); r.setGravity(Gravity.CENTER_VERTICAL); r.setPadding(dp(7), dp(5), dp(7), dp(5)); TextView l = text(label, 14, true); r.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1)); Button minus = smallButton("−"), val = smallButton(String.valueOf(value)), plus = smallButton("+"); minus.setOnClickListener(v -> { set.set(clamp(value - 1, min, max)); render(); }); plus.setOnClickListener(v -> { set.set(clamp(value + 1, min, max)); render(); }); r.addView(minus); r.addView(val); r.addView(plus); return r;
    }
    private interface IntSet { void set(int value); }

    private void syncDerived() {
        RuleItem cls = classItem(), ancestry = ancestryItem(), armor = equippedArmor();
        state.maxHp = DerivedStats.hp(state, stats, ancestry, cls); state.hp = clamp(state.hp, 0, state.maxHp); state.ac = DerivedStats.ac(state, stats, cls, armor); state.fortitude = DerivedStats.save(state, stats, cls, "fortitude"); state.reflex = DerivedStats.save(state, stats, cls, "reflex"); state.will = DerivedStats.save(state, stats, cls, "will"); state.perception = DerivedStats.perception(state, stats, cls);
    }

    private RuleItem classItem() { return state.className.isEmpty() ? null : store.findExact("class", state.className); }
    private RuleItem ancestryItem() { return state.ancestry.isEmpty() ? null : store.findExact("ancestry", state.ancestry); }
    private RuleItem equippedArmor() { return stats.equippedArmorId == null || stats.equippedArmorId.isEmpty() ? null : store.findById(stats.equippedArmorId); }
    private RuleItem firstSubtype(String subtype) { for (RuleItem item : inventoryItems()) if (subtype.equalsIgnoreCase(item.subtype)) return item; return null; }

    private List<RuleItem> inventoryItems() {
        List<RuleItem> out = new ArrayList<>(); for (int i=0;i<state.inventory.length();i++) { RuleItem item = store.findById(storedId(state.inventory.optString(i, ""))); if (item != null) out.add(item); } return out;
    }
    private boolean containsInventory(String id) { for (int i=0;i<state.inventory.length();i++) if (id.equals(storedId(state.inventory.optString(i,"")))) return true; return false; }
    private void removeInventory(String id) { for (int i=state.inventory.length()-1;i>=0;i--) if (id.equals(storedId(state.inventory.optString(i,"")))) state.inventory.remove(i); if (id.equals(stats.equippedArmorId)) { stats.equippedArmorId=""; stats.save(this); } }
    private int activeConditionCount() { int n=0; Iterator<String> it=state.conditions.keys(); while(it.hasNext()) if(state.conditions.optInt(it.next(),0)>0)n++; return n; }
    private static String storedId(String raw) { int i = raw == null ? -1 : raw.indexOf('\u001f'); return i >= 0 ? raw.substring(0, i) : raw; }
    private static boolean hasTrait(RuleItem item, String trait) { if (item == null) return false; for (String t : item.traits) if (trait.equalsIgnoreCase(t)) return true; return false; }

    private void rollD20(String label, int bonus) { int die = random.nextInt(20) + 1; toast(label + ": " + die + (bonus >= 0 ? "+" : "") + bonus + " = " + (die + bonus)); }
    private void toast(String value) { Toast.makeText(this, value, Toast.LENGTH_SHORT).show(); }
    private String show(String value) { return value == null || value.isEmpty() ? "—" : RuNames.shortName(value); }
    private String featMeta(RuleItem item) { return (item.subtype == null || item.subtype.isEmpty() ? "фит" : translatedSubtype(item.subtype)) + (item.level > 0 ? " • ур. " + item.level : ""); }
    private String translatedSubtype(String subtype) {
        if (subtype == null || subtype.isEmpty()) return "предмет"; switch (subtype.toLowerCase(Locale.ROOT)) { case "weapon": return "оружие"; case "armor": return "броня"; case "shield": return "щит"; case "consumable": return "расходник"; case "ammo": return "боеприпасы"; case "class": return "классовый"; case "ancestry": return "родовой"; case "skill": return "навыковый"; case "general": return "общий"; case "archetype": return "архетип"; default: return subtype; }
    }
    private String signed(int value) { return (value >= 0 ? "+" : "") + value; }
    private int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }

    private LinearLayout page() { LinearLayout l=column(); l.setPadding(dp(8),dp(6),dp(8),dp(28)); return l; }
    private LinearLayout panel() { LinearLayout l=column(); l.setPadding(dp(8),dp(7),dp(8),dp(7)); l.setBackground(round(PANEL,5,BORDER)); return l; }
    private TextView section(String value) { TextView v=text(value,13,true); v.setTextColor(ACCENT); v.setPadding(dp(5),dp(9),dp(5),dp(4)); return v; }
    private TextView note(String value) { TextView v=text(value,12,false); v.setTextColor(MUTED); v.setPadding(dp(5),dp(4),dp(5),dp(5)); return v; }
    private TextView pair(String left, String right) { TextView v=text(left + "\n" + right,14,false); v.setPadding(dp(8),dp(6),dp(8),dp(6)); v.setBackground(round(PANEL_2,4,BORDER)); LinearLayout.LayoutParams p=matchWrap(dp(2)); v.setLayoutParams(p); return v; }
    private TextView actionRow(String left, String right) { TextView v=text(left + "\n" + right,14,false); v.setPadding(dp(9),dp(7),dp(9),dp(7)); v.setBackground(round(PANEL_2,4,BORDER)); LinearLayout.LayoutParams p=matchWrap(dp(2)); v.setLayoutParams(p); return v; }
    private View divider() { View v=new View(this); v.setBackgroundColor(Color.rgb(218,218,214)); v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(1))); return v; }
    private TextView badge(String value,int color) { TextView v=text(value,10,true); v.setTextColor(Color.WHITE); v.setGravity(Gravity.CENTER); v.setPadding(dp(7),dp(4),dp(7),dp(4)); v.setBackground(round(color,10,color)); return v; }
    private TextView tab(String value, boolean active) { TextView v=text(value,11,true); v.setTextColor(active?TOP:Color.WHITE); v.setPadding(dp(10),dp(7),dp(10),dp(7)); v.setBackground(round(active?Color.rgb(226,208,181):TOP_2,3,active?Color.rgb(226,208,181):TOP_2)); return v; }
    private View abilityCell(String label,int score,int mod) { LinearLayout c=column(); c.setGravity(Gravity.CENTER); c.setPadding(dp(3),dp(5),dp(3),dp(5)); c.setBackground(round(PANEL,4,BORDER)); TextView l=text(label,10,true);l.setTextColor(MUTED);l.setGravity(Gravity.CENTER);TextView s=text(String.valueOf(score),17,true);s.setTextColor(ACCENT);s.setGravity(Gravity.CENTER);TextView m=text(signed(mod),10,false);m.setGravity(Gravity.CENTER);m.setTextColor(MUTED);c.addView(l);c.addView(s);c.addView(m);return c; }
    private View metricBox(String label,String value) { LinearLayout c=column(); c.setGravity(Gravity.CENTER); c.setPadding(dp(5),dp(7),dp(5),dp(7)); c.setBackground(round(PANEL_2,5,BORDER)); TextView l=text(label,10,true);l.setTextColor(MUTED);l.setGravity(Gravity.CENTER);TextView v=text(value,18,true);v.setTextColor(ACCENT);v.setGravity(Gravity.CENTER);c.addView(l);c.addView(v);return c; }
    private View statsRow(String[][] values) { LinearLayout r=row(); for(String[] value:values) r.addView(metricBox(value[0],value[1]),weighted(dp(2))); return r; }
    private Button button(String value) { Button b=new Button(this); b.setText(value); b.setAllCaps(false); b.setTextSize(12); b.setTextColor(ACCENT); b.setMinHeight(0); b.setMinimumHeight(0); b.setPadding(dp(7),dp(7),dp(7),dp(7)); b.setBackground(round(PANEL_2,5,BORDER)); return b; }
    private Button smallButton(String value) { Button b=button(value); b.setTextSize(11); b.setMinWidth(dp(42)); return b; }
    private EditText input(String hint) { EditText e=new EditText(this); e.setHint(hint); e.setSingleLine(true); e.setTextColor(TEXT); e.setHintTextColor(MUTED); e.setBackground(round(PANEL_2,5,BORDER)); e.setPadding(dp(9),dp(7),dp(9),dp(7)); return e; }
    private TextWatcher watcher(Runnable r) { return new TextWatcher(){ public void beforeTextChanged(CharSequence s,int st,int c,int a){} public void onTextChanged(CharSequence s,int st,int b,int c){r.run();} public void afterTextChanged(Editable e){} }; }
    private TextView text(String value,int sp,boolean bold) { TextView v=new TextView(this);v.setText(value);v.setTextSize(sp);v.setTextColor(TEXT);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v; }
    private ScrollView scroll(View child) { ScrollView s=new ScrollView(this);s.setFillViewport(true);s.addView(child);return s; }
    private LinearLayout row() { LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);return l; }
    private LinearLayout column() { LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l; }
    private GradientDrawable round(int color,int radius,int stroke) { GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));d.setStroke(dp(1),stroke);return d; }
    private int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }
    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT); }
    private LinearLayout.LayoutParams matchWrap(int margin) { LinearLayout.LayoutParams p=matchWrap();p.setMargins(0,margin,0,margin);return p; }
    private LinearLayout.LayoutParams wrapWrap(int margin) { LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);p.setMargins(margin,0,margin,0);return p; }
    private LinearLayout.LayoutParams weighted(int margin) { LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1);p.setMargins(margin,margin,margin,margin);return p; }
    private LinearLayout.LayoutParams fixed(int width) { return new LinearLayout.LayoutParams(width,ViewGroup.LayoutParams.WRAP_CONTENT); }
}
