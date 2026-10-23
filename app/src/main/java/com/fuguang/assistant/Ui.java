package com.fuguang.assistant;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.*;

final class Ui {
    static final int BLUE=Color.rgb(50,92,232), INK=Color.rgb(23,33,57), MUTED=Color.rgb(104,115,137), BG=Color.rgb(245,247,252);
    static int dp(Context c,float v){return Math.round(v*c.getResources().getDisplayMetrics().density);}
    static GradientDrawable bg(int color,int radius,Context c){GradientDrawable d=new GradientDrawable(); d.setColor(color);d.setCornerRadius(dp(c,radius));return d;}
    static LinearLayout column(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(LinearLayout.VERTICAL);return l;}
    static TextView text(Context c,String s,int size,int color){TextView t=new TextView(c);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,dp(c,5),0,dp(c,5));return t;}
    static TextView title(Context c,String s,int size){TextView t=text(c,s,size,INK);t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    static Button button(Context c,String s,boolean primary){Button b=new Button(c);b.setText(s);b.setAllCaps(false);b.setTextSize(15);b.setTextColor(primary?Color.WHITE:BLUE);b.setBackground(bg(primary?BLUE:0xffeaf0ff,16,c));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(c,52));p.topMargin=dp(c,12);b.setLayoutParams(p);return b;}
    static EditText input(Context c,String hint){EditText e=new EditText(c);e.setTextSize(15);e.setTextColor(INK);e.setHintTextColor(MUTED);e.setHint(hint);e.setPadding(dp(c,14),dp(c,12),dp(c,14),dp(c,12));e.setBackground(bg(BG,14,c));return e;}
    static LinearLayout card(Context c,LinearLayout parent){LinearLayout box=column(c);box.setPadding(dp(c,18),dp(c,16),dp(c,18),dp(c,18));box.setBackground(bg(Color.WHITE,22,c));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(c,16);parent.addView(box,p);return box;}
}
