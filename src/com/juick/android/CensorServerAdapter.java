package com.juick.android;

/**
 * Created with IntelliJ IDEA.
 * User: coderoo
 * Date: 28/10/13
 * Time: 8:55 PM
 * To change this template use File | Settings | File Templates.
 */

public interface CensorServerAdapter {
    public void submitForReview(int level, String token);
}
