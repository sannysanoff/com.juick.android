package com.juick.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.ListView;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 10/12/12
 * Time: 11:42 PM
 * To change this template use File | Settings | File Templates.
 */
public class MyListView extends ListView {
    public MyListView(Context context) {
        super(context);
    }

    public MyListView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MyListView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public int maxActiveFingers = 0;
    boolean shouldReset = false;

    public int getMaxActiveFingers() {
        return maxActiveFingers;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        switch(ev.getActionMasked()) {
            case MotionEvent.ACTION_POINTER_DOWN:
                if (shouldReset) {
                    shouldReset = false;
                    maxActiveFingers = 0;
                }
                maxActiveFingers = Math.max(ev.getPointerCount(), maxActiveFingers);
                break;
            case MotionEvent.ACTION_DOWN:
                if (shouldReset) {
                    shouldReset = false;
                    maxActiveFingers = 0;
                }
                maxActiveFingers = Math.max(ev.getPointerCount(), maxActiveFingers);
                break;
            case MotionEvent.ACTION_POINTER_UP:
                break;
            case MotionEvent.ACTION_UP:     // last pointer up
                shouldReset = true;
                break;
        }
        try {
            return super.onTouchEvent(ev);
        } finally {

        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
    }

    public boolean blockLayoutRequests;

    @Override
    public void requestLayout() {
        // if (blockLayoutRequests) return;
        super.requestLayout();    //To change body of overridden methods use File | Settings | File Templates.
    }

    @Override
    public void invalidate(Rect dirty) {
        super.invalidate(dirty);    //To change body of overridden methods use File | Settings | File Templates.
    }

    @Override
    public void draw(Canvas canvas) {
        try {
            super.draw(canvas);    //To change body of overridden methods use File | Settings | File Templates.
        } catch (Exception e) {
            // shit happens
        }
    }
}
