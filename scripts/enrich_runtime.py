#!/usr/bin/env python3
"""Enrich generated PF2e rows with executable rule-element metadata.

This does not copy Pathbuilder code. It keeps the open PF2e source rule elements that
are needed by Gran's runtime to rebuild a character after every choice.
"""
from __future__ import annotations

import json
import sqlite3
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CACHE = ROOT / "build" / "pf2e-source"
PACK = CACHE / "packs" / "pf2e"
DB = ROOT / "app" / "src" / "main" / "assets" / "rules.db"

PACKS = [
    "classes", "class-features", "ancestries", "heritages", "backgrounds",
    "feats", "spells", "equipment", "actions", "conditions", "deities"
]


def slug(value: object) -> str:
    import re
    text = str(value or "").lower().strip()
    text = re.sub(r"[^a-z0-9]+", "-", text)
    return text.strip("-")


def compact_rule(rule: dict) -> dict:
    # Keep the complete rule element: predicates, injected properties, selections,
    # grant UUIDs, alterations and selector data are all meaningful at runtime.
    return rule


def simple_choice_sets(rules: list[dict]) -> list[dict]:
    out: list[dict] = []
    for rule in rules:
        if not isinstance(rule, dict) or rule.get("key") != "ChoiceSet":
            continue
        choices = rule.get("choices")
        normalized = []
        if isinstance(choices, list):
            for choice in choices:
                if isinstance(choice, dict) and "value" in choice:
                    normalized.append({
                        "label": str(choice.get("label") or choice.get("value")),
                        "value": choice.get("value"),
                    })
                elif isinstance(choice, (str, int, float)):
                    normalized.append({"label": str(choice), "value": choice})
        out.append({
            "flag": str(rule.get("flag") or ""),
            "prompt": str(rule.get("prompt") or ""),
            "adjustName": bool(rule.get("adjustName", False)),
            "choices": normalized,
            "dynamic": not isinstance(choices, list),
        })
    return out


def main() -> None:
    if not DB.exists():
        raise SystemExit("rules.db missing")
    db = sqlite3.connect(DB)
    counters: Counter[str] = Counter()
    updated = 0
    try:
        for top in PACKS:
            base = PACK / top
            if not base.exists():
                continue
            for path in base.rglob("*.json"):
                if path.name.startswith("_"):
                    continue
                try:
                    doc = json.loads(path.read_text(encoding="utf-8"))
                except Exception:
                    continue
                if not isinstance(doc, dict):
                    continue
                row_id = str(doc.get("_id") or "")
                if not row_id:
                    continue
                row = db.execute("SELECT json FROM rules WHERE id=?", (row_id,)).fetchone()
                if not row:
                    continue
                data = json.loads(row[0])
                system = doc.get("system") if isinstance(doc.get("system"), dict) else {}
                rules = system.get("rules") if isinstance(system.get("rules"), list) else []
                rules = [compact_rule(r) for r in rules if isinstance(r, dict)]
                meta = data.setdefault("meta", {})
                meta["slug"] = str(system.get("slug") or slug(doc.get("name")))
                meta["sourcePack"] = top
                meta["sourceId"] = row_id
                meta["ruleElements"] = rules
                meta["choiceSets"] = simple_choice_sets(rules)
                for rule in rules:
                    counters[str(rule.get("key") or "<unknown>")] += 1
                raw = json.dumps(data, ensure_ascii=False, separators=(",", ":"))
                db.execute("UPDATE rules SET json=? WHERE id=?", (raw, row_id))
                updated += 1
        db.execute("PRAGMA user_version=5")
        db.commit()
    finally:
        db.close()

    print(f"Runtime metadata enriched rows: {updated}")
    print("Rule-element counts:")
    for key, count in counters.most_common():
        print(f"  {key}: {count}")

    # Guardrails: if these disappear, the runtime would silently regress to a text-only dictionary.
    if updated < 15000:
        raise SystemExit("Runtime enrichment unexpectedly incomplete")
    if counters["ChoiceSet"] < 100:
        raise SystemExit("Too few ChoiceSet rule elements")
    if counters["ActiveEffectLike"] < 100:
        raise SystemExit("Too few ActiveEffectLike rule elements")
    if counters["GrantItem"] < 100:
        raise SystemExit("Too few GrantItem rule elements")


if __name__ == "__main__":
    main()
