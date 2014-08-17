package com.juick.android.ja;

import android.content.Context;
import com.juick.android.JuickMessagesAdapter;
import com.juick.android.MicroBlog;
import com.juickadvanced.RESTResponse;
import com.juick.android.Utils;
import com.juick.android.juick.MessagesSource;
import com.juickadvanced.data.MessageID;
import com.juickadvanced.data.juick.JuickMessage;
import com.juickadvanced.data.juick.JuickUser;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.Date;
import java.util.Vector;

/**
 * Created by san on 6/1/14.
 */
public class GlavSuForumMessagesSource extends MessagesSource {

    public static class GlavSuMessageID extends MessageID {

        String id;
        public static final String CODE = "glavsu";

        public GlavSuMessageID(String id) {
            this.id = id;
        }

        @Override
        public String toString() {
            return CODE+"-"+id;
        }

        @Override
        public String toDisplayString() {
            return "GS"+id;
        }

        @Override
        public String getMicroBlogCode() {
            return "glavsu";
        }
    }

    String nextURL = null;

    public GlavSuForumMessagesSource(Context ctx) {
        super(ctx, "glavsu.ukr");
    }

    @Override
    public boolean supportsBackwardRefresh() {
        return true;
    }

    @Override
    public void getFirst(Utils.Notification notifications, Utils.Function<Void, ArrayList<JuickMessage>> cont) {
        nextURL = null;
        getNext(notifications, cont);
    }

    private ArrayList<JuickMessage> parseGlavSu(String html) {
        Document doc = Jsoup.parse(html);
        Elements as = doc.select("a[class=blueButton]");
        for(int i=0; i<as.size(); i++) {
            String inside = as.get(i).ownText();
            if (inside.contains("Предыдущ")) {
                nextURL = as.get(i).attr("href"); break;
            }
        }
        ArrayList<JuickMessage> retval = new ArrayList<JuickMessage>();
        Elements items = doc.select("div[id=forumMessageList] > table");
        for(int i=items.size()-1; i>=0; i--) {
            Element msg = items.get(i);
            try {
                String id = msg.attr("id");
                if (id.startsWith("forumMessageListMessage")) {
                    id = id.substring("forumMessageListMessage".length());
                } else {
                    continue;
                }
                Element title = msg.select("span[id=forumMessageListMessageTitle"+id+"]").get(0);
                Element date = title.previousElementSibling();
                String dateText = date.text();
                String username = msg.select("a[class=fwBold]").get(0).ownText();
                Element content = msg.select("*[id=forumMessageListMessageContent"+id+"]").get(0);
                Elements iframes = content.select("iframe");
                for(int w=0; w<iframes.size(); w++) {
                    iframes.get(w).remove();
                }
                Elements images = content.select("img");
                for(int w=0; w<images.size(); w++) {
                    Element img = images.get(w);
                    String src = img.attr("src");
                    if (src.startsWith("/")) {
                        src = "http://glav.su"+src;
                    } else {
                        src = JuickMessagesAdapter.setupProxyForURL(getContext(), src, null);
                    }
                    img.attr("src", src);
                }
                JuickMessage jm = new JuickMessage();
                jm.Timestamp = new Date();
                jm.tags = new Vector<String>();
                jm.microBlogCode = GlavSuMicroblog.INSTANCE.getCode();
                jm.Text = content.html();
                jm.User = new JuickUser();
                jm.User.UName = username;
                jm.setMID(new GlavSuMessageID(id));
                retval.add(jm);
            } catch (Exception ex) {
                // shit happens
                System.out.println(ex);
            }
        }
        return retval;
    }

    @Override
    public void getNext(Utils.Notification notifications, Utils.Function<Void, ArrayList<JuickMessage>> cont) {
        RESTResponse jsonWithRetries = Utils.getJSONWithRetries(getContext(), nextURL != null ? nextURL : "http://glav.su/forum/4-politics/38-ukraine-russia-relationships/offset/99000000/", notifications);
        if (jsonWithRetries.getErrorText() == null) {
            String result = jsonWithRetries.getResult();
            cont.apply(parseGlavSu(result));
        }
    }

    @Override
    public void getChildren(MessageID mid, Utils.Notification notifications, Utils.Function<Void, ArrayList<JuickMessage>> cont) {
        cont.apply(new ArrayList<JuickMessage>());
    }

    @Override
    public CharSequence getTitle() {
        return "GlavSu - Ukraine";
    }

    @Override
    public MicroBlog getMicroBlog() {
        return GlavSuMicroblog.INSTANCE;
    }
}
