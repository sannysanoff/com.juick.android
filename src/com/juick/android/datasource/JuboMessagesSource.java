package com.juick.android.datasource;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import com.juick.android.Utils;
import com.juick.android.api.JuickMessage;
import com.juick.android.api.JuickUser;
import com.juickadvanced.R;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Vector;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 9/19/12
 * Time: 10:56 PM
 * To change this template use File | Settings | File Templates.
 */
public class JuboMessagesSource extends MessagesSource {

    public JuboMessagesSource(Context ctx) {
        super(ctx);
    }

    @Override
    public void getChildren(int mid, Utils.Notification notifications, Utils.Function<Void, ArrayList<JuickMessage>> cont) {
        new JuickCompatibleURLMessagesSource(ctx).getChildren(mid, notifications, cont);
    }

    @Override
    public boolean supportsBackwardRefresh() {
        return false;
    }

    @Override
    public void getFirst(Utils.Notification notifications, Utils.Function<Void, ArrayList<JuickMessage>> cont) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
        String juboRssURL = sp.getString("juboRssURL", "");
        if (juboRssURL != null) {
            try {
                String result = Utils.getJSON(ctx, juboRssURL, notifications).getResult();
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                DocumentBuilder documentBuilder = dbf.newDocumentBuilder();
                Document doc = documentBuilder.parse(new ByteArrayInputStream(result.getBytes("UTF-8")));
                NodeList items = doc.getElementsByTagName("item");
                ArrayList<JuickMessage> resultList = new ArrayList<JuickMessage>();
                if (items != null) {
                    for(int i=0; i<items.getLength(); i++) {
                        Element item = (Element)items.item(i);
                        JuickMessage jm = rssItemToMessage(item);
                        resultList.add(jm);
                    }
                }
                System.out.println(result);
                cont.apply(resultList);
            } catch (Exception ex) {
                if (notifications instanceof Utils.DownloadErrorNotification) {
                    ((Utils.DownloadErrorNotification)notifications).notifyDownloadError(ex.toString());
                }
                cont.apply(new ArrayList<JuickMessage>());
            }
        }
    }


    transient SimpleDateFormat rssDateFormat;

    private JuickMessage rssItemToMessage(Element item) {
        if (rssDateFormat == null) {
            // Tue, 18 Sep 2012 19:21:15 +0400
            rssDateFormat = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z", Locale.ENGLISH);
        }
        NodeList guid = item.getElementsByTagName("guid");
        String textContent = guid.item(0).getTextContent();
        int lastSlash = textContent.lastIndexOf("/");
        int msgNo = Integer.parseInt(textContent.substring(lastSlash + 1));
        JuickMessage jm = new JuickMessage();
        jm.MID = msgNo;
        NodeList descrs = item.getElementsByTagName("description");
        String txt = descrs.item(0).getTextContent();
        txt = txt.replace("<br />","");
        txt = txt.replace("&gt;",">");
        txt = txt.replace("&lt;","<");
        txt = txt.replace("&quot;","\"");
        txt = txt.replace("&amp;","&");
        jm.Text = txt;
        jm.User = new JuickUser();
        jm.User.UID = -1;
        NodeList titles = item.getElementsByTagName("title");
        String title = titles.item(0).getTextContent();
        int titleColon = title.indexOf(":");
        jm.User.UName = title.substring(0, titleColon).substring(1);
        NodeList tags = item.getElementsByTagName("category");
        jm.tags = new Vector<String>();
        for(int q=0; q<tags.getLength(); q++) {
            jm.tags.add(tags.item(q).getTextContent());
        }
        NodeList dates = item.getElementsByTagName("pubDate");
        try {
            jm.Timestamp = rssDateFormat.parse(dates.item(0).getTextContent());
        } catch (ParseException e) {
            jm.Timestamp = new Date(1900, 0, 1);
        }
        return jm;
    }

    @Override
    public void getNext(Utils.Notification notifications, Utils.Function<Void, ArrayList<JuickMessage>> cont) {
        cont.apply(new ArrayList<JuickMessage>());
    }

    @Override
    public CharSequence getTitle() {
        return ctx.getString(R.string.navigationJuboRSS);
    }
}
