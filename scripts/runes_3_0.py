#!/usr/bin/env python3
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
D=ROOT/'app/src/main/java/ru/gran/edge2e/DerivedStats.java'
V=ROOT/'app/src/main/java/ru/gran/edge2e/MainActivityV2.java'


def rep(s,a,b,label):
    if a not in s: raise SystemExit('runes 3.0 missing anchor: '+label)
    return s.replace(a,b,1)

s=D.read_text(encoding='utf-8')
s=rep(s,'            potency = armor.meta.optInt("potency", 0);','            potency = ItemMods.potency(s.context(), armor);','armor potency')
s=rep(s,' + weapon.meta.optInt("potency", 0) + weapon.meta.optInt("bonus", 0) + modifier;',' + ItemMods.potency(s.context(), weapon) + weapon.meta.optInt("bonus", 0) + modifier;','weapon potency')
s=rep(s,'        int striking = Math.max(0, weapon.meta.optInt("striking", 0));','        int striking = Math.max(0, ItemMods.striking(s.context(), weapon));','weapon striking')
D.write_text(s,encoding='utf-8')

s=V.read_text(encoding='utf-8')
old='''        if (has) {
            body.addView(intStepper("Количество", () -> inventory.quantity(item.id), v -> inventory.setQuantity(item.id, Math.max(1, v)), 1, 999));
            if ("armor".equalsIgnoreCase(item.subtype)) {'''
new='''        if (has) {
            body.addView(intStepper("Количество", () -> inventory.quantity(item.id), v -> inventory.setQuantity(item.id, Math.max(1, v)), 1, 999));
            if ("weapon".equalsIgnoreCase(item.subtype) || "armor".equalsIgnoreCase(item.subtype)) {
                TextView runes = actionRow("Руны", runeSummary(item));
                runes.setOnClickListener(v -> showRunes(item)); body.addView(runes);
            }
            if ("armor".equalsIgnoreCase(item.subtype)) {'''
s=rep(s,old,new,'equipment rune row')
s=rep(s,'if (has) { state.toggleArrayItem(state.inventory, item); inventory.remove(item.id); if (item.id.equals(stats.equippedArmorId)) stats.equippedArmorId = ""; }','if (has) { state.toggleArrayItem(state.inventory, item); inventory.remove(item.id); ItemMods.clear(this, item.id); if (item.id.equals(stats.equippedArmorId)) stats.equippedArmorId = ""; }','clear mods')
anchor='''    private String equipmentMeta(RuleItem item) {'''
extra='''    private void showRunes(RuleItem item) {
        LinearLayout body=column(); body.setPadding(dp(8),dp(5),dp(8),dp(5));
        body.addView(note("Настройка рун относится к экземпляру предмета этого персонажа и сразу влияет на расчёты."));
        body.addView(intStepper("Руна мощи", () -> ItemMods.potency(this,item), v -> ItemMods.setPotency(this,item.id,v), 0, 4));
        if ("weapon".equalsIgnoreCase(item.subtype)) body.addView(intStepper("Разящая руна", () -> ItemMods.striking(this,item), v -> ItemMods.setStriking(this,item.id,v), 0, 3));
        EditText properties=input(ItemMods.propertiesText(this,item),"Руны свойств через запятую"); body.addView(properties,matchWrap(dp(4)));
        new AlertDialog.Builder(this).setTitle("Руны • "+RuNames.shortName(item.name)).setView(scroll(body)).setNegativeButton("Отмена",null).setPositiveButton("Сохранить",(d,w)->{ItemMods.setProperties(this,item.id,properties.getText().toString());syncDerived(true);render();}).show();
    }

    private String runeSummary(RuleItem item) {
        int potency=ItemMods.potency(this,item), striking=ItemMods.striking(this,item); String props=ItemMods.propertiesText(this,item);
        StringBuilder out=new StringBuilder(); if(potency>0) out.append("+").append(potency).append(" мощь"); if("weapon".equalsIgnoreCase(item.subtype)&&striking>0){if(out.length()>0)out.append(" • ");out.append("разящая ").append(striking);} if(!props.isEmpty()){if(out.length()>0)out.append(" • ");out.append(props);} return out.length()==0?"Нет настроенных рун":out.toString();
    }

'''
if anchor not in s: raise SystemExit('runes 3.0 missing anchor: equipmentMeta')
s=s.replace(anchor,extra+anchor,1)
V.write_text(s,encoding='utf-8')
print('Applied Gran 2e 3.0 rune mechanics')
