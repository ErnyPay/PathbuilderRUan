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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(17, 24, 32);
    private static final int SURFACE = Color.rgb(29, 39, 49);
    private static final int SURFACE_2 = Color.rgb(39, 50, 61);
    private static final int TEXT = Color.rgb(239, 239, 232);
    private static final int MUTED = Color.rgb(171, 181, 187);
    private static final int ACCENT = Color.rgb(215, 154, 66);
    private static final int GOOD = Color.rgb(76, 175, 125);
    private static final int BAD = Color.rgb(198, 79, 78);

    private static final String[][] SKILLS = {
            {"acrobatics", "Акробатика"}, {"arcana", "Аркана"}, {"athletics", "Атлетика"},
            {"crafting", "Ремесло"}, {"deception", "Обман"}, {"diplomacy", "Дипломатия"},
            {"intimidation", "Запугивание"}, {"medicine", "Медицина"}, {"nature", "Природа"},
            {"occultism", "Оккультизм"}, {"performance", "Выступление"}, {"religion", "Религия"},
            {"society", "Общество"}, {"stealth", "Скрытность"}, {"survival", "Выживание"},
            {"thievery", "Воровство"}
    };
    private static final String[] RANKS = {"Нет", "Обучен", "Эксперт", "Мастер", "Легенда"};
    private static final int[] RANK_BONUS = {0, 2, 4, 6, 8};

    private RuleStore store;
    private CharacterState state;
    private FrameLayout content;
    private TextView summary;
    private String screen = "build";
    private final Random random = new Random();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new RuleStore(this);
        store.getReadableDatabase();
        state = CharacterState.load(this);
        setContentView(createShell());
        render();
    }

    private View createShell() {
        LinearLayout root = column();
        root.setBackgroundColor(BG);

        LinearLayout head = column();
        head.setPadding(dp(16), dp(12), dp(16), dp(10));
        head.setBackgroundColor(Color.rgb(13, 19, 26));
        TextView title = text("ГРАНЬ 2e", 21, true);
        title.setTextColor(ACCENT);
        head.addView(title);
        summary = text("", 13, false);
        summary.setTextColor(MUTED);
        head.addView(summary);
        root.addView(head, matchWrap());

        HorizontalScrollView navScroll = new HorizontalScrollView(this);
        navScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout nav = row();
        nav.setPadding(dp(6), dp(4), dp(6), dp(6));
        addNav(nav, "BUILD", "build");
        addNav(nav, "ЛИСТ", "sheet");
        addNav(nav, "БОЙ", "combat");
        addNav(nav, "АТАКА", "attack");
        addNav(nav, "ЗАЩИТА", "defense");
        addNav(nav, "НАВЫКИ", "skills");
        addNav(nav, "ЗАКЛИНАНИЯ", "spells");
        addNav(nav, "СНАРЯЖЕНИЕ", "equipment");
        addNav(nav, "СПРАВОЧНИК", "reference");
        navScroll.addView(nav);
        root.addView(navScroll, matchWrap());

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    private void addNav(LinearLayout nav, String label, String target) {
        TextView v = chip(label, false);
        v.setOnClickListener(x -> { screen = target; render(); });
        nav.addView(v, wrapWrap(dp(4)));
    }

    private void render() {
        updateSummary();
        switch (screen) {
            case "sheet": showSheet(); break;
            case "combat": showCombat(); break;
            case "attack": showAttack(); break;
            case "defense": showDefense(); break;
            case "skills": showSkills(); break;
            case "spells": showCollection(true); break;
            case "equipment": showCollection(false); break;
            case "reference": showReference(); break;
            default: showBuild();
        }
    }

    private void updateSummary() {
        String c = state.className.isEmpty() ? "класс не выбран" : state.className;
        summary.setText(state.name + "  •  ур. " + state.level + "  •  " + c + "  •  база: " + store.count());
    }

    private void showBuild() {
        LinearLayout col = column();
        col.setPadding(dp(10), dp(10), dp(10), dp(28));
        col.addView(sectionTitle("ПЛАНИРОВЩИК 1–20"));
        col.addView(note("Выборы показываются по уровню и проверяемым требованиям. Долгое нажатие по выбранной карточке очищает её."));

        LinearLayout basics = card();
        basics.addView(selector("Род", state.ancestry, "ancestry"));
        basics.addView(selector("Наследие", state.choiceName("base:heritage"), "heritage"));
        basics.addView(selector("Предыстория", state.background, "background"));
        basics.addView(selector("Класс", state.className, "class"));
        col.addView(basics, matchWrap(dp(8)));

        RuleItem cls = store.findExact("class", state.className);
        for (int level = 1; level <= 20; level++) {
            LinearLayout levelCard = card();
            TextView levelTitle = text("УРОВЕНЬ " + level, 17, true);
            levelTitle.setTextColor(level <= state.level ? ACCENT : MUTED);
            levelCard.addView(levelTitle);
            boolean any = false;

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
                TextView skills = actionRow("Повышение навыка", "Открыть навыки");
                skills.setOnClickListener(v -> { screen = "skills"; render(); });
                levelCard.addView(skills); any = true;
            }
            if (!any) levelCard.addView(note("Автоматические особенности класса / без отдельного выбора."));
            col.addView(levelCard, matchWrap(dp(7)));
        }
        setContent(scroll(col));
    }

    private View selector(String label, String current, String category) {
        TextView v = actionRow(label, current == null || current.isEmpty() ? "Выбрать" : current);
        v.setOnClickListener(x -> showPicker(category, "", 20, "", 20, null, item -> {
            if ("class".equals(category)) {
                state.className = item.name;
                RuleItem cls = item;
                int base = cls.meta.optInt("hp", 8);
                state.maxHp = Math.max(state.maxHp, base + 8);
                state.hp = Math.min(state.hp, state.maxHp);
            } else if ("ancestry".equals(category)) state.ancestry = item.name;
            else if ("background".equals(category)) state.background = item.name;
            else if ("heritage".equals(category)) state.setChoice("base:heritage", item);
            revalidate();
            save();
            render();
        }));
        return v;
    }

    private View choiceSlot(int level, String label, String slotCategory) {
        String key = "L" + level + ":" + slotCategory;
        String chosen = state.choiceName(key);
        TextView v = actionRow(label, chosen.isEmpty() ? "Выбрать" : chosen);
        v.setOnClickListener(x -> showPicker("feat", "", level, slotCategory, level, key, item -> {
            state.setChoice(key, item);
            revalidate();
            save();
            render();
        }));
        v.setOnLongClickListener(x -> {
            state.setChoice(key, null);
            revalidate();
            save();
            render();
            return true;
        });
        return v;
    }

    private void revalidate() {
        List<String> remove = new ArrayList<>();
        Iterator<String> keys = state.choices.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (!key.startsWith("L")) continue;
            String raw = state.choices.optString(key, "");
            String id = storedId(raw);
            RuleItem item = store.findById(id);
            int colon = key.indexOf(':');
            int level = 1;
            try { level = Integer.parseInt(key.substring(1, colon)); } catch (Exception ignored) { }
            String slot = colon >= 0 ? key.substring(colon + 1) : "";
            if (!RuleEngine.canChoose(item, state, slot, level)) remove.add(key);
        }
        for (String key : remove) state.choices.remove(key);
    }

    private void showSheet() {
        LinearLayout col = column(); col.setPadding(dp(12), dp(12), dp(12), dp(30));
        col.addView(sectionTitle("ЛИСТ ПЕРСОНАЖА"));
        LinearLayout identity = card();
        EditText name = input(state.name, "Имя персонажа");
        identity.addView(name);
        name.setOnFocusChangeListener((v, hasFocus) -> { if (!hasFocus) { state.name = name.getText().toString().trim(); save(); updateSummary(); } });
        identity.addView(statStepper("Уровень", () -> state.level, v -> state.level = clamp(v, 1, 20), 1, 20));
        identity.addView(actionRow("Род", value(state.ancestry)));
        identity.addView(actionRow("Предыстория", value(state.background)));
        identity.addView(actionRow("Класс", value(state.className)));
        col.addView(identity, matchWrap(dp(8)));

        LinearLayout stats = card();
        stats.addView(statStepper("КД", () -> state.ac, v -> state.ac = v, 0, 99));
        stats.addView(statStepper("Восприятие", () -> state.perception, v -> state.perception = v, -20, 99));
        stats.addView(statStepper("Стойкость", () -> state.fortitude, v -> state.fortitude = v, -20, 99));
        stats.addView(statStepper("Рефлекс", () -> state.reflex, v -> state.reflex = v, -20, 99));
        stats.addView(statStepper("Воля", () -> state.will, v -> state.will = v, -20, 99));
        col.addView(stats, matchWrap(dp(8)));

        LinearLayout io = row();
        Button export = button("Экспорт JSON"); export.setOnClickListener(v -> exportCharacter());
        Button importB = button("Импорт JSON"); importB.setOnClickListener(v -> importCharacter());
        io.addView(export, weighted()); io.addView(importB, weighted());
        col.addView(io, matchWrap(dp(8)));

        col.addView(sectionTitle("ВЫБОРЫ"));
        Iterator<String> it = state.choices.keys();
        int count = 0;
        while (it.hasNext()) {
            String key = it.next(); String chosen = state.choiceName(key);
            if (!chosen.isEmpty()) { col.addView(actionRow(key, chosen)); count++; }
        }
        if (count == 0) col.addView(note("Пока нет выбранных фитов и особенностей."));
        setContent(scroll(col));
    }

    private void exportCharacter() {
        String raw = state.toJson().toString();
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("Gran 2e character", raw));
        EditText box = input(raw, "JSON"); box.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this).setTitle("JSON скопирован").setView(box).setPositiveButton("Готово", null).show();
    }

    private void importCharacter() {
        EditText box = input("", "Вставьте JSON персонажа");
        new AlertDialog.Builder(this).setTitle("Импорт персонажа").setView(box)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Импорт", (d, w) -> {
                    try { state = CharacterJson.fromString(box.getText().toString()); revalidate(); save(); render(); }
                    catch (Exception e) { toast("Некорректный JSON"); }
                }).show();
    }

    private void showCombat() {
        LinearLayout col = column(); col.setPadding(dp(12), dp(12), dp(12), dp(30));
        col.addView(sectionTitle("ИГРОВОЙ РЕЖИМ"));
        LinearLayout hp = card();
        TextView hpText = text("ОЗ  " + state.hp + " / " + state.maxHp + (state.tempHp > 0 ? "  +" + state.tempHp + " врем." : ""), 28, true);
        hpText.setTextColor(state.hp > state.maxHp / 3 ? GOOD : BAD);
        hp.addView(hpText);
        LinearLayout controls = row();
        for (int d : new int[]{-10,-1,1,10}) {
            Button b = button((d > 0 ? "+" : "") + d);
            b.setOnClickListener(v -> { state.hp = clamp(state.hp + d, 0, state.maxHp); save(); render(); });
            controls.addView(b, weighted());
        }
        hp.addView(controls);
        hp.addView(statStepper("Макс. ОЗ", () -> state.maxHp, v -> { state.maxHp = Math.max(1, v); state.hp = Math.min(state.hp, state.maxHp); }, 1, 999));
        hp.addView(statStepper("Временные ОЗ", () -> state.tempHp, v -> state.tempHp = Math.max(0, v), 0, 999));
        col.addView(hp, matchWrap(dp(8)));

        col.addView(sectionTitle("СОСТОЯНИЯ"));
        LinearLayout conditions = card();
        List<RuleItem> all = store.query("condition", 99, "", 60);
        if (all.isEmpty()) conditions.addView(note("Состояния не найдены в базе."));
        for (RuleItem item : all) {
            int value = state.conditions.optInt(item.id, 0);
            TextView chip = chip(item.name + (value > 0 ? " " + value : ""), value > 0);
            chip.setOnClickListener(v -> {
                int next = state.conditions.optInt(item.id, 0) + 1;
                if (next > 4) next = 0;
                try { if (next == 0) state.conditions.remove(item.id); else state.conditions.put(item.id, next); } catch (Exception ignored) { }
                save(); render();
            });
            conditions.addView(chip, matchWrap(dp(3)));
        }
        col.addView(conditions, matchWrap(dp(8)));

        col.addView(sectionTitle("БЫСТРЫЕ БРОСКИ"));
        LinearLayout dice = card();
        LinearLayout drow = row();
        for (int sides : new int[]{4,6,8,10,12,20,100}) {
            Button b = button("d" + sides);
            b.setOnClickListener(v -> toast("d" + sides + " → " + (random.nextInt(sides) + 1)));
            drow.addView(b, weighted());
        }
        dice.addView(drow);
        col.addView(dice, matchWrap(dp(8)));
        setContent(scroll(col));
    }

    private void showAttack() {
        LinearLayout col = column(); col.setPadding(dp(12), dp(12), dp(12), dp(30));
        col.addView(sectionTitle("АТАКИ"));
        col.addView(note("Оружие берётся из снаряжения. Бонус можно вести вручную до подключения полного расчёта характеристик."));
        int weapons = 0;
        for (int i = 0; i < state.inventory.length(); i++) {
            RuleItem item = store.findById(storedId(state.inventory.optString(i, "")));
            if (item == null || !"weapon".equalsIgnoreCase(item.subtype)) continue;
            weapons++;
            LinearLayout c = card();
            c.addView(text(item.name, 18, true));
            c.addView(note(item.traitsLine()));
            EditText mod = input("0", "Модификатор"); mod.setInputType(2 | 4096);
            c.addView(mod);
            Button roll = button("Бросок атаки d20");
            roll.setOnClickListener(v -> {
                int m = parseInt(mod.getText().toString(), 0);
                int die = random.nextInt(20) + 1;
                toast(item.name + ": " + die + (m >= 0 ? "+" : "") + m + " = " + (die + m));
            });
            c.addView(roll);
            col.addView(c, matchWrap(dp(7)));
        }
        if (weapons == 0) {
            Button add = button("Добавить оружие"); add.setOnClickListener(v -> { screen = "equipment"; render(); });
            col.addView(add, matchWrap(dp(8)));
        }
        setContent(scroll(col));
    }

    private void showDefense() {
        LinearLayout col = column(); col.setPadding(dp(12), dp(12), dp(12), dp(30));
        col.addView(sectionTitle("ЗАЩИТА"));
        LinearLayout c = card();
        c.addView(bigStat("КД", state.ac));
        c.addView(bigStat("СТОЙКОСТЬ", state.fortitude));
        c.addView(bigStat("РЕФЛЕКС", state.reflex));
        c.addView(bigStat("ВОЛЯ", state.will));
        c.addView(bigStat("ВОСПРИЯТИЕ", state.perception));
        col.addView(c, matchWrap(dp(8)));
        Button edit = button("Изменить значения"); edit.setOnClickListener(v -> { screen = "sheet"; render(); });
        col.addView(edit, matchWrap(dp(8)));
        setContent(scroll(col));
    }

    private View bigStat(String label, int value) {
        LinearLayout r = row(); r.setGravity(Gravity.CENTER_VERTICAL);
        TextView l = text(label, 14, true); l.setTextColor(MUTED);
        TextView v = text((value >= 0 ? "+" : "") + value, 28, true); v.setTextColor(ACCENT); v.setGravity(Gravity.END);
        r.addView(l, weighted()); r.addView(v, weighted());
        return r;
    }

    private void showSkills() {
        LinearLayout col = column(); col.setPadding(dp(12), dp(12), dp(12), dp(30));
        col.addView(sectionTitle("НАВЫКИ"));
        col.addView(note("Нажатие по рангу циклически меняет: нет → обучен → эксперт → мастер → легенда. Показан бонус мастерства без модификатора характеристики."));
        for (String[] skill : SKILLS) {
            int rank = state.rank(skill[0]);
            int proficiency = rank == 0 ? 0 : state.level + RANK_BONUS[rank];
            LinearLayout c = card();
            LinearLayout r = row(); r.setGravity(Gravity.CENTER_VERTICAL);
            TextView name = text(skill[1], 16, true);
            TextView bonus = text((proficiency >= 0 ? "+" : "") + proficiency, 20, true); bonus.setTextColor(ACCENT); bonus.setGravity(Gravity.END);
            r.addView(name, weighted()); r.addView(bonus, weighted()); c.addView(r);
            TextView rankView = chip(RANKS[rank], rank > 0);
            rankView.setOnClickListener(v -> { state.setRank(skill[0], (state.rank(skill[0]) + 1) % 5); revalidate(); save(); render(); });
            c.addView(rankView, matchWrap(dp(3)));
            col.addView(c, matchWrap(dp(5)));
        }
        setContent(scroll(col));
    }

    private void showCollection(boolean spells) {
        String category = spells ? "spell" : "equipment";
        JSONArray selected = spells ? state.spells : state.inventory;
        LinearLayout outer = column(); outer.setPadding(dp(12), dp(12), dp(12), dp(28));
        outer.addView(sectionTitle(spells ? "ЗАКЛИНАНИЯ" : "СНАРЯЖЕНИЕ"));
        EditText search = input("", spells ? "Поиск заклинания" : "Поиск предмета / оружия / брони");
        outer.addView(search, matchWrap(dp(6)));
        LinearLayout list = column(); outer.addView(list, matchWrap());

        Runnable refresh = () -> {
            list.removeAllViews();
            if (selected.length() > 0) {
                list.addView(text("ВЫБРАНО", 13, true));
                for (int i = 0; i < selected.length(); i++) {
                    RuleItem item = store.findById(storedId(selected.optString(i, "")));
                    if (item == null) continue;
                    TextView row = actionRow("✓ " + item.name, item.subtype + (item.level > 0 ? " • ур. " + item.level : ""));
                    row.setOnClickListener(v -> { state.toggleArrayItem(selected, item); save(); render(); });
                    list.addView(row);
                }
            }
            list.addView(text("КАТАЛОГ", 13, true));
            List<RuleItem> items = store.query(category, spells ? 10 : 30, search.getText().toString(), 150);
            for (RuleItem item : items) {
                boolean has = state.hasArrayItem(selected, item.id);
                TextView row = actionRow((has ? "✓ " : "+ ") + item.name,
                        item.subtype + (item.level > 0 ? " • ур. " + item.level : ""));
                row.setOnClickListener(v -> showRuleDetail(item, () -> {
                    state.toggleArrayItem(selected, item); save(); render();
                }, has ? "Убрать" : "Добавить"));
                list.addView(row);
            }
        };
        search.addTextChangedListener(watcher(refresh));
        refresh.run();
        setContent(scroll(outer));
    }

    private void showReference() {
        LinearLayout outer = column(); outer.setPadding(dp(12), dp(12), dp(12), dp(28));
        outer.addView(sectionTitle("СПРАВОЧНИК"));
        outer.addView(note("Локальная база: " + store.count() + " записей. Данные доступны без сети после установки."));
        EditText search = input("", "Поиск по названию"); outer.addView(search, matchWrap(dp(6)));
        LinearLayout list = column(); outer.addView(list, matchWrap());
        Runnable refresh = () -> {
            list.removeAllViews();
            List<RuleItem> items = store.query("all", 99, search.getText().toString(), 250);
            for (RuleItem item : items) {
                TextView r = actionRow(item.name,
                        item.category + (item.subtype.isEmpty() ? "" : " / " + item.subtype) + (item.level > 0 ? " • ур. " + item.level : ""));
                r.setOnClickListener(v -> showRuleDetail(item, null, "Закрыть"));
                list.addView(r);
            }
        };
        search.addTextChangedListener(watcher(refresh)); refresh.run();
        setContent(scroll(outer));
    }

    private void showPicker(String category, String subtype, int maxLevel, String slotCategory, int slotLevel,
                            String choiceKey, Selection selection) {
        LinearLayout outer = column(); outer.setPadding(dp(12), dp(6), dp(12), dp(6));
        EditText search = input("", "Поиск"); outer.addView(search, matchWrap(dp(6)));
        TextView status = note(""); outer.addView(status);
        ScrollView scroll = new ScrollView(this); LinearLayout list = column(); scroll.addView(list);
        outer.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(480)));
        AlertDialog.Builder builder = new AlertDialog.Builder(this).setTitle("Выбор").setView(outer).setNegativeButton("Закрыть", null);
        if (choiceKey != null) builder.setNeutralButton("Очистить", (d, w) -> { state.setChoice(choiceKey, null); revalidate(); save(); render(); });
        AlertDialog dialog = builder.create();

        Runnable refresh = () -> {
            list.removeAllViews(); int shown = 0; int locked = 0;
            List<RuleItem> items = store.query(category, maxLevel, search.getText().toString(), 500);
            for (RuleItem item : items) {
                if (!subtype.isEmpty() && !subtype.equalsIgnoreCase(item.subtype)) continue;
                if (!slotCategory.isEmpty() && !RuleEngine.canChoose(item, state, slotCategory, slotLevel)) { locked++; continue; }
                shown++;
                TextView r = actionRow(item.name, (item.level > 0 ? "ур. " + item.level + " • " : "") + item.traitsLine());
                r.setOnClickListener(v -> showRuleDetail(item, () -> { selection.onSelect(item); dialog.dismiss(); }, "Выбрать"));
                list.addView(r);
                if (shown >= 150) break;
            }
            status.setText("Доступно: " + shown + (locked > 0 ? " • скрыто по требованиям: " + locked : ""));
        };
        search.addTextChangedListener(watcher(refresh));
        dialog.setOnShowListener(d -> refresh.run());
        dialog.show();
    }

    private void showRuleDetail(RuleItem item, Runnable positive, String positiveLabel) {
        LinearLayout body = column(); body.setPadding(dp(8), dp(4), dp(8), dp(4));
        if (item.level > 0) body.addView(note("Уровень " + item.level));
        if (!item.traits.isEmpty()) body.addView(note(item.traitsLine()));
        if (!item.prerequisites.isEmpty()) body.addView(note("Требования: " + String.join("; ", item.prerequisites)));
        if (!item.source.isEmpty()) body.addView(note("Источник: " + item.source + (item.license.isEmpty() ? "" : " • " + item.license)));
        TextView desc = text(item.description.isEmpty() ? "Описание отсутствует." : item.description, 15, false);
        desc.setTextColor(TEXT); desc.setPadding(0, dp(8), 0, dp(8)); body.addView(desc);
        AlertDialog.Builder b = new AlertDialog.Builder(this).setTitle(item.name).setView(scroll(body)).setNegativeButton("Назад", null);
        if (positive != null) b.setPositiveButton(positiveLabel, (d, w) -> positive.run());
        else b.setPositiveButton("Закрыть", null);
        b.show();
    }

    private View statStepper(String label, IntGetter getter, IntSetter setter, int min, int max) {
        LinearLayout r = row(); r.setGravity(Gravity.CENTER_VERTICAL); r.setPadding(0, dp(4), 0, dp(4));
        TextView l = text(label, 15, true); TextView value = text(String.valueOf(getter.get()), 17, true); value.setGravity(Gravity.CENTER); value.setTextColor(ACCENT);
        Button minus = button("−"); Button plus = button("+");
        minus.setOnClickListener(v -> { setter.set(clamp(getter.get() - 1, min, max)); save(); value.setText(String.valueOf(getter.get())); updateSummary(); });
        plus.setOnClickListener(v -> { setter.set(clamp(getter.get() + 1, min, max)); save(); value.setText(String.valueOf(getter.get())); updateSummary(); });
        r.addView(l, weighted()); r.addView(minus, fixed(dp(48))); r.addView(value, fixed(dp(62))); r.addView(plus, fixed(dp(48)));
        return r;
    }

    private TextWatcher watcher(Runnable r) {
        return new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) { }
            public void onTextChanged(CharSequence s, int st, int before, int count) { r.run(); }
            public void afterTextChanged(Editable e) { }
        };
    }

    private void save() { state.save(this); }
    private void setContent(View view) { content.removeAllViews(); content.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)); }
    private ScrollView scroll(View child) { ScrollView s = new ScrollView(this); s.setFillViewport(true); s.addView(child); return s; }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); return l; }

    private LinearLayout card() {
        LinearLayout l = column(); l.setPadding(dp(12), dp(10), dp(12), dp(10));
        l.setBackground(round(SURFACE, 12, Color.rgb(56, 69, 80), 1)); return l;
    }

    private TextView sectionTitle(String s) { TextView v = text(s, 14, true); v.setTextColor(ACCENT); v.setPadding(dp(4), dp(10), dp(4), dp(6)); return v; }
    private TextView note(String s) { TextView v = text(s, 13, false); v.setTextColor(MUTED); v.setPadding(dp(4), dp(4), dp(4), dp(6)); return v; }

    private TextView actionRow(String title, String value) {
        TextView v = text(title + "\n" + value, 15, false); v.setTextColor(TEXT); v.setPadding(dp(12), dp(10), dp(12), dp(10));
        v.setBackground(round(SURFACE_2, 9, Color.rgb(65, 79, 91), 1));
        v.setCompoundDrawablePadding(dp(6)); return v;
    }

    private TextView chip(String s, boolean active) {
        TextView v = text(s, 13, true); v.setGravity(Gravity.CENTER); v.setPadding(dp(12), dp(8), dp(12), dp(8));
        v.setTextColor(active ? Color.BLACK : TEXT); v.setBackground(round(active ? ACCENT : SURFACE_2, 18, active ? ACCENT : Color.rgb(66, 78, 89), 1)); return v;
    }

    private EditText input(String value, String hint) {
        EditText e = new EditText(this); e.setText(value); e.setHint(hint); e.setTextColor(TEXT); e.setHintTextColor(MUTED); e.setSingleLine(false);
        e.setPadding(dp(10), dp(8), dp(10), dp(8)); e.setBackground(round(SURFACE_2, 8, Color.rgb(73, 87, 99), 1)); return e;
    }

    private Button button(String s) { Button b = new Button(this); b.setText(s); b.setTextColor(TEXT); b.setTextSize(12); b.setAllCaps(false); return b; }
    private TextView text(String s, int sp, boolean bold) { TextView v = new TextView(this); v.setText(s); v.setTextSize(sp); v.setTextColor(TEXT); if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return v; }
    private GradientDrawable round(int color, int radius, int strokeColor, int stroke) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); if (stroke > 0) d.setStroke(dp(stroke), strokeColor); return d; }
    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); }
    private LinearLayout.LayoutParams matchWrap(int margin) { LinearLayout.LayoutParams p = matchWrap(); p.setMargins(0, margin, 0, margin); return p; }
    private LinearLayout.LayoutParams wrapWrap(int margin) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT); p.setMargins(margin, 0, margin, 0); return p; }
    private LinearLayout.LayoutParams weighted() { return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f); }
    private LinearLayout.LayoutParams fixed(int width) { return new LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT); }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
    private static int parseInt(String s, int fallback) { try { return Integer.parseInt(s.trim()); } catch (Exception e) { return fallback; } }
    private static String storedId(String raw) { int i = raw.indexOf('\u001f'); return i >= 0 ? raw.substring(0, i) : raw; }
    private static String value(String s) { return s == null || s.isEmpty() ? "—" : s; }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private interface Selection { void onSelect(RuleItem item); }
    private interface IntGetter { int get(); }
    private interface IntSetter { void set(int value); }
}
