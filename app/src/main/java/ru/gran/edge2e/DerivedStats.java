package ru.gran.edge2e;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;
import java.util.Set;

public final class DerivedStats {
    private DerivedStats() { }

    public static int proficiency(int rank, int level) {
        return rank <= 0 ? 0 : level + (rank * 2);
    }

    public static int hp(CharacterState c, StatsState s, RuleItem ancestry, RuleItem cls) {
        int ancestryHp = ancestry == null ? 0 : ancestry.meta.optInt("hp", 0);
        int classHp = cls == null ? 8 : cls.meta.optInt("hp", 8);
        int total = ancestryHp + (classHp + s.ability("con")) * Math.max(1, c.level);
        Set<String> selected = c.selectedNames();
        for (String name : selected) {
            if (name.equals("toughness") || name.contains("стойкость")) {
                total += c.level;
                break;
            }
        }
        return Math.max(1, total);
    }

    public static int save(CharacterState c, StatsState s, RuleItem cls, String save) {
        String ability = "fortitude".equals(save) ? "con" : "reflex".equals(save) ? "dex" : "wis";
        int rank = classMapRank(cls, "savingThrows", save, 0);
        return s.ability(ability) + proficiency(rank, c.level);
    }

    public static int perception(CharacterState c, StatsState s, RuleItem cls) {
        int rank = cls == null ? 0 : cls.meta.optInt("perception", 0);
        return s.ability("wis") + proficiency(rank, c.level);
    }

    public static int ac(CharacterState c, StatsState s, RuleItem cls, RuleItem armor) {
        String category = "unarmored";
        int armorBonus = 0;
        int dexCap = 99;
        int potency = 0;
        if (armor != null) {
            category = armor.meta.optString("itemCategory", "unarmored");
            armorBonus = armor.meta.optInt("acBonus", 0);
            dexCap = armor.meta.optInt("dexCap", 99);
            potency = armor.meta.optInt("potency", 0);
        }
        int rank = classMapRank(cls, "defenses", category, 0);
        int dex = Math.min(s.ability("dex"), dexCap);
        int shield = s.shieldRaised ? 2 : 0;
        return 10 + dex + proficiency(rank, c.level) + armorBonus + potency + shield;
    }

    public static int speed(StatsState s, RuleItem ancestry, RuleItem armor) {
        int speed = ancestry == null ? 25 : ancestry.meta.optInt("speed", 25);
        if (armor != null) {
            int strength = armor.meta.optInt("strength", 0);
            int penalty = armor.meta.optInt("speedPenalty", 0);
            if (s.ability("str") >= strength && penalty < 0) penalty = Math.min(0, penalty + 5);
            speed += penalty;
        }
        return Math.max(5, speed);
    }

    public static int skill(CharacterState c, StatsState s, String skill) {
        int rank = c.rank(skill);
        return abilityForSkill(s, skill) + proficiency(rank, c.level);
    }

    public static int attack(CharacterState c, StatsState s, RuleItem cls, RuleItem weapon) {
        if (weapon == null) return 0;
        String category = weapon.meta.optString("itemCategory", "simple");
        int rank = classMapRank(cls, "attacks", category, 0);
        boolean ranged = hasRange(weapon);
        int ability = ranged ? s.ability("dex") : s.ability("str");
        if (!ranged && hasTrait(weapon, "finesse")) ability = Math.max(s.ability("str"), s.ability("dex"));
        return ability + proficiency(rank, c.level) + weapon.meta.optInt("potency", 0) + weapon.meta.optInt("bonus", 0);
    }

    public static String damage(StatsState s, RuleItem weapon) {
        if (weapon == null) return "—";
        int baseDice = Math.max(1, weapon.meta.optInt("damageDice", 1));
        int striking = Math.max(0, weapon.meta.optInt("striking", 0));
        int dice = baseDice * (striking + 1);
        String die = weapon.meta.optString("damageDie", "d4");
        int mod = weapon.meta.optInt("bonusDamage", 0);
        boolean ranged = hasRange(weapon);
        if (!ranged || hasTrait(weapon, "thrown") || anyTraitPrefix(weapon, "thrown-")) mod += s.ability("str");
        else if (hasTrait(weapon, "propulsive")) mod += Math.max(0, s.ability("str") / 2);
        return dice + die + (mod == 0 ? "" : (mod > 0 ? "+" : "") + mod) + " " + weapon.meta.optString("damageType", "");
    }

    public static int spellAttack(CharacterState c, StatsState s, RuleItem cls) {
        int rank = cls == null ? 0 : cls.meta.optInt("spellcasting", 0);
        return proficiency(rank, c.level) + keyAbility(s, cls);
    }

    public static int spellDc(CharacterState c, StatsState s, RuleItem cls) {
        return 10 + spellAttack(c, s, cls);
    }

    public static int keyAbility(StatsState s, RuleItem cls) {
        if (cls == null) return 0;
        JSONArray a = cls.meta.optJSONArray("keyAbility");
        int best = -99;
        if (a != null) {
            for (int i = 0; i < a.length(); i++) best = Math.max(best, s.ability(a.optString(i)));
        }
        return best == -99 ? 0 : best;
    }

    public static int classMapRank(RuleItem cls, String mapName, String key, int fallback) {
        if (cls == null) return fallback;
        JSONObject map = cls.meta.optJSONObject(mapName);
        if (map == null) return fallback;
        Object raw = map.opt(key);
        if (raw instanceof Number) return ((Number) raw).intValue();
        if (raw instanceof JSONObject) return ((JSONObject) raw).optInt("rank", fallback);
        try { return Integer.parseInt(String.valueOf(raw)); }
        catch (Exception ignored) { return fallback; }
    }

    private static int abilityForSkill(StatsState s, String skill) {
        switch (skill.toLowerCase(Locale.ROOT)) {
            case "athletics": return s.ability("str");
            case "acrobatics": case "stealth": case "thievery": return s.ability("dex");
            case "arcana": case "crafting": case "occultism": case "society": return s.ability("int");
            case "medicine": case "nature": case "religion": case "survival": return s.ability("wis");
            default: return s.ability("cha");
        }
    }

    private static boolean hasRange(RuleItem weapon) {
        Object r = weapon.meta.opt("range");
        return r != null && !JSONObject.NULL.equals(r) && !String.valueOf(r).isEmpty() && !"0".equals(String.valueOf(r));
    }

    public static boolean hasTrait(RuleItem item, String trait) {
        for (String t : item.traits) if (t.equalsIgnoreCase(trait)) return true;
        return false;
    }

    private static boolean anyTraitPrefix(RuleItem item, String prefix) {
        for (String t : item.traits) if (t.toLowerCase(Locale.ROOT).startsWith(prefix)) return true;
        return false;
    }
}
