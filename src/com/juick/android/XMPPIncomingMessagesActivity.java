package com.juick.android;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.*;
import com.juickadvanced.R;
 import de.quist.app.errorreporter.ExceptionReporter;

import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 8/8/12
 * Time: 2:18 PM
 * To change this template use File | Settings | File Templates.
 */
public class XMPPIncomingMessagesActivity extends Activity implements XMPPMessageReceiver.MessageReceiverListener{

    class Item {
        ArrayList<XMPPService.IncomingMessage> messages;

        Item(XMPPService.IncomingMessage message) {
            this();
            messages.add(message);
        }
        Item() {
            messages = new ArrayList<XMPPService.IncomingMessage>();
        }
    }

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
        ExceptionReporter.register(this);
        super.onCreate(savedInstanceState);
        handler = new Handler();
        setContentView(R.layout.incoming_messages);
        xmppServiceServiceGetter = new Utils.ServiceGetter<XMPPService>(this, XMPPService.class);
        refreshList();
        ListView lv = (ListView)findViewById(R.id.list);
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                Object o = displayItems.get(i);
                if (o instanceof Item) {
                    Item item = (Item) o;
                    final XMPPService.IncomingMessage incomingMessage = item.messages.get(0);
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
                            intent.putExtra("scrollToBottom", true);
                        }
                        intent.putExtra("mid", ((XMPPService.JuickIncomingMessage) incomingMessage).getPureThread());
                        startActivity(intent);
                        return;
                    }
                }
            }
        });
    }

    private void deleteMessage(XMPPService.IncomingMessage incomingMessage) {
        //To change body of created methods use File | Settings | File Templates.
    }

    @Override
    protected void onResume() {
        XMPPMessageReceiver.listeners.add(this);
        super.onResume();    //To change body of overridden methods use File | Settings | File Templates.
        refreshList();
    }

    @Override
    protected void onPause() {
        XMPPMessageReceiver.listeners.remove(this);
        super.onPause();    //To change body of overridden methods use File | Settings | File Templates.
    }

    @Override
    public boolean onMessageReceived(XMPPService.IncomingMessage msg) {
        refreshList();
        return false;
    }

    void refreshList() {

        xmppServiceServiceGetter.getService(new Utils.ServiceGetter.Receiver<XMPPService>() {
            @Override
            public void withService(XMPPService service) {
                synchronized (service.incomingMessages) {
                    refreshListWithAllMessages(service, service.incomingMessages);
                    service.maybeCancelNotification();
                }
            }

            @Override
            public void withoutService() {
                super.withoutService();
                Toast.makeText(XMPPIncomingMessagesActivity.this, "Unable to connect to XMPP service", Toast.LENGTH_LONG).show();
                refreshListWithAllMessages(null, new ArrayList<XMPPService.IncomingMessage>());
            }
        });
        MainActivity.restyleChildrenOrWidget(getWindow().getDecorView());
    }

    private void refreshListWithAllMessages(XMPPService service, ArrayList<XMPPService.IncomingMessage> allMessages) {

        ArrayList<Item> privateMessages = new ArrayList<Item>();
        ArrayList<Item> jabberMessages = new ArrayList<Item>();
        ArrayList<Item> subscriptions = new ArrayList<Item>();
        HashMap<String, Item> threadMessages = new HashMap<String, Item>();

        int nCommentsTotal = 0;

        for (XMPPService.IncomingMessage message : allMessages) {
            if (message instanceof XMPPService.JuickPrivateIncomingMessage) {
                privateMessages.add(new Item(message));
            }
            if (message instanceof XMPPService.JuickThreadIncomingMessage) {
                XMPPService.JuickThreadIncomingMessage tim = (XMPPService.JuickThreadIncomingMessage)message;
                Item item = threadMessages.get(""+tim.getPureThread());
                if (item == null) {
                    item = new Item();
                    threadMessages.put(""+tim.getPureThread(), item);
                }
                item.messages.add(tim);
                nCommentsTotal++;
            }
            if (message instanceof XMPPService.JabberIncomingMessage) {
                jabberMessages.add(new Item(message));
            }
            if (message instanceof XMPPService.JuickSubscriptionIncomingMessage) {
                XMPPService.JuickSubscriptionIncomingMessage subscriptionIncomingMessage = (XMPPService.JuickSubscriptionIncomingMessage) message;
                subscriptions.add(new Item(message));
            }
        }

        displayItems = new ArrayList<Object>();
        if (subscriptions.size() > 0) {
            displayItems.add(new Header("Subscriptions messages: "+subscriptions.size(), new Runnable() {
                @Override
                public void run() {
                    deleteMessages(XMPPService.JuickSubscriptionIncomingMessage.class);
                }
            }));
            for (int i = subscriptions.size() - 1; i >= 0; i--) {
                displayItems.add(subscriptions.get(i));
            }
        }
        if (privateMessages.size() > 0) {
            displayItems.add(new Header("Private messages: "+privateMessages.size(), new Runnable() {
                @Override
                public void run() {
                    deleteMessages(XMPPService.JuickPrivateIncomingMessage.class);
                }
            }));
            for (int i = privateMessages.size() - 1; i >= 0; i--) {
                displayItems.add(privateMessages.get(i));
            }
        }
        if (threadMessages.size() > 0) {
            displayItems.add(new Header("Thread comments: " + nCommentsTotal+" total", new Runnable() {
                @Override
                public void run() {
                    deleteMessages(XMPPService.JuickThreadIncomingMessage.class);
                }
            }));
            ArrayList<Item> sortee = new ArrayList<Item>(threadMessages.values());
            Collections.sort(sortee, new Comparator<Item>() {
                @Override
                public int compare(Item item, Item item1) {
                    XMPPService.JuickThreadIncomingMessage im = (XMPPService.JuickThreadIncomingMessage)item.messages.get(0);
                    XMPPService.JuickThreadIncomingMessage im1 = (XMPPService.JuickThreadIncomingMessage)item1.messages.get(0);
                    return im.getPureThread()- im1.getPureThread(); // sort by threads
                }
            });
            displayItems.addAll(sortee);
        }
        if (jabberMessages.size() > 0) {
            displayItems.add(new Header("Other messages: "+jabberMessages.size(), new Runnable() {
                @Override
                public void run() {
                    deleteMessages(XMPPService.JabberIncomingMessage.class);
                }
            }));
            for (int i = jabberMessages.size() - 1; i >= 0; i--) {
                displayItems.add(jabberMessages.get(i));
            }
        }
        ListView lv = (ListView)findViewById(R.id.list);
        Parcelable parcelable = lv.onSaveInstanceState();
        lv.setAdapter(new MyListAdapter());
        lv.onRestoreInstanceState(parcelable);

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

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
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
                TextView fromTags = (TextView)view.findViewById(R.id.from_tags);
                TextView preview = (TextView)view.findViewById(R.id.preview);
                fromTags.setText(privmsg.getFrom());
                preview.setText(privmsg.getBody());
                MainActivity.restyleChildrenOrWidget(view);
                return view;
            }
            if (message instanceof XMPPService.JuickThreadIncomingMessage) {
                HashMap<String,Integer> counts = new HashMap<String, Integer>();
                int totalCount = 0;
                int topicMessageId = -1;
                for (XMPPService.IncomingMessage incomingMessage : messagesItem.messages) {
                    XMPPService.JuickThreadIncomingMessage commentMessage = (XMPPService.JuickThreadIncomingMessage)incomingMessage;
                    String from = commentMessage.getFrom();
                    Integer oldCount = counts.get(from);
                    if (oldCount == null) oldCount = 0;
                    oldCount = oldCount + 1;
                    counts.put(from, oldCount);
                    totalCount++;
                    if (topicMessageId == -1) {
                        topicMessageId = commentMessage.getPureThread();
                    }
                }
                SpannableStringBuilder sb = new SpannableStringBuilder();
                for (Map.Entry<String, Integer> stringIntegerEntry : counts.entrySet()) {
                    sb.append(stringIntegerEntry.getKey());
                    Integer commentCount = stringIntegerEntry.getValue();
                    if (commentCount != 1) {
                        sb.append("("+commentCount+")");
                    }
                    sb.append(" ");
                }
                view = getLayoutInflater().inflate(R.layout.incoming_messages_threads, null);
                XMPPService.JuickThreadIncomingMessage commentMessage = (XMPPService.JuickThreadIncomingMessage)messagesItem.messages.get(0);
                TextView fromTags = (TextView)view.findViewById(R.id.from_tags);
                fromTags.setText(commentMessage.getOriginalFrom());
                TextView preview = (TextView)view.findViewById(R.id.preview);
                preview.setText(commentMessage.getOriginalBody() != null && commentMessage.getOriginalBody().length() > 0 ? commentMessage.getOriginalBody() : "[ loading ... ]");
                TextView commentCounts = (TextView)view.findViewById(R.id.comment_counts);
                String insertString;
                if (totalCount == 1) {
                    insertString = "1 comment ";
                } else {
                    insertString = totalCount + " comments ";
                }
                sb.insert(0, insertString);
                sb.setSpan(new ForegroundColorSpan(0xFF008000), 0, insertString.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.insert(insertString.length(), "from: ");
                int offset = insertString.length() + 6;
                sb.setSpan(new ForegroundColorSpan(0xFFC8934E), offset, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                commentCounts.setText(sb);
                MainActivity.restyleChildrenOrWidget(view);
                return view;
            }
            if (message instanceof XMPPService.JabberIncomingMessage) {
                XMPPService.JabberIncomingMessage jabberIncomingMessage = (XMPPService.JabberIncomingMessage)message;
                view = getLayoutInflater().inflate(R.layout.incoming_messages_other, null);
                TextView from  = (TextView)view.findViewById(R.id.from);
                TextView preview = (TextView)view.findViewById(R.id.preview);
                from.setText(jabberIncomingMessage.getFrom());
                preview.setText(jabberIncomingMessage.getBody());
                MainActivity.restyleChildrenOrWidget(view);
                return view;
            }
            if (message instanceof XMPPService.JuickSubscriptionIncomingMessage) {
                XMPPService.JuickSubscriptionIncomingMessage subscriptionMessage = (XMPPService.JuickSubscriptionIncomingMessage)message;
                view = getLayoutInflater().inflate(R.layout.incoming_messages_subscription, null);
                TextView fromTags  = (TextView)view.findViewById(R.id.from_tags);
                TextView preview = (TextView)view.findViewById(R.id.preview);
                fromTags.setText(subscriptionMessage.getFrom());
                preview.setText(subscriptionMessage.getBody());
                MainActivity.restyleChildrenOrWidget(view);
                return view;
            }
            return null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}
