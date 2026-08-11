#!/usr/bin/env python3
from pathlib import Path
import re

ROOT=Path(__file__).resolve().parents[1]
V2=ROOT/'app/src/main/java/ru/gran/edge2e/MainActivityV2.java'
s=V2.read_text(encoding='utf-8')
pattern=re.compile(r'''    // SKILLS\n    private void showSkills\(\) \{.*?\n    // SPELLS''',re.S)
replacement=r'''    // SKILLS / LANGUAGES / LORE
    private void showSkills() {
        LinearLayout col = page();
        KnowledgeState knowledge = KnowledgeState.load(this);
        KnowledgeRules.sanitize(store, state, stats, knowledge);
        knowledge.save(this);

        col.addView(sectionTitle("ЯЗЫКИ"));
        int languageMax = KnowledgeRules.languageSlots(store, state, stats);
        List<String> grantedLanguages = KnowledgeRules.grantedLanguages(store, state, stats);
        LinearLayout languages = card();
        if (grantedLanguages.isEmpty()) languages.addView(note("Выбери род — здесь появятся автоматически выданные языки."));
        for (String slug : grantedLanguages) languages.addView(staticRow(KnowledgeRules.label(slug), "выдан автоматически"));
        List<String> selectedLanguages = knowledge.languages();
        for (String slug : selectedLanguages) {
            TextView row = actionRow("✓ " + KnowledgeRules.label(slug), "дополнительный язык • долгое нажатие — убрать");
            row.setOnLongClickListener(v -> { KnowledgeState k=KnowledgeState.load(this); k.removeLanguage(slug); k.save(this); render(); return true; });
            languages.addView(row, matchWrap(dp(2)));
        }
        int remaining = Math.max(0, languageMax - selectedLanguages.size());
        Button addLanguage = button("+ Выбрать язык (" + selectedLanguages.size() + " / " + languageMax + ")");
        addLanguage.setEnabled(remaining > 0);
        addLanguage.setOnClickListener(v -> showLanguagePicker());
        languages.addView(addLanguage);
        languages.addView(note("Лимит: языки рода + положительный модификатор Интеллекта + эффекты фитов. Недоступные uncommon/rare языки не предлагаются без доступа."));
        col.addView(languages, matchWrap(dp(6)));

        col.addView(sectionTitle("LORE"));
        LinearLayout loreCard = card();
        List<KnowledgeRules.Lore> lores = KnowledgeRules.lores(store, state, stats, knowledge);
        if (lores.isEmpty()) loreCard.addView(note("Предыстория и некоторые фиты автоматически добавляют Lore."));
        for (KnowledgeRules.Lore lore : lores) {
            String rank = KnowledgeRules.rankLabel(lore.rank);
            TextView row = actionRow(RuNames.shortName(lore.name) + "  " + signed(lore.bonus), rank + " • источник: " + RuNames.shortName(lore.source));
            if ("Additional Lore".equalsIgnoreCase(lore.source)) row.setOnLongClickListener(v -> { KnowledgeState k=KnowledgeState.load(this); k.removeLore(lore.name); k.save(this); render(); return true; });
            loreCard.addView(row, matchWrap(dp(2)));
        }
        int loreSlots = KnowledgeRules.additionalLoreCount(store, state);
        int customLore = knowledge.lores().size();
        if (customLore < loreSlots) {
            Button addLore = button("+ Выбрать Lore для Additional Lore (" + customLore + " / " + loreSlots + ")");
            addLore.setOnClickListener(v -> showAdditionalLoreDialog()); loreCard.addView(addLore);
        }
        if (loreSlots > 0) loreCard.addView(note("Additional Lore повышается автоматически: Эксперт 3, Мастер 7, Легенда 15."));
        col.addView(loreCard, matchWrap(dp(7)));

        col.addView(sectionTitle("НАВЫКИ"));
        col.addView(note("Ранги, выданные классом/предысторией/правилами, учитываются автоматически. Ручной ранг ограничен уровнем персонажа."));
        for (String[] skill : SKILLS) {
            RuleRuntime.Snapshot runtime = RuntimeBridge.snapshot(state, stats);
            int rank = runtime == null ? state.rank(skill[0]) : runtime.rank(state, skill[0]);
            int bonus = DerivedStats.skill(state, stats, skill[0]);
            LinearLayout c = card(); LinearLayout r = row(); r.setGravity(Gravity.CENTER_VERTICAL);
            TextView name = text(skill[1], 16, true), b = text(signed(bonus), 23, true); b.setTextColor(ACCENT); b.setGravity(Gravity.END); r.addView(name, weighted()); r.addView(b, fixed(dp(72))); c.addView(r);
            TextView rankView = chip(RANKS[Math.max(0,Math.min(4,rank))], rank > 0);
            rankView.setOnClickListener(v -> { state.setRank(skill[0], (state.rank(skill[0]) + 1) % (state.maxSkillRankForLevel()+1)); revalidate(); saveAll(); render(); });
            c.addView(rankView, matchWrap(dp(2)));
            Button roll = button("Проверка d20"); roll.setOnClickListener(v -> { int die = random.nextInt(20) + 1; toast(skill[1] + ": " + die + signedInline(bonus) + " = " + (die + bonus)); }); c.addView(roll);
            col.addView(c, matchWrap(dp(4)));
        }
        setContent(scroll(col));
    }

    private void showLanguagePicker() {
        KnowledgeState knowledge=KnowledgeState.load(this); KnowledgeRules.sanitize(store,state,stats,knowledge);
        int max=KnowledgeRules.languageSlots(store,state,stats); if(knowledge.languages().size()>=max){toast("Все доступные языковые слоты заполнены");return;}
        List<KnowledgeRules.Language> candidates=new ArrayList<>();
        for(KnowledgeRules.Language l:KnowledgeRules.allowedLanguages(store,state,stats))if(!knowledge.hasLanguage(l.slug))candidates.add(l);
        if(candidates.isEmpty()){toast("Нет доступных языков для текущего доступа");return;}
        String[] labels=new String[candidates.size()];for(int i=0;i<candidates.size();i++){KnowledgeRules.Language l=candidates.get(i);labels[i]=l.label+("uncommon".equals(l.rarity)?" • необычный":"");}
        new AlertDialog.Builder(this).setTitle("Выбрать дополнительный язык").setItems(labels,(d,w)->{KnowledgeState k=KnowledgeState.load(this);k.addLanguage(candidates.get(w).slug);KnowledgeRules.sanitize(store,state,stats,k);k.save(this);render();}).setNegativeButton("Отмена",null).show();
    }

    private void showAdditionalLoreDialog() {
        KnowledgeState knowledge=KnowledgeState.load(this);int max=KnowledgeRules.additionalLoreCount(store,state);if(knowledge.lores().size()>=max){toast("Все Additional Lore уже выбраны");return;}
        EditText input=input("","Например: Военная история");
        new AlertDialog.Builder(this).setTitle("Новая область Lore").setMessage("Additional Lore создаёт отдельную область знаний. Введи её название.").setView(input).setPositiveButton("Добавить",(d,w)->{String name=input.getText().toString().trim();if(name.isEmpty()){toast("Название Lore не может быть пустым");return;}KnowledgeState k=KnowledgeState.load(this);k.addLore(name);k.trimLores(max);k.save(this);render();}).setNegativeButton("Отмена",null).show();
    }

    // SPELLS'''
s,n=pattern.subn(replacement,s,count=1)
if n!=1:raise SystemExit('knowledge 3.3: SKILLS block not found')
V2.write_text(s,encoding='utf-8')
print('Applied Gran 3.3 Languages + Lore UI')
