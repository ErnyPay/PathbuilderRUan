package ru.gran.edge2e;

import java.util.Locale;
import java.util.Set;

/** Class spellcasting profiles used by the PLAY spell-slot UI. */
public final class SpellcastingRules {
    private SpellcastingRules() { }

    public static final String PREPARED = "prepared";
    public static final String SPONTANEOUS = "spontaneous";
    public static final String SPECIAL = "special";
    public static final String STANDARD = "standard";
    public static final String BOUNDED = "bounded";

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
        public final String progression;

        Profile(String source, String mode, String tradition, String ability, int cantrips,
                int firstRankSlots, int normalSlots, boolean wizardCurriculum,
                boolean signatures, boolean supported, String note) {
            this(source, mode, tradition, ability, cantrips, firstRankSlots, normalSlots,
                    wizardCurriculum, signatures, supported, note, STANDARD);
        }

        Profile(String source, String mode, String tradition, String ability, int cantrips,
                int firstRankSlots, int normalSlots, boolean wizardCurriculum,
                boolean signatures, boolean supported, String note, String progression) {
            this.source=source; this.mode=mode; this.tradition=tradition; this.ability=ability;
            this.cantrips=cantrips; this.firstRankSlots=firstRankSlots; this.normalSlots=normalSlots;
            this.wizardCurriculum=wizardCurriculum; this.signatures=signatures;
            this.supported=supported; this.note=note == null ? "" : note;
            this.progression = progression == null ? STANDARD : progression;
        }

        public int slots(int level, int rank) {
            if (rank < 1 || rank > 10 || level < 1) return 0;
            if (BOUNDED.equals(progression)) return boundedSlots(level, rank);
            if (rank == 10) return level >= 19 ? 1 : 0;
            int unlock = rank * 2 - 1;
            if (level < unlock) return 0;
            return level == unlock ? firstRankSlots : normalSlots;
        }

        private int boundedSlots(int level, int rank) {
            int max = maxRank(level);
            if (rank > max || rank < Math.max(1, max - 1)) return 0;
            if (level == 1 && rank == 1) return 1;
            if (level == 2 && rank == 1) return 2;
            boolean newRankLevel = (level % 2) == 1;
            if (rank == max) return newRankLevel ? 1 : 2;
            return 2;
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
                        "Заклинания из репертуара; signature spells поддерживаются.");
            case "witch":
                return new Profile("Witch", PREPARED, tradition, "int", 5, 2, 3, false, false, !tradition.isEmpty(),
                        tradition.isEmpty() ? "Сначала выбери покровителя и традицию." : "Подготовка через фамильяра.");
            case "druid":
                return new Profile("Druid", PREPARED, "primal", "wis", 5, 2, 3, false, false, true, "Подготовленные природные заклинания.");
            case "cleric":
                return new Profile("Cleric", PREPARED, "divine", "wis", 5, 2, 3, false, false, true,
                        "Основные подготовленные слоты; фокусные и дополнительные источники отображаются отдельными секциями.");
            case "sorcerer":
                return new Profile("Sorcerer", SPONTANEOUS, tradition, "cha", 5, 3, 4, false, true, !tradition.isEmpty(),
                        tradition.isEmpty() ? "Сначала выбери bloodline/tradition." : "Репертуар крови; signature spells поддерживаются.");
            case "oracle":
                return new Profile("Oracle", SPONTANEOUS, "divine", "cha", 5, 3, 3, false, true, true,
                        "Сакральный репертуар, signature spells и revelation/focus spells.");
            case "psychic":
                return new Profile("Psychic", SPONTANEOUS, "occult", "int/cha", 3, 1, 2, false, true, true,
                        "Оккультный репертуар с уменьшенным числом слотов; psi cantrips и amps отображаются через фокусные/выбранные заклинания.");
            case "magus":
                return new Profile("Magus", PREPARED, "arcane", "int", 5, 1, 2, false, false, true,
                        "Ограниченная подготовленная магия: одновременно поддерживаются только два высших ранга слотов.", BOUNDED);
            case "summoner":
                return new Profile("Summoner", SPONTANEOUS, tradition, "cha", 5, 1, 2, false, true, !tradition.isEmpty(),
                        tradition.isEmpty() ? "Сначала выбери эйдолона, определяющего традицию." : "Ограниченный репертуар: одновременно доступны только два высших ранга слотов.", BOUNDED);
            case "animist":
                return new Profile("Animist", PREPARED, "divine", "wis", 2, 1, 2, false, false, true,
                        "Основные подготовленные слоты анимиста; заклинания явлений, vessel/focus spells и выбранные apparition spells отображаются рядом как дополнительные источники.");
            default:
                if (runtime != null && runtime.hasSpellcasting()) {
                    return new Profile(state.className, SPECIAL, tradition, "", 0, 0, 0, false, false, false,
                            "Источник магии найден в графе правил, но его прогрессия требует отдельного профиля.");
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
        if ("Animist".equalsIgnoreCase(p.source)) return "подготовленные + явления";
        if (BOUNDED.equals(p.progression) && PREPARED.equals(p.mode)) return "ограниченные подготовленные";
        if (BOUNDED.equals(p.progression) && SPONTANEOUS.equals(p.mode)) return "ограниченный репертуар";
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
