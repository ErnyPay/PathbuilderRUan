#!/usr/bin/env python3
"""Inject PF2e attribute-building ChoiceSets into the executable rule graph."""
from __future__ import annotations

import json
import re
import sqlite3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DB = ROOT / "app" / "src" / "main" / "assets" / "rules.db"
ABILITIES = [
    ("str", "Сила"), ("dex", "Ловкость"), ("con", "Телосложение"),
    ("int", "Интеллект"), ("wis", "Мудрость"), ("cha", "Харизма"),
]
LABELS = dict(ABILITIES)


def runtime_slug(value: str) -> str:
    return re.sub(r"[^a-z0-9]+", "-", str(value).lower().strip()).strip("-")


def flag_option(flag: str, ability: str, previous_flags: list[str]) -> dict:
    predicates = [{"not": f"rules-selection:{runtime_slug(p)}:{ability}"} for p in previous_flags]
    result = {"label": LABELS.get(ability, ability), "value": ability}
    if predicates:
        result["predicate"] = predicates
    return result


def choice_rule(flag: str, prompt: str, options: list[str], previous_flags: list[str] | None = None,
                level: int | None = None) -> dict:
    previous_flags = previous_flags or []
    clean = [x for x in options if x in LABELS]
    rule = {
        "key": "ChoiceSet",
        "flag": flag,
        "prompt": prompt,
        "choices": [flag_option(flag, ability, previous_flags) for ability in clean],
        "_granSynthetic": "ability-building",
    }
    if len(clean) == 1:
        rule["selection"] = clean[0]
    if level is not None:
        rule["predicate"] = {"gte": ["actor:level", level]}
    return rule


def add_stage(rules: list[dict], source: list, prefix: str, title: str) -> int:
    previous: list[str] = []
    added = 0
    for i, raw in enumerate(source or []):
        options = [str(x) for x in raw] if isinstance(raw, list) else []
        if not options:
            continue
        flag = f"{prefix}{i}"
        rules.append(choice_rule(flag, f"{title} {added + 1}", options, previous))
        previous.append(flag)
        added += 1
    return added


def main() -> None:
    if not DB.exists():
        raise SystemExit("rules.db missing")
    db = sqlite3.connect(DB)
    rows_changed = 0
    rules_added = 0
    try:
        rows = db.execute("SELECT id,category,json FROM rules WHERE category IN ('ancestry','background','class')").fetchall()
        for row_id, category, raw in rows:
            data = json.loads(raw)
            meta = data.setdefault("meta", {})
            rules = [
                r for r in meta.get("ruleElements", [])
                if not (isinstance(r, dict) and r.get("_granSynthetic") == "ability-building")
            ]
            before = len(rules)
            if category == "ancestry":
                add_stage(rules, meta.get("flaws", []), "granAncestryFlaw", "Изъян рода")
                add_stage(rules, meta.get("boosts", []), "granAncestryBoost", "Boost рода")
            elif category == "background":
                add_stage(rules, meta.get("boosts", []), "granBackgroundBoost", "Boost предыстории")
            elif category == "class":
                key_options = [str(x) for x in meta.get("keyAbility", []) if str(x) in LABELS]
                if key_options:
                    rules.append(choice_rule("granClassKey", "Ключевая характеристика класса", key_options))
                for level in (1, 5, 10, 15, 20):
                    previous: list[str] = []
                    for i in range(4):
                        flag = f"granFree{level}_{i}"
                        rules.append(choice_rule(flag, f"Свободный boost {level} ур. — {i + 1}/4",
                                                 [a for a, _ in ABILITIES], previous, level=level))
                        previous.append(flag)
            added = len(rules) - before
            if added:
                meta["ruleElements"] = rules
                db.execute("UPDATE rules SET json=? WHERE id=?", (json.dumps(data, ensure_ascii=False, separators=(",", ":")), row_id))
                rows_changed += 1
                rules_added += added
        db.commit()
    finally:
        db.close()
    print(f"Ability-choice rows changed: {rows_changed}; ChoiceSets added: {rules_added}")
    if rows_changed < 500 or rules_added < 1000:
        raise SystemExit("Ability ChoiceSet injection unexpectedly incomplete")


if __name__ == "__main__":
    main()
