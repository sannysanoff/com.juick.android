package com.juick.android;

import android.content.Context;
import android.util.AttributeSet;
import android.webkit.WebView;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 10/20/12
 * Time: 11:01 AM
 * To change this template use File | Settings | File Templates.
 */
public class MyWebView extends WebView {

    public static int instanceCount = 0;

    {
        instanceCount++;
    }

    public MyWebView(Context context) {
        super(context);
    }

    public MyWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MyWebView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public MyWebView(Context context, AttributeSet attrs, int defStyle, boolean privateBrowsing) {
        super(context, attrs, defStyle, privateBrowsing);
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        instanceCount--;
    }
}
