#!/usr/bin/env python3
"""PF2.RU reference integration for Gran 2e.

The site explicitly asks clients not to scrape it. This integration therefore:
- adds visible PF2.RU links to rule/equipment details;
- seeds only a tiny hand-curated terminology bridge;
- consumes an explicit local JSON export when one is provided by the site owner/user;
- never crawls pf2.ru during build or runtime.
"""
from __future__ import annotations

import json
import os
import re
import sqlite3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app" / "src" / "main" / "assets"
DB = ASSETS / "rules.db"
V2 = ROOT / "app" / "src" / "main" / "java" / "ru" / "gran" / "edge2e" / "MainActivityV2.java"
CYR = re.compile(r"[А-Яа-яЁё]")

# Short labels / compact paraphrases only. Full PF2.RU text is intentionally not copied.
SEED = [
    {"english":"Skill Feats","name":"Черты навыков","description":"Черты навыков обычно выдаются на 2-м уровне и затем через каждые 2 уровня; для связанной черты требуется обучение соответствующему навыку.","url":"https://pf2.ru/rules/skill%20feats?rule=2107"},
    {"english":"Focus Spells","name":"Фокальные заклинания","description":"Фокальные заклинания получают из особенностей и черт; они используют очки фокуса, а не обычные ячейки заклинаний.","url":"https://pf2.ru/rules/focus%20spells?rule=2228"},
    {"english":"Scrolls","name":"Свитки","description":"Свитки служат одноразовым способом применения заклинаний и полезны как ориентир для баланса похожих расходуемых предметов.","url":"https://pf2.ru/rules/scrolls?rule=2941"},
    {"english":"Specialty Crafting","name":"Ремесленник-специалист","description":"Специализация в выбранной области Ремесла даёт ситуативный бонус к Созданию подходящих предметов, который растёт при мастерском владении Ремеслом.","url":"https://pf2.ru/feats/specialty%20crafting"},
    {"english":"Enhanced Familiar","name":"Улучшенный фамильяр","description":"Черта увеличивает число способностей фамильяра или хозяина, которые можно выбирать каждый день.","url":"https://pf2.ru/feats/enhanced%20familiar"},
]


def safe_name(value: object) -> str:
    s = str(value or "").strip()
    if not s or not CYR.search(s) or len(s) > 100 or "\n" in s:
        return ""
    return s


def load_records() -> list[dict]:
    records = list(SEED)
    export_path = Path(os.environ.get("PF2RU_EXPORT", ROOT / "external" / "pf2ru-export.json"))
    if not export_path.exists():
        return records
    data = json.loads(export_path.read_text(encoding="utf-8"))
    if isinstance(data, dict) and isinstance(data.get("records"), list):
        data = data["records"]
    elif isinstance(data, dict):
        data = [dict(v, english=k) if isinstance(v, dict) else {"english": k, "name": v} for k, v in data.items()]
    if not isinstance(data, list):
        raise SystemExit("PF2RU_EXPORT must contain a list, a {records:[...]} object, or an english->record mapping")
    for entry in data:
        if not isinstance(entry, dict):
            continue
        english = str(entry.get("english") or entry.get("sourceName") or "").strip()
        name = safe_name(entry.get("name"))
        desc = str(entry.get("description") or entry.get("text") or "").strip()
        url = str(entry.get("url") or entry.get("sourceUrl") or "").strip()
        prereq = entry.get("prerequisites") or []
        if isinstance(prereq, str):
            prereq = [prereq]
        if english and (name or desc or url or prereq):
            records.append({"english": english, "name": name, "description": desc, "url": url, "prerequisites": prereq})
    return records


def merge_dictionary(records: list[dict]) -> int:
    if not DB.exists():
        raise SystemExit(f"Missing {DB}; run rule generation first")
    names_path = ASSETS / "ru_names.json"
    texts_path = ASSETS / "ru_text.json"
    names = json.loads(names_path.read_text(encoding="utf-8")) if names_path.exists() else {}
    texts = json.loads(texts_path.read_text(encoding="utf-8")) if texts_path.exists() else {}
    conn = sqlite3.connect(DB)
    matched = 0
    for rec in records:
        english = str(rec.get("english") or "").strip()
        if not english:
            continue
        rows = conn.execute("SELECT id,json FROM rules WHERE lower(name)=lower(?)", (english,)).fetchall()
        if not rows:
            continue
        ru_name = safe_name(rec.get("name"))
        if ru_name:
            names.setdefault(english, ru_name)
        for ident, raw in rows:
            obj = json.loads(raw)
            meta = obj.setdefault("meta", {})
            if ru_name and not meta.get("ruName"):
                meta["ruName"] = ru_name
            desc = str(rec.get("description") or "").strip()
            if desc and not meta.get("ruDescription"):
                meta["ruDescription"] = desc
            prereq = [str(x).strip() for x in (rec.get("prerequisites") or []) if str(x).strip()]
            if prereq and not meta.get("ruPrerequisites"):
                meta["ruPrerequisites"] = prereq
            url = str(rec.get("url") or "").strip()
            if url.startswith("https://pf2.ru/") or url.startswith("https://www.pf2.ru/"):
                meta["pf2ruUrl"] = url
            entry = texts.setdefault(ident, {"english": english, "name": "", "description": "", "prerequisites": []})
            if ru_name and not entry.get("name"):
                entry["name"] = ru_name
            if desc and not entry.get("description"):
                entry["description"] = desc
            if prereq and not entry.get("prerequisites"):
                entry["prerequisites"] = prereq
            conn.execute(
                "UPDATE rules SET json=? WHERE id=?",
                (json.dumps(obj, ensure_ascii=False, separators=(",", ":")), ident),
            )
            matched += 1
    conn.commit()
    conn.close()
    names_path.write_text(json.dumps(names, ensure_ascii=False, sort_keys=True, separators=(",", ":")), encoding="utf-8")
    texts_path.write_text(json.dumps(texts, ensure_ascii=False, sort_keys=True, separators=(",", ":")), encoding="utf-8")
    (ASSETS / "pf2ru_source.json").write_text(
        json.dumps(
            {
                "name": "PF2.RU",
                "homepage": "https://pf2.ru/",
                "searchTemplate": "https://pf2.ru/search?q={query}",
                "mode": "external-reference-plus-explicit-export",
                "networkScraping": False,
                "exportEnvironment": "PF2RU_EXPORT",
            },
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )
    return matched


def insert_before_dialog(s: str, method_name: str, code: str, marker: str) -> str:
    """Insert a row before the AlertDialog builder inside a generated detail method."""
    if marker in s:
        return s
    pattern = re.compile(
        r"(    private void " + re.escape(method_name) + r"\([^\n]*\) \{.*?)(\n        AlertDialog\.Builder b = )",
        re.S,
    )
    m = pattern.search(s)
    if not m:
        raise SystemExit(f"PF2.RU UI patch: {method_name} method/dialog boundary not found")
    return s[:m.start()] + m.group(1) + "\n" + code + m.group(2) + s[m.end():]


def patch_ui() -> None:
    s = V2.read_text(encoding="utf-8")
    if "import android.content.Intent;" not in s:
        s = s.replace("import android.content.Context;\n", "import android.content.Context;\nimport android.content.Intent;\n")
    if "import android.net.Uri;" not in s:
        s = s.replace("import android.os.Bundle;\n", "import android.net.Uri;\nimport android.os.Bundle;\n")

    old_reference = 'outer.addView(note("Локальная база без сети: " + store.count() + " записей. Поиск работает по английским данным и по встроенным русским названиям основных элементов."));'
    if old_reference in s:
        s = s.replace(
            old_reference,
            'outer.addView(note("Локальная база без сети: " + store.count() + " записей. PF2.RU используется как внешний русский справочник текста и правил; открывается только по вашему нажатию."));',
            1,
        )

    s = insert_before_dialog(
        s,
        "showRuleDetail",
        '        TextView pf2ru = actionRow("PF2.RU — текст и правила", "Открыть русский справочник для этого правила");\n'
        '        pf2ru.setOnClickListener(v -> openPf2Ru(item));\n'
        '        body.addView(pf2ru);',
        "TextView pf2ru = actionRow(\"PF2.RU — текст и правила\"",
    )
    s = insert_before_dialog(
        s,
        "equipmentDetail",
        '        TextView pf2ruEquip = actionRow("PF2.RU — текст и правила", "Открыть русский справочник для этого предмета");\n'
        '        pf2ruEquip.setOnClickListener(v -> openPf2Ru(item));\n'
        '        body.addView(pf2ruEquip);',
        "TextView pf2ruEquip = actionRow(\"PF2.RU — текст и правила\"",
    )

    if "private void openPf2Ru(RuleItem item)" not in s:
        helper_anchor = "    private String spellLongMeta(RuleItem item) {\n"
        if helper_anchor not in s:
            raise SystemExit("PF2.RU UI patch: spellLongMeta helper anchor not found")
        helper = '''    private void openPf2Ru(RuleItem item) {\n        String exact = item.meta.optString("pf2ruUrl", "");\n        String url = exact.isEmpty() ? "https://pf2.ru/search?q=" + Uri.encode(item.name) : exact;\n        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }\n        catch (Exception e) { toast("Не удалось открыть PF2.RU"); }\n    }\n\n'''
        s = s.replace(helper_anchor, helper + helper_anchor, 1)

    V2.write_text(s, encoding="utf-8")


def main() -> None:
    records = load_records()
    matched = merge_dictionary(records)
    patch_ui()
    print("PF2.RU integration ready; matched local rules:", matched, "records considered:", len(records))


if __name__ == "__main__":
    main()
