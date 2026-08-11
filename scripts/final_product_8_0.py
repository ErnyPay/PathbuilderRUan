#!/usr/bin/env python3
from pathlib import Path
import base64, hashlib, io, tarfile

ROOT=Path(__file__).resolve().parents[1]

EXPECTED_ENCODED='56da64bd92db342aad228a010070e5ff72200db3fc51e857ffce0785a3648da4'
EXPECTED_RAW='74e30f4ffe4d6c8b6d18bdedb506f7573177912923ae1299654e2503c98b830e'
EXPECTED={
    '00':'b5fae8ea22422c859d53afd73aedda5b338fb87ff83758f50f389beeb43c2265',
    '01':'05bb2baff8857b3d322ab3d448637777566463a50180a3ecf91a12ed42627f95',
    '02':'600f79b8aacead7fc85ccd8842437a8de2482e5f0404ccd80e460a9a297954db',
    '03_fix':'7b436991242dc22d729fbac38b96f8b49f0878b9c0f4feecb1972a11a45c45db',
    '04':'f08d6b61ee00703786359249c48b976f93daee768c7cde29e9f53cc7501d9aab',
    '06':'62f780bec2cad5a4eecdd3173b00a167b2eee84e67073d8788c5a6127b36aa6f',
    '05':'36b5f9873046b7dd7c90cca20a1a466f0ee2b2bb156bb3fa50aefd5411b69391',
}

def sha(data: bytes): return hashlib.sha256(data).hexdigest()

def main():
    scripts=ROOT/'scripts'
    names=['00','01','02','03_fix','04','06','05']
    chunks=[]
    for name in names:
        p=scripts/f'final_product_8_0_payload_{name}.b64'
        if not p.exists(): raise SystemExit(f'final product payload missing: {p}')
        s=p.read_text(encoding='ascii').strip()
        actual=sha(s.encode())
        if actual != EXPECTED[name]: raise SystemExit(f'payload chunk {name} hash mismatch: {actual}')
        chunks.append(s)
    encoded=''.join(chunks)
    encoded_hash=sha(encoded.encode())
    if encoded_hash != EXPECTED_ENCODED: raise SystemExit(f'encoded payload hash mismatch: {encoded_hash}')
    payload=base64.b64decode(encoded, validate=True)
    raw_hash=sha(payload)
    if raw_hash != EXPECTED_RAW: raise SystemExit(f'raw payload hash mismatch: {raw_hash}')
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
    print('Gran 2e 8.0 final product sources installed; payload integrity verified')

if __name__=='__main__': main()
