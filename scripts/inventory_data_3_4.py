#!/usr/bin/env python3
"""Enrich Gran rules.db with PF2e Bulk/container metadata."""
import json, sqlite3
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
CACHE=ROOT/'build'/'pf2e-source'/'packs'/'pf2e'/'equipment'
DB=ROOT/'app'/'src'/'main'/'assets'/'rules.db'


def obj(path):
    try:d=json.loads(path.read_text(encoding='utf-8'))
    except Exception:return None
    return d if isinstance(d,dict) else None


def number(v,default=0.0):
    try:return float(v)
    except Exception:return default


def main():
    if not DB.exists():raise SystemExit('rules.db missing')
    db=sqlite3.connect(DB);updated=containers=0
    try:
        for path in CACHE.rglob('*.json'):
            doc=obj(path)
            if doc is None:continue
            ident=str(doc.get('_id') or '');system=doc.get('system') or {};bulk=system.get('bulk') or {}
            if not ident or not isinstance(bulk,dict):continue
            row=db.execute('select json from rules where id=?',(ident,)).fetchone()
            if not row:continue
            data=json.loads(row[0]);meta=data.setdefault('meta',{})
            meta['bulkValue']=round(number(bulk.get('value'),0),1)
            meta['bulkCapacity']=round(number(bulk.get('capacity'),0),1)
            meta['bulkIgnored']=round(number(bulk.get('ignored'),0),1)
            meta['bulkHeldOrStowed']=round(number(bulk.get('heldOrStowed'),meta['bulkValue']),1)
            meta['stowing']=bool(system.get('stowing',False))
            meta['size']=str(system.get('size') or 'med')
            if meta['bulkCapacity']>0 or doc.get('type')=='backpack':containers+=1
            db.execute('update rules set json=? where id=?',(json.dumps(data,ensure_ascii=False,separators=(',',':')),ident));updated+=1
        db.commit()
    finally:db.close()
    print('Bulk enriched equipment',updated,'containers',containers)
    if updated<5000 or containers<5:raise SystemExit('Bulk/container enrichment incomplete')

if __name__=='__main__':main()
