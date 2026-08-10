package ru.gran.edge2e;

import android.content.Context;

import org.json.JSONArray;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Resolves PF2e attribute boosts/flaws from the same mandatory ChoiceSets used by BUILD. */
public final class AbilityPlanner {
    private static final String[] KEYS = {"str", "dex", "con", "int", "wis", "cha"};

    private AbilityPlanner() { }

    public static void apply(Context context, CharacterState character, StatsState stats) {
        if (context == null || character == null || stats == null) return;
        RuleStore store = new RuleStore(context);
        try {
            Map<String, Integer> scores = new HashMap<>();
            for (String key : KEYS) scores.put(key, 10);

            RuleItem ancestry = character.ancestry.isEmpty() ? null : store.findExact("ancestry", character.ancestry);
            RuleItem background = character.background.isEmpty() ? null : store.findExact("background", character.background);
            RuleItem cls = character.className.isEmpty() ? null : store.findExact("class", character.className);

            if (ancestry != null) {
                applyStage(character, ancestry.id, "granAncestryFlaw", ancestry.meta.optJSONArray("flaws"), scores, false);
                applyStage(character, ancestry.id, "granAncestryBoost", ancestry.meta.optJSONArray("boosts"), scores, true);
            }
            if (background != null) {
                applyStage(character, background.id, "granBackgroundBoost", background.meta.optJSONArray("boosts"), scores, true);
            }
            if (cls != null) {
                JSONArray keyAbility = cls.meta.optJSONArray("keyAbility");
                String selected = selectedFromOptions(character, cls.id, "granClassKey", keyAbility, new HashSet<>());
                if (!selected.isEmpty()) boost(scores, selected);

                for (int stage : new int[]{1, 5, 10, 15, 20}) {
                    if (character.level < stage) continue;
                    Set<String> used = new HashSet<>();
                    for (int i = 0; i < 4; i++) {
                        String selectedFree = character.ruleSelection(cls.id, "granFree" + stage + "_" + i);
                        if (!validAbility(selectedFree) || used.contains(selectedFree)) continue;
                        used.add(selectedFree);
                        boost(scores, selectedFree);
                    }
                }
            }

            for (String key : KEYS) {
                int score = scores.get(key);
                stats.setAbilityScore(key, score);
            }
            stats.saveAttached();
        } finally {
            store.close();
        }
    }

    private static void applyStage(CharacterState character, String sourceId, String flagPrefix,
                                   JSONArray slots, Map<String, Integer> scores, boolean isBoost) {
        if (slots == null) return;
        Set<String> used = new HashSet<>();
        for (int i = 0; i < slots.length(); i++) {
            JSONArray options = slots.optJSONArray(i);
            if (options == null || options.length() == 0) continue;
            String selected = selectedFromOptions(character, sourceId, flagPrefix + i, options, used);
            if (selected.isEmpty()) continue;
            used.add(selected);
            if (isBoost) boost(scores, selected);
            else scores.put(selected, scores.get(selected) - 2);
        }
    }

    private static String selectedFromOptions(CharacterState character, String sourceId, String flag,
                                              JSONArray options, Set<String> used) {
        if (options == null || options.length() == 0) return "";
        if (options.length() == 1) {
            String only = options.optString(0, "");
            return validAbility(only) && !used.contains(only) ? only : "";
        }
        String selected = character.ruleSelection(sourceId, flag);
        if (!validAbility(selected) || used.contains(selected)) return "";
        for (int i = 0; i < options.length(); i++) if (selected.equals(options.optString(i))) return selected;
        return "";
    }

    private static void boost(Map<String, Integer> scores, String ability) {
        if (!scores.containsKey(ability)) return;
        int value = scores.get(ability);
        scores.put(ability, value >= 18 ? value + 1 : value + 2);
    }

    private static boolean validAbility(String value) {
        for (String key : KEYS) if (key.equals(value)) return true;
        return false;
    }
}
