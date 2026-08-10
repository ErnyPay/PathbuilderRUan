#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RUNTIME = ROOT / 'app/src/main/java/ru/gran/edge2e/RuleRuntime.java'
DERIVED = ROOT / 'app/src/main/java/ru/gran/edge2e/DerivedStats.java'
V2 = ROOT / 'app/src/main/java/ru/gran/edge2e/MainActivityV2.java'


def replace_once(text, old, new, label):
    if old not in text:
        raise SystemExit(f'runtime 2.0 patch missing anchor: {label}')
    return text.replace(old, new, 1)


r = RUNTIME.read_text(encoding='utf-8')

r = replace_once(r,
'''    public static final class Snapshot {
        private final LinkedHashMap<String, RuleItem> items = new LinkedHashMap<>();''',
'''    private static final class MartialRule {
        final String kind;
        final Object definition;
        final String sameAs;
        final int rank;
        final int maxRank;
        MartialRule(String kind, Object definition, String sameAs, int rank, int maxRank) {
            this.kind = kind;
            this.definition = definition;
            this.sameAs = sameAs;
            this.rank = rank;
            this.maxRank = maxRank;
        }
    }

    public static final class Snapshot {
        private final LinkedHashMap<String, RuleItem> items = new LinkedHashMap<>();''', 'Snapshot class')

r = replace_once(r,
'''        private final LinkedHashMap<String, Integer> modifierEffects = new LinkedHashMap<>();
        private final LinkedHashMap<String, String> modifierSelectors = new LinkedHashMap<>();
        public final List<String> warnings = new ArrayList<>();''',
'''        private final LinkedHashMap<String, Integer> modifierEffects = new LinkedHashMap<>();
        private final LinkedHashMap<String, String> modifierSelectors = new LinkedHashMap<>();
        private final LinkedHashMap<String, Integer> baseSpeeds = new LinkedHashMap<>();
        private final List<MartialRule> martialRules = new ArrayList<>();
        public final List<String> warnings = new ArrayList<>();''', 'Snapshot runtime fields')

r = replace_once(r,
'''        public int modifier(String selector) {
            int total = 0;
            for (Map.Entry<String, String> entry : modifierSelectors.entrySet()) {
                if (selector.equals(entry.getValue())) total += modifierEffects.get(entry.getKey());
            }
            return total;
        }

        public boolean hasName(String value) {''',
'''        public int modifier(String selector) {
            int total = 0;
            for (Map.Entry<String, String> entry : modifierSelectors.entrySet()) {
                if (selector.equals(entry.getValue())) total += modifierEffects.get(entry.getKey());
            }
            return total;
        }

        public int baseSpeed(String type, int fallback) {
            Integer value = baseSpeeds.get(slug(type));
            return value == null || value <= 0 ? fallback : value;
        }

        public int martialRank(RuleItem item, String kind, int fallback) {
            if (item == null) return fallback;
            int result = fallback;
            for (MartialRule rule : martialRules) {
                if (!rule.kind.equals(kind) || !martialDefinitionMatches(rule.definition, item)) continue;
                int candidate = rule.rank;
                if (!rule.sameAs.isEmpty()) {
                    String prefix = "attack".equals(kind) ? "attack:" : "defense:";
                    candidate = Math.max(candidate, proficiency(prefix + slug(rule.sameAs), candidate));
                }
                if (rule.maxRank > 0) candidate = Math.min(candidate, rule.maxRank);
                result = Math.max(result, candidate);
            }
            return result;
        }

        public boolean hasName(String value) {''', 'Snapshot mechanic accessors')

r = replace_once(r,
'''                case "FlatModifier": applyFlatModifier(state, out, item, rule, i); break;
                default: break;''',
'''                case "FlatModifier": applyFlatModifier(state, out, item, rule, i); break;
                case "BaseSpeed": applyBaseSpeed(state, out, item, rule); break;
                case "MartialProficiency": applyMartialProficiency(state, out, item, rule); break;
                default: break;''', 'rule switch')

r = replace_once(r,
'''    private static boolean predicateMatches(Object predicate, RuleItem item, CharacterState state, Snapshot out) {''',
'''    private static void applyBaseSpeed(CharacterState state, Snapshot out, RuleItem item, JSONObject rule) {
        String selector = slug(resolveInjected(rule.optString("selector", "land"), item, state).replace("-speed", ""));
        if (selector.isEmpty()) selector = "land";
        String raw = resolveInjected(String.valueOf(rule.opt("value")), item, state);
        int value = intValue(raw, 0);
        if (value <= 0) return;
        Integer old = out.baseSpeeds.get(selector);
        if (old == null || value > old) out.baseSpeeds.put(selector, value);
    }

    private static void applyMartialProficiency(CharacterState state, Snapshot out, RuleItem item, JSONObject rule) {
        String kind = slug(rule.optString("kind", "attack"));
        if (!"attack".equals(kind) && !"defense".equals(kind)) return;
        Object definition = rule.opt("definition");
        if (definition == null || JSONObject.NULL.equals(definition)) return;
        int rank = Math.max(1, Math.min(4, intValue(resolveInjected(String.valueOf(rule.opt("value")), item, state), 1)));
        String sameAs = slug(resolveInjected(rule.optString("sameAs", ""), item, state));
        int maxRank = rankNameValue(rule.optString("maxRank", ""));
        out.martialRules.add(new MartialRule(kind, definition, sameAs, rank, maxRank));
    }

    private static boolean martialDefinitionMatches(Object definition, RuleItem item) {
        Set<String> options = itemRollOptions(item);
        return optionPredicateMatches(definition, options);
    }

    private static Set<String> itemRollOptions(RuleItem item) {
        Set<String> options = new LinkedHashSet<>();
        if (item == null) return options;
        String category = slug(item.meta.optString("itemCategory", ""));
        String group = slug(item.meta.optString("group", ""));
        String baseItem = slug(item.meta.optString("baseItem", ""));
        String itemType = slug(item.meta.optString("itemType", item.subtype));
        String itemSlug = slug(item.meta.optString("slug", item.name));
        if (!category.isEmpty()) options.add("item:category:" + category);
        if (!group.isEmpty()) options.add("item:group:" + group);
        if (!baseItem.isEmpty()) { options.add("item:base:" + baseItem); options.add("item:base-item:" + baseItem); }
        if (!itemType.isEmpty()) options.add("item:type:" + itemType);
        if (!itemSlug.isEmpty()) { options.add("item:slug:" + itemSlug); options.add("item:id:" + itemSlug); }
        for (String trait : item.traits) options.add("item:trait:" + slug(trait));
        return options;
    }

    private static boolean optionPredicateMatches(Object predicate, Set<String> options) {
        if (predicate == null || JSONObject.NULL.equals(predicate)) return true;
        if (predicate instanceof String) {
            String value = (String) predicate;
            if (value.startsWith("not:")) return !options.contains(value.substring(4));
            return options.contains(value);
        }
        if (predicate instanceof JSONArray) {
            JSONArray a = (JSONArray) predicate;
            for (int i = 0; i < a.length(); i++) if (!optionPredicateMatches(a.opt(i), options)) return false;
            return true;
        }
        if (predicate instanceof JSONObject) {
            JSONObject o = (JSONObject) predicate;
            if (o.has("and")) return optionPredicateMatches(o.opt("and"), options);
            if (o.has("or")) {
                Object raw = o.opt("or");
                if (raw instanceof JSONArray) {
                    JSONArray a = (JSONArray) raw;
                    for (int i = 0; i < a.length(); i++) if (optionPredicateMatches(a.opt(i), options)) return true;
                    return false;
                }
                return optionPredicateMatches(raw, options);
            }
            if (o.has("not")) return !optionPredicateMatches(o.opt("not"), options);
            if (o.has("nor")) {
                Object raw = o.opt("nor");
                if (raw instanceof JSONArray) {
                    JSONArray a = (JSONArray) raw;
                    for (int i = 0; i < a.length(); i++) if (optionPredicateMatches(a.opt(i), options)) return false;
                    return true;
                }
                return !optionPredicateMatches(raw, options);
            }
            return false;
        }
        return false;
    }

    private static int rankNameValue(String rank) {
        switch (slug(rank)) {
            case "trained": return 1;
            case "expert": return 2;
            case "master": return 3;
            case "legendary": return 4;
            default: return 0;
        }
    }

    private static boolean predicateMatches(Object predicate, RuleItem item, CharacterState state, Snapshot out) {''', 'new rule handlers')

RUNTIME.write_text(r, encoding='utf-8')


d = DERIVED.read_text(encoding='utf-8')
d = replace_once(d,
'''        if (runtime != null) {
            rank = Math.max(rank, runtime.proficiency("defense:" + category, rank));
            rank = Math.max(rank, runtime.proficiency("proficiency:" + category, rank));
        }''',
'''        if (runtime != null) {
            rank = Math.max(rank, runtime.proficiency("defense:" + category, rank));
            rank = Math.max(rank, runtime.proficiency("proficiency:" + category, rank));
            rank = runtime.martialRank(armor, "defense", rank);
        }''', 'armor martial proficiency')

d = replace_once(d,
'''    public static int speed(StatsState s, RuleItem ancestry, RuleItem armor) {
        int speed = ancestry == null ? 25 : ancestry.meta.optInt("speed", 25);''',
'''    public static int speed(CharacterState c, StatsState s, RuleItem ancestry, RuleItem armor) {
        int speed = ancestry == null ? 25 : ancestry.meta.optInt("speed", 25);
        RuleRuntime.Snapshot runtime = RuntimeBridge.snapshot(c, s);
        if (runtime != null) speed = runtime.baseSpeed("land", speed);''', 'derived land speed')

d = replace_once(d,
'''        if (runtime != null) {
            rank = Math.max(rank, runtime.proficiency("attack:" + category, rank));
            rank = Math.max(rank, runtime.proficiency("proficiency:" + category, rank));
        }''',
'''        if (runtime != null) {
            rank = Math.max(rank, runtime.proficiency("attack:" + category, rank));
            rank = Math.max(rank, runtime.proficiency("proficiency:" + category, rank));
            rank = runtime.martialRank(weapon, "attack", rank);
        }''', 'weapon martial proficiency')
DERIVED.write_text(d, encoding='utf-8')

v = V2.read_text(encoding='utf-8')
v = v.replace('DerivedStats.speed(stats, ancestryItem(), equippedArmor())', 'DerivedStats.speed(state, stats, ancestryItem(), equippedArmor())')
V2.write_text(v, encoding='utf-8')

print('Extended Gran 2e runtime: BaseSpeed + MartialProficiency')
