package com.jcm.whoopheartratepoc;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.*;
import android.widget.*;

/** Shared keyboard-aware form surface, without platform alert titles or button bars. */
final class FormSheet extends Dialog {
    static final int BG=0xff101c2d, FIELD=0xff1b2c41, INK=0xfff2f7ff, MUTED=0xffa0b2c8, ACCENT=0xff66ecc4;
    final LinearLayout body, actions;
    private final Activity activity;
    FormSheet(Activity activity, String title, String subtitle) {
        super(activity); this.activity=activity; requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root=new LinearLayout(activity); root.setOrientation(1);
        root.setPadding(dp(22),dp(16),dp(22),dp(16));root.setBackground(shape(BG,24));
        LinearLayout header=new LinearLayout(activity);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading=text(title,24,INK);heading.setTypeface(activity.getResources().getFont(R.font.geist_medium));
        header.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        Button close=button("✕",false);close.setContentDescription("Close");
        close.setOnClickListener(v->dismiss());header.addView(close,new LinearLayout.LayoutParams(dp(44),dp(44)));
        root.addView(header);
        if (!subtitle.isEmpty()) {TextView sub=text(subtitle,14,MUTED);sub.setPadding(0,dp(10),0,dp(16));root.addView(sub);}
        ScrollView scroll=new ScrollView(activity);scroll.setFillViewport(false);
        body=new LinearLayout(activity);body.setOrientation(1);
        scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        actions=new LinearLayout(activity);actions.setOrientation(1);actions.setPadding(0,dp(12),0,0);root.addView(actions);
        setContentView(root);
        Window window=getWindow();
        if(window!=null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setGravity(Gravity.BOTTOM);window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams p=window.getAttributes();p.dimAmount=.55f;window.setAttributes(p);
        }
    }
    @Override public void show() {
        super.show();
        if(getWindow()!=null) {
            int height=activity.getResources().getDisplayMetrics().heightPixels;
            getWindow().setLayout(-1,(int)(height*.88f));
        }
    }
    TextView label(String text) {
        TextView t=text(text,14,MUTED);t.setPadding(0,dp(12),0,dp(8));body.addView(t);return t;
    }
    TextView error() { TextView t=label("");t.setTextColor(0xffff9ca8);return t; }
    EditText input(String label, String hint, String value, boolean multi) {
        label(label);EditText field=new EditText(activity);
        styleInput(field);field.setHint(hint);field.setText(value);
        field.setInputType(InputType.TYPE_CLASS_TEXT | (multi ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : InputType.TYPE_TEXT_FLAG_CAP_SENTENCES));
        field.setMinLines(multi?4:1);field.setMaxLines(multi?9:1);
        body.addView(field,new LinearLayout.LayoutParams(-1,-2));return field;
    }
    void styleInput(EditText field) {
        field.setTextColor(INK);field.setHintTextColor(0xff8094ad);field.setTextSize(16);
        field.setPadding(dp(14),dp(14),dp(14),dp(14));
        field.setBackground(shape(FIELD,12));field.setGravity(Gravity.TOP|Gravity.START);
        field.setSelectAllOnFocus(false);
    }
    Button action(String label, boolean primary, Runnable click) {
        Button b=button(label,primary);b.setOnClickListener(v->click.run());
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(50));p.topMargin=dp(8);actions.addView(b,p);return b;
    }
    Button item(String title, String detail, Runnable click) {
        Button b=button(title+(detail.isEmpty()?"":"\n"+detail),false);
        b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);b.setOnClickListener(v->click.run());
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(8);
        b.setMinHeight(dp(68));body.addView(b,p);return b;
    }
    Button button(String title, boolean primary) {
        Button b=new Button(activity);b.setText(title);b.setTextSize(15);b.setAllCaps(false);
        b.setTypeface(activity.getResources().getFont(R.font.geist_medium));b.setLetterSpacing(0);
        b.setTextColor(primary?BG:INK);b.setBackground(shape(primary?ACCENT:FIELD,12));
        b.setPadding(dp(14),dp(10),dp(14),dp(10));b.setStateListAnimator(null);
        b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(0);b.setMinimumHeight(0);return b;
    }
    TextView text(String value,int size,int color) {
        TextView t=new TextView(activity);t.setText(value);t.setTextSize(size);t.setTextColor(color);return t;
    }
    GradientDrawable shape(int color,int radius) {GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    int dp(int n){return Math.round(n*activity.getResources().getDisplayMetrics().density);}
}
