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

/** Character planner backed by the executable PF2e rule graph. */
public final class MainActivityV3 extends Activity {
    private static final int BG = Color.rgb(239, 237, 232);
    private static final int HEADER = Color.rgb(94, 26, 40);
    private static final int HEADER_DARK = Color.rgb(71, 19, 30);
    private static final int CARD = Color.rgb(255, 255, 255);
    private static final int CARD_2 = Color.rgb(248, 246, 241);
    private static final int BORDER = Color.rgb(207, 199, 188);
    private static final int TEXT = Color.rgb(39, 36, 34);
    private static final int MUTED = Color.rgb(107, 101, 95);
    private static final int ACCENT = Color.rgb(125, 31, 48);
    private static final int GOOD = Color.rgb(42, 124, 79);
    private static final int BAD = Color.rgb(171, 55, 55);
    private static final int WARM = Color.rgb(175, 112, 44);

    private static final String[][] SKILLS = {
            {"acrobatics", "Акробатика"}, {"arcana", "Аркана"}, {"athletics", "Атлетика"},
            {"crafting", "Ремесло"}, {"deception", "Обман"}, {"diplomacy", "Дипломатия"},
            {"intimidation", "Запугивание"}, {"medicine", "Медицина"}, {"nature", "Природа"},
            {"occultism", "Оккультизм"}, {"performance", "Выступление"}, {"religion", "Религия"},
            {"society", "Общество"}, {"stealth", "Скрытность"}, {"survival", "Выживание"},
            {"thievery", "Воровство"}
    };
    private static final String[] RANKS = {"—", "ОБУЧЕН", "ЭКСПЕРТ", "МАСТЕР", "ЛЕГЕНДА"};
    private static final String[][] ABILITIES = {
            {"str", "СИЛ"}, {"dex", "ЛВК"}, {"con", "ТЕЛ"},
            {"int", "ИНТ"}, {"wis", "МДР"}, {"cha", "ХАР"}
    };

    private RuleStore store;
    private CharacterState state;
    private StatsState stats;
    private RuleRuntime.Snapshot runtime;
    private FrameLayout content;
    private TextView subtitle;
    private String screen = "build";

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(HEADER_DARK);
        store = new RuleStore(this);
        store.getReadableDatabase();
        state = CharacterState.load(this);
        stats = StatsState.load(this);
        rebuildRuntime();
        setContentView(shell());
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        if (store != null) {
            state = CharacterState.load(this);
            stats = StatsState.load(this);
            rebuildRuntime();
            if (content != null) render();
        }
    }

    private View shell() {
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
        TextView mode = text("ПЛАН ПЕРСОНАЖА", 11, true);
        mode.setTextColor(Color.rgb(242, 211, 183));
        titleLine.addView(mode);
        top.addView(titleLine);
        subtitle = text("", 12, false);
        subtitle.setTextColor(Color.rgb(235, 218, 210));
        top.addView(subtitle);
        root.addView(top, matchWrap());

        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setBackgroundColor(HEADER_DARK);
        LinearLayout nav = row();
        nav.setPadding(dp(5), dp(4), dp(5), dp(4));
        nav(nav, "BUILD", "build");
        nav(nav, "НАВЫКИ", "skills");
        nav(nav, "ЗАЩИТА", "play");
        nav(nav, "АТАКА", "play");
        nav(nav, "СНАРЯЖ.", "play");
        nav(nav, "ЗАКЛ.", "play");
        nav(nav, "ЛИСТ / БОЙ", "play");
        hsv.addView(nav);
        root.addView(hsv, matchWrap());

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        return root;
    }

    private void nav(LinearLayout parent, String label, String target) {
        boolean active = screen.equals(target) && !"play".equals(target);
        TextView v = tab(label, active);
        v.setOnClickListener(x -> {
            if ("play".equals(target)) {
                startActivity(new Intent(this, MainActivityV2.class));
                return;
            }
            screen = target;
            render();
        });
        parent.addView(v, wrapWrap(dp(2)));
    }

    private void render() {
        stats = StatsState.load(this);
        rebuildRuntime();
        String cls = state.className.isEmpty() ? "класс не выбран" : RuNames.shortName(state.className);
        int[] completion = completion();
        String remain = runtime.choices().isEmpty() ? "" : " • обязательных выборов " + runtime.choices().size();
        subtitle.setText("ур. " + state.level + " • " + cls + " • сборка " + completion[0] + "/" + completion[1] + remain);
        content.removeAllViews();
        content.addView(scroll("skills".equals(screen) ? skillsPage() : buildPage()));
    }

    private LinearLayout buildPage() {
        LinearLayout col = page();
        col.addView(heroCard(), matchWrap(dp(6)));

        List<RuleRuntime.ChoicePrompt> prompts = runtime.choices();
        if (!prompts.isEmpty()) {
            col.addView(section("НУЖНО ВЫБРАТЬ"));
            LinearLayout choices = card();
            for (RuleRuntime.ChoicePrompt prompt : prompts) choices.addView(ruleChoiceRow(prompt));
            col.addView(choices, matchWrap(dp(4)));
        }

        TextView progression = section("ПРОГРЕССИЯ 1–20");
        col.addView(progression);
        RuleItem cls = classItem();
        for (int level = 1; level <= 20; level++) {
            LinearLayout levelCard = card();
            levelCard.setPadding(0, 0, 0, dp(5));
            levelCard.addView(levelHeader(level));

            int rows = 0;
            for (RuleItem item : runtime.allItems()) {
                if (!runtime.isAutomatic(item.id) || runtime.automaticLevel(item.id) != level) continue;
                TextView auto = compactRow("✓", RuNames.shortName(item.name), "автоматически", GOOD);
                auto.setOnClickListener(v -> ruleDetail(item, null));
                levelCard.addView(auto);
                rows++;
            }

            if (RuleEngine.classHasSlot(cls, "classFeatLevels", level, new int[]{1,2,4,6,8,10,12,14,16,18,20})) {
                levelCard.addView(featSlot(level, "Классовый / архетипный фит", "class")); rows++;
            }
            if (RuleEngine.classHasSlot(cls, "ancestryFeatLevels", level, new int[]{1,5,9,13,17})) {
                levelCard.addView(featSlot(level, "Фит рода", "ancestry")); rows++;
            }
            if (RuleEngine.classHasSlot(cls, "skillFeatLevels", level, new int[]{2,4,6,8,10,12,14,16,18,20})) {
                levelCard.addView(featSlot(level, "Фит навыка", "skill")); rows++;
            }
            if (RuleEngine.classHasSlot(cls, "generalFeatLevels", level, new int[]{3,7,11,15,19})) {
                levelCard.addView(featSlot(level, "Общий фит", "general")); rows++;
            }
            if (RuleEngine.classHasSlot(cls, "skillIncreaseLevels", level, new int[]{3,5,7,9,11,13,15,17,19})) {
                TextView r = compactRow("↑", "Повышение навыка", "открыть навыки", WARM);
                r.setOnClickListener(v -> { screen = "skills"; render(); });
                levelCard.addView(r); rows++;
            }
            if (isAbilityBoostLevel(level)) {
                levelCard.addView(compactRow("◆", "Повышения характеристик", level == 1 ? "создание персонажа" : "4 повышения", ACCENT));
                rows++;
            }
            if (rows == 0) levelCard.addView(note("На этом уровне нет отдельного выбора."));
            col.addView(levelCard, matchWrap(dp(4)));
        }
        return col;
    }

    private View heroCard() {
        LinearLayout outer = card();
        outer.setPadding(dp(10), dp(9), dp(10), dp(10));

        EditText name = input("Имя персонажа");
        name.setText(state.name);
        name.setTextSize(20);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        name.setSelectAllOnFocus(false);
        name.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) { state.name = s.toString(); state.save(MainActivityV3.this); }
        });
        outer.addView(name, matchWrap(dp(3)));

        LinearLayout identity = column();
        identity.addView(selector("Род", state.ancestry, "ancestry", "base:ancestry"));
        identity.addView(selector("Наследие", state.choiceName("base:heritage"), "heritage", "base:heritage"));
        identity.addView(selector("Предыстория", state.background, "background", "base:background"));
        identity.addView(selector("Класс", state.className, "class", "base:class"));
        outer.addView(identity);
        outer.addView(levelRow());

        LinearLayout abilities = row();
        abilities.setGravity(Gravity.CENTER);
        abilities.setPadding(0, dp(7), 0, 0);
        for (String[] a : ABILITIES) abilities.addView(abilityBox(a[1], stats.abilityScore(a[0]), stats.ability(a[0])), new LinearLayout.LayoutParams(0, dp(64), 1));
        outer.addView(abilities);
        return outer;
    }

    private View abilityBox(String label, int score, int mod) {
        LinearLayout box = column();
        box.setGravity(Gravity.CENTER);
        box.setBackground(round(CARD_2, 7, BORDER));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(64), 1);
        p.setMargins(dp(2), 0, dp(2), 0);
        box.setLayoutParams(p);
        TextView l = text(label, 10, true); l.setTextColor(MUTED); l.setGravity(Gravity.CENTER);
        TextView s = text(String.valueOf(score), 18, true); s.setTextColor(ACCENT); s.setGravity(Gravity.CENTER);
        TextView m = text((mod >= 0 ? "+" : "") + mod, 10, false); m.setTextColor(MUTED); m.setGravity(Gravity.CENTER);
        box.addView(l); box.addView(s); box.addView(m);
        return box;
    }

    private View selector(String label, String current, String category, String key) {
        String value = current == null || current.isEmpty() ? "Выбрать" : RuNames.shortName(current);
        TextView row = compactRow(current == null || current.isEmpty() ? "+" : "✓", label, value, current == null || current.isEmpty() ? WARM : GOOD);
        row.setOnClickListener(v -> showBasePicker(category, item -> {
            if ("class".equals(category)) {
                clearSelectionsForNamed("class", state.className);
                state.className = item.name;
            } else if ("ancestry".equals(category)) {
                clearSelectionsForNamed("ancestry", state.ancestry);
                state.ancestry = item.name;
                state.setChoice("base:heritage", null);
            } else if ("background".equals(category)) {
                clearSelectionsForNamed("background", state.background);
                state.background = item.name;
            } else state.setChoice(key, item);
            saveAndRevalidate();
        }));
        return row;
    }

    private void clearSelectionsForNamed(String category, String name) {
        if (name == null || name.isEmpty()) return;
        RuleItem old = store.findExact(category, name);
        if (old != null) state.clearRuleSelectionsFor(old.id);
    }

    private View levelRow() {
        LinearLayout r = row();
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(8), dp(7), dp(8), dp(3));
        TextView label = text("УРОВЕНЬ", 12, true); label.setTextColor(MUTED);
        r.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button minus = mini("−"), plus = mini("+");
        TextView value = text(String.valueOf(state.level), 22, true); value.setTextColor(ACCENT); value.setGravity(Gravity.CENTER); value.setMinWidth(dp(48));
        minus.setOnClickListener(v -> { if (state.level > 1) { state.level--; saveAndRevalidate(); } });
        plus.setOnClickListener(v -> { if (state.level < 20) { state.level++; saveAndRevalidate(); } });
        r.addView(minus); r.addView(value); r.addView(plus);
        return r;
    }

    private View ruleChoiceRow(RuleRuntime.ChoicePrompt prompt) {
        String selected = state.ruleSelection(prompt.sourceId, prompt.flag);
        String shown = selected.isEmpty() ? "Выбрать" : selectedChoiceLabel(prompt, selected);
        boolean available = !prompt.options.isEmpty();
        TextView row = compactRow(selected.isEmpty() ? "!" : "✓", cleanPrompt(prompt), shown, selected.isEmpty() ? WARM : GOOD);
        if (!available) {
            row.setTextColor(MUTED);
            row.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle(cleanPrompt(prompt))
                    .setMessage("Для этого обязательного выбора пока не удалось сформировать варианты. Он не будет подменён случайным или неверным значением.")
                    .setPositiveButton("Понятно", null).show());
            return row;
        }
        row.setOnClickListener(v -> {
            String[] labels = new String[prompt.options.size()];
            for (int i = 0; i < labels.length; i++) labels[i] = choiceLabel(prompt.options.get(i));
            new AlertDialog.Builder(this).setTitle(cleanPrompt(prompt)).setItems(labels, (d, which) -> {
                state.setRuleSelection(prompt.sourceId, prompt.flag, prompt.options.get(which).value);
                saveAndRevalidate();
            }).setNegativeButton("Отмена", null).show();
        });
        return row;
    }

    private View featSlot(int level, String label, String slotCategory) {
        String key = "L" + level + ":" + slotCategory;
        String chosen = state.choiceName(key);
        TextView row = compactRow(chosen.isEmpty() ? "+" : "✓", label, chosen.isEmpty() ? "Выбрать" : RuNames.shortName(chosen), chosen.isEmpty() ? WARM : GOOD);
        row.setOnClickListener(v -> showFeatPicker(slotCategory, level, key));
        row.setOnLongClickListener(v -> { state.setChoice(key, null); saveAndRevalidate(); return true; });
        return row;
    }

    private LinearLayout skillsPage() {
        LinearLayout col = page();
        LinearLayout summary = card();
        TextView h = text("НАВЫКИ", 19, true); h.setTextColor(ACCENT); summary.addView(h);
        summary.addView(note("Автоматические владения класса и предыстории уже учтены. Повышение вручную ограничено уровнем персонажа."));
        col.addView(summary, matchWrap(dp(5)));

        LinearLayout c = card();
        for (String[] skill : SKILLS) {
            String key = skill[0];
            LinearLayout r = row();
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(dp(9), dp(7), dp(9), dp(7));
            TextView name = text(skill[1], 14, false);
            r.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            int effective = runtime.rank(state, key);
            TextView rank = badge(RANKS[Math.max(0, Math.min(4, effective))], effective > 0 ? GOOD : MUTED);
            Button minus = mini("−"), plus = mini("+");
            minus.setOnClickListener(v -> { state.setRank(key, Math.max(0, state.rank(key) - 1)); saveAndRevalidate(); });
            plus.setOnClickListener(v -> { state.setRank(key, Math.min(state.maxSkillRankForLevel(), state.rank(key) + 1)); saveAndRevalidate(); });
            r.addView(rank); r.addView(minus); r.addView(plus); c.addView(r);
        }
        col.addView(c, matchWrap(dp(5)));
        return col;
    }

    private View levelHeader(int level) {
        LinearLayout head = row();
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(10), dp(7), dp(10), dp(7));
        head.setBackground(round(level == state.level ? ACCENT : level < state.level ? HEADER_DARK : Color.rgb(126, 121, 116), 7, level <= state.level ? ACCENT : Color.rgb(126,121,116)));
        TextView left = text("УРОВЕНЬ " + level, 15, true); left.setTextColor(Color.WHITE);
        head.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        String mark = level < state.level ? "ГОТОВО" : level == state.level ? "ТЕКУЩИЙ" : "";
        if (!mark.isEmpty()) { TextView right = text(mark, 10, true); right.setTextColor(Color.rgb(248,226,208)); head.addView(right); }
        return head;
    }

    private TextView compactRow(String icon, String left, String right, int iconColor) {
        TextView v = text(icon + "  " + left + "\n     " + right, 14, false);
        v.setTextColor(TEXT);
        v.setPadding(dp(10), dp(8), dp(10), dp(8));
        v.setBackground(round(CARD_2, 6, BORDER));
        LinearLayout.LayoutParams p = matchWrap(dp(2));
        v.setLayoutParams(p);
        if (iconColor == BAD) v.setTextColor(BAD);
        return v;
    }

    private void showBasePicker(String category, Selection selection) {
        final EditText search = input("Поиск по-русски или по-английски");
        LinearLayout outer = column(); outer.setPadding(dp(10), dp(4), dp(10), dp(4)); outer.addView(search);
        ScrollView sv = new ScrollView(this); LinearLayout list = column(); sv.addView(list); outer.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(540)));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Выбор").setView(outer).setNegativeButton("Закрыть", null).create();
        Runnable refresh = () -> {
            list.removeAllViews(); String q = search.getText().toString(); int shown = 0;
            for (RuleItem item : store.query(category, 20, "", 700)) {
                if (!matches(item, q)) continue;
                TextView r = compactRow("+", RuNames.shortName(item.name), item.source, WARM);
                r.setOnClickListener(v -> { selection.select(item); dialog.dismiss(); });
                list.addView(r); if (++shown >= 240) break;
            }
            if (shown == 0) list.addView(note("Ничего не найдено."));
        };
        search.addTextChangedListener(watcher(refresh)); refresh.run(); dialog.show();
    }

    private void showFeatPicker(String slotCategory, int level, String choiceKey) {
        final EditText search = input("Поиск фита");
        final boolean[] showLocked = {false};
        LinearLayout outer = column(); outer.setPadding(dp(10), dp(4), dp(10), dp(4)); outer.addView(search);
        TextView filter = compactRow("☰", "Фильтр", "Только доступные", ACCENT); outer.addView(filter);
        TextView status = note(""); outer.addView(status);
        ScrollView sv = new ScrollView(this); LinearLayout list = column(); sv.addView(list); outer.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(560)));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Фит — уровень " + level).setView(outer)
                .setNeutralButton("Очистить", (d,w) -> { state.setChoice(choiceKey, null); saveAndRevalidate(); })
                .setNegativeButton("Закрыть", null).create();

        Runnable refresh = () -> {
            list.removeAllViews(); String q = search.getText().toString();
            List<RuleItem> candidates = featCandidates(slotCategory, level, q);
            List<RowCandidate> rows = new ArrayList<>();
            int open = 0;
            for (RuleItem item : candidates) {
                String reason = RuleEngine.blockReason(item, state, runtime, slotCategory, level);
                if (reason == null) open++;
                if (reason == null || showLocked[0]) rows.add(new RowCandidate(item, reason));
            }
            Collections.sort(rows, (a,b) -> {
                if ((a.reason == null) != (b.reason == null)) return a.reason == null ? -1 : 1;
                int lv = Integer.compare(a.item.level, b.item.level); return lv != 0 ? lv : a.item.name.compareToIgnoreCase(b.item.name);
            });
            int shown = 0;
            for (RowCandidate rc : rows) {
                String right = "ур. " + rc.item.level + (rc.reason == null ? " • доступен" : " • " + rc.reason);
                TextView r = compactRow(rc.reason == null ? "+" : "×", RuNames.shortName(rc.item.name), right, rc.reason == null ? GOOD : BAD);
                if (rc.reason != null) r.setTextColor(MUTED);
                r.setOnClickListener(v -> ruleDetail(rc.item, rc.reason == null ? () -> {
                    state.setChoice(choiceKey, rc.item); dialog.dismiss(); saveAndRevalidate();
                } : null));
                list.addView(r); if (++shown >= 260) break;
            }
            status.setText("Подходит по текущей сборке: " + open + (showLocked[0] ? " • показаны также недоступные" : ""));
            if (shown == 0) list.addView(note("Нет подходящих вариантов."));
        };
        filter.setOnClickListener(v -> {
            showLocked[0] = !showLocked[0];
            filter.setText("☰  Фильтр\n     " + (showLocked[0] ? "Доступные + недоступные" : "Только доступные"));
            refresh.run();
        });
        search.addTextChangedListener(watcher(refresh)); refresh.run(); dialog.show();
    }

    private List<RuleItem> featCandidates(String slot, int level, String search) {
        List<RuleItem> raw = new ArrayList<>(); Set<String> seen = new HashSet<>();
        if ("class".equals(slot)) {
            String group = RuleRuntime.slug(state.className);
            addAll(raw, seen, store.queryGroup("feat", "class", group, level, "", 500));
            addAll(raw, seen, store.queryGroup("feat", "archetype", "", level, "", 1100));
        } else if ("ancestry".equals(slot)) {
            addAll(raw, seen, store.bySubtype("feat", "ancestry", level, "", 1200));
        } else if ("skill".equals(slot)) {
            addAll(raw, seen, store.bySubtype("feat", "skill", level, "", 900));
        } else if ("general".equals(slot)) {
            addAll(raw, seen, store.bySubtype("feat", "general", level, "", 500));
            addAll(raw, seen, store.bySubtype("feat", "skill", level, "", 900));
        }
        if (search == null || search.trim().isEmpty()) return raw;
        List<RuleItem> out = new ArrayList<>();
        for (RuleItem item : raw) if (matches(item, search)) out.add(item);
        return out;
    }

    private boolean matches(RuleItem item, String q) {
        if (q == null || q.trim().isEmpty()) return true;
        String s = q.toLowerCase(Locale.ROOT).trim();
        if (RuNames.matches(item.name, q)) return true;
        if (item.description != null && item.description.toLowerCase(Locale.ROOT).contains(s)) return true;
        for (String t : item.traits) if (t.toLowerCase(Locale.ROOT).contains(s)) return true;
        return false;
    }

    private static void addAll(List<RuleItem> out, Set<String> seen, List<RuleItem> items) {
        for (RuleItem item : items) if (seen.add(item.id)) out.add(item);
    }

    private void ruleDetail(RuleItem item, Runnable choose) {
        StringBuilder body = new StringBuilder();
        if (item.level > 0) body.append("Уровень: ").append(item.level).append("\n");
        if (!item.traits.isEmpty()) body.append("Черты: ").append(item.traitsLine()).append("\n");
        if (!item.prerequisites.isEmpty()) body.append("Требования: ").append(String.join("; ", item.prerequisites)).append("\n");
        if (!item.source.isEmpty()) body.append("Источник: ").append(item.source).append("\n");
        body.append("\n").append(item.description == null ? "" : item.description);
        AlertDialog.Builder b = new AlertDialog.Builder(this).setTitle(RuNames.display(item.name)).setMessage(body.toString()).setNegativeButton("Закрыть", null);
        if (choose != null) b.setPositiveButton("Выбрать", (d,w) -> choose.run());
        b.show();
    }

    private void saveAndRevalidate() {
        state.save(this);
        StatsState.recalculate(state);
        stats = StatsState.load(this);
        for (int pass = 0; pass < 30; pass++) {
            rebuildRuntime();
            String remove = null;
            Iterator<String> it = state.choices.keys();
            while (it.hasNext()) {
                String key = it.next(); if (!key.startsWith("L")) continue;
                RuleItem item = store.findById(state.choiceId(key));
                int colon = key.indexOf(':'); int level = 1;
                try { level = Integer.parseInt(key.substring(1, colon)); } catch (Exception ignored) { }
                String slot = colon >= 0 ? key.substring(colon + 1) : "";
                if (RuleEngine.blockReason(item, state, runtime, slot, level) != null) { remove = key; break; }
            }
            if (remove == null) break;
            state.setChoice(remove, null);
        }
        state.save(this);
        StatsState.recalculate(state);
        stats = StatsState.load(this);
        rebuildRuntime();
        render();
    }

    private int[] completion() {
        int total = 4;
        int done = 0;
        if (!state.ancestry.isEmpty()) done++;
        if (!state.choiceName("base:heritage").isEmpty()) done++;
        if (!state.background.isEmpty()) done++;
        if (!state.className.isEmpty()) done++;
        RuleItem cls = classItem();
        for (int level = 1; level <= state.level; level++) {
            for (String slot : new String[]{"class","ancestry","skill","general"}) {
                if (!hasFeatSlot(cls, slot, level)) continue;
                total++;
                if (!state.choiceName("L" + level + ":" + slot).isEmpty()) done++;
            }
        }
        return new int[]{done, total};
    }

    private boolean hasFeatSlot(RuleItem cls, String slot, int level) {
        if ("class".equals(slot)) return RuleEngine.classHasSlot(cls, "classFeatLevels", level, new int[]{1,2,4,6,8,10,12,14,16,18,20});
        if ("ancestry".equals(slot)) return RuleEngine.classHasSlot(cls, "ancestryFeatLevels", level, new int[]{1,5,9,13,17});
        if ("skill".equals(slot)) return RuleEngine.classHasSlot(cls, "skillFeatLevels", level, new int[]{2,4,6,8,10,12,14,16,18,20});
        return RuleEngine.classHasSlot(cls, "generalFeatLevels", level, new int[]{3,7,11,15,19});
    }

    private static boolean isAbilityBoostLevel(int level) { return level == 1 || level == 5 || level == 10 || level == 15 || level == 20; }
    private void rebuildRuntime() { runtime = RuleRuntime.resolve(store, state, stats); }
    private RuleItem classItem() { return state.className.isEmpty() ? null : store.findExact("class", state.className); }

    private String cleanPrompt(RuleRuntime.ChoicePrompt prompt) {
        String flag = prompt.flag == null ? "" : prompt.flag;
        if (flag.startsWith("granAncestryBoost")) return "Повышение характеристики рода";
        if (flag.startsWith("granBackgroundBoost")) return "Повышение характеристики предыстории";
        if (flag.equals("granClassKey")) return "Ключевая характеристика класса";
        if (flag.startsWith("granFree")) return "Свободное повышение характеристики";
        if (flag.startsWith("granClassSkill")) return "Обученный навык класса";
        if (flag.equals("fighterSkill")) return "Навык воина";
        if (flag.toLowerCase(Locale.ROOT).contains("muse")) return "Муза барда";
        String raw = prompt.title;
        if (raw == null || raw.isEmpty() || raw.startsWith("PF2E.")) return "Дополнительный выбор";
        return raw;
    }

    private String selectedChoiceLabel(RuleRuntime.ChoicePrompt prompt, String selected) {
        for (RuleRuntime.Option option : prompt.options) if (String.valueOf(option.value).equals(selected)) return choiceLabel(option);
        return translateValue(selected);
    }

    private String choiceLabel(RuleRuntime.Option option) {
        String label = option.label == null ? "" : option.label;
        if (label.startsWith("PF2E.Skill.")) return skillName(label.substring("PF2E.Skill.".length()).toLowerCase(Locale.ROOT));
        if (label.startsWith("PF2E.Ability.")) return translateValue(option.value);
        if (!label.isEmpty() && !label.startsWith("PF2E.")) return RuNames.shortName(label);
        return translateValue(option.value);
    }

    private String translateValue(Object raw) {
        String value = raw == null ? "" : String.valueOf(raw);
        switch (value.toLowerCase(Locale.ROOT)) {
            case "str": return "Сила";
            case "dex": return "Ловкость";
            case "con": return "Телосложение";
            case "int": return "Интеллект";
            case "wis": return "Мудрость";
            case "cha": return "Харизма";
            default:
                String skill = skillName(value.toLowerCase(Locale.ROOT));
                return skill.equals(value) ? RuNames.shortName(value) : skill;
        }
    }

    private String skillName(String key) {
        for (String[] s : SKILLS) if (s[0].equals(key)) return s[1];
        return key;
    }

    private interface Selection { void select(RuleItem item); }
    private static final class RowCandidate { final RuleItem item; final String reason; RowCandidate(RuleItem i, String r) { item=i; reason=r; } }

    private TextWatcher watcher(Runnable r) { return new TextWatcher() { public void beforeTextChanged(CharSequence s,int st,int c,int a){} public void onTextChanged(CharSequence s,int st,int b,int c){r.run();} public void afterTextChanged(Editable e){} }; }
    private ScrollView scroll(View child) { ScrollView s = new ScrollView(this); s.setFillViewport(true); s.addView(child); return s; }
    private LinearLayout page() { LinearLayout c=column(); c.setPadding(dp(8),dp(7),dp(8),dp(22)); return c; }
    private LinearLayout column() { LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row() { LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); return l; }
    private LinearLayout card() { LinearLayout l=column(); l.setPadding(dp(7),dp(6),dp(7),dp(6)); l.setBackground(round(CARD,8,BORDER)); return l; }
    private TextView section(String s) { TextView v=text(s,13,true); v.setTextColor(ACCENT); v.setPadding(dp(4),dp(9),dp(4),dp(3)); return v; }
    private TextView note(String s) { TextView v=text(s,12,false); v.setTextColor(MUTED); v.setPadding(dp(7),dp(5),dp(7),dp(6)); return v; }
    private TextView badge(String s,int color) { TextView v=text(s,10,true); v.setTextColor(Color.WHITE); v.setGravity(Gravity.CENTER); v.setPadding(dp(7),dp(5),dp(7),dp(5)); v.setBackground(round(color,12,color)); return v; }
    private TextView tab(String s,boolean active) { TextView v=text(s,11,true); v.setTextColor(active?HEADER_DARK:Color.WHITE); v.setPadding(dp(11),dp(7),dp(11),dp(7)); v.setBackground(round(active?Color.rgb(242,211,183):HEADER_DARK,4,active?Color.rgb(242,211,183):Color.rgb(119,58,70))); return v; }
    private TextView text(String s,int size,boolean bold) { TextView v=new TextView(this); v.setText(s); v.setTextSize(size); v.setTextColor(TEXT); if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD); return v; }
    private EditText input(String hint) { EditText e=new EditText(this); e.setHint(hint); e.setHintTextColor(MUTED); e.setTextColor(TEXT); e.setSingleLine(true); e.setBackground(round(CARD_2,6,BORDER)); e.setPadding(dp(10),dp(7),dp(10),dp(7)); return e; }
    private Button mini(String s) { Button b=new Button(this); b.setText(s); b.setTextSize(17); b.setTextColor(TEXT); b.setMinWidth(dp(42)); b.setMinimumHeight(0); b.setMinHeight(dp(38)); return b; }
    private GradientDrawable round(int color,int radius,int stroke) { GradientDrawable g=new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(radius)); g.setStroke(dp(1),stroke); return g; }
    private int dp(int v) { return Math.round(v*getResources().getDisplayMetrics().density); }
    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT); }
    private LinearLayout.LayoutParams matchWrap(int margin) { LinearLayout.LayoutParams p=matchWrap(); p.setMargins(0,margin,0,margin); return p; }
    private LinearLayout.LayoutParams wrapWrap(int margin) { LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT); p.setMargins(margin,0,margin,0); return p; }
}
