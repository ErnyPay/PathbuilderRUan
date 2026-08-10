package ru.gran.edge2e;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Character rule graph runtime.
 *
 * The builder resolves a character from base choices, automatic features, selected
 * feats and executable PF2e rule elements. The result is a fixed-point snapshot used
 * by prerequisites, skill ranks, the BUILD screen and future combat calculations.
 */
public final class RuleRuntime {
    // Android's ICU regex parser requires the closing brace to be escaped explicitly.
    private static final Pattern INJECT_SELECTION = Pattern.compile("\\{item\\|flags\\.system\\.rulesSelections\\.([^}]+)\\}");
    private static final List<String> SKILLS = Arrays.asList(
            "acrobatics", "arcana", "athletics", "crafting", "deception", "diplomacy",
            "intimidation", "medicine", "nature", "occultism", "performance", "religion",
            "society", "stealth", "survival", "thievery"
    );

    private RuleRuntime() { }

    public static final class Option {
        public final String label;
        public final String value;
        Option(String label, String value) { this.label = label; this.value = value; }
    }

    public static final class ChoicePrompt {
        public final String sourceId;
        public final String flag;
        public final String title;
        public final List<Option> options;
        public final boolean dynamic;

        ChoicePrompt(String sourceId, String flag, String title, List<Option> options, boolean dynamic) {
            this.sourceId = sourceId;
            this.flag = flag;
            this.title = title;
            this.options = options;
            this.dynamic = dynamic;
        }

        public String key() { return sourceId + ":" + flag; }
    }

    public static final class Snapshot {
        private final LinkedHashMap<String, RuleItem> items = new LinkedHashMap<>();
        private final LinkedHashMap<String, Integer> automaticLevels = new LinkedHashMap<>();
        private final LinkedHashSet<String> names = new LinkedHashSet<>();
        private final LinkedHashSet<String> rollOptions = new LinkedHashSet<>();
        private final LinkedHashMap<String, Integer> skillRanks = new LinkedHashMap<>();
        private final LinkedHashMap<String, Integer> proficiencyRanks = new LinkedHashMap<>();
        private final LinkedHashMap<String, ChoicePrompt> prompts = new LinkedHashMap<>();
        private final LinkedHashMap<String, Integer> modifierEffects = new LinkedHashMap<>();
        private final LinkedHashMap<String, String> modifierSelectors = new LinkedHashMap<>();
        public final List<String> warnings = new ArrayList<>();

        public List<RuleItem> allItems() { return new ArrayList<>(items.values()); }
        public List<ChoicePrompt> choices() { return new ArrayList<>(prompts.values()); }
        public Set<String> allNames() { return new LinkedHashSet<>(names); }
        public Set<String> rollOptions() { return new LinkedHashSet<>(rollOptions); }
        public int automaticLevel(String itemId) { return automaticLevels.containsKey(itemId) ? automaticLevels.get(itemId) : 0; }
        public boolean isAutomatic(String itemId) { return automaticLevels.containsKey(itemId); }

        public int rank(CharacterState state, String skill) {
            String k = slug(skill);
            int manual = state == null ? 0 : state.rank(k);
            int automatic = skillRanks.containsKey(k) ? skillRanks.get(k) : 0;
            return Math.max(manual, automatic);
        }

        public int proficiency(String key, int fallback) {
            return proficiencyRanks.containsKey(key) ? proficiencyRanks.get(key) : fallback;
        }

        public int modifier(String selector) {
            int total = 0;
            for (Map.Entry<String, String> entry : modifierSelectors.entrySet()) {
                if (selector.equals(entry.getValue())) total += modifierEffects.get(entry.getKey());
            }
            return total;
        }

        public boolean hasName(String value) {
            String wanted = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
            if (wanted.isEmpty()) return false;
            for (String name : names) {
                if (name.equals(wanted) || name.contains(wanted) || wanted.contains(name)) return true;
            }
            return false;
        }
    }

    public static Snapshot resolve(RuleStore store, CharacterState state, StatsState stats) {
        Snapshot out = new Snapshot();
        if (store == null || state == null) return out;

        RuleItem cls = addNamed(store, out, "class", state.className, false, 1);
        RuleItem ancestry = addNamed(store, out, "ancestry", state.ancestry, false, 1);
        RuleItem background = addNamed(store, out, "background", state.background, false, 1);
        String heritageId = state.choiceId("base:heritage");
        if (!heritageId.isEmpty()) addItem(out, store.findById(heritageId), false, 1);

        Iterator<String> choiceKeys = state.choices.keys();
        while (choiceKeys.hasNext()) {
            String key = choiceKeys.next();
            String id = state.choiceId(key);
            if (!id.isEmpty()) addItem(out, store.findById(id), false, levelFromKey(key));
        }

        if (background != null) applyTrainedSkills(out, background.meta.optJSONArray("trainedSkills"));
        if (cls != null) {
            JSONObject trained = cls.meta.optJSONObject("trainedSkills");
            if (trained != null) applyTrainedSkills(out, trained.optJSONArray("value"));
            applyClassBaseProficiencies(out, cls);
            addFeatureList(store, out, cls.meta.optJSONArray("features"), state.level);
        }
        if (background != null) addFeatureList(store, out, background.meta.optJSONArray("features"), state.level);

        Set<String> appliedGrantRules = new HashSet<>();
        for (int pass = 0; pass < 8; pass++) {
            int beforeItems = out.items.size();
            int beforeOptions = out.rollOptions.size();
            int beforeRanks = hashRanks(out);
            List<RuleItem> current = new ArrayList<>(out.items.values());
            for (RuleItem item : current) applyRuleElements(store, state, out, item, appliedGrantRules);
            refreshSkillRollOptions(out, state);
            if (beforeItems == out.items.size() && beforeOptions == out.rollOptions.size() && beforeRanks == hashRanks(out)) break;
        }

        // Class free trained skills are resolved after class/background Rule Elements so options can
        // exclude skills already granted by those rules. PF2e classes grant a base number plus INT mod.
        if (cls != null) addClassAdditionalSkillPrompts(out, state, cls, stats);
        refreshSkillRollOptions(out, state);

        if (ancestry != null) out.rollOptions.add("self:ancestry:" + slug(ancestry.name));
        if (background != null) out.rollOptions.add("self:background:" + slug(background.name));
        return out;
    }

    private static RuleItem addNamed(RuleStore store, Snapshot out, String category, String name, boolean automatic, int level) {
        if (name == null || name.isEmpty()) return null;
        RuleItem item = store.findExact(category, name);
        addItem(out, item, automatic, level);
        return item;
    }

    private static boolean addItem(Snapshot out, RuleItem item, boolean automatic, int level) {
        if (item == null || item.id == null || item.id.isEmpty()) return false;
        if (out.items.containsKey(item.id)) {
            if (automatic && !out.automaticLevels.containsKey(item.id)) out.automaticLevels.put(item.id, level);
            return false;
        }
        out.items.put(item.id, item);
        if (automatic) out.automaticLevels.put(item.id, Math.max(1, level));
        String lowerName = item.name.toLowerCase(Locale.ROOT).trim();
        if (!lowerName.isEmpty()) out.names.add(lowerName);
        String s = item.meta.optString("slug", slug(item.name));
        if ("class".equals(item.category)) out.rollOptions.add("self:class:" + s);
        else if ("ancestry".equals(item.category)) out.rollOptions.add("self:ancestry:" + s);
        else if ("heritage".equals(item.category)) out.rollOptions.add("self:heritage:" + s);
        else if ("background".equals(item.category)) out.rollOptions.add("self:background:" + s);
        else if ("class-feature".equals(item.category)) {
            out.rollOptions.add("self:feature:" + s);
            out.rollOptions.add("feature:" + s);
        } else if ("feat".equals(item.category)) {
            out.rollOptions.add("self:feat:" + s);
            out.rollOptions.add("feat:" + s);
        }
        for (String trait : item.traits) {
            String t = slug(trait);
            if (!t.isEmpty()) out.rollOptions.add("self:trait:" + t);
        }
        return true;
    }

    private static void addFeatureList(RuleStore store, Snapshot out, JSONArray features, int currentLevel) {
        if (features == null) return;
        for (int i = 0; i < features.length(); i++) {
            JSONObject f = features.optJSONObject(i);
            if (f == null) continue;
            int level = Math.max(1, f.optInt("level", 1));
            if (level > currentLevel) continue;
            RuleItem feature = store.findExact("class-feature", f.optString("name", ""));
            if (feature == null) feature = store.findFromUuid(f.optString("uuid", ""));
            if (feature == null) feature = store.findAnyExact(f.optString("name", ""));
            if (feature != null) addItem(out, feature, true, level);
        }
    }

    private static void addClassAdditionalSkillPrompts(Snapshot out, CharacterState state, RuleItem cls, StatsState stats) {
        JSONObject trained = cls.meta.optJSONObject("trainedSkills");
        if (trained == null) return;
        int intModifier = stats == null ? 0 : stats.ability("int");
        int additional = Math.max(0, trained.optInt("additional", 0) + intModifier);
        Set<String> chosenHere = new HashSet<>();

        // Discard stale selections from a previous class/INT value that no longer has this many slots.
        for (int i = additional; i < 16; i++) {
            String staleFlag = "granClassSkill" + (i + 1);
            if (!state.ruleSelection(cls.id, staleFlag).isEmpty()) state.setRuleSelection(cls.id, staleFlag, null);
        }

        for (int i = 0; i < additional; i++) {
            String flag = "granClassSkill" + (i + 1);
            String selected = slug(state.ruleSelection(cls.id, flag));
            boolean validSelected = !selected.isEmpty()
                    && SKILLS.contains(selected)
                    && !chosenHere.contains(selected)
                    && out.rank(state, selected) <= 0;

            if (validSelected) {
                chosenHere.add(selected);
                upgrade(out.skillRanks, selected, 1);
                out.rollOptions.add("skill:" + selected + ":rank:trained");
                continue;
            }

            if (!selected.isEmpty()) state.setRuleSelection(cls.id, flag, null);
            List<Option> options = new ArrayList<>();
            for (String skill : SKILLS) {
                if (chosenHere.contains(skill) || out.rank(state, skill) > 0) continue;
                options.add(new Option(skill, skill));
            }
            ChoicePrompt prompt = new ChoicePrompt(
                    cls.id,
                    flag,
                    "Обученный навык класса " + (i + 1) + " из " + additional,
                    options,
                    false
            );
            out.prompts.put(prompt.key(), prompt);
        }
    }

    private static void applyTrainedSkills(Snapshot out, JSONArray values) {
        if (values == null) return;
        for (int i = 0; i < values.length(); i++) {
            String skill = slug(values.optString(i, ""));
            if (!skill.isEmpty()) upgrade(out.skillRanks, skill, 1);
        }
    }

    private static void applyClassBaseProficiencies(Snapshot out, RuleItem cls) {
        copyRankMap(out, "attack:", cls.meta.optJSONObject("attacks"));
        copyRankMap(out, "defense:", cls.meta.optJSONObject("defenses"));
        copyRankMap(out, "save:", cls.meta.optJSONObject("savingThrows"));
        out.proficiencyRanks.put("perception", cls.meta.optInt("perception", 0));
        out.proficiencyRanks.put("spellcasting", cls.meta.optInt("spellcasting", 0));
        if (cls.meta.optInt("spellcasting", 0) > 0 || cls.meta.optJSONArray("traditions") != null) out.rollOptions.add("self:spellcasting");
    }

    private static void copyRankMap(Snapshot out, String prefix, JSONObject map) {
        if (map == null) return;
        Iterator<String> it = map.keys();
        while (it.hasNext()) {
            String key = it.next();
            Object raw = map.opt(key);
            int rank = 0;
            if (raw instanceof Number) rank = ((Number) raw).intValue();
            else if (raw instanceof JSONObject) rank = ((JSONObject) raw).optInt("rank", 0);
            if (rank > 0) out.proficiencyRanks.put(prefix + key, rank);
        }
    }

    private static void applyRuleElements(RuleStore store, CharacterState state, Snapshot out, RuleItem item, Set<String> appliedGrantRules) {
        JSONArray rules = item.meta.optJSONArray("ruleElements");
        if (rules == null) return;
        for (int i = 0; i < rules.length(); i++) {
            JSONObject rule = rules.optJSONObject(i);
            if (rule == null) continue;
            if (!predicateMatches(rule.opt("predicate"), item, state, out)) continue;
            String key = rule.optString("key", "");
            switch (key) {
                case "ChoiceSet": applyChoiceSet(state, out, item, rule, i); break;
                case "ActiveEffectLike": applyActiveEffectLike(state, out, item, rule); break;
                case "GrantItem":
                    String grantKey = item.id + "#" + i;
                    if (!appliedGrantRules.contains(grantKey)) {
                        String uuid = resolveInjected(rule.optString("uuid", ""), item, state);
                        RuleItem granted = store.findFromUuid(uuid);
                        if (granted != null) {
                            addItem(out, granted, true, Math.max(1, item.level));
                            appliedGrantRules.add(grantKey);
                        } else if (!uuid.isEmpty()) addWarning(out, "Не найден GrantItem: " + uuid);
                    }
                    break;
                case "RollOption":
                    String option = resolveInjected(rule.optString("option", rule.optString("value", "")), item, state);
                    if (!option.isEmpty()) out.rollOptions.add(option);
                    break;
                case "FlatModifier": applyFlatModifier(state, out, item, rule, i); break;
                default: break;
            }
        }
    }

    private static void applyChoiceSet(CharacterState state, Snapshot out, RuleItem item, JSONObject rule, int index) {
        String flag = rule.optString("flag", "");
        if (flag.isEmpty()) flag = "choice" + index;
        String selected = state.ruleSelection(item.id, flag);
        if (selected.isEmpty() && rule.has("selection")) selected = String.valueOf(rule.opt("selection"));
        if (!selected.isEmpty() && !"null".equals(selected)) {
            String v = slug(selected);
            out.rollOptions.add("rules-selection:" + slug(flag) + ":" + v);
            out.rollOptions.add("item:rules-selection:" + slug(flag) + ":" + v);
            return;
        }

        Object choicesRaw = rule.opt("choices");
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
        }
        String promptText = rule.optString("prompt", "Дополнительный выбор: " + item.name);
        ChoicePrompt prompt = new ChoicePrompt(item.id, flag, promptText, options, dynamic);
        out.prompts.put(prompt.key(), prompt);
    }

    private static void applyActiveEffectLike(CharacterState state, Snapshot out, RuleItem item, JSONObject rule) {
        String path = resolveInjected(rule.optString("path", ""), item, state);
        int value = intValue(resolveInjected(String.valueOf(rule.opt("value")), item, state), 0);
        String mode = rule.optString("mode", "upgrade");
        if (path.startsWith("system.skills.") && path.endsWith(".rank")) {
            String skill = path.substring("system.skills.".length(), path.length() - ".rank".length());
            setRank(out.skillRanks, slug(skill), value, mode); return;
        }
        if (path.startsWith("system.saves.") && path.endsWith(".rank")) {
            String save = path.substring("system.saves.".length(), path.length() - ".rank".length());
            setRank(out.proficiencyRanks, "save:" + slug(save), value, mode); return;
        }
        if ("system.attributes.perception.rank".equals(path) || "system.perception.rank".equals(path)) {
            setRank(out.proficiencyRanks, "perception", value, mode); return;
        }
        if (path.contains("spellcasting") && path.endsWith(".rank")) {
            setRank(out.proficiencyRanks, "spellcasting", value, mode);
            if (value > 0) out.rollOptions.add("self:spellcasting"); return;
        }
        if (path.contains("proficiencies") && path.endsWith(".rank")) {
            String[] parts = path.split("\\.");
            if (parts.length >= 3) setRank(out.proficiencyRanks, "proficiency:" + slug(parts[parts.length - 2]), value, mode);
        }
    }

    private static void applyFlatModifier(CharacterState state, Snapshot out, RuleItem item, JSONObject rule, int index) {
        Object selectorRaw = rule.opt("selector");
        String selector = selectorRaw instanceof JSONArray ? ((JSONArray) selectorRaw).optString(0, "") : String.valueOf(selectorRaw == null ? "" : selectorRaw);
        selector = resolveInjected(selector, item, state);
        int value = intValue(resolveInjected(String.valueOf(rule.opt("value")), item, state), 0);
        if (selector.isEmpty() || value == 0) return;
        String effectKey = item.id + "#flat#" + index;
        out.modifierEffects.put(effectKey, value);
        out.modifierSelectors.put(effectKey, selector);
    }

    private static boolean predicateMatches(Object predicate, RuleItem item, CharacterState state, Snapshot out) {
        if (predicate == null || JSONObject.NULL.equals(predicate)) return true;
        if (predicate instanceof JSONArray) {
            JSONArray a = (JSONArray) predicate;
            for (int i = 0; i < a.length(); i++) if (!predicateMatches(a.opt(i), item, state, out)) return false;
            return true;
        }
        if (predicate instanceof String) {
            String raw = resolveInjected((String) predicate, item, state);
            if (raw.isEmpty()) return true;
            if (raw.startsWith("not:")) return !out.rollOptions.contains(raw.substring(4));
            return out.rollOptions.contains(raw);
        }
        if (predicate instanceof JSONObject) {
            JSONObject o = (JSONObject) predicate;
            if (o.has("and")) return predicateMatches(o.opt("and"), item, state, out);
            if (o.has("or")) {
                Object raw = o.opt("or");
                if (raw instanceof JSONArray) {
                    JSONArray a = (JSONArray) raw;
                    for (int i = 0; i < a.length(); i++) if (predicateMatches(a.opt(i), item, state, out)) return true;
                    return false;
                }
                return predicateMatches(raw, item, state, out);
            }
            if (o.has("not")) return !predicateMatches(o.opt("not"), item, state, out);
            if (o.has("nor")) {
                Object raw = o.opt("nor");
                if (raw instanceof JSONArray) {
                    JSONArray a = (JSONArray) raw;
                    for (int i = 0; i < a.length(); i++) if (predicateMatches(a.opt(i), item, state, out)) return false;
                    return true;
                }
                return !predicateMatches(raw, item, state, out);
            }
            for (String op : new String[]{"gte", "gt", "lte", "lt", "eq"}) {
                if (!o.has(op)) continue;
                Object raw = o.opt(op);
                if (!(raw instanceof JSONArray)) return false;
                JSONArray pair = (JSONArray) raw;
                if (pair.length() < 2) return false;
                int left = numericOperand(pair.opt(0), state);
                int right = numericOperand(pair.opt(1), state);
                switch (op) {
                    case "gte": return left >= right;
                    case "gt": return left > right;
                    case "lte": return left <= right;
                    case "lt": return left < right;
                    case "eq": return left == right;
                }
            }
            return false;
        }
        return false;
    }

    private static int numericOperand(Object raw, CharacterState state) {
        if (raw instanceof Number) return ((Number) raw).intValue();
        String s = String.valueOf(raw);
        if ("actor:level".equals(s) || "self:level".equals(s)) return state.level;
        return intValue(s, 0);
    }

    private static String resolveInjected(String raw, RuleItem item, CharacterState state) {
        if (raw == null) return "";
        Matcher m = INJECT_SELECTION.matcher(raw);
        StringBuffer b = new StringBuffer();
        while (m.find()) {
            String selection = state.ruleSelection(item.id, m.group(1));
            m.appendReplacement(b, Matcher.quoteReplacement(selection));
        }
        m.appendTail(b);
        String result = b.toString();
        result = result.replace("@item.level", String.valueOf(item.level));
        result = result.replace("@actor.level", String.valueOf(state.level));
        return result;
    }

    private static void refreshSkillRollOptions(Snapshot out, CharacterState state) {
        for (String skill : SKILLS) {
            int rank = out.rank(state, skill);
            if (rank <= 0) continue;
            out.rollOptions.add("skill:" + skill + ":rank:" + rankName(rank));
            out.rollOptions.add("skill:" + skill + ":rank:" + rank);
        }
    }

    private static void setRank(Map<String, Integer> map, String key, int value, String mode) {
        if (key == null || key.isEmpty()) return;
        int current = map.containsKey(key) ? map.get(key) : 0;
        int next;
        if ("override".equalsIgnoreCase(mode)) next = value;
        else if ("add".equalsIgnoreCase(mode)) next = current + value;
        else next = Math.max(current, value);
        if (next != current) map.put(key, next);
    }

    private static void upgrade(Map<String, Integer> map, String key, int value) { setRank(map, key, value, "upgrade"); }
    private static int hashRanks(Snapshot out) { return out.skillRanks.hashCode() * 31 + out.proficiencyRanks.hashCode(); }

    private static int levelFromKey(String key) {
        if (key == null || !key.startsWith("L")) return 1;
        int colon = key.indexOf(':');
        try { return Integer.parseInt(key.substring(1, colon > 1 ? colon : key.length())); }
        catch (Exception ignored) { return 1; }
    }

    private static String rankName(int rank) {
        switch (rank) {
            case 1: return "trained";
            case 2: return "expert";
            case 3: return "master";
            case 4: return "legendary";
            default: return "untrained";
        }
    }

    private static int intValue(String raw, int fallback) {
        try { return Integer.parseInt(raw.replaceAll("[^0-9+-]", "")); }
        catch (Exception ignored) { return fallback; }
    }

    private static void addWarning(Snapshot out, String value) {
        if (!out.warnings.contains(value) && out.warnings.size() < 50) out.warnings.add(value);
    }

    public static String slug(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).trim().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }
}
