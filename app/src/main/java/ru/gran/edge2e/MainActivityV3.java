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
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/** Pathbuilder-like builder shell backed by the executable PF2e rule graph. */
public final class MainActivityV3 extends Activity {
    private static final int BG = Color.rgb(18, 22, 27);
    private static final int HEADER = Color.rgb(27, 31, 37);
    private static final int CARD = Color.rgb(35, 40, 47);
    private static final int CARD_2 = Color.rgb(43, 49, 57);
    private static final int BORDER = Color.rgb(68, 76, 87);
    private static final int TEXT = Color.rgb(238, 240, 242);
    private static final int MUTED = Color.rgb(166, 174, 184);
    private static final int ACCENT = Color.rgb(198, 139, 65);
    private static final int GOOD = Color.rgb(76, 175, 122);
    private static final int BAD = Color.rgb(210, 82, 82);

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
    private RuleRuntime.Snapshot runtime;
    private FrameLayout content;
    private TextView subtitle;
    private String screen = "build";

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
        LinearLayout root = column(); root.setBackgroundColor(BG);
        LinearLayout top = column(); top.setPadding(dp(16), dp(12), dp(16), dp(8)); top.setBackgroundColor(HEADER);
        TextView title = text("ГРАНЬ 2e", 23, true); title.setTextColor(ACCENT); top.addView(title);
        subtitle = text("", 12, false); subtitle.setTextColor(MUTED); top.addView(subtitle);
        root.addView(top, matchWrap());

        HorizontalScrollView hsv = new HorizontalScrollView(this); hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout nav = row(); nav.setPadding(dp(6), dp(4), dp(6), dp(5));
        nav(nav, "BUILD", "build"); nav(nav, "НАВЫКИ", "skills"); nav(nav, "ПРАВИЛА", "rules"); nav(nav, "ЛИСТ / БОЙ", "play");
        hsv.addView(nav); root.addView(hsv, matchWrap());

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        return root;
    }

    private void nav(LinearLayout parent, String label, String target) {
        TextView v = chip(label, screen.equals(target));
        v.setOnClickListener(x -> {
            if ("play".equals(target)) {
                startActivity(new Intent(this, MainActivityV2.class));
                return;
            }
            screen = target; render();
        });
        parent.addView(v, wrapWrap(dp(4)));
    }

    private void render() {
        rebuildRuntime();
        String cls = state.className.isEmpty() ? "класс не выбран" : RuNames.shortName(state.className);
        subtitle.setText("ур. " + state.level + " • " + cls + " • база " + store.count() + " • активных правил " + runtime.allItems().size());
        content.removeAllViews();
        if ("skills".equals(screen)) content.addView(scroll(skillsPage()));
        else if ("rules".equals(screen)) content.addView(scroll(rulesPage()));
        else content.addView(scroll(buildPage()));
    }

    private LinearLayout buildPage() {
        LinearLayout col = page();
        col.addView(section("ПЕРСОНАЖ"));
        LinearLayout identity = card();
        identity.addView(selector("Род", state.ancestry, "ancestry", "base:ancestry"));
        identity.addView(selector("Наследие", state.choiceName("base:heritage"), "heritage", "base:heritage"));
        identity.addView(selector("Предыстория", state.background, "background", "base:background"));
        identity.addView(selector("Класс", state.className, "class", "base:class"));
        identity.addView(levelRow());
        col.addView(identity, matchWrap(dp(7)));

        List<RuleRuntime.ChoicePrompt> prompts = runtime.choices();
        if (!prompts.isEmpty()) {
            col.addView(section("ОБЯЗАТЕЛЬНЫЕ ВЫБОРЫ"));
            LinearLayout choices = card();
            for (RuleRuntime.ChoicePrompt prompt : prompts) choices.addView(ruleChoiceRow(prompt));
            col.addView(choices, matchWrap(dp(7)));
        }

        col.addView(section("ПРОГРЕССИЯ 1–20"));
        RuleItem cls = classItem();
        for (int level = 1; level <= 20; level++) {
            LinearLayout lc = card();
            TextView h = text("УРОВЕНЬ " + level, 17, true); h.setTextColor(level <= state.level ? ACCENT : MUTED); lc.addView(h);
            int featureCount = 0;
            for (RuleItem item : runtime.allItems()) {
                if (!runtime.isAutomatic(item.id) || runtime.automaticLevel(item.id) != level) continue;
                lc.addView(infoRow("Автоматически", RuNames.shortName(item.name), GOOD));
                featureCount++;
            }
            boolean slot = false;
            if (RuleEngine.classHasSlot(cls, "classFeatLevels", level, new int[]{1,2,4,6,8,10,12,14,16,18,20})) {
                lc.addView(featSlot(level, "Классовый / архетипный фит", "class")); slot = true;
            }
            if (RuleEngine.classHasSlot(cls, "ancestryFeatLevels", level, new int[]{1,5,9,13,17})) {
                lc.addView(featSlot(level, "Фит рода", "ancestry")); slot = true;
            }
            if (RuleEngine.classHasSlot(cls, "skillFeatLevels", level, new int[]{2,4,6,8,10,12,14,16,18,20})) {
                lc.addView(featSlot(level, "Фит навыка", "skill")); slot = true;
            }
            if (RuleEngine.classHasSlot(cls, "generalFeatLevels", level, new int[]{3,7,11,15,19})) {
                lc.addView(featSlot(level, "Общий фит", "general")); slot = true;
            }
            if (RuleEngine.classHasSlot(cls, "skillIncreaseLevels", level, new int[]{3,5,7,9,11,13,15,17,19})) {
                TextView r = actionRow("Повышение навыка", "Открыть навыки");
                r.setOnClickListener(v -> { screen = "skills"; render(); }); lc.addView(r); slot = true;
            }
            if (featureCount == 0 && !slot) lc.addView(note("Нет отдельного выбора на этом уровне."));
            col.addView(lc, matchWrap(dp(6)));
        }
        return col;
    }

    private View selector(String label, String current, String category, String key) {
        TextView row = actionRow(label, current == null || current.isEmpty() ? "Выбрать" : RuNames.shortName(current));
        row.setOnClickListener(v -> showBasePicker(category, item -> {
            if ("class".equals(category)) {
                state.className = item.name;
                state.clearRuleSelectionsFor(item.id);
            } else if ("ancestry".equals(category)) {
                state.ancestry = item.name;
                state.setChoice("base:heritage", null);
            } else if ("background".equals(category)) state.background = item.name;
            else state.setChoice(key, item);
            saveAndRevalidate();
        }));
        return row;
    }

    private View levelRow() {
        LinearLayout r = row(); r.setGravity(Gravity.CENTER_VERTICAL); r.setPadding(dp(10), dp(8), dp(10), dp(8));
        TextView label = text("Уровень", 14, true); r.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button minus = mini("−"), plus = mini("+"); TextView value = text(String.valueOf(state.level), 17, true); value.setGravity(Gravity.CENTER); value.setMinWidth(dp(42));
        minus.setOnClickListener(v -> { if (state.level > 1) { state.level--; saveAndRevalidate(); } });
        plus.setOnClickListener(v -> { if (state.level < 20) { state.level++; saveAndRevalidate(); } });
        r.addView(minus); r.addView(value); r.addView(plus); return r;
    }

    private View ruleChoiceRow(RuleRuntime.ChoicePrompt prompt) {
        String selected = state.ruleSelection(prompt.sourceId, prompt.flag);
        TextView row = actionRow(cleanPrompt(prompt.title), selected.isEmpty() ? (prompt.dynamic ? "Нужен обработчик" : "Выбрать") : selected);
        if (prompt.options.isEmpty()) {
            row.setTextColor(MUTED);
            row.setOnClickListener(v -> Toast.makeText(this, "Динамический ChoiceSet пока не может быть вычислен автоматически", Toast.LENGTH_LONG).show());
        } else {
            row.setOnClickListener(v -> {
                String[] labels = new String[prompt.options.size()];
                for (int i = 0; i < labels.length; i++) labels[i] = prompt.options.get(i).label + "  [" + prompt.options.get(i).value + "]";
                new AlertDialog.Builder(this).setTitle(cleanPrompt(prompt.title)).setItems(labels, (d, which) -> {
                    state.setRuleSelection(prompt.sourceId, prompt.flag, prompt.options.get(which).value);
                    saveAndRevalidate();
                }).setNegativeButton("Отмена", null).show();
            });
        }
        return row;
    }

    private View featSlot(int level, String label, String slotCategory) {
        String key = "L" + level + ":" + slotCategory;
        String chosen = state.choiceName(key);
        TextView row = actionRow(label, chosen.isEmpty() ? "Выбрать" : RuNames.shortName(chosen));
        row.setOnClickListener(v -> showFeatPicker(slotCategory, level, key));
        row.setOnLongClickListener(v -> { state.setChoice(key, null); saveAndRevalidate(); return true; });
        return row;
    }

    private LinearLayout skillsPage() {
        LinearLayout col = page();
        col.addView(section("НАВЫКИ"));
        col.addView(note("Ранг = максимум из базового выбора, автоматических Rule Elements и ручных повышений. Ограничение по уровню: Эксперт 3+, Мастер 7+, Легенда 15+."));
        LinearLayout c = card();
        for (String[] skill : SKILLS) {
            String key = skill[0];
            LinearLayout r = row(); r.setGravity(Gravity.CENTER_VERTICAL); r.setPadding(dp(8), dp(6), dp(8), dp(6));
            TextView name = text(skill[1], 14, false); r.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            int effective = runtime.rank(state, key);
            TextView rank = text(RANKS[Math.max(0, Math.min(4, effective))], 13, true); rank.setTextColor(effective > state.rank(key) ? GOOD : TEXT); rank.setMinWidth(dp(82)); rank.setGravity(Gravity.CENTER);
            Button minus = mini("−"), plus = mini("+");
            minus.setOnClickListener(v -> { state.setRank(key, Math.max(0, state.rank(key) - 1)); state.save(this); render(); });
            plus.setOnClickListener(v -> { state.setRank(key, Math.min(state.maxSkillRankForLevel(), state.rank(key) + 1)); state.save(this); render(); });
            r.addView(minus); r.addView(rank); r.addView(plus); c.addView(r);
        }
        col.addView(c, matchWrap(dp(6))); return col;
    }

    private LinearLayout rulesPage() {
        LinearLayout col = page(); col.addView(section("ИСПОЛНЯЕМЫЕ ПРАВИЛА"));
        col.addView(note("Этот экран показывает не весь справочник, а то, что сейчас реально вошло в граф персонажа: базовые элементы, выбранные фиты, автоматические особенности и GrantItem."));
        LinearLayout summary = card();
        summary.addView(staticRow("Элементов графа", String.valueOf(runtime.allItems().size())));
        summary.addView(staticRow("Незакрытых ChoiceSet", String.valueOf(runtime.choices().size())));
        summary.addView(staticRow("Roll options", String.valueOf(runtime.rollOptions().size())));
        summary.addView(staticRow("Предупреждений runtime", String.valueOf(runtime.warnings.size())));
        col.addView(summary, matchWrap(dp(6)));

        List<RuleItem> items = runtime.allItems();
        Collections.sort(items, Comparator.comparingInt((RuleItem x) -> runtime.automaticLevel(x.id)).thenComparing(x -> x.name));
        LinearLayout graph = card();
        for (RuleItem item : items) {
            String kind = runtime.isAutomatic(item.id) ? "AUTO ур. " + runtime.automaticLevel(item.id) : item.category;
            TextView r = actionRow(RuNames.shortName(item.name), kind);
            r.setOnClickListener(v -> ruleDetail(item, null)); graph.addView(r);
        }
        col.addView(graph, matchWrap(dp(6)));
        if (!runtime.warnings.isEmpty()) {
            col.addView(section("НЕПОДДЕРЖАННЫЕ / НЕРАЗРЕШЁННЫЕ ЭФФЕКТЫ"));
            LinearLayout warn = card(); for (String w : runtime.warnings) warn.addView(note("• " + w)); col.addView(warn, matchWrap(dp(6)));
        }
        return col;
    }

    private void showBasePicker(String category, Selection selection) {
        final EditText search = input("Поиск");
        LinearLayout outer = column(); outer.setPadding(dp(10), dp(4), dp(10), dp(4)); outer.addView(search);
        ScrollView sv = new ScrollView(this); LinearLayout list = column(); sv.addView(list); outer.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(540)));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Выбор").setView(outer).setNegativeButton("Закрыть", null).create();
        Runnable refresh = () -> {
            list.removeAllViews(); String q = search.getText().toString(); int shown = 0;
            for (RuleItem item : store.query(category, 20, q, 350)) {
                TextView r = actionRow(RuNames.display(item.name), item.source);
                r.setOnClickListener(v -> { selection.select(item); dialog.dismiss(); }); list.addView(r); if (++shown >= 220) break;
            }
        };
        search.addTextChangedListener(watcher(refresh)); refresh.run(); dialog.show();
    }

    private void showFeatPicker(String slotCategory, int level, String choiceKey) {
        final EditText search = input("Поиск фита");
        LinearLayout outer = column(); outer.setPadding(dp(10), dp(4), dp(10), dp(4)); outer.addView(search);
        TextView status = note(""); outer.addView(status);
        ScrollView sv = new ScrollView(this); LinearLayout list = column(); sv.addView(list); outer.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(560)));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Выбор фита — уровень " + level).setView(outer)
                .setNeutralButton("Очистить", (d,w) -> { state.setChoice(choiceKey, null); saveAndRevalidate(); })
                .setNegativeButton("Закрыть", null).create();
        Runnable refresh = () -> {
            list.removeAllViews(); String q = search.getText().toString();
            List<RuleItem> candidates = featCandidates(slotCategory, level, q);
            List<RowCandidate> rows = new ArrayList<>();
            int open = 0, locked = 0;
            for (RuleItem item : candidates) {
                String reason = RuleEngine.blockReason(item, state, runtime, slotCategory, level);
                if (reason == null) open++; else locked++;
                rows.add(new RowCandidate(item, reason));
            }
            Collections.sort(rows, (a,b) -> {
                if ((a.reason == null) != (b.reason == null)) return a.reason == null ? -1 : 1;
                int lv = Integer.compare(a.item.level, b.item.level); return lv != 0 ? lv : a.item.name.compareToIgnoreCase(b.item.name);
            });
            int shown = 0;
            for (RowCandidate rc : rows) {
                String right = "ур. " + rc.item.level + (rc.reason == null ? " • ✓" : " • 🔒 " + rc.reason);
                TextView r = actionRow(RuNames.display(rc.item.name), right); r.setTextColor(rc.reason == null ? TEXT : MUTED);
                r.setOnClickListener(v -> ruleDetail(rc.item, rc.reason == null ? () -> {
                    state.setChoice(choiceKey, rc.item); dialog.dismiss(); saveAndRevalidate();
                } : null));
                list.addView(r); if (++shown >= 260) break;
            }
            status.setText("Доступно: " + open + " • заблокировано: " + locked + " • показано: " + Math.min(shown, 260));
        };
        search.addTextChangedListener(watcher(refresh)); refresh.run(); dialog.show();
    }

    private List<RuleItem> featCandidates(String slot, int level, String search) {
        List<RuleItem> out = new ArrayList<>(); Set<String> seen = new HashSet<>();
        if ("class".equals(slot)) {
            String group = RuleRuntime.slug(state.className);
            addAll(out, seen, store.queryGroup("feat", "class", group, level, search, 400));
            addAll(out, seen, store.queryGroup("feat", "archetype", "", level, search, 900));
        } else if ("ancestry".equals(slot)) {
            addAll(out, seen, store.bySubtype("feat", "ancestry", level, search, 900));
        } else if ("skill".equals(slot)) {
            addAll(out, seen, store.bySubtype("feat", "skill", level, search, 700));
        } else if ("general".equals(slot)) {
            addAll(out, seen, store.bySubtype("feat", "general", level, search, 400));
            addAll(out, seen, store.bySubtype("feat", "skill", level, search, 600));
        }
        return out;
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
        JSONArray elements = item.meta.optJSONArray("ruleElements");
        if (elements != null && elements.length() > 0) body.append("Rule Elements: ").append(elements.length()).append("\n");
        body.append("\n").append(item.description == null ? "" : item.description);
        AlertDialog.Builder b = new AlertDialog.Builder(this).setTitle(RuNames.display(item.name)).setMessage(body.toString()).setNegativeButton("Закрыть", null);
        if (choose != null) b.setPositiveButton("Выбрать", (d,w) -> choose.run()); b.show();
    }

    private void saveAndRevalidate() {
        state.save(this);
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
        state.save(this); rebuildRuntime(); render();
    }

    private void rebuildRuntime() { runtime = RuleRuntime.resolve(store, state, stats); }
    private RuleItem classItem() { return state.className.isEmpty() ? null : store.findExact("class", state.className); }

    private static String cleanPrompt(String raw) {
        if (raw == null || raw.isEmpty()) return "Дополнительный выбор";
        if (raw.startsWith("PF2E.")) return "Дополнительный выбор правила";
        return raw;
    }

    private interface Selection { void select(RuleItem item); }
    private static final class RowCandidate { final RuleItem item; final String reason; RowCandidate(RuleItem i, String r) { item=i; reason=r; } }

    private TextWatcher watcher(Runnable r) { return new TextWatcher() { public void beforeTextChanged(CharSequence s,int st,int c,int a){} public void onTextChanged(CharSequence s,int st,int b,int c){r.run();} public void afterTextChanged(Editable e){} }; }
    private ScrollView scroll(View child) { ScrollView s = new ScrollView(this); s.addView(child); return s; }
    private LinearLayout page() { LinearLayout c=column(); c.setPadding(dp(10),dp(8),dp(10),dp(24)); return c; }
    private LinearLayout column() { LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row() { LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); return l; }
    private LinearLayout card() { LinearLayout l=column(); l.setPadding(dp(8),dp(6),dp(8),dp(6)); l.setBackground(round(CARD,10,BORDER)); return l; }
    private TextView section(String s) { TextView v=text(s,14,true); v.setTextColor(ACCENT); v.setPadding(dp(5),dp(12),dp(5),dp(5)); return v; }
    private TextView note(String s) { TextView v=text(s,12,false); v.setTextColor(MUTED); v.setPadding(dp(6),dp(5),dp(6),dp(6)); return v; }
    private TextView staticRow(String a,String b) { return actionRow(a,b); }
    private TextView infoRow(String a,String b,int color) { TextView v=actionRow(a,b); v.setTextColor(color); return v; }
    private TextView actionRow(String left,String right) { TextView v=text(left + "\n" + right,14,false); v.setPadding(dp(10),dp(9),dp(10),dp(9)); v.setBackground(round(CARD_2,8,BORDER)); LinearLayout.LayoutParams p=matchWrap(dp(3)); v.setLayoutParams(p); return v; }
    private TextView chip(String s,boolean active) { TextView v=text(s,12,true); v.setTextColor(active?Color.BLACK:TEXT); v.setPadding(dp(12),dp(8),dp(12),dp(8)); v.setBackground(round(active?ACCENT:CARD_2,18,BORDER)); return v; }
    private TextView text(String s,int size,boolean bold) { TextView v=new TextView(this); v.setText(s); v.setTextSize(size); v.setTextColor(TEXT); if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD); return v; }
    private EditText input(String hint) { EditText e=new EditText(this); e.setHint(hint); e.setHintTextColor(MUTED); e.setTextColor(TEXT); e.setSingleLine(true); e.setBackground(round(CARD_2,8,BORDER)); e.setPadding(dp(10),dp(8),dp(10),dp(8)); return e; }
    private Button mini(String s) { Button b=new Button(this); b.setText(s); b.setTextSize(18); b.setMinWidth(dp(44)); b.setMinimumHeight(0); b.setMinHeight(dp(40)); return b; }
    private GradientDrawable round(int color,int radius,int stroke) { GradientDrawable g=new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(radius)); g.setStroke(dp(1),stroke); return g; }
    private int dp(int v) { return Math.round(v*getResources().getDisplayMetrics().density); }
    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT); }
    private LinearLayout.LayoutParams matchWrap(int margin) { LinearLayout.LayoutParams p=matchWrap(); p.setMargins(0,margin,0,margin); return p; }
    private LinearLayout.LayoutParams wrapWrap(int margin) { LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT); p.setMargins(margin,0,margin,0); return p; }
}
