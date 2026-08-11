#!/usr/bin/env python3
"""Enrich rules.db with PF2e language/Lore subfeature metadata for Gran 3.3."""
import json
import sqlite3
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
CACHE=ROOT/'build'/'pf2e-source'/'packs'/'pf2e'
ASSETS=ROOT/'app'/'src'/'main'/'assets'
DB=ASSETS/'rules.db'
RU_NAMES=ASSETS/'ru_names.json'


def update(db, ident, mutate):
    row=db.execute('select json from rules where id=?',(ident,)).fetchone()
    if not row:return False
    data=json.loads(row[0]); mutate(data)
    db.execute('update rules set json=? where id=?',(json.dumps(data,ensure_ascii=False,separators=(',',':')),ident));return True


def arr(value):
    if isinstance(value,dict):value=value.get('value',[])
    return [str(x) for x in value] if isinstance(value,list) else []


def load_object(path):
    try:
        doc=json.loads(path.read_text(encoding='utf-8'))
    except Exception:
        return None
    return doc if isinstance(doc,dict) else None


def patch_ui_terms():
    try: names=json.loads(RU_NAMES.read_text(encoding='utf-8'))
    except Exception: names={}
    # Stable game terms: PF2ERUS already translates Additional Lore; Scribing Lore has no
    # safe global match in the source dictionary, so keep this tiny reviewed fallback here.
    names['Additional Lore']='Дополнительные знания'
    names['Scribing Lore']='Знания писца'
    RU_NAMES.write_text(json.dumps(names,ensure_ascii=False,sort_keys=True,separators=(',',':')),encoding='utf-8')


def main():
    if not DB.exists(): raise SystemExit('rules.db missing')
    db=sqlite3.connect(DB); ancestries=feats=skipped_non_objects=0
    try:
        for path in (CACHE/'ancestries').rglob('*.json'):
            doc=load_object(path)
            if doc is None:
                skipped_non_objects+=1
                continue
            ident=str(doc.get('_id') or ''); system=doc.get('system') or {}; extra=system.get('additionalLanguages') or {}
            if not ident or not isinstance(extra,dict):continue
            choices=arr(extra.get('value'))
            count=int(extra.get('count') or 0)
            def mutate(data,choices=choices,count=count):
                meta=data.setdefault('meta',{});meta['additionalLanguages']=count;meta['additionalLanguageChoices']=choices
            ancestries+=int(update(db,ident,mutate))

        for path in (CACHE/'feats').rglob('*.json'):
            doc=load_object(path)
            if doc is None:
                skipped_non_objects+=1
                continue
            ident=str(doc.get('_id') or ''); system=doc.get('system') or {}; sub=system.get('subfeatures') or {}
            langs=sub.get('languages') if isinstance(sub,dict) else None
            if not ident or not isinstance(langs,dict):continue
            slots=int(langs.get('slots') or 0); granted=arr(langs.get('granted'))
            if slots<=0 and not granted:continue
            def mutate(data,slots=slots,granted=granted):
                meta=data.setdefault('meta',{});meta['languageSlots']=slots;meta['grantedLanguages']=granted
            feats+=int(update(db,ident,mutate))
        db.commit()
    finally: db.close()
    patch_ui_terms()
    print('Knowledge enrichment: ancestries',ancestries,'feats with language subfeatures',feats,'skipped non-object JSON',skipped_non_objects)
    if ancestries < 40: raise SystemExit('Ancestry language enrichment incomplete')

if __name__=='__main__':main()
