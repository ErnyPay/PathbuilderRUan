#!/usr/bin/env python3
"""Build Russian presentation dictionaries from the PF2ERUS translation project.

Canonical rule data remains English and machine-readable. Russian names and text are
stored as presentation assets so prerequisites/group keys/UUIDs keep working.
"""
from __future__ import annotations

import json
import re
import shutil
import sqlite3
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app" / "src" / "main" / "assets"
DB = ASSETS / "rules.db"
CACHE = ROOT / "build" / "pf2erus"
TAG = "2.2.0"
CYR = re.compile(r"[А-Яа-яЁё]")


def run(*args: str) -> None:
    subprocess.run(args, check=True)


def ensure_source() -> None:
    if CACHE.exists():
        shutil.rmtree(CACHE)
    CACHE.parent.mkdir(parents=True, exist_ok=True)
    run("git", "clone", "--depth", "1", "--branch", TAG, "https://gitlab.com/gnuraco/pf2r.git", str(CACHE))


def is_ru(value) -> bool:
    return isinstance(value, str) and bool(CYR.search(value))


def plain(value):
    if not isinstance(value, str):
        return ""
    value = re.sub(r"<br\s*/?>", "\n", value, flags=re.I)
    value = re.sub(r"</p>", "\n", value, flags=re.I)
    value = re.sub(r"<[^>]+>", "", value)
    value = re.sub(r"@UUID\[[^]]+\](?:\{([^}]+)\})?", lambda m: m.group(1) or "", value)
    value = re.sub(r"\n{3,}", "\n\n", value)
    return value.strip()


def extract_description(obj):
    if not isinstance(obj, dict):
        return ""
    for key in ("description", "desc", "text"):
        raw = obj.get(key)
        if isinstance(raw, str) and is_ru(raw):
            return plain(raw)
        if isinstance(raw, dict):
            for sub in ("value", "text"):
                v = raw.get(sub)
                if isinstance(v, str) and is_ru(v):
                    return plain(v)
    system = obj.get("system")
    if isinstance(system, dict):
        d = system.get("description")
        if isinstance(d, dict):
            v = d.get("value")
            if isinstance(v, str) and is_ru(v):
                return plain(v)
        elif isinstance(d, str) and is_ru(d):
            return plain(d)
    return ""


def extract_prerequisites(obj):
    if not isinstance(obj, dict):
        return []
    candidates = []
    for container in (obj, obj.get("system") if isinstance(obj.get("system"), dict) else {}):
        raw = container.get("prerequisites") if isinstance(container, dict) else None
        if isinstance(raw, dict):
            raw = raw.get("value", raw)
        if isinstance(raw, str):
            raw = [raw]
        if isinstance(raw, list):
            for entry in raw:
                if isinstance(entry, dict):
                    entry = entry.get("value") or entry.get("label") or ""
                if isinstance(entry, str) and is_ru(entry):
                    candidates.append(plain(entry))
    return [x for x in candidates if x]


def normalized_key(s):
    return re.sub(r"[^a-z0-9]+", " ", str(s or "").lower()).strip()


def collect_translations():
    by_id = {}
    by_name = {}
    samples = []

    def remember(key, payload):
        if not isinstance(payload, dict):
            return
        name = payload.get("name")
        if not is_ru(name):
            # Some Babele payloads store translated text directly under label.
            label = payload.get("label")
            name = label if is_ru(label) else ""
        desc = extract_description(payload)
        prereqs = extract_prerequisites(payload)
        if not name and not desc and not prereqs:
            return
        record = {"name": name or "", "description": desc, "prerequisites": prereqs}
        if isinstance(key, str) and key:
            by_id.setdefault(key, record)
            by_name.setdefault(normalized_key(key), record)
        for idkey in ("_id", "id", "sourceId", "sourceID"):
            ident = payload.get(idkey)
            if isinstance(ident, str) and ident:
                by_id.setdefault(ident, record)
        english = payload.get("original") or payload.get("english") or payload.get("sourceName")
        if isinstance(english, str) and english:
            by_name.setdefault(normalized_key(english), record)
        if len(samples) < 30 and name:
            samples.append((str(key)[:80], name[:80]))

    def walk(node):
        if isinstance(node, dict):
            entries = node.get("entries")
            if isinstance(entries, dict):
                for key, payload in entries.items():
                    if isinstance(payload, dict):
                        remember(key, payload)
                    elif is_ru(payload):
                        remember(key, {"name": payload})
            # Older and module-specific translation files can be direct maps.
            for key, value in node.items():
                if key == "entries":
                    continue
                if isinstance(value, dict):
                    if is_ru(value.get("name")):
                        remember(key, value)
                    walk(value)
                elif isinstance(value, list):
                    walk(value)
                elif is_ru(value) and isinstance(key, str) and re.search(r"[A-Za-z]", key):
                    # Keep this low priority: it often catches UI labels, but can still
                    # translate canonical PF2e names if no richer payload exists.
                    by_name.setdefault(normalized_key(key), {"name": value, "description": "", "prerequisites": []})
        elif isinstance(node, list):
            for x in node:
                walk(x)

    files = 0
    for path in CACHE.rglob("*.json"):
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
        except Exception:
            continue
        files += 1
        walk(data)
    return by_id, by_name, samples, files


def main():
    if not DB.exists():
        raise SystemExit(f"Missing {DB}; build rules first")
    ensure_source()
    by_id, by_name, samples, files = collect_translations()
    print("PF2ERUS JSON files:", files, "id candidates:", len(by_id), "name candidates:", len(by_name))
    print("PF2ERUS samples:", samples[:12])

    conn = sqlite3.connect(DB)
    rows = conn.execute("SELECT id,name,json FROM rules").fetchall()
    names = {}
    texts = {}
    matched_name = matched_desc = matched_prereq = 0

    for ident, english, raw in rows:
        record = by_id.get(ident) or by_name.get(normalized_key(english))
        if not record:
            continue
        ru_name = str(record.get("name") or "").strip()
        ru_desc = str(record.get("description") or "").strip()
        ru_prereqs = [str(x).strip() for x in (record.get("prerequisites") or []) if str(x).strip()]
        if ru_name and is_ru(ru_name):
            names[english] = ru_name
            matched_name += 1
        if ru_desc and is_ru(ru_desc):
            matched_desc += 1
        if ru_prereqs:
            matched_prereq += 1
        if ru_name or ru_desc or ru_prereqs:
            texts[ident] = {
                "english": english,
                "name": ru_name,
                "description": ru_desc,
                "prerequisites": ru_prereqs,
            }
            obj = json.loads(raw)
            meta = obj.setdefault("meta", {})
            if ru_name: meta["ruName"] = ru_name
            if ru_desc: meta["ruDescription"] = ru_desc
            if ru_prereqs: meta["ruPrerequisites"] = ru_prereqs
            conn.execute("UPDATE rules SET json=? WHERE id=?", (json.dumps(obj, ensure_ascii=False, separators=(",", ":")), ident))

    conn.commit()
    conn.close()
    ASSETS.mkdir(parents=True, exist_ok=True)
    (ASSETS / "ru_names.json").write_text(json.dumps(names, ensure_ascii=False, sort_keys=True, separators=(",", ":")), encoding="utf-8")
    (ASSETS / "ru_text.json").write_text(json.dumps(texts, ensure_ascii=False, sort_keys=True, separators=(",", ":")), encoding="utf-8")

    licenses = ASSETS / "licenses"
    licenses.mkdir(parents=True, exist_ok=True)
    for source_name, dest_name in (("LICENSE", "PF2ERUS_LICENSE.txt"), ("README.md", "PF2ERUS_SOURCE.md")):
        src = CACHE / source_name
        if src.exists():
            shutil.copyfile(src, licenses / dest_name)

    print("Russian matched names:", matched_name, "descriptions:", matched_desc, "prerequisites:", matched_prereq)
    print("ru_names bytes:", (ASSETS / "ru_names.json").stat().st_size, "ru_text bytes:", (ASSETS / "ru_text.json").stat().st_size)
    # A low floor intentionally catches broken source/layout changes while allowing partial
    # coverage on a new PF2e data revision. CI prints the exact measured coverage.
    if matched_name < 500:
        raise SystemExit(f"Russian dictionary coverage unexpectedly low: {matched_name}")


if __name__ == "__main__":
    main()
