package com.juick.android;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    private static List<Set<String>> blacklists;

    public static final Character CENSORED_CHAR = '*';


    public interface CensorServerAdapter {
        public void submitForReview(int level, String token);
    }

    public interface CensorStorageAdapter {

        public List<String> fetchBlacklist(final int level);

        public void saveBlacklist(final int level, final List<String> list);

        public void deleteBlacklist(final int level);
    }

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

    public static boolean isDisabled() {
        return !isEnabled();
    }


    public static void disable() {
        setCensorshipLevel(0);
    }

    private static List<Set<String>> getBlacklists() {
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

    public static void setCensorshipLevel(int newLevel) {
        censorshipLevel = newLevel;
        // clear blacklists' memory cache, might need to optimise later
        clearBlacklistsCache();
    }

    public static String getCensoredText(final String originalText) {
        if (Censor.isDisabled() || originalText == null) {
            return originalText;
        } else {
            BreakIterator wordIterator = BreakIterator.getWordInstance();
            String unicaseText = originalText.toLowerCase();
            wordIterator.setText(unicaseText);
            int start = wordIterator.first();
            StringBuilder censoredText = null;
            for (int end = wordIterator.next(); end != BreakIterator.DONE; start = end, end = wordIterator.next()) {
                if (end - start > 1) {
                    String word = unicaseText.substring(start, end);
                    reloadBlacklistsCache(false);
                    for (Set<String> blacklist : getBlacklists()) {
                        if (blacklist.contains(word)) {
                            if (censoredText == null) {
                                censoredText = new StringBuilder(originalText);
                            }
                            for (int pos = start; pos < end; pos++) {
                                censoredText.setCharAt(pos, CENSORED_CHAR);
                            }
                            // we have censored this word, moving on to the next one
                            break;
                        }
                    }
                }
            }
            return (censoredText == null) ? originalText : censoredText.toString();
        }
    }
}
