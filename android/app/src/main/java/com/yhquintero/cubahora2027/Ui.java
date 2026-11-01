package com.yhquintero.cubahora2027;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Utilidades mínimas de interfaz, con la paleta de la guía web del proyecto. */
final class Ui {

    static final int NAVY = 0xFF10213B;
    static final int NAVY_DEEP = 0xFF0B1526;
    static final int INK = 0xFF14233B;
    static final int MUTED = 0xFF67758A;
    static final int LINE = 0xFFE2E7EF;
    static final int CANVAS = 0xFFF5F7FB;
    static final int WHITE = 0xFFFFFFFF;
    static final int BLUE = 0xFF4F6FE2;
    static final int BLUE_DARK = 0xFF3151BD;
    static final int BLUE_PALE = 0xFFEDF1FF;
    static final int MINT_PALE = 0xFFEAF8F3;
    static final int MINT_DEEP = 0xFF207C64;
    static final int AMBER_PALE = 0xFFFDF4DE;
    static final int AMBER_DEEP = 0xFF946613;
    static final int ROSE_PALE = 0xFFFBEDED;
    static final int ROSE = 0xFFC95753;

    private Ui() {
    }

    static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable rounded(Context context, int color, float radiusDp, Integer strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radiusDp));
        if (strokeColor != null) {
            drawable.setStroke(dp(context, 1), strokeColor);
        }
        return drawable;
    }

    static TextView text(Context context, CharSequence value, float sp, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        view.setLineSpacing(0f, 1.15f);
        if (bold) {
            view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        }
        return view;
    }

    static TextView pill(Context context, CharSequence value, int background, int color) {
        TextView view = text(context, value, 12.5f, color, true);
        view.setBackground(rounded(context, background, 999f, null));
        int h = dp(context, 8);
        int v = dp(context, 5);
        view.setPadding(dp(context, 12), v, dp(context, 12), v);
        view.setLineSpacing(0f, 1f);
        view.setMinHeight(h);
        return view;
    }

    static Button button(Context context, CharSequence label, boolean primary) {
        Button button = new Button(context);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        button.setGravity(android.view.Gravity.CENTER);
        button.setMinHeight(dp(context, 50));
        button.setMinimumHeight(dp(context, 50));
        button.setPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 12));
        button.setStateListAnimator(null);
        android.graphics.drawable.StateListDrawable states = new android.graphics.drawable.StateListDrawable();
        int base = primary ? BLUE : WHITE;
        int pressed = primary ? BLUE_DARK : BLUE_PALE;
        states.addState(new int[]{android.R.attr.state_pressed},
                rounded(context, pressed, 14f, primary ? null : LINE));
        states.addState(new int[]{android.R.attr.state_enabled},
                rounded(context, base, 14f, primary ? null : LINE));
        states.addState(new int[]{}, rounded(context, primary ? 0xFF8093E8 : 0xFFF1F3F8, 14f, LINE));
        button.setBackground(states);
        button.setTextColor(primary ? WHITE : INK);
        button.setElevation(0f);
        return button;
    }

    static LinearLayout card(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(rounded(context, WHITE, 18f, LINE));
        int p = dp(context, 18);
        card.setPadding(p, p, p, p);
        card.setElevation(dp(context, 1));
        return card;
    }

    static LinearLayout row(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        int top = dp(context, 10);
        row.setPadding(0, top, 0, top);
        return row;
    }

    static LinearLayout pair(Context context, TextView first, TextView second) {
        LinearLayout line = new LinearLayout(context);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setBaselineAligned(false);
        LinearLayout.LayoutParams leftParams =
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        LinearLayout.LayoutParams rightParams =
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
        first.setLayoutParams(leftParams);
        second.setLayoutParams(rightParams);
        second.setGravity(android.view.Gravity.END);
        second.setPadding(dp(context, 12), 0, 0, 0);
        line.addView(first);
        line.addView(second);
        return line;
    }

    static View divider(Context context) {
        View divider = new View(context);
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 1));
        divider.setLayoutParams(params);
        divider.setBackgroundColor(LINE);
        return divider;
    }

    static View spacer(Context context, float dp) {
        View view = new View(context);
        view.setLayoutParams(new LinearLayout.LayoutParams(1, dp(context, dp)));
        return view;
    }

    static int alpha(int color, float alpha) {
        return Color.argb(Math.round(alpha * 255), Color.red(color), Color.green(color), Color.blue(color));
    }
}
