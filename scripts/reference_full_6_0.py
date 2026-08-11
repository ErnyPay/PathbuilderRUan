#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FRONT = ROOT / 'app/src/main/java/ru/gran/edge2e/FrontPageActivity.java'
BUILD = ROOT / 'app/src/main/java/ru/gran/edge2e/ReferenceBuildActivity.java'
PLAY = ROOT / 'app/src/main/java/ru/gran/edge2e/ReferencePlayActivity.java'


def replace_once(text, old, new, label):
    if old not in text:
        raise SystemExit(f'Gran 6.0 patch missing anchor: {label}')
    return text.replace(old, new, 1)


def patch_front():
    s = FRONT.read_text(encoding='utf-8')
    anchor = '''        body.addView(source, matchWrap(dp(5)));'''
    inject = '''        body.addView(source, matchWrap(dp(5)));

        body.addView(section("ИНСТРУМЕНТЫ И БИБЛИОТЕКА"));
        LinearLayout tools = card();
        Button allTools = primaryButton("ОТКРЫТЬ ВСЕ ИНСТРУМЕНТЫ");
        allTools.setOnClickListener(v -> startActivity(new Intent(this, ReferenceMoreActivity.class)));
        tools.addView(allTools, matchWrap(dp(3)));
        TextView toolInfo = text("Роды, наследия, классы, фиты, заклинания, оружие, броня, щиты, предметы, состояния, действия, языки, Lore, импорт/экспорт и источники.", 13, false);
        toolInfo.setTextColor(MUTED); toolInfo.setPadding(0, dp(5), 0, 0); tools.addView(toolInfo);
        body.addView(tools, matchWrap(dp(5)));'''
    s = replace_once(s, anchor, inject, 'front tools card')
    FRONT.write_text(s, encoding='utf-8')


def patch_build():
    s = BUILD.read_text(encoding='utf-8')
    anchor = '''        search.addTextChangedListener(watcher(refresh)); refresh.run(); return col;'''
    inject = '''        TextView full = actionRow("ВСЕ БРАУЗЕРЫ И ИНСТРУМЕНТЫ", "заклинания, предметы, языки, Lore, импорт / экспорт");
        full.setOnClickListener(v -> startActivity(new Intent(this, ReferenceMoreActivity.class)));
        results.addView(full);
        search.addTextChangedListener(watcher(refresh)); refresh.run(); return col;'''
    s = replace_once(s, anchor, inject, 'build reference tools link')
    BUILD.write_text(s, encoding='utf-8')


def patch_play():
    s = PLAY.read_text(encoding='utf-8')
    # Add MORE next to BUILD in the persistent top bar.
    anchor = '''        build.setOnClickListener(v -> startActivity(new Intent(this, ReferenceBuildActivity.class)));
        line.addView(build); top.addView(line);'''
    inject = '''        build.setOnClickListener(v -> startActivity(new Intent(this, ReferenceBuildActivity.class)));
        line.addView(build);
        TextView more = text("ЕЩЁ", 11, true); more.setTextColor(Color.rgb(236, 205, 169)); more.setPadding(dp(12), dp(5), 0, dp(5));
        more.setOnClickListener(v -> startActivity(new Intent(this, ReferenceMoreActivity.class)));
        line.addView(more); top.addView(line);'''
    s = replace_once(s, anchor, inject, 'play more navigation')

    # Replace generic inventory item dialog with full item/rune editor.
    old = '''            TextView row = actionRow(RuNames.shortName(item.name), meta); row.setOnClickListener(v -> equipmentDialog(item)); list.addView(row);'''
    new = '''            TextView row = actionRow(RuNames.shortName(item.name), meta);
            row.setOnClickListener(v -> { Intent i = new Intent(this, ReferenceItemActivity.class); i.putExtra("itemId", item.id); startActivity(i); });
            list.addView(row);'''
    s = replace_once(s, old, new, 'gear item editor')

    # Open the full equipment browser instead of the small legacy dialog.
    old = '''        TextView add = actionRow("+ ДОБАВИТЬ ПРЕДМЕТ", "оружие, броня, расходники, инструменты"); add.setOnClickListener(v -> showEquipmentPicker()); list.addView(add); col.addView(list, matchWrap(dp(4)));'''
    new = '''        TextView add = actionRow("+ ДОБАВИТЬ ПРЕДМЕТ", "оружие, броня, щиты, расходники, инструменты");
        add.setOnClickListener(v -> { Intent i = new Intent(this, ReferenceCatalogActivity.class); i.putExtra("mode", "equipment"); i.putExtra("maxLevel", state.level); startActivity(i); });
        list.addView(add); col.addView(list, matchWrap(dp(4)));'''
    s = replace_once(s, old, new, 'full equipment catalog')

    # Full condition browser.
    old = '''        Button add = button("+ ДОБАВИТЬ СОСТОЯНИЕ"); add.setOnClickListener(v -> showConditionPicker()); col.addView(add, matchWrap(dp(4)));'''
    new = '''        Button add = button("+ ДОБАВИТЬ СОСТОЯНИЕ");
        add.setOnClickListener(v -> { Intent i = new Intent(this, ReferenceCatalogActivity.class); i.putExtra("mode", "condition"); startActivity(i); });
        col.addView(add, matchWrap(dp(4)));'''
    s = replace_once(s, old, new, 'full condition catalog')

    # Dedicated companion/familiar/eidolon editor on a normal tap.
    old = '''        card.setOnLongClickListener(v -> { new AlertDialog.Builder(this).setTitle("Удалить " + c.name + "?").setNegativeButton("Отмена", null).setPositiveButton("Удалить", (d,w) -> { companions.remove(c.id); companions.save(this); render(); }).show(); return true; }); return card;'''
    new = '''        card.setOnClickListener(v -> { Intent i = new Intent(this, ReferenceCompanionActivity.class); i.putExtra("companionId", c.id); startActivity(i); });
        card.setOnLongClickListener(v -> { new AlertDialog.Builder(this).setTitle("Удалить " + c.name + "?").setNegativeButton("Отмена", null).setPositiveButton("Удалить", (d,w) -> { companions.remove(c.id); companions.save(this); render(); }).show(); return true; }); return card;'''
    s = replace_once(s, old, new, 'companion editor')
    PLAY.write_text(s, encoding='utf-8')


def main():
    patch_front(); patch_build(); patch_play()
    print('Applied Gran 6.0 full reference workflow wiring')


if __name__ == '__main__':
    main()
