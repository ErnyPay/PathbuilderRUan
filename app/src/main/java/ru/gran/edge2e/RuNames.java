package ru.gran.edge2e;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Russian presentation dictionary. English canonical names stay in the rule engine. */
public final class RuNames {
    private static final Map<String, String> MAP = new HashMap<>();
    private static final Set<String> CORE_KEYS = new HashSet<>();
    private static final Map<String, String> DESCRIPTIONS = new HashMap<>();
    private static final Map<String, String> PREREQUISITES = new HashMap<>();
    private static volatile boolean loaded = false;

    static {
        putCore("Alchemist", "Алхимик"); putCore("Animist", "Анимист"); putCore("Barbarian", "Варвар");
        putCore("Bard", "Бард"); putCore("Champion", "Чемпион"); putCore("Cleric", "Клирик");
        putCore("Commander", "Командир"); putCore("Druid", "Друид"); putCore("Exemplar", "Экземпляр");
        putCore("Fighter", "Воин"); putCore("Guardian", "Страж"); putCore("Gunslinger", "Стрелок");
        putCore("Inventor", "Изобретатель"); putCore("Investigator", "Следователь"); putCore("Kineticist", "Кинетик");
        putCore("Magus", "Магус"); putCore("Monk", "Монах"); putCore("Oracle", "Оракул");
        putCore("Psychic", "Психик"); putCore("Ranger", "Следопыт"); putCore("Rogue", "Плут");
        putCore("Sorcerer", "Чародей"); putCore("Summoner", "Призыватель"); putCore("Swashbuckler", "Сорвиголова");
        putCore("Thaumaturge", "Тауматург"); putCore("Witch", "Ведьма"); putCore("Wizard", "Волшебник");
        putCore("Human", "Человек"); putCore("Dwarf", "Дварф"); putCore("Elf", "Эльф"); putCore("Gnome", "Гном");
        putCore("Goblin", "Гоблин"); putCore("Halfling", "Полурослик"); putCore("Orc", "Орк"); putCore("Kobold", "Кобольд");
        putCore("Leshy", "Леший"); putCore("Catfolk", "Кошколюд"); putCore("Tengu", "Тэнгу"); putCore("Android", "Андроид");
        putCore("Frightened", "Испуган"); putCore("Sickened", "Тошнота"); putCore("Clumsy", "Неуклюж");
        putCore("Enfeebled", "Ослаблен"); putCore("Stupefied", "Одурманен"); putCore("Slowed", "Замедлен");
        putCore("Quickened", "Ускорен"); putCore("Dying", "При смерти"); putCore("Wounded", "Ранен");
        putCore("Prone", "Лежит"); putCore("Grabbed", "Схвачен"); putCore("Restrained", "Обездвижен");
        putCore("Off-Guard", "Застигнут врасплох"); putCore("Concealed", "Скрыт"); putCore("Hidden", "Спрятан");
        putCore("Longsword", "Длинный меч"); putCore("Shortsword", "Короткий меч"); putCore("Dagger", "Кинжал");
        putCore("Greatsword", "Двуручный меч"); putCore("Longbow", "Длинный лук"); putCore("Shortbow", "Короткий лук");
        putCore("Chain Mail", "Кольчуга"); putCore("Leather Armor", "Кожаный доспех"); putCore("Plate Armor", "Латный доспех");
        putCore("Shield", "Щит"); putCore("Healing Potion", "Зелье исцеления");
        putCore("Force Barrage", "Силовой залп"); putCore("Electric Arc", "Электрическая дуга");
        putCore("Fireball", "Огненный шар"); putCore("Heal", "Исцеление"); putCore("Sure Strike", "Верный удар");
        putCore("Fear", "Страх"); putCore("Fly", "Полёт"); putCore("Invisibility", "Невидимость");
        putCore("Vicious Swing", "Мощный взмах"); putCore("Sudden Charge", "Внезапный рывок");
        putCore("Toughness", "Стойкость"); putCore("Battle Medicine", "Боевая медицина");
        putCore("Natural Ambition", "Природная амбициозность"); putCore("Reactive Strike", "Ответный удар");
        putCore("Shield Block", "Блок щитом"); putCore("Weapon Specialization", "Специализация на оружии");
    }

    private RuNames() { }

    public static synchronized void init(Context context) {
        if (loaded || context == null) return;
        loadNames(context, "ru_names.json");
        loadTexts(context, "ru_text.json");
        loaded = true;
    }

    private static void loadNames(Context context, String asset) {
        try {
            JSONObject o = new JSONObject(readAsset(context, asset));
            Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String en = it.next();
                String ru = o.optString(en, "").trim();
                if (!ru.isEmpty()) putExternal(en, ru);
            }
        } catch (Exception ignored) { }
    }

    private static void loadTexts(Context context, String asset) {
        try {
            JSONObject root = new JSONObject(readAsset(context, asset));
            Iterator<String> it = root.keys();
            while (it.hasNext()) {
                String id = it.next();
                JSONObject t = root.optJSONObject(id);
                if (t == null) continue;
                String name = t.optString("name", "").trim();
                String english = t.optString("english", "").trim();
                if (!name.isEmpty() && !english.isEmpty()) putExternal(english, name);
                String description = t.optString("description", "").trim();
                if (!description.isEmpty()) DESCRIPTIONS.put(id, description);
                JSONArray prereqs = t.optJSONArray("prerequisites");
                if (prereqs != null && prereqs.length() > 0) {
                    StringBuilder b = new StringBuilder();
                    for (int i = 0; i < prereqs.length(); i++) {
                        String v = prereqs.optString(i, "").trim();
                        if (v.isEmpty()) continue;
                        if (b.length() > 0) b.append("; ");
                        b.append(v);
                    }
                    if (b.length() > 0) PREREQUISITES.put(id, b.toString());
                }
            }
        } catch (Exception ignored) { }
    }

    private static String readAsset(Context context, String asset) throws Exception {
        try (InputStream in = context.getAssets().open(asset); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static String key(String en) { return en == null ? "" : en.trim().toLowerCase(Locale.ROOT); }

    private static void putCore(String en, String ru) {
        String key = key(en), value = ru == null ? "" : ru.trim();
        if (key.isEmpty() || value.isEmpty()) return;
        CORE_KEYS.add(key);
        MAP.put(key, value);
    }

    private static void putExternal(String en, String ru) {
        String key = key(en), value = ru == null ? "" : ru.trim();
        if (key.isEmpty() || value.isEmpty() || CORE_KEYS.contains(key)) return;
        MAP.put(key, value);
    }

    /** Russian-first display. Canonical English remains internal and searchable. */
    public static String display(String english) {
        if (english == null) return "";
        String ru = MAP.get(key(english));
        return ru == null || ru.isEmpty() ? english : ru;
    }

    public static String shortName(String english) {
        if (english == null) return "";
        String ru = MAP.get(key(english));
        return ru == null || ru.isEmpty() ? english : ru;
    }

    public static String description(String id, String fallback) {
        String value = id == null ? null : DESCRIPTIONS.get(id);
        return value == null || value.isEmpty() ? (fallback == null ? "" : fallback) : value;
    }

    public static String prerequisites(String id, java.util.List<String> fallback) {
        String value = id == null ? null : PREREQUISITES.get(id);
        if (value != null && !value.isEmpty()) return value;
        if (fallback == null || fallback.isEmpty()) return "";
        StringBuilder out = new StringBuilder();
        for (String requirement : fallback) {
            if (out.length() > 0) out.append("; ");
            out.append(translateRequirement(requirement));
        }
        return out.toString();
    }

    private static String translateRequirement(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "";
        String exact = MAP.get(key(raw));
        if (exact != null && !exact.isEmpty()) return exact;
        String s = raw;
        String[][] terms = {
                {"legendary", "легендарный"}, {"master", "мастер"}, {"expert", "эксперт"}, {"trained", "обучен"},
                {"Athletics", "Атлетика"}, {"Acrobatics", "Акробатика"}, {"Arcana", "Аркана"},
                {"Crafting", "Ремесло"}, {"Deception", "Обман"}, {"Diplomacy", "Дипломатия"},
                {"Intimidation", "Запугивание"}, {"Medicine", "Медицина"}, {"Nature", "Природа"},
                {"Occultism", "Оккультизм"}, {"Performance", "Выступление"}, {"Religion", "Религия"},
                {"Society", "Общество"}, {"Stealth", "Скрытность"}, {"Survival", "Выживание"}, {"Thievery", "Воровство"},
                {"Strength", "Сила"}, {"Dexterity", "Ловкость"}, {"Constitution", "Телосложение"},
                {"Intelligence", "Интеллект"}, {"Wisdom", "Мудрость"}, {"Charisma", "Харизма"},
                {"spellcasting", "умение творить заклинания"}, {"cast spells", "творить заклинания"},
                {"level", "уровень"}, {"in ", "в "}
        };
        for (String[] term : terms) {
            s = s.replaceAll("(?i)\\b" + java.util.regex.Pattern.quote(term[0]) + "\\b", java.util.regex.Matcher.quoteReplacement(term[1]));
        }
        return s;
    }

    public static boolean matches(String english, String query) {
        if (query == null || query.trim().isEmpty()) return true;
        String q = query.toLowerCase(Locale.ROOT).trim();
        String en = english == null ? "" : english.toLowerCase(Locale.ROOT);
        String ru = MAP.getOrDefault(en, "").toLowerCase(Locale.ROOT);
        return en.contains(q) || ru.contains(q);
    }

    public static int dictionarySize() { return MAP.size(); }
}
