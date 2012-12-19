package com.juick.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.*;
import android.widget.*;
import com.juickadvanced.data.juick.JuickMessage;
import com.juickadvanced.data.juick.JuickUser;
import com.juickadvanced.data.MessageID;
import com.juick.android.juick.JuickComAuthorizer;
import com.juick.android.juick.JuickCompatibleURLMessagesSource;
import com.juickadvanced.data.juick.JuickMessageID;
import com.juickadvanced.R;
import org.acra.ACRA;

import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 8/8/12
 * Time: 2:18 PM
 * To change this template use File | Settings | File Templates.
 */
public class XMPPIncomingMessagesActivity extends Activity implements XMPPMessageReceiver.MessageReceiverListener{

    private boolean resumed;

    class Item {
        ArrayList<XMPPService.IncomingMessage> messages;
        long lastTime;
        int lastRid;

        Item(XMPPService.IncomingMessage message) {
            this();
            messages.add(message);
        }
        Item() {
            messages = new ArrayList<XMPPService.IncomingMessage>();
        }
    }

    public static boolean editMode;

    class Header {
        String label;
        Runnable action;

        Header(String label, Runnable action) {
            this.action = action;
            this.label = label;
        }
    }

    Utils.ServiceGetter<XMPPService> xmppServiceServiceGetter;
    ArrayList<Object> displayItems;
    Handler handler;


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        overridePendingTransition(R.anim.enter_slide_to_bottom, android.R.anim.fade_out);
        handler = new Handler();
        setContentView(R.layout.incoming_messages);
        findViewById(R.id.gotoMain).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(XMPPIncomingMessagesActivity.this, MainActivity.class));
            }
        });
        xmppServiceServiceGetter = new Utils.ServiceGetter<XMPPService>(this, XMPPService.class);
        final MyListView lv = (MyListView)findViewById(R.id.list);
        lv.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                new MessageMenu(XMPPIncomingMessagesActivity.this, null, null, null) {
                    XMPPService.IncomingMessage incomingMessage;
                    View view;
                    @Override
                    public boolean onItemLongClick(AdapterView parent, final View view, int position, long id) {
                        Object o = displayItems.get(position);
                        if (o instanceof Item) {
                            Item item = (Item) o;
                            this.view = view;
                            incomingMessage = item.messages.get(0);
                            if (incomingMessage instanceof XMPPService.JuickIncomingMessage) {
                                final XMPPService.JuickIncomingMessage jim = (XMPPService.JuickIncomingMessage)incomingMessage;
                                final String fromUser = jim.getFrom();
                                final MessageID thread = jim.getMID();
                                JuickMessage msg = new JuickMessage();
                                msg.microBlogCode = JuickMessageID.CODE;
                                msg.setMID(thread);
                                msg.Text = jim.getBody();
                                msg.setRID(jim.getRID());
                                msg.User = new JuickUser();
                                msg.User.UID = 0;
                                msg.User.UName = fromUser;
//                                if (msg.Timestamp == null)
//                                    msg.Timestamp = new Date();
//                                msg.parsedText = JuickMessagesAdapter.formatMessageText(XMPPIncomingMessagesActivity.this, msg, false);
                                if (msg.User.UName.startsWith("@"))
                                    msg.User.UName = msg.User.UName.substring(1);
                                listSelectedItem = msg;

                                menuActions.add(0, new RunnableItem(activity.getResources().getString(R.string.OpenThread)) {
                                    @Override
                                    public void run() {
                                        clickedOnMessage(incomingMessage);
                                    }

                                });
                                if (incomingMessage instanceof XMPPService.JuickSubscriptionIncomingMessage) {
                                    menuActions.add(1, new RunnableItem(activity.getResources().getString(R.string.ExpandMessage) + " " + fromUser) {
                                        @Override
                                        public void run() {
                                            expandMessage(view);
                                        }

                                    });
                                }
                                collectURLs(msg.Text);
                                runActions();
                            }
                        }
                        return false;
                    }

                    @Override
                    protected void completeInitDialogMode(final AlertDialog alertDialog, final View dialogView) {
                        View openMessage = dialogView.findViewById(R.id.open_message);
                        final View expandMessage = dialogView.findViewById(R.id.expand_message);
                        openMessage.setVisibility(View.VISIBLE);
                        expandMessage.setVisibility(View.VISIBLE);
                        openMessage.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                alertDialog.dismiss();
                                clickedOnMessage(incomingMessage);
                            }
                        });
                        expandMessage.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                alertDialog.dismiss();
                                expandMessage(view);
                            }
                        });
                        View saveMessage = dialogView.findViewById(R.id.save_message);
                        View translateMessage = dialogView.findViewById(R.id.translate_message);
                        View shareMessage = dialogView.findViewById(R.id.share_message);
                        View userBlog = dialogView.findViewById(R.id.user_blog);
                        saveMessage.setEnabled(false);      // because in this mode doing smth with message body is not implemented properly
                        translateMessage.setEnabled(false);
                        shareMessage.setEnabled(false);
                        if (incomingMessage instanceof XMPPService.JuickThreadIncomingMessage) {
                            if (userBlog != null)
                                userBlog.setEnabled(false);
                        }

                    }
                }.onItemLongClick(parent, view, position, id);
                return true;
            }
        });
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                Object o = displayItems.get(i);
                if (lv.getMaxActiveFingers() > 1) {
                    if (o instanceof Item) {
                        Item item = (Item) o;
                        final XMPPService.IncomingMessage incomingMessage = item.messages.get(0);
                        if (incomingMessage instanceof XMPPService.JuickSubscriptionIncomingMessage) {
                            expandMessage(view);
                        }
                    }

                    //
                } else {
                    if (o instanceof Item) {
                        Item item = (Item) o;
                        final XMPPService.IncomingMessage incomingMessage = item.messages.get(0);
                        clickedOnMessage(incomingMessage);
                    }
                }
            }
        });
        MainActivity.restyleChildrenOrWidget(getWindow().getDecorView());
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        listeningAll = sp.getBoolean("extxmpp.local.listeningAll", false);
    }

    private void clickedOnMessage(final XMPPService.IncomingMessage incomingMessage) {
        if (incomingMessage instanceof XMPPService.JabberIncomingMessage) {
            xmppServiceServiceGetter.getService(new Utils.ServiceGetter.Receiver<XMPPService>() {
                @Override
                public void withService(XMPPService service) {
                    service.removeMessage(incomingMessage);
                    refreshList();
                }
            });
            return;
        }
        if (incomingMessage instanceof XMPPService.JuickIncomingMessage) {
            Intent intent = new Intent(XMPPIncomingMessagesActivity.this, ThreadActivity.class);
            if (incomingMessage instanceof XMPPService.JuickThreadIncomingMessage) {
                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
                if (!sp.getBoolean("enableMessageDB", false)) {
                    intent.putExtra("scrollToBottom", true);
                } else {
                    // for enabled messagedb, it will be placed properly automatically
                }
            }
            intent.putExtra("mid", ((XMPPService.JuickIncomingMessage) incomingMessage).getMID());
            intent.putExtra("messagesSource", new JuickCompatibleURLMessagesSource(this));
            startActivity(intent);
            return;
        }
    }

    private void expandMessage(View view) {
        TextView preview = (TextView)view.findViewById(R.id.preview);
        preview.setMaxLines(9999);
    }

    @Override
    protected void onResume() {
        XMPPMessageReceiver.listeners.add(this);
        super.onResume();    //To change body of overridden methods use File | Settings | File Templates.
        refreshList();
        launchMainMessagesEnabler();
        resumed = true;
    }

    private void launchMainMessagesEnabler() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                findViewById(R.id.gotoMain).setVisibility(MainActivity.nActiveMainActivities == 0 ? View.VISIBLE : View.GONE);
                if (resumed)
                    handler.postDelayed(this, 1000);
            }
        });
    }
    @Override
    protected void onRestart() {
        super.onRestart();
    }

    @Override
    protected void onPause() {
        XMPPMessageReceiver.listeners.remove(this);
        super.onPause();    //To change body of overridden methods use File | Settings | File Templates.
        resumed = false;
    }

    @Override
    public boolean onMessageReceived(XMPPService.IncomingMessage msg) {
        Log.i("XMPPIncomingMessages","message received");
        refreshList();
        return false;
    }

    void refreshList() {
        final long serviceObtainer = System.currentTimeMillis();
        xmppServiceServiceGetter.getService(new Utils.ServiceGetter.Receiver<XMPPService>() {
            @Override
            public void withService(XMPPService service) {
                long l = System.currentTimeMillis() - serviceObtainer;
                Log.i("XMPPIncomingMessages","obtain service time="+l);
                ArrayList<XMPPService.IncomingMessage> incomingMessages;
                synchronized (service.incomingMessages) {
                    incomingMessages = new ArrayList<XMPPService.IncomingMessage>(service.incomingMessages);
                    service.maybeCancelNotification();
                }
                Log.i("XMPPIncomingMessages","begin refresh all");
                refreshListWithAllMessages(service, incomingMessages);
            }

            @Override
            public void withoutService() {
                super.withoutService();
                Toast.makeText(XMPPIncomingMessagesActivity.this, "Unable to connect to XMPP service", Toast.LENGTH_LONG).show();
                refreshListWithAllMessages(null, new ArrayList<XMPPService.IncomingMessage>());
            }
        });
    }

    MyListAdapter adapter;

    private void refreshListWithAllMessages(XMPPService service, ArrayList<XMPPService.IncomingMessage> allMessages) {
        long l = System.currentTimeMillis();
        ArrayList<Item> privateMessages = new ArrayList<Item>();
        ArrayList<Item> jabberMessages = new ArrayList<Item>();
        ArrayList<Item> subscriptions = new ArrayList<Item>();
        HashMap<JuickMessageID, Item> threadMessages = new HashMap<JuickMessageID, Item>();

        int nCommentsTotal = 0;

        for (XMPPService.IncomingMessage message : allMessages) {
            if (message instanceof XMPPService.JuickPrivateIncomingMessage) {
                privateMessages.add(new Item(message));
            }
            if (message instanceof XMPPService.JuickThreadIncomingMessage) {
                XMPPService.JuickThreadIncomingMessage tim = (XMPPService.JuickThreadIncomingMessage)message;
                Item item = threadMessages.get(tim.getMID());
                if (item == null) {
                    item = new Item();
                    threadMessages.put(tim.getMID(), item);
                }
                item.messages.add(tim);
                item.lastTime = Math.max(item.lastTime, tim.datetime.getTime());
                item.lastRid = Math.max(item.lastRid, tim.getRID());
                nCommentsTotal++;
            }
            if (message instanceof XMPPService.JabberIncomingMessage) {
                jabberMessages.add(new Item(message));
            }
            if (message instanceof XMPPService.JuickSubscriptionIncomingMessage) {
                subscriptions.add(new Item(message));
            }
        }

        l = System.currentTimeMillis() - l;
        Log.i("XMPPIncomingMessages","group by time="+l);
        l = System.currentTimeMillis();
        displayItems = new ArrayList<Object>();
        if (threadMessages.size() > 0) {
            displayItems.add(new Header(getString(R.string.ThreadComments) + nCommentsTotal+" " + getString(R.string.Total), new Runnable() {
                @Override
                public void run() {
                    deleteMessages(XMPPService.JuickThreadIncomingMessage.class);
                }
            }));
            ArrayList<Item> sortee = new ArrayList<Item>(threadMessages.values());
            Collections.sort(sortee, new Comparator<Item>() {
                @Override
                public int compare(Item item, Item item1) {
                    return (int)Math.signum(item1.lastTime - item.lastTime); // sort by last comment, descending
                }
            });
            displayItems.addAll(sortee);
        }
        if (privateMessages.size() > 0) {
            displayItems.add(new Header(getString(R.string.PrivateMessages)+privateMessages.size(), new Runnable() {
                @Override
                public void run() {
                    deleteMessages(XMPPService.JuickPrivateIncomingMessage.class);
                }
            }));
            for (int i = privateMessages.size() - 1; i >= 0; i--) {
                displayItems.add(privateMessages.get(i));
            }
        }
        if (subscriptions.size() > 0) {
            displayItems.add(new Header(getString(R.string.SubscriptionsMessages)+subscriptions.size(), new Runnable() {
                @Override
                public void run() {
                    deleteMessages(XMPPService.JuickSubscriptionIncomingMessage.class);
                }
            }));
            for (int i = subscriptions.size() - 1; i >= 0; i--) {
                displayItems.add(subscriptions.get(i));
            }
        }
        if (jabberMessages.size() > 0) {
            displayItems.add(new Header(getString(R.string.OtherMessages)+jabberMessages.size(), new Runnable() {
                @Override
                public void run() {
                    deleteMessages(XMPPService.JabberIncomingMessage.class);
                }
            }));
            for (int i = jabberMessages.size() - 1; i >= 0; i--) {
                displayItems.add(jabberMessages.get(i));
            }
        }
        l = System.currentTimeMillis() - l;
        Log.i("XMPPIncomingMessages","display items time="+l);
        l = System.currentTimeMillis();
        ListView lv = (ListView)findViewById(R.id.list);
        if (adapter == null) {
            adapter = new MyListAdapter();
            lv.setAdapter(adapter);
        } else{
            adapter.notifyDataSetChanged();
        }
        l = System.currentTimeMillis() - l;
        Log.i("XMPPIncomingMessages","update adapter time="+l);
        //MainActivity.restyleChildrenOrWidget(getWindow().getDecorView());
    }

    private int compareJuickMessages(XMPPService.JuickIncomingMessage im, XMPPService.JuickIncomingMessage im1) {
        return im.getMID().getMid() -  im1.getMID().getMid();
    }

    private void deleteMessages(final Class incomingMessageClass) {
        xmppServiceServiceGetter.getService(new Utils.ServiceGetter.Receiver<XMPPService>() {
            @Override
            public void withService(XMPPService service) {
                service.removeMessages(incomingMessageClass);
                refreshList();
            }
        });
    }

    private class MyListAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return displayItems.size();
        }

        @Override
        public Object getItem(int i) {
            return displayItems.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        @Override
        public boolean isEnabled(int position) {
            return displayItems.get(position) instanceof Item;
        }

        public ColorsTheme.ColorTheme colorTheme = JuickMessagesAdapter.getColorTheme(XMPPIncomingMessagesActivity.this);

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            long l = System.currentTimeMillis();
            try {
                Object item = displayItems.get(i);
                if (item instanceof Header) {
                    view = getLayoutInflater().inflate(R.layout.incoming_messages_section, null);
                    TextView tv = (TextView)view.findViewById(R.id.value);
                    TextView btn = (TextView)view.findViewById(R.id.button);
                    final Header header = (Header) item;
                    tv.setText(header.label);
                    btn.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            header.action.run();
                            refreshList();
                        }
                    });
                    return view;
                }
                Item messagesItem = (Item)item;
                XMPPService.IncomingMessage message = messagesItem.messages.get(0);
                if (message instanceof XMPPService.JuickPrivateIncomingMessage) {
                    XMPPService.JuickPrivateIncomingMessage privmsg = (XMPPService.JuickPrivateIncomingMessage)message;
                    view = getLayoutInflater().inflate(R.layout.incoming_messages_private, null);
                    makePressable(view);
                    TextView fromTags = (TextView)view.findViewById(R.id.from_tags);
                    TextView preview = (TextView)view.findViewById(R.id.preview);
                    fromTags.setText(privmsg.getFrom());
                    preview.setText(privmsg.getBody());
                    enableInternalButtons(view, message);
                    MainActivity.restyleChildrenOrWidget(view);
                    return view;
                }
                if (message instanceof XMPPService.JuickThreadIncomingMessage) {
                    HashMap<String,HashSet<String>> counts = new HashMap<String, HashSet<String>>();
                    int totalCount = 0;
                    MessageID topicMessageId = null;
                    int toYouCount = 0;
                    for (XMPPService.IncomingMessage incomingMessage : messagesItem.messages) {
                        XMPPService.JuickThreadIncomingMessage commentMessage = (XMPPService.JuickThreadIncomingMessage)incomingMessage;
                        String from = commentMessage.getFrom();
                        HashSet oldCount = counts.get(from);
                        if (oldCount == null) {
                            oldCount = new HashSet();
                            counts.put(from, oldCount);
                        }
                        oldCount.add(commentMessage.messageNoPlain);
                        String nickScanArea = commentMessage.getBody().toString().toLowerCase()+" ";
                        String accountName = JuickComAuthorizer.getJuickAccountName(XMPPIncomingMessagesActivity.this.getApplicationContext()).toLowerCase();
                        int scan = 0;
                        while(true) {
                            int myNick = nickScanArea.indexOf("@" + accountName, scan);
                            if (myNick != -1) {
                                if (!JuickMessagesAdapter.isNickPart(nickScanArea.charAt(myNick + accountName.length() + 1))) {
                                    toYouCount++;
                                    break;
                                }
                                scan = myNick + 1;
                            } else {
                                break;
                            }
                        }
                        if (topicMessageId == null) {
                            topicMessageId = commentMessage.getMID();
                        }
                    }
                    SpannableStringBuilder sb = new SpannableStringBuilder();
                    for (Map.Entry<String, HashSet<String>> stringIntegerEntry : counts.entrySet()) {
                        Integer commentCount = stringIntegerEntry.getValue().size();
                        totalCount+=commentCount;
                        sb.append(stringIntegerEntry.getKey());
                        if (commentCount != 1) {
                            sb.append("("+commentCount+")");
                        }
                        sb.append(" ");
                    }
                    view = getLayoutInflater().inflate(R.layout.incoming_messages_threads, null);
                    makePressable(view);
                    XMPPService.JuickThreadIncomingMessage commentMessage = (XMPPService.JuickThreadIncomingMessage)messagesItem.messages.get(0);
                    XMPPService.JuickIncomingMessage originalMessage = commentMessage.getOriginalMessage();
                    TextView fromTags = (TextView)view.findViewById(R.id.from_tags);
                    SpannableStringBuilder ssb = new SpannableStringBuilder();
                    TextView preview = (TextView)view.findViewById(R.id.preview);

                    if (originalMessage != null) {
                        ssb.append(originalMessage.getFrom()+" ");
                        int off = ssb.length();
                        ArrayList<String> tags = originalMessage.getTags();
                        for (int i1 = 0; i1 < tags.size(); i1++) {
                            String tag = tags.get(i1);
                            if (tag.length() == 0) continue;;
                            if (tag.startsWith("*")) tag = tag.substring(1);
                            ssb.append("*");
                            ssb.append(tag);
                            if (i1 != tags.size()-1)
                                ssb.append(" ");
                        }
                        ssb.setSpan(new ForegroundColorSpan(colorTheme.getColor(ColorsTheme.ColorKey.TAGS, 0xFF0000CC)), off, ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        int ix = ssb.length();
                        ssb.append(" - " + toRelaviteDate(((Item) item).lastTime, russian));
                        ssb.setSpan(new ForegroundColorSpan(0xFF808080), ix, ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        fromTags.setText(ssb);
                    } else {
                        fromTags.setText("");
                    }
                    String originalBody = originalMessage != null && originalMessage.getBody().length() > 0 ? originalMessage.getBody().toString() : "[ loading ... ]";
                    if (originalBody.startsWith("http://i.juick.com")) {
                        int ix = originalBody.indexOf(".jpg");
                        if (ix != -1) {
                            originalBody = "[img] "+ originalBody.substring(ix+5);
                        }
                    }
                    preview.setText(originalBody);

                    TextView commentCounts = (TextView)view.findViewById(R.id.comment_counts);
                    String insertString;
                    if (totalCount == 1) {
                        insertString = "1 comment ";
                    } else {
                        insertString = totalCount + " comments ";
                    }
                    sb.insert(0, insertString);
                    sb.setSpan(new ForegroundColorSpan(0xFF008000), 0, insertString.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    int oldComments = ((Item) item).lastRid - totalCount;
                    String moreInsert = "";
                    if (oldComments != 0) {
                        moreInsert += oldComments + "+";
                        sb.insert(0, moreInsert);
                    }
                    int offset = insertString.length()+moreInsert.length();
                    if (toYouCount != 0) {
                        String toYou = " ("+toYouCount + " to you)  ";
                        sb.insert(offset, toYou);
                        sb.setSpan(new BackgroundColorSpan(colorTheme.getColor(ColorsTheme.ColorKey.USERNAME_ME, 0xFF938e00)), offset, offset + toYou.length()-1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        offset += toYou.length();
                    }
                    sb.insert(offset, "from: ");
                    offset += 6;


                    // paint all grouped nicks
                    sb.setSpan(new ForegroundColorSpan(0xFFC8934E), offset, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    commentCounts.setText(sb);
                    enableInternalButtons(view, message);
                    MainActivity.restyleChildrenOrWidget(view);
                    return view;
                }
                if (message instanceof XMPPService.JabberIncomingMessage) {
                    XMPPService.JabberIncomingMessage jabberIncomingMessage = (XMPPService.JabberIncomingMessage)message;
                    view = getLayoutInflater().inflate(R.layout.incoming_messages_other, null);
                    makePressable(view);
                    TextView from  = (TextView)view.findViewById(R.id.from);
                    TextView preview = (TextView)view.findViewById(R.id.preview);
                    from.setText(jabberIncomingMessage.getFrom());
                    preview.setText(jabberIncomingMessage.getBody());
                    enableInternalButtons(view, message);
                    MainActivity.restyleChildrenOrWidget(view);
                    return view;
                }
                if (message instanceof XMPPService.JuickSubscriptionIncomingMessage) {
                    XMPPService.JuickSubscriptionIncomingMessage subscriptionMessage = (XMPPService.JuickSubscriptionIncomingMessage)message;
                    view = getLayoutInflater().inflate(R.layout.incoming_messages_subscription, null);
                    makePressable(view);

                    SpannableStringBuilder ssb = new SpannableStringBuilder(subscriptionMessage.getFrom()+" ");
                    int off = ssb.length();
                    TextView fromTags  = (TextView)view.findViewById(R.id.from_tags);
                    TextView preview = (TextView)view.findViewById(R.id.preview);
                    ArrayList<String> tags = subscriptionMessage.getTags();
                    for (int i1 = 0; i1 < tags.size(); i1++) {
                        String tag = tags.get(i1);
                        if (tag.length() == 0) continue;;
                        if (tag.startsWith("*")) tag = tag.substring(1);
                        ssb.append("*");
                        ssb.append(tag);
                        if (i1 != tags.size()-1)
                            ssb.append(" ");
                    }
                    ssb.setSpan(new ForegroundColorSpan(colorTheme.getColor(ColorsTheme.ColorKey.TAGS, 0xFF0000CC)), off, ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    int ix = ssb.length();
                    ssb.append(" - " + toRelaviteDate(subscriptionMessage.datetime.getTime(), russian));
                    ssb.setSpan(new ForegroundColorSpan(0xFF808080), ix, ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);


                    fromTags.setText(ssb);
                    String body = subscriptionMessage.getBody();
                    while(body.endsWith("\n")) {
                        body = body.substring(0, body.length()-1);
                    }
                    preview.setText(body);
                    enableInternalButtons(view, message);
                    MainActivity.restyleChildrenOrWidget(view);
                    return view;
                }
                return null;
            } catch (Exception ex) {
                TextView tv = new TextView(XMPPIncomingMessagesActivity.this);
                tv.setText("Error here: "+ex.toString());
                ACRA.getErrorReporter().handleException(ex, false);
                return tv;
            } finally {
                l = System.currentTimeMillis() - l;
                Log.i("XMPPIncomingMessages","get item ("+i+") time="+l);
            }
        }
    }

    final boolean russian = isRussian();

    public static boolean isRussian() {
        return Locale.getDefault().getLanguage().equals("ru");
    }

    public static String toRelaviteDate(long ts, boolean russian) {
        StringBuilder sb = new StringBuilder();
        ts = ((System.currentTimeMillis() - ts) / 1000) / 60;
        if (ts < 0) {
            return russian ? "в будущем" : "in future";
        }
        long minutes = ts % 60;
        ts /= 60;
        long hours = ts % 24;
        ts /= 24;
        long days = ts;
        if (days != 0) {
            sb.append(days);
            sb.append(russian?"д":"d ");
        }
        if (hours != 0) {
            sb.append(hours);
            sb.append(russian?"ч":"h ");
        }
        if (minutes != 0) {
            sb.append(minutes);
            sb.append(russian?"м":"m ");
        }
        if (sb.length() == 0) {
            sb.append(russian ? "только что":"now");
        }
        return sb.toString();
    }

    private void makePressable(View view) {
        if (view instanceof PressableLinearLayout) {
            final PressableLinearLayout sll = (PressableLinearLayout)view;
            sll.setPressedListener(new PressableLinearLayout.PressedListener() {
                @Override
                public void onPressStateChanged(boolean selected) {
                    MainActivity.restyleChildrenOrWidget(sll);
                }

                @Override
                public void onSelectStateChanged(boolean selected) {
                    MainActivity.restyleChildrenOrWidget(sll);
                }
            });
        }
    }

    private void enableInternalButtons(View view, final XMPPService.IncomingMessage message) {
        View deleteButton = view.findViewById(R.id.delete);
        if (deleteButton != null) {
            deleteButton.setVisibility(editMode ? View.VISIBLE : View.GONE);
            deleteButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    deleteOneMessage(message);

                }
            });
        }
    }

    private void deleteOneMessage(final XMPPService.IncomingMessage message) {
        Utils.ServiceGetter<XMPPService> xmppServiceGetter = new Utils.ServiceGetter<XMPPService>(this, XMPPService.class);
        xmppServiceServiceGetter.getService(new Utils.ServiceGetter.Receiver<XMPPService>() {
            @Override
            public void withoutService() {
            }

            @Override
            public void withService(XMPPService service) {
                service.removeMessage(message);
                refreshListWithAllMessages(service, service.incomingMessages);
                service.maybeCancelNotification();
            }
        });
    }

    private void deleteMessagesFrom(final String user) {
        Utils.ServiceGetter<XMPPService> xmppServiceGetter = new Utils.ServiceGetter<XMPPService>(this, XMPPService.class);
        xmppServiceServiceGetter.getService(new Utils.ServiceGetter.Receiver<XMPPService>() {
            @Override
            public void withoutService() {
            }

            @Override
            public void withService(XMPPService service) {
                service.removeMessagesFrom(user);
                refreshListWithAllMessages(service, service.incomingMessages);
                service.maybeCancelNotification();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.xmpp_messages, menu);
        return true;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        sp.edit().putBoolean("extxmpp.local.listeningAll", listeningAll).commit();
    }

    static boolean listeningAll;

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem listenAll = menu.findItem(R.id.listen_all);
        MenuItem unlistenAll = menu.findItem(R.id.unlisten_all);
        listenAll.setVisible(!listeningAll);
        unlistenAll.setVisible(listeningAll);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menuitem_preferences:
                Intent prefsIntent = new Intent(this, NewJuickPreferenceActivity.class);
                prefsIntent.putExtra("menu", NewJuickPreferenceActivity.Menu.TOP_LEVEL.name());
                startActivity(prefsIntent);
                return true;
            case R.id.listen_all:
            case R.id.unlisten_all:
                if (MainActivity.commandJAMService(this, item.getItemId() == R.id.listen_all ? "listen_all": "unlisten_all")) {
                    listeningAll = item.getItemId() == R.id.listen_all;
                } else {
                    Toast.makeText(this, "\""+getString(R.string.NonJabberNotifications)+"\" "+getString(R.string.IsNotEnabledOrServiceIsNotRunning), Toast.LENGTH_LONG).show();
                }
                return true;
            case R.id.editable_list:
                editMode = !editMode;
                final MyListView lv = (MyListView)findViewById(R.id.list);
                MyListAdapter adapter = (MyListAdapter)lv.getAdapter();
                adapter.notifyDataSetChanged();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(android.R.anim.fade_in, R.anim.leave_slide_to_top);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}
