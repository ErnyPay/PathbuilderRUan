#!/usr/bin/env python3
from pathlib import Path
import re

ROOT=Path(__file__).resolve().parents[1]
V=ROOT/'app/src/main/java/ru/gran/edge2e/MainActivityV2.java'
s=V.read_text(encoding='utf-8')

pattern=re.compile(r'''    // EQUIPMENT\n    private void showEquipment\(\) \{.*?\n    private void equipmentDetail''',re.S)
replacement=r'''    // EQUIPMENT
    private void showEquipment() {
        syncDerived(true);
        LinearLayout outer = page(); outer.addView(sectionTitle("СНАРЯЖЕНИЕ"));

        BulkRules.Summary bulk = BulkRules.calculate(store,state,stats,inventory);
        LinearLayout bulkCard=card();
        bulkCard.addView(metricRow("Bulk", BulkRules.label(bulk.totalLight) + " • " + bulk.status()));
        bulkCard.addView(metricRow("Перегруз после", BulkRules.label(bulk.encumberedAfterLight)));
        bulkCard.addView(metricRow("Максимум", BulkRules.label(bulk.maxLight)));
        bulkCard.addView(note("Light Bulk = L. Порог перегруза: 5 + модификатор Силы; максимум: 10 + модификатор Силы. Контейнеры применяют свою вместимость и игнорируемый Bulk автоматически."));
        outer.addView(bulkCard,matchWrap(dp(6)));

        LinearLayout money = card(); money.addView(metricRow("Монеты", inventory.pp + " пл • " + inventory.gp + " зм • " + inventory.sp + " см • " + inventory.cp + " мм"));
        LinearLayout mr = row(); Button mg = miniButton("−1 зм"), pg = miniButton("+1 зм"); mg.setOnClickListener(v -> { inventory.gp = Math.max(0, inventory.gp - 1); inventory.save(this); render(); }); pg.setOnClickListener(v -> { inventory.gp++; inventory.save(this); render(); }); mr.addView(mg, weighted(dp(2))); mr.addView(pg, weighted(dp(2))); money.addView(mr); outer.addView(money, matchWrap(dp(6)));

        EditText search = input("", "Поиск предмета, оружия, брони, рюкзака или руны"); outer.addView(search, matchWrap(dp(5))); LinearLayout list = column(); outer.addView(list);
        Runnable refresh = () -> {
            list.removeAllViews();
            List<RuleItem> owned=new ArrayList<>(); for(int i=0;i<state.inventory.length();i++){RuleItem x=store.findById(storedId(state.inventory.optString(i,"")));if(x!=null)owned.add(x);}

            list.addView(sectionTitle("КОНТЕЙНЕРЫ")); int containerCount=0;
            BulkRules.Summary now=BulkRules.calculate(store,state,stats,inventory);
            for(BulkRules.ContainerLoad load:now.containers){
                containerCount++; RuleItem item=load.item;
                String detail="содержимое "+BulkRules.label(load.rawContentsLight)+(load.capacityLight>0?" / "+BulkRules.label(load.capacityLight):"")+" • считается "+BulkRules.label(load.totalLight)+(load.overCapacity?" • ПЕРЕПОЛНЕН":"")+(load.worn?" • надет":" • несётся");
                TextView r=actionRow(RuNames.shortName(item.name),detail); r.setOnClickListener(v->equipmentDetail(item)); list.addView(r,matchWrap(dp(2)));
            }
            if(containerCount==0)list.addView(note("Добавь рюкзак, сумку или другой контейнер из каталога."));

            list.addView(sectionTitle("ИНВЕНТАРЬ"));
            if(owned.isEmpty())list.addView(note("Инвентарь пуст."));
            for(RuleItem item:owned){
                if(BulkRules.isContainer(item))continue;
                int q=inventory.quantity(item.id);String location=inventory.containerFor(item.id);RuleItem c=location.isEmpty()?null:store.findById(location);
                String where=c==null?"при себе":"в: "+RuNames.shortName(c.name);
                String extra=q>1?" ×"+q:"";
                TextView r=actionRow("✓ "+RuNames.shortName(item.name)+extra,"Bulk "+BulkRules.itemBulkLabel(item,q)+" • "+where+(item.id.equals(stats.equippedArmorId)?" • ЭКИПИРОВАНО":""));
                r.setOnClickListener(v->equipmentDetail(item)); list.addView(r,matchWrap(dp(2)));
            }

            list.addView(sectionTitle("КАТАЛОГ"));
            for(RuleItem item:localizedQuery("equipment",30,search.getText().toString(),180)){
                boolean has=state.hasArrayItem(state.inventory,item.id);
                String kind=BulkRules.isContainer(item)?"контейнер • вместимость "+BulkRules.label((int)Math.round(item.meta.optDouble("bulkCapacity",0)*10)):equipmentMeta(item);
                TextView r=actionRow((has?"✓ ":"+ ")+RuNames.shortName(item.name),kind+" • Bulk "+BulkRules.itemBulkLabel(item,Math.max(1,item.meta.optInt("quantity",1))));
                r.setOnClickListener(v->equipmentDetail(item));list.addView(r,matchWrap(dp(2)));
            }
        };
        search.addTextChangedListener(watcher(refresh)); refresh.run(); setContent(scroll(outer));
    }

    private void equipmentDetail'''
s,n=pattern.subn(replacement,s,count=1)
if n!=1:raise SystemExit('inventory 3.4: equipment page not found')

anchor='''            body.addView(intStepper("Количество", () -> inventory.quantity(item.id), v -> inventory.setQuantity(item.id, Math.max(1, v)), 1, 999));
            if ("weapon".equalsIgnoreCase(item.subtype) || "armor".equalsIgnoreCase(item.subtype)) {'''
insert='''            body.addView(intStepper("Количество", () -> inventory.quantity(item.id), v -> inventory.setQuantity(item.id, Math.max(1, v)), 1, 999));
            if (BulkRules.isContainer(item)) {
                BulkRules.Summary bs=BulkRules.calculate(store,state,stats,inventory); BulkRules.ContainerLoad load=null; for(BulkRules.ContainerLoad x:bs.containers)if(item.id.equals(x.item.id)){load=x;break;}
                String loadText=load==null?"—":("содержимое "+BulkRules.label(load.rawContentsLight)+(load.capacityLight>0?" / "+BulkRules.label(load.capacityLight):"")+" • учитывается "+BulkRules.label(load.totalLight)+(load.overCapacity?" • ПЕРЕПОЛНЕН":""));
                TextView loadRow=actionRow("Загрузка контейнера",loadText); body.addView(loadRow);
                TextView worn=actionRow("Ношение",inventory.isContainerWorn(item.id)?"Надет / используется штатно":"Несётся в руках или уложен");
                worn.setOnClickListener(v->{inventory.setContainerWorn(item.id,!inventory.isContainerWorn(item.id));inventory.save(this);render();}); body.addView(worn);
            } else {
                String cid=inventory.containerFor(item.id); RuleItem box=cid.isEmpty()?null:store.findById(cid);
                TextView location=actionRow("Место хранения",box==null?"При себе":"В: "+RuNames.shortName(box.name));
                location.setOnClickListener(v->showContainerPicker(item)); body.addView(location);
            }
            if ("weapon".equalsIgnoreCase(item.subtype) || "armor".equalsIgnoreCase(item.subtype)) {'''
if anchor not in s:raise SystemExit('inventory 3.4: equipment detail anchor not found after rune patch')
s=s.replace(anchor,insert,1)

helper_anchor='''    private void showRunes(RuleItem item) {'''
helpers='''    private void showContainerPicker(RuleItem item) {
        List<RuleItem> boxes=new ArrayList<>(); for(int i=0;i<state.inventory.length();i++){RuleItem x=store.findById(storedId(state.inventory.optString(i,"")));if(x!=null&&BulkRules.isContainer(x)&&!x.id.equals(item.id))boxes.add(x);}
        String[] labels=new String[boxes.size()+1]; labels[0]="При себе"; for(int i=0;i<boxes.size();i++){RuleItem b=boxes.get(i);BulkRules.Summary bs=BulkRules.calculate(store,state,stats,inventory);BulkRules.ContainerLoad load=null;for(BulkRules.ContainerLoad x:bs.containers)if(b.id.equals(x.item.id)){load=x;break;}labels[i+1]=RuNames.shortName(b.name)+(load==null?"":" • "+BulkRules.label(load.rawContentsLight)+" / "+BulkRules.label(load.capacityLight));}
        new AlertDialog.Builder(this).setTitle("Куда положить • "+RuNames.shortName(item.name)).setItems(labels,(d,w)->{inventory.assignContainer(item.id,w==0?"":boxes.get(w-1).id);inventory.save(this);render();}).setNegativeButton("Отмена",null).show();
    }

'''
if helper_anchor not in s:raise SystemExit('inventory 3.4: rune helper anchor missing')
s=s.replace(helper_anchor,helpers+helper_anchor,1)

V.write_text(s,encoding='utf-8')
print('Applied Gran 3.4 Bulk + containers UI')
