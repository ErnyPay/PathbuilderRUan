#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
V2 = ROOT / 'app/src/main/java/ru/gran/edge2e/MainActivityV2.java'

s = V2.read_text(encoding='utf-8')

# Replace the old catalog-only spell page after all 2.0/3.0/3.1 patches have run.
pattern = re.compile(r'''    // SPELLS\n    private void showSpells\(\) \{.*?\n    // EQUIPMENT''', re.S)
replacement = r'''    // SPELLS
    private void showSpells() {
        syncDerived(true);
        LinearLayout outer = page();
        outer.addView(sectionTitle("ЗАКЛИНАНИЯ"));

        RuleItem cls = classItem();
        RuleRuntime.Snapshot spellRuntime = RuntimeBridge.snapshot(state, stats);
        SpellcastingRules.Profile profile = SpellcastingRules.resolve(state, spellRuntime);
        SpellcastingState casting = SpellcastingState.load(this);

        if (profile == null) {
            outer.addView(note("У персонажа пока нет источника заклинаний. Выбери заклинательский класс или архетип."));
            setContent(scroll(outer));
            return;
        }

        casting.sanitize(profile, state.level);
        casting.save(this);

        LinearLayout summaryCard = card();
        summaryCard.addView(text(RuNames.shortName(profile.source), 19, true));
        summaryCard.addView(note("Магия: " + SpellcastingRules.modeLabel(profile) + " • традиция: " + SpellcastingRules.traditionLabel(profile.tradition)));
        if (cls != null && profile.supported) {
            summaryCard.addView(metricRow("Атака заклинанием", signed(DerivedStats.spellAttack(state, stats, cls))));
            summaryCard.addView(metricRow("КС заклинания", String.valueOf(DerivedStats.spellDc(state, stats, cls))));
        }
        if (!profile.note.isEmpty()) summaryCard.addView(note(profile.note));
        Button resetDay = button("Новая подготовка / восстановить слоты");
        resetDay.setOnClickListener(v -> { SpellcastingState c=SpellcastingState.load(this); c.dailyReset(); c.save(this); toast("Слоты восстановлены"); render(); });
        summaryCard.addView(resetDay);
        outer.addView(summaryCard, matchWrap(dp(7)));

        if (!profile.supported) {
            outer.addView(sectionTitle("ОСОБАЯ ПРОГРЕССИЯ"));
            outer.addView(note("Gran не подменяет особую механику этого класса обычной таблицей слотов. Каталог ниже уже ограничен активной традицией; специализированный caster-модуль будет добавлен отдельно."));
            addSpellCatalog(outer, profile, casting, -1, false);
            setContent(scroll(outer));
            return;
        }

        addCantripSection(outer, profile, casting);
        if (SpellcastingRules.PREPARED.equals(profile.mode)) addPreparedSpellcasting(outer, profile, casting);
        else if (SpellcastingRules.SPONTANEOUS.equals(profile.mode)) addSpontaneousSpellcasting(outer, profile, casting);

        if (preparedUsesSpellbook(profile)) addSpellbookSection(outer, profile);
        else if (!SpellcastingRules.SPONTANEOUS.equals(profile.mode)) addSpellCatalog(outer, profile, casting, -1, false);
        setContent(scroll(outer));
    }

    private void addCantripSection(LinearLayout outer, SpellcastingRules.Profile profile, SpellcastingState casting) {
        outer.addView(sectionTitle(SpellcastingRules.PREPARED.equals(profile.mode) ? "ПОДГОТОВЛЕННЫЕ КАНТРИПЫ" : "КАНТРИПЫ РЕПЕРТУАРА"));
        LinearLayout c = card();
        List<SpellcastingState.RepertoireSpell> known = casting.repertoire(0);
        for (SpellcastingState.RepertoireSpell entry : known) {
            RuleItem item=store.findById(entry.id);
            TextView row=actionRow("✓ "+RuNames.shortName(entry.name), "кантрип • автоматически повышается");
            row.setOnClickListener(v -> { if(item!=null) showRuleDetail(item, null, "Закрыть"); });
            row.setOnLongClickListener(v -> { SpellcastingState st=SpellcastingState.load(this); st.removeRepertoire(0,entry.id); st.save(this); render(); return true; });
            c.addView(row, matchWrap(dp(2)));
        }
        if (known.size() < profile.cantrips) {
            Button add=button("+ Кантрип ("+known.size()+" / "+profile.cantrips+")");
            add.setOnClickListener(v -> showSpellChoice(profile, 0, true, item -> { SpellcastingState st=SpellcastingState.load(this); st.addRepertoire(0,item); st.save(this); render(); }));
            c.addView(add);
        } else c.addView(note("Лимит кантрипов заполнен: "+known.size()+" / "+profile.cantrips+"."));
        outer.addView(c, matchWrap(dp(6)));
    }

    private void addPreparedSpellcasting(LinearLayout outer, SpellcastingRules.Profile profile, SpellcastingState casting) {
        outer.addView(sectionTitle("ПОДГОТОВЛЕННЫЕ СЛОТЫ"));
        for (int rank=1; rank<=profile.maxRank(state.level); rank++) {
            final int spellRank=rank;
            int slots=profile.slots(state.level,rank);
            if(slots<=0) continue;
            LinearLayout rankCard=card();
            rankCard.addView(text("Ранг "+rank+" • "+slots+" слотов",17,true));
            for(int slot=0;slot<slots;slot++) {
                final int spellSlot=slot;
                String id=casting.preparedId(rank,slot), name=casting.preparedName(rank,slot);
                boolean spent=casting.preparedSpent(rank,slot);
                LinearLayout row=column(); row.setPadding(dp(4),dp(5),dp(4),dp(5));
                TextView spell=actionRow("Слот "+(slot+1)+(spent?" • ПОТРАЧЕН":" • ГОТОВ"), id.isEmpty()?"Выбрать заклинание":RuNames.shortName(name));
                spell.setOnClickListener(v -> {
                    if(id.isEmpty()) showPreparedSpellChoice(profile,spellRank,item->{ SpellcastingState st=SpellcastingState.load(this); st.prepare(spellRank,spellSlot,item); st.save(this); render(); });
                    else { SpellcastingState st=SpellcastingState.load(this); st.setPreparedSpent(spellRank,spellSlot,!st.preparedSpent(spellRank,spellSlot)); st.save(this); render(); }
                });
                spell.setOnLongClickListener(v -> { showPreparedSpellChoice(profile,spellRank,item->{ SpellcastingState st=SpellcastingState.load(this); st.prepare(spellRank,spellSlot,item); st.save(this); render(); }); return true; });
                row.addView(spell); rankCard.addView(row);
            }
            if(profile.wizardCurriculum && profile.bonusPreparedSlots(state.level,rank)>0) {
                rankCard.addView(note("Школьный слот ранга "+rank+" зарезервирован под curriculum spell. Пока школа не разрешена полностью, Gran не позволит заполнить его случайным арканным заклинанием."));
            }
            outer.addView(rankCard,matchWrap(dp(5)));
        }
    }

    private void addSpontaneousSpellcasting(LinearLayout outer, SpellcastingRules.Profile profile, SpellcastingState casting) {
        outer.addView(sectionTitle("РЕПЕРТУАР И СЛОТЫ"));
        for(int rank=1;rank<=profile.maxRank(state.level);rank++) {
            final int spellRank=rank;
            int slots=profile.slots(state.level,rank); if(slots<=0) continue;
            int spent=casting.spent(rank), remaining=Math.max(0,slots-spent);
            LinearLayout c=card();
            c.addView(text("Ранг "+rank+" • слоты "+remaining+" / "+slots,17,true));
            LinearLayout controls=row(); Button use=button("Потратить слот"), restore=button("Вернуть слот");
            use.setEnabled(remaining>0); restore.setEnabled(spent>0);
            use.setOnClickListener(v->{SpellcastingState st=SpellcastingState.load(this); if(st.spend(spellRank,slots)){st.save(this);render();}});
            restore.setOnClickListener(v->{SpellcastingState st=SpellcastingState.load(this);st.restoreOne(spellRank,slots);st.save(this);render();});
            controls.addView(use,weighted(dp(2))); controls.addView(restore,weighted(dp(2))); c.addView(controls);

            List<SpellcastingState.RepertoireSpell> repertoire=casting.repertoire(rank);
            for(SpellcastingState.RepertoireSpell entry:repertoire) {
                RuleItem item=store.findById(entry.id); boolean sig=casting.isSignature(rank,entry.id);
                TextView row=actionRow((sig?"★ ":"◆ ")+RuNames.shortName(entry.name), "изучено как ранг "+rank+(sig?" • SIGNATURE":""));
                row.setOnClickListener(v->{if(item!=null)showRuleDetail(item,null,"Закрыть");});
                row.setOnLongClickListener(v->{showRepertoireActions(profile,spellRank,entry);return true;}); c.addView(row,matchWrap(dp(2)));
            }
            int limit=slots;
            if(repertoire.size()<limit) {
                Button add=button("+ Заклинание репертуара ("+repertoire.size()+" / "+limit+")");
                add.setOnClickListener(v->showSpellChoice(profile,spellRank,false,item->{SpellcastingState st=SpellcastingState.load(this);st.addRepertoire(spellRank,item);st.save(this);render();})); c.addView(add);
            } else c.addView(note("Репертуар этого ранга заполнен: "+repertoire.size()+" / "+limit+"."));
            if(profile.signatures && state.level>=3) {
                String signature=casting.signatureId(rank);
                c.addView(note(signature.isEmpty()?"Signature spell не выбран. Долгое нажатие на заклинание позволяет назначить его.":"★ Signature spell позволяет свободно heighten это заклинание подходящим слотом."));
            }
            outer.addView(c,matchWrap(dp(5)));
        }
    }

    private void showRepertoireActions(SpellcastingRules.Profile profile,int rank,SpellcastingState.RepertoireSpell entry) {
        List<String> actions=new ArrayList<>();
        if(profile.signatures && state.level>=3) actions.add("Сделать signature spell");
        actions.add("Убрать из репертуара");
        new AlertDialog.Builder(this).setTitle(RuNames.shortName(entry.name)).setItems(actions.toArray(new String[0]),(d,which)->{
            SpellcastingState st=SpellcastingState.load(this); String action=actions.get(which);
            if(action.startsWith("Сделать")) st.setSignature(rank,entry.id); else st.removeRepertoire(rank,entry.id);
            st.save(this); render();
        }).setNegativeButton("Отмена",null).show();
    }

    private void showPreparedSpellChoice(SpellcastingRules.Profile profile,int rank,Selection selection) {
        List<RuleItem> candidates=new ArrayList<>();
        if(preparedUsesSpellbook(profile)) {
            for(int i=0;i<state.spells.length();i++) {
                RuleItem item=store.findById(storedId(state.spells.optString(i,"")));
                if(item!=null && !spellHasTrait(item,"cantrip") && SpellcastingRules.spellAllowed(profile,item,rank)) candidates.add(item);
            }
        } else {
            for(RuleItem item:localizedQuery("spell",rank,"",700)) if(!spellHasTrait(item,"cantrip") && SpellcastingRules.spellAllowed(profile,item,rank)) candidates.add(item);
        }
        showSpellListDialog("Подготовить в слот ранга "+rank,candidates,selection);
    }

    private void showSpellChoice(SpellcastingRules.Profile profile,int learnedRank,boolean cantrip,Selection selection) {
        int max=Math.max(1,learnedRank); List<RuleItem> candidates=new ArrayList<>();
        for(RuleItem item:localizedQuery("spell",max,"",800)) {
            if(spellHasTrait(item,"cantrip")!=cantrip) continue;
            if(cantrip) { if(spellTraditionMatch(item,profile.tradition)) candidates.add(item); }
            else if(SpellcastingRules.spellAllowed(profile,item,learnedRank)) candidates.add(item);
        }
        showSpellListDialog(cantrip?"Выбрать кантрип":"Изучить как ранг "+learnedRank,candidates,selection);
    }

    private void showSpellListDialog(String title,List<RuleItem> candidates,Selection selection) {
        if(candidates.isEmpty()){toast("Нет доступных заклинаний для этого выбора");return;}
        candidates.sort((a,b)->RuNames.shortName(a.name).compareToIgnoreCase(RuNames.shortName(b.name)));
        String[] labels=new String[candidates.size()]; for(int i=0;i<candidates.size();i++)labels[i]=RuNames.shortName(candidates.get(i).name)+"  •  ранг "+candidates.get(i).level;
        new AlertDialog.Builder(this).setTitle(title).setItems(labels,(d,w)->selection.onSelect(candidates.get(w))).setNegativeButton("Отмена",null).show();
    }

    private boolean preparedUsesSpellbook(SpellcastingRules.Profile profile) {
        if(profile==null)return false; String s=profile.source.toLowerCase(Locale.ROOT); return "wizard".equals(s)||"witch".equals(s);
    }

    private void addSpellbookSection(LinearLayout outer,SpellcastingRules.Profile profile) {
        outer.addView(sectionTitle("КНИГА / ИЗВЕСТНЫЕ ЗАКЛИНАНИЯ"));
        LinearLayout known=card(); int count=0;
        for(int i=0;i<state.spells.length();i++) {
            RuleItem item=store.findById(storedId(state.spells.optString(i,""))); if(item==null)continue; count++;
            TextView row=actionRow("✓ "+RuNames.shortName(item.name),spellMeta(item)+" • долгое нажатие — убрать");
            row.setOnClickListener(v->showRuleDetail(item,null,"Закрыть")); row.setOnLongClickListener(v->{state.toggleArrayItem(state.spells,item);saveAll();render();return true;}); known.addView(row,matchWrap(dp(2)));
        }
        if(count==0)known.addView(note("Добавь заклинания в книгу. Подготовленные слоты Wizard/Witch выбирают только отсюда.")); outer.addView(known,matchWrap(dp(5)));
        addSpellCatalog(outer,profile,SpellcastingState.load(this),-1,true);
    }

    private void addSpellCatalog(LinearLayout outer,SpellcastingRules.Profile profile,SpellcastingState casting,int learnedRank,boolean toggleSpellbook) {
        outer.addView(sectionTitle("КАТАЛОГ")); EditText search=input("","Поиск заклинания"); outer.addView(search,matchWrap(dp(4))); LinearLayout list=column(); outer.addView(list);
        Runnable refresh=()->{
            list.removeAllViews(); int maxRank=Math.max(1,Math.min(10,(state.level+1)/2)),shown=0;
            for(RuleItem item:localizedQuery("spell",maxRank,search.getText().toString(),300)) {
                if(!spellTraditionMatch(item,profile.tradition))continue;
                boolean has=state.hasArrayItem(state.spells,item.id); TextView row=actionRow((has?"✓ ":"+ ")+RuNames.shortName(item.name),spellMeta(item));
                row.setOnClickListener(v->{ if(toggleSpellbook){state.toggleArrayItem(state.spells,item);saveAll();render();} else showRuleDetail(item,null,"Закрыть"); }); list.addView(row,matchWrap(dp(2))); if(++shown>=180)break;
            }
            if(shown==0)list.addView(note("Ничего не найдено для текущей традиции."));
        };
        search.addTextChangedListener(watcher(refresh)); refresh.run();
    }

    private boolean spellTraditionMatch(RuleItem item,String tradition) {
        if(item==null||tradition==null||tradition.isEmpty())return false; JSONArray a=item.meta.optJSONArray("traditions"); if(a==null)return false;
        for(int i=0;i<a.length();i++)if(tradition.equalsIgnoreCase(a.optString(i,"")))return true; return false;
    }

    private boolean spellHasTrait(RuleItem item,String trait) {
        if(item==null)return false; for(String value:item.traits)if(trait.equalsIgnoreCase(value))return true; return false;
    }

    private String spellMeta(RuleItem item) {
        JSONArray traditions=item.meta.optJSONArray("traditions"); String tr=joinJson(traditions),time=item.meta.optString("time","");
        return (spellHasTrait(item,"cantrip")?"кантрип":"ранг "+item.level)+(tr.isEmpty()?"":" • "+tr)+(time.isEmpty()?"":" • "+time+" действия");
    }

    // EQUIPMENT'''

s, n = pattern.subn(replacement, s, count=1)
if n != 1:
    raise SystemExit('spellcasting 3.2: SPELLS block not found after 3.1')

V2.write_text(s, encoding='utf-8')
print('Applied Gran 2e 3.2 prepared/spontaneous spellcasting PLAY module')
