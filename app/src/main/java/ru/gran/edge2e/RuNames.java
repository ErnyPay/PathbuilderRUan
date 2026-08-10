package ru.gran.edge2e;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/** Russian presentation dictionary. English canonical names stay in the rule engine. */
public final class RuNames {
    private static final Map<String, String> MAP = new HashMap<>();
    private static final Map<String, String> DESCRIPTIONS = new HashMap<>();
    private static final Map<String, String> PREREQUISITES = new HashMap<>();
    private static volatile boolean loaded = false;

    static {
        put("Alchemist", "Алхимик"); put("Animist", "Анимист"); put("Barbarian", "Варвар");
        put("Bard", "Бард"); put("Champion", "Чемпион"); put("Cleric", "Клирик");
        put("Commander", "Командир"); put("Druid", "Друид"); put("Exemplar", "Экземпляр");
        put("Fighter", "Воин"); put("Guardian", "Страж"); put("Gunslinger", "Стрелок");
        put("Inventor", "Изобретатель"); put("Investigator", "Следователь"); put("Kineticist", "Кинетик");
        put("Magus", "Магус"); put("Monk", "Монах"); put("Oracle", "Оракул");
        put("Psychic", "Психик"); put("Ranger", "Следопыт"); put("Rogue", "Плут");
        put("Sorcerer", "Чародей"); put("Summoner", "Призыватель"); put("Swashbuckler", "Сорвиголова");
        put("Thaumaturge", "Тауматург"); put("Witch", "Ведьма"); put("Wizard", "Волшебник");
        put("Human", "Человек"); put("Dwarf", "Дварф"); put("Elf", "Эльф"); put("Gnome", "Гном");
        put("Goblin", "Гоблин"); put("Halfling", "Полурослик"); put("Orc", "Орк"); put("Kobold", "Кобольд");
        put("Leshy", "Леший"); put("Catfolk", "Кошколюд"); put("Tengu", "Тэнгу"); put("Android", "Андроид");
        put("Frightened", "Испуган"); put("Sickened", "Тошнота"); put("Clumsy", "Неуклюж");
        put("Enfeebled", "Ослаблен"); put("Stupefied", "Одурманен"); put("Slowed", "Замедлен");
        put("Quickened", "Ускорен"); put("Dying", "При смерти"); put("Wounded", "Ранен");
        put("Prone", "Лежит"); put("Grabbed", "Схвачен"); put("Restrained", "Обездвижен");
        put("Off-Guard", "Застигнут врасплох"); put("Concealed", "Скрыт"); put("Hidden", "Спрятан");
        put("Longsword", "Длинный меч"); put("Shortsword", "Короткий меч"); put("Dagger", "Кинжал");
        put("Greatsword", "Двуручный меч"); put("Longbow", "Длинный лук"); put("Shortbow", "Короткий лук");
        put("Chain Mail", "Кольчуга"); put("Leather Armor", "Кожаный доспех"); put("Plate Armor", "Латный доспех");
        put("Shield", "Щит"); put("Healing Potion", "Зелье исцеления");
        put("Force Barrage", "Силовой залп"); put("Electric Arc", "Электрическая дуга");
        put("Fireball", "Огненный шар"); put("Heal", "Исцеление"); put("Sure Strike", "Верный удар");
        put("Fear", "Страх"); put("Fly", "Полёт"); put("Invisibility", "Невидимость");
        put("Vicious Swing", "Мощный взмах"); put("Sudden Charge", "Внезапный рывок");
        put("Toughness", "Стойкость"); put("Battle Medicine", "Боевая медицина");
        put("Natural Ambition", "Природная амбициозность"); put("Reactive Strike", "Ответный удар");
        put("Shield Block", "Блок щитом"); put("Weapon Specialization", "Специализация на оружии");
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
                if (!ru.isEmpty()) put(en, ru);
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
                if (!name.isEmpty() && !english.isEmpty()) put(english, name);
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

    private static void put(String en, String ru) {
        if (en == null || ru == null) return;
        String key = en.trim().toLowerCase(Locale.ROOT);
        String value = ru.trim();
        if (!key.isEmpty() && !value.isEmpty()) MAP.put(key, value);
    }

    public static String display(String english) {
        if (english == null) return "";
        String ru = MAP.get(english.toLowerCase(Locale.ROOT));
        return ru == null || ru.isEmpty() ? english : ru + " (" + english + ")";
    }

    public static String shortName(String english) {
        if (english == null) return "";
        String ru = MAP.get(english.toLowerCase(Locale.ROOT));
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
        String exact = MAP.get(raw.trim().toLowerCase(Locale.ROOT));
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
