package com.juick.android;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.os.Build;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.webkit.WebView;
import android.widget.ImageView;
import com.juickadvanced.R;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 10/20/12
 * Time: 11:01 AM
 * To change this template use File | Settings | File Templates.
 */
public class MyImageView extends ImageView {

    public float initialTranslationX = -1;
    public float initialTranslationY = -1;

    public MyImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initFromAttrs(attrs);
    }

    public MyImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initFromAttrs(attrs);
    }

    private void initFromAttrs(AttributeSet attrs) {
        // getContext().obtainStyledAttributes(attrs, new int[]{50})
        for(int i=0; i<attrs.getAttributeCount(); i++) {
            if (attrs.getAttributeName(i).equals("translationX")) {
                initialTranslationX = getDimension(attrs.getAttributeValue(i));
            }
            if (attrs.getAttributeName(i).equals("translationY")) {
                initialTranslationY = getDimension(attrs.getAttributeValue(i));
            }
        }
        System.out.println();
    }

    private float getDimension(String attributeValue) {
        if (attributeValue.endsWith("dip")) {
            float value = Float.parseFloat(attributeValue.substring(0, attributeValue.length() - 3));
            return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
        }
        throw new RuntimeException("OOps");
    }


}
