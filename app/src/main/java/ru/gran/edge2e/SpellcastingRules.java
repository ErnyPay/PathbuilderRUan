package ru.gran.edge2e;

import java.util.Locale;
import java.util.Set;

/** Class spellcasting profiles used by the PLAY spell-slot UI. */
public final class SpellcastingRules {
    private SpellcastingRules() { }

    public static final String PREPARED = "prepared";
    public static final String SPONTANEOUS = "spontaneous";
    public static final String SPECIAL = "special";

    public static final class Profile {
        public final String source;
        public final String mode;
        public final String tradition;
        public final String ability;
        public final int cantrips;
        public final int firstRankSlots;
        public final int normalSlots;
        public final boolean wizardCurriculum;
        public final boolean signatures;
        public final boolean supported;
        public final String note;

        Profile(String source, String mode, String tradition, String ability, int cantrips,
                int firstRankSlots, int normalSlots, boolean wizardCurriculum,
                boolean signatures, boolean supported, String note) {
            this.source=source; this.mode=mode; this.tradition=tradition; this.ability=ability;
            this.cantrips=cantrips; this.firstRankSlots=firstRankSlots; this.normalSlots=normalSlots;
            this.wizardCurriculum=wizardCurriculum; this.signatures=signatures;
            this.supported=supported; this.note=note == null ? "" : note;
        }

        /** Standard full-caster progression: 2/3 (or 3/4 for Sorcerer) per rank, 1 at rank 10. */
        public int slots(int level, int rank) {
            if (rank < 1 || rank > 10 || level < 1) return 0;
            if (rank == 10) return level >= 19 ? 1 : 0;
            int unlock = rank * 2 - 1;
            if (level < unlock) return 0;
            return level == unlock ? firstRankSlots : normalSlots;
        }

        public int bonusPreparedSlots(int level, int rank) {
            if (!wizardCurriculum || rank < 1 || rank > 9) return 0;
            return slots(level, rank) > 0 ? 1 : 0;
        }

        public int totalPreparedSlots(int level, int rank) { return slots(level, rank) + bonusPreparedSlots(level, rank); }
        public int maxRank(int level) { return Math.min(10, Math.max(1, (level + 1) / 2)); }
    }

    public static Profile resolve(CharacterState state, RuleRuntime.Snapshot runtime) {
        if (state == null || state.className == null || state.className.trim().isEmpty()) return null;
        String cls = state.className.trim().toLowerCase(Locale.ROOT);
        String tradition = tradition(runtime);
        switch (cls) {
            case "wizard":
                return new Profile("Wizard", PREPARED, "arcane", "int", 5, 2, 3, true, false, true,
                        "Подготовка из книги заклинаний; дополнительный curriculum-slot отслеживается отдельно.");
            case "bard":
                return new Profile("Bard", SPONTANEOUS, "occult", "cha", 5, 2, 3, false, true, true,
                        "Заклинания из репертуара; с 3-го уровня — по одному signature spell каждого доступного ранга.");
            case "witch":
                return new Profile("Witch", PREPARED, tradition, "int", 5, 2, 3, false, false, !tradition.isEmpty(),
                        tradition.isEmpty() ? "Сначала выбери покровителя и традицию." : "Подготовка через фамильяра.");
            case "druid":
                return new Profile("Druid", PREPARED, "primal", "wis", 5, 2, 3, false, false, true, "Подготовленные природные заклинания.");
            case "cleric":
                return new Profile("Cleric", PREPARED, "divine", "wis", 5, 2, 3, false, false, true,
                        "Основные подготовленные слоты. Divine Font будет отдельным источником бонусных слотов.");
            case "sorcerer":
                return new Profile("Sorcerer", SPONTANEOUS, tradition, "cha", 5, 3, 4, false, true, !tradition.isEmpty(),
                        tradition.isEmpty() ? "Сначала выбери bloodline/tradition." : "Репертуар крови; signature spells поддерживаются.");
            case "oracle":
            case "psychic":
            case "animist":
            case "magus":
            case "summoner":
                return new Profile(state.className, SPECIAL, tradition, "", 0, 0, 0, false, false, false,
                        "У этого класса особая прогрессия. Gran не подменяет её стандартной таблицей; нужен специализированный caster-модуль.");
            default:
                if (runtime != null && runtime.hasSpellcasting()) {
                    return new Profile(state.className, SPECIAL, tradition, "", 0, 0, 0, false, false, false,
                            "Источник магии найден в графе правил, но его прогрессия пока не распознана как стандартный класс.");
                }
                return null;
        }
    }

    public static boolean spellAllowed(Profile p, RuleItem spell, int slotRank) {
        if (p == null || spell == null || slotRank < 1) return false;
        if (spell.level < 1 || spell.level > slotRank) return false;
        if (p.tradition == null || p.tradition.isEmpty()) return false;
        org.json.JSONArray traditions = spell.meta.optJSONArray("traditions");
        if (traditions == null) return false;
        for (int i=0;i<traditions.length();i++) if (p.tradition.equalsIgnoreCase(traditions.optString(i,""))) return true;
        return false;
    }

    public static String modeLabel(Profile p) {
        if (p == null) return "нет";
        if (PREPARED.equals(p.mode)) return "подготовленные";
        if (SPONTANEOUS.equals(p.mode)) return "спонтанные";
        return "особая магия";
    }

    public static String traditionLabel(String t) {
        if (t == null) return "—";
        switch (t.toLowerCase(Locale.ROOT)) {
            case "arcane": return "арканная";
            case "divine": return "сакральная";
            case "occult": return "оккультная";
            case "primal": return "природная";
            default: return t;
        }
    }

    private static String tradition(RuleRuntime.Snapshot runtime) {
        if (runtime == null) return "";
        Set<String> traditions = runtime.spellTraditions();
        return traditions.size() == 1 ? traditions.iterator().next() : "";
    }
}
