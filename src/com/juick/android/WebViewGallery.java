package com.juick.android;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.Gallery;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 8/11/12
 * Time: 1:18 AM
 * To change this template use File | Settings | File Templates.
 */
public class WebViewGallery extends Gallery {

    private final int slop;
    private float initialX;
    private float initialY;

    public WebViewGallery(Context context) {
        super(context);
        slop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public WebViewGallery(Context context, AttributeSet attrs) {
        super(context, attrs);
        slop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public WebViewGallery(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        slop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        return false;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (getAdapter().getCount() == 1) {
            return true; // dont pass horizontal scrolls to single item
        }
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                    /*
                     * Kludge: Both the gallery and the child need to see the
                     * gesture, until we know enough about it to decide who gets it.
                     */
                onTouchEvent(ev);

                initialX = ev.getX();
                initialY = ev.getY();

                return false;

            case MotionEvent.ACTION_MOVE:
                float distX = Math.abs(ev.getX() - initialX);
                float distY = Math.abs(ev.getY() - initialY);

                if (distY > distX && distY > slop)
                        /* Vertical scroll, child takes the gesture. */
                    return false;

                    /*
                     * If a horizontal scroll, we take the gesture, otherwise keep
                     * peeking.
                     */
                return true; // distX > slop;

            default:
                return false;
        }
    }

    @Override
    public boolean hasFocusable() {
        return false;
    }
}
