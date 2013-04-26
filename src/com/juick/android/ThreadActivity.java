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

import android.app.ActionBar;
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
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.MenuItem;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import com.juickadvanced.data.juick.JuickMessage;
import com.juickadvanced.data.juick.JuickUser;
import com.juickadvanced.data.MessageID;
import com.juick.android.juick.MessagesSource;
import com.juickadvanced.R;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Vector;

/**
 *
 * @author Ugnich Anton
 */
public class ThreadActivity extends JuickFragmentActivity implements View.OnClickListener, DialogInterface.OnClickListener, ThreadFragment.ThreadFragmentListener {

    public static int instanceCount;
    {
        instanceCount++;
    }

    public static final int ACTIVITY_ATTACHMENT_IMAGE = 2;
    public static final int ACTIVITY_ATTACHMENT_VIDEO = 3;
    private TextView tvReplyTo;
    private RelativeLayout replyToContainer;
    private Button showThread;
    private Button draftsButton;
    private boolean enableDrafts;
    private EditText etMessage;
    private String pulledDraft;        // originally pulled draft text, saved to compare and ignore save question
    private String pulledDraftMid;
    private long pulledDraftRid;
    private long pulledDraftTs;
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
        JuickAdvancedApplication.setupTheme(this);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        super.onCreate(savedInstanceState);
        handler = new Handler();

        Intent i = getIntent();
        mid = (MessageID)i.getSerializableExtra("mid");
        if (mid == null) {
            finish();
        }

        messagesSource = (MessagesSource) i.getSerializableExtra("messagesSource");
        if (sp.getBoolean("fullScreenThread", false)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
        }
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
                doCancel();
            }
        });

        tvReplyTo = (TextView) findViewById(R.id.textReplyTo);
        replyToContainer = (RelativeLayout) findViewById(R.id.replyToContainer);
        setHeight(replyToContainer, 0);
        showThread = (Button) findViewById(R.id.showThread);
        draftsButton = (Button) findViewById(R.id.drafts);
        etMessage.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    etMessage.setHint("");
                    setHeight(buttons, ActionBar.LayoutParams.WRAP_CONTENT);
                    InputMethodManager inputMgr = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                    inputMgr.toggleSoftInput(0, 0);
                } else {
                    etMessage.setHint(R.string.ClickToReply);
                    setHeight(buttons, 0);
                }
                //To change body of implemented methods use File | Settings | File Templates.
            }
        });
        draftsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                class Item {
                    String label;
                    long ts;
                    int index;

                    Item(String label, long ts, int index) {
                        this.label = label;
                        this.ts = ts;
                        this.index = index;
                    }
                }
                final ArrayList<Item> items = new ArrayList<Item>();
                final SharedPreferences drafts = getSharedPreferences("drafts", MODE_PRIVATE);
                for(int q=0; q<1000; q++) {
                    String msg = drafts.getString("message"+q, null);
                    if (msg != null) {
                        if (msg.length() > 50)
                            msg = msg.substring(0, 50);
                        items.add(new Item(msg, drafts.getLong("timestamp"+q, 0), q));
                    }
                }
                Collections.sort(items, new Comparator<Item>() {
                    @Override
                    public int compare(Item item, Item item2) {
                        final long l = item2.ts - item.ts;
                        return l == 0 ? 0 : l > 0 ? 1: -1;
                    }
                });
                CharSequence[] arr = new CharSequence[items.size()];
                for (int i1 = 0; i1 < items.size(); i1++) {
                    Item item = items.get(i1);
                    arr[i1] = item.label;
                }
                new AlertDialog.Builder(ThreadActivity.this)
                        .setItems(arr, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, final int which) {
                                final Runnable doPull= new Runnable() {
                                    @Override
                                    public void run() {
                                        pullDraft(null, drafts, items.get(which).index);
                                        updateDraftsButton();
                                    }
                                };
                                if (pulledDraft != null && pulledDraft.trim().equals(etMessage.getText().toString().trim())) {
                                    // no need to ask, user just looks at the drafts
                                    saveDraft(pulledDraftRid, pulledDraftMid, pulledDraftTs, pulledDraft);
                                    doPull.run();
                                } else {
                                    if (etMessage.getText().toString().length() > 0) {
                                        new AlertDialog.Builder(ThreadActivity.this)
                                                .setTitle("Replacing text")
                                                .setMessage("Your entered is not sent and will be replaced!")
                                                .setNegativeButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                                    @Override
                                                    public void onClick(DialogInterface dialog, int which) {
                                                        doPull.run();
                                                    }

                                                })
                                                .setNeutralButton(pulledDraft != null ? "Save changed draft":"Save to draft", new DialogInterface.OnClickListener() {
                                                    @Override
                                                    public void onClick(DialogInterface dialog, int which) {
                                                        if (pulledDraft != null) {
                                                            saveDraft(pulledDraftRid, pulledDraftMid, System.currentTimeMillis(), etMessage.getText().toString());
                                                        } else {
                                                            saveDraft(rid, mid.toString(), System.currentTimeMillis(), etMessage.getText().toString());
                                                        }
                                                        doPull.run();
                                                    }
                                                })
                                                .setPositiveButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                                                    @Override
                                                    public void onClick(DialogInterface dialog, int which) {
                                                    }
                                                }).show();
                                    } else {
                                        doPull.run();
                                    }
                                }
                            }
                        })
                        .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.cancel();
                            }
                        })
                        .show();
            }
        });
        enableDrafts = (sp.getBoolean("enableDrafts", false));
        if (sp.getBoolean("capitalizeReplies", false)) {
            etMessage.setInputType(etMessage.getInputType() | EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES);
        }
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        tf = new ThreadFragment(getLastCustomNonConfigurationInstance(), this);
        Bundle args = new Bundle();
        args.putSerializable("mid", mid);
        args.putSerializable("messagesSource", messagesSource);
        args.putSerializable("prefetched", i.getSerializableExtra("prefetched"));
        args.putBoolean("scrollToBottom", i.getBooleanExtra("scrollToBottom", false));
        tf.setArguments(args);
        ft.add(R.id.threadfragment, tf);
        ft.commit();
        MainActivity.restyleChildrenOrWidget(getWindow().getDecorView());

    }

    void saveDraft(long saveRid, String saveMid, long saveTs, String messag) {
        final SharedPreferences drafts = getSharedPreferences("drafts", MODE_PRIVATE);
        for (int i = 0; i < 1000; i++) {
            final String string = drafts.getString("message" + i, null);
            if (string == null) {
                drafts.edit()
                        .putString("message" + i, messag)
                        .putLong("timestamp" + i, saveTs)
                        .putLong("rid" + i, saveRid)
                        .putString("mid" + i, saveMid)
                        .commit();
                break;
            }
        }
    }

    private void doCancel() {
        etMessage.clearFocus();
        InputMethodManager imm = (InputMethodManager)getSystemService(
              Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(etMessage.getWindowToken(), 0);
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
        setHeight(replyToContainer, 0);
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

    boolean focusedOnceOnPrefetched = false;
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
        final Intent i = getIntent();
        final Serializable prefetched = i.getSerializableExtra("prefetched");
        JuickMessage prefetchedReply = null;
        if (prefetched != null && !focusedOnceOnPrefetched) {
            focusedOnceOnPrefetched = true;
            prefetchedReply = (JuickMessage) tf.getListView().getAdapter().getItem(tf.getListView().getAdapter().getCount() - 1);
            onReplySelected(prefetchedReply);
            etMessage.requestFocus();
        } else {
        }
        final String midS = mid.toString();
        final SharedPreferences drafts = getSharedPreferences("drafts", MODE_PRIVATE);
        for (int q = 0; q < 1000; q++) {
            final String savedMID = drafts.getString("mid" + q, null);
            if (savedMID != null && savedMID.equals(midS)) {
                pullDraft(prefetchedReply, drafts, q);
                break;
            } else {
            }
        }
        updateDraftsButton();
    }

    private void updateDraftsButton() {
        final SharedPreferences drafts = getSharedPreferences("drafts", MODE_PRIVATE);
        draftsButton.setVisibility(drafts.getAll().size() > 0 && enableDrafts? View.VISIBLE : View.GONE);
    }

    /**
     * @param prefetchedReply nullable, for prefetch mode only
     * @param drafts
     * @param q     draft index
     * @return      number of someDrafts increased if this draft is of no use
     */
    private void pullDraft(JuickMessage prefetchedReply, SharedPreferences drafts, int q) {
        final long savedRid = drafts.getLong("rid" + q, 0);
        boolean matchingRid = prefetchedReply == null;
        for(int msg=0; msg<tf.getListView().getAdapter().getCount(); msg++) {
            final Object item = tf.getListView().getAdapter().getItem(msg);
            if (item instanceof JuickMessage) {
                JuickMessage someReply = (JuickMessage) item;
                if (someReply.getRID() == savedRid) {
                    onReplySelected(someReply);
                    if (prefetchedReply != null && prefetchedReply.getRID() == someReply.getRID()) {
                        matchingRid = true;
                    }
                    break;
                }
            }
        }
        if (matchingRid) {
            etMessage.setText(pulledDraft = drafts.getString("message" + q, null));
            pulledDraftMid= drafts.getString("mid" + q, null);
            pulledDraftRid= drafts.getLong("rid" + q, 0);
            pulledDraftTs= drafts.getLong("timestamp" + q, 0);
            etMessage.requestFocus();
            drafts.edit()
                    .remove("message" + q)
                    .remove("timestamp" + q)
                    .remove("rid" + q)
                    .remove("mid" + q)
                    .commit();
        }
        return;
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
            setHeight(replyToContainer, ActionBar.LayoutParams.WRAP_CONTENT);
            showThread.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    tf.showThread(msg, false);
                }
            });
        } else {
            setHeight(replyToContainer, 0);
        }
    }

    private void setHeight(View view, int heightHint) {
        view.getLayoutParams().height = heightHint;
        view.setLayoutParams(view.getLayoutParams());
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
                    doCancel();
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
                        Toast.makeText(ThreadActivity.this, error, Toast.LENGTH_LONG).show();
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

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        instanceCount--;
    }

    @Override
    public boolean requestWindowFeature(long featureId) {
        // actionbar sherlock deducing flag from theme id.
        if (featureId == android.support.v4.view.Window.FEATURE_ACTION_BAR) return false;
        return super.requestWindowFeature(featureId);
    }



    @Override
    public void onBackPressed() {
        if (tf.onBackPressed()) return;
        if (tf != null && tf.listAdapter.imagePreviewHelper != null && tf.listAdapter.imagePreviewHelper.handleBack())
            return;
        if (enableDrafts) {
            final String messag = etMessage.getText().toString().trim();
            if (messag.length() > 0) {
                if (pulledDraft != null && pulledDraft.trim().equals(etMessage.getText().toString().trim())) {
                    saveDraft(pulledDraftRid, pulledDraftMid, pulledDraftTs, pulledDraft);
                } else {
                    new AlertDialog.Builder(this)
                            .setTitle("Leaving thread")
                            .setMessage("Your reply is not sent!")
                            .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.cancel();
                                }
                            })
                            .setNeutralButton("Save draft", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    if (pulledDraft != null ) {
                                        saveDraft(pulledDraftRid, pulledDraftMid, System.currentTimeMillis(), messag);
                                    } else {
                                        saveDraft(rid, mid.toString(), System.currentTimeMillis(), messag);
                                    }
                                    finish();
                                }
                            })
                            .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    finish();
                                }
                            }).show();
                    return;
                }
            }
        }
        super.onBackPressed();    //To change body of overridden methods use File | Settings | File Templates.
    }
}
