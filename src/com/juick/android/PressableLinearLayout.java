/*
 * Juick
 * Copyright (C) 2008-2012, Ugnich Anton
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.juick.android;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.View;
import android.widget.*;

public class PressableLinearLayout extends LinearLayout {

    public static int instanceCount = 0;
    {
        instanceCount++;
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        instanceCount--;
    }


    interface PressedListener {
        public void onPressStateChanged(boolean selected);
        public void onSelectStateChanged(boolean selected);
    }

    PressedListener pressedListener;

    public PressableLinearLayout(Context context) {
        super(context);
    }

    public PressableLinearLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public PressableLinearLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public void setPressed(boolean selected) {
        super.setPressed(selected);
        if (pressedListener != null)
            pressedListener.onPressStateChanged(selected);
    }

    public void setPressedListener(PressedListener pressedListener) {
        this.pressedListener = pressedListener;
    }

    @Override
    public void setSelected(boolean selected) {
        super.setSelected(selected);    //To change body of overridden methods use File | Settings | File Templates.
        if (pressedListener != null)
            pressedListener.onSelectStateChanged(selected);
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        try {
            return super.drawChild(canvas, child, drawingTime);    //
        } catch (Exception e) {
            System.out.println("E="+e);
            return false;
        }
    }

}
