#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
V3 = ROOT / 'app/src/main/java/ru/gran/edge2e/MainActivityV3.java'


def replace_once(text, pattern, replacement, label):
    out, n = re.subn(pattern, replacement, text, count=1, flags=re.S)
    if n != 1:
        raise SystemExit(f'4.1 BUILD wizard patch missing anchor: {label}')
    return out


def main():
    s = V3.read_text(encoding='utf-8')

    build_page = r'''    private LinearLayout buildPage() {
        LinearLayout col = page();
        col.addView(heroCard(), matchWrap(dp(6)));

        col.addView(section("СОЗДАНИЕ ПЕРСОНАЖА"));
        LinearLayout wizard = card();
        wizard.addView(note("Проходи шаги сверху вниз. Каждый следующий выбор использует уже выбранные род, наследие, предысторию и класс."));
        wizard.addView(creationStep("1", "Род", state.ancestry, "ancestry", "base:ancestry"));
        wizard.addView(creationStep("2", "Наследие", state.choiceName("base:heritage"), "heritage", "base:heritage"));
        wizard.addView(creationStep("3", "Предыстория", state.background, "background", "base:background"));
        wizard.addView(creationStep("4", "Класс", state.className, "class", "base:class"));
        String abilityState = runtime.choices().isEmpty() ? "Базовые выборы завершены" : "Осталось выборов: " + runtime.choices().size();
        TextView abilitiesStep = compactRow(runtime.choices().isEmpty() ? "✓" : "5", "Характеристики и особенности", abilityState, runtime.choices().isEmpty() ? GOOD : WARM);
        abilitiesStep.setOnClickListener(v -> { if (!runtime.choices().isEmpty()) showMandatoryChoices(); });
        wizard.addView(abilitiesStep);
        TextView skillsStep = compactRow("6", "Навыки", "Настроить владения и повышения", ACCENT);
        skillsStep.setOnClickListener(v -> { screen = "skills"; render(); });
        wizard.addView(skillsStep);
        col.addView(wizard, matchWrap(dp(5)));

        List<RuleRuntime.ChoicePrompt> prompts = runtime.choices();
        if (!prompts.isEmpty()) {
            col.addView(section("ОБЯЗАТЕЛЬНЫЕ ВЫБОРЫ"));
            LinearLayout choices = card();
            choices.addView(note("Эти решения пришли из выбранных правил. Они не подменяются случайными значениями."));
            for (RuleRuntime.ChoicePrompt prompt : prompts) choices.addView(ruleChoiceRow(prompt));
            col.addView(choices, matchWrap(dp(4)));
        }

        int[] completion = completion();
        LinearLayout ready = card();
        ready.addView(text("ГОТОВНОСТЬ СБОРКИ", 14, true));
        ready.addView(note("Заполнено " + completion[0] + " из " + completion[1] + " основных решений до текущего уровня."));
        Button play = new Button(this); play.setText("ОТКРЫТЬ В ИГРЕ"); play.setTextSize(13); play.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        play.setOnClickListener(v -> startActivity(new Intent(this, MainActivityV2.class)));
        ready.addView(play, matchWrap(dp(3)));
        col.addView(ready, matchWrap(dp(5)));

        TextView progression = section("ПРОГРЕССИЯ ПО УРОВНЯМ");
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

    private View creationStep(String number, String label, String current, String category, String key) {
        boolean done = current != null && !current.isEmpty();
        String value = done ? RuNames.shortName(current) : "Выбрать";
        TextView row = compactRow(done ? "✓" : number, label, value, done ? GOOD : WARM);
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
            } else {
                state.setChoice(key, item);
            }
            saveAndRevalidate();
        }));
        return row;
    }

    private void showMandatoryChoices() {
        List<RuleRuntime.ChoicePrompt> prompts = runtime.choices();
        if (prompts.isEmpty()) return;
        LinearLayout body = column(); body.setPadding(dp(8), dp(4), dp(8), dp(4));
        body.addView(note("Обязательные выборы из рода, предыстории, класса и особенностей."));
        for (RuleRuntime.ChoicePrompt prompt : prompts) body.addView(ruleChoiceRow(prompt));
        new AlertDialog.Builder(this).setTitle("Характеристики и особенности").setView(scroll(body)).setNegativeButton("Закрыть", null).show();
    }

'''
    s = replace_once(s, r'    private LinearLayout buildPage\(\) \{.*?\n    private View heroCard\(\)', build_page + '    private View heroCard()', 'buildPage')

    hero = r'''    private View heroCard() {
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

        String ancestry = state.ancestry.isEmpty() ? "род не выбран" : RuNames.shortName(state.ancestry);
        String background = state.background.isEmpty() ? "предыстория не выбрана" : RuNames.shortName(state.background);
        String cls = state.className.isEmpty() ? "класс не выбран" : RuNames.shortName(state.className);
        outer.addView(note(ancestry + " • " + background + " • " + cls));
        outer.addView(levelRow());

        LinearLayout abilities = row();
        abilities.setGravity(Gravity.CENTER);
        abilities.setPadding(0, dp(7), 0, 0);
        for (String[] a : ABILITIES) abilities.addView(abilityBox(a[1], stats.abilityScore(a[0]), stats.ability(a[0])), new LinearLayout.LayoutParams(0, dp(64), 1));
        outer.addView(abilities);
        return outer;
    }

'''
    s = replace_once(s, r'    private View heroCard\(\) \{.*?\n    private View abilityBox\(', hero + '    private View abilityBox(', 'heroCard')

    picker = r'''    private void showBasePicker(String category, Selection selection) {
        final EditText search = input("Поиск по-русски или по-английски");
        LinearLayout outer = column(); outer.setPadding(dp(10), dp(4), dp(10), dp(4));
        if ("heritage".equals(category) && !state.ancestry.isEmpty()) outer.addView(note("Показываются наследия рода «" + RuNames.shortName(state.ancestry) + "» и универсальные наследия."));
        outer.addView(search);
        ScrollView sv = new ScrollView(this); LinearLayout list = column(); sv.addView(list); outer.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(540)));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(basePickerTitle(category)).setView(outer).setNegativeButton("Закрыть", null).create();
        Runnable refresh = () -> {
            list.removeAllViews(); String q = search.getText().toString(); int shown = 0;
            for (RuleItem item : baseCandidates(category)) {
                if (!matches(item, q)) continue;
                String right = item.source;
                if ("heritage".equals(category) && item.meta.optBoolean("versatile", false)) right = "универсальное" + (right.isEmpty() ? "" : " • " + right);
                TextView r = compactRow("+", RuNames.shortName(item.name), right, WARM);
                r.setOnClickListener(v -> { selection.select(item); dialog.dismiss(); });
                r.setOnLongClickListener(v -> { ruleDetail(item, null); return true; });
                list.addView(r); if (++shown >= 240) break;
            }
            if (shown == 0) list.addView(note("Ничего не найдено для текущей сборки."));
        };
        search.addTextChangedListener(watcher(refresh)); refresh.run(); dialog.show();
    }

    private String basePickerTitle(String category) {
        if ("ancestry".equals(category)) return "Выбор рода";
        if ("heritage".equals(category)) return "Выбор наследия";
        if ("background".equals(category)) return "Выбор предыстории";
        if ("class".equals(category)) return "Выбор класса";
        return "Выбор";
    }

    private List<RuleItem> baseCandidates(String category) {
        List<RuleItem> raw = store.query(category, 20, "", 900);
        if (!"heritage".equals(category) || state.ancestry.isEmpty()) return raw;
        List<RuleItem> out = new ArrayList<>();
        for (RuleItem item : raw) {
            String ancestry = item.meta.optString("ancestry", "");
            boolean versatile = item.meta.optBoolean("versatile", false);
            if (versatile || ancestry.equalsIgnoreCase(state.ancestry)) out.add(item);
        }
        return out;
    }

'''
    s = replace_once(s, r'    private void showBasePicker\(String category, Selection selection\) \{.*?\n    private void showFeatPicker\(', picker + '    private void showFeatPicker(', 'showBasePicker')

    V3.write_text(s, encoding='utf-8')
    print('Applied Gran 4.1 staged BUILD wizard, compact hero summary and ancestry-aware heritage picker')


if __name__ == '__main__':
    main()
