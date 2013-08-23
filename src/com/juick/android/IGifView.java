package com.juick.android;

import android.view.ViewGroup;

import java.io.File;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 8/23/13
 * Time: 2:56 PM
 * To change this template use File | Settings | File Templates.
 */
public interface IGifView {
    void setMovieFile(File file);

    void setVisibility(int i);

    void release();


    ViewGroup.LayoutParams getLayoutParams();
}
