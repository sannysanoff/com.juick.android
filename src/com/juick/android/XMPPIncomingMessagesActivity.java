package com.juick.android;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.method.LinkMovementMethod;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.util.Linkify;
import android.util.Log;
import android.view.*;
import android.widget.*;
import com.actionbarsherlock.app.SherlockActivity;
import com.juick.android.api.ChildrenMessageSource;
import com.juick.android.bnw.BNWMicroBlog;
import com.juick.android.juick.JuickAPIAuthorizer;
import com.juickadvanced.data.bnw.BNWMessage;
import com.juickadvanced.data.bnw.BnwMessageID;
import com.juickadvanced.data.juick.JuickMessage;
import com.juickadvanced.data.juick.JuickUser;
import com.juickadvanced.data.MessageID;
import com.juickadvanced.data.juick.JuickMessageID;
import com.juickadvanced.R;
import com.juickadvanced.data.point.PointMessageID;

import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 8/8/12
 * Time: 2:18 PM
 * To change this template use File | Settings | File Templates.
 */
public class XMPPIncomingMessagesActivity extends SherlockActivity implements XMPPMessageReceiver.MessageReceiverListener{

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

        // requestWindowFeature(Window.FEATURE_NO_TITLE);
        JuickAdvancedApplication.maybeEnableAcceleration(this);
        super.onCreate(savedInstanceState);

        overridePendingTransition(R.anim.enter_slide_to_bottom, android.R.anim.fade_out);
        handler = new Handler();
        setContentView(R.layout.incoming_messages);
//        findViewById(R.id.gotoMain).setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                startActivity(new Intent(XMPPIncomingMessagesActivity.this, MainActivity.class));
//            }
//        });
//        TextView oldTitle = (TextView)findViewById(R.id.old_title);
//        oldTitle.setText(R.string.Incoming_Events);
        SharedPreferences sp = getSharedPrefs();
        editMode = sp.getBoolean("editMode", false);
        xmppServiceServiceGetter = new Utils.ServiceGetter<XMPPService>(this, XMPPService.class);
        final MyListView lv = (MyListView)findViewById(R.id.list);

        try {
            SwipeDismissListViewTouchListener touchListener =
                    new SwipeDismissListViewTouchListener(
                            lv,
                            new SwipeDismissListViewTouchListener.DismissCallbacks() {
                                @Override
                                public boolean canDismiss(int position) {
                                    MyListAdapter mAdapter = (MyListAdapter)lv.getAdapter();
                                    Object item = mAdapter.getItem(position);
                                    return item instanceof Item;
                                }

                                @Override
                                public void onDismiss(ListView listView, int[] reverseSortedPositions) {
                                    MyListAdapter mAdapter = (MyListAdapter)lv.getAdapter();
                                    for (int position : reverseSortedPositions) {
                                        Object messag = mAdapter.getItem(position);
                                        if (messag instanceof Item) {
                                            Item it = (Item)messag;
                                            deleteOneOrManyMessages(it.messages.get(0));
                                        }
                                        displayItems.remove(position);
                                    }
                                    // mAdapter.notifyDataSetChanged();
                                }
                            });
            lv.setOnTouchListener(touchListener);
            lv.setOnScrollListener(touchListener.makeScrollListener());
        } catch (Throwable ex) {
            // not supported
        }
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
                                final XMPPService.JuickIncomingMessage jim = (XMPPService.JuickIncomingMessage) incomingMessage;
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
                                collectURLs(msg.Text, listSelectedItem.getMID());
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
        com.actionbarsherlock.app.ActionBar actionBar = getSherlock().getActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
        actionBar.setHomeButtonEnabled(true);
        actionBar.setLogo(R.drawable.back_button);
        MainActivity.restyleChildrenOrWidget(getWindow().getDecorView());
        listeningAll = sp.getBoolean("extxmpp.local.listeningAll", false);
    }

    private void clickedOnMessage(final XMPPService.IncomingMessage incomingMessage) {
        if (incomingMessage instanceof XMPPService.JabberIncomingMessage) {
            xmppServiceServiceGetter.getService(new Utils.ServiceGetter.Receiver<XMPPService>() {
                @Override
                public void withService(final XMPPService service) {
                    ScrollView sv = new ScrollView(XMPPIncomingMessagesActivity.this);
                    final TextView tv = new TextView(XMPPIncomingMessagesActivity.this);
                    tv.setText(incomingMessage.body);
                    tv.setAutoLinkMask(Linkify.ALL);
                    tv.setMovementMethod(LinkMovementMethod.getInstance());
                    sv.addView(tv);
                    final ViewGroup.LayoutParams lp = tv.getLayoutParams();
                    lp.width = ViewGroup.LayoutParams.FILL_PARENT;
                    lp.height = ViewGroup.LayoutParams.FILL_PARENT;
                    tv.setLayoutParams(lp);
                    new AlertDialog.Builder(XMPPIncomingMessagesActivity.this)
                            .setView(sv)
                            .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    service.removeMessage(incomingMessage);
                                    refreshList();
                                    dialog.cancel();
                                }
                            }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                        }
                    }).setTitle(incomingMessage.from)
                    .show();
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
            MessageID mid = ((XMPPService.JuickIncomingMessage) incomingMessage).getMID();
            intent.putExtra("mid", mid);
            intent.putExtra("messagesSource", ChildrenMessageSource.forMID(this, mid));
            startActivity(intent);
            overridePendingTransition(R.anim.enter_slide_to_left, R.anim.leave_lower_and_dark);
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
        //launchMainMessagesEnabler();
        resumed = true;
    }

    private void launchMainMessagesEnabler() {
        // show always here.

//        runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//                findViewById(R.id.gotoMain).setVisibility(MainActivity.nActiveMainActivities == 0 ? View.VISIBLE : View.GONE);
//                if (resumed)
//                    handler.postDelayed(this, 1000);
//            }
//        });
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
        HashMap<MessageID, Item> threadMessages = new HashMap<MessageID, Item>();

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
        public int getViewTypeCount() {
            return 5;
        }

        @Override
        public int getItemViewType(int position) {
            Object item = displayItems.get(position);
            if (item instanceof Header) {
                return 0;
            }
            Item messagesItem = (Item)item;
            XMPPService.IncomingMessage message = messagesItem.messages.get(0);
            if (message instanceof XMPPService.JuickPrivateIncomingMessage) {
                return 1;
            }
            if (message instanceof XMPPService.JuickThreadIncomingMessage) {
                return 2;
            }
            if (message instanceof XMPPService.JabberIncomingMessage) {
                return 3;
            }
            if (message instanceof XMPPService.JuickSubscriptionIncomingMessage) {
                return 4;
            }
            return -1;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            long l = System.currentTimeMillis();
            try {
                Object item = displayItems.get(i);
                if (item instanceof Header) {
                    if (view == null)
                        view = getLayoutInflater().inflate(R.layout.incoming_messages_section, null);
                    TextView tv = (TextView)view.findViewById(R.id.value);
                    TextView btn = (TextView)view.findViewById(R.id.button);
                    if (editMode) {
                        RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams)btn.getLayoutParams();
                        layoutParams.getRules()[RelativeLayout.ALIGN_PARENT_RIGHT] = 0;
                        layoutParams.getRules()[RelativeLayout.ALIGN_PARENT_LEFT] = RelativeLayout.TRUE;
                        layoutParams = (RelativeLayout.LayoutParams)tv.getLayoutParams();
                        layoutParams.getRules()[RelativeLayout.LEFT_OF] = 0;
                        layoutParams.getRules()[RelativeLayout.RIGHT_OF] = R.id.button;
                    }
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
                    if (view == null)
                        view = getLayoutInflater().inflate(R.layout.incoming_messages_private, null);
                    makePressable(view);
                    TextView fromTags = (TextView)view.findViewById(R.id.from_tags);
                    fromTags.setCompoundDrawablesWithIntrinsicBounds(getIconForMessageId(privmsg.getMID(), fromTags), null, null, null);
                    TextView preview = (TextView)view.findViewById(R.id.preview);
                    fromTags.setText(privmsg.getFrom());
                    preview.setText(privmsg.getBody());
                    enableInternalButtons(view, message);
                    MainActivity.restyleChildrenOrWidget(view, true);
                    return view;
                }
                if (message instanceof XMPPService.JuickThreadIncomingMessage) {
                    XMPPService.JuickThreadIncomingMessage juickThreadIncomingMessage = (XMPPService.JuickThreadIncomingMessage)message;
                    HashMap<String,HashSet<String>> counts = new HashMap<String, HashSet<String>>();
                    int totalCount = 0;
                    MessageID topicMessageId = null;
                    int toYouCount = 0;
                    String accountName = JuickAPIAuthorizer.getJuickAccountName(XMPPIncomingMessagesActivity.this.getApplicationContext());
                    if (accountName != null) {
                        for (XMPPService.IncomingMessage incomingMessage : messagesItem.messages) {
                            XMPPService.JuickThreadIncomingMessage commentMessage = (XMPPService.JuickThreadIncomingMessage)incomingMessage;
                            String from = commentMessage.getFrom();
                            HashSet oldCount = counts.get(from);
                            if (oldCount == null) {
                                oldCount = new HashSet();
                                counts.put(from, oldCount);
                            }
                            oldCount.add(commentMessage.messageNoPlain);
                            String body = commentMessage.getBody();
                            boolean found = hasMyNickAnywhereInBody(accountName, body);
                            if (found) {
                                toYouCount++;
                            }
                            if (topicMessageId == null) {
                                topicMessageId = commentMessage.getMID();
                            }
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
                    if (view == null)
                        view = getLayoutInflater().inflate(R.layout.incoming_messages_threads, null);
                    makePressable(view);
                    XMPPService.JuickThreadIncomingMessage commentMessage = (XMPPService.JuickThreadIncomingMessage)messagesItem.messages.get(0);
                    XMPPService.JuickIncomingMessage originalMessage = commentMessage.getOriginalMessage();
                    TextView fromTags = (TextView)view.findViewById(R.id.from_tags);
                    fromTags.setCompoundDrawablesWithIntrinsicBounds(getIconForMessageId(juickThreadIncomingMessage.getMID(), fromTags), null, null, null);

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
                        ssb.append(" - " + com.juickadvanced.Utils.toRelaviteDate(((Item) item).lastTime, russian));
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
                        String toYou = " ("+toYouCount + " "+getString(R.string._to_you_)+")  ";
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
                    MainActivity.restyleChildrenOrWidget(view, true);
                    return view;
                }
                if (message instanceof XMPPService.JabberIncomingMessage) {
                    XMPPService.JabberIncomingMessage jabberIncomingMessage = (XMPPService.JabberIncomingMessage)message;
                    if (view == null)
                        view = getLayoutInflater().inflate(R.layout.incoming_messages_other, null);
                    makePressable(view);
                    TextView from  = (TextView)view.findViewById(R.id.from);
                    TextView preview = (TextView)view.findViewById(R.id.preview);
                    from.setText(jabberIncomingMessage.getFrom());
                    preview.setText(jabberIncomingMessage.getBody());
                    enableInternalButtons(view, message);
                    MainActivity.restyleChildrenOrWidget(view, true);
                    return view;
                }
                if (message instanceof XMPPService.JuickSubscriptionIncomingMessage) {
                    XMPPService.JuickSubscriptionIncomingMessage subscriptionMessage = (XMPPService.JuickSubscriptionIncomingMessage)message;
                    if (view == null)
                        view = getLayoutInflater().inflate(R.layout.incoming_messages_subscription, null);
                    makePressable(view);

                    SpannableStringBuilder ssb = new SpannableStringBuilder(subscriptionMessage.getFrom()+" ");
                    int off = ssb.length();
                    TextView fromTags  = (TextView)view.findViewById(R.id.from_tags);
                    if (fromTags != null) {
                        fromTags.setCompoundDrawablesWithIntrinsicBounds(getIconForMessageId(subscriptionMessage.getMID(), fromTags), null, null, null);
                    }
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
                    ssb.append(" - " + com.juickadvanced.Utils.toRelaviteDate(subscriptionMessage.datetime.getTime(), russian));
                    ssb.setSpan(new ForegroundColorSpan(0xFF808080), ix, ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    if (fromTags != null) {
                        fromTags.setText(ssb);
                    }
                    String body = subscriptionMessage.getBody();
                    while(body.endsWith("\n")) {
                        body = body.substring(0, body.length()-1);
                    }
                    if (preview != null) {
                        preview.setText(body);
                    }
                    enableInternalButtons(view, message);
                    MainActivity.restyleChildrenOrWidget(view, true);
                    return view;
                }
                return null;
            } catch (Exception ex) {
                TextView tv = new TextView(XMPPIncomingMessagesActivity.this);
                tv.setText("Error here: "+ex.toString());
                MainActivity.handleException(ex);
                return tv;
            } finally {
                l = System.currentTimeMillis() - l;
                Log.i("XMPPIncomingMessages","get item ("+i+") time="+l);
            }
        }
    }

    HashMap<String, Drawable> icons = new HashMap<String, Drawable>();

    private Drawable getIconForMessageId(MessageID mid, TextView destView) {
        String microBlogCode = mid.getMicroBlogCode();
        Drawable retval = icons.get(microBlogCode);
        if (retval == null) {
            float requiredSize = destView.getTextSize() * 1.3f;
            Drawable drawable = getIconForMicroblog(this, microBlogCode, (int) requiredSize);
            if (drawable != null) {
                icons.put(microBlogCode, drawable);
            }
            retval = drawable;
        }
        return retval;
    }

    public static Drawable getIconForMicroblog(Context ctx, String microBlogCode, int requiredSize) {
        Drawable drawable = null;
        if (microBlogCode.equals(JuickMessageID.CODE)) {
            drawable = ctx.getResources().getDrawable(R.drawable.navicon_juickadvanced);
        }
        if (microBlogCode.equals(PointMessageID.CODE)) {
            drawable = ctx.getResources().getDrawable(R.drawable.navicon_point);
        }
        if (microBlogCode.equals(BnwMessageID.CODE)) {
            drawable = ctx.getResources().getDrawable(R.drawable.navicon_bnw);
        }
        if (drawable != null) {
            drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
            int size = (int) requiredSize;
            Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bmp);
            canvas.scale(((float) size) / drawable.getIntrinsicWidth(), ((float) size) / drawable.getIntrinsicHeight());
            drawable.draw(canvas);
            drawable = new BitmapDrawable(ctx.getResources(), bmp);
        }
        return drawable;
    }

    public static boolean hasMyNickAnywhereInBody(String accountName, String body) {
        if (body == null || accountName == null) return false;
        String nickScanArea = body.toLowerCase()+" ";
        int scan = 0;
        String accountNameL = accountName.toLowerCase();
        boolean found = false;
        while(true) {
            int myNick = nickScanArea.indexOf("@" + accountNameL, scan);
            if (myNick != -1) {
                if (!JuickMessagesAdapter.isNickPart(nickScanArea.charAt(myNick + accountNameL.length() + 1))) {
                    found = true;
                    break;
                }
                scan = myNick + 1;
            } else {
                break;
            }
        }
        return found;
    }

    final boolean russian = isRussian();

    public static boolean isRussian() {
        return Locale.getDefault().getLanguage().equals("ru");
    }

    private void makePressable(View view) {
        if (view instanceof PressableLinearLayout) {
            final PressableLinearLayout sll = (PressableLinearLayout)view;
            sll.setPressedListener(new PressableLinearLayout.PressedListener() {
                @Override
                public void onPressStateChanged(boolean selected) {
                    MainActivity.restyleChildrenOrWidget(sll, true);
                }

                @Override
                public void onSelectStateChanged(boolean selected) {
                    MainActivity.restyleChildrenOrWidget(sll, true);
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
                    deleteOneOrManyMessages(message);

                }
            });
        }
    }

    private void deleteOneOrManyMessages(XMPPService.IncomingMessage message) {
        if (message instanceof XMPPService.JuickThreadIncomingMessage) {
            deleteThreadMessages(((XMPPService.JuickThreadIncomingMessage) message).getMID());
        } else {
            deleteOneMessage(message);
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

    private void deleteThreadMessages(final MessageID mid) {
        xmppServiceServiceGetter.getService(new Utils.ServiceGetter.Receiver<XMPPService>() {
            @Override
            public void withoutService() {
            }

            @Override
            public void withService(XMPPService service) {
                service.removeMessages(mid, false);
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
    public boolean onCreateOptionsMenu(com.actionbarsherlock.view.Menu menu) {
        com.actionbarsherlock.view.MenuInflater inflater = getSupportMenuInflater();
        inflater.inflate(R.menu.xmpp_messages, menu);
        return true;
    }


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        SharedPreferences sp = getSharedPrefs();
        sp.edit()
                .putBoolean("extxmpp.local.listeningAll", listeningAll)
                .putBoolean("editMode", editMode)
                .commit();
    }

    static boolean listeningAll;

    @Override
    public boolean onPrepareOptionsMenu(com.actionbarsherlock.view.Menu menu) {
        com.actionbarsherlock.view.MenuItem listenAll = menu.findItem(R.id.listen_all);
        com.actionbarsherlock.view.MenuItem unlistenAll = menu.findItem(R.id.unlisten_all);
        listenAll.setVisible(!listeningAll);
        unlistenAll.setVisible(listeningAll);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(com.actionbarsherlock.view.MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                if (MainActivity.nActiveMainActivities == 0) {
                    startActivity(new Intent(this, MainActivity.class));
                }
                return true;
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

    private SharedPreferences getSharedPrefs() {
        return getSharedPreferences("xmpp_incoming_messages_activity", MODE_PRIVATE);
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
