#!/usr/bin/env python3
"""Enrich generated PF2e rows with executable rule-element metadata.

Gran keeps the open PF2e rule-element structures and pre-resolves ChoiceSet pack
queries that depend only on static item metadata (tags/traits/type/rarity). Runtime
choices that depend on the current actor remain dynamic.
"""
from __future__ import annotations

import copy
import json
import re
import sqlite3
from collections import Counter
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
CACHE = ROOT / "build" / "pf2e-source"
PACK = CACHE / "packs" / "pf2e"
DB = ROOT / "app" / "src" / "main" / "assets" / "rules.db"

PACKS = [
    "classes", "class-features", "ancestries", "heritages", "backgrounds",
    "feats", "spells", "equipment", "actions", "conditions", "deities"
]


def slug(value: object) -> str:
    text = str(value or "").lower().strip()
    text = re.sub(r"[^a-z0-9]+", "-", text)
    return text.strip("-")


def values(raw: Any) -> list[str]:
    if isinstance(raw, dict) and "value" in raw:
        raw = raw.get("value")
    if isinstance(raw, list):
        return [str(v) for v in raw if v is not None]
    if raw is None:
        return []
    return [str(raw)]


def load_documents(db: sqlite3.Connection) -> list[dict[str, Any]]:
    docs: list[dict[str, Any]] = []
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
            traits_obj = system.get("traits") if isinstance(system.get("traits"), dict) else {}
            descriptor = {
                "id": row_id,
                "name": str(doc.get("name") or data.get("name") or ""),
                "sourceType": str(doc.get("type") or ""),
                "slug": str(system.get("slug") or slug(doc.get("name"))),
                "traits": set(values(traits_obj.get("value"))),
                "otherTags": set(values(traits_obj.get("otherTags"))),
                "rarity": str(traits_obj.get("rarity") or "common"),
                "category": str(system.get("category") or data.get("subtype") or ""),
                "level": int((system.get("level") or {}).get("value", data.get("level", 0)) if isinstance(system.get("level"), dict) else system.get("level") or data.get("level", 0)),
                "top": top,
                "doc": doc,
                "data": data,
            }
            docs.append(descriptor)
    return docs


def static_term(term: Any, candidate: dict[str, Any]) -> bool | None:
    """Evaluate the static subset of Foundry's item predicates.

    None means the term needs actor/runtime context and therefore must remain dynamic.
    """
    if term is None:
        return True
    if isinstance(term, list):
        unknown = False
        for part in term:
            result = static_term(part, candidate)
            if result is False:
                return False
            if result is None:
                unknown = True
        return None if unknown else True
    if isinstance(term, dict):
        if "and" in term:
            return static_term(term["and"], candidate)
        if "not" in term:
            result = static_term(term["not"], candidate)
            return None if result is None else not result
        if "or" in term:
            raw = term["or"]
            parts = raw if isinstance(raw, list) else [raw]
            unknown = False
            for part in parts:
                result = static_term(part, candidate)
                if result is True:
                    return True
                if result is None:
                    unknown = True
            return None if unknown else False
        if "nor" in term:
            raw = term["nor"]
            parts = raw if isinstance(raw, list) else [raw]
            unknown = False
            for part in parts:
                result = static_term(part, candidate)
                if result is True:
                    return False
                if result is None:
                    unknown = True
            return None if unknown else True
        return None
    if not isinstance(term, str):
        return None

    token = term.strip().lower()
    if token.startswith("item:tag:"):
        return token[9:] in candidate["otherTags"]
    if token.startswith("item:trait:"):
        return token[11:] in candidate["traits"]
    if token.startswith("item:slug:"):
        return token[10:] == candidate["slug"]
    if token.startswith("item:type:"):
        return token[10:] == candidate["sourceType"]
    if token.startswith("item:category:"):
        return token[14:] == slug(candidate["category"])
    if token.startswith("item:rarity:"):
        return token[12:] == candidate["rarity"].lower()
    if token.startswith("item:level:"):
        try:
            return int(token[11:]) == candidate["level"]
        except ValueError:
            return None
    return None


def expand_pack_choice(rule: dict[str, Any], catalog: list[dict[str, Any]]) -> tuple[dict[str, Any], bool]:
    choices = rule.get("choices")
    if not isinstance(choices, dict) or "filter" not in choices:
        return rule, False
    # Actor-owned attacks/items and CONFIG references are runtime-dependent by definition.
    if any(k in choices for k in ("ownedItems", "attacks", "unarmedAttacks", "config")):
        return rule, False

    item_type = str(choices.get("itemType") or "feat")
    filter_value = choices.get("filter")
    matched: list[dict[str, Any]] = []
    unknown = False
    for candidate in catalog:
        if item_type and candidate["sourceType"] != item_type:
            continue
        result = static_term(filter_value, candidate)
        if result is None:
            unknown = True
            break
        if result:
            matched.append(candidate)
    if unknown:
        return rule, False

    matched.sort(key=lambda c: (c["level"], c["name"].lower()))
    use_slugs = bool(choices.get("slugsAsValues", False))
    expanded = copy.deepcopy(rule)
    expanded["_granDynamicSource"] = copy.deepcopy(choices)
    expanded["choices"] = [
        {
            "label": c["name"],
            "value": c["slug"] if use_slugs else c["id"],
            "granId": c["id"],
            "granCategory": c["data"].get("category", ""),
        }
        for c in matched
    ]
    return expanded, True


def synthetic_subfeature_rules(system: dict[str, Any]) -> list[dict[str, Any]]:
    """Translate declarative feat subfeatures into runtime rule elements where safe."""
    sub = system.get("subfeatures") if isinstance(system.get("subfeatures"), dict) else {}
    profs = sub.get("proficiencies") if isinstance(sub.get("proficiencies"), dict) else {}
    out: list[dict[str, Any]] = []
    for key, spec in profs.items():
        if not isinstance(spec, dict):
            continue
        try:
            rank = int(spec.get("rank", 0))
        except (TypeError, ValueError):
            rank = 0
        if rank <= 0:
            continue
        key_slug = slug(key)
        if key_slug in {"fortitude", "reflex", "will"}:
            path = f"system.saves.{key_slug}.rank"
        elif key_slug == "perception":
            path = "system.attributes.perception.rank"
        elif key_slug == "spellcasting":
            path = "system.proficiencies.spellcasting.rank"
        else:
            path = f"system.proficiencies.{key_slug}.rank"
        out.append({
            "key": "ActiveEffectLike",
            "mode": "upgrade",
            "path": path,
            "value": rank,
            "_granSynthetic": "subfeatures.proficiencies",
        })
    return out


def simple_choice_sets(rules: list[dict[str, Any]]) -> list[dict[str, Any]]:
    out: list[dict[str, Any]] = []
    for rule in rules:
        if not isinstance(rule, dict) or rule.get("key") != "ChoiceSet":
            continue
        choices = rule.get("choices")
        normalized = []
        if isinstance(choices, list):
            for choice in choices:
                if isinstance(choice, dict) and "value" in choice:
                    normalized.append({"label": str(choice.get("label") or choice.get("value")), "value": choice.get("value")})
                elif isinstance(choice, (str, int, float)):
                    normalized.append({"label": str(choice), "value": choice})
        out.append({
            "flag": str(rule.get("flag") or ""),
            "prompt": str(rule.get("prompt") or ""),
            "adjustName": bool(rule.get("adjustName", False)),
            "choices": normalized,
            "dynamic": not isinstance(choices, list),
            "expandedPackQuery": "_granDynamicSource" in rule,
        })
    return out


def main() -> None:
    if not DB.exists():
        raise SystemExit("rules.db missing")
    db = sqlite3.connect(DB)
    counters: Counter[str] = Counter()
    updated = 0
    expanded_queries = 0
    synthetic_proficiencies = 0
    try:
        catalog = load_documents(db)
        for descriptor in catalog:
            doc = descriptor["doc"]
            data = descriptor["data"]
            system = doc.get("system") if isinstance(doc.get("system"), dict) else {}
            raw_rules = system.get("rules") if isinstance(system.get("rules"), list) else []
            rules: list[dict[str, Any]] = []
            for raw_rule in raw_rules:
                if not isinstance(raw_rule, dict):
                    continue
                rule, expanded = expand_pack_choice(raw_rule, catalog)
                expanded_queries += int(expanded)
                rules.append(rule)
            synthetic = synthetic_subfeature_rules(system)
            synthetic_proficiencies += len(synthetic)
            rules.extend(synthetic)

            traits_obj = system.get("traits") if isinstance(system.get("traits"), dict) else {}
            meta = data.setdefault("meta", {})
            meta["slug"] = descriptor["slug"]
            meta["sourcePack"] = descriptor["top"]
            meta["sourceId"] = descriptor["id"]
            meta["sourceType"] = descriptor["sourceType"]
            meta["otherTags"] = sorted(descriptor["otherTags"])
            meta["subfeatures"] = system.get("subfeatures") if isinstance(system.get("subfeatures"), dict) else {}
            meta["ruleElements"] = rules
            meta["choiceSets"] = simple_choice_sets(rules)
            for rule in rules:
                counters[str(rule.get("key") or "<unknown>")] += 1

            raw = json.dumps(data, ensure_ascii=False, separators=(",", ":"))
            db.execute("UPDATE rules SET json=? WHERE id=?", (raw, descriptor["id"]))
            updated += 1

        db.execute("PRAGMA user_version=5")
        db.commit()
    finally:
        db.close()

    print(f"Runtime metadata enriched rows: {updated}")
    print(f"Statically expanded ChoiceSet pack queries: {expanded_queries}")
    print(f"Synthetic proficiency effects: {synthetic_proficiencies}")
    print("Rule-element counts:")
    for key, count in counters.most_common():
        print(f"  {key}: {count}")

    if updated < 15000:
        raise SystemExit("Runtime enrichment unexpectedly incomplete")
    if counters["ChoiceSet"] < 100:
        raise SystemExit("Too few ChoiceSet rule elements")
    if counters["ActiveEffectLike"] < 100:
        raise SystemExit("Too few ActiveEffectLike rule elements")
    if counters["GrantItem"] < 100:
        raise SystemExit("Too few GrantItem rule elements")
    if expanded_queries < 10:
        raise SystemExit("Too few dynamic ChoiceSet pack queries expanded")
    if synthetic_proficiencies < 10:
        raise SystemExit("Too few subfeature proficiency effects")


if __name__ == "__main__":
    main()
