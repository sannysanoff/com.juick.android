package com.juick.android.bnw;

import com.juick.android.MainActivity;
import com.juick.android.MicroBlog;
import com.juick.android.api.MessageID;
import com.juick.android.juick.JuickMicroBlog;

import java.io.Serializable;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 11/9/12
 * Time: 4:04 AM
 * To change this template use File | Settings | File Templates.
 */
public class BnwMessageID extends MessageID implements Serializable {
    String id;

    public BnwMessageID(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Override
    public String toString() {
        return "bnw-"+id;
    }

    @Override
    public String toDisplayString() {
        return "#"+id;
    }

    @Override
    public MicroBlog getMicroBlog() {
        return MainActivity.getMicroBlog(BNWMicroBlog.CODE);
    }

    public static BnwMessageID fromString(String str) {
        if (str.startsWith("bnw-")) {
            return new BnwMessageID(str.substring(4));
        }
        return null;
    }

    static String remap = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    public int toInt() {
        int integer = 0;
        for(int i=0; i<6; i++) {
            int ix = remap.indexOf(id.charAt(i));
            integer = integer * remap.length() + ix;
        }
        return integer;
    }

    public static String bnwToInt(int midd) {
        String smid = "";
        for(int i=0; i<6; i++) {
            smid = remap.charAt(midd % remap.length()) + smid;
            midd /= remap.length();
        }
        return smid;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BnwMessageID that = (BnwMessageID) o;

        if (id != null ? !id.equals(that.id) : that.id != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
