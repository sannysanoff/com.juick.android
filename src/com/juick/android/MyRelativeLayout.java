package com.juick.android;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.ViewParent;
import android.widget.RelativeLayout;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 4/1/13
 * Time: 11:58 PM
 * To change this template use File | Settings | File Templates.
 */
public class MyRelativeLayout extends RelativeLayout {
    public MyRelativeLayout(Context context) {
        super(context);
        init();
    }

    private void init() {
    }

    public MyRelativeLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MyRelativeLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    public boolean blockLayoutRequests;

    @Override
    public void requestLayout() {
        if (blockLayoutRequests) return;
        super.requestLayout();    //To change body of overridden methods use File | Settings | File Templates.
    }

    interface Listener {
        void onLayout();
    }

    Listener listener;

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        try {
            super.onLayout(changed, l, t, r, b);
            if (listener != null) {
                listener.onLayout();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
