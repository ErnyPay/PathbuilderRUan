package ru.gran.edge2e;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/**
 * Character/save front page. 4.0 deliberately starts outside BUILD/PLAY so the
 * application behaves like a complete character manager instead of a single sheet.
 */
public final class FrontPageActivity extends Activity {
    private static final int BG = Color.rgb(239, 237, 232);
    private static final int HEADER = Color.rgb(94, 26, 40);
    private static final int HEADER_DARK = Color.rgb(71, 19, 30);
    private static final int CARD = Color.rgb(255, 255, 255);
    private static final int CARD_2 = Color.rgb(248, 246, 241);
    private static final int BORDER = Color.rgb(207, 199, 188);
    private static final int TEXT = Color.rgb(39, 36, 34);
    private static final int MUTED = Color.rgb(107, 101, 95);
    private static final int ACCENT = Color.rgb(125, 31, 48);
    private static final int WARM = Color.rgb(175, 112, 44);

    private LinearLayout body;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(HEADER_DARK);
        setContentView(shell());
    }

    @Override protected void onResume() {
        super.onResume();
        String active = CharacterProfiles.activeId(this);
        if (active != null && !active.isEmpty()) CharacterProfiles.saveCurrent(this);
        render();
    }

    private View shell() {
        LinearLayout root = column();
        root.setBackgroundColor(BG);

        LinearLayout top = column();
        top.setPadding(dp(18), dp(16), dp(18), dp(13));
        top.setBackgroundColor(HEADER);
        TextView title = text("ГРАНЬ 2e", 27, true); title.setTextColor(Color.WHITE); top.addView(title);
        TextView subtitle = text("ПЕРСОНАЖИ • СБОРКА • ИГРА", 12, true); subtitle.setTextColor(Color.rgb(242, 211, 183)); top.addView(subtitle);
        root.addView(top, matchWrap());

        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        body = column(); body.setPadding(dp(14), dp(12), dp(14), dp(34));
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        return root;
    }

    private void render() {
        if (body == null) return;
        body.removeAllViews();

        body.addView(section("НАЧАТЬ"));
        LinearLayout start = card();
        Button create = primaryButton("НОВЫЙ ПЕРСОНАЖ");
        create.setOnClickListener(v -> {
            CharacterProfiles.createNew(this);
            startActivity(new Intent(this, MainActivityV3.class));
        });
        start.addView(create, matchWrap(dp(3)));

        String active = CharacterProfiles.activeId(this);
        if (active != null && !active.isEmpty()) {
            Button build = button("ПРОДОЛЖИТЬ СБОРКУ");
            build.setOnClickListener(v -> openBuild(active));
            Button play = button("ОТКРЫТЬ В ИГРЕ");
            play.setOnClickListener(v -> openPlay(active));
            LinearLayout actions = row();
            actions.addView(build, weighted(dp(3))); actions.addView(play, weighted(dp(3)));
            start.addView(actions, matchWrap(dp(2)));
        }
        body.addView(start, matchWrap(dp(5)));

        body.addView(section("МОИ ПЕРСОНАЖИ"));
        List<CharacterProfiles.Profile> profiles = CharacterProfiles.list(this);
        if (profiles.isEmpty()) {
            LinearLayout empty = card();
            empty.addView(text("Пока нет сохранённых персонажей.", 17, true));
            TextView hint = text("Создай первый билд — он автоматически появится здесь и будет доступен отдельно в режимах СБОРКА и ИГРА.", 14, false);
            hint.setTextColor(MUTED); hint.setPadding(0, dp(5), 0, 0); empty.addView(hint);
            body.addView(empty, matchWrap(dp(5)));
        } else {
            for (CharacterProfiles.Profile p : profiles) body.addView(profileCard(p, p.id.equals(active)), matchWrap(dp(5)));
        }

        body.addView(section("СПРАВОЧНЫЕ ДАННЫЕ"));
        LinearLayout source = card();
        TextView pf = text("PF2.RU", 17, true); pf.setTextColor(ACCENT); source.addView(pf);
        TextView info = text("Русские названия, тексты и правила используются как основной языковой слой поверх локального движка персонажа.", 13, false);
        info.setTextColor(MUTED); info.setPadding(0, dp(4), 0, 0); source.addView(info);
        body.addView(source, matchWrap(dp(5)));
    }

    private View profileCard(CharacterProfiles.Profile profile, boolean active) {
        LinearLayout card = card();
        LinearLayout head = row(); head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(profile.name == null || profile.name.trim().isEmpty() ? "Без имени" : profile.name, 20, true);
        title.setTextColor(active ? ACCENT : TEXT);
        head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (active) {
            TextView badge = text("АКТИВЕН", 10, true); badge.setTextColor(ACCENT); badge.setPadding(dp(8), dp(4), dp(8), dp(4)); badge.setBackground(round(CARD_2, 12, BORDER)); head.addView(badge);
        }
        card.addView(head);
        TextView summary = text(profile.summary, 14, false); summary.setTextColor(MUTED); summary.setPadding(0, dp(3), 0, dp(8)); card.addView(summary);

        LinearLayout actions = row();
        Button build = button("СБОРКА"); build.setOnClickListener(v -> openBuild(profile.id));
        Button play = button("ИГРА"); play.setOnClickListener(v -> openPlay(profile.id));
        actions.addView(build, weighted(dp(3))); actions.addView(play, weighted(dp(3))); card.addView(actions);

        card.setOnLongClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Удалить персонажа?")
                    .setMessage(profile.name + " будет удалён из локальных сохранений.")
                    .setNegativeButton("Отмена", null)
                    .setPositiveButton("Удалить", (d, w) -> { CharacterProfiles.delete(this, profile.id); render(); })
                    .show();
            return true;
        });
        return card;
    }

    private void openBuild(String id) {
        if (!CharacterProfiles.load(this, id)) return;
        startActivity(new Intent(this, MainActivityV3.class));
    }

    private void openPlay(String id) {
        if (!CharacterProfiles.load(this, id)) return;
        Intent i = new Intent(this, MainActivityV2.class); i.putExtra("screen", "sheet"); startActivity(i);
    }

    private LinearLayout card() {
        LinearLayout l = column(); l.setPadding(dp(14), dp(12), dp(14), dp(12)); l.setBackground(round(CARD, 12, BORDER)); return l;
    }
    private TextView section(String value) { TextView v = text(value, 13, true); v.setTextColor(ACCENT); v.setPadding(dp(3), dp(10), dp(3), dp(6)); return v; }
    private Button primaryButton(String value) { Button b = button(value); b.setTextColor(Color.WHITE); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD); b.setBackground(round(ACCENT, 9, ACCENT)); return b; }
    private Button button(String value) { Button b = new Button(this); b.setText(value); b.setAllCaps(false); b.setTextSize(13); b.setTextColor(ACCENT); b.setBackground(round(CARD_2, 9, BORDER)); return b; }
    private TextView text(String value, int sp, boolean bold) { TextView v = new TextView(this); v.setText(value); v.setTextSize(sp); v.setTextColor(TEXT); if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return v; }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); return l; }
    private GradientDrawable round(int color, int radius, int stroke) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); d.setStroke(dp(1), stroke); return d; }
    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); }
    private LinearLayout.LayoutParams matchWrap(int margin) { LinearLayout.LayoutParams p = matchWrap(); p.setMargins(0, margin, 0, margin); return p; }
    private LinearLayout.LayoutParams weighted(int margin) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1); p.setMargins(margin, margin, margin, margin); return p; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
