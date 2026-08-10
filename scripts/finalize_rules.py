#!/usr/bin/env python3
import json
import shutil
import sqlite3
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CACHE = ROOT / "build" / "pf2e-source"
ASSETS = ROOT / "app" / "src" / "main" / "assets"
DB = ASSETS / "rules.db"

FIXED_TRADITIONS = {
    "Animist": ["divine"],
    "Bard": ["occult"],
    "Cleric": ["divine"],
    "Druid": ["primal"],
    "Magus": ["arcane"],
    "Oracle": ["divine"],
    "Psychic": ["occult"],
    "Wizard": ["arcane"],
}


def update_row(db, row_id, mutate):
    row = db.execute("SELECT json FROM rules WHERE id=?", (row_id,)).fetchone()
    if not row:
        return False
    data = json.loads(row[0])
    mutate(data)
    raw = json.dumps(data, ensure_ascii=False, separators=(",", ":"))
    db.execute("UPDATE rules SET json=? WHERE id=?", (raw, row_id))
    return True


def enrich_heritages(db):
    base = CACHE / "packs" / "pf2e" / "heritages"
    count = 0
    versatile = 0
    for path in base.rglob("*.json"):
        try:
            doc = json.loads(path.read_text(encoding="utf-8"))
        except Exception:
            continue
        if not isinstance(doc, dict):
            continue
        row_id = str(doc.get("_id") or "")
        if not row_id:
            continue
        system = doc.get("system") or {}
        if not isinstance(system, dict):
            continue
        ancestry = system.get("ancestry")
        ancestry_name = ancestry.get("name", "") if isinstance(ancestry, dict) else ""
        is_versatile = ancestry is None
        def mutate(data):
            meta = data.setdefault("meta", {})
            meta["ancestry"] = ancestry_name
            meta["versatile"] = bool(is_versatile)
        if update_row(db, row_id, mutate):
            count += 1
            versatile += int(is_versatile)
    return count, versatile


def enrich_classes(db):
    count = 0
    for name, traditions in FIXED_TRADITIONS.items():
        row = db.execute("SELECT id,json FROM rules WHERE category='class' AND name=? COLLATE NOCASE LIMIT 1", (name,)).fetchone()
        if not row:
            continue
        data = json.loads(row[1])
        data.setdefault("meta", {})["traditions"] = traditions
        db.execute("UPDATE rules SET json=? WHERE id=?", (json.dumps(data, ensure_ascii=False, separators=(",", ":")), row[0]))
        count += 1
    return count


def copy_licenses():
    subprocess.run(["git", "-C", str(CACHE), "sparse-checkout", "add", "static/licenses"], check=True)
    source = CACHE / "static" / "licenses"
    target = ASSETS / "licenses"
    target.mkdir(parents=True, exist_ok=True)
    copied = []
    for name in ("ORCLicense.md", "OpenGameLicense.md"):
        src = source / name
        if src.exists():
            shutil.copyfile(src, target / name)
            copied.append(name)
    commit = subprocess.check_output(["git", "-C", str(CACHE), "rev-parse", "HEAD"], text=True).strip()
    notice = (
        "Gran 2e — third-party notices\n\n"
        "Rules data is generated from the open-source foundryvtt/pf2e data repository.\n"
        f"Source revision: {commit}\n"
        "Individual rule records are included only when their publication license is ORC, OGL, or unspecified by the source record.\n"
        "The ORC and Open Game License texts distributed by the upstream rules project are bundled in assets/licenses.\n"
        "Pathfinder and related trademarks are property of Paizo Inc. Gran 2e is an independent application and is not endorsed by Paizo.\n"
        "No Pathbuilder code, logo, or proprietary visual assets are included.\n"
    )
    (ASSETS / "THIRD_PARTY_NOTICES.txt").write_text(notice, encoding="utf-8")
    return copied, commit


def main():
    if not DB.exists():
        raise SystemExit("rules.db not found; run build_rules.py first")
    db = sqlite3.connect(DB)
    try:
        heritage_count, versatile_count = enrich_heritages(db)
        class_count = enrich_classes(db)
        db.execute("PRAGMA user_version=4")
        db.commit()
    finally:
        db.close()
    copied, commit = copy_licenses()
    print(f"Enriched heritages: {heritage_count}; versatile: {versatile_count}")
    print(f"Classes with fixed spell traditions: {class_count}")
    print(f"Copied licenses: {copied}")
    print(f"Rules source revision: {commit}")
    if heritage_count < 250 or versatile_count < 10:
        raise SystemExit("Heritage enrichment unexpectedly incomplete")
    if class_count != len(FIXED_TRADITIONS):
        raise SystemExit("Class tradition enrichment incomplete")
    if len(copied) != 2:
        raise SystemExit("Required license files missing")


if __name__ == "__main__":
    main()
