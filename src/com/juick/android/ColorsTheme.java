package com.juick.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import com.juickadvanced.R;
import de.quist.app.errorreporter.ExceptionReporter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 8/7/12
 * Time: 4:59 PM
 * To change this template use File | Settings | File Templates.
 */
public class ColorsTheme {

    public static class ColorSetup {
        String label;
        int color;

        ColorSetup(String label, int color) {
            this.color = color;
            this.label = label;
        }
    }

    public static class ColorTheme {
        HashMap<ColorKey, ColorSetup> editableColors;

        public ColorTheme(Context ctx) {
            editableColors = initDefaults(ctx).editableColors;
        }

        public ColorTheme(HashMap<ColorKey, ColorSetup> editableColors) {
            this.editableColors = editableColors;
        }

        public int getColor(ColorKey key, int def) {
            ColorSetup retval = editableColors.get(key);
            if (retval == null) {
                return def;
            } else {
                return retval.color;
            }
        }

        public int getForeground() {
            return getColor(ColorsTheme.ColorKey.COMMON_FOREGROUND, 0xFF000000);
        }

        public int getBackground() {
            return getColor(ColorsTheme.ColorKey.COMMON_BACKGROUND, 0xFFFFFFFF);
        }

        public int getBackground(boolean selected) {
            return selected ? getForeground() : getBackground();
        }

        public int getForeground(boolean selected) {
            return selected ? getBackground(): getForeground();
        }
    }

    public enum ColorKey {
        COMMON_BACKGROUND,
        COMMON_FOREGROUND,
        USERNAME,
        USERNAME_READ,
        TAGS,
        URLS,
        MESSAGE_ID,
        NUMBER_OF_COMMENTS,
        TRANSLATED_LABEL,
        DATE
    }


    public static ColorTheme initDefaults(Context context) {
        SharedPreferences defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        ColorKey[] values = ColorKey.values();
        HashMap<ColorKey, ColorSetup> retval = new HashMap<ColorKey, ColorSetup>();
        for (ColorKey value : values) {
            int color = defaultSharedPreferences.getInt("Colors." + value.name(), -2);
            if (color != -2)
                retval.put(value, new ColorSetup("generic",color));
        }
        return new ColorTheme(retval);
    }


}
