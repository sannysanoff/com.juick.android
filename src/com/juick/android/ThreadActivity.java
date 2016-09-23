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
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.os.Handler;
import android.provider.MediaStore;
import android.support.v4.app.FragmentTransaction;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;
import android.view.*;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import com.actionbarsherlock.view.Menu;
import com.crashlytics.android.Crashlytics;
import com.juick.android.facebook.FacebookFeedMessagesSource;
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

    public static int instanceCount = 0;

    public static final int ACTIVITY_ATTACHMENT_IMAGE = 2;
    public static final int ACTIVITY_ATTACHMENT_VIDEO = 3;
    private TextView tvReplyTo;
    private RelativeLayout replyToContainer;
    private Button showThread;
    private Button draftsButton;
    private boolean enableDrafts;
    EditText etMessage;
    private String pulledDraft;        // originally pulled draft text, saved to compare and ignore save question
    private String pulledDraftMid;
    private long pulledDraftRid;
    private long pulledDraftTs;
    public ImageButton bSend;
    private ImageButton bAttach;
    private MessageID mid = null;
    private int rid = 0;
    private String attachmentUri = null;
    private String attachmentMime = null;
    Handler handler;
    private MessagesSource messagesSource;
    private JuickMessage selectedReply;
    private long usageStart;
    GestureDetector detector;

    public ThreadActivity() {
        instanceCount++;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        JuickAdvancedApplication.maybeEnableAcceleration(this);
        JuickAdvancedApplication.setupTheme(this);
        //requestWindowFeature(Window.FEATURE_NO_TITLE);
        //getSherlock().requestFeature(Window.FEATURE_NO_TITLE);
        final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
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
/*
        findViewById(R.id.gotoMain).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(ThreadActivity.this, MainActivity.class));
            }
        });
*/
        final View buttons = findViewById(R.id.buttons);
        bSend = (ImageButton) findViewById(R.id.buttonSend);
        bSend.setOnClickListener(this);
        bAttach = (ImageButton) findViewById(R.id.buttonAttachment);
        bAttach.setOnClickListener(this);
        etMessage = (EditText) findViewById(R.id.editMessage);

        if (sp.getBoolean("helvNueFonts", false)) {
            etMessage.setTypeface(JuickAdvancedApplication.helvNue);
/*
            TextView oldTitle = (TextView)findViewById(R.id.old_title);
            oldTitle.setTypeface(JuickAdvancedApplication.helvNue);
*/
        }


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
                                                .setTitle(getString(R.string.ReplacingText))
                                                .setMessage(getString(R.string.YourTextWillBeReplaced))
                                                .setNegativeButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                                    @Override
                                                    public void onClick(DialogInterface dialog, int which) {
                                                        doPull.run();
                                                    }

                                                })
                                                .setNeutralButton(getString(pulledDraft != null ? R.string.SaveChangedDraft:R.string.SaveDraft), new DialogInterface.OnClickListener() {
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
        tf = new ThreadFragment();
        tf.init(getLastCustomNonConfigurationInstance(), this);
        Bundle args = new Bundle();
        args.putSerializable("mid", mid);
        args.putSerializable("messagesSource", messagesSource);
        args.putSerializable("prefetched", i.getSerializableExtra("prefetched"));
        args.putSerializable("originalMessage", i.getSerializableExtra("originalMessage"));
        args.putBoolean("scrollToBottom", i.getBooleanExtra("scrollToBottom", false));
        tf.setArguments(args);
        ft.add(R.id.threadfragment, tf);
        ft.commit();
        MainActivity.restyleChildrenOrWidget(getWindow().getDecorView());
        detector = new GestureDetector(this, new GestureDetector.OnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return false;
            }

            @Override
            public void onShowPress(MotionEvent e) {

            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                return false;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                return false;
            }

            @Override
            public void onLongPress(MotionEvent e) {

            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (velocityX > 0 && Math.abs(velocityX) > 4 * Math.abs(velocityY) && Math.abs(velocityX) > 400) {
                    if (etMessage.getText().toString().trim().length() == 0) {
                        System.out.println("velocityX="+velocityX+" velocityY"+velocityY);
                        if (sp.getBoolean("swipeToClose", true)) {
                            onBackPressed();
                        }
                    }
                }
                return false;
            }
        });

        com.actionbarsherlock.app.ActionBar actionBar = getSherlock().getActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
        actionBar.setHomeButtonEnabled(true);
        actionBar.setLogo(R.drawable.back_button);

    }



    Runnable pendingTransition;

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

    public void doCancel() {
        etMessage.clearFocus();
        InputMethodManager imm = (InputMethodManager)getSystemService(
              Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(etMessage.getWindowToken(), 0);
    }

/*
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
*/

    void resetForm() {
        rid = 0;
        replyInProgress = null;
        selectedReply = null;
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
        //launchMainMessagesEnabler();
        super.onResume();    //To change body of overridden methods use File | Settings | File Templates.
        usageStart = System.currentTimeMillis();
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
        long usedTime = System.currentTimeMillis() - usageStart;
        if (messagesSource != null) {
            new WhatsNew(this).increaseUsage(this, "activity_time_"+messagesSource.getKind()+"_thread", usedTime);
        }
    }

    public void setFormEnabled(boolean state) {
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
                    com.juickadvanced.Utils.toRelaviteDate(message.Timestamp.getTime(), XMPPIncomingMessagesActivity.isRussian());
        //TextView oldTitle = (TextView)findViewById(R.id.old_title);
        setTitle(title);
        //oldTitle.setText(title);
        DatabaseService.rememberVisited(message);
        final Intent i = getIntent();
        final Serializable prefetched = i.getSerializableExtra("prefetched");
        JuickMessage prefetchedReply = null;
        if (prefetched != null && !focusedOnceOnPrefetched) {
            focusedOnceOnPrefetched = true;
            prefetchedReply = (JuickMessage) tf.getListView().getAdapter().getItem(tf.getListView().getAdapter().getCount() - 1);
            onReplySelected(prefetchedReply);
            etMessage.requestFocus();
        }
        final String midS = mid.toString();
        final SharedPreferences drafts = getSharedPreferences("drafts", MODE_PRIVATE);
        for (int q = 0; q < 1000; q++) {
            final String savedMID = drafts.getString("mid" + q, null);
            if (savedMID != null && savedMID.equals(midS)) {
                pullDraft(prefetchedReply, drafts, q);
                break;
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
     * @param drafts oh
     * @param q     draft index
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
    }

    public void onReplySelected(final JuickMessage msg) {
        selectedReply = msg;
        rid = msg.getRID();
        if (rid > 0) {
            SpannableStringBuilder ssb = new SpannableStringBuilder();
            String inreplyto = getResources().getString(R.string.In_reply_to_) + " ";
            ssb.append(inreplyto).append(msg.Text);
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
            jm.User = new JuickUser();
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


    public MicroBlog.OperationInProgress replyInProgress;

    private void sendReplyMain(String msg) {
        if (tf.listAdapter.getCount() > 0) {
            setFormEnabled(false);
            final JuickMessage threadStarter = tf.listAdapter.getItem(0);
            replyInProgress = MainActivity.getMicroBlog(mid.getMicroBlogCode()).postReply(this, mid, threadStarter, selectedReply, msg, attachmentUri, attachmentMime, new Utils.Function<Void, String>() {
                @Override
                public Void apply(String error) {
                    replyInProgress = null;
                    if (error == null) {
                        Utils.ServiceGetter<DatabaseService> databaseGetter = new Utils.ServiceGetter<DatabaseService>(ThreadActivity.this, DatabaseService.class);
                        databaseGetter.getService(new Utils.ServiceGetter.Receiver<DatabaseService>() {
                            @Override
                            public void withService(DatabaseService service) {
                                service.saveRecentlyCommentedThread(threadStarter);
                            }

                        });
                        sendMessageSucceed();
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
        } else {
            Toast.makeText(this, "Pardon!", Toast.LENGTH_LONG);
        }
    }

    public void sendMessageSucceed() {
        resetForm();
        if (attachmentUri == null) {
            Toast.makeText(this, R.string.Message_posted, Toast.LENGTH_LONG).show();
        } else {
            NewMessageActivity.getPhotoCaptureFile().delete(); // if any
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setNeutralButton(R.string.OK, null);
            builder.setIcon(android.R.drawable.ic_dialog_info);
            builder.setMessage(R.string.Message_posted);
            AlertDialog alertDialog = builder.create();
            alertDialog.show();
        }
        doCancel();
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
    public boolean onCreateOptionsMenu(Menu menu) {
        com.actionbarsherlock.view.MenuInflater inflater = getSupportMenuInflater();
        inflater.inflate(R.menu.thread, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.sort_by_rating).setVisible(messagesSource instanceof MessagesSource.SorterByRating);
        menu.findItem(R.id.sort_by_date_dec).setVisible(messagesSource instanceof MessagesSource.SorterByDateDesc);
        menu.findItem(R.id.sort_by_date_inc).setVisible(messagesSource instanceof MessagesSource.SorterByDateAsc);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(com.actionbarsherlock.view.MenuItem item) {
        switch (item.getItemId()) {
            case R.id.sort_by_rating:
                sortComments(((MessagesSource.SorterByRating) messagesSource).getSorterByRating());
                return true;
            case R.id.sort_by_date_dec:
                sortComments(((MessagesSource.SorterByDateDesc) messagesSource).getSorterByDateDesc());
                return true;
            case R.id.sort_by_date_inc:
                sortComments(((MessagesSource.SorterByDateAsc) messagesSource).getSorterByDateAsc());
                return true;
            case R.id.menuitem_preferences:
                Intent prefsIntent = new Intent(this, NewJuickPreferenceActivity.class);
                prefsIntent.putExtra("menu", NewJuickPreferenceActivity.Menu.TOP_LEVEL.name());
                startActivity(prefsIntent);
                return true;
            case android.R.id.home:
                finish();
                overridePendingTransition(R.anim.enter_raise_and_light, R.anim.leave_slide_to_right);
                if (MainActivity.nActiveMainActivities == 0) {
                    startActivity(new Intent(ThreadActivity.this, MainActivity.class));
                }
                return true;
            case R.id.reload:
                tf.reload();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void sortComments(MessagesSource.Sorter sorter) {
        tf.sortMessages(sorter);
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
    public void requestWindowFeature(long featureId) {
        // actionbar sherlock deducing flag from theme id.
        if (featureId == com.actionbarsherlock.view.Window.FEATURE_ACTION_BAR) return;
        super.requestWindowFeature(featureId);
    }

    @Override
    public boolean onListTouchEvent(View view, MotionEvent event) {
        return detector.onTouchEvent(event) || super.onListTouchEvent(view, event);
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
                            .setTitle(getString(R.string.LeavingThread))
                            .setMessage(getString(R.string.YourReplyIsNotSent))
                            .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.cancel();
                                }
                            })
                            .setNeutralButton(getString(R.string.SaveDraft), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    if (pulledDraft != null) {
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
        try {
            super.onBackPressed();    //To change body of overridden methods use File | Settings | File Templates.
        } catch (Throwable e) {
            MainActivity.handleException(e);
        }
        overridePendingTransition(R.anim.enter_raise_and_light, R.anim.leave_slide_to_right);
    }
}
