package com.juick.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.widget.TextView;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 3/6/13
 * Time: 7:38 PM
 * To change this template use File | Settings | File Templates.
 */
public class MyTextView extends TextView {


    public MyTextView(Context context) {
        super(context);
    }

    public MyTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MyTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public JuickMessagesAdapter.RenderedText content;

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.save();
        super.onDraw(canvas);
        if (content != null) {
            for (JuickMessagesAdapter.CanvasPainter canvasPainter : content.data) {
                canvasPainter.paintOnCanvas(canvas, null);
            }
        }
        canvas.restore();
        if (content != null) {
            final Paint paint = new Paint();
            paint.setColor(Color.BLUE);
            paint.setStrokeWidth(2);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(80, 5, 3, paint);
        }
    }

    public boolean blockLayoutRequests;

    @Override
    public void requestLayout() {
        if (blockLayoutRequests) return;
        super.requestLayout();    //To change body of overridden methods use File | Settings | File Templates.
    }


}
