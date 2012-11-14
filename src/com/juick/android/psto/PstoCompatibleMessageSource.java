package com.juick.android.psto;

import android.content.Context;
import com.juick.android.MainActivity;
import com.juick.android.MicroBlog;
import com.juick.android.URLParser;
import com.juick.android.Utils;
import com.juick.android.api.JuickMessage;
import com.juick.android.api.JuickUser;
import com.juick.android.api.MessageID;
import com.juick.android.juick.JuickWebCompatibleURLMessagesSource;
import com.juick.android.juick.MessagesSource;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 11/9/12
 * Time: 12:59 AM
 * To change this template use File | Settings | File Templates.
 */
public class PstoCompatibleMessageSource extends MessagesSource {

    URLParser urlParser;
    String title;
    int page;

    public PstoCompatibleMessageSource(Context ctx, String title, String path) {
        super(ctx);
        this.title = title;
        urlParser = new URLParser(path);
        PstoAuthorizer.skipAskPassword = false;
    }

    @Override
    public void getChildren(MessageID mid, Utils.Notification notifications, Utils.Function<Void, ArrayList<JuickMessage>> cont) {
        String midString = ((PstoMessageID)mid).getId();
        String user = ((PstoMessageID)mid).user;
        String url = user != null && user.length() > 0 ? "http://" + user.toLowerCase() + ".psto.net/" + midString : "http://psto.net/" + midString;
        Utils.RESTResponse jsonWithRetries = getJSONWithRetries(ctx, url, notifications);
        if (jsonWithRetries.errorText != null) {
            cont.apply(badRetval);
        } else {
            String result = jsonWithRetries.result;
            ArrayList<JuickMessage> messages = parseWebMessageListPure(result, ParseMode.PARSE_THREAD_FIRST);
            if (messages.size() == 1) {
                PstoMessage msg = (PstoMessage)messages.get(0);
                msg.setMID(mid);
                int commentsStart = result.indexOf("<div class=\"comments\">");
                if (commentsStart > 0) {
                    result = result.substring(commentsStart + 10);
                    ArrayList<JuickMessage> comments = parseWebMessageListPure(result, ParseMode.PARSE_THREAD_COMMENTS);
                    HashMap<Integer, JuickMessage> replies = new HashMap<Integer, JuickMessage>();
                    for (JuickMessage comment : comments) {
                        replies.put(comment.getRID(), comment);
                    }
                    for (JuickMessage comment : comments) {
                        comment.setMID(mid);
                        // filling in "@User" into replies
                        if (comment.getReplyTo() != 0) {
                            JuickMessage juickMessage = replies.get(comment.getReplyTo());
                            if (juickMessage != null) {
                                comment.Text = "@"+juickMessage.User.UName+" "+comment.Text;
                            }
                        }
                    }
                    messages.addAll(comments);
                }
                cont.apply(messages);
            } else {
                cont.apply(badRetval);
            }
        }

    }

    @Override
    public boolean supportsBackwardRefresh() {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }


    HashSet<String> loadedMessages = new HashSet<String>();

    @Override
    public void getFirst(Utils.Notification notifications, Utils.Function<Void, ArrayList<JuickMessage>> cont) {
        page = 0;
        loadedMessages.clear();
        fetchURLAndProcess(notifications, cont);
    }

    protected void fetchURLAndProcess(Utils.Notification notification, Utils.Function<Void, ArrayList<JuickMessage>> cont) {
        // put in page
        String pathPart = urlParser.getPathPart();
        int ix = pathPart.lastIndexOf("/");
        if (ix != -1) {
            try {
                Integer.parseInt(pathPart.substring(ix+1));
                urlParser.setPath(pathPart.substring(0, ix));  // replace page
            } catch (NumberFormatException e) {
                // not a number
            }
        } else {
            // have no number
        }
        if (page != 0) {
            urlParser.setPath(urlParser.getPathPart()+"/"+page);
        }
        final String jsonStr = getJSONWithRetries(ctx, urlParser.getFullURL(), notification).getResult();
        ArrayList<JuickMessage> messages = parseWebMessageListPure(jsonStr, ParseMode.PARSE_MESSAGE_LIST);
        if (messages.size() > 0) {
            for (Iterator<JuickMessage> iterator = messages.iterator(); iterator.hasNext(); ) {
                JuickMessage message = iterator.next();
                if (!loadedMessages.add(message.getMID().toString())) {
                    iterator.remove();
                }
            }
            if (loadedMessages.size() == 0) {
                page++;
                fetchURLAndProcess(notification, cont);
                return;
            }
        }
        cont.apply(messages);
    }

    @Override
    public void getNext(Utils.Notification notifications, Utils.Function<Void, ArrayList<JuickMessage>> cont) {
        page++;
        fetchURLAndProcess(notifications, cont);
    }

    @Override
    public CharSequence getTitle() {
        return title;
    }

    @Override
    public MicroBlog getMicroBlog() {
        return MainActivity.getMicroBlog(PstoMicroBlog.CODE);
    }

    public Utils.RESTResponse getJSONWithRetries(Context ctx, String url, Utils.Notification notifications) {
        return Utils.getJSONWithRetries(ctx,url, notifications);
    }


    static Pattern messageStart = Pattern.compile("<div class=\"post\">|<div class=\"post private\">");
    static Pattern commentStart = Pattern.compile("div class=\"post\" id=\"comment-(.*?)\" ");
    static Pattern commentNumber = Pattern.compile("data-comment-id=\"(.*?)\"");
    static Pattern commentReplyto = Pattern.compile("data-to-comment-id=\"(.*?)\"");
    static Pattern messageTime = Pattern.compile("<span class=\"info\">(.*?)</span>");
    static Pattern messageUser = Pattern.compile("<a class=\"name\" href=\"(.*?)/\" title=\"(.*?)\">(.*?)</a>");
    static Pattern answer = Pattern.compile("<a class=\"answer\" href=\"#\" data-to=\"(.*)\" data-to-comment=\"(.*?)\"");
    static Pattern messageTag = Pattern.compile("<a href=\"http://psto.net/tag\\?tag=(.*?)\">(.*)</a>");
    static Pattern messageID = Pattern.compile("<div class=\"post-id\"><a href=\"(.*)\">#(.*)</a></div>");
    static Pattern nreplies = Pattern.compile("title=\"Add comment\"><img src=\"/img/reply.png\" alt=\"re\"/>(.*)</a>");
    static SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm");
    static {
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
    }
    static ArrayList<JuickMessage> badRetval = new ArrayList<JuickMessage>();

    enum ParseMode {
        PARSE_MESSAGE_LIST,
        PARSE_THREAD_FIRST,
        PARSE_THREAD_COMMENTS
    }

    public ArrayList<JuickMessage> parseWebMessageListPure(String jsonStr, ParseMode parseMode) {
        ArrayList<JuickMessage> retval = new ArrayList<JuickMessage>();
        String[] lines = jsonStr.split("\n");
        JuickMessage message = null;
        //
        // YES! I CAN USE REGEXPS to parse HTML!
        //
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            line = line.trim();
            Matcher matcher = parseMode == ParseMode.PARSE_THREAD_COMMENTS ? commentStart.matcher(line) : messageStart.matcher(line);
            if (matcher.find()) {
                if (message != null) {
                    retval.add(message);
                }
                message = new PstoMessage();
                message.User = new JuickUser();
                message.messagesSource = this;
                message.microBlogCode = PstoMicroBlog.CODE;
                message.privateMessage = line.contains("private");
                if (parseMode == ParseMode.PARSE_THREAD_COMMENTS) {
                    matcher = commentNumber.matcher(line);
                    if (matcher.find()) {
                        message.setRID(Integer.parseInt(matcher.group(1)));
                    }
                    matcher = commentReplyto.matcher(line);
                    if (matcher.find()) {
                        message.setReplyTo(Integer.parseInt(matcher.group(1)));
                    }
                }
                continue;
            }
            if (message == null) continue;
            if (line.contains("class=\"pager\"")) break;    // end of messages
            if (line.contains("<div class=\"comments\">")) break;    // end of messages
            Matcher timeMatcher = messageTime.matcher(line);
            if (timeMatcher.find()) {
                try {
                    String dateOrTime = timeMatcher.group(1).trim();
                    String[] split = dateOrTime.split(" ");
                    // 10.09.2012 06:53 Gajim_
                    if (split[0].length() == 5 && split[0].indexOf(':') == 2) { // time first
                        dateOrTime = split[0];
                    } else {
                        dateOrTime = split[0] + " " + split[1];    // date + time
                    }
                    if (dateOrTime.length() < 6) {      // time only
                        // 14:07
                        split = dateOrTime.split(":");
                        GregorianCalendar gregorianCalendar = new GregorianCalendar();
                        gregorianCalendar.setTimeZone(TimeZone.getTimeZone("GMT"));
                        gregorianCalendar.set(Calendar.HOUR_OF_DAY, Integer.parseInt(split[0]));
                        gregorianCalendar.set(Calendar.MINUTE, Integer.parseInt(split[1]));
                        message.Timestamp = gregorianCalendar.getTime();
                    } else {
                        // 09.11.2012 12:25
                        message.Timestamp = sdf.parse(dateOrTime);
                    }
                    continue;
                } catch (Exception e) {
                    return badRetval;
                }
            }
            if (line.startsWith("<p>")) {
                if (parseMode == ParseMode.PARSE_THREAD_COMMENTS) {
                    // multiple lines here
                    StringBuilder sb = new StringBuilder(line.trim());
                    while(true) {
                        i++;
                        line = lines[i];
                        sb.append(line.trim());
                        if (line.indexOf("</p>") != -1) {
                            break;
                        }
                    }
                    line = sb.toString();
                }
                if (!line.endsWith("</p>")) {
                    return badRetval;
                }
                line = line.substring(3, line.length() - 4);
                message.Text = JuickWebCompatibleURLMessagesSource.unwebMessageText(line);
                if (message.privateMessage) {
                    message.Text = "[private] " +message.Text;
                }
                continue;
            }
            Matcher tagMatcher = messageTag.matcher(line);
            if (tagMatcher.find()) {
                message.tags.add(tagMatcher.group(2));
                continue;
            }
            Matcher nameMatcher = messageUser.matcher(line);
            if (nameMatcher.find()) {
                message.User.UName = nameMatcher.group(3);
                if (message.getMID() != null) {
                    ((PstoMessageID) message.getMID()).user = message.User.UName;
                }
                continue;
            }
            Matcher answerMatcher = answer.matcher(line);
            if (answerMatcher.find() && answerMatcher.group(1).length() > 0) {
                message.User.UID = Integer.parseInt(answerMatcher.group(1));
                continue;
            }
            Matcher postidMatcher = messageID.matcher(line);
            if (postidMatcher.find()) {
                PstoMessageID mid = new PstoMessageID("",postidMatcher.group(2));
                message.setMID(mid);
                if (message.User.UName != null) {
                    mid.user = message.User.UName;
                }
                continue;
            }
            Matcher nrepliesMatcher = nreplies.matcher(line);
            if (nrepliesMatcher.find()) {
                String group = nrepliesMatcher.group(1);
                String[] split = group.split(" ");
                if (split[0].equals("add")) {
                    // zero
                } else {
                    try {
                        message.replies = Integer.parseInt(split[0]);
                    } catch (NumberFormatException e) {
                        // bad luck
                    }
                }
                continue;
            }
        }
        if (message != null) {
            retval.add(message);
        }
        return retval;
    }



}
