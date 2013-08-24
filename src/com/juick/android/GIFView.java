package com.juick.android;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * Created with IntelliJ IDEA.
 * User: san
 */

public class GIFView extends View implements IGifView {

    private Movie mMovie;
    long movieStart;

    public GIFView(Context context) {
        super(context);
        initializeView();
    }

    public GIFView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initializeView();
    }

    public GIFView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initializeView();
    }

    private void initializeView() {
        //R.drawable.loader - our animated GIF
//        InputStream is = getContext().getResources().openRawResource(R.drawable.loader);
        try {
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        } catch (Exception e) {
            //
        }
    }

    File file;

    @Override
    public void setMovieFile(File file)  {
        if (this.file != null && file != null && file.getPath().equals(this.file.getPath())) return;
        this.file = file;
        movieStart = 0;
        if (file == null) {
            mMovie = null;
        } else {
            BufferedInputStream bis = null;
            try {
                bis = new BufferedInputStream(new FileInputStream(file));
                bis.mark((int)file.length());
                mMovie = Movie.decodeStream(bis);
                if (mMovie != null && mMovie.duration() == 0) mMovie = null;
            } catch (IOException e) {
                //
            } finally {
                if (bis != null){
                    try {
                        bis.close();
                    } catch (IOException e) {

                    }
                }

            }
            this.invalidate();
        }
    }

    @Override
    public void release() {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(Color.TRANSPARENT);
        super.onDraw(canvas);
        if (mMovie == null || mMovie.duration() == 0) return;
        long now = android.os.SystemClock.uptimeMillis();
        if (movieStart == 0) {
            movieStart = now;
        }
        int relTime = mMovie.duration() == 0 ? 0 : (int) ((now - movieStart) % mMovie.duration());
        mMovie.setTime(relTime);
        final Matrix mat = new Matrix();
        //float[] vals = new float[200];
        mat.setScale(((float)getWidth())/mMovie.width(), ((float)getHeight())/mMovie.height());
        //mat.getValues(vals);
        canvas.concat(mat);
        mMovie.draw(canvas, 0, 0);
        this.invalidate();
    }


}