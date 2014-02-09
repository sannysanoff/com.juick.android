package com.juick.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import com.google.gson.Gson;

import java.io.File;
import java.util.HashMap;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 8/7/12
 * Time: 4:59 PM
 * To change this template use File | Settings | File Templates.
 */
public class ColorsTheme {

    public static final String COLOR_PREFERENCE_PREFIX = "Colors.";
    public static final String COLORS_THEMES_DIR = "ColorsThemes";
    public static final String COLOR_THEME_FILE_EXTENSION = "clr";
    public static final String COLOR_THEME_FILE_EXTENSION_WITH_A_PERIOD = '.' + COLOR_THEME_FILE_EXTENSION;

    public static final String[] FILE_DIALOG_FILE_EXTENSIONS = new String[]{COLOR_THEME_FILE_EXTENSION};


    public static class ColorSetup {
        String label;
        int color;

        ColorSetup(String label, int color) {
            this.color = color;
            this.label = label;
        }
    }

    public static class SimpleColorTheme {
        private HashMap<String, Integer> colors;

        public SimpleColorTheme() {
            colors = new HashMap<String, Integer>();
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

        public int getButtonBackground() {
            return getColor(ColorsTheme.ColorKey.BUTTON_BACKGROUND, getBackground());
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
        BUTTON_BACKGROUND,
        DIVIDER,
        COMMON_FOREGROUND,
        USERNAME,
        USERNAME_ME,
        USERNAME_READ,
        TAGS,
        URLS,
        MESSAGE_ID,
        NUMBER_OF_COMMENTS,
        TRANSLATED_LABEL,
        DATE
    }

    public static File getStorageDir(Context context) {
        File dir = new File(context.getFilesDir(), COLORS_THEMES_DIR);
        dir.mkdirs();
        return dir;
    }

    public static String addExtensionIfRequired(String fileName) {
        if (fileName.toLowerCase().endsWith(COLOR_THEME_FILE_EXTENSION_WITH_A_PERIOD)) {
            return fileName;
        } else {
            return fileName + COLOR_THEME_FILE_EXTENSION_WITH_A_PERIOD;
        }
    }

    public static void saveColorsTheme(Context context, String fileName) {
        final HashMap<ColorKey, ColorSetup> colors = readColorsFromPreferences(context);
        SimpleColorTheme simpleColors = new SimpleColorTheme();
        for (ColorKey colorKey : colors.keySet()) {
            String simpleColorKey = colorKey.name();
            Integer simpleColorValue = Integer.valueOf(colors.get(colorKey).color);
            simpleColors.colors.put(simpleColorKey, simpleColorValue);
        }
        Gson gson = new Gson();
        final String colorsAsJson = gson.toJson(simpleColors);

        File file = new File(addExtensionIfRequired(fileName));
        XMPPService.writeStringToFile(file, colorsAsJson);
    }

    public static boolean loadColorsTheme(Context context, String fileName) {
        File file = new File(fileName);
        String colorsAsJson = XMPPService.readFile(file);
        if (colorsAsJson == null) {
            return false;
        }
        Gson gson = new Gson();
        final SimpleColorTheme simpleColors = gson.fromJson(colorsAsJson, SimpleColorTheme.class);
        writeColorsToPreferences(context, simpleColors);
        return true;
    }

    private static void writeColorsToPreferences(Context context, SimpleColorTheme colorsToWrite) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
        JuickMessagesAdapter.clearColorTheme();
        for (ColorKey colorKey : ColorKey.values()) {
            if (colorsToWrite.colors.containsKey(colorKey.name())) {
                String preferenceKey = COLOR_PREFERENCE_PREFIX + colorKey.name();
                int preferenceValue = colorsToWrite.colors.get(colorKey.name()).intValue();
                editor.putInt(preferenceKey, preferenceValue);
            }
        }
        editor.commit();
    }

    private static HashMap<ColorKey, ColorSetup> readColorsFromPreferences(Context context) {
        SharedPreferences defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        ColorKey[] values = ColorKey.values();
        HashMap<ColorKey, ColorSetup> retval = new HashMap<ColorKey, ColorSetup>();
        for (ColorKey value : values) {
            int color = defaultSharedPreferences.getInt(COLOR_PREFERENCE_PREFIX + value.name(), -2);
            if (color != -2)
                retval.put(value, new ColorSetup("generic", color));
        }
        return retval;
    }

    public static ColorTheme initDefaults(Context context) {
        return new ColorTheme(readColorsFromPreferences(context));
    }



}
