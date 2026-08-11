#!/usr/bin/env python3
"""Build a conservative Russian presentation dictionary from PF2ERUS.

Canonical PF2e rule data stays English and machine-readable. Translation records are
accepted only when their identity can be matched safely; a missing Russian label is
preferable to putting an unrelated sentence into a feat/class name.
"""
from __future__ import annotations

import json
import re
import shutil
import sqlite3
import subprocess
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app" / "src" / "main" / "assets"
DB = ASSETS / "rules.db"
CACHE = ROOT / "build" / "pf2erus"
TAG = "2.2.0"
CYR = re.compile(r"[А-Яа-яЁё]")
RULE_MARKUP = re.compile(r"@(?:UUID|Damage|Check|Template|Localize|Roll|FlatCheck)|</?\w+|\{[^}]{3,}\}", re.I)

# Small trusted vocabulary used both as a fallback and an integrity anchor. External
# dictionaries may enrich this list but never replace these labels.
CORE_NAMES = {
    "Alchemist":"Алхимик","Animist":"Анимист","Barbarian":"Варвар","Bard":"Бард",
    "Champion":"Чемпион","Cleric":"Клирик","Commander":"Командир","Druid":"Друид",
    "Exemplar":"Экземпляр","Fighter":"Воин","Guardian":"Страж","Gunslinger":"Стрелок",
    "Inventor":"Изобретатель","Investigator":"Следователь","Kineticist":"Кинетик",
    "Magus":"Магус","Monk":"Монах","Oracle":"Оракул","Psychic":"Психик",
    "Ranger":"Следопыт","Rogue":"Плут","Sorcerer":"Чародей","Summoner":"Призыватель",
    "Swashbuckler":"Сорвиголова","Thaumaturge":"Тауматург","Witch":"Ведьма","Wizard":"Волшебник",
    "Human":"Человек","Dwarf":"Дварф","Elf":"Эльф","Gnome":"Гном","Goblin":"Гоблин",
    "Halfling":"Полурослик","Orc":"Орк","Kobold":"Кобольд","Leshy":"Леший",
    "Catfolk":"Кошколюд","Tengu":"Тэнгу","Android":"Андроид",
    "Frightened":"Испуган","Sickened":"Тошнота","Clumsy":"Неуклюж","Enfeebled":"Ослаблен",
    "Stupefied":"Одурманен","Slowed":"Замедлен","Quickened":"Ускорен","Dying":"При смерти",
    "Wounded":"Ранен","Prone":"Лежит","Grabbed":"Схвачен","Restrained":"Обездвижен",
}


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


def looks_like_name(value) -> bool:
    """Reject prose/rule markup masquerading as a translated item name."""
    if not is_ru(value):
        return False
    value = str(value).strip()
    if not value or "\n" in value or "\r" in value:
        return False
    if RULE_MARKUP.search(value):
        return False
    if len(value) > 80:
        return False
    words = re.findall(r"[A-Za-zА-Яа-яЁё0-9]+", value)
    if len(words) > 10:
        return False
    # Names can contain commas/parentheses, but a full sentence is not a safe label.
    if value.endswith((".", "!", "?", ";")):
        return False
    return True


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


def source_english(payload):
    if not isinstance(payload, dict):
        return ""
    value = payload.get("original") or payload.get("english") or payload.get("sourceName") or payload.get("originalName")
    return value.strip() if isinstance(value, str) else ""


def collect_translations():
    by_id = defaultdict(list)
    by_name = defaultdict(list)
    samples = []

    def remember(key, payload):
        if not isinstance(payload, dict):
            return
        name = payload.get("name")
        if not is_ru(name):
            label = payload.get("label")
            name = label if is_ru(label) else ""
        desc = extract_description(payload)
        prereqs = extract_prerequisites(payload)
        english = source_english(payload)
        if not name and not desc and not prereqs:
            return
        record = {
            "name": str(name or "").strip(),
            "description": desc,
            "prerequisites": prereqs,
            "source_english": english,
            "source_key": str(key or ""),
        }
        if isinstance(key, str) and key:
            # Only the key-as-name index can be exact-name matched; IDs are also stored below.
            by_name[normalized_key(key)].append(record)
            by_id[key].append(record)
        for idkey in ("_id", "id", "sourceId", "sourceID"):
            ident = payload.get(idkey)
            if isinstance(ident, str) and ident:
                by_id[ident].append(record)
        if english:
            by_name[normalized_key(english)].append(record)
        if len(samples) < 30 and looks_like_name(record["name"]):
            samples.append((str(key)[:80], record["name"][:80]))

    def walk(node):
        if isinstance(node, dict):
            entries = node.get("entries")
            if isinstance(entries, dict):
                for key, payload in entries.items():
                    if isinstance(payload, dict):
                        remember(key, payload)
                    elif is_ru(payload):
                        remember(key, {"name": payload})
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
                    # Direct UI maps are name candidates only when they actually resemble a label.
                    if looks_like_name(value):
                        by_name[normalized_key(key)].append({
                            "name": value.strip(), "description": "", "prerequisites": [],
                            "source_english": str(key), "source_key": str(key),
                        })
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


def choose_record(ident, english, by_id, by_name):
    wanted = normalized_key(english)
    candidates = []
    # Remember provenance: exact canonical-name matching is much safer than a bare ID collision.
    for r in by_name.get(wanted, []):
        candidates.append((r, "name"))
    for r in by_id.get(ident, []):
        candidates.append((r, "id"))
    if not candidates:
        return None, ""

    best = None
    best_score = -1
    best_origin = ""
    for record, origin in candidates:
        src = normalized_key(record.get("source_english"))
        key = normalized_key(record.get("source_key"))
        safe_name = looks_like_name(record.get("name"))
        score = 0
        if src and src == wanted:
            score += 100
        if key and key == wanted:
            score += 80
        if origin == "name":
            score += 50
        if safe_name:
            score += 20
        if record.get("description"):
            score += 2
        if score > best_score:
            best, best_score, best_origin = record, score, origin

    # A bare ID with no corroborating English identity is accepted only for a plausible short label;
    # description/prerequisites from such an ambiguous record are intentionally discarded later.
    if best is None:
        return None, ""
    exact_identity = normalized_key(best.get("source_english")) == wanted or normalized_key(best.get("source_key")) == wanted or best_origin == "name"
    if not exact_identity and not looks_like_name(best.get("name")):
        return None, ""
    return best, ("exact" if exact_identity else "id-only")


def main():
    if not DB.exists():
        raise SystemExit(f"Missing {DB}; build rules first")
    ensure_source()
    by_id, by_name, samples, files = collect_translations()
    print("PF2ERUS JSON files:", files, "id keys:", len(by_id), "name keys:", len(by_name))
    print("PF2ERUS safe samples:", samples[:12])

    conn = sqlite3.connect(DB)
    rows = conn.execute("SELECT id,name,json FROM rules").fetchall()
    names = dict(CORE_NAMES)
    texts = {}
    matched_name = len(CORE_NAMES)
    matched_desc = matched_prereq = rejected_names = ambiguous_records = 0

    for ident, english, raw in rows:
        record, confidence = choose_record(ident, english, by_id, by_name)
        ru_name = ""
        ru_desc = ""
        ru_prereqs = []
        if record:
            candidate_name = str(record.get("name") or "").strip()
            if looks_like_name(candidate_name):
                ru_name = candidate_name
            elif candidate_name:
                rejected_names += 1
            # Never attach long prose from a record whose only evidence is a potentially colliding ID.
            if confidence == "exact":
                ru_desc = str(record.get("description") or "").strip()
                ru_prereqs = [str(x).strip() for x in (record.get("prerequisites") or []) if str(x).strip()]
            else:
                ambiguous_records += 1

        if english in CORE_NAMES:
            ru_name = CORE_NAMES[english]

        if ru_name and is_ru(ru_name):
            if english not in names:
                matched_name += 1
            names[english] = ru_name
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
            # Remove stale generated translation fields before writing validated values.
            meta.pop("ruName", None); meta.pop("ruDescription", None); meta.pop("ruPrerequisites", None)
            if ru_name: meta["ruName"] = ru_name
            if ru_desc: meta["ruDescription"] = ru_desc
            if ru_prereqs: meta["ruPrerequisites"] = ru_prereqs
            conn.execute("UPDATE rules SET json=? WHERE id=?", (json.dumps(obj, ensure_ascii=False, separators=(",", ":")), ident))

    # Defensive integrity pass: no generated rule label may look like prose/markup.
    suspicious = []
    for ident, english, raw in conn.execute("SELECT id,name,json FROM rules"):
        meta = json.loads(raw).get("meta", {})
        ru = str(meta.get("ruName") or "").strip()
        if ru and not looks_like_name(ru):
            suspicious.append((ident, english, ru[:120]))
    if suspicious:
        raise SystemExit(f"Unsafe Russian names survived validation: {suspicious[:10]}")

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

    print("Russian matched names:", len(names), "descriptions:", matched_desc, "prerequisites:", matched_prereq)
    print("Rejected prose names:", rejected_names, "ambiguous ID-only records:", ambiguous_records)
    print("ru_names bytes:", (ASSETS / "ru_names.json").stat().st_size, "ru_text bytes:", (ASSETS / "ru_text.json").stat().st_size)
    if len(names) < 500:
        raise SystemExit(f"Russian dictionary coverage unexpectedly low: {len(names)}")
    for english, expected in {"Wizard":"Волшебник","Bard":"Бард","Fighter":"Воин","Human":"Человек"}.items():
        if names.get(english) != expected:
            raise SystemExit(f"Core dictionary corruption: {english} -> {names.get(english)!r}")


if __name__ == "__main__":
    main()
