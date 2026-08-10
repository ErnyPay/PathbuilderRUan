package ru.gran.edge2e;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class RuNames {
    private static final Map<String, String> MAP;
    static {
        Map<String, String> m = new HashMap<>();
        put(m, "Alchemist", "Алхимик"); put(m, "Animist", "Анимист"); put(m, "Barbarian", "Варвар");
        put(m, "Bard", "Бард"); put(m, "Champion", "Чемпион"); put(m, "Cleric", "Клирик");
        put(m, "Commander", "Командир"); put(m, "Druid", "Друид"); put(m, "Exemplar", "Экземпляр");
        put(m, "Fighter", "Воин"); put(m, "Guardian", "Страж"); put(m, "Gunslinger", "Стрелок");
        put(m, "Inventor", "Изобретатель"); put(m, "Investigator", "Следователь"); put(m, "Kineticist", "Кинетик");
        put(m, "Magus", "Магус"); put(m, "Monk", "Монах"); put(m, "Oracle", "Оракул");
        put(m, "Psychic", "Психик"); put(m, "Ranger", "Следопыт"); put(m, "Rogue", "Плут");
        put(m, "Sorcerer", "Чародей"); put(m, "Summoner", "Призыватель"); put(m, "Swashbuckler", "Сорвиголова");
        put(m, "Thaumaturge", "Тауматург"); put(m, "Witch", "Ведьма"); put(m, "Wizard", "Волшебник");

        put(m, "Human", "Человек"); put(m, "Dwarf", "Дварф"); put(m, "Elf", "Эльф"); put(m, "Gnome", "Гном");
        put(m, "Goblin", "Гоблин"); put(m, "Halfling", "Полурослик"); put(m, "Orc", "Орк"); put(m, "Kobold", "Кобольд");
        put(m, "Leshy", "Леший"); put(m, "Catfolk", "Кошколюд"); put(m, "Tengu", "Тэнгу"); put(m, "Android", "Андроид");

        put(m, "Frightened", "Испуган"); put(m, "Sickened", "Тошнота"); put(m, "Clumsy", "Неуклюж");
        put(m, "Enfeebled", "Ослаблен"); put(m, "Stupefied", "Одурманен"); put(m, "Slowed", "Замедлен");
        put(m, "Quickened", "Ускорен"); put(m, "Dying", "При смерти"); put(m, "Wounded", "Ранен");
        put(m, "Prone", "Лежит"); put(m, "Grabbed", "Схвачен"); put(m, "Restrained", "Обездвижен");
        put(m, "Off-Guard", "Застигнут врасплох"); put(m, "Concealed", "Скрыт"); put(m, "Hidden", "Спрятан");

        put(m, "Longsword", "Длинный меч"); put(m, "Shortsword", "Короткий меч"); put(m, "Dagger", "Кинжал");
        put(m, "Greatsword", "Двуручный меч"); put(m, "Longbow", "Длинный лук"); put(m, "Shortbow", "Короткий лук");
        put(m, "Chain Mail", "Кольчуга"); put(m, "Leather Armor", "Кожаный доспех"); put(m, "Plate Armor", "Латный доспех");
        put(m, "Shield", "Щит"); put(m, "Healing Potion", "Зелье исцеления");

        put(m, "Force Barrage", "Силовой залп"); put(m, "Electric Arc", "Электрическая дуга");
        put(m, "Fireball", "Огненный шар"); put(m, "Heal", "Исцеление"); put(m, "Sure Strike", "Верный удар");
        put(m, "Fear", "Страх"); put(m, "Fly", "Полёт"); put(m, "Invisibility", "Невидимость");

        put(m, "Vicious Swing", "Мощный взмах"); put(m, "Sudden Charge", "Внезапный рывок");
        put(m, "Toughness", "Стойкость"); put(m, "Battle Medicine", "Боевая медицина");
        put(m, "Natural Ambition", "Природная амбициозность"); put(m, "Reactive Strike", "Ответный удар");
        put(m, "Shield Block", "Блок щитом"); put(m, "Weapon Specialization", "Специализация на оружии");
        MAP = Collections.unmodifiableMap(m);
    }

    private RuNames() { }
    private static void put(Map<String,String> m, String en, String ru) { m.put(en.toLowerCase(Locale.ROOT), ru); }

    public static String display(String english) {
        if (english == null) return "";
        String ru = MAP.get(english.toLowerCase(Locale.ROOT));
        return ru == null ? english : ru + " (" + english + ")";
    }

    public static String shortName(String english) {
        if (english == null) return "";
        String ru = MAP.get(english.toLowerCase(Locale.ROOT));
        return ru == null ? english : ru;
    }

    public static boolean matches(String english, String query) {
        if (query == null || query.trim().isEmpty()) return true;
        String q = query.toLowerCase(Locale.ROOT).trim();
        String en = english == null ? "" : english.toLowerCase(Locale.ROOT);
        String ru = MAP.getOrDefault(en, "").toLowerCase(Locale.ROOT);
        return en.contains(q) || ru.contains(q);
    }
}
