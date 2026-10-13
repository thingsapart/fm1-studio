/* SPDX-License-Identifier: GPL-3.0-only */
package org.fm1.wrapper;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.content.res.ColorStateList;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

final class InstrumentStyle {
    private static Typeface typeface;
    static Typeface font(Context c) {
        if(typeface==null)typeface=Typeface.createFromAsset(c.getAssets(),"sloop-screen.ttf");
        return typeface;
    }
    static GradientDrawable surface(int color,int stroke,float radius) {
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{color, color-0x00030303});
        d.setCornerRadius(radius);d.setStroke(1,stroke);return d;
    }
    static void text(TextView v) {
        v.setTypeface(font(v.getContext()));v.setIncludeFontPadding(false);
    }
    static void tree(View v) {
        if(v instanceof TextView)text((TextView)v);
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)tree(g.getChildAt(i));}
    }
}
