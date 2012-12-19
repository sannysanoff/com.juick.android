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

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.MenuItem;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;
import android.view.ContextThemeWrapper;
import android.view.MenuInflater;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import com.juickadvanced.data.juick.JuickMessage;
import com.juickadvanced.data.juick.JuickUser;
import com.juickadvanced.data.MessageID;
import com.juick.android.juick.MessagesSource;
import com.juickadvanced.R;

import java.io.File;
import java.util.Vector;

/**
 *
 * @author Ugnich Anton
 */
public class ThreadActivity extends FragmentActivity implements View.OnClickListener, DialogInterface.OnClickListener, ThreadFragment.ThreadFragmentListener {
    
    public static final int ACTIVITY_ATTACHMENT_IMAGE = 2;
    public static final int ACTIVITY_ATTACHMENT_VIDEO = 3;
    private TextView tvReplyTo;
    private RelativeLayout replyToContainer;
    private Button showThread;
    private EditText etMessage;
    private Button bSend;
    private ImageButton bAttach;
    private MessageID mid = null;
    private int rid = 0;
    private String attachmentUri = null;
    private String attachmentMime = null;
    private ProgressDialog progressDialog = null;
    Handler handler;
    private Handler progressHandler = new Handler() {
        
        @Override
        public void handleMessage(Message msg) {
            if (progressDialog.getMax() < msg.what) {
                progressDialog.setMax(msg.what);
            } else {
                progressDialog.setProgress(msg.what);
            }
        }
    };
    public ThreadFragment tf;
    private MessagesSource messagesSource;
    private JuickMessage selectedReply;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        handler = new Handler();

        Intent i = getIntent();
        mid = (MessageID)i.getSerializableExtra("mid");
        if (mid == null) {
            finish();
        }

        messagesSource = (MessagesSource) i.getSerializableExtra("messagesSource");
        setContentView(R.layout.thread);
        findViewById(R.id.gotoMain).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(ThreadActivity.this, MainActivity.class));
            }
        });
        final View buttons = findViewById(R.id.buttons);
        bSend = (Button) findViewById(R.id.buttonSend);
        bSend.setOnClickListener(this);
        bAttach = (ImageButton) findViewById(R.id.buttonAttachment);
        bAttach.setOnClickListener(this);
        etMessage = (EditText) findViewById(R.id.editMessage);
        Button cancel = (Button) findViewById(R.id.buttonCancel);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                etMessage.clearFocus();
                InputMethodManager imm = (InputMethodManager)getSystemService(
                      Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(etMessage.getWindowToken(), 0);
            }
        });

        tvReplyTo = (TextView) findViewById(R.id.textReplyTo);
        replyToContainer = (RelativeLayout) findViewById(R.id.replyToContainer);
        replyToContainer.setVisibility(View.GONE);
        showThread = (Button) findViewById(R.id.showThread);
        etMessage.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    etMessage.setHint("");
                    buttons.setVisibility(View.VISIBLE);
                } else {
                    etMessage.setHint(R.string.ClickToReply);
                    buttons.setVisibility(View.GONE);
                }
                //To change body of implemented methods use File | Settings | File Templates.
            }
        });
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        if (sp.getBoolean("capitalizeReplies", false)) {
            etMessage.setInputType(etMessage.getInputType() | EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES);
        }


        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        tf = new ThreadFragment(getLastCustomNonConfigurationInstance());
        Bundle args = new Bundle();
        args.putSerializable("mid", mid);
        args.putSerializable("messagesSource", messagesSource);
        args.putBoolean("scrollToBottom", i.getBooleanExtra("scrollToBottom", false));
        tf.setArguments(args);
        ft.add(R.id.threadfragment, tf);
        ft.commit();
        MainActivity.restyleChildrenOrWidget(getWindow().getDecorView());
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

    private void resetForm() {
        rid = 0;
        replyToContainer.setVisibility(View.GONE);
        etMessage.setText("");
        attachmentMime = null;
        attachmentUri = null;
        bAttach.setSelected(false);
        setFormEnabled(true);
    }

    boolean resumed = false;

    @Override
    protected void onResume() {
        resumed = true;
        MainActivity.restyleChildrenOrWidget(getWindow().getDecorView());
        launchMainMessagesEnabler();
        super.onResume();    //To change body of overridden methods use File | Settings | File Templates.
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
    }

    private void setFormEnabled(boolean state) {
        etMessage.setEnabled(state);
        bSend.setEnabled(state);
    }
    
    public void onThreadLoaded(JuickMessage message) {
        String title = "@" + message.User.UName;
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        boolean showNumbers = sp.getBoolean("showNumbers", false);
        if (showNumbers)
            title += " - "+message.getDisplayMessageNo()+" - "+
                    XMPPIncomingMessagesActivity.toRelaviteDate(message.Timestamp.getTime(), XMPPIncomingMessagesActivity.isRussian());
        TextView oldTitle = (TextView)findViewById(R.id.old_title);
        oldTitle.setText(title);
        DatabaseService.rememberVisited(message);
    }
    
    public void onReplySelected(final JuickMessage msg) {
        selectedReply = msg;
        rid = msg.getRID();
        if (rid > 0) {
            SpannableStringBuilder ssb = new SpannableStringBuilder();
            String inreplyto = getResources().getString(R.string.In_reply_to_) + " ";
            ssb.append(inreplyto + msg.Text);
            ssb.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, inreplyto.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            tvReplyTo.setText(ssb);
            replyToContainer.setVisibility(View.VISIBLE);
            showThread.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    tf.showThread(msg);
                }
            });
        } else {
            replyToContainer.setVisibility(View.GONE);
        }
    }

    @Override
    public Object onRetainCustomNonConfigurationInstance() {
        if (tf != null) {
            return tf.saveState();
        }
        return super.onRetainCustomNonConfigurationInstance();    //To change body of overridden methods use File | Settings | File Templates.
    }

    public void onClick(View view) {
        if (view == bAttach) {
            if (attachmentUri == null) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(R.string.Attach);
                builder.setAdapter(new AttachAdapter(this), this);
                builder.show();
            } else {
                attachmentUri = null;
                attachmentMime = null;
                bAttach.setSelected(false);
            }
        } else if (view == bSend) {
            final String msg = etMessage.getText().toString();
            if (msg.length() < 1) {
                Toast.makeText(this, R.string.Enter_a_message, Toast.LENGTH_SHORT).show();
                return;
            }
            final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
            try {
                if (sp.getBoolean("warnRepliesToBody", false) && rid == 0 && tf.getListView().getAdapter().getCount() > 3) {
                    new AlertDialog.Builder(this)
                            .setTitle("Post reply")
                            .setMessage("Replying to topic starter? (or select recipient)")
                            .setCancelable(true)
                            .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.cancel();
                                }
                            })
                            .setPositiveButton("Continue", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.cancel();
                                    previewAndSendReply(msg);
                                }
                            }).show();
                } else {
                    previewAndSendReply(msg);
                }
            } catch (Exception e) {
                Toast.makeText(this, e.toString(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void previewAndSendReply(final String msg) {
        final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        if (sp.getBoolean("previewReplies", false)) {
            final TextView tv = new TextView(this);
            JuickMessage jm = new JuickMessage();
            jm.User = new JuickUser();;
            jm.User.UName = "You";
            jm.Text = msg;
            jm.tags = new Vector<String>();
            if (rid != 0) {
                // insert destination user name
                JuickMessage reply = tf.findReply(tf.getListView(), rid);
                if (reply != null) {
                    jm.Text = "@"+reply.User.UName+" "+jm.Text;
                }
            }
            JuickMessagesAdapter.ParsedMessage parsedMessage = JuickMessagesAdapter.formatMessageText(this, jm, true);
            tv.setText(parsedMessage.textContent);
            tv.setPadding(10, 10, 10, 10);
            MainActivity.restyleChildrenOrWidget(tv);
            final AlertDialog dialog = new AlertDialog.Builder(new ContextThemeWrapper(this, R.style.Theme_Sherlock_Light))
                    .setTitle("Post reply - preview")
                    .setView(tv)
                    .setCancelable(true)
                    .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                        }
                    })
                    .setPositiveButton("Post reply", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                            sendReplyMain(msg);
                        }
                    }).create();
            dialog.setOnShowListener(new DialogInterface.OnShowListener() {
                @Override
                public void onShow(DialogInterface i) {
                    tv.setBackgroundColor(JuickMessagesAdapter.getColorTheme(dialog.getContext()).getBackground());
                    MainActivity.restyleChildrenOrWidget(tv);
                }
            });
            dialog.show();
        } else {
            sendReplyMain(msg);
        }
    }

    private void sendReplyMain(String msg) {
        setFormEnabled(false);
        MainActivity.getMicroBlog(mid.getMicroBlogCode()).postReply(this, mid, selectedReply, msg, attachmentUri, attachmentMime, new Utils.Function<Void, String>() {
            @Override
            public Void apply(String error) {
                if (error == null) {
                    resetForm();
                    if (attachmentUri == null) {
                        Toast.makeText(ThreadActivity.this, R.string.Message_posted, Toast.LENGTH_LONG).show();
                    } else {
                        NewMessageActivity.getPhotoCaptureFile().delete(); // if any
                        AlertDialog.Builder builder = new AlertDialog.Builder(ThreadActivity.this);
                        builder.setNeutralButton(R.string.OK, null);
                        builder.setIcon(android.R.drawable.ic_dialog_info);
                        builder.setMessage(R.string.Message_posted);
                        AlertDialog alertDialog = builder.create();
                        alertDialog.show();
                    }
                } else {
                    setFormEnabled(true);
                    if (attachmentUri != null) {
                        try {
                            AlertDialog.Builder builder = new AlertDialog.Builder(ThreadActivity.this);
                            builder.setNeutralButton(R.string.OK, null);
                            builder.setIcon(android.R.drawable.ic_dialog_alert);
                            builder.setMessage(error);
                            builder.show();
                        } catch (Exception e) {
                            // activity must be dead already
                        }
                    } else {

                    }
                }
                return null;
            }
        });
    }



    public void onClick(DialogInterface dialog, int which) {
        Intent intent;
        switch (which) {
            case 0:
                intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                startActivityForResult(Intent.createChooser(intent, null), ACTIVITY_ATTACHMENT_IMAGE);
                break;
            case 1:
                intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
                boolean useTempFileForCapture = sp.getBoolean("useTempFileForCapture", true);
                if (useTempFileForCapture) {
                    File file = NewMessageActivity.getPhotoCaptureFile();
                    file.delete();
                    intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(file));
                } else {
                    intent.putExtra(MediaStore.EXTRA_OUTPUT,
                            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                }
                startActivityForResult(intent, ACTIVITY_ATTACHMENT_IMAGE);
                break;
            case 2:
                intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("video/*");
                startActivityForResult(Intent.createChooser(intent, null), ACTIVITY_ATTACHMENT_VIDEO);
                break;
            case 3:
                intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
                intent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1);
                startActivityForResult(intent, ACTIVITY_ATTACHMENT_VIDEO);
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            if ((requestCode == ACTIVITY_ATTACHMENT_IMAGE || requestCode == ACTIVITY_ATTACHMENT_VIDEO)) {
                if (data != null) {
                    attachmentUri = data.getDataString();
                } else if (NewMessageActivity.getPhotoCaptureFile().exists()) {
                    attachmentUri = Uri.fromFile(NewMessageActivity.getPhotoCaptureFile()).toString();
                }
                if (requestCode == ACTIVITY_ATTACHMENT_IMAGE) {
                    NewMessageActivity.maybeResizePicture(this, attachmentUri, new Utils.Function<Void, String>() {
                        @Override
                        public Void apply(String s) {
                            attachmentUri = s;
                            bAttach.setSelected(attachmentUri != null);
                            return null;
                        }
                    });
                }
                attachmentMime = (requestCode == ACTIVITY_ATTACHMENT_IMAGE) ? "image/jpeg" : "video/3gpp";
                bAttach.setSelected(attachmentUri != null);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(android.support.v4.view.Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.thread, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menuitem_preferences:
                Intent prefsIntent = new Intent(this, NewJuickPreferenceActivity.class);
                prefsIntent.putExtra("menu", NewJuickPreferenceActivity.Menu.TOP_LEVEL.name());
                startActivity(prefsIntent);
                return true;
            case R.id.reload:
                tf.reload();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}
