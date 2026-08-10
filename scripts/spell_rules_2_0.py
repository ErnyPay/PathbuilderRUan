#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RUNTIME = ROOT / 'app/src/main/java/ru/gran/edge2e/RuleRuntime.java'
V2 = ROOT / 'app/src/main/java/ru/gran/edge2e/MainActivityV2.java'


def replace_once(text, old, new, label):
    if old not in text:
        raise SystemExit(f'spell rules 2.0 patch missing anchor: {label}')
    return text.replace(old, new, 1)


r = RUNTIME.read_text(encoding='utf-8')

r = replace_once(r,
'''        private final LinkedHashMap<String, Integer> baseSpeeds = new LinkedHashMap<>();
        private final List<MartialRule> martialRules = new ArrayList<>();
        public final List<String> warnings = new ArrayList<>();''',
'''        private final LinkedHashMap<String, Integer> baseSpeeds = new LinkedHashMap<>();
        private final List<MartialRule> martialRules = new ArrayList<>();
        private final LinkedHashSet<String> spellTraditions = new LinkedHashSet<>();
        public final List<String> warnings = new ArrayList<>();''', 'spell tradition field')

r = replace_once(r,
'''        public boolean hasName(String value) {
            String wanted = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);''',
'''        public Set<String> spellTraditions() { return new LinkedHashSet<>(spellTraditions); }

        public boolean hasSpellcasting() {
            return proficiency("spellcasting", 0) > 0 || rollOptions.contains("self:spellcasting");
        }

        public boolean hasName(String value) {
            String wanted = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);''', 'spell accessors')

r = replace_once(r,
'''            if (trained != null) applyTrainedSkills(out, trained.optJSONArray("value"));
            applyClassBaseProficiencies(out, cls);
            addFeatureList(store, out, cls.meta.optJSONArray("features"), state.level);''',
'''            if (trained != null) applyTrainedSkills(out, trained.optJSONArray("value"));
            applyClassBaseProficiencies(out, cls);
            applyClassTraditions(out, cls);
            addFeatureList(store, out, cls.meta.optJSONArray("features"), state.level);''', 'class traditions')

r = replace_once(r,
'''    private static void applyClassBaseProficiencies(Snapshot out, RuleItem cls) {''',
'''    private static void applyClassTraditions(Snapshot out, RuleItem cls) {
        JSONArray traditions = cls.meta.optJSONArray("traditions");
        if (traditions == null) return;
        for (int i = 0; i < traditions.length(); i++) captureTradition(out, traditions.optString(i, ""));
    }

    private static void applyClassBaseProficiencies(Snapshot out, RuleItem cls) {''', 'class tradition helper')

r = replace_once(r,
'''                case "RollOption":
                    String option = resolveInjected(rule.optString("option", rule.optString("value", "")), item, state);
                    if (!option.isEmpty()) out.rollOptions.add(option);
                    break;''',
'''                case "RollOption":
                    String option = resolveInjected(rule.optString("option", rule.optString("value", "")), item, state);
                    if (!option.isEmpty()) {
                        out.rollOptions.add(option);
                        captureTraditionFromOption(out, option);
                    }
                    break;''', 'roll option tradition capture')

r = replace_once(r,
'''        Object choicesRaw = rule.opt("choices");
        List<Option> options = new ArrayList<>();
        boolean dynamic = !(choicesRaw instanceof JSONArray);
        if (choicesRaw instanceof JSONArray) {
            JSONArray choices = (JSONArray) choicesRaw;
            for (int i = 0; i < choices.length(); i++) {
                Object raw = choices.opt(i);
                if (raw instanceof JSONObject) {
                    JSONObject choice = (JSONObject) raw;
                    if (!predicateMatches(choice.opt("predicate"), item, state, out)) continue;
                    Object value = choice.opt("value");
                    if (value == null || JSONObject.NULL.equals(value)) continue;
                    String label = choice.optString("label", String.valueOf(value));
                    options.add(new Option(label, String.valueOf(value)));
                } else if (raw != null && !JSONObject.NULL.equals(raw)) options.add(new Option(String.valueOf(raw), String.valueOf(raw)));
            }
        }''',
'''        Object choicesRaw = rule.opt("choices");
        List<Option> options = new ArrayList<>();
        boolean dynamic = true;
        if (choicesRaw instanceof JSONArray) {
            dynamic = false;
            JSONArray choices = (JSONArray) choicesRaw;
            for (int i = 0; i < choices.length(); i++) {
                Object raw = choices.opt(i);
                if (raw instanceof JSONObject) {
                    JSONObject choice = (JSONObject) raw;
                    if (!predicateMatches(choice.opt("predicate"), item, state, out)) continue;
                    Object value = choice.opt("value");
                    if (value == null || JSONObject.NULL.equals(value)) continue;
                    String label = choice.optString("label", String.valueOf(value));
                    options.add(new Option(label, String.valueOf(value)));
                } else if (raw != null && !JSONObject.NULL.equals(raw)) options.add(new Option(String.valueOf(raw), String.valueOf(raw)));
            }
        } else if (choicesRaw instanceof JSONObject) {
            String config = ((JSONObject) choicesRaw).optString("config", "");
            if ("skills".equals(config)) {
                for (String skill : SKILLS) options.add(new Option(skill, skill));
                dynamic = false;
            } else if ("magicTraditions".equals(config)) {
                options.add(new Option("Арканная", "arcane"));
                options.add(new Option("Сакральная", "divine"));
                options.add(new Option("Оккультная", "occult"));
                options.add(new Option("Природная", "primal"));
                dynamic = false;
            }
        }''', 'standard dynamic ChoiceSet configs')

r = replace_once(r,
'''    private static void applyActiveEffectLike(CharacterState state, Snapshot out, RuleItem item, JSONObject rule) {
        String path = resolveInjected(rule.optString("path", ""), item, state);
        int value = intValue(resolveInjected(String.valueOf(rule.opt("value")), item, state), 0);
        String mode = rule.optString("mode", "upgrade");''',
'''    private static void applyActiveEffectLike(CharacterState state, Snapshot out, RuleItem item, JSONObject rule) {
        String path = resolveInjected(rule.optString("path", ""), item, state);
        String rawValue = resolveInjected(String.valueOf(rule.opt("value")), item, state);
        if (path.toLowerCase(Locale.ROOT).contains("tradition")) captureTradition(out, rawValue);
        int value = intValue(rawValue, 0);
        String mode = rule.optString("mode", "upgrade");''', 'ActiveEffectLike tradition capture')

r = replace_once(r,
'''    private static void applyFlatModifier(CharacterState state, Snapshot out, RuleItem item, JSONObject rule, int index) {''',
'''    private static void captureTraditionFromOption(Snapshot out, String option) {
        if (option == null) return;
        String lower = option.toLowerCase(Locale.ROOT);
        for (String tradition : new String[]{"arcane", "divine", "occult", "primal"}) {
            if (lower.equals(tradition) || lower.endsWith(":" + tradition) || lower.contains(":tradition:" + tradition)) {
                captureTradition(out, tradition);
            }
        }
    }

    private static void captureTradition(Snapshot out, String raw) {
        String value = slug(raw);
        if ("arcane".equals(value) || "divine".equals(value) || "occult".equals(value) || "primal".equals(value)) {
            out.spellTraditions.add(value);
        }
    }

    private static void applyFlatModifier(CharacterState state, Snapshot out, RuleItem item, JSONObject rule, int index) {''', 'tradition capture helpers')

RUNTIME.write_text(r, encoding='utf-8')


v = V2.read_text(encoding='utf-8')
v = replace_once(v,
'''        LinearLayout outer = page(); outer.addView(sectionTitle("ЗАКЛИНАНИЯ")); RuleItem cls = classItem();
        if (cls != null && cls.meta.optInt("spellcasting", 0) > 0) outer.addView(note("Атака заклинанием " + signed(DerivedStats.spellAttack(state, stats, cls)) + " • КС " + DerivedStats.spellDc(state, stats, cls)));
        EditText search = input("", "Поиск заклинания");''',
'''        LinearLayout outer = page(); outer.addView(sectionTitle("ЗАКЛИНАНИЯ")); RuleItem cls = classItem();
        RuleRuntime.Snapshot spellRuntime = RuntimeBridge.snapshot(state, stats);
        boolean hasSpellcasting = spellRuntime != null && spellRuntime.hasSpellcasting();
        java.util.Set<String> allowedTraditions = spellRuntime == null ? new java.util.LinkedHashSet<>() : spellRuntime.spellTraditions();
        if (hasSpellcasting && cls != null) outer.addView(note("Атака заклинанием " + signed(DerivedStats.spellAttack(state, stats, cls)) + " • КС " + DerivedStats.spellDc(state, stats, cls) + (allowedTraditions.isEmpty() ? "" : " • " + traditionLabels(allowedTraditions))));
        if (!hasSpellcasting) outer.addView(note("У персонажа пока нет источника заклинаний. Выбери заклинательский класс или архетип."));
        else if (allowedTraditions.isEmpty()) outer.addView(note("Сначала заверши обязательный выбор традиции, кровной линии, покровителя или другого источника магии."));
        EditText search = input("", "Поиск заклинания");''', 'spell page runtime context')

v = replace_once(v,
'''            list.addView(sectionTitle("КАТАЛОГ")); int maxRank = Math.max(1, Math.min(10, (state.level + 1) / 2));
            for (RuleItem item : localizedQuery("spell", maxRank, search.getText().toString(), 180)) {
                boolean has = state.hasArrayItem(state.spells, item.id); TextView r = actionRow((has ? "✓ " : "+ ") + RuNames.shortName(item.name), spellMeta(item));''',
'''            list.addView(sectionTitle("КАТАЛОГ")); int maxRank = Math.max(1, Math.min(10, (state.level + 1) / 2));
            if (!hasSpellcasting || allowedTraditions.isEmpty()) {
                list.addView(note("Каталог откроется после определения источника и традиции заклинаний."));
                return;
            }
            for (RuleItem item : localizedQuery("spell", maxRank, search.getText().toString(), 260)) {
                if (!spellMatchesTraditions(item, allowedTraditions)) continue;
                boolean has = state.hasArrayItem(state.spells, item.id); TextView r = actionRow((has ? "✓ " : "+ ") + RuNames.shortName(item.name), spellMeta(item));''', 'spell catalog filtering')

v = replace_once(v,
'''    private String spellMeta(RuleItem item) {
        JSONArray traditions = item.meta.optJSONArray("traditions"); String tr = joinJson(traditions); String time = item.meta.optString("time", "");
        return "ранг " + item.level + (tr.isEmpty() ? "" : " • " + tr) + (time.isEmpty() ? "" : " • " + time + " действия");
    }''',
'''    private boolean spellMatchesTraditions(RuleItem item, java.util.Set<String> allowed) {
        if (item == null || allowed == null || allowed.isEmpty()) return false;
        JSONArray traditions = item.meta.optJSONArray("traditions");
        if (traditions == null || traditions.length() == 0) return false;
        for (int i = 0; i < traditions.length(); i++) if (allowed.contains(traditions.optString(i, "").toLowerCase(Locale.ROOT))) return true;
        return false;
    }

    private String traditionLabels(java.util.Set<String> traditions) {
        List<String> out = new ArrayList<>();
        for (String t : traditions) {
            if ("arcane".equals(t)) out.add("арканная");
            else if ("divine".equals(t)) out.add("сакральная");
            else if ("occult".equals(t)) out.add("оккультная");
            else if ("primal".equals(t)) out.add("природная");
        }
        return String.join(", ", out);
    }

    private String spellMeta(RuleItem item) {
        JSONArray traditions = item.meta.optJSONArray("traditions"); String tr = joinJson(traditions); String time = item.meta.optString("time", "");
        return "ранг " + item.level + (tr.isEmpty() ? "" : " • " + tr) + (time.isEmpty() ? "" : " • " + time + " действия");
    }''', 'spell tradition helpers')

V2.write_text(v, encoding='utf-8')
print('Gran 2e spell catalogs now follow resolved class/rule traditions')
