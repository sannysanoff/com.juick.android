package com.juick.android;

import java.util.ArrayList;

/**
 * Created with IntelliJ IDEA.
 * User: coderoo
 * Date: 28/10/13
 * Time: 5:08 PM
 * To change this template use File | Settings | File Templates.
 */
public class CensorDummyStorageAdapter implements CensorStorageAdapter {

    public static final int WORDS_PER_LEVEL = 300;
    public static final String WORD_PREFIX = "TEST";

    @Override
    public ArrayList<String> fetchBlacklist(int level) {
        ArrayList<String> result = new ArrayList<String>();
        for (int i = 0; i < WORDS_PER_LEVEL; i++) {
            if (level == 1) {
                result.add("ЖОК");
            }
            result.add(new StringBuilder()
                    .append(WORD_PREFIX)
                    .append(String.format("%02d", level))
                    .append(String.format("%04d", i + 1))
                    .toString());
        }
        return result;
    }

    @Override
    public void saveBlacklist(int level, ArrayList<String> list) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void deleteBlacklist(int level) {
        //To change body of implemented methods use File | Settings | File Templates.
    }
}
