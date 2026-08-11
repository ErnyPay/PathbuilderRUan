#!/usr/bin/env python3
from pathlib import Path
import base64, io, tarfile

ROOT=Path(__file__).resolve().parents[1]


def main():
    scripts=ROOT/'scripts'
    # The payload is intentionally chunked for the repository connector. 05 and 06
    # were uploaded in reverse physical order, so restore the exact original stream.
    order=[0,1,2,3,4,6,5]
    chunks=[scripts/f'final_product_8_0_payload_{i:02d}.b64' for i in order]
    missing=[str(p) for p in chunks if not p.exists()]
    if missing: raise SystemExit('final product payload missing: '+', '.join(missing))
    encoded=''.join(p.read_text(encoding='ascii').strip() for p in chunks)
    payload=base64.b64decode(encoded, validate=True)
    with tarfile.open(fileobj=io.BytesIO(payload),mode='r:gz') as tf:
        for member in tf.getmembers():
            target=(ROOT/member.name).resolve()
            if ROOT.resolve() not in target.parents and target != ROOT.resolve():
                raise SystemExit('unsafe payload path')
        tf.extractall(ROOT)
    checks={
        'app/src/main/java/ru/gran/edge2e/FrontPageActivity.java':['home-new-character','CharacterSetupActivity.class','GranArchive.importCharacter'],
        'app/src/main/java/ru/gran/edge2e/CharacterSetupActivity.java':['setup-result-','setup-option-','unresolvedRequired','ReferenceBuildActivity.class'],
        'app/src/main/java/ru/gran/edge2e/ReferenceBuildActivity.java':['build-play','CharacterProfiles.saveCurrent','runtime.choices()'],
        'app/src/main/java/ru/gran/edge2e/ReferencePlayActivity.java':['play-tab-','spell-picker-','equipment-picker-','condition-option-','pet-add-','play-level-next'],
        'app/src/main/java/ru/gran/edge2e/ReferenceMoreActivity.java':['more-copy','more-paste','more-paste-confirm','GranArchive.exportCharacter'],
        'app/src/main/java/ru/gran/edge2e/ReferenceItemActivity.java':['item-equip-armor','item-toggle-shield'],
        'app/src/main/java/ru/gran/edge2e/CharacterProfiles.java':['itemMods'],
    }
    for rel,marks in checks.items():
        s=(ROOT/rel).read_text(encoding='utf-8')
        for mark in marks:
            if mark not in s: raise SystemExit(f'missing final product marker {mark} in {rel}')
    print('Gran 2e 8.0 final product sources installed')

if __name__=='__main__': main()
