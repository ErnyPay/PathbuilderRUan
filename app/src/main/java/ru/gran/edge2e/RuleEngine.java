package ru.gran.edge2e;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RuleEngine {
    private static final Pattern RANK_IN = Pattern.compile("(?i)(trained|expert|master|legendary)\\s+(?:in\\s+)?([a-zA-Z ]+)");
    private static final Pattern LEVEL = Pattern.compile("(?i)(?:level|уровень)\\s*(\\d+)");
    private static final Pattern ABILITY = Pattern.compile("(?i)(strength|dexterity|constitution|intelligence|wisdom|charisma)\\s*(?:score\\s*)?(?:of\\s*)?([+\\-]?\\d+)");
    private static final Set<String> SPELLCASTING_CLASSES = new HashSet<>(Arrays.asList(
            "animist", "bard", "cleric", "druid", "magus", "oracle", "psychic",
            "sorcerer", "summoner", "witch", "wizard"
    ));

    private RuleEngine() { }

    public static boolean canChoose(RuleItem item, CharacterState state, String slotCategory, int slotLevel) {
        return blockReason(item, state, null, slotCategory, slotLevel) == null;
    }

    public static boolean canChoose(RuleItem item, CharacterState state, RuleRuntime.Snapshot runtime, String slotCategory, int slotLevel) {
        return blockReason(item, state, runtime, slotCategory, slotLevel) == null;
    }

    public static String blockReason(RuleItem item, CharacterState state, String slotCategory, int slotLevel) {
        return blockReason(item, state, null, slotCategory, slotLevel);
    }

    public static String blockReason(RuleItem item, CharacterState state, RuleRuntime.Snapshot runtime, String slotCategory, int slotLevel) {
        if (item == null) return "Правило не найдено";
        if (item.level > slotLevel) return "Нужен уровень " + item.level;
        if (item.meta.optBoolean("onlyLevel1", false) && slotLevel != 1) return "Этот фит можно взять только на 1 уровне";
        if (!slotMatches(item, slotCategory)) return "Не подходит для этого типа выбора";

        int maxTakable = maxTakable(item);
        if (maxTakable > 0) {
            int already = selectedCount(state, item.id);
            String currentKey = "L" + slotLevel + ":" + slotCategory;
            if (item.id.equals(state.choiceId(currentKey))) already = Math.max(0, already - 1);
            if (already >= maxTakable) return maxTakable == 1 ? "Этот фит уже выбран" : "Достигнут предел: " + maxTakable;
        }

        String group = item.meta.optString("groupKey", "");
        if ("class".equals(slotCategory) && "class".equals(item.subtype) && !state.className.isEmpty()) {
            String current = slug(state.className);
            if (!group.isEmpty() && !group.equals(current) && !hasTrait(item, current)) return "Фит другого класса";
            if (group.isEmpty() && !hasTrait(item, current)) return "Фит другого класса";
        }

        if ("ancestry".equals(slotCategory) && !state.ancestry.isEmpty()) {
            String ancestry = slug(state.ancestry);
            String heritage = slug(state.choiceName("base:heritage"));
            boolean branchMatch = !group.isEmpty() && (group.equals(ancestry) || (!heritage.isEmpty() && group.equals(heritage)));
            boolean traitMatch = hasTrait(item, ancestry) || (!heritage.isEmpty() && hasTrait(item, heritage));
            if (!branchMatch && !traitMatch && !hasTrait(item, "versatile-heritage")) return "Фит другого рода или наследия";
        }

        if ("archetype".equals(item.subtype)) {
            boolean dedication = isDedication(item);
            String active = state.activeDedicationGroup();
            if (dedication) {
                if (!active.isEmpty() && !active.equals(group) && state.countSelectedGroup(active) < 3) {
                    return "Сначала выбери ещё два фита текущего архетипа";
                }
            } else if (!group.isEmpty() && !state.hasDedication(group)) {
                return "Сначала нужна Dedication этого архетипа";
            }
        }

        String structured = structuredRequirementBlock(item, state, runtime);
        if (structured != null) return structured;

        Set<String> selected = runtime == null ? state.selectedNames() : runtime.allNames();
        selected.addAll(state.selectedNames());
        for (String prereq : item.prerequisites) {
            if (!prerequisiteMet(prereq, state, runtime, selected)) return "Не выполнено: " + prereq;
        }
        return null;
    }

    private static int maxTakable(RuleItem item) {
        if (!item.meta.has("maxTakable") || item.meta.isNull("maxTakable")) return 0;
        Object raw = item.meta.opt("maxTakable");
        if (raw instanceof Number) return Math.max(0, ((Number) raw).intValue());
        try { return Math.max(0, Integer.parseInt(String.valueOf(raw))); }
        catch (Exception ignored) { return 0; }
    }

    private static int selectedCount(CharacterState state, String id) {
        int count = 0;
        Iterator<String> it = state.choices.keys();
        while (it.hasNext()) if (id.equals(state.choiceId(it.next()))) count++;
        return count;
    }

    private static boolean slotMatches(RuleItem item, String slotCategory) {
        if (slotCategory == null || slotCategory.isEmpty()) return true;
        if (slotCategory.equals(item.category)) return true;
        if (!"feat".equals(item.category)) return false;
        if ("class".equals(slotCategory)) return "class".equals(item.subtype) || "archetype".equals(item.subtype);
        if ("ancestry".equals(slotCategory)) return "ancestry".equals(item.subtype);
        if ("skill".equals(slotCategory)) return "skill".equals(item.subtype);
        if ("general".equals(slotCategory)) return "general".equals(item.subtype) || ("skill".equals(item.subtype) && hasTrait(item, "general"));
        return false;
    }

    private static String structuredRequirementBlock(RuleItem item, CharacterState state, RuleRuntime.Snapshot runtime) {
        JSONArray requirements = item.meta.optJSONArray("requirementsParsed");
        if (requirements == null) return null;
        for (int i = 0; i < requirements.length(); i++) {
            JSONObject req = requirements.optJSONObject(i);
            if (req == null) continue;
            String kind = req.optString("kind", "");
            if ("skill".equals(kind)) {
                String skill = req.optString("skill", "");
                int rank = req.optInt("rank", 0);
                if (rank(state, runtime, skill) < rank) return rankLabel(rank) + " в навыке " + skill;
            } else if ("level".equals(kind)) {
                int level = req.optInt("level", 0);
                if (state.level < level) return "Нужен уровень " + level;
            } else if ("ability".equals(kind)) {
                String ability = req.optString("ability", "");
                int value = req.optInt("value", 0);
                String format = req.optString("format", "modifier");
                int requiredMod = "score".equals(format) ? Math.floorDiv(value - 10, 2) : value;
                if (StatsState.currentAbility(ability) < requiredMod) return "Недостаточная характеристика " + ability;
            } else if ("spellcasting".equals(kind) && !hasSpellcasting(state, runtime)) {
                return "Требуется заклинательство";
            }
        }
        return null;
    }

    private static boolean prerequisiteMet(String raw, CharacterState state, RuleRuntime.Snapshot runtime, Set<String> selected) {
        if (raw == null) return true;
        String p = raw.trim();
        if (p.isEmpty()) return true;
        String lower = p.toLowerCase(Locale.ROOT);
        boolean recognized = false;

        Matcher rankMatcher = RANK_IN.matcher(p);
        while (rankMatcher.find()) {
            recognized = true;
            int required = rankValue(rankMatcher.group(1));
            String skill = normalizeSkill(rankMatcher.group(2));
            if (rank(state, runtime, skill) < required) return false;
        }

        Matcher levelMatcher = LEVEL.matcher(p);
        while (levelMatcher.find()) {
            recognized = true;
            if (state.level < Integer.parseInt(levelMatcher.group(1))) return false;
        }

        Matcher abilityMatcher = ABILITY.matcher(p);
        while (abilityMatcher.find()) {
            recognized = true;
            String key = abilityKey(abilityMatcher.group(1));
            int rawValue = Integer.parseInt(abilityMatcher.group(2));
            int required = Math.abs(rawValue) > 5 ? Math.floorDiv(rawValue - 10, 2) : rawValue;
            if (StatsState.currentAbility(key) < required) return false;
        }

        if (lower.startsWith("trained in ")) {
            recognized = true;
            if (rank(state, runtime, normalizeSkill(p.substring(11))) < 1) return false;
        }

        if (lower.contains("spellcasting") || lower.contains("cast spells") || lower.contains("cast a spell")) {
            recognized = true;
            if (!hasSpellcasting(state, runtime)) return false;
        }

        String cleaned = lower.replace("feat", "").replace("the ", "").trim();
        for (String selectedName : selected) {
            if (cleaned.equals(selectedName) || cleaned.contains(selectedName) || selectedName.contains(cleaned)) return true;
        }

        if (lower.contains("shield")) {
            for (String selectedName : selected) if (selectedName.contains("shield")) return true;
            if (isSimpleRecognized(lower)) return false;
        }

        if (recognized && isSimpleRecognized(lower)) return true;
        return false;
    }

    private static boolean isSimpleRecognized(String lower) {
        String residual = lower;
        residual = residual.replaceAll("(?i)(trained|expert|master|legendary)\\s+(?:in\\s+)?[a-zA-Z ]+", " ");
        residual = residual.replaceAll("(?i)(?:level|уровень)\\s*\\d+", " ");
        residual = residual.replaceAll("(?i)(strength|dexterity|constitution|intelligence|wisdom|charisma)\\s*(?:score\\s*)?(?:of\\s*)?[+\\-]?\\d+", " ");
        residual = residual.replace("spellcasting", " ").replace("ability to cast spells", " ").replace("cast spells", " ").replace("cast a spell", " ");
        residual = residual.replaceAll("(?i)\\b(and|or|a|an|the|in|of|at|least|character)\\b", " ");
        residual = residual.replaceAll("[(),;:+0-9-]", " ").replaceAll("\\s+", " ").trim();
        return residual.isEmpty();
    }

    private static int rank(CharacterState state, RuleRuntime.Snapshot runtime, String skill) {
        return runtime == null ? state.rank(skill) : runtime.rank(state, skill);
    }

    private static boolean hasSpellcasting(CharacterState state, RuleRuntime.Snapshot runtime) {
        if (runtime != null && runtime.rollOptions().contains("self:spellcasting")) return true;
        String current = slug(state.className);
        if (SPELLCASTING_CLASSES.contains(current)) return true;
        for (String name : state.selectedNames()) {
            String s = slug(name);
            if (!s.endsWith("-dedication")) continue;
            for (String caster : SPELLCASTING_CLASSES) if (s.startsWith(caster + "-")) return true;
        }
        return false;
    }

    private static boolean isDedication(RuleItem item) {
        return hasTrait(item, "dedication") || item.name.toLowerCase(Locale.ROOT).endsWith(" dedication");
    }

    private static boolean hasTrait(RuleItem item, String trait) {
        String wanted = slug(trait);
        for (String t : item.traits) if (slug(t).equals(wanted)) return true;
        return false;
    }

    private static int rankValue(String rank) {
        switch (rank.toLowerCase(Locale.ROOT)) {
            case "trained": return 1; case "expert": return 2; case "master": return 3; case "legendary": return 4; default: return 0;
        }
    }

    private static String rankLabel(int rank) {
        switch (rank) {
            case 1: return "Обучен"; case 2: return "Эксперт"; case 3: return "Мастер"; case 4: return "Легенда"; default: return "Нужный ранг";
        }
    }

    private static String normalizeSkill(String s) {
        String value = s.toLowerCase(Locale.ROOT).trim();
        for (String skill : new String[]{"acrobatics","arcana","athletics","crafting","deception","diplomacy","intimidation","medicine","nature","occultism","performance","religion","society","stealth","survival","thievery"}) {
            if (value.startsWith(skill) || value.equals(skill)) return skill;
        }
        return value.replaceAll("[^a-z ]", "").trim().replace(' ', '-');
    }

    private static String abilityKey(String name) {
        switch (name.toLowerCase(Locale.ROOT)) {
            case "strength": return "str"; case "dexterity": return "dex"; case "constitution": return "con";
            case "intelligence": return "int"; case "wisdom": return "wis"; case "charisma": return "cha"; default: return "";
        }
    }

    private static String slug(String s) { return RuleRuntime.slug(s); }

    public static boolean classHasSlot(RuleItem classItem, String key, int level, int[] fallback) {
        if (classItem != null && classItem.meta != null) {
            JSONArray a = classItem.meta.optJSONArray(key);
            if (a != null) {
                for (int i = 0; i < a.length(); i++) if (a.optInt(i) == level) return true;
                return false;
            }
        }
        for (int v : fallback) if (v == level) return true;
        return false;
    }
}
