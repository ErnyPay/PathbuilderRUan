#!/usr/bin/env python3
import html
import json
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CACHE = ROOT / "build" / "pf2e-source"
OUT = ROOT / "app" / "src" / "main" / "assets" / "rules.jsonl"
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
    value = system.get("level", 0)
    if isinstance(value, dict):
        value = value.get("value", 0)
    try:
        return int(value or 0)
    except (TypeError, ValueError):
        return 0


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
    traits = values((system.get("traits") or {}).get("value") if isinstance(system.get("traits"), dict) else system.get("traits"))
    description = system.get("description", {})
    if isinstance(description, dict):
        description = description.get("value", "")
    source = publication.get("title") or ((system.get("source") or {}).get("value") if isinstance(system.get("source"), dict) else system.get("source")) or ""
    meta = {}
    if category == "class":
        for key in ("classFeatLevels", "ancestryFeatLevels", "skillFeatLevels", "generalFeatLevels", "skillIncreaseLevels"):
            meta[key] = [int(x) for x in values(system.get(key)) if str(x).isdigit()]
        meta["hp"] = int(system.get("hp") or 0)
        meta["keyAbility"] = values(system.get("keyAbility"))
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
        "meta": meta,
    }


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
    OUT.parent.mkdir(parents=True, exist_ok=True)
    with OUT.open("w", encoding="utf-8") as f:
        for row in rows:
            f.write(json.dumps(row, ensure_ascii=False, separators=(",", ":")) + "\n")
    print(f"Wrote {len(rows)} rules to {OUT}")
    print(json.dumps(counts, ensure_ascii=False, indent=2, sort_keys=True))
    if len(rows) < 1000:
        raise SystemExit("Rules corpus unexpectedly small")


if __name__ == "__main__":
    main()
