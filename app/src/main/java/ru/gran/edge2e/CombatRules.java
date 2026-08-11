package ru.gran.edge2e;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PLAY-mode combat projection of the already resolved PF2e rule graph.
 *
 * This deliberately does not mutate the character. It reads active Rule Elements from
 * RuleRuntime.Snapshot and exposes strikes plus IWR (immunity/weakness/resistance) in a
 * form the table UI can use. Unknown predicates are rejected rather than guessed true.
 */
public final class CombatRules {
    private static final Pattern FLOOR_DIV = Pattern.compile("floor\\s*\\(\\s*@?actor\\.level\\s*/\\s*(\\d+)\\s*\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CEIL_DIV = Pattern.compile("ceil\\s*\\(\\s*@?actor\\.level\\s*/\\s*(\\d+)\\s*\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern MAX_FLOOR = Pattern.compile("max\\s*\\(\\s*(\\d+)\\s*,\\s*floor\\s*\\(\\s*@?actor\\.level\\s*/\\s*(\\d+)\\s*\\)\\s*\\)", Pattern.CASE_INSENSITIVE);

    private CombatRules() { }

    public static final class Strike {
        public final String sourceId;
        public final String label;
        public final String category;
        public final String group;
        public final String ability;
        public final int attackModifier;
        public final int dice;
        public final String die;
        public final int damageModifier;
        public final String damageType;
        public final int range;
        public final List<String> traits;

        Strike(String sourceId, String label, String category, String group, String ability,
               int attackModifier, int dice, String die, int damageModifier, String damageType,
               int range, List<String> traits) {
            this.sourceId = sourceId;
            this.label = label;
            this.category = category;
            this.group = group;
            this.ability = ability;
            this.attackModifier = attackModifier;
            this.dice = dice;
            this.die = die;
            this.damageModifier = damageModifier;
            this.damageType = damageType;
            this.range = range;
            this.traits = traits;
        }

        public boolean agile() { return has(traits, "agile"); }
        public boolean finesse() { return has(traits, "finesse"); }
        public boolean ranged() { return range > 0; }
        public String damageFormula(StatsState stats) {
            int mod = damageModifier;
            if (stats != null && !ranged()) mod += stats.ability("str");
            String base = Math.max(1, dice) + (die == null || die.isEmpty() ? "d4" : die);
            if (mod != 0) base += (mod > 0 ? "+" : "") + mod;
            return base + (damageType == null || damageType.isEmpty() ? "" : " " + damageType);
        }
    }

    public static final class Iwr {
        public final String kind;
        public final String type;
        public final int value;
        public final String source;
        public final String note;

        Iwr(String kind, String type, int value, String source, String note) {
            this.kind = kind;
            this.type = type;
            this.value = value;
            this.source = source;
            this.note = note;
        }

        public String key() { return kind + ":" + type; }
    }

    public static List<Strike> grantedStrikes(CharacterState state, StatsState stats) {
        List<Strike> out = new ArrayList<>();
        RuleRuntime.Snapshot snapshot = RuntimeBridge.snapshot(state, stats);
        if (snapshot == null) return out;
        for (RuleItem item : snapshot.allItems()) {
            JSONArray elements = item.meta.optJSONArray("ruleElements");
            if (elements == null) continue;
            for (int i = 0; i < elements.length(); i++) {
                JSONObject rule = elements.optJSONObject(i);
                if (rule == null || !"Strike".equals(rule.optString("key"))) continue;
                if (!predicate(rule.opt("predicate"), snapshot, item, state)) continue;
                Strike strike = parseStrike(rule, item, state);
                if (strike != null) out.add(strike);
            }
        }
        return out;
    }

    public static List<Iwr> defenses(CharacterState state, StatsState stats) {
        LinkedHashMap<String, Iwr> result = new LinkedHashMap<>();
        RuleRuntime.Snapshot snapshot = RuntimeBridge.snapshot(state, stats);
        if (snapshot == null) return new ArrayList<>();
        for (RuleItem item : snapshot.allItems()) {
            JSONArray elements = item.meta.optJSONArray("ruleElements");
            if (elements == null) continue;
            for (int i = 0; i < elements.length(); i++) {
                JSONObject rule = elements.optJSONObject(i);
                if (rule == null) continue;
                String key = rule.optString("key", "");
                if (!("Resistance".equals(key) || "Weakness".equals(key) || "Immunity".equals(key))) continue;
                if (!predicate(rule.opt("predicate"), snapshot, item, state)) continue;
                String kind = "Resistance".equals(key) ? "resistance" : "Weakness".equals(key) ? "weakness" : "immunity";
                List<String> types = strings(rule.opt("type"));
                if (types.isEmpty()) types.add("all-damage");
                String mode = rule.optString("mode", "add").toLowerCase(Locale.ROOT);
                int value = "immunity".equals(kind) ? 0 : numeric(rule.opt("value"), state == null ? 1 : state.level, 0);
                String note = exceptionText(rule);
                for (String rawType : types) {
                    String type = slug(rawType);
                    if (type.isEmpty()) continue;
                    String mapKey = kind + ":" + type;
                    if ("remove".equals(mode)) {
                        result.remove(mapKey);
                        continue;
                    }
                    Iwr next = new Iwr(kind, type, Math.max(0, value), item.name, note);
                    Iwr old = result.get(mapKey);
                    if (old == null || "override".equals(mode) || next.value > old.value) result.put(mapKey, next);
                }
            }
        }
        return new ArrayList<>(result.values());
    }

    public static int strikeAttack(CharacterState state, StatsState stats, RuleItem cls, Strike strike) {
        if (state == null || stats == null || strike == null) return 0;
        RuleRuntime.Snapshot runtime = RuntimeBridge.snapshot(state, stats);
        int baseRank = DerivedStats.classMapRank(cls, "attacks", strike.category, 0);
        if (runtime != null) {
            baseRank = Math.max(baseRank, runtime.proficiency("attack:" + strike.category, baseRank));
            if (!strike.group.isEmpty()) baseRank = Math.max(baseRank, runtime.proficiency("attack:" + strike.group, baseRank));
            baseRank = Math.max(baseRank, runtime.proficiency("proficiency:" + strike.category, baseRank));
        }
        int ability;
        if (!strike.ability.isEmpty()) ability = stats.ability(shortAbility(strike.ability));
        else if (strike.ranged()) ability = stats.ability("dex");
        else if (strike.finesse()) ability = Math.max(stats.ability("str"), stats.ability("dex"));
        else ability = stats.ability("str");
        int modifier = runtime == null ? 0 : runtime.modifier("attack");
        return ability + DerivedStats.proficiency(baseRank, state.level) + strike.attackModifier + modifier;
    }

    public static int mapPenalty(boolean agile, int attackNumber) {
        if (attackNumber <= 1) return 0;
        if (attackNumber == 2) return agile ? -4 : -5;
        return agile ? -8 : -10;
    }

    private static Strike parseStrike(JSONObject rule, RuleItem source, CharacterState state) {
        JSONObject damage = rule.optJSONObject("damage");
        JSONObject base = damage == null ? null : damage.optJSONObject("base");
        int dice = base == null ? 1 : Math.max(1, numeric(base.opt("dice"), state == null ? 1 : state.level, 1));
        String die = base == null ? "d4" : base.optString("die", "d4");
        String damageType = base == null ? "bludgeoning" : base.optString("damageType", "bludgeoning");
        int damageModifier = base == null ? 0 : numeric(base.opt("modifier"), state == null ? 1 : state.level, 0);
        int attackModifier = numeric(rule.opt("attackModifier"), state == null ? 1 : state.level, 0);
        String label = rule.optString("label", source == null ? "Безоружная атака" : source.name);
        if (label.startsWith("PF2E.")) label = source == null ? "Безоружная атака" : source.name;
        String category = slug(rule.optString("category", "unarmed"));
        String group = slug(rule.optString("group", "brawling"));
        String ability = slug(rule.optString("ability", ""));
        int range = 0;
        JSONObject rangeObj = rule.optJSONObject("range");
        if (rangeObj != null) range = Math.max(numeric(rangeObj.opt("increment"), state == null ? 1 : state.level, 0), numeric(rangeObj.opt("max"), state == null ? 1 : state.level, 0));
        List<String> traits = strings(rule.opt("traits"));
        return new Strike(source == null ? "" : source.id, label, category, group, ability,
                attackModifier, dice, die, damageModifier, damageType, range, traits);
    }

    private static boolean predicate(Object raw, RuleRuntime.Snapshot snapshot, RuleItem source, CharacterState state) {
        if (raw == null || JSONObject.NULL.equals(raw)) return true;
        Set<String> options = new LinkedHashSet<>(snapshot.rollOptions());
        if (source != null) {
            String sourceSlug = slug(source.name);
            options.add("item:slug:" + sourceSlug);
            options.add("item:id:" + sourceSlug);
            for (String trait : source.traits) options.add("item:trait:" + slug(trait));
        }
        return predicateValue(raw, options, state == null ? 1 : state.level);
    }

    private static boolean predicateValue(Object raw, Set<String> options, int level) {
        if (raw == null || JSONObject.NULL.equals(raw)) return true;
        if (raw instanceof String) {
            String value = (String) raw;
            if (value.startsWith("not:")) return !options.contains(value.substring(4));
            return options.contains(value);
        }
        if (raw instanceof JSONArray) {
            JSONArray a = (JSONArray) raw;
            for (int i = 0; i < a.length(); i++) if (!predicateValue(a.opt(i), options, level)) return false;
            return true;
        }
        if (!(raw instanceof JSONObject)) return false;
        JSONObject o = (JSONObject) raw;
        if (o.has("and")) return predicateValue(o.opt("and"), options, level);
        if (o.has("not")) return !predicateValue(o.opt("not"), options, level);
        if (o.has("or")) return any(o.opt("or"), options, level);
        if (o.has("nor")) return !any(o.opt("nor"), options, level);
        if (o.has("gte")) return compare(o.opt("gte"), level, true);
        if (o.has("lte")) return compare(o.opt("lte"), level, false);
        return false;
    }

    private static boolean any(Object raw, Set<String> options, int level) {
        if (raw instanceof JSONArray) {
            JSONArray a = (JSONArray) raw;
            for (int i = 0; i < a.length(); i++) if (predicateValue(a.opt(i), options, level)) return true;
            return false;
        }
        return predicateValue(raw, options, level);
    }

    private static boolean compare(Object raw, int level, boolean gte) {
        if (!(raw instanceof JSONArray)) return false;
        JSONArray a = (JSONArray) raw;
        if (a.length() < 2) return false;
        Object left = a.opt(0), right = a.opt(1);
        int l = "self:level".equals(String.valueOf(left)) ? level : numeric(left, level, Integer.MIN_VALUE);
        int r = "self:level".equals(String.valueOf(right)) ? level : numeric(right, level, Integer.MIN_VALUE);
        if (l == Integer.MIN_VALUE || r == Integer.MIN_VALUE) return false;
        return gte ? l >= r : l <= r;
    }

    private static int numeric(Object raw, int level, int fallback) {
        if (raw == null || JSONObject.NULL.equals(raw)) return fallback;
        if (raw instanceof Number) return ((Number) raw).intValue();
        String text = String.valueOf(raw).trim().replace("@actor.level", String.valueOf(level)).replace("actor.level", String.valueOf(level));
        try { return Integer.parseInt(text); } catch (Exception ignored) { }
        Matcher maxFloor = MAX_FLOOR.matcher(String.valueOf(raw));
        if (maxFloor.find()) return Math.max(Integer.parseInt(maxFloor.group(1)), level / Math.max(1, Integer.parseInt(maxFloor.group(2))));
        Matcher floor = FLOOR_DIV.matcher(String.valueOf(raw));
        if (floor.find()) return level / Math.max(1, Integer.parseInt(floor.group(1)));
        Matcher ceil = CEIL_DIV.matcher(String.valueOf(raw));
        if (ceil.find()) { int d = Math.max(1, Integer.parseInt(ceil.group(1))); return (level + d - 1) / d; }
        return fallback;
    }

    private static String exceptionText(JSONObject rule) {
        List<String> parts = new ArrayList<>();
        List<String> exceptions = strings(rule.opt("exceptions"));
        if (!exceptions.isEmpty()) parts.add("кроме: " + String.join(", ", exceptions));
        List<String> doubled = strings(rule.opt("doubleVs"));
        if (!doubled.isEmpty()) parts.add("удвоено против: " + String.join(", ", doubled));
        return String.join(" • ", parts);
    }

    private static List<String> strings(Object raw) {
        List<String> out = new ArrayList<>();
        if (raw == null || JSONObject.NULL.equals(raw)) return out;
        if (raw instanceof JSONArray) {
            JSONArray a = (JSONArray) raw;
            for (int i = 0; i < a.length(); i++) {
                String value = String.valueOf(a.opt(i));
                if (!value.isEmpty() && !"null".equals(value)) out.add(value);
            }
        } else {
            String value = String.valueOf(raw);
            if (!value.isEmpty() && !"null".equals(value)) out.add(value);
        }
        return out;
    }

    private static String shortAbility(String ability) {
        String a = slug(ability);
        if (a.startsWith("str")) return "str";
        if (a.startsWith("dex")) return "dex";
        if (a.startsWith("con")) return "con";
        if (a.startsWith("int")) return "int";
        if (a.startsWith("wis")) return "wis";
        if (a.startsWith("cha")) return "cha";
        return a;
    }

    private static String slug(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT).trim().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }

    private static boolean has(List<String> values, String wanted) {
        for (String value : values) if (wanted.equalsIgnoreCase(value)) return true;
        return false;
    }
}
