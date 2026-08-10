#!/usr/bin/env python3
import html
import json
import re
import sqlite3
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CACHE = ROOT / "build" / "pf2e-source"
ASSETS = ROOT / "app" / "src" / "main" / "assets"
OUT_DB = ASSETS / "rules.db"
PACK = CACHE / "packs" / "pf2e"

PACKS = [
    "classes", "class-features", "ancestries", "heritages", "backgrounds",
    "feats", "spells", "equipment", "actions", "conditions", "deities"
]


def run(*args):
    subprocess.run(args, check=True)


def ensure_source():
    if not (CACHE / ".git").exists():
        CACHE.parent.mkdir(parents=True, exist_ok=True)
        run("git", "clone", "--depth", "1", "--branch", "v14-dev", "--filter=blob:none", "--sparse",
            "https://github.com/foundryvtt/pf2e.git", str(CACHE))
    run("git", "-C", str(CACHE), "sparse-checkout", "set", *[f"packs/pf2e/{p}" for p in PACKS])
    run("git", "-C", str(CACHE), "checkout", "v14-dev")


def plain(value):
    if not value:
        return ""
    value = html.unescape(str(value))
    value = re.sub(r"<br\s*/?>", "\n", value, flags=re.I)
    value = re.sub(r"</p>", "\n", value, flags=re.I)
    value = re.sub(r"<[^>]+>", "", value)
    value = re.sub(r"@UUID\[[^]]+\](?:\{([^}]+)\})?", lambda m: m.group(1) or "", value)
    value = re.sub(r"@Check\[[^]]+\](?:\{([^}]+)\})?", lambda m: m.group(1) or "проверка", value)
    value = re.sub(r"\n{3,}", "\n\n", value)
    return value.strip()


def values(x):
    if x is None:
        return []
    if isinstance(x, dict) and "value" in x:
        x = x["value"]
    if isinstance(x, list):
        return [str(v) for v in x if v is not None]
    return [str(x)]


def number(x, default=0):
    if isinstance(x, dict) and "value" in x:
        x = x.get("value")
    try:
        return int(x)
    except (TypeError, ValueError):
        return default


def value(x, default=None):
    if isinstance(x, dict) and "value" in x:
        return x.get("value", default)
    return default if x is None else x


def boosts(raw):
    out = []
    if not isinstance(raw, dict):
        return out
    for key in sorted(raw):
        entry = raw.get(key) or {}
        vals = values(entry)
        if vals:
            out.append(vals)
    return out


def feature_list(raw):
    if not isinstance(raw, dict):
        return []
    out = []
    for entry in raw.values():
        if not isinstance(entry, dict) or not entry.get("name"):
            continue
        out.append({
            "name": str(entry.get("name")),
            "level": number(entry.get("level"), 1),
            "uuid": str(entry.get("uuid") or ""),
        })
    out.sort(key=lambda x: (x["level"], x["name"].lower()))
    return out


def prereqs(system):
    raw = system.get("prerequisites", {})
    raw = raw.get("value", raw) if isinstance(raw, dict) else raw
    out = []
    if not isinstance(raw, list):
        raw = [raw] if raw else []
    for entry in raw:
        if isinstance(entry, dict):
            entry = entry.get("value") or entry.get("label") or ""
        text = plain(entry)
        if text:
            out.append(text)
    return out


def level_of(system):
    return number(system.get("level"), 0)


def category_for(top, rel, doc):
    if top == "feats":
        parts = rel.parts
        subtype = parts[0] if len(parts) > 1 else str(doc.get("system", {}).get("category", "feat"))
        return "feat", subtype
    mapping = {
        "classes": ("class", "class"),
        "class-features": ("class-feature", "class-feature"),
        "ancestries": ("ancestry", "ancestry"),
        "heritages": ("heritage", "heritage"),
        "backgrounds": ("background", "background"),
        "spells": ("spell", "spell"),
        "actions": ("action", "action"),
        "conditions": ("condition", "condition"),
        "deities": ("deity", "deity"),
    }
    if top == "equipment":
        return "equipment", str(doc.get("type") or doc.get("system", {}).get("category") or "equipment")
    return mapping.get(top, (top, top))


def mechanic_meta(category, subtype, system):
    meta = {}
    if category == "class":
        for key in ("classFeatLevels", "ancestryFeatLevels", "skillFeatLevels", "generalFeatLevels", "skillIncreaseLevels"):
            meta[key] = [int(x) for x in values(system.get(key)) if str(x).isdigit()]
        meta.update({
            "hp": number(system.get("hp"), 0),
            "keyAbility": values(system.get("keyAbility")),
            "attacks": system.get("attacks") or {},
            "defenses": system.get("defenses") or {},
            "savingThrows": system.get("savingThrows") or {},
            "perception": number(system.get("perception"), 0),
            "spellcasting": number(system.get("spellcasting"), 0),
            "trainedSkills": system.get("trainedSkills") or {},
            "features": feature_list(system.get("items")),
        })
    elif category == "ancestry":
        meta.update({
            "hp": number(system.get("hp"), 0),
            "speed": number(system.get("speed"), 25),
            "size": str(value(system.get("size"), "med") or "med"),
            "boosts": boosts(system.get("boosts")),
            "flaws": boosts(system.get("flaws")),
            "languages": values(system.get("languages")),
            "additionalLanguages": number((system.get("additionalLanguages") or {}).get("count") if isinstance(system.get("additionalLanguages"), dict) else 0, 0),
            "vision": str(value(system.get("vision"), "normal") or "normal"),
        })
    elif category == "background":
        trained = system.get("trainedSkills") or {}
        meta.update({
            "boosts": boosts(system.get("boosts")),
            "trainedSkills": values(trained.get("value") if isinstance(trained, dict) else []),
            "lore": values(trained.get("lore") if isinstance(trained, dict) else []),
            "features": feature_list(system.get("items")),
        })
    elif category == "heritage":
        meta.update({
            "ancestry": str(system.get("ancestry") or ""),
        })
    elif category == "equipment":
        damage = system.get("damage") if isinstance(system.get("damage"), dict) else {}
        armor = system.get("acBonus")
        runes = system.get("runes") if isinstance(system.get("runes"), dict) else {}
        price = system.get("price") if isinstance(system.get("price"), dict) else {}
        meta.update({
            "itemType": subtype,
            "baseItem": str(system.get("baseItem") or ""),
            "itemCategory": str(system.get("category") or ""),
            "group": str(system.get("group") or ""),
            "damageDice": number(damage.get("dice"), 0),
            "damageDie": str(damage.get("die") or ""),
            "damageType": str(damage.get("damageType") or ""),
            "bonus": number(system.get("bonus"), 0),
            "bonusDamage": number(system.get("bonusDamage"), 0),
            "range": value(system.get("range"), None),
            "reload": str(value(system.get("reload"), "") or ""),
            "potency": number(runes.get("potency"), 0),
            "striking": number(runes.get("striking"), 0),
            "propertyRunes": values(runes.get("property")),
            "acBonus": number(armor, 0),
            "dexCap": number(system.get("dexCap"), 99),
            "checkPenalty": number(system.get("checkPenalty"), 0),
            "speedPenalty": number(system.get("speedPenalty"), 0),
            "strength": number(system.get("strength"), 0),
            "bulk": value(system.get("bulk"), 0),
            "price": value(price, {}),
            "usage": str(value(system.get("usage"), "") or ""),
            "quantity": number(system.get("quantity"), 1),
            "hardness": number(system.get("hardness"), 0),
            "hp": system.get("hp") or {},
            "splashDamage": number(system.get("splashDamage"), 0),
        })
    elif category == "spell":
        traits = system.get("traits") if isinstance(system.get("traits"), dict) else {}
        meta.update({
            "traditions": values(traits.get("traditions")),
            "time": str(value(system.get("time"), "") or ""),
            "range": str(value(system.get("range"), "") or ""),
            "target": str(value(system.get("target"), "") or ""),
            "duration": str(value(system.get("duration"), "") or ""),
            "area": system.get("area"),
            "defense": system.get("defense"),
            "damage": system.get("damage") or {},
            "requirements": str(system.get("requirements") or ""),
            "cost": str(value(system.get("cost"), "") or ""),
        })
    return meta


def normalize(top, path):
    try:
        doc = json.loads(path.read_text(encoding="utf-8"))
    except Exception:
        return None
    if not isinstance(doc, dict) or not doc.get("name") or path.name.startswith("_"):
        return None
    system = doc.get("system") or {}
    publication = system.get("publication") or {}
    license_name = str(publication.get("license") or "")
    if license_name and license_name.upper() not in {"ORC", "OGL"}:
        return None
    rel = path.relative_to(PACK / top)
    category, subtype = category_for(top, rel, doc)
    traits_object = system.get("traits") if isinstance(system.get("traits"), dict) else {}
    traits = values(traits_object.get("value") if traits_object else system.get("traits"))
    description = system.get("description", {})
    if isinstance(description, dict):
        description = description.get("value", "")
    source = publication.get("title") or ((system.get("source") or {}).get("value") if isinstance(system.get("source"), dict) else system.get("source")) or ""
    return {
        "id": str(doc.get("_id") or f"{top}:{rel.as_posix()}"),
        "name": str(doc.get("name")),
        "category": category,
        "subtype": subtype,
        "level": level_of(system),
        "description": plain(description),
        "source": str(source),
        "license": license_name,
        "traits": traits,
        "prerequisites": prereqs(system),
        "meta": mechanic_meta(category, subtype, system),
    }


def write_database(rows):
    ASSETS.mkdir(parents=True, exist_ok=True)
    if OUT_DB.exists():
        OUT_DB.unlink()
    db = sqlite3.connect(OUT_DB)
    try:
        db.execute("PRAGMA journal_mode=DELETE")
        db.execute("PRAGMA synchronous=OFF")
        db.execute("CREATE TABLE rules (id TEXT PRIMARY KEY, name TEXT NOT NULL, category TEXT NOT NULL, subtype TEXT, level INTEGER NOT NULL, json TEXT NOT NULL)")
        db.execute("CREATE INDEX idx_rules_category_level ON rules(category, level)")
        db.execute("CREATE INDEX idx_rules_name ON rules(name COLLATE NOCASE)")
        payload = []
        for row in rows:
            raw = json.dumps(row, ensure_ascii=False, separators=(",", ":"))
            payload.append((row["id"], row["name"], row["category"], row["subtype"], row["level"], raw))
        db.executemany("INSERT OR REPLACE INTO rules(id,name,category,subtype,level,json) VALUES(?,?,?,?,?,?)", payload)
        db.execute("PRAGMA user_version=2")
        db.commit()
        result = db.execute("SELECT COUNT(*) FROM rules").fetchone()[0]
        if result != len(rows):
            raise SystemExit(f"Database count mismatch: {result} vs {len(rows)}")
    finally:
        db.close()


def main():
    ensure_source()
    rows = []
    counts = {}
    for top in PACKS:
        base = PACK / top
        if not base.exists():
            continue
        for path in base.rglob("*.json"):
            row = normalize(top, path)
            if row:
                rows.append(row)
                key = row["category"] + (":" + row["subtype"] if row["category"] == "feat" else "")
                counts[key] = counts.get(key, 0) + 1
    rows.sort(key=lambda x: (x["category"], x["subtype"], x["level"], x["name"].lower()))
    if len(rows) < 1000:
        raise SystemExit("Rules corpus unexpectedly small")
    write_database(rows)
    print(f"Wrote {len(rows)} rules to {OUT_DB} ({OUT_DB.stat().st_size} bytes)")
    print(json.dumps(counts, ensure_ascii=False, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
