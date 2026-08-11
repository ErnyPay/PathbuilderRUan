#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
FRONT = ROOT / 'app/src/main/java/ru/gran/edge2e/FrontPageActivity.java'
GRADLE = ROOT / 'app/build.gradle'
MARKER = 'GRAN_REFERENCE_FRONT_7_1'


def method_span(src: str, signature: str):
    start = src.find(signature)
    if start < 0: raise SystemExit(f'7.1 front missing: {signature}')
    brace = src.find('{', start)
    depth = 0; i = brace; state = 'code'
    while i < len(src):
        ch = src[i]; nxt = src[i+1] if i+1 < len(src) else ''
        if state == 'code':
            if ch == '"': state = 'string'
            elif ch == "'": state = 'char'
            elif ch == '/' and nxt == '/': state = 'line'; i += 1
            elif ch == '/' and nxt == '*': state = 'block'; i += 1
            elif ch == '{': depth += 1
            elif ch == '}':
                depth -= 1
                if depth == 0: return start, i + 1
        elif state == 'string':
            if ch == '\\': i += 1
            elif ch == '"': state = 'code'
        elif state == 'char':
            if ch == '\\': i += 1
            elif ch == "'": state = 'code'
        elif state == 'line':
            if ch == '\n': state = 'code'
        elif state == 'block':
            if ch == '*' and nxt == '/': state = 'code'; i += 1
        i += 1
    raise SystemExit(f'7.1 front unterminated: {signature}')


def replace_method(src, signature, replacement):
    a,b = method_span(src, signature)
    return src[:a] + replacement.strip('\n') + src[b:]


SHELL = r'''
    private View shell() {
        LinearLayout root = column(); root.setBackgroundColor(Color.rgb(31, 32, 34));
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        body = column(); body.setPadding(dp(12), dp(18), dp(12), dp(34));
        scroll.addView(body); root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        return root;
    }'''

RENDER = r'''
    private void render() {
        if (body == null) return; body.removeAllViews();
        LinearLayout brand = column(); brand.setGravity(Gravity.CENTER); brand.setPadding(dp(8), dp(24), dp(8), dp(18));
        TextView emblem = text("Г", 36, true); emblem.setTextColor(Color.rgb(236,205,169)); emblem.setGravity(Gravity.CENTER); emblem.setMinWidth(dp(64)); emblem.setMinHeight(dp(64)); emblem.setBackground(round(Color.rgb(55,57,59), 32, Color.rgb(121,31,44))); brand.addView(emblem);
        TextView title = text("ГРАНЬ 2E", 30, true); title.setTextColor(Color.WHITE); title.setGravity(Gravity.CENTER); title.setLetterSpacing(0.08f); title.setPadding(0,dp(10),0,0); brand.addView(title);
        TextView subtitle = text("ОДНОГО ПЕРСОНАЖА ВСЕГДА МАЛО", 10, true); subtitle.setTextColor(Color.rgb(185,186,188)); subtitle.setGravity(Gravity.CENTER); subtitle.setLetterSpacing(0.06f); brand.addView(subtitle); body.addView(brand, matchWrap(dp(2)));

        TextView create = frontBigAction("НОВЫЙ ПЕРСОНАЖ", "создать сборку с 1 уровня", "＋"); create.setOnClickListener(v -> { CharacterProfiles.createNew(this); startActivity(new Intent(this, ReferenceBuildActivity.class)); }); body.addView(create, matchWrap(dp(5)));
        TextView load = frontBigAction("ЗАГРУЗИТЬ ПЕРСОНАЖА", "локальные сохранения", "↥"); load.setOnClickListener(v -> showLoadDialog()); body.addView(load, matchWrap(dp(5)));

        String active = CharacterProfiles.activeId(this); List<CharacterProfiles.Profile> profiles = CharacterProfiles.list(this);
        if (!profiles.isEmpty()) {
            body.addView(frontSection("ПЕРСОНАЖИ"));
            for (CharacterProfiles.Profile p : profiles) body.addView(profileCard(p, p.id.equals(active)), matchWrap(dp(3)));
        }

        body.addView(frontSection("НАСТРОЙКИ ПРИЛОЖЕНИЯ")); LinearLayout options = frontPanel();
        TextView tools = frontOption("ИНСТРУМЕНТЫ И БИБЛИОТЕКА", "правила, языки, Lore, импорт / экспорт"); tools.setOnClickListener(v -> startActivity(new Intent(this, ReferenceMoreActivity.class))); options.addView(tools);
        TextView source = frontOption("РУССКИЙ СПРАВОЧНИК", "локальные переводы + переходы к PF2.RU"); source.setOnClickListener(v -> { Intent i=new Intent(this,ReferenceCatalogActivity.class); i.putExtra("mode","reference"); startActivity(i); }); options.addView(source);
        body.addView(options, matchWrap(dp(4)));
    }'''

OPEN_BUILD = r'''
    private void openBuild(String id) {
        if (!CharacterProfiles.load(this, id)) return;
        startActivity(new Intent(this, ReferenceBuildActivity.class));
    }'''

HELPERS = r'''
    // GRAN_REFERENCE_FRONT_7_1
    private TextView frontBigAction(String title, String subtitle, String symbol) {
        TextView v = text(symbol + "     " + title + "\n      " + subtitle, 17, true); v.setTextColor(Color.WHITE); v.setGravity(Gravity.CENTER_VERTICAL); v.setPadding(dp(18), dp(18), dp(18), dp(18)); v.setMinHeight(dp(88)); v.setBackground(round(Color.rgb(55,57,59), 3, Color.rgb(91,93,95))); return v;
    }
    private TextView frontSection(String value){TextView v=text(value,11,true);v.setTextColor(Color.rgb(236,205,169));v.setPadding(dp(4),dp(16),dp(4),dp(5));return v;}
    private LinearLayout frontPanel(){LinearLayout l=column();l.setPadding(dp(6),dp(5),dp(6),dp(5));l.setBackground(round(Color.rgb(48,49,51),3,Color.rgb(91,93,95)));return l;}
    private TextView frontOption(String title,String subtitle){TextView v=text(title+"\n"+subtitle,13,true);v.setTextColor(Color.WHITE);v.setPadding(dp(10),dp(9),dp(10),dp(9));v.setBackground(round(Color.rgb(55,57,59),2,Color.rgb(78,80,82)));LinearLayout.LayoutParams p=matchWrap(dp(2));v.setLayoutParams(p);return v;}
    private void showLoadDialog(){List<CharacterProfiles.Profile> profiles=CharacterProfiles.list(this);if(profiles.isEmpty()){new AlertDialog.Builder(this).setTitle("Персонажи").setMessage("Сохранённых персонажей пока нет.").setPositiveButton("Закрыть",null).show();return;}String[] labels=new String[profiles.size()];for(int i=0;i<labels.length;i++){CharacterProfiles.Profile p=profiles.get(i);labels[i]=(p.name==null||p.name.trim().isEmpty()?"Без имени":p.name)+"  •  "+p.summary;}new AlertDialog.Builder(this).setTitle("ЗАГРУЗИТЬ ПЕРСОНАЖА").setItems(labels,(d,which)->openBuild(profiles.get(which).id)).setNegativeButton("Отмена",null).show();}
'''


def main():
    s=FRONT.read_text(encoding='utf-8')
    s=replace_method(s,'    private View shell()',SHELL)
    s=replace_method(s,'    private void render()',RENDER)
    s=replace_method(s,'    private void openBuild(String id)',OPEN_BUILD)
    if MARKER not in s:
        pos=s.rfind('\n}')
        s=s[:pos]+'\n\n'+HELPERS.strip('\n')+'\n'+s[pos:]
    FRONT.write_text(s,encoding='utf-8')
    g=GRADLE.read_text(encoding='utf-8');g=re.sub(r'versionCode\s+\d+','versionCode 710',g);g=re.sub(r"versionName\s+'[^']+'","versionName '7.1.0'",g);GRADLE.write_text(g,encoding='utf-8')
    print('Applied Gran 7.1 reference front page parity')


if __name__=='__main__': main()
