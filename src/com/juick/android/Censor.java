package com.juick.android;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;

/**
 * Created with IntelliJ IDEA.
 * User: coderoo
 * Date: 28/10/13
 * Time: 4:57 PM
 * To change this template use File | Settings | File Templates.
 */
public class Censor {

    public static final int MAX_CENSORSHIP_LEVEL = 10;

    /*
    0 - censor disabled
    1..MAX_CENSORSHIP_LEVEL - censor enabled
    */
    private static int censorshipLevel = 0;

    private static boolean isBlacklistsCacheInvalid = true;

    private static CensorStorageAdapter storageAdapter;

    private static CensorServerAdapter serverAdapter;

    private static ArrayList<Set<String>> blacklists;

    public static final String TOKEN_DELIMITERS = " ,.:;\\\\\\t\\n\\r\\f\\a";

    public static final String CENSORED_WORD = "*****************************************************************************************************************************************************************************";

    public static CensorStorageAdapter getStorageAdapter() {
        if (storageAdapter == null) {
            storageAdapter = new CensorDummyStorageAdapter();
        }
        return storageAdapter;
    }

    public static CensorServerAdapter getServerAdapter() {
        if (serverAdapter == null) {
            serverAdapter = new CensorDummyServerAdapter();
        }
        return serverAdapter;
    }

    public static int getCensorshipLevel() {
        return censorshipLevel;
    }

    public static boolean isEnabled() {
        return censorshipLevel > 0;
    }

    public static void disable() {
        setCensorshipLevel(0);
    }

    private static ArrayList<Set<String>> getBlacklists() {
        if (blacklists == null) {
            blacklists = new ArrayList<Set<String>>(MAX_CENSORSHIP_LEVEL);
        }
        return blacklists;
    }

    private static void clearBlacklistsCache() {
        if (blacklists != null) {
            blacklists.clear();
        }
        isBlacklistsCacheInvalid = true;
    }

    public static void reloadBlacklistsCache(boolean forceReload) {
        if (isBlacklistsCacheInvalid || forceReload) {
            for (int level = 1; level <= getCensorshipLevel(); level++) {
                getBlacklists().add(new HashSet<String>(getStorageAdapter().fetchBlacklist(level)));
            }
            isBlacklistsCacheInvalid = false;
        }
    }

    public static void setCensorshipLevel(int censorshipLevel) {
        Censor.censorshipLevel = censorshipLevel;
        // clear blacklists' memory cache, might need to optimise later
        clearBlacklistsCache();
    }

    private static ArrayList<String> tokenizeString(final String text) {
        StringTokenizer st = new StringTokenizer(text, TOKEN_DELIMITERS);
        ArrayList<String> result = new ArrayList<String>();
        while (st.hasMoreTokens()) {
            String token = st.nextToken();
            if (token.length() > 1) {
                result.add(token);
            }
        }
        return result;
    }

    // based on of http://stackoverflow.com/a/3472705
    private static void replaceAll(StringBuilder builder, String from, String to) {
        int index = builder.indexOf(from);
        while (index != -1) {
            builder.replace(index, index + from.length(), to);
            index += to.length(); // Move to the end of the replacement
            index = builder.indexOf(from, index);
        }
    }

    public static String getCensoredText(final String textToCensor) {
        if (isEnabled()) {
            ArrayList<String> tokens = tokenizeString(textToCensor);
            if (tokens.isEmpty()) {
                // No tokens found, returning the original text
                return textToCensor;
            } else {
                StringBuilder result = null;
                reloadBlacklistsCache(false);
                for (String token: tokens) {
                    String uppercaseToken = token.toUpperCase();
                    for (Set<String> blacklist : getBlacklists()) {
                        if (blacklist.contains(uppercaseToken)) {
                            if (result == null) {
                                // the first blacklisted token found
                                result = new StringBuilder(textToCensor);
                            }
                            int tokenLength = token.length();
                            replaceAll(result, token, (tokenLength <= CENSORED_WORD.length())?
                                    CENSORED_WORD.substring(0, tokenLength) : CENSORED_WORD );
                            // we have censored this token, moving on to the next one
                            break;
                        }
                    }
                }
                return (result == null) ? textToCensor : result.toString();
            }
        } else {
            // Censor is disabled, returning the original text
            return textToCensor;
        }
    }
}
