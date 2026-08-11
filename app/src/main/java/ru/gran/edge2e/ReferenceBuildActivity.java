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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Gran 5.0 BUILD shell. The reference APK is used as a behavioral specification:
 * persistent BUILD/PLAY navigation, level navigation 1-20, contextual base pickers,
 * level-specific feat slots and rule-driven mandatory choices. UI/code are original.
 */
public final class ReferenceBuildActivity extends Activity {
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
    private RuleRuntime.Snapshot runtime;
    private FrameLayout content;
    private LinearLayout levelNav;
    private TextView headerName;
    private TextView headerStats;
    private int selectedLevel = 1;
    private String section = "levels";

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(TOP);
        store = new RuleStore(this);
        store.getReadableDatabase();
        loadState();
        selectedLevel = state.level;
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
        state = CharacterState.load(this);
        stats = StatsState.load(this);
        runtime = RuleRuntime.resolve(store, state, stats);
    }

    private View shell() {
        LinearLayout root = column(); root.setBackgroundColor(BG);

        LinearLayout top = column(); top.setPadding(dp(12), dp(7), dp(12), dp(7)); top.setBackgroundColor(TOP);
        LinearLayout line = row(); line.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("‹ ПЕРСОНАЖИ", 11, true); back.setTextColor(Color.WHITE); back.setPadding(0, dp(5), dp(12), dp(5));
        back.setOnClickListener(v -> { startActivity(new Intent(this, FrontPageActivity.class)); finish(); });
        line.addView(back);
        headerName = text("", 18, true); headerName.setTextColor(Color.WHITE); line.addView(headerName, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView play = text("ИГРА", 11, true); play.setTextColor(Color.rgb(236, 205, 169)); play.setPadding(dp(12), dp(5), 0, dp(5));
        play.setOnClickListener(v -> { Intent i = new Intent(this, ReferencePlayActivity.class); i.putExtra("screen", "character"); startActivity(i); });
        line.addView(play); top.addView(line);
        headerStats = text("", 11, false); headerStats.setTextColor(Color.rgb(211, 212, 213)); top.addView(headerStats);
        root.addView(top, matchWrap());

        LinearLayout mode = row(); mode.setBackgroundColor(TOP_2); mode.setPadding(dp(5), dp(4), dp(5), dp(4));
        TextView levels = modeTab("УРОВНИ", true); TextView skills = modeTab("НАВЫКИ", false); TextView reference = modeTab("СПРАВОЧНИК", false);
        levels.setOnClickListener(v -> { section = "levels"; render(); });
        skills.setOnClickListener(v -> { section = "skills"; render(); });
        reference.setOnClickListener(v -> { section = "reference"; render(); });
        mode.addView(levels, weighted(dp(2))); mode.addView(skills, weighted(dp(2))); mode.addView(reference, weighted(dp(2)));
        root.addView(mode, matchWrap());

        HorizontalScrollView levelScroll = new HorizontalScrollView(this); levelScroll.setHorizontalScrollBarEnabled(false); levelScroll.setBackgroundColor(PANEL_2);
        levelNav = row(); levelNav.setPadding(dp(5), dp(4), dp(5), dp(4)); levelScroll.addView(levelNav); root.addView(levelScroll, matchWrap());

        content = new FrameLayout(this); root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        return root;
    }

    private void render() {
        if (content == null) return;
        stats = StatsState.load(this);
        runtime = RuleRuntime.resolve(store, state, stats);
        headerName.setText(state.name == null || state.name.trim().isEmpty() ? "Новый герой" : state.name);
        String cls = state.className.isEmpty() ? "класс не выбран" : RuNames.shortName(state.className);
        int[] done = completion();
        headerStats.setText("СБОРКА • ур. " + state.level + " • " + cls + " • " + done[0] + "/" + done[1]);
        rebuildLevelNavigation();
        content.removeAllViews();
        View page = "skills".equals(section) ? skillsPage() : "reference".equals(section) ? referencePage() : levelPage(selectedLevel);
        content.addView(scroll(page));
        refreshModeTabs();
    }

    private void refreshModeTabs() {
        View p = (View) content.getParent();
        if (!(p instanceof LinearLayout)) return;
        LinearLayout root = (LinearLayout) p;
        if (root.getChildCount() < 2 || !(root.getChildAt(1) instanceof LinearLayout)) return;
        LinearLayout mode = (LinearLayout) root.getChildAt(1);
        for (int i = 0; i < mode.getChildCount(); i++) {
            if (!(mode.getChildAt(i) instanceof TextView)) continue;
            TextView v = (TextView) mode.getChildAt(i);
            boolean active = (i == 0 && "levels".equals(section)) || (i == 1 && "skills".equals(section)) || (i == 2 && "reference".equals(section));
            v.setTextColor(active ? TOP : Color.WHITE);
            v.setBackground(round(active ? Color.rgb(236, 205, 169) : TOP_2, 4, active ? Color.rgb(236,205,169) : Color.rgb(94,96,98)));
        }
    }

    private void rebuildLevelNavigation() {
        if (levelNav == null) return;
        levelNav.removeAllViews();
        for (int level = 1; level <= 20; level++) {
            final int target = level;
            TextView v = levelButton(level, selectedLevel == level, level <= state.level);
            v.setOnClickListener(x -> { selectedLevel = target; section = "levels"; render(); });
            levelNav.addView(v, wrapWrap(dp(2)));
        }
    }

    private LinearLayout levelPage(int level) {
        LinearLayout col = page();
        LinearLayout title = panel();
        LinearLayout t = row(); t.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = text("УРОВЕНЬ " + level, 23, true); name.setTextColor(ACCENT); t.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView status = badge(level == state.level ? "ТЕКУЩИЙ" : level < state.level ? "ПРОЙДЕН" : "БУДУЩИЙ", level <= state.level ? GOOD : MUTED); t.addView(status); title.addView(t);
        if (level != state.level) {
            Button set = button("СДЕЛАТЬ УРОВНЕМ ПЕРСОНАЖА");
            set.setOnClickListener(v -> { state.level = targetLevel(level); saveAndRevalidate(); selectedLevel = state.level; }); title.addView(set);
        }
        col.addView(title, matchWrap(dp(4)));

        if (level == 1) {
            col.addView(sectionTitle("ОСНОВА ПЕРСОНАЖА"));
            LinearLayout base = panel();
            base.addView(baseChoice("РОД", state.ancestry, "ancestry"));
            base.addView(baseChoice("НАСЛЕДИЕ", state.choiceName("base:heritage"), "heritage"));
            base.addView(baseChoice("ПРЕДЫСТОРИЯ", state.background, "background"));
            base.addView(baseChoice("КЛАСС", state.className, "class"));
            col.addView(base, matchWrap(dp(4)));

            col.addView(sectionTitle("ХАРАКТЕРИСТИКИ"));
            LinearLayout abilities = row();
            for (String[] a : ABILITIES) abilities.addView(abilityCell(a[1], stats.abilityScore(a[0]), stats.ability(a[0])), weighted(dp(2)));
            col.addView(abilities, matchWrap(dp(4)));
        }

        List<RuleRuntime.ChoicePrompt> prompts = runtime.choices();
        if (!prompts.isEmpty()) {
            col.addView(sectionTitle("ОБЯЗАТЕЛЬНЫЕ ВЫБОРЫ"));
            LinearLayout choices = panel();
            int shown = 0;
            for (RuleRuntime.ChoicePrompt prompt : prompts) {
                choices.addView(ruleChoiceRow(prompt));
                if (++shown >= 24) break;
            }
            col.addView(choices, matchWrap(dp(4)));
        }

        col.addView(sectionTitle("ФИТЫ И ОСОБЕННОСТИ"));
        LinearLayout features = panel(); int rows = 0;
        for (RuleItem item : runtime.allItems()) {
            if (!runtime.isAutomatic(item.id) || runtime.automaticLevel(item.id) != level) continue;
            TextView auto = actionRow("✓  " + RuNames.shortName(item.name), "автоматически");
            auto.setOnClickListener(v -> ruleDetail(item, null)); features.addView(auto); rows++;
        }
        RuleItem cls = classItem();
        if (hasFeatSlot(cls, "class", level)) { features.addView(featSlot(level, "КЛАССОВЫЙ / АРХЕТИПНЫЙ ФИТ", "class")); rows++; }
        if (hasFeatSlot(cls, "ancestry", level)) { features.addView(featSlot(level, "ФИТ РОДА", "ancestry")); rows++; }
        if (hasFeatSlot(cls, "skill", level)) { features.addView(featSlot(level, "ФИТ НАВЫКА", "skill")); rows++; }
        if (hasFeatSlot(cls, "general", level)) { features.addView(featSlot(level, "ОБЩИЙ ФИТ", "general")); rows++; }
        if (hasSkillIncreaseSlot(cls, level)) {
            TextView skill = actionRow("↑  ПОВЫШЕНИЕ НАВЫКА", "открыть навыки"); skill.setOnClickListener(v -> { section = "skills"; render(); }); features.addView(skill); rows++;
        }
        if (isAbilityBoostLevel(level)) {
            TextView boosts = actionRow("◆  ПОВЫШЕНИЯ ХАРАКТЕРИСТИК", level == 1 ? "выборы создания персонажа" : "4 повышения");
            boosts.setOnClickListener(v -> showAbilityPrompts()); features.addView(boosts); rows++;
        }
        if (rows == 0) features.addView(note("На этом уровне нет отдельных решений. Автоматические особенности появятся здесь после выбора класса."));
        col.addView(features, matchWrap(dp(4)));

        if (level == state.level) {
            col.addView(sectionTitle("БЫСТРЫЙ ПЕРЕХОД"));
            LinearLayout actions = panel();
            TextView play = actionRow("ИГРА", "открыть лист персонажа"); play.setOnClickListener(v -> startActivity(new Intent(this, ReferencePlayActivity.class))); actions.addView(play);
            TextView skills = actionRow("НАВЫКИ", "проверить владения и повышения"); skills.setOnClickListener(v -> { section = "skills"; render(); }); actions.addView(skills);
            col.addView(actions, matchWrap(dp(4)));
        }
        return col;
    }

    private int targetLevel(int level) { return Math.max(1, Math.min(20, level)); }

    private View baseChoice(String label, String current, String type) {
        boolean empty = current == null || current.isEmpty();
        TextView row = actionRow(label, empty ? "ВЫБРАТЬ" : RuNames.shortName(current));
        row.setTextColor(empty ? WARM : TEXT);
        row.setOnClickListener(v -> {
            switch (type) {
                case "ancestry": showAncestryPicker(); break;
                case "heritage": showHeritagePicker(); break;
                case "background": showBackgroundPicker(); break;
                case "class": showClassPicker(); break;
            }
        });
        return row;
    }

    private View featSlot(int level, String label, String slotCategory) {
        String key = "L" + level + ":" + slotCategory;
        String chosen = state.choiceName(key);
        TextView row = actionRow(label, chosen.isEmpty() ? "ВЫБРАТЬ" : RuNames.shortName(chosen));
        row.setTextColor(chosen.isEmpty() ? WARM : TEXT);
        row.setOnClickListener(v -> showFeatBrowser(slotCategory, level, key));
        row.setOnLongClickListener(v -> { state.setChoice(key, null); saveAndRevalidate(); return true; });
        return row;
    }

    private LinearLayout skillsPage() {
        LinearLayout col = page();
        col.addView(sectionTitle("НАВЫКИ"));
        LinearLayout summary = panel(); summary.addView(note("Владения от рода, предыстории, класса и правил рассчитываются автоматически. Ручное повышение ограничено текущим уровнем.")); col.addView(summary, matchWrap(dp(4)));
        LinearLayout table = panel();
        for (String[] skill : SKILLS) {
            String key = skill[0]; int effective = runtime.rank(state, key);
            LinearLayout r = row(); r.setGravity(Gravity.CENTER_VERTICAL); r.setPadding(dp(8), dp(7), dp(8), dp(7));
            TextView label = text(skill[1], 15, true); r.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            TextView rank = text(RANKS[Math.max(0, Math.min(4, effective))], 10, true); rank.setTextColor(effective > 0 ? GOOD : MUTED); rank.setGravity(Gravity.CENTER); rank.setMinWidth(dp(82)); r.addView(rank);
            Button minus = smallButton("−"); Button plus = smallButton("+");
            minus.setOnClickListener(v -> { state.setRank(key, Math.max(0, state.rank(key) - 1)); saveAndRevalidate(); });
            plus.setOnClickListener(v -> { state.setRank(key, Math.min(state.maxSkillRankForLevel(), state.rank(key) + 1)); saveAndRevalidate(); });
            r.addView(minus, fixed(dp(48))); r.addView(plus, fixed(dp(48))); table.addView(r); table.addView(divider());
        }
        col.addView(table, matchWrap(dp(4))); return col;
    }

    private LinearLayout referencePage() {
        LinearLayout col = page(); col.addView(sectionTitle("СПРАВОЧНИК"));
        LinearLayout searchCard = panel();
        EditText search = input("Поиск правила, фита, заклинания, предмета"); searchCard.addView(search);
        LinearLayout results = column(); searchCard.addView(results); col.addView(searchCard, matchWrap(dp(4)));
        Runnable refresh = () -> {
            results.removeAllViews(); String q = search.getText().toString().trim();
            if (q.length() < 2) { results.addView(note("Введи минимум 2 символа. Поиск идёт по локальной русской базе и английским именам.")); return; }
            int shown = 0;
            for (RuleItem item : store.query("all", 20, "", 1200)) {
                if (!matches(item, q)) continue;
                TextView row = actionRow(RuNames.shortName(item.name), categoryLabel(item) + (item.level > 0 ? " • ур. " + item.level : "")); row.setOnClickListener(v -> ruleDetail(item, null)); results.addView(row);
                if (++shown >= 80) break;
            }
            if (shown == 0) results.addView(note("Ничего не найдено."));
        };
        search.addTextChangedListener(watcher(refresh)); refresh.run(); return col;
    }

    private String categoryLabel(RuleItem item) {
        if (item == null) return "";
        if ("feat".equals(item.category)) return "Фит";
        if ("spell".equals(item.category)) return "Заклинание";
        if ("equipment".equals(item.category)) return "Снаряжение";
        if ("action".equals(item.category)) return "Действие";
        if ("condition".equals(item.category)) return "Состояние";
        if ("class-feature".equals(item.category)) return "Особенность";
        if ("ancestry".equals(item.category)) return "Род";
        if ("heritage".equals(item.category)) return "Наследие";
        if ("background".equals(item.category)) return "Предыстория";
        if ("class".equals(item.category)) return "Класс";
        return RuNames.shortName(item.category);
    }

    private void showAncestryPicker() {
        showBaseBrowser("ВЫБОР РОДА", "ancestry", "Поиск рода", item -> {
            clearSelectionsForNamed("ancestry", state.ancestry); state.ancestry = item.name; state.setChoice("base:heritage", null); saveAndRevalidate();
        });
    }

    private void showHeritagePicker() {
        if (state.ancestry == null || state.ancestry.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("Наследие").setMessage("Сначала выбери род — список наследий зависит от него.").setPositiveButton("Понятно", null).show(); return;
        }
        showBaseBrowser("НАСЛЕДИЕ — " + RuNames.shortName(state.ancestry), "heritage", "Поиск наследия", item -> { state.setChoice("base:heritage", item); saveAndRevalidate(); });
    }

    private void showBackgroundPicker() {
        showBaseBrowser("ВЫБОР ПРЕДЫСТОРИИ", "background", "Поиск предыстории", item -> {
            clearSelectionsForNamed("background", state.background); state.background = item.name; saveAndRevalidate();
        });
    }

    private void showClassPicker() {
        showBaseBrowser("ВЫБОР КЛАССА", "class", "Поиск класса", item -> {
            clearSelectionsForNamed("class", state.className); state.className = item.name; saveAndRevalidate();
        });
    }

    private void showBaseBrowser(String title, String category, String hint, Selection selection) {
        LinearLayout outer = column(); outer.setPadding(dp(10), dp(5), dp(10), dp(5));
        EditText search = input(hint); outer.addView(search);
        TextView info = note("Нажми на вариант для выбора. Долгое нажатие открывает полное описание."); outer.addView(info);
        ScrollView sv = new ScrollView(this); LinearLayout list = column(); sv.addView(list); outer.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(560)));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(title).setView(outer).setNegativeButton("Закрыть", null).create();
        Runnable refresh = () -> {
            list.removeAllViews(); String q = search.getText().toString(); int shown = 0;
            for (RuleItem item : store.query(category, 20, "", 1000)) {
                if (!matches(item, q)) continue;
                String meta = item.level > 0 ? "ур. " + item.level : (!item.source.isEmpty() ? item.source : categoryLabel(item));
                TextView r = actionRow(RuNames.shortName(item.name), meta);
                r.setOnClickListener(v -> { selection.select(item); dialog.dismiss(); });
                r.setOnLongClickListener(v -> { ruleDetail(item, null); return true; }); list.addView(r); if (++shown >= 280) break;
            }
            if (shown == 0) list.addView(note("Ничего не найдено."));
        };
        search.addTextChangedListener(watcher(refresh)); refresh.run(); dialog.show();
    }

    private void showFeatBrowser(String slotCategory, int level, String choiceKey) {
        LinearLayout outer = column(); outer.setPadding(dp(10), dp(5), dp(10), dp(5));
        EditText search = input("Поиск фита"); outer.addView(search);
        final boolean[] showLocked = {false};
        TextView filter = actionRow("ФИЛЬТР", "ТОЛЬКО ДОСТУПНЫЕ"); outer.addView(filter);
        TextView status = note(""); outer.addView(status);
        ScrollView sv = new ScrollView(this); LinearLayout list = column(); sv.addView(list); outer.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(560)));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(featBrowserTitle(slotCategory, level)).setView(outer)
                .setNeutralButton("Очистить", (d,w) -> { state.setChoice(choiceKey, null); saveAndRevalidate(); })
                .setNegativeButton("Закрыть", null).create();
        Runnable refresh = () -> {
            list.removeAllViews(); List<RuleItem> candidates = featCandidates(slotCategory, level, search.getText().toString()); List<RowCandidate> rows = new ArrayList<>(); int open = 0;
            for (RuleItem item : candidates) {
                String reason = RuleEngine.blockReason(item, state, runtime, slotCategory, level); if (reason == null) open++;
                if (reason == null || showLocked[0]) rows.add(new RowCandidate(item, reason));
            }
            Collections.sort(rows, (a,b) -> { if ((a.reason == null) != (b.reason == null)) return a.reason == null ? -1 : 1; int lv = Integer.compare(a.item.level, b.item.level); return lv != 0 ? lv : a.item.name.compareToIgnoreCase(b.item.name); });
            int shown = 0;
            for (RowCandidate rc : rows) {
                String meta = "ур. " + rc.item.level + (rc.reason == null ? " • ДОСТУПЕН" : " • " + rc.reason);
                TextView r = actionRow(RuNames.shortName(rc.item.name), meta); r.setTextColor(rc.reason == null ? TEXT : MUTED);
                r.setOnClickListener(v -> ruleDetail(rc.item, rc.reason == null ? () -> { state.setChoice(choiceKey, rc.item); dialog.dismiss(); saveAndRevalidate(); } : null));
                list.addView(r); if (++shown >= 300) break;
            }
            status.setText("Доступно: " + open + (showLocked[0] ? " • показаны также недоступные" : "")); if (shown == 0) list.addView(note("Нет подходящих вариантов."));
        };
        filter.setOnClickListener(v -> { showLocked[0] = !showLocked[0]; filter.setText("ФИЛЬТР\n" + (showLocked[0] ? "ДОСТУПНЫЕ + НЕДОСТУПНЫЕ" : "ТОЛЬКО ДОСТУПНЫЕ")); refresh.run(); });
        search.addTextChangedListener(watcher(refresh)); refresh.run(); dialog.show();
    }

    private String featBrowserTitle(String slot, int level) {
        if ("class".equals(slot)) return "КЛАССОВЫЕ И АРХЕТИПНЫЕ ФИТЫ — УР. " + level;
        if ("ancestry".equals(slot)) return "ФИТЫ РОДА — УР. " + level;
        if ("skill".equals(slot)) return "ФИТЫ НАВЫКОВ — УР. " + level;
        return "ОБЩИЕ ФИТЫ — УР. " + level;
    }

    private List<RuleItem> featCandidates(String slot, int level, String search) {
        List<RuleItem> raw = new ArrayList<>(); Set<String> seen = new HashSet<>();
        if ("class".equals(slot)) {
            String group = RuleRuntime.slug(state.className); addAll(raw, seen, store.queryGroup("feat", "class", group, level, "", 600)); addAll(raw, seen, store.queryGroup("feat", "archetype", "", level, "", 1300));
        } else if ("ancestry".equals(slot)) addAll(raw, seen, store.bySubtype("feat", "ancestry", level, "", 1300));
        else if ("skill".equals(slot)) addAll(raw, seen, store.bySubtype("feat", "skill", level, "", 1000));
        else { addAll(raw, seen, store.bySubtype("feat", "general", level, "", 600)); addAll(raw, seen, store.bySubtype("feat", "skill", level, "", 1000)); }
        if (search == null || search.trim().isEmpty()) return raw; List<RuleItem> out = new ArrayList<>(); for (RuleItem item : raw) if (matches(item, search)) out.add(item); return out;
    }

    private View ruleChoiceRow(RuleRuntime.ChoicePrompt prompt) {
        String selected = state.ruleSelection(prompt.sourceId, prompt.flag); String shown = selected.isEmpty() ? "ВЫБРАТЬ" : selectedChoiceLabel(prompt, selected);
        TextView row = actionRow(cleanPrompt(prompt), shown); row.setTextColor(selected.isEmpty() ? WARM : TEXT);
        if (prompt.options.isEmpty()) {
            row.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle(cleanPrompt(prompt)).setMessage("В текущем графе правил для этого обязательного выбора пока не сформированы варианты.").setPositiveButton("Понятно", null).show()); return row;
        }
        row.setOnClickListener(v -> {
            String[] labels = new String[prompt.options.size()]; for (int i = 0; i < labels.length; i++) labels[i] = choiceLabel(prompt.options.get(i));
            new AlertDialog.Builder(this).setTitle(cleanPrompt(prompt)).setItems(labels, (d, which) -> { state.setRuleSelection(prompt.sourceId, prompt.flag, prompt.options.get(which).value); saveAndRevalidate(); }).setNegativeButton("Отмена", null).show();
        }); return row;
    }

    private void showAbilityPrompts() {
        List<RuleRuntime.ChoicePrompt> ability = new ArrayList<>();
        for (RuleRuntime.ChoicePrompt p : runtime.choices()) {
            String f = p.flag == null ? "" : p.flag.toLowerCase(Locale.ROOT);
            if (f.contains("boost") || f.contains("classkey") || f.contains("granfree")) ability.add(p);
        }
        if (ability.isEmpty()) { new AlertDialog.Builder(this).setTitle("Характеристики").setMessage("Все доступные повышения для текущей сборки уже выбраны или применяются автоматически.").setPositiveButton("Закрыть", null).show(); return; }
        LinearLayout list = column(); list.setPadding(dp(8), dp(6), dp(8), dp(6)); for (RuleRuntime.ChoicePrompt p : ability) list.addView(ruleChoiceRow(p));
        new AlertDialog.Builder(this).setTitle("ПОВЫШЕНИЯ ХАРАКТЕРИСТИК").setView(list).setNegativeButton("Закрыть", null).show();
    }

    private void clearSelectionsForNamed(String category, String name) {
        if (name == null || name.isEmpty()) return; RuleItem old = store.findExact(category, name); if (old != null) state.clearRuleSelectionsFor(old.id);
    }

    private void saveAndRevalidate() {
        state.save(this); StatsState.recalculate(state); stats = StatsState.load(this);
        for (int pass = 0; pass < 30; pass++) {
            runtime = RuleRuntime.resolve(store, state, stats); String remove = null; Iterator<String> it = state.choices.keys();
            while (it.hasNext()) {
                String key = it.next(); if (!key.startsWith("L")) continue; RuleItem item = store.findById(state.choiceId(key)); int colon = key.indexOf(':'); int level = 1;
                try { level = Integer.parseInt(key.substring(1, colon)); } catch (Exception ignored) { }
                String slot = colon >= 0 ? key.substring(colon + 1) : ""; if (RuleEngine.blockReason(item, state, runtime, slot, level) != null) { remove = key; break; }
            }
            if (remove == null) break; state.setChoice(remove, null);
        }
        state.save(this); StatsState.recalculate(state); loadState(); render();
    }

    private void ruleDetail(RuleItem item, Runnable choose) {
        if (item == null) return; StringBuilder body = new StringBuilder();
        if (item.level > 0) body.append("Уровень: ").append(item.level).append("\n");
        if (!item.traits.isEmpty()) body.append("Черты: ").append(item.traitsLine()).append("\n");
        if (!item.prerequisites.isEmpty()) body.append("Требования: ").append(String.join("; ", item.prerequisites)).append("\n");
        if (!item.source.isEmpty()) body.append("Источник: ").append(item.source).append("\n");
        body.append("\n").append(item.description == null ? "" : item.description);
        AlertDialog.Builder b = new AlertDialog.Builder(this).setTitle(RuNames.display(item.name)).setMessage(body.toString()).setNegativeButton("Закрыть", null);
        if (choose != null) b.setPositiveButton("Выбрать", (d,w) -> choose.run()); b.show();
    }

    private int[] completion() {
        int total = 4, done = 0; if (!state.ancestry.isEmpty()) done++; if (!state.choiceName("base:heritage").isEmpty()) done++; if (!state.background.isEmpty()) done++; if (!state.className.isEmpty()) done++;
        RuleItem cls = classItem(); for (int level = 1; level <= state.level; level++) for (String slot : new String[]{"class","ancestry","skill","general"}) if (hasFeatSlot(cls, slot, level)) { total++; if (!state.choiceName("L" + level + ":" + slot).isEmpty()) done++; }
        return new int[]{done,total};
    }

    private boolean hasFeatSlot(RuleItem cls, String slot, int level) {
        if ("class".equals(slot)) return RuleEngine.classHasSlot(cls, "classFeatLevels", level, new int[]{1,2,4,6,8,10,12,14,16,18,20});
        if ("ancestry".equals(slot)) return RuleEngine.classHasSlot(cls, "ancestryFeatLevels", level, new int[]{1,5,9,13,17});
        if ("skill".equals(slot)) return RuleEngine.classHasSlot(cls, "skillFeatLevels", level, new int[]{2,4,6,8,10,12,14,16,18,20});
        return RuleEngine.classHasSlot(cls, "generalFeatLevels", level, new int[]{3,7,11,15,19});
    }

    private boolean hasSkillIncreaseSlot(RuleItem cls, int level) { return RuleEngine.classHasSlot(cls, "skillIncreaseLevels", level, new int[]{3,5,7,9,11,13,15,17,19}); }
    private static boolean isAbilityBoostLevel(int level) { return level == 1 || level == 5 || level == 10 || level == 15 || level == 20; }
    private RuleItem classItem() { return state.className.isEmpty() ? null : store.findExact("class", state.className); }

    private boolean matches(RuleItem item, String q) {
        if (q == null || q.trim().isEmpty()) return true; String s = q.toLowerCase(Locale.ROOT).trim(); if (RuNames.matches(item.name, q)) return true;
        if (item.description != null && item.description.toLowerCase(Locale.ROOT).contains(s)) return true; for (String t : item.traits) if (t.toLowerCase(Locale.ROOT).contains(s)) return true; return false;
    }

    private static void addAll(List<RuleItem> out, Set<String> seen, List<RuleItem> items) { for (RuleItem item : items) if (seen.add(item.id)) out.add(item); }

    private String cleanPrompt(RuleRuntime.ChoicePrompt prompt) {
        String flag = prompt.flag == null ? "" : prompt.flag;
        if (flag.startsWith("granAncestryBoost")) return "Повышение характеристики рода";
        if (flag.startsWith("granBackgroundBoost")) return "Повышение характеристики предыстории";
        if (flag.equals("granClassKey")) return "Ключевая характеристика класса";
        if (flag.startsWith("granFree")) return "Свободное повышение характеристики";
        if (flag.startsWith("granClassSkill")) return "Обученный навык класса";
        if (flag.equals("fighterSkill")) return "Навык воина";
        if (flag.toLowerCase(Locale.ROOT).contains("muse")) return "Муза барда";
        String raw = prompt.title; if (raw == null || raw.isEmpty() || raw.startsWith("PF2E.")) return "Дополнительный выбор"; return raw;
    }

    private String selectedChoiceLabel(RuleRuntime.ChoicePrompt prompt, String selected) { for (RuleRuntime.Option option : prompt.options) if (String.valueOf(option.value).equals(selected)) return choiceLabel(option); return translateValue(selected); }
    private String choiceLabel(RuleRuntime.Option option) { String label = option.label == null ? "" : option.label; if (label.startsWith("PF2E.Skill.")) return skillName(label.substring("PF2E.Skill.".length()).toLowerCase(Locale.ROOT)); if (label.startsWith("PF2E.Ability.")) return translateValue(option.value); if (!label.isEmpty() && !label.startsWith("PF2E.")) return RuNames.shortName(label); return translateValue(option.value); }
    private String translateValue(Object raw) { String value = raw == null ? "" : String.valueOf(raw); switch (value.toLowerCase(Locale.ROOT)) { case "str": return "Сила"; case "dex": return "Ловкость"; case "con": return "Телосложение"; case "int": return "Интеллект"; case "wis": return "Мудрость"; case "cha": return "Харизма"; default: String skill = skillName(value.toLowerCase(Locale.ROOT)); return skill.equals(value) ? RuNames.shortName(value) : skill; } }
    private String skillName(String key) { for (String[] s : SKILLS) if (s[0].equals(key)) return s[1]; return key; }

    private View abilityCell(String label, int score, int mod) {
        LinearLayout box = column(); box.setGravity(Gravity.CENTER); box.setPadding(dp(3), dp(6), dp(3), dp(6)); box.setBackground(round(PANEL, 4, BORDER));
        TextView a = text(label, 9, true); a.setTextColor(MUTED); a.setGravity(Gravity.CENTER); TextView s = text(String.valueOf(score), 18, true); s.setTextColor(ACCENT); s.setGravity(Gravity.CENTER); TextView m = text((mod >= 0 ? "+" : "") + mod, 10, false); m.setTextColor(MUTED); m.setGravity(Gravity.CENTER); box.addView(a); box.addView(s); box.addView(m); return box;
    }

    private TextView levelButton(int level, boolean active, boolean reached) { TextView v = text(String.valueOf(level), 12, true); v.setGravity(Gravity.CENTER); v.setMinWidth(dp(38)); v.setPadding(dp(8), dp(8), dp(8), dp(8)); v.setTextColor(active ? Color.WHITE : reached ? ACCENT : MUTED); v.setBackground(round(active ? ACCENT : PANEL, 4, active ? ACCENT : BORDER)); return v; }
    private TextView modeTab(String label, boolean active) { TextView v = text(label, 11, true); v.setGravity(Gravity.CENTER); v.setTextColor(active ? TOP : Color.WHITE); v.setPadding(dp(9), dp(7), dp(9), dp(7)); v.setBackground(round(active ? Color.rgb(236,205,169) : TOP_2, 4, active ? Color.rgb(236,205,169) : Color.rgb(94,96,98))); return v; }
    private TextView sectionTitle(String s) { TextView v = text(s, 13, true); v.setTextColor(ACCENT); v.setPadding(dp(3), dp(9), dp(3), dp(4)); return v; }
    private TextView actionRow(String left, String right) { TextView v = text(left + "\n" + right, 14, false); v.setTextColor(TEXT); v.setPadding(dp(10), dp(9), dp(10), dp(9)); v.setBackground(round(PANEL_2, 3, BORDER)); LinearLayout.LayoutParams p = matchWrap(dp(2)); v.setLayoutParams(p); return v; }
    private TextView note(String s) { TextView v = text(s, 12, false); v.setTextColor(MUTED); v.setPadding(dp(7), dp(5), dp(7), dp(6)); return v; }
    private TextView badge(String s, int color) { TextView v = text(s, 10, true); v.setTextColor(Color.WHITE); v.setGravity(Gravity.CENTER); v.setPadding(dp(7), dp(5), dp(7), dp(5)); v.setBackground(round(color, 12, color)); return v; }
    private View divider() { View v = new View(this); v.setBackgroundColor(BORDER); v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))); return v; }
    private Button button(String s) { Button b = new Button(this); b.setText(s); b.setAllCaps(false); b.setTextSize(12); b.setTextColor(ACCENT); b.setBackground(round(PANEL_2, 5, BORDER)); return b; }
    private Button smallButton(String s) { Button b = button(s); b.setTextSize(16); b.setMinimumHeight(0); b.setMinHeight(dp(38)); return b; }
    private EditText input(String hint) { EditText e = new EditText(this); e.setHint(hint); e.setHintTextColor(MUTED); e.setTextColor(TEXT); e.setSingleLine(true); e.setBackground(round(PANEL, 5, BORDER)); e.setPadding(dp(10), dp(7), dp(10), dp(7)); return e; }
    private TextView text(String s, int size, boolean bold) { TextView v = new TextView(this); v.setText(s); v.setTextSize(size); v.setTextColor(TEXT); if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return v; }
    private LinearLayout panel() { LinearLayout l = column(); l.setPadding(dp(8), dp(7), dp(8), dp(7)); l.setBackground(round(PANEL, 5, BORDER)); return l; }
    private LinearLayout page() { LinearLayout l = column(); l.setPadding(dp(8), dp(7), dp(8), dp(26)); return l; }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); return l; }
    private ScrollView scroll(View child) { ScrollView s = new ScrollView(this); s.setFillViewport(true); s.addView(child); return s; }
    private GradientDrawable round(int color, int radius, int stroke) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); d.setStroke(dp(1), stroke); return d; }
    private TextWatcher watcher(Runnable r) { return new TextWatcher() { public void beforeTextChanged(CharSequence s,int st,int c,int a){} public void onTextChanged(CharSequence s,int st,int b,int c){r.run();} public void afterTextChanged(Editable e){} }; }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); }
    private LinearLayout.LayoutParams matchWrap(int margin) { LinearLayout.LayoutParams p = matchWrap(); p.setMargins(0, margin, 0, margin); return p; }
    private LinearLayout.LayoutParams wrapWrap(int margin) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT); p.setMargins(margin, 0, margin, 0); return p; }
    private LinearLayout.LayoutParams weighted(int margin) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1); p.setMargins(margin, 0, margin, 0); return p; }
    private LinearLayout.LayoutParams fixed(int width) { return new LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT); }
    private interface Selection { void select(RuleItem item); }
    private static final class RowCandidate { final RuleItem item; final String reason; RowCandidate(RuleItem i, String r) { item = i; reason = r; } }
}
