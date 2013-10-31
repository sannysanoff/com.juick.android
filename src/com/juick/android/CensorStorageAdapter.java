package com.juick.android;

import java.util.ArrayList;

/**
 * Created with IntelliJ IDEA.
 * User: coderoo
 * Date: 28/10/13
 * Time: 5:03 PM
 * To change this template use File | Settings | File Templates.
 */
public interface CensorStorageAdapter {

    public ArrayList<String> fetchBlacklist(int level);
    public void saveBlacklist(int level, ArrayList<String> list);
    public void deleteBlacklist(int level);
}
