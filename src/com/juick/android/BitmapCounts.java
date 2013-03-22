package com.juick.android;

import android.graphics.Bitmap;

import java.util.HashMap;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 3/22/13
 * Time: 7:43 PM
 * To change this template use File | Settings | File Templates.
 */
public class BitmapCounts {
    public static HashMap<Bitmap, Integer> counts = new HashMap<Bitmap, Integer>();

    // ios heritage! reference counting

    public synchronized static void retainBitmap(Bitmap bitmap) {
        Integer integer = counts.get(bitmap);
        if (integer == null) {
            integer = 1;
        } else {
            integer = integer + 1;
        }
        counts.put(bitmap, integer);
    }

    public static synchronized void releaseBitmap(Bitmap bitmap) {
        Integer integer = counts.get(bitmap);
        if (integer == null) {
            // not ours. no warning
        } else if (integer == 1) {
            counts.remove(bitmap);
            bitmap.recycle();
        } else {
            counts.put(bitmap, integer -1 );
        }
    }

}
