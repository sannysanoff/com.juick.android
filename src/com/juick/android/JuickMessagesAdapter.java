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
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.*;
import android.net.Uri;
import android.os.Handler;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.text.Layout;
import android.text.Layout.Alignment;
import android.text.style.*;
import android.util.Log;
import android.view.MotionEvent;
import android.webkit.WebView;
import android.widget.*;
import com.juick.android.api.JuickMessage;
import android.content.Context;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.juickadvanced.R;

import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.juickadvanced.imaging.*;
import org.apache.http.*;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.impl.client.DefaultHttpClient;

/**
 *
 * @author Ugnich Anton
 */
public class JuickMessagesAdapter extends ArrayAdapter<JuickMessage> {

    private static final String PREFERENCES_SCALE = "messagesFontScale";
    public static final int TYPE_MESSAGES = 0;
    public static final int TYPE_THREAD = 1;
    public static final int SUBTYPE_ALL = 1;
    public static final int SUBTYPE_OTHER = 2;
    public static Pattern msgPattern = Pattern.compile("#[0-9]+");
//    public static Pattern usrPattern = Pattern.compile("@[a-zA-Z0-9\\-]{2,16}");
    private static String Replies;
    public int type;
    private boolean allItemsEnabled = true;

    public static Set<String> filteredOutUsers;
    private final double imageHeightPercent;
    private final String imageLoadMode;
    private final String proxyPassword;
    private final String proxyLogin;
    private final Thread mUiThread;

    Utils.ServiceGetter<DatabaseService> databaseGetter;
    Utils.ServiceGetter<XMPPService> xmppServiceGetter;
    private boolean trackLastRead;
    private int subtype;
    private final boolean indirectImages;
    private final boolean otherImages;
    Handler handler;

    public static int instanceCount;

    {
        instanceCount++;
    }


    public static Set<String> getFilteredOutUsers(Context ctx) {
        if (filteredOutUsers == null) {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
            filteredOutUsers = Utils.string2set(sp.getString("filteredOutUsers", ""));
        }
        return filteredOutUsers;
    }

    static long lastCachedShowNumbers;
    static boolean _showNumbers;
    public static boolean showNumbers(Context ctx) {
        if (System.currentTimeMillis() - lastCachedShowNumbers > 3000) {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
            _showNumbers = sp.getBoolean("showNumbers", false);
            lastCachedShowNumbers = System.currentTimeMillis();
        }
        return _showNumbers;
    }
    static long lastCachedShowUserpics;
    static boolean _showUserpics;
    public static boolean showUserpics(Context ctx) {
        if (System.currentTimeMillis() - lastCachedShowUserpics > 3000) {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
            _showUserpics = sp.getBoolean("showUserpics", true);
            lastCachedShowUserpics = System.currentTimeMillis();
        }
        return _showUserpics;
    }
    private SharedPreferences sp;
    public static float defaultTextSize;
    public static float textScale;

    public JuickMessagesAdapter(Context context, int type, int subtype) {
        super(context, R.layout.listitem_juickmessage);
        sp = PreferenceManager.getDefaultSharedPreferences(context);
        mUiThread = Thread.currentThread();
        if (Replies == null) {
            Replies = context.getResources().getString(R.string.Replies_) + " ";
        }
        this.type = type;
        this.subtype = subtype;
        imageHeightPercent = Double.parseDouble(sp.getString("image.height_percent", "0.3"));
        trackLastRead = sp.getBoolean("lastReadMessages", false);
        imageLoadMode = sp.getString("image.loadMode", "off");
        proxyPassword = sp.getString("imageproxy.password", "");
        proxyLogin = sp.getString("imageproxy.login", "");
        indirectImages = sp.getBoolean("image.indirect", true);
        otherImages = sp.getBoolean("image.other", true);
        textScale = 1;
        try {
            textScale = Float.parseFloat(sp.getString(PREFERENCES_SCALE, "1.0"));
        } catch (Exception ex) {
            //
        }
        databaseGetter = new Utils.ServiceGetter<DatabaseService>(context, DatabaseService.class);
        xmppServiceGetter = new Utils.ServiceGetter<XMPPService>(context, XMPPService.class);
        handler = new Handler();
    }


    static class ListRowRuntime {
        int pictureSize;
        JuickMessage jmsg;
        UserpicStorage.Listener userpicListener;

        ListRowRuntime(JuickMessage jmsg) {
            this.jmsg = jmsg;
        }
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final JuickMessage jmsg = getItem(position);
        View v = convertView;

        float neededTextSize = getDefaultTextSize(getContext()) * textScale;
        if (jmsg.User != null && jmsg.Text != null) {
            if (v == null || !(v instanceof LinearLayout)) {
                LayoutInflater vi = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                v = vi.inflate(R.layout.listitem_juickmessage, null);
                final PressableLinearLayout sll = (PressableLinearLayout)v;
                sll.setPressedListener(new PressableLinearLayout.PressedListener() {
                    @Override
                    public void onPressStateChanged(boolean selected) {
                        MainActivity.restyleChildrenOrWidget(sll);
                    }
                });
//                TextView tv = (TextView) v.findViewById(R.id.text);
//                float textSize = tv.getTextSize();
//                try {
//                    Float fontScale = Float.parseFloat(sp.getString(PREFERENCES_SCALE, "1.0"));
//                    tv.setTextSize(textSize*fontScale);
//                } catch (Exception e) {
//                    //
//                }
            }
            final LinearLayout ll = (LinearLayout)v;
            final TextView t = (TextView) v.findViewById(R.id.text);
            final ListRowRuntime lrr = new ListRowRuntime(jmsg);
            t.setTag(lrr);
            t.setTextSize(neededTextSize);

            final ParsedMessage parsedMessage;
            if (type == TYPE_THREAD && jmsg.RID == 0) {
                parsedMessage = formatFirstMessageText(jmsg);
            } else {
                parsedMessage = formatMessageText(getContext(), jmsg, false);
            }

            final ImageView userPic = (ImageView) v.findViewById(R.id.userpic);
            if (parsedMessage.userpicSpan != null) {
                lrr.pictureSize = (int)(2 * neededTextSize * 0.9);
                userPic.getLayoutParams().width = lrr.pictureSize;
                userPic.getLayoutParams().height = lrr.pictureSize;
                int padding = ((int)(2 * neededTextSize) - lrr.pictureSize)/2;
                userPic.setPadding(padding, padding, padding, padding);
                lrr.userpicListener = new UserpicStorage.Listener() {
                    @Override
                    public void onUserpicReady(int id, int size) {
                        UserpicStorage.instance.removeListener(lrr.jmsg.User.UID, lrr.pictureSize, this);
                        final Bitmap userpic = UserpicStorage.instance.getUserpic(getContext(), jmsg.User.UID, lrr.pictureSize, lrr.userpicListener);
                        if (userpic != null) {
                            ((Activity)getContext()).runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    ListRowRuntime tag = (ListRowRuntime)t.getTag();
                                    if (tag.jmsg.MID == jmsg.MID) {
                                        userPic.setImageBitmap(userpic);
                                    }
                                    //To change body of implemented methods use File | Settings | File Templates.
                                }
                            });
                        }
                    }
                };
                Bitmap userpic = UserpicStorage.instance.getUserpic(getContext(), jmsg.User.UID, lrr.pictureSize, lrr.userpicListener);
                userPic.setImageBitmap(userpic);    // can be null
            } else {
                userPic.setImageBitmap(null);
            }
            if (type != TYPE_THREAD && trackLastRead) {
                databaseGetter.getService(new Utils.ServiceGetter.Receiver<DatabaseService>() {
                    @Override
                    public void withService(DatabaseService service) {
                        service.getMessageReadStatus(jmsg.MID, new Utils.Function<Void, DatabaseService.MessageReadStatus>() {
                            @Override
                            public Void apply(DatabaseService.MessageReadStatus messageReadStatus) {
                                if (messageReadStatus.read) {
                                    handler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            ListRowRuntime tag = (ListRowRuntime)t.getTag();
                                            if (tag.jmsg.MID == jmsg.MID) {
                                                // still valid
                                                parsedMessage.markAsRead();
                                                t.setText(parsedMessage.textContent);
                                            }
                                        }
                                    });
                                }
                                return null;
                            }
                        });
                    }
                });
                xmppServiceGetter.getService(new Utils.ServiceGetter.Receiver<XMPPService>() {
                    @Override
                    public void withService(XMPPService service) {
                        service.removeMessages(jmsg.MID, true);
                    }
                });

            }
            if (!parsedMessage.read) {  // could be set synchronously by the getMessageReadStatus, above
                t.setText(parsedMessage.textContent);
            }
            final ArrayList<String> images = filterImagesUrls(parsedMessage.urls);
            if (images.size() > 0 && !imageLoadMode.equals("off")) {
                final WebViewGallery gallery = new WebViewGallery(getContext());
                gallery.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                gallery.setSpacing(20);
                gallery.setVisibility(View.VISIBLE);
                ((LinearLayout) v).addView(gallery);
                gallery.setOnItemClickListener(new AdapterView.OnItemClickListener() {

                    long lastClick = 0;
                    @Override
                    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                        Object tag = gallery.getTag();
                        if (tag instanceof HashMap) {
                            HashMap<Integer, ImageLoaderConfiguration> loaders = (HashMap<Integer, ImageLoaderConfiguration>)tag;
                            final ImageLoaderConfiguration imageLoader = loaders.get(i);
                            if (imageLoader != null && imageLoader.loader != null) {
                                Toast.makeText(getContext(), imageLoader.loader.info(), Toast.LENGTH_SHORT).show();
                                if (System.currentTimeMillis() - lastClick < 500) {
                                    if (!imageLoader.useOriginal) {
                                        new AlertDialog.Builder(getContext())
                                                .setTitle(R.string.DownloadOriginal)
                                                .setPositiveButton(R.string.OK, new DialogInterface.OnClickListener() {
                                                    @Override
                                                    public void onClick(DialogInterface dialog, int which) {
                                                        imageLoader.useOriginal = true;
                                                        imageLoader.loader.getDestFile().delete();
                                                        ImageLoader oldLoader = imageLoader.loader;
                                                        imageLoader.loader = null;
                                                        oldLoader.resetAdapter();
                                                    }
                                                })
                                                .setNeutralButton(R.string.JustSame, new DialogInterface.OnClickListener() {
                                                    @Override
                                                    public void onClick(DialogInterface dialog, int which) {
                                                        imageLoader.loader.getDestFile().delete();
                                                        ImageLoader oldLoader = imageLoader.loader;
                                                        imageLoader.loader = null;
                                                        oldLoader.resetAdapter();
                                                    }
                                                })
                                                .setNegativeButton(R.string.Cancel, new DialogInterface.OnClickListener() {
                                                    @Override
                                                    public void onClick(DialogInterface dialog, int which) {
                                                        dialog.dismiss();
                                                    }
                                                }).show();
                                    }
                                }
                                lastClick = System.currentTimeMillis();
                            }
                        }
                        //To change body of implemented methods use File | Settings | File Templates.
                    }
                });
                final HashMap<Integer, ImageLoaderConfiguration> imageLoaders = new HashMap<Integer, ImageLoaderConfiguration>();
                gallery.setTag(imageLoaders);
                final int HEIGHT = (int)(((Activity)getContext()).getWindow().getWindowManager().getDefaultDisplay().getHeight() * imageHeightPercent);
                gallery.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, HEIGHT + 20));
                gallery.setAdapter(new BaseAdapter() {
                    @Override
                    public int getCount() {
                        return images.size();
                    }

                    @Override
                    public Object getItem(int i) {
                        return i;  //To change body of implemented methods use File | Settings | File Templates.
                    }

                    @Override
                    public long getItemId(int i) {
                        return i;  //To change body of implemented methods use File | Settings | File Templates.
                    }

                    @Override
                    public View getView(int i, View view, ViewGroup viewGroup) {
                        if (view == null) {
                            view = ((Activity) getContext()).getLayoutInflater().inflate(R.layout.image_holder, null);
                            MyWebView wv = (MyWebView) view.findViewById(R.id.webview);
                            gallery.addInitializedWebView(wv);
                        }
                        view.setMinimumHeight(HEIGHT);
                        ImageLoaderConfiguration imageLoader = imageLoaders.get(i);
                        if (imageLoader == null) {
                            imageLoader = new ImageLoaderConfiguration(new ImageLoader(images.get(i), view, HEIGHT, ll, false, images.size() > 1), false);
                            imageLoaders.put(i, imageLoader);
                        } else if (imageLoader.loader == null) {
                            imageLoader = new ImageLoaderConfiguration(new ImageLoader(images.get(i), view, HEIGHT, ll, imageLoader.useOriginal, images.size() > 1), imageLoader.useOriginal);
                            imageLoaders.put(i, imageLoader);
                        } else {
                            imageLoader.loader.setDestinationView(view);
                        }
                        if (view == null) {
                            System.out.println("oh");
                        }
                        //view.measure(2000, 2000);
                        MainActivity.restyleChildrenOrWidget(view);
                        return view;
                    }
                });
            }


            /*
            ImageView i = (ImageView) v.findViewById(R.id.icon);
            if (jmsg.User != null && jmsg.User.Avatar != null) {
            i.setImageDrawable((Drawable) jmsg.User.Avatar);
            } else {
            i.setImageResource(R.drawable.ic_user_32);
            }
             */

        } else {
            if (v == null || !(v instanceof TextView)) {
                LayoutInflater vi = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                v = vi.inflate(R.layout.preference_category, null);
            }

            ((TextView) v).setTextSize(neededTextSize);

            if (jmsg.Text != null) {
                ((TextView) v).setText(jmsg.Text);
            } else {
                ((TextView) v).setText("");
            }
        }
        MainActivity.restyleChildrenOrWidget(v);

        return v;
    }

    public static float getDefaultTextSize(Context context) {
        if (defaultTextSize == 0)
            defaultTextSize = new TextView(context).getTextSize();
        return defaultTextSize;
    }

    private ArrayList<String> filterImagesUrls(ArrayList<String> urls) {
        ArrayList<String> retval = new ArrayList<String>();
        for (String url : urls) {
            String urlLower = url.toLowerCase();
            if (isValidImageURl(urlLower)) {
                retval.add(ImageURLConvertor.convertURLToDownloadable(url));
            }
        }
        return retval;
    }

    private String trimRequest(String url) {
        int ask = url.indexOf("?");
        if (ask != -1) {
            url = url.substring(0, ask);
        }
        return url;
    }

    public boolean isValidImageURl(String urlLower) {
        if (!otherImages) {
            if (urlLower.contains("juick.com/")) {
                // ok
            } else {
                return false;
            }
        }
        if (isHTMLSource(urlLower)) return true;
        if (imageLoadMode.contains("japroxy") && HTMLImageSourceDetector.isHTMLImageSource0(urlLower)) {
            return true;
        }
        return ValidImageURLDetector.isValidImageURL0(urlLower);
    }

    private boolean isHTMLSource(String url) {
        if (!indirectImages) return false;
        if (imageLoadMode.contains("japroxy")) return false;
        return HTMLImageSourceDetector.isHTMLImageSource0(url);
    }

    @Override
    public boolean isEnabled(int position) {
        JuickMessage jmsg = getItem(position);
        boolean retval = jmsg != null && jmsg.User != null && jmsg.MID > 0;
        return retval;
    }



    public void addDisabledItem(String txt, int position) {
        allItemsEnabled = false;
        JuickMessage jmsg = new JuickMessage();
        jmsg.Text = txt;
        try {
            insert(jmsg, position);
        } catch (Exception e) {
            // fsck them all
        }
    }

    public void recycleView(View view) {
        if (view instanceof ViewGroup) {
            ListRowRuntime lrr = (ListRowRuntime)view.getTag();
            if (lrr != null) {
                if (lrr.userpicListener != null) {
                    UserpicStorage.instance.removeListener(lrr.jmsg.User.UID, lrr.pictureSize, lrr.userpicListener);
                }
            }
            ViewGroup vg = (ViewGroup)view;
            if (vg.getChildCount() > 1 && vg.getChildAt(1) instanceof WebViewGallery) {
                // our view
                WebViewGallery gallery = (WebViewGallery)vg.getChildAt(1);
                Object tag = gallery.getTag();
                if (tag instanceof HashMap) {
                    HashMap<Integer, ImageLoader> loaders = (HashMap<Integer, ImageLoader>)tag;
                    for (ImageLoader imageLoader : loaders.values()) {
                        imageLoader.terminate();
                    }
                }
                gallery.cleanup();
                gallery.setTag(null);
                vg.removeViewAt(1);
            }
        }
    }

    public static class ParsedMessage {
        SpannableStringBuilder textContent;
        ArrayList<String> urls;
        public int messageNumberStart, messageNumberEnd;
        public int userNameStart, userNameEnd;
        public StyleSpan userNameBoldSpan;
        public Object userpicSpan;
        public ForegroundColorSpan userNameColorSpan;
        boolean read;

        ParsedMessage(SpannableStringBuilder textContent, ArrayList<String> urls) {
            this.textContent = textContent;
            this.urls = urls;
        }

        public void markAsRead() {
            this.read = true;
            textContent.removeSpan(userNameBoldSpan);
            textContent.removeSpan(userNameColorSpan);
            if (messageNumberStart != -1) {
                textContent.setSpan(new StrikethroughSpan(), messageNumberStart, messageNumberEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                textContent.setSpan(new ForegroundColorSpan(colorTheme.getColor(ColorsTheme.ColorKey.USERNAME_READ, 0xFFc84e4e)), userNameStart, userNameEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }

    public void setScale(float scale) {
        textScale *= scale;
        textScale = Math.max(0.5f, Math.min(textScale, 2.0f));
        sp.edit().putString(PREFERENCES_SCALE, ""+textScale).commit();
    }

    public static ColorsTheme.ColorTheme colorTheme = null;
    public static ColorsTheme.ColorTheme getColorTheme(Context ctx) {
        if (colorTheme == null)
            colorTheme = new ColorsTheme.ColorTheme(ctx);
        return colorTheme;
    }

    public static ParsedMessage formatMessageText(final Context ctx, final JuickMessage jmsg, boolean condensed) {
        if (jmsg.parsedText != null) {
            return jmsg.parsedText;    // was parsed before
        }
        getColorTheme(ctx);
        SpannableStringBuilder ssb = new SpannableStringBuilder();
        int spanOffset = 0;
        if (jmsg.continuationInformation != null) {
            ssb.append(jmsg.continuationInformation+"\n");
            ssb.setSpan(new StyleSpan(Typeface.ITALIC), spanOffset, ssb.length()-1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        //
        // NAME
        //
        spanOffset = ssb.length();
        String name = '@' + jmsg.User.UName;
        int userNameStart = ssb.length();
        ssb.append(name);
        int userNameEnd = ssb.length();
        StyleSpan userNameBoldSpan;
        ForegroundColorSpan userNameColorSpan;
        ssb.setSpan(userNameBoldSpan = new StyleSpan(Typeface.BOLD), spanOffset, ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.setSpan(userNameColorSpan = new ForegroundColorSpan(colorTheme.getColor(ColorsTheme.ColorKey.USERNAME, 0xFFC8934E)), spanOffset, ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.append(' ');
        spanOffset = ssb.length();

        if (!condensed) {
            //
            // TAGS
            //
            String tags = jmsg.getTags();
            ssb.append(tags + "\n");
            if (tags.length() > 0)
                ssb.setSpan(new ForegroundColorSpan(colorTheme.getColor(ColorsTheme.ColorKey.TAGS, 0xFF0000CC)), spanOffset,
                        ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            spanOffset = ssb.length();
        }
        if (jmsg.translated) {
            //
            // 'translated'
            //
            ssb.append("translated: ");
            ssb.setSpan(new ForegroundColorSpan(colorTheme.getColor(ColorsTheme.ColorKey. TRANSLATED_LABEL, 0xFF4ec856)), spanOffset, ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), spanOffset, ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            spanOffset = ssb.length();
        }
        int messageNumberStart = -1, messageNumberEnd = -1;
        if (showNumbers(ctx) && !condensed) {
            //
            // numbers
            //
            if (jmsg.RID > 0) {
                ssb.append("/"+jmsg.RID);
                if (jmsg.replyTo != 0) {
                    ssb.append("->"+jmsg.replyTo);
                }
                ssb.append(" ");
            } else {
                messageNumberStart = ssb.length();
                ssb.append("#"+jmsg.MID);
                messageNumberEnd = ssb.length();
                ssb.append(" ");
            }
            ssb.setSpan(new ForegroundColorSpan(colorTheme.getColor(ColorsTheme.ColorKey.MESSAGE_ID, 0xFFa0a5bd)), spanOffset, ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            spanOffset = ssb.length();
        }
        //
        // MESSAGE BODY
        //
        String txt = jmsg.Text;
        if (!condensed) {
            if (jmsg.Photo != null) {
                txt = jmsg.Photo + "\n" + txt;
            }
            if (jmsg.Video != null) {
                txt = jmsg.Video + "\n" + txt;
            }
        }
        ssb.append(txt);
        // Highlight links http://example.com/
        ArrayList<ExtractURLFromMessage.FoundURL> foundURLs = ExtractURLFromMessage.extractUrls(txt);
        ArrayList<String> urls = new ArrayList<String>();
        for (ExtractURLFromMessage.FoundURL foundURL : foundURLs) {
            ssb.setSpan(new ForegroundColorSpan(colorTheme.getColor(ColorsTheme.ColorKey.URLS, 0xFF0000CC)), spanOffset + foundURL.getStart(), spanOffset + foundURL.getEnd(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            urls.add(foundURL.getUrl());
        }
        // bold italic underline
        setStyleSpans(ssb, spanOffset, Typeface.BOLD, "*");
        setStyleSpans(ssb, spanOffset, Typeface.ITALIC, "/");
        setStyleSpans(ssb, spanOffset, UnderlineSpan.class, "_");
        txt = ssb.subSequence(spanOffset, ssb.length()).toString(); // ssb was modified in between


        // Highlight nick
        String accountName = Utils.getAccountName(ctx).toLowerCase();
        int scan = spanOffset;
        String nickScanArea = ssb.toString().toLowerCase()+" ";
        while(true) {
            int myNick = nickScanArea.indexOf("@" + accountName, scan);
            if (myNick != -1) {
                if (!isNickPart(nickScanArea.charAt(myNick + accountName.length() + 1))) {
                    ssb.setSpan(new BackgroundColorSpan(colorTheme.getColor(ColorsTheme.ColorKey.USERNAME_ME, 0xFF938e00)), myNick-1, myNick + accountName.length()+2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                scan = myNick + 1;
            } else {
                break;
            }
        }

        // Highlight messages #1234
        int pos = 0;
        Matcher m = msgPattern.matcher(txt);
        while (m.find(pos)) {
            ssb.setSpan(new ForegroundColorSpan(colorTheme.getColor(ColorsTheme.ColorKey.URLS, 0xFF0000CC)), spanOffset + m.start(), spanOffset + m.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            pos = m.end();
        }

        if (!condensed) {
            //
            // TIME
            //
            int rightPartOffset = spanOffset = ssb.length();

            DateFormat df = new SimpleDateFormat("HH:mm dd/MMM/yy");
            df.setTimeZone(TimeZone.getDefault());
            String date = df.format(jmsg.Timestamp);
            ssb.append("\n" + date + " ");

            ssb.setSpan(new ForegroundColorSpan(colorTheme.getColor(ColorsTheme.ColorKey.DATE, 0xFFAAAAAA)), spanOffset, ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

            spanOffset = ssb.length();

            //
            // Number of REPLIES
            //
            if (jmsg.replies > 0) {
                String replies = Replies + jmsg.replies;
                ssb.append("  " + replies + " ");
                ssb.setSpan(new ForegroundColorSpan(colorTheme.getColor(ColorsTheme.ColorKey.NUMBER_OF_COMMENTS, 0xFFC8934E)), spanOffset, ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            // right align
            ssb.setSpan(new AlignmentSpan.Standard(Alignment.ALIGN_OPPOSITE), rightPartOffset, ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        LeadingMarginSpan.LeadingMarginSpan2 userpicSpan = null;
        if (showUserpics(ctx)) {
            if (jmsg.messagesSource != null && jmsg.User.UID != 0) {
                userpicSpan = new LeadingMarginSpan.LeadingMarginSpan2() {
                    @Override
                    public int getLeadingMarginLineCount() {
                        return 2;  //To change body of implemented methods use File | Settings | File Templates.
                    }

                    @Override
                    public int getLeadingMargin(boolean first) {
                        if (first) {
                            return (int) (2 * getDefaultTextSize(ctx) * textScale);
                        } else {
                            return 0;
                        }
                    }

                    @Override
                    public void drawLeadingMargin(Canvas c, Paint p, int x, int dir, int top, int baseline, int bottom, CharSequence text, int start, int end, boolean first, Layout layout) {
                        if (top == 0) {
                            Bitmap userpic = jmsg.messagesSource.getUserpic(jmsg.User.UID, getLeadingMargin(true));
                            if (userpic != null) {
                                c.drawBitmap(userpic, x, top, null);
                            }
                        }
                    }
                };
                ssb.setSpan(userpicSpan, 0, ssb.length(), 0);
            }
        }
        ParsedMessage parsedMessage = new ParsedMessage(ssb, urls);
        parsedMessage.userpicSpan = userpicSpan;
        parsedMessage.userNameBoldSpan = userNameBoldSpan;
        parsedMessage.userNameColorSpan = userNameColorSpan;
        parsedMessage.userNameStart = userNameStart;
        parsedMessage.userNameEnd = userNameEnd;
        parsedMessage.messageNumberStart = messageNumberStart;
        parsedMessage.messageNumberEnd = messageNumberEnd;
        return parsedMessage;
    }

    private static void setStyleSpans(SpannableStringBuilder ssb, int ssbOffset, Object what, String styleChar) {
        // easing bounds checking
        String txt = ssb.subSequence(ssbOffset, ssb.length()).toString();
        txt = " "+txt+" ";
        ssbOffset-=1;
        //
        int scan =  0;
        int cnt = 0;
        while(cnt++ < 20) {        // don't need bugs in production.
            int ix = txt.indexOf(styleChar, scan);
            if (ix < 0) break;
            if (" \n".indexOf(txt.charAt(ix-1)) != -1) {
                int ix2 = txt.indexOf(styleChar, ix+1);
                if (ix2 < 0)
                    break;
                if (" \n".indexOf(txt.charAt(ix2+1)) == -1  // not ends with space
                        || txt.substring(ix, ix2).indexOf("\n") != -1) {    // spans several lines
                    scan = ix2;         // not ok
                    continue;
                } else {
                    CharacterStyle span = null;
                    // found needed stuff
                    if (what instanceof Integer && (((Integer)what)== Typeface.BOLD)) {
                        span = new StyleSpan(Typeface.BOLD);
                    }
                    if (what instanceof Integer && (((Integer)what)== Typeface.ITALIC)) {
                        span = new StyleSpan(Typeface.ITALIC);
                    }
                    if (what == UnderlineSpan.class) {
                        span = new UnderlineSpan();
                    }
                    if (span != null && ix2 - ix > 1) {
                        ssb.delete(ssbOffset + ix, ssbOffset + ix + 1); // delete styling char
                        txt = stringDelete(txt, ix, ix + 1);
                        ix2--;  // moves, too
                        ssb.delete(ssbOffset + ix2, ssbOffset + ix2 + 1); // second char deleted
                        txt = stringDelete(txt, ix2, ix2 + 1);
                        ssb.setSpan(span, ssbOffset + ix, ssbOffset + ix2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                    scan = ix2;
                }
            }
        }
    }

    private static String stringDelete(String txt, int from, int to) {
        return txt.substring(0, from) + txt.substring(to);
    }

    public static boolean isNickPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '@';
    }

    private ParsedMessage formatFirstMessageText(JuickMessage jmsg) {
        SpannableStringBuilder ssb = new SpannableStringBuilder();
        String tags = jmsg.getTags();
        if (tags.length() > 0) {
            tags += "\n";
        }
        String txt = jmsg.Text;
        if (jmsg.Photo != null) {
            txt = jmsg.Photo + "\n" + txt;
        }
        if (jmsg.Video != null) {
            txt = jmsg.Video + "\n" + txt;
        }
        ssb.append(tags + txt);
        if (tags.length() > 0) {
            ssb.setSpan(new ForegroundColorSpan(colorTheme.getColor(ColorsTheme.ColorKey.TAGS, 0xFF0000CC)), 0, tags.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        int paddingt = tags.length();
        ArrayList<ExtractURLFromMessage.FoundURL> foundURLs = ExtractURLFromMessage.extractUrls(txt);
        ArrayList<String> urls = new ArrayList<String>();
        for (ExtractURLFromMessage.FoundURL foundURL : foundURLs) {
            ssb.setSpan(new ForegroundColorSpan(colorTheme.getColor(ColorsTheme.ColorKey.URLS, 0xFF0000CC)), paddingt + foundURL.getStart(), paddingt + foundURL.getEnd(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            urls.add(foundURL.getUrl());
        }
        return new ParsedMessage(ssb, urls);
    }

    public void addAllMessages(ArrayList<JuickMessage> messages) {
        for (JuickMessage message : messages) {
            boolean canAdd = true;
            if (message.RID > 0) {
                // don't add duplicate replies coming from any source (XMPP/websocket)
                int limit = getCount();

                for(int q=0; q<limit; q++) {
                    JuickMessage item = getItem(q);
                    if (item.RID == message.RID) {
                        canAdd = false;     // already there
                        break;
                    }
                }
            }
            if (canAdd) {
                add(message);
            }
        }
    }

    public static class ImageLoaderConfiguration {
        ImageLoader loader;
        boolean useOriginal;

        ImageLoaderConfiguration(ImageLoader loader, boolean useOriginal) {
            this.loader = loader;
            this.useOriginal = useOriginal;
        }
    }

    class ImageLoader {

        private View imageHolder;
        String url;
        Activity activity;
        int imageW, imageH = -1;
        int destHeight;
        LinearLayout listRow;
        boolean loading;
        boolean terminated;
        int totalRead = 0;
        String status;
        TextView progressBarText;
        DefaultHttpClient httpClient;
        HttpGet httpGet;
        boolean notOnlyImage;

        String suffix;

        public ImageLoader(final String url, View imageHolder, int destHeight, LinearLayout listRow, final boolean forceOriginalImage, boolean notOnlyImage) {
            this.url = url;
            this.listRow = listRow;
            this.destHeight = destHeight;
            this.notOnlyImage = notOnlyImage;
            activity = (Activity)getContext();
            this.imageHolder = imageHolder;
            updateUIBindings();
            final File destFile = getDestFile();
            suffix = getSuffix(destFile);
            if (destFile.exists()) {
                updateWebView(destFile);
            } else {
                progressBarText.setText("Connect..");
                httpClient = new DefaultHttpClient();
                String loadURl = url;

                if (!forceOriginalImage && !isHTMLSource(url)) {
                    loadURl = setupProxyForURL(url);
                }
                try {
                    httpGet = new HttpGet(loadURl);
                    loading = true;
                    View progressBar = imageHolder.findViewById(R.id.progressbar);
                    progressBar.setVisibility(View.VISIBLE);
                    TextView progressBarText = (TextView) imageHolder.findViewById(R.id.progressbar_text);
                    progressBarText.setVisibility(View.VISIBLE);
                    final WebView webView = (WebView) imageHolder.findViewById(R.id.webview);
                    webView.setVisibility(View.GONE);
                    new Thread("Image downloader") {
                        @Override
                        public void run() {
                            try {

                                httpClient.execute(httpGet, new ResponseHandler<HttpResponse>() {
                                    @Override
                                    public HttpResponse handleResponse(HttpResponse response) throws ClientProtocolException, IOException {
                                        updateStatus("Load..");
                                        if (response.getStatusLine().getStatusCode() != 200) {
                                            destFile.delete();
                                            updateStatus("ERR:" + response.getStatusLine().getStatusCode());
                                            return response;
                                        }
                                        HttpEntity entity = response.getEntity();
                                        String type = "image";
                                        Header contentType = entity.getContentType();
                                        if (contentType != null)
                                            type = contentType.getValue();
                                        long len = entity.getContentLength();
                                        String maybeTotalSize = "K";
                                        if (len > 0) {
                                            maybeTotalSize = "/"+(len/1024)+"K";
                                        }
                                        InputStream content = entity.getContent();
                                        long l = System.currentTimeMillis();
                                        StringBuffer sb = new StringBuffer();
                                        if (content != null) {
                                            OutputStream outContent = new FileOutputStream(destFile);
                                            byte[] buf = new byte[4096];
                                            while (true) {
                                                int rd = content.read(buf);
                                                if (rd <= 0) break;
                                                totalRead += rd;
                                                if (terminated)
                                                    break;
                                                outContent.write(buf, 0, rd);
                                                if (type.equals("text/html")) {
                                                    try {
                                                        sb.append(new String(buf, 0, rd));
                                                    } catch (Exception e) {
                                                        // invalid encoding goes here
                                                    }
                                                }
                                                if (System.currentTimeMillis() - l > 300) {
                                                    updateStatus("Load.." + (totalRead / 1024) + maybeTotalSize +" - " + suffix);
                                                    l = System.currentTimeMillis();
                                                }
                                            }
                                            content.close();
                                            outContent.close();
                                            updateStatus("Done.." + (totalRead / 1024) + "K");
                                            if (terminated) {
                                                destFile.delete();
                                            } else {
                                                if (type.startsWith("text")) {
                                                    String imageURL = ExtractImageURLFromHTML.extractImageURLFromHTML(sb);
                                                    if (imageURL != null) {
                                                        suffix = "jpg";
                                                        totalRead = 0;
                                                        if (!forceOriginalImage)
                                                            imageURL = setupProxyForURL(imageURL);
                                                        updateStatus("Img load starts..");
                                                        try {
                                                            httpGet = new HttpGet(imageURL);
                                                            httpClient.execute(httpGet, this);
                                                        } catch (IOException e) {
                                                            updateStatus("Error: " + e);
                                                        }
                                                        return null;
                                                    } else {
                                                        updateStatus("Invalid HTML");
                                                    }
                                                }
                                            }
                                            loading = false;
                                            if (!terminated) {
                                                handler.post(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        updateWebView(destFile);
                                                    }
                                                });
                                            }
                                        }
                                        return null;
                                    }
                                });

                            } catch (Exception e) {
                                updateStatus("Error:"+e);
                            }
                        }
                    }.start();
                } catch (Exception e) {
                    updateStatus("Error: "+e.toString());
                }
            }
        }


        private String setupProxyForURL(String url) {
            String loadURl = url;
            if (imageLoadMode.contains("japroxy")) {
                final int HEIGHT = (int)(((Activity)getContext()).getWindow().getWindowManager().getDefaultDisplay().getHeight() * imageHeightPercent);
                final int WIDTH = (int)(((Activity)getContext()).getWindow().getWindowManager().getDefaultDisplay().getWidth());
                String host = "ja.ip.rt.ru:8080";
                //String host = "192.168.1.77:8080";
                loadURl  = "http://"+host+"/img?url=" + Uri.encode(url)+"&height="+HEIGHT+"&width="+WIDTH;
            }
            if (imageLoadMode.contains("weserv")) {
                final int HEIGHT = (int)(((Activity)getContext()).getWindow().getWindowManager().getDefaultDisplay().getHeight() * imageHeightPercent);
                // WESERV.NL
                if (url.startsWith("https://")) {
                    loadURl  = "http://images.weserv.nl/?url=ssl:" + Uri.encode(url.substring(8))+"&h="+HEIGHT+"&q=0.5";
                } else if (url.startsWith("http://")) {
                    loadURl  = "http://images.weserv.nl/?url=" + Uri.encode(url.substring(7))+"&h="+HEIGHT+"&q=0.5";
                } else  {
                    // keep url
                }
            }
            if (imageLoadMode.contains("fastun") && proxyLogin.length() > 0 && proxyPassword.length() > 0) {
                httpClient.getCredentialsProvider().setCredentials(new AuthScope("fastun.com",7000), new UsernamePasswordCredentials(proxyLogin, proxyPassword));
                HttpHost proxy = new HttpHost("fastun.com", 7000);
                httpClient.getParams().setParameter(ConnRoutePNames.DEFAULT_PROXY, proxy);
            }
            return loadURl;
        }

        private void updateStatus(final String text) {
            this.status = text;
            handler.post(new Runnable() {
                @Override
                public void run() {
                    progressBarText.setText(text);
                }
            });
        }

        private File getDestFile() {
            File cacheDir = new File(getContext().getCacheDir(), "image_cache");
            cacheDir.mkdirs();
            return new File(cacheDir, urlToFileName(url));
        }

        private void updateWebView(final File destFile) {
            final WebView webView = (WebView) imageHolder.findViewById(R.id.webview);
            if (webView != null) {      // concurrent remove
                if (destFile.getPath().equals(webView.getTag())) return;    // already there
                webView.setTag(destFile.getPath());
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(destFile.getPath(), opts);
                imageW = opts.outWidth;
                imageH = opts.outHeight;
                int screenWidth = ((Activity) getContext()).getWindow().getWindowManager().getDefaultDisplay().getWidth();
                double scaleFactor = (((double)destHeight) / imageH);
                double screenWidthLimiter = notOnlyImage ? 0.8 : 0.96;   // gallery scrolls poorly if image width = screen width
                if (scaleFactor * imageW > (screenWidth * screenWidthLimiter)) {
                    // image does not fit by width, gallery will crop it. Preventing this:
                    scaleFactor = (((double)(screenWidth * screenWidthLimiter)) / imageW);
                    final Gallery gallery = (Gallery) listRow.getChildAt(1);        // gallery is here, maybe
                    if (gallery.getAdapter().getCount() == 1) { // no other children
                        //final LinearLayout content = (LinearLayout)listRow.findViewById(R.id.content);
                        // happened to be smaller than user requested height
                        int newHeiht = (int) (imageH * scaleFactor);
                        imageHolder.setMinimumHeight(newHeiht);
                    }
                }
                final int scaledW = (int) (imageW * scaleFactor);
                final int scaledH = (int) (imageH * scaleFactor);
                final boolean sameThread = Thread.currentThread() == mUiThread;
                if (imageHolder != null && imageH > 0) {
                    View content = imageHolder.findViewById(R.id.content);
                    content.getLayoutParams().width = scaledW;
                    content.getLayoutParams().height = scaledH;
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (webView.getTag(MyWebView.DESTROYED_TAG) == null) {
                                View progressBar = imageHolder.findViewById(R.id.progressbar);
                                progressBar.setVisibility(View.GONE);
                                TextView progressBarText = (TextView) imageHolder.findViewById(R.id.progressbar_text);
                                progressBarText.setVisibility(View.GONE);
                                webView.setVisibility(View.VISIBLE);
                                webView.getLayoutParams().height = destHeight;
                                webView.setInitialScale(100);

                                StringBuilder content = new StringBuilder();
                                content.append(String.format("<html><head>"));
                                //content.append(String.format("<meta name=\"viewport\" content=\"initial-scale=%f; maximum-scale=%f; user-scalable=0;\" />", scaleFactor, scaleFactor));
                                content.append(String.format("</head><body style='padding: 0px; margin: 0px'>"));
                                content.append(String.format("<img src='%s' width=%d height=%d/>",
                                        CachedImageContentProvider.constructUri(destFile.getName()),
                                        scaledW, scaledH
                                ));
                                content.append(String.format("</body></html>"));
                                try {
                                    webView.loadData(content.toString(), "text/html", "UTF-8");
                                    webView.setOnTouchListener(new View.OnTouchListener() {
                                        @Override
                                        public boolean onTouch(View view, MotionEvent motionEvent) {
                                            return true;
                                        }
                                    });
                                    if (!sameThread) {
                                        resetAdapter();
            //                            imageHolder.invalidate();
            //                            gallery.invalidate();
            //                            imageHolder.requestLayout();
                                    }
                                } catch (Exception e) {
                                    Log.e("JuickAdvanced","Exception in MyWebView.loaddata:  "+e.toString());
                                    // that webview is probably destroyed some way
                                }
                            }
                        }
                    });
                }
            }
        }

        private void resetAdapter() {
            final Gallery gallery = (Gallery) listRow.getChildAt(1);
            Parcelable parcelable = gallery.onSaveInstanceState();
            gallery.setAdapter(gallery.getAdapter());
            gallery.onRestoreInstanceState(parcelable);
        }

        private String urlToFileName(String url) {
            StringBuilder sb = new StringBuilder();
            String curl = Uri.encode(url);
            int ask = url.indexOf('?');
            if (ask != -1) {
                String suburl = url.substring(0, ask);
                if (isValidImageURl(suburl)) {
                    int q = suburl.lastIndexOf('.');
                    // somefile.jpg?zz=true   -> somefile.jpg?zz=true.jpg   -> somefile.jpg_zz_true.jpg
                    url += suburl.substring(q);
                }
            }
            for(int i=0; i<curl.length(); i++) {
                char c = curl.charAt(i);
                if (Character.isLetterOrDigit(c) || c == '.') {
                    sb.append(c);
                } else {
                    sb.append("_");
                }
            }
            return sb.toString();
        }

        public void setDestinationView(View destinationView) {
            this.imageHolder = destinationView;
            updateUIBindings();
            File destFile = getDestFile();
            if (destFile.exists() && !loading) {
                updateWebView(destFile);
            }
            if (status != null)
                updateStatus(status);
        }

        private void updateUIBindings() {
            progressBarText = (TextView) imageHolder.findViewById(R.id.progressbar_text);
            View progressBar = imageHolder.findViewById(R.id.progressbar);
            View.OnClickListener canceller = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    updateStatus("Cancelled");
                    if (httpGet != null)
                        httpGet.abort();
                }
            };
            progressBarText.setOnClickListener(canceller);
            progressBar.setOnClickListener(canceller);

        }

        public void terminate() {
            terminated = true;
        }

        public CharSequence info() {
            File destFile = getDestFile();
            String suffix = getSuffix(destFile);
            return "Image: "+imageW+"x"+imageH+" size "+(destFile.length()/1024)+"K "+ suffix;
        }

        private String getSuffix(File destFile) {
            String path = destFile.getPath();
            String suffix = path.substring(path.lastIndexOf(".") + 1);
            if (suffix.length() > 4) {
                suffix = suffix.substring(0, 4);
            }
            return suffix;
        }
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        instanceCount--;
    }
}
