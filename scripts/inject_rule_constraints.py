#!/usr/bin/env python3
"""Preserve build-relevant feat constraints from the PF2e source model."""
from __future__ import annotations

import json
import sqlite3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CACHE = ROOT / "build" / "pf2e-source"
PACK = CACHE / "packs" / "pf2e"
DB = ROOT / "app" / "src" / "main" / "assets" / "rules.db"


def main() -> None:
    db = sqlite3.connect(DB)
    updated = only_level_1 = repeatable = 0
    try:
        for base_name in ("feats", "class-features"):
            base = PACK / base_name
            if not base.exists():
                continue
            for path in base.rglob("*.json"):
                if path.name.startswith("_"):
                    continue
                try:
                    doc = json.loads(path.read_text(encoding="utf-8"))
                except Exception:
                    continue
                row_id = str(doc.get("_id") or "")
                if not row_id:
                    continue
                row = db.execute("SELECT json FROM rules WHERE id=?", (row_id,)).fetchone()
                if not row:
                    continue
                data = json.loads(row[0])
                system = doc.get("system") if isinstance(doc.get("system"), dict) else {}
                meta = data.setdefault("meta", {})
                meta["onlyLevel1"] = bool(system.get("onlyLevel1", False))
                max_takable = system.get("maxTakable", 1)
                meta["maxTakable"] = max_takable
                action_type = system.get("actionType") if isinstance(system.get("actionType"), dict) else {}
                actions = system.get("actions") if isinstance(system.get("actions"), dict) else {}
                meta["actionType"] = str(action_type.get("value") or "passive")
                meta["actions"] = actions.get("value")
                meta["frequency"] = system.get("frequency")
                meta["selfEffect"] = system.get("selfEffect")
                db.execute("UPDATE rules SET json=? WHERE id=?", (json.dumps(data, ensure_ascii=False, separators=(",", ":")), row_id))
                updated += 1
                only_level_1 += int(meta["onlyLevel1"])
                repeatable += int(max_takable is None or (isinstance(max_takable, int) and max_takable > 1))
        db.commit()
    finally:
        db.close()
    print(f"Feat constraints enriched: {updated}; onlyLevel1: {only_level_1}; repeatable/unlimited: {repeatable}")
    if updated < 6500 or only_level_1 < 20 or repeatable < 20:
        raise SystemExit("Feat-constraint enrichment unexpectedly incomplete")


if __name__ == "__main__":
    main()
