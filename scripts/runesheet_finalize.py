#!/usr/bin/env python3
"""Final source-level RuneSheet product patch.

Runs after the Gran/PF2e code generators. Keeps canonical rules in English,
adds Russian presentation labels, RuneSheet branding, Pathbuilder share import,
and deep-link handling without binary DEX patching.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app"
JAVA = APP / "src/main/java/ru/gran/edge2e"
MANIFEST = APP / "src/main/AndroidManifest.xml"
GRADLE = APP / "build.gradle"
FRONT = JAVA / "FrontPageActivity.java"
RUNAMES = JAVA / "RuNames.java"


def once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    if old not in text:
        raise SystemExit(f"RuneSheet finalizer: missing anchor: {label}")
    return text.replace(old, new, 1)


def patch_gradle():
    s = GRADLE.read_text(encoding="utf-8")
    import re
    s = re.sub(r"applicationId\s+'[^']+'", "applicationId 'com.pf2ebuilder.zz.producty'", s, count=1)
    s = re.sub(r"versionCode\s+\d+", "versionCode 700", s, count=1)
    s = re.sub(r"versionName\s+'[^']+'", "versionName '7.0.0-runesheet'", s, count=1)
    GRADLE.write_text(s, encoding="utf-8")


def patch_manifest():
    s = MANIFEST.read_text(encoding="utf-8")
    if "android.permission.INTERNET" not in s:
        s = once(
            s,
            '<manifest xmlns:android="http://schemas.android.com/apk/res/android">',
            '<manifest xmlns:android="http://schemas.android.com/apk/res/android">\n    <uses-permission android:name="android.permission.INTERNET" />',
            "internet permission",
        )
    import re
    s = re.sub(r'android:label="[^"]+"', 'android:label="RuneSheet RU"', s, count=1)
    app = '<application'
    if 'android:icon="@drawable/runesheet_icon"' not in s:
        s = once(s, app, app + '\n        android:icon="@drawable/runesheet_icon"\n        android:roundIcon="@drawable/runesheet_icon"', "launcher icon")
    if 'android:host="pathbuilder2e.com"' not in s:
        close = '''            </intent-filter>
        </activity>'''
        extra = '''            </intent-filter>
            <intent-filter android:autoVerify="false">
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="https" android:host="pathbuilder2e.com" android:path="/launch.html" />
                <data android:scheme="https" android:host="www.pathbuilder2e.com" android:path="/launch.html" />
            </intent-filter>
        </activity>'''
        s = once(s, close, extra, "front-page deep link")
    MANIFEST.write_text(s, encoding="utf-8")


def patch_ru_names():
    s = RUNAMES.read_text(encoding="utf-8")
    if 'putCore("Necromancer", "Некромант")' not in s:
        s = once(
            s,
            'putCore("Commander", "Командир"); putCore("Druid", "Друид");',
            'putCore("Commander", "Командир"); putCore("Necromancer", "Некромант"); putCore("Runesmith", "Рунотворец"); putCore("Druid", "Друид");',
            "new class translations",
        )
    RUNAMES.write_text(s, encoding="utf-8")


def patch_front():
    s = FRONT.read_text(encoding="utf-8")
    if "import android.net.Uri;" not in s:
        s = once(s, "import android.os.Bundle;\n", "import android.net.Uri;\nimport android.os.Bundle;\n", "Uri import")
    if "import android.widget.EditText;" not in s:
        s = once(s, "import android.widget.Button;\n", "import android.widget.Button;\nimport android.widget.EditText;\n", "EditText import")
    if "import android.widget.Toast;" not in s:
        s = once(s, "import android.widget.TextView;\n", "import android.widget.TextView;\nimport android.widget.Toast;\n", "Toast import")

    s = s.replace('"ГРАНЬ 2e"', '"RUNESHEET RU"')
    s = s.replace('"ПЕРСОНАЖИ • СБОРКА • ИГРА"', '"PATHFINDER 2e • ПЕРСОНАЖИ • СБОРКА • ИГРА"')

    if "handleIncomingIntent(getIntent());" not in s:
        s = once(
            s,
            "        setContentView(shell());\n    }",
            """        setContentView(shell());
        handleIncomingIntent(getIntent());
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
    }""",
            "incoming link lifecycle",
        )

    if "ИМПОРТ ИЗ PATHBUILDER" not in s:
        s = once(
            s,
            "        start.addView(create, matchWrap(dp(3)));",
            """        start.addView(create, matchWrap(dp(3)));
        Button importPb = button("ИМПОРТ ИЗ PATHBUILDER");
        importPb.setOnClickListener(v -> promptPathbuilderImport());
        start.addView(importPb, matchWrap(dp(3)));""",
            "Pathbuilder import button",
        )

    if "private void promptPathbuilderImport()" not in s:
        methods = r'''
    private void promptPathbuilderImport() {
        EditText input = new EditText(this);
        input.setHint("https://pathbuilder2e.com/launch.html?build=...");
        input.setSingleLine(true);
        new AlertDialog.Builder(this)
                .setTitle("Импорт Pathbuilder")
                .setMessage("Вставь полную ссылку «Поделиться персонажем» или только Build ID.")
                .setView(input)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Импортировать", (d, w) -> importPathbuilder(input.getText().toString()))
                .show();
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent == null || !Intent.ACTION_VIEW.equals(intent.getAction())) return;
        Uri data = intent.getData();
        if (data == null || data.getHost() == null || !data.getHost().toLowerCase().contains("pathbuilder2e.com")) return;
        String value = data.toString();
        if (!PathbuilderImport.buildId(value).isEmpty()) importPathbuilder(value);
    }

    private void importPathbuilder(String value) {
        String id = PathbuilderImport.buildId(value);
        if (id.isEmpty()) {
            Toast.makeText(this, "Не найден Build ID", Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, "Загрузка персонажа " + id + "…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                CharacterState imported = PathbuilderImport.fetch(this, value);
                runOnUiThread(() -> {
                    CharacterProfiles.createNew(this);
                    imported.save(this);
                    CharacterProfiles.saveCurrent(this);
                    Toast.makeText(this, "Персонаж импортирован", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(this, MainActivityV3.class));
                });
            } catch (Exception e) {
                runOnUiThread(() -> new AlertDialog.Builder(this)
                        .setTitle("Не удалось импортировать")
                        .setMessage(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())
                        .setPositiveButton("Закрыть", null)
                        .show());
            }
        }).start();
    }

'''
        s = once(s, "    private void openBuild(String id) {", methods + "    private void openBuild(String id) {", "Pathbuilder methods")
    FRONT.write_text(s, encoding="utf-8")


def patch_visible_branding():
    for path in JAVA.glob("*.java"):
        s = path.read_text(encoding="utf-8")
        before = s
        s = s.replace('"ГРАНЬ 2e"', '"RUNESHEET RU"')
        s = s.replace('"Gran character"', '"RuneSheet character"')
        s = s.replace("сохранение Gran", "сохранение RuneSheet")
        s = s.replace("код Gran и интерфейсная реализация самостоятельные", "код RuneSheet RU и интерфейсная реализация самостоятельные")
        if s != before:
            path.write_text(s, encoding="utf-8")


def main():
    patch_gradle()
    patch_manifest()
    patch_ru_names()
    patch_front()
    patch_visible_branding()
    print("RuneSheet final product patch applied")


if __name__ == "__main__":
    main()
