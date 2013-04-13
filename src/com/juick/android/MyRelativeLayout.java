package com.juick.android;

import android.content.Context;
import android.util.AttributeSet;
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
    }

    public MyRelativeLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MyRelativeLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public boolean blockLayoutRequests;

    @Override
    public void requestLayout() {
        if (blockLayoutRequests) return;
        super.requestLayout();    //To change body of overridden methods use File | Settings | File Templates.
    }


}
