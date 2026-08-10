package ru.gran.edge2e;

import org.json.JSONArray;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RuleEngine {
    private static final Pattern RANK_IN = Pattern.compile("(?i)(trained|expert|master|legendary)\\s+(?:in\\s+)?([a-zA-Z ]+)");

    private RuleEngine() { }

    public static boolean canChoose(RuleItem item, CharacterState state, String slotCategory, int slotLevel) {
        if (item == null || item.level > slotLevel) return false;
        if (!slotMatches(item, slotCategory)) return false;

        if ("class".equals(slotCategory) && !state.className.isEmpty()) {
            boolean archetype = hasTrait(item, "archetype") || hasTrait(item, "dedication");
            boolean classTrait = hasTrait(item, slug(state.className));
            if (!archetype && !classTrait) return false;
        }
        if ("ancestry".equals(slotCategory) && !state.ancestry.isEmpty()) {
            String ancestrySlug = slug(state.ancestry);
            if (!hasTrait(item, ancestrySlug) && !hasTrait(item, "versatile-heritage")) return false;
        }

        Set<String> selected = state.selectedNames();
        for (String prereq : item.prerequisites) {
            if (!prerequisiteMet(prereq, state, selected)) return false;
        }
        return true;
    }

    private static boolean slotMatches(RuleItem item, String slotCategory) {
        if (slotCategory == null || slotCategory.isEmpty()) return true;
        if (slotCategory.equals(item.category)) return true;
        if ("class".equals(slotCategory) && "feat".equals(item.category)) {
            return "class".equals(item.subtype) || "archetype".equals(item.subtype);
        }
        if ("ancestry".equals(slotCategory) && "feat".equals(item.category)) return "ancestry".equals(item.subtype);
        if ("skill".equals(slotCategory) && "feat".equals(item.category)) return "skill".equals(item.subtype);
        if ("general".equals(slotCategory) && "feat".equals(item.category)) return "general".equals(item.subtype);
        return false;
    }

    private static boolean prerequisiteMet(String raw, CharacterState state, Set<String> selected) {
        if (raw == null) return true;
        String p = raw.trim();
        if (p.isEmpty()) return true;
        String lower = p.toLowerCase(Locale.ROOT);

        Matcher m = RANK_IN.matcher(p);
        if (m.find()) {
            int required = rankValue(m.group(1));
            String skill = normalizeSkill(m.group(2));
            return state.rank(skill) >= required;
        }

        if (lower.contains("level")) {
            Matcher level = Pattern.compile("(?i)(?:level|уровень)\\s*(\\d+)").matcher(p);
            if (level.find()) return state.level >= Integer.parseInt(level.group(1));
        }

        if (lower.startsWith("trained in ")) {
            return state.rank(normalizeSkill(p.substring(11))) >= 1;
        }

        // A prerequisite that names another feat/dedication is satisfied only when that choice exists.
        String cleaned = lower.replace("feat", "").replace("the ", "").trim();
        for (String selectedName : selected) {
            if (cleaned.equals(selectedName) || cleaned.contains(selectedName) || selectedName.contains(cleaned)) return true;
        }

        // Common non-choice prerequisites that are represented elsewhere in the character.
        if (lower.contains("spellcasting") || lower.contains("cast spells")) return !state.className.isEmpty();
        if (lower.contains("shield")) return true;

        // Conservatively hide choices whose prerequisite cannot yet be proven.
        return false;
    }

    private static boolean hasTrait(RuleItem item, String trait) {
        String wanted = slug(trait);
        for (String t : item.traits) if (slug(t).equals(wanted)) return true;
        return false;
    }

    private static int rankValue(String rank) {
        switch (rank.toLowerCase(Locale.ROOT)) {
            case "trained": return 1;
            case "expert": return 2;
            case "master": return 3;
            case "legendary": return 4;
            default: return 0;
        }
    }

    private static String normalizeSkill(String s) {
        return s.toLowerCase(Locale.ROOT).trim().replaceAll("[^a-z ]", "").replace(' ', '-');
    }

    private static String slug(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).trim().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

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
