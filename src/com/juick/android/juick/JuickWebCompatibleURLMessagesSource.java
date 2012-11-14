package com.juick.android.juick;

import android.content.Context;
import com.juick.android.URLParser;
import com.juick.android.Utils;
import com.juick.android.api.JuickMessage;
import com.juick.android.api.JuickUser;
import com.juick.android.api.MessageID;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.TimeZone;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 11/6/12
 * Time: 12:59 AM
 * To change this template use File | Settings | File Templates.
 */
public class JuickWebCompatibleURLMessagesSource extends JuickMessagesSource  {

    String label;
    URLParser urlParser;
    private int lastRetrievedMID;

    public JuickWebCompatibleURLMessagesSource(String label, Context activity, String url) {
        super(activity);
        urlParser = new URLParser(url);
        this.label = label;
    }

    @Override
    public boolean supportsBackwardRefresh() {
        return true;
    }

    @Override
    public void getFirst(Utils.Notification notifications, Utils.Function<Void, ArrayList<JuickMessage>> cont) {
        urlParser.getArgsMap().remove("before");
        lastRetrievedMID = -1;
        fetchURLAndProcess(notifications, cont);
    }

    protected void fetchURLAndProcess(Utils.Notification notification, Utils.Function<Void, ArrayList<JuickMessage>> cont) {
        final String jsonStr = new JuickCompatibleURLMessagesSource(label, ctx, urlParser.getFullURL()).getJSONWithRetries(ctx, urlParser.getFullURL(), notification).getResult();
        Utils.DownloadErrorNotification errorNotification = null;
        if (notification instanceof Utils.DownloadErrorNotification)
            errorNotification = (Utils.DownloadErrorNotification)notification;
        if (jsonStr == null) {
            if (errorNotification != null)
                errorNotification.notifyDownloadError("Page download error.");
            cont.apply(new ArrayList<JuickMessage>());
        } else {
            ArrayList<JuickMessage> messages = parseWebPure(jsonStr);
            if (messages.size() > 0) {
                JuickMessage juickMessage = messages.get(messages.size() - 1);
                lastRetrievedMID = ((JuickMessageID)juickMessage.getMID()).getMid();
            } else {
                if (errorNotification != null)
                    errorNotification.notifyDownloadError("Page parse error.");
            }
            cont.apply(messages);
        }
    }

    enum State {
        WAIT_MESSAGE_START,
        WAIT_MSG_TEXT,
        IN_MESSAGE_TEXT
    }

    static Pattern messageStart = Pattern.compile("<li id=\"msg-(\\d+)\"");
    static Pattern messageTime = Pattern.compile("<div class=\"msg-ts\"><a href=\"(.*)\" title=\"(.*).0 GMT\">");
    static Pattern messageUser = Pattern.compile("<div class=\"msg-avatar\"><a href=\"/(.*)/\"><img src=\"//i.juick.com/a/(.*).png\" alt=");
    static Pattern messageHeader = Pattern.compile("<div class=\"msg-header\"><a href=");
    static Pattern nreplies = Pattern.compile("<div class=\"msg-comments\"><a href=\"(.*?)\">(\\d*) replies</a>");
    static Pattern onereply = Pattern.compile("<div class=\"msg-comments\"><a href=\"(.*?)\">1 reply</a>");
    static Pattern mediaImage = Pattern.compile("<div class=\"msg-media\"><a href=\"(.*?)\">");
    static String messageBodyStart = "<div class=\"msg-txt\">";
    static Pattern hyperlink = Pattern.compile("<a (.*?)href=\"(.*?)\"(.*?)>(.*?)</a>");
    static Pattern juick = Pattern.compile("http://juick.com/(\\d+)");
    static Pattern juick2 = Pattern.compile("http://juick.com/(\\w+)/(\\d+)");
    static Pattern hashNo = Pattern.compile("#(\\d*)");
    static Pattern blockQuote = Pattern.compile("<blockquote>(.*?)</blockquote>");
    static Pattern italic = Pattern.compile("<i>(.*?)</i>");
    static Pattern bold = Pattern.compile("<b>(.*?)</b>");
    static Pattern underline = Pattern.compile("<u>(.*?)</u>");
    static String messageBodyEnd = "</div>";
    static Pattern tags = Pattern.compile("<.*?>");
    static SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    static ArrayList<JuickMessage> badRetval = new ArrayList<JuickMessage>();

    public ArrayList<JuickMessage> parseWebPure(String jsonStr) {
        ArrayList<JuickMessage> retval = new ArrayList<JuickMessage>();
        String[] lines = jsonStr.split("\n");
        State state = State.WAIT_MESSAGE_START;
        JuickMessage message = null;
        //
        // YES! I CAN USE REGEXPS to parse HTML!
        //
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
        for (String line : lines) {
            switch (state) {
                case WAIT_MESSAGE_START:
                    Matcher matcher = messageStart.matcher(line);
                    if (matcher.find()) {
                        message = new JuickMessage();
                        message.User = new JuickUser();
                        message.setMID(new JuickMessageID(Integer.parseInt(matcher.group(1))));
                        message.messagesSource = this;
                        message.microBlogCode = JuickMicroBlog.CODE;
                        state = State.WAIT_MSG_TEXT;
                    }
                    Matcher nrepliesMatcher = nreplies.matcher(line);
                    if (nrepliesMatcher.find()) {
                        retval.get(retval.size()-1).replies = Integer.parseInt(nrepliesMatcher.group(2));
                    }
                    if (onereply.matcher(line).find()) {
                        retval.get(retval.size()-1).replies = 1;
                    }
                    Matcher mediaImageMatcher = mediaImage.matcher(line);
                    if (mediaImageMatcher.find()) {
                        String url = mediaImageMatcher.group(1);
                        if (url.startsWith("//")) {
                            url = "http:"+url;
                        }
                        retval.get(retval.size()-1).Text = url +"\n"+retval.get(retval.size()-1).Text;
                    }
                    continue;
                case WAIT_MSG_TEXT:
                    Matcher timeMatcher = messageTime.matcher(line);
                    if (timeMatcher.find()) {
                        try {
                            message.Timestamp = sdf.parse(timeMatcher.group(2));
                        } catch (ParseException e) {
                            return badRetval;
                        }
                    }
                    Matcher userMatcher = messageUser.matcher(line);
                    if (userMatcher.find()) {
                        try {
                            message.User.UName = userMatcher.group(1);
                            message.User.UID = Integer.parseInt(userMatcher.group(2));
                        } catch (NumberFormatException e) {
                            return badRetval;
                        }
                    }
                    if (messageHeader.matcher(line).find()) {
                        try {
                            message.tags = new Vector<String>();
                            int star = line.indexOf("*");
                            if (star != -1) {
                                line = line.substring(star);
                                line = tags.matcher(line).replaceAll("").trim();
                                String[] tagz = line.split(" ");
                                for (String s : tagz) {
                                    if (s.startsWith("*")) {
                                        message.tags.add(s.substring(1));
                                    }
                                }
                            }
                        } catch (NumberFormatException e) {
                            return badRetval;
                        }
                    }
                    if (line.contains(messageBodyStart)) {
                        message.Text = line.substring(line.indexOf(messageBodyStart)+messageBodyStart.length());
                        if (line.contains(messageBodyEnd)) {
                            retval.add(message);
                            state = State.WAIT_MESSAGE_START;   // quickly ended
                        } else {
                            state = State.IN_MESSAGE_TEXT;
                        }
                    }
                    break;
                case IN_MESSAGE_TEXT:
                    if (line.contains(messageBodyEnd)) {
                        message.Text += line.replace(messageBodyEnd,"");
                        retval.add(message);
                        state = State.WAIT_MESSAGE_START;
                    } else {
                        message.Text += line;
                    }
            }
        }
        for (JuickMessage juickMessage : retval) {
            juickMessage.Text = unwebMessageText(juickMessage.Text);
        }
        return retval;
    }

    public static String unwebMessageText(String text) {
        text = text.replace("<br/>","\n");
        text = text.replace("</div>","");
        while(true) {
            Matcher matcher = hyperlink.matcher(text);
            if (matcher.find()) {
                text = matcher.replaceFirst(matcher.group(2));
                continue;
            } else {
                break;
            }
        }
        text = unjuick(text);
        while(true) {
            Matcher matcher = blockQuote.matcher(text);
            if (matcher.find()) {
                text = matcher.replaceFirst("> " + matcher.group(1));
                continue;
            } else {
                break;
            }
        }
        while(true) {
            Matcher matcher = bold.matcher(text);
            if (matcher.find()) {
                text = matcher.replaceFirst("*" + matcher.group(1)+"*");
                continue;
            } else {
                break;
            }
        }
        while(true) {
            Matcher matcher = italic.matcher(text);
            if (matcher.find()) {
                text = matcher.replaceFirst("/" + matcher.group(1)+"/");
                continue;
            } else {
                break;
            }
        }
        while(true) {
            Matcher matcher = underline.matcher(text);
            if (matcher.find()) {
                text = matcher.replaceFirst("_" + matcher.group(1)+"_");
                continue;
            } else {
                break;
            }
        }
        text = text.replace("&gt;",">");
        text = text.replace("&lt;","<");
        text = text.replace("&mdash;","-");
        return text;
    }

    public static String unjuick(String text) {
        while(true) {
            Matcher matcher = juick.matcher(text);
            if (matcher.find()) {
                int end = matcher.end(1);
                Matcher hash = hashNo.matcher(text.substring(end));
                if (hash.find() && hash.start() == 0) {
                    text = text.substring(0, end) + hash.replaceFirst("/"+hash.group(1));
                    continue;
                }
                text = matcher.replaceFirst("#"+matcher.group(1));
                continue;
            } else {
                break;
            }
        }
        while(true) {
            Matcher matcher = juick2.matcher(text);
            if (matcher.find()) {
                int end = matcher.end(2);
                Matcher hash = hashNo.matcher(text.substring(end));
                if (hash.find() && hash.start() == 0) {
                    text = text.substring(0, end) + hash.replaceFirst("/"+hash.group(1));
                    continue;
                }
                text = matcher.replaceFirst("#"+matcher.group(2));
                continue;
            } else {
                break;
            }
        }
        return text;
    }

    @Override
    public void getNext(Utils.Notification notifications, Utils.Function<Void, ArrayList<JuickMessage>> cont) {
        if (lastRetrievedMID > 0) {
            putArg("before",""+lastRetrievedMID);
        }
        fetchURLAndProcess(notifications, cont);
    }

    public void putArg(String name, String value) {
        urlParser.getArgsMap().put(name, value);
    }

    @Override
    public void getChildren(MessageID mid, Utils.Notification notifications, Utils.Function<Void, ArrayList<JuickMessage>> cont) {
        new JuickCompatibleURLMessagesSource(ctx).getChildren(mid, notifications, cont);
    }

    @Override
    public CharSequence getTitle() {
        return label;
    }

}
