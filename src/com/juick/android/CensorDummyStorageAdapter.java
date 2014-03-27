package com.juick.android;

import java.util.ArrayList;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: coderoo
 * Date: 28/10/13
 * Time: 5:08 PM
 * To change this template use File | Settings | File Templates.
 */
public class CensorDummyStorageAdapter implements Censor.CensorStorageAdapter {

    public static final int WORDS_PER_LEVEL = 300;
    public static final String WORD_PREFIX = "TEST";

    @Override
    public List<String> fetchBlacklist(final int level) {
        ArrayList<String> result = new ArrayList<String>();
        for (int i = 0; i < WORDS_PER_LEVEL; i++) {
            if (level == 1) {
                result.add("УВИДЕЛИ");
                result.add("ПОКУПАЛ");
                result.add("ДОСТАВКА");
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
    public void saveBlacklist(final int level, final List<String> list) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void deleteBlacklist(final int level) {
        //To change body of implemented methods use File | Settings | File Templates.
    }
}
