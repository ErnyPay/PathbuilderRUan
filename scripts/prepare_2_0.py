#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
V3 = ROOT / 'app/src/main/java/ru/gran/edge2e/MainActivityV3.java'
V2 = ROOT / 'app/src/main/java/ru/gran/edge2e/MainActivityV2.java'


def must_replace(text, old, new, label):
    if old not in text:
        raise SystemExit(f'2.0 patch missing anchor: {label}')
    return text.replace(old, new, 1)


def patch_v3():
    s = V3.read_text(encoding='utf-8')
    s = must_replace(s, 'super.onCreate(savedInstanceState);\n        getWindow().setStatusBarColor(HEADER_DARK);',
        'super.onCreate(savedInstanceState);\n        RuNames.init(this);\n        getWindow().setStatusBarColor(HEADER_DARK);', 'V3 RuNames.init')
    old_nav = '''        nav(nav, "BUILD", "build");
        nav(nav, "НАВЫКИ", "skills");
        nav(nav, "ЗАЩИТА", "play");
        nav(nav, "АТАКА", "play");
        nav(nav, "СНАРЯЖ.", "play");
        nav(nav, "ЗАКЛ.", "play");
        nav(nav, "ЛИСТ / БОЙ", "play");'''
    new_nav = '''        nav(nav, "BUILD", "build");
        nav(nav, "НАВЫКИ", "skills");
        nav(nav, "ЗАЩИТА", "defense");
        nav(nav, "АТАКА", "attack");
        nav(nav, "СНАРЯЖ.", "equipment");
        nav(nav, "ЗАКЛ.", "spells");
        nav(nav, "ЛИСТ", "sheet");
        nav(nav, "БОЙ", "combat");
        nav(nav, "СПРАВ.", "reference");'''
    s = must_replace(s, old_nav, new_nav, 'V3 navigation labels')
    pattern = re.compile(r'''    private void nav\(LinearLayout parent, String label, String target\) \{.*?\n    \}\n\n    private void render\(\) \{''', re.S)
    repl = '''    private void nav(LinearLayout parent, String label, String target) {
        boolean internal = "build".equals(target) || "skills".equals(target);
        boolean active = internal && screen.equals(target);
        TextView v = tab(label, active);
        v.setOnClickListener(x -> {
            if (!internal) {
                Intent intent = new Intent(this, MainActivityV2.class);
                intent.putExtra("screen", target);
                startActivity(intent);
                return;
            }
            screen = target;
            render();
        });
        parent.addView(v, wrapWrap(dp(2)));
    }

    private void render() {'''
    s, n = pattern.subn(repl, s, count=1)
    if n != 1:
        raise SystemExit('2.0 patch missing anchor: V3 nav method')
    s = s.replace('String.join("; ", item.prerequisites)', 'RuNames.prerequisites(item.id, item.prerequisites)')
    s = s.replace('item.description == null ? "" : item.description', 'RuNames.description(item.id, item.description)')

    # Eliminate the remaining platform-grey controls from the builder.
    old_mini = 'private Button mini(String s) { Button b=new Button(this); b.setText(s); b.setTextSize(17); b.setTextColor(TEXT); b.setMinWidth(dp(42)); b.setMinimumHeight(0); b.setMinHeight(dp(38)); return b; }'
    new_mini = 'private Button mini(String s) { Button b=new Button(this); b.setText(s); b.setTextSize(17); b.setTextColor(ACCENT); b.setBackground(round(Color.rgb(248,246,241),6,BORDER)); b.setMinWidth(dp(42)); b.setMinimumHeight(0); b.setMinHeight(dp(38)); return b; }'
    s = must_replace(s, old_mini, new_mini, 'V3 mini buttons')
    V3.write_text(s, encoding='utf-8')


def patch_v2():
    s = V2.read_text(encoding='utf-8')
    replacements = {
        'private static final int BG = Color.rgb(15, 22, 29);': 'private static final int BG = Color.rgb(239, 237, 232);',
        'private static final int HEADER = Color.rgb(10, 16, 22);': 'private static final int HEADER = Color.rgb(94, 26, 40);\n    private static final int HEADER_DARK = Color.rgb(71, 19, 30);',
        'private static final int SURFACE = Color.rgb(27, 38, 48);': 'private static final int SURFACE = Color.rgb(255, 255, 255);',
        'private static final int SURFACE_2 = Color.rgb(38, 51, 62);': 'private static final int SURFACE_2 = Color.rgb(248, 246, 241);',
        'private static final int BORDER = Color.rgb(64, 80, 93);': 'private static final int BORDER = Color.rgb(207, 199, 188);',
        'private static final int TEXT = Color.rgb(240, 239, 232);': 'private static final int TEXT = Color.rgb(39, 36, 34);',
        'private static final int MUTED = Color.rgb(174, 184, 191);': 'private static final int MUTED = Color.rgb(107, 101, 95);',
        'private static final int ACCENT = Color.rgb(219, 158, 69);': 'private static final int ACCENT = Color.rgb(125, 31, 48);',
        'private String screen = "build";': 'private String screen = "sheet";',
    }
    for old, new in replacements.items():
        s = must_replace(s, old, new, 'V2 ' + old[:30])
    s = must_replace(s, 'super.onCreate(savedInstanceState);\n        store = new RuleStore(this);',
        '''super.onCreate(savedInstanceState);
        RuNames.init(this);
        getWindow().setStatusBarColor(HEADER_DARK);
        String requestedScreen = getIntent().getStringExtra("screen");
        if (requestedScreen != null && !requestedScreen.isEmpty()) screen = requestedScreen;
        store = new RuleStore(this);''', 'V2 init + route')

    s = must_replace(s,
        'LinearLayout head = column(); head.setPadding(dp(16), dp(10), dp(16), dp(8)); head.setBackgroundColor(HEADER);',
        'LinearLayout head = column(); head.setPadding(dp(14), dp(9), dp(14), dp(8)); head.setBackgroundColor(HEADER);',
        'V2 header padding')
    s = must_replace(s,
        'TextView title = text("ГРАНЬ 2e", 22, true); title.setTextColor(ACCENT); head.addView(title);\n        summary = text("", 13, false); summary.setTextColor(MUTED); head.addView(summary);',
        'TextView title = text("ГРАНЬ 2e", 21, true); title.setTextColor(Color.WHITE); head.addView(title);\n        summary = text("", 12, false); summary.setTextColor(Color.rgb(235, 218, 210)); head.addView(summary);',
        'V2 header text')
    s = must_replace(s,
        'HorizontalScrollView navScroll = new HorizontalScrollView(this); navScroll.setHorizontalScrollBarEnabled(false);',
        'HorizontalScrollView navScroll = new HorizontalScrollView(this); navScroll.setHorizontalScrollBarEnabled(false); navScroll.setBackgroundColor(HEADER_DARK);',
        'V2 nav background')
    s = must_replace(s,
        'LinearLayout nav = row(); nav.setPadding(dp(6), dp(4), dp(6), dp(6));\n        addNav(nav, "BUILD", "build"); addNav(nav, "ЛИСТ", "sheet"); addNav(nav, "БОЙ", "combat");\n        addNav(nav, "АТАКА", "attack"); addNav(nav, "ЗАЩИТА", "defense"); addNav(nav, "НАВЫКИ", "skills");\n        addNav(nav, "ЗАКЛИНАНИЯ", "spells"); addNav(nav, "СНАРЯЖЕНИЕ", "equipment"); addNav(nav, "СПРАВОЧНИК", "reference");',
        'LinearLayout nav = row(); nav.setPadding(dp(5), dp(4), dp(5), dp(4));\n        addNav(nav, "BUILD", "build"); addNav(nav, "НАВЫКИ", "skills"); addNav(nav, "ЗАЩИТА", "defense");\n        addNav(nav, "АТАКА", "attack"); addNav(nav, "СНАРЯЖ.", "equipment"); addNav(nav, "ЗАКЛ.", "spells");\n        addNav(nav, "ЛИСТ", "sheet"); addNav(nav, "БОЙ", "combat"); addNav(nav, "СПРАВ.", "reference");',
        'V2 nav ordering')

    old_method = '''    private void addNav(LinearLayout nav, String label, String target) {
        TextView v = chip(label, screen.equals(target));
        v.setOnClickListener(x -> { screen = target; render(); });
        nav.addView(v, wrapWrap(dp(4)));
    }'''
    new_method = '''    private void addNav(LinearLayout nav, String label, String target) {
        TextView v = chip(label, screen.equals(target));
        v.setOnClickListener(x -> {
            if ("build".equals(target)) {
                startActivity(new android.content.Intent(this, MainActivityV3.class));
                finish();
                return;
            }
            screen = target;
            render();
        });
        nav.addView(v, wrapWrap(dp(2)));
    }'''
    s = must_replace(s, old_method, new_method, 'V2 addNav')

    old_chip = 'private TextView chip(String s, boolean active) { TextView v=text(s,13,true); v.setGravity(Gravity.CENTER); v.setPadding(dp(12),dp(8),dp(12),dp(8)); v.setTextColor(active?Color.BLACK:TEXT); v.setBackground(round(active?ACCENT:SURFACE_2,18,active?ACCENT:BORDER,1)); return v; }'
    new_chip = 'private TextView chip(String s, boolean active) { TextView v=text(s,11,true); v.setGravity(Gravity.CENTER); v.setPadding(dp(11),dp(7),dp(11),dp(7)); v.setTextColor(active?HEADER_DARK:Color.WHITE); v.setBackground(round(active?Color.rgb(242,211,183):HEADER_DARK,4,active?Color.rgb(242,211,183):Color.rgb(119,58,70),1)); return v; }'
    s = must_replace(s, old_chip, new_chip, 'V2 nav chip')

    old_button = 'private Button button(String s) { Button b=new Button(this); b.setText(s); b.setTextColor(TEXT); b.setTextSize(12); b.setAllCaps(false); return b; }'
    new_button = 'private Button button(String s) { Button b=new Button(this); b.setText(s); b.setTextColor(ACCENT); b.setTextSize(12); b.setAllCaps(false); b.setBackground(round(SURFACE_2,6,BORDER,1)); return b; }'
    s = must_replace(s, old_button, new_button, 'V2 buttons')

    # All user-facing details use the mass PF2ERUS dictionary. Canonical English
    # remains in RuleItem for IDs, constraints and fallback search.
    s = s.replace('String.join("; ", item.prerequisites)', 'RuNames.prerequisites(item.id, item.prerequisites)')
    s = s.replace('item.description == null ? "" : item.description', 'RuNames.description(item.id, item.description)')
    s = s.replace('item.description.isEmpty() ? "Описание отсутствует." : item.description',
                  'RuNames.description(item.id, item.description).isEmpty() ? "Описание отсутствует." : RuNames.description(item.id, item.description)')
    s = s.replace('body.append(item.description);', 'body.append(RuNames.description(item.id, item.description));')
    s = s.replace('body.append("\\n\\n").append(item.description);', 'body.append("\\n\\n").append(RuNames.description(item.id, item.description));')
    V2.write_text(s, encoding='utf-8')


patch_v3()
patch_v2()
print('Prepared Gran 2e 2.0 unified UX patches')
