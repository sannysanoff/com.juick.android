/*
 * Juick
 * Copyright (C) 2008-2012, Ugnich Anton
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.juick.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Pair;
import android.view.*;
import android.widget.*;
import com.google.gson.Gson;
import com.juick.android.ja.JAPastConversationMessagesSource;
import com.juick.android.juick.JuickMicroBlog;
import com.juick.android.juick.MessagesSource;
import com.juickadvanced.R;
import com.juickadvanced.RESTResponse;
import com.juickadvanced.data.MessageID;
import com.juickadvanced.data.UserInfo;
import com.juickadvanced.data.juick.JuickMessage;
import com.juickadvanced.data.juick.JuickUser;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import static com.juick.android.Utils.JA_API_URL;

/**
 */
public class UserCenterActivity extends Activity {

    private View search;
    private String uname;
    private String microblogCode;

    public static final int SEARCH_PAST_CONVERSATIONS = 0x100001;
    public static final int SEARCH_MORE               = 0x100002;
    Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        JuickAdvancedApplication.setupTheme(this);
        handler = new Handler();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.user_center);
        final ListView list = (ListView) findViewById(R.id.list);
        final View listWait = findViewById(R.id.list_wait);
        final TextView userRealName = (TextView)findViewById(R.id.user_realname);
        final ImageView userPic = (ImageView)findViewById(R.id.userpic);
        final TextView userName = (TextView)findViewById(R.id.username);
        search = findViewById(R.id.search);
        final View stats = findViewById(R.id.stats);
        userRealName.setText("...");
        Bundle extras = getIntent().getExtras();
        if (extras == null) {
            finish();
            return;
        }
        uname = extras.getString("uname");
        microblogCode = extras.getString("microblogCode");
        final int uid = extras.getInt("uid");

        final MessageID mid = (MessageID)extras.getSerializable("mid");
        final MessagesSource messagesSource = (MessagesSource)extras.getSerializable("messagesSource");
        if (uname == null || mid == null) {
            finish();
            return;
        }
        int height = getWindow().getWindowManager().getDefaultDisplay().getHeight();
        final int userpicSize = height <= 320 ? 32 : 96;
        float scaledDensity = getResources().getDisplayMetrics().scaledDensity;
        userPic.setMinimumHeight((int)(scaledDensity * userpicSize));
        userPic.setMinimumWidth((int) (scaledDensity * userpicSize));
        stats.setEnabled(false);
        userName.setText("@"+uname);
        final boolean russian = Locale.getDefault().getLanguage().equals("ru");
        new Thread() {
            @Override
            public void run() {
                final RESTResponse json = Utils.getJSON(UserCenterActivity.this,
                        JA_API_URL + "/userinfo?uname=" + Uri.encode(uname), null);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        stats.setEnabled(true);
                        if (json.getErrorText() != null) {
                            Toast.makeText(UserCenterActivity.this, "JA server: "+json.getErrorText(), Toast.LENGTH_LONG).show();
                            listWait.setVisibility(View.GONE);
                        } else {
                            final UserInfo userInfo = new Gson().fromJson(json.getResult(), UserInfo.class);
                            if (userInfo == null){
                                Toast.makeText(UserCenterActivity.this, "Unable to parse JSON", Toast.LENGTH_LONG).show();
                                listWait.setVisibility(View.GONE);
                            } else {
                                userRealName.setText(userInfo.fullName);
                                listWait.setVisibility(View.GONE);
                                list.setVisibility(View.VISIBLE);
                                list.setAdapter(new BaseAdapter() {
                                    @Override
                                    public int getCount() {
                                        return userInfo.getExtraInfo().size();
                                    }

                                    @Override
                                    public Object getItem(int position) {
                                        return userInfo.getExtraInfo().get(position);
                                    }

                                    @Override
                                    public long getItemId(int position) {
                                        return position;
                                    }

                                    @Override
                                    public View getView(int position, View convertView, ViewGroup parent) {
                                        if (convertView == null) {
                                            convertView = getLayoutInflater().inflate(R.layout.listitem_userinfo, null);
                                        }
                                        TextView text = (TextView)convertView.findViewById(R.id.text);
                                        TextView text2 = (TextView)convertView.findViewById(R.id.text2);
                                        String info = userInfo.getExtraInfo().get(position);
                                        int ix = info.indexOf("|");
                                        if (ix == -1) {
                                            text.setText(info);
                                            if (russian && UserInfo.translations.containsKey(info)) {
                                                info = UserInfo.translations.get(info);
                                            }
                                            text2.setText("");
                                        } else {
                                            String theInfo = info.substring(0, ix);
                                            if (russian && UserInfo.translations.containsKey(theInfo)) {
                                                theInfo = UserInfo.translations.get(theInfo);
                                            }
                                            text.setText(theInfo);
                                            String value = info.substring(ix + 1);
                                            if (value.startsWith("Date:")) {
                                                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                                                value = value.substring(5);
                                                value = sdf.format(new Date(Long.parseLong(value)));
                                            }
                                            text2.setText(value);
                                        }
                                        return convertView;
                                    }
                                });
                            }
                        }
                    }
                });
            }

        }.start();

        View subscribe_user = findViewById(R.id.subscribe_user);
        View unsubscribe_user = findViewById(R.id.unsubscribe_user);
        View subscribe_comments = findViewById(R.id.subscribe_comments);
        View unsubscribe_comments = findViewById(R.id.unsubscribe_comments);
        View filter_user = findViewById(R.id.filter_user);
        View blacklist_user = findViewById(R.id.blacklist_user);
        View show_blog = findViewById(R.id.show_blog);
        MicroBlog microBlog = MainActivity.getMicroBlog(mid.getMicroBlogCode());
        final MessageMenu mm = microBlog.getMessageMenu(this, messagesSource, null, null);
        JuickMessage message = microBlog.createMessage();
        mm.listSelectedItem = message;
        message.User = new JuickUser();
        message.User.UName = uname;
        message.User.UID = uid;
        message.setMID(mid);
        final UserpicStorage.AvatarID avatarID = microBlog.getAvatarID(message);

        final UserpicStorage.Listener userpicListener = new UserpicStorage.Listener() {
            @Override
            public void onUserpicReady(UserpicStorage.AvatarID id, int size) {
                final UserpicStorage.Listener thiz = this;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        UserpicStorage.instance.removeListener(avatarID, userpicSize, thiz);
                        final Bitmap userpic = UserpicStorage.instance.getUserpic(UserCenterActivity.this, avatarID, userpicSize, thiz);
                        userPic.setImageBitmap(userpic);    // can be null
                    }
                });
            }
        };
        Bitmap userpic = UserpicStorage.instance.getUserpic(this, avatarID, userpicSize, userpicListener);
        userPic.setImageBitmap(userpic);    // can be null


        subscribe_user.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mm.actionSubscribeUser();
            }
        });
        show_blog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mm.listSelectedItem.User.UID == 0) {
                    JuickMicroBlog.obtainProperUserIdByName(UserCenterActivity.this, mm.listSelectedItem.User.UName, "Getting Juick User Id", new Utils.Function<Void, Pair<String, String>>() {
                        @Override
                        public Void apply(Pair<String, String> cred) {
                            mm.listSelectedItem.User.UID = Integer.parseInt(cred.first);
                            mm.actionUserBlog();
                            return null;
                        }
                    });
                } else {
                    mm.actionUserBlog();
                }
            }
        });
        search.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                search.setOnCreateContextMenuListener(new View.OnCreateContextMenuListener() {
                    @Override
                    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
                        menu.add(0, SEARCH_PAST_CONVERSATIONS, 0, "My dialogs with user");
                        menu.add(0, SEARCH_MORE, 1, "More");
                    }
                });
                search.showContextMenu();
            }
        });
        stats.setOnClickListener(new View.OnClickListener() {

            NewJuickPreferenceActivity.MenuItem[] items = new NewJuickPreferenceActivity.MenuItem[] {
                    new NewJuickPreferenceActivity.MenuItem(R.string.UserAllTimeActivityReport, R.string.UserAllTimeActivityReport2, new Runnable() {
                        @Override
                        public void run() {
                            NewJuickPreferenceActivity.showChart(UserCenterActivity.this, "USER_ACTIVITY_VOLUME", "uid="+uid);
                        }
                    }),
                    new NewJuickPreferenceActivity.MenuItem(R.string.UserHoursReport, R.string.UserHoursReport2, new Runnable() {
                        @Override
                        public void run() {
                            NewJuickPreferenceActivity.showChart(UserCenterActivity.this, "USER_HOURS_ACTIVITY", "uid="+uid+"&tzoffset="+ TimeZone.getDefault().getRawOffset()/1000/60/60);
                        }
                    })
            };

            @Override
            public void onClick(View v) {
                list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                        items[position].action.run();
                    }
                });
                list.setAdapter(new BaseAdapter() {
                    @Override
                    public int getCount() {
                        return items.length;
                    }

                    @Override
                    public Object getItem(int position) {
                        return items[position];
                    }

                    @Override
                    public long getItemId(int position) {
                        return position;
                    }

                    @Override
                    public View getView(int position, View convertView, ViewGroup parent) {
                        LayoutInflater layoutInflater = getLayoutInflater();
                        View listItem = layoutInflater.inflate(android.R.layout.simple_list_item_2, null);
                        TextView text = (TextView)listItem.findViewById(android.R.id.text1);
                        text.setText(items[position].labelId);
                        TextView text2 = (TextView)listItem.findViewById(android.R.id.text2);
                        text2.setText(items[position].label2Id);
                        return listItem;
                    }
                });
            }
        });
        blacklist_user.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mm.actionBlacklistUser();
            }
        });
        filter_user.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mm.actionFilterUser(uname);
            }
        });
        unsubscribe_user.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mm.actionUnsubscribeUser();
            }
        });
        subscribe_comments.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                enableJAM(new Runnable() {
                    @Override
                    public void run() {
                        JAMService.instance.client.subscribeToComments(uname, microblogCode);
                    }
                });
            }
        });
        unsubscribe_comments.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                enableJAM(new Runnable() {
                    @Override
                    public void run() {
                        JAMService.instance.client.unsubscribeFromComments(uname, microblogCode);
                    }
                });
            }
        });
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (item.getItemId() == SEARCH_PAST_CONVERSATIONS) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    JuickMicroBlog.withUserId(UserCenterActivity.this, new Utils.Function<Void, Pair<Integer, String>>() {
                        @Override
                        public Void apply(Pair<Integer, String> integerStringPair) {
                            Intent i = new Intent(UserCenterActivity.this, MessagesActivity.class);
                            i.putExtra("messagesSource", new JAPastConversationMessagesSource(UserCenterActivity.this, uname));
                            startActivity(i);
                            return null;
                        }
                    });
                }
            }, 500);
            return true;
        }
        if (item.getItemId() == SEARCH_MORE) {
            Toast.makeText(this, "Coming soon", Toast.LENGTH_LONG).show();
        }
        return super.onContextItemSelected(item);
    }

    private void enableJAM(final Runnable then) {
        if (!MainActivity.isJAMServiceRunning(UserCenterActivity.this)) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setCancelable(true);
            builder.setMessage(R.string.JAMNotEnabledEnable);
            builder.setTitle(getString(R.string.JuickAdvancedFunctionality));
            builder.setPositiveButton(R.string.OK, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(UserCenterActivity.this);
                    sp.edit().putBoolean("enableJAMessaging", true).commit();
                    MainActivity.toggleJAMessaging(UserCenterActivity.this, true);
                    final ProgressDialog pd = new ProgressDialog(UserCenterActivity.this);
                    dialog.cancel();
                    pd.setIndeterminate(true);
                    pd.setMessage(getString(R.string.WaitingForService));
                    final Thread thread = new Thread() {
                        @Override
                        public void run() {
                            while (true) {
                                try {
                                    JAMService instance = JAMService.instance;
                                    if (instance != null) {
                                        JAXMPPClient client = instance.client;
                                        if (client != null && client.loggedIn) {
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    pd.cancel();
                                                    then.run();
                                                }
                                            });
                                        }
                                    }
                                    Thread.sleep(500);
                                } catch (InterruptedException e) {
                                    break;
                                }
                            }
                            super.run();    //To change body of overridden methods use File | Settings | File Templates.
                        }
                    };
                    thread.start();
                    pd.setCancelable(true);
                    pd.setOnCancelListener(new DialogInterface.OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialog) {
                            thread.interrupt();
                        }
                    });
                    pd.show();
                }
            });
            builder.setNegativeButton(R.string.Cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.cancel();
                }
            });
            builder.show();
        } else {
            then.run();
        }
        return;
    }
}
