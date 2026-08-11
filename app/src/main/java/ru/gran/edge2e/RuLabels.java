package ru.gran.edge2e;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Russian presentation labels for PF2e slugs. Canonical slugs remain untouched in the rules engine. */
public final class RuLabels {
    private RuLabels() { }

    public static String rarity(String value) {
        if (value == null) return "";
        switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "common": return "обычный";
            case "uncommon": return "необычный";
            case "rare": return "редкий";
            case "unique": return "уникальный";
            default: return value;
        }
    }

    public static String type(String value) {
        if (value == null) return "";
        switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "ancestry": return "род";
            case "heritage": return "наследие";
            case "background": return "предыстория";
            case "class": return "класс";
            case "class-feature": return "особенность класса";
            case "feat": return "фит";
            case "general": return "общий фит";
            case "skill": return "фит навыка";
            case "archetype": return "архетип";
            case "spell": return "заклинание";
            case "focus": return "фокусное заклинание";
            case "ritual": return "ритуал";
            case "weapon": return "оружие";
            case "armor": return "броня";
            case "shield": return "щит";
            case "equipment": return "предмет";
            case "consumable": return "расходник";
            case "ammo": case "ammunition": return "боеприпасы";
            case "condition": return "состояние";
            case "action": return "действие";
            default: return value;
        }
    }

    public static String traits(List<String> traits) {
        if (traits == null || traits.isEmpty()) return "";
        ArrayList<String> out = new ArrayList<>();
        for (String trait : traits) {
            String v = trait(trait);
            if (!v.isEmpty()) out.add(v);
        }
        return String.join(" • ", out);
    }

    public static String trait(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "";
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.startsWith("fatal-")) return "смертельное " + s.substring(6).toUpperCase(Locale.ROOT);
        if (s.startsWith("deadly-")) return "убойное " + s.substring(7).toUpperCase(Locale.ROOT);
        if (s.startsWith("thrown-")) return "метательное " + s.substring(7) + " фт";
        if (s.startsWith("reach-")) return "длинное " + s.substring(6) + " фт";
        if (s.startsWith("versatile-")) return "универсальное (" + damageType(s.substring(10)) + ")";
        if (s.startsWith("two-hand-")) return "двуручное " + s.substring(9).toUpperCase(Locale.ROOT);
        if (s.startsWith("volley-")) return "залповое " + s.substring(7) + " фт";
        if (s.startsWith("capacity-")) return "ёмкость " + s.substring(9);
        if (s.startsWith("scatter-")) return "рассеивание " + s.substring(8) + " фт";
        if (s.startsWith("modular-")) return "модульное (" + damageType(s.substring(8)) + ")";
        switch (s) {
            case "agile": return "быстрое";
            case "alchemical": return "алхимический";
            case "air": return "воздух";
            case "acid": return "кислота";
            case "arcane": return "арканный";
            case "attack": return "атака";
            case "auditory": return "слуховой";
            case "backswing": return "обратный замах";
            case "backstabber": return "удар в спину";
            case "beast": return "зверь";
            case "brutal": return "грубое";
            case "cantrip": return "чары";
            case "cleric": return "клирик";
            case "cold": return "холод";
            case "concentrate": return "концентрация";
            case "concussive": return "ударное";
            case "construct": return "конструкт";
            case "curse": return "проклятие";
            case "darkness": return "тьма";
            case "death": return "смерть";
            case "detection": return "обнаружение";
            case "divine": return "сакральный";
            case "earth": return "земля";
            case "electricity": return "электричество";
            case "emotion": return "эмоция";
            case "evocation": return "эвокация";
            case "fear": return "страх";
            case "finesse": return "точное";
            case "fire": return "огонь";
            case "force": return "сила";
            case "forceful": return "силовое";
            case "focus": return "фокусное";
            case "fortune": return "удача";
            case "free-hand": return "свободная рука";
            case "grapple": return "захват";
            case "healing": return "исцеление";
            case "humanoid": return "гуманоид";
            case "incapacitation": return "нейтрализация";
            case "injury": return "ранение";
            case "illusion": return "иллюзия";
            case "light": return "свет";
            case "magical": return "магический";
            case "manipulate": return "манипуляция";
            case "mental": return "ментальный";
            case "mindless": return "неразумный";
            case "morph": return "трансформация";
            case "nonlethal": return "несмертельное";
            case "occult": return "оккультный";
            case "parry": return "парирование";
            case "poison": return "яд";
            case "polymorph": return "полиморф";
            case "primal": return "природный";
            case "propulsive": return "тяговое";
            case "reach": return "длинное";
            case "repeating": return "многозарядное";
            case "ranged-trip": return "дистанционное опрокидывание";
            case "ritual": return "ритуал";
            case "shove": return "толчок";
            case "sonic": return "звук";
            case "spell": return "заклинание";
            case "spirit": return "дух";
            case "sweep": return "размашистое";
            case "tethered": return "привязанное";
            case "trip": return "опрокидывание";
            case "unarmed": return "безоружное";
            case "visual": return "зрительный";
            case "water": return "вода";
            default:
                String translated = RuNames.shortName(raw);
                return translated.equals(raw) ? raw : translated;
        }
    }

    private static String damageType(String value) {
        if (value == null) return "";
        switch (value.toLowerCase(Locale.ROOT)) {
            case "b": case "bludgeoning": return "дробящий";
            case "p": case "piercing": return "колющий";
            case "s": case "slashing": return "рубящий";
            default: return value;
        }
    }
}
