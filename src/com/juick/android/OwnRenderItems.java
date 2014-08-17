package com.juick.android;

import android.content.Context;
import android.view.View;
import com.juickadvanced.data.juick.JuickMessage;

/**
 * Created by san on 6/1/14.
 */
public interface OwnRenderItems {

    View getView(Context context, JuickMessage jmsg, View convertView);
}
