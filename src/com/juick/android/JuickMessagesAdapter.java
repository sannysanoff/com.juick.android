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
import android.content.SharedPreferences;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.text.Layout;
import android.text.Layout.Alignment;
import android.text.style.*;
import android.util.Log;
import android.view.*;
import android.widget.*;
import com.juickadvanced.data.juick.JuickMessage;
import android.content.Context;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import com.juick.android.juick.JuickComAuthorizer;
import com.juickadvanced.R;

import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.juickadvanced.imaging.*;
import org.acra.ACRA;
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
    private final boolean noProxyOnWifi;
    public int type;
    private boolean allItemsEnabled = true;

    public static Set<String> filteredOutUsers;
    private final double imageHeightPercent;
    static String imageLoadMode;
    private final String proxyPassword;
    private final String proxyLogin;
    private final Thread mUiThread;

    Utils.ServiceGetter<DatabaseService> databaseGetter;
    Utils.ServiceGetter<XMPPService> xmppServiceGetter;
    private boolean trackLastRead;
    private int subtype;
    static boolean indirectImages;
    static boolean otherImages;
    static boolean feedlyFonts;
    static boolean compactComments;
    private final boolean hideGif;
    Handler handler;
    boolean enableScaleByGesture;
    ImagePreviewHelper imagePreviewHelper;
    static boolean russian;


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

    static long lastDontKeepParsed;
    static boolean _dontKeepParsed;
    public static boolean dontKeepParsed(Context ctx) {
        if (System.currentTimeMillis() - lastDontKeepParsed > 3000) {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
            _dontKeepParsed = sp.getBoolean("dontKeepParsed", false);
            lastDontKeepParsed = System.currentTimeMillis();
        }
        return _dontKeepParsed;
    }

    static long lastCachedShowUserpics;
    static boolean _showUserpics;
    static boolean _wrapUserpics;
    public static boolean showUserpics(Context ctx) {
        if (System.currentTimeMillis() - lastCachedShowUserpics > 3000) {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
            _showUserpics = sp.getBoolean("showUserpics", true);
            _wrapUserpics = sp.getBoolean("wrapUserpics", true);
            try {
                Class.forName(LeadingMarginSpan.LeadingMarginSpan2.class.getName());
            } catch (Throwable e) {
                _showUserpics = false;
            }
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
        noProxyOnWifi = sp.getBoolean("imageproxy.skipOnWifi", false);
        indirectImages = sp.getBoolean("image.indirect", true);
        otherImages = sp.getBoolean("image.other", true);
        hideGif = sp.getBoolean("image.hide_gif", false);
        feedlyFonts = sp.getBoolean("feedlyFonts", false);
        compactComments = sp.getBoolean("compactComments", false);
        russian = XMPPIncomingMessagesActivity.isRussian();
        enableScaleByGesture = sp.getBoolean("enableScaleByGesture", true);
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
        UserpicStorage.AvatarID avatarID;
        UserpicStorage.Listener userpicListener;

        ListRowRuntime(JuickMessage jmsg, UserpicStorage.AvatarID avatarID, int pictureSize) {
            this.jmsg = jmsg;
            this.avatarID = avatarID;
            this.pictureSize = pictureSize;
        }

        public void removeListenerIfExists() {
            if (userpicListener != null) {
                UserpicStorage.instance.removeListener(avatarID, pictureSize, userpicListener);
                userpicListener = null;
            }
        }
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final JuickMessage jmsg = getItem(position);
        View v = convertView;
        if (v != null) {
            v.requestLayout();
        }

        float neededTextSize = getDefaultTextSize(getContext()) * textScale;
        if (jmsg.User != null && jmsg.Text != null) {
            if (v == null || !(v instanceof LinearLayout)) {
                LayoutInflater vi = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                v = vi.inflate(R.layout.listitem_juickmessage, null);
//                v.setWillNotCacheDrawing(true);       78 msec/iter with cache turned off (40 msec with on)
                final PressableLinearLayout sll = (PressableLinearLayout)v;
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
            final LinearLayout ll = (LinearLayout)v;
            final MyTextView t = (MyTextView) v.findViewById(R.id.text);
            final TextView compactDate = (TextView) v.findViewById(R.id.compactDate);
            final MyTextView pret = (MyTextView) v.findViewById(R.id.pretext);
            final ListRowRuntime lrr = new ListRowRuntime(jmsg, null, 0);
            final ListRowRuntime prelrr = jmsg.contextPost != null ? new ListRowRuntime(jmsg.contextPost, null, 0) : null;
            ListRowRuntime oldlrr = (ListRowRuntime)t.getTag();
            if (oldlrr != null) {
                oldlrr.removeListenerIfExists();
            }
            t.setTag(lrr);
            t.setTextSize(neededTextSize);
            pret.setTag(prelrr);
            pret.setTextSize(neededTextSize);

            final ParsedMessage parsedMessage;
            ParsedMessage parsedPreMessage = null;
            if (type == TYPE_THREAD && jmsg.getRID() == 0) {
                parsedMessage = formatFirstMessageText(jmsg);
            } else {
                parsedMessage = formatMessageText(getContext(), jmsg, false);
            }
            if (jmsg.contextPost != null) {
                String savedText = jmsg.contextPost.Text;
                if (savedText.length() > 200) {
                    jmsg.contextPost.Text = savedText.substring(0, 200)+".......";
                }
                parsedPreMessage = formatMessageText(getContext(), jmsg.contextPost, false);
                jmsg.contextPost.Text = savedText;
            }

            final ImageView userPic = (ImageView) v.findViewById(R.id.userpic);
            final ImageView preuserPic = (ImageView) v.findViewById(R.id.preuserpic);
            startLoadUserPic(jmsg, t, lrr, parsedMessage, userPic);
            if (parsedPreMessage != null) {
                startLoadUserPic(jmsg.contextPost, pret, prelrr, parsedPreMessage, preuserPic);
            }
            if (type != TYPE_THREAD && trackLastRead && jmsg.getRID() < 1) {
                databaseGetter.getService(new Utils.ServiceGetter.Receiver<DatabaseService>() {
                    @Override
                    public void withService(DatabaseService service) {
                        service.getMessageReadStatus(jmsg.getMID(), new Utils.Function<Void, DatabaseService.MessageReadStatus>() {
                            @Override
                            public Void apply(DatabaseService.MessageReadStatus messageReadStatus) {
                                if (messageReadStatus.read) {
                                    handler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            ListRowRuntime tag = (ListRowRuntime)t.getTag();
                                            if (tag.jmsg.getMID() == jmsg.getMID()) {
                                                // still valid
                                                parsedMessage.markAsRead(getContext());
//                                                t.blockLayoutRequests = true;
                                                t.setText(parsedMessage.textContent);
//                                                t.layout(t.getLeft(), t.getTop(), t.getRight(), t.getBottom());
//                                                t.blockLayoutRequests = false;
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
                        service.removeMessages(jmsg.getMID(), true);
                    }
                });

            }
            if (!parsedMessage.read) {  // could be set synchronously by the getMessageReadStatus, above
                t.setText(parsedMessage.textContent);
                compactDate.setText(parsedMessage.compactDate);
            }
            if (parsedPreMessage != null) {
                pret.setText(parsedPreMessage.textContent);
                pret.setVisibility(View.VISIBLE);
                preuserPic.setVisibility(View.VISIBLE);
            } else {
                pret.setVisibility(View.GONE);
                preuserPic.setVisibility(View.GONE);
            }
            final ArrayList<String> images = filterImagesUrls(parsedMessage.urls);
            if (images.size() > 0 && !imageLoadMode.equals("off")) {
                final ImageGallery gallery = new ImageGallery(getContext());
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
                                // Toast.makeText(getContext(), imageLoader.loader.info(), Toast.LENGTH_SHORT).show();
                                if (System.currentTimeMillis() - lastClick < 500) {
                                    if (imagePreviewHelper != null) {
                                        MyImageView myImageView = (MyImageView)gallery.getSelectedView().findViewById(R.id.non_webview);
                                        if (myImageView.getVisibility() == View.VISIBLE) {
                                            imagePreviewHelper.startWithImage(myImageView.getDrawable(), imageLoader.loader.info().toString(), imageLoader.loader.url, false);
                                        }
                                    }
                                    lastClick = 0;  // triple click prevention
                                    return;
                                }
                                lastClick = System.currentTimeMillis();
                            }
                        }
                        //To change body of implemented methods use File | Settings | File Templates.
                    }
                });
                final HashMap<Integer, ImageLoaderConfiguration> imageLoaders = new HashMap<Integer, ImageLoaderConfiguration>();
                gallery.setTag(imageLoaders);
                final Display dd = ((Activity) getContext()).getWindow().getWindowManager().getDefaultDisplay();
                final int HEIGHT = (int)(Math.max(dd.getHeight(), dd.getWidth()) * imageHeightPercent);
                gallery.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, HEIGHT + 20));
                gallery.setAdapter(new BaseAdapter() {

                    LRUCache<Integer, View> views = new LRUCache<Integer, View>(5, gallery) {

                        @Override
                        void cleanupValue(View view) {
                            cleanupRow(view);
                        }

                    };

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
                        final View cached = views.get(i);
                        if (cached != null) return cached;
                        if (view == null) {
                            view = ((Activity) getContext()).getLayoutInflater().inflate(R.layout.image_holder, null);
                            views.put(i, view);
                            ImageView wv = (ImageView) view.findViewById(R.id.non_webview);
                            gallery.addInitializedView(wv);
                            GIFView gv = (GIFView) view.findViewById(R.id.gif_view);
                            gallery.addInitializedView(gv);
                        } else {
                            cleanupRow(view);
                        }
                        view.setMinimumHeight(HEIGHT);
                        ImageLoaderConfiguration imageLoader = imageLoaders.get(i);
                        if (imageLoader == null) {
                            imageLoader = new ImageLoaderConfiguration(new ImageLoader(gallery, images.get(i), view, HEIGHT, ll, false, images.size() > 1), false);
                            imageLoaders.put(i, imageLoader);
                        } else if (imageLoader.loader == null) {
                            imageLoader = new ImageLoaderConfiguration(new ImageLoader(gallery, images.get(i), view, HEIGHT, ll, imageLoader.useOriginal, images.size() > 1), imageLoader.useOriginal);
                            imageLoaders.put(i, imageLoader);
                        } else {
                            imageLoader.loader.setDestinationView(view);
                        }
                        if (view == null) {
                            System.out.println("oh");
                        }
                        //view.measure(2000, 2000);
                        MainActivity.restyleChildrenOrWidget(view, true);
                        return view;
                    }

                    private void cleanupRow(View view) {
                        ImageView wv = (ImageView) view.findViewById(R.id.non_webview);
                        if (wv != null) {
                            final Drawable drawable = wv.getDrawable();
                            if (drawable instanceof BitmapDrawable) {
                                BitmapDrawable bd = (BitmapDrawable)drawable;
                                BitmapCounts.releaseBitmap(bd.getBitmap());
                                wv.setImageDrawable(null);
                            }
                        }
                    }
                });
            }


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
        MainActivity.restyleChildrenOrWidget(v, true);

        return v;
    }

    static boolean useDirectImageMode(Context ctx) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
        boolean noProxyOnWifi = sp.getBoolean("imageproxy.skipOnWifi", false);
        if (noProxyOnWifi) {
            String connectivityTypeKey = ConnectivityChangeReceiver.getCurrentConnectivityTypeKey(ctx);
            if (connectivityTypeKey != null && connectivityTypeKey.startsWith("WIFI")) return true;
        }
        return false;
    }

    private void startLoadUserPic(final JuickMessage jmsg, final MyTextView t, final ListRowRuntime lrr, ParsedMessage parsedMessage, final ImageView userPic) {
        if (parsedMessage.userpicSpan != null) {
            lrr.pictureSize = (int)(parsedMessage.userpicSpan.getLeadingMargin(true) * 0.9);
            userPic.getLayoutParams().width = lrr.pictureSize;
            userPic.getLayoutParams().height = lrr.pictureSize;
            int padding = ((parsedMessage.userpicSpan.getLeadingMargin(true)) - lrr.pictureSize)/2;
            userPic.setPadding(padding, padding, padding, padding);

            final UserpicStorage.AvatarID avatarId = getAvatarId(jmsg);
            lrr.avatarID = avatarId;

            lrr.userpicListener = new UserpicStorage.Listener() {
                @Override
                public void onUserpicReady(UserpicStorage.AvatarID id, int size) {
                    lrr.removeListenerIfExists();
                    final Bitmap userpic = UserpicStorage.instance.getUserpic(getContext(), avatarId, lrr.pictureSize, lrr.userpicListener);
                    if (userpic != null) {
                        ((Activity)getContext()).runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                ListRowRuntime tag = (ListRowRuntime)t.getTag();
                                if (tag.jmsg.getMID() == jmsg.getMID()) {
                                    userPic.setImageBitmap(userpic);
                                }
                                //To change body of implemented methods use File | Settings | File Templates.
                            }
                        });
                    }
                }
            };
            Bitmap userpic = UserpicStorage.instance.getUserpic(getContext(), avatarId, lrr.pictureSize, lrr.userpicListener);
            if (userpic == null) {
                // until it loads, we will put proper size placeholder (later re-layout minimization)
                userpic = UserpicStorage.instance.getUserpic(getContext(), UserpicStorage.NO_AVATAR, lrr.pictureSize, null);
            }
            userPic.setImageBitmap(userpic);    // can be null
        } else {
            userPic.setImageBitmap(null);
        }
    }

    public static UserpicStorage.AvatarID getAvatarId(JuickMessage jmsg) {
        if (jmsg == null)
            return UserpicStorage.NO_AVATAR;
        MicroBlog microBlog = MainActivity.getMicroBlog(jmsg);
        if (microBlog == null)
            return UserpicStorage.NO_AVATAR;
        return microBlog.getAvatarID(jmsg);
    }

    public static float getDefaultTextSize(Context context) {
        if (defaultTextSize == 0) {
            TextView textView = new TextView(context);
            defaultTextSize = textView.getTextSize();
        }
        return defaultTextSize;
    }

    static HashMap<Double, Integer> lineHeights = new HashMap<Double, Integer>();
    public static float getLineHeight(Context context, double scale) {
        Integer integer = lineHeights.get(scale);
        if (integer == null) {
            TextView textView = new TextView(context);
            textView.setTextSize((int)(getDefaultTextSize(context)*scale));
            integer = textView.getLineHeight();
            lineHeights.put(scale, integer);
        }
        return integer;
    }

    private ArrayList<String> filterImagesUrls(ArrayList<String> urls) {
        ArrayList<String> retval = new ArrayList<String>();
        for (String url : urls) {
            String urlLower = url.toLowerCase();
            if (isValidImageURl(getContext(), urlLower)) {
                if (hideGif && url.toLowerCase().contains(".gif")) continue;
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

    public static boolean isValidImageURl(Context ctx, String urlLower) {
        if (!otherImages) {
            if (urlLower.contains("juick.com/")) {
                // ok
            } else {
                return false;
            }
        }
        if (isHTMLSource(ctx, urlLower)) return true;
        if (imageLoadMode.contains("japroxy") && HTMLImageSourceDetector.isHTMLImageSource0(urlLower)) {
            return true;
        }
        return ValidImageURLDetector.isValidImageURL0(urlLower);
    }

    private static boolean isHTMLSource(Context ctx, String url) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
        String imageLoadMode = sp.getString("image.loadMode", "off");
        boolean indirectImages = sp.getBoolean("image.indirect", true);
        if (!indirectImages) return false;
        if (imageLoadMode.contains("japroxy") && !useDirectImageMode(ctx)) return false;
        return HTMLImageSourceDetector.isHTMLImageSource0(url);
    }

    @Override
    public boolean isEnabled(int position) {
        JuickMessage jmsg = getItem(position);
        boolean retval = jmsg != null && jmsg.User != null && jmsg.getMID() != null;
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
                    lrr.removeListenerIfExists();
                }
            }
            ViewGroup vg = (ViewGroup)view;
            if (vg.getChildCount() > 1 && vg.getChildAt(1) instanceof ImageGallery && vg instanceof PressableLinearLayout) {
                PressableLinearLayout pll = (PressableLinearLayout)vg;
                // our view
                ImageGallery gallery = (ImageGallery)vg.getChildAt(1);
                Object tag = gallery.getTag();
                if (tag instanceof HashMap) {
                    HashMap<Integer, ImageLoaderConfiguration> loaders = (HashMap<Integer, ImageLoaderConfiguration>)tag;
                    for (ImageLoaderConfiguration imageLoader : loaders.values()) {
                        imageLoader.loader.terminate();
                    }
                }
                pll.blockLayoutRequests = true;
                gallery.cleanup();
                gallery.setTag(null);
                vg.removeViewAt(1);
                vg.measure(
                        View.MeasureSpec.makeMeasureSpec(vg.getRight()- vg.getLeft(), View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                        );
                vg.layout(vg.getLeft(), vg.getTop(), vg.getRight(), vg.getTop() + vg.getMeasuredHeight());
                pll.blockLayoutRequests = false;
            }
        }
    }

    public static class ParsedMessage {
        SpannableStringBuilder textContent;
        SpannableStringBuilder compactDate;
        ArrayList<String> urls;
        public int messageNumberStart, messageNumberEnd;
        public int userNameStart, userNameEnd;
        public StyleSpan userNameBoldSpan;
        public LeadingMarginSpan.LeadingMarginSpan2 userpicSpan;
        public ForegroundColorSpan userNameColorSpan;
        boolean read;

        ParsedMessage(SpannableStringBuilder textContent, ArrayList<String> urls) {
            this.textContent = textContent;
            this.urls = urls;
        }

        public void markAsRead(Context ctx) {
            this.read = true;
            textContent.removeSpan(userNameBoldSpan);
            textContent.removeSpan(userNameColorSpan);
            if (messageNumberStart != -1) {
                textContent.setSpan(new StrikethroughSpan(), messageNumberStart, messageNumberEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                textContent.setSpan(new ForegroundColorSpan(getColorTheme(ctx).getColor(ColorsTheme.ColorKey.USERNAME_READ, 0xFFc84e4e)), userNameStart, userNameEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }

    public void setScale(float scale, float x, float y, ListView listView) {
//        View v = findViewForCoordinates(listView, x, y);
//        if (v instanceof MyImageView) {
//            System.out.println("OK");
//        }
        if (enableScaleByGesture) {
            textScale *= scale;
            textScale = Math.max(0.5f, Math.min(textScale, 2.0f));
            sp.edit().putString(PREFERENCES_SCALE, ""+textScale).commit();
        }
    }

    Rect hitRect = new Rect();

    private View findViewForCoordinates(ViewGroup listView, float x, float y) {
        int childCount = listView.getChildCount();
        for(int i=childCount-1; i>=0; i--) {
            View child = listView.getChildAt(i);
            child.getHitRect(hitRect);
            if (hitRect.top < y && hitRect.top + hitRect.bottom > y && hitRect.left < x && hitRect.right > x) {
                View retval = null;
                if (child instanceof ViewGroup) {
                    retval = findViewForCoordinates((ViewGroup)child, x - hitRect.left, y - hitRect.top);
                }
                if (retval == null)
                    retval = child;
                return retval;
            }
        }
        return null;
    }

    public static ColorsTheme.ColorTheme colorTheme = null;
    public static ColorsTheme.ColorTheme getColorTheme(Context ctx) {
        if (colorTheme == null)
            colorTheme = new ColorsTheme.ColorTheme(ctx);
        return colorTheme;
    }

    public void invalidateItemsRendering() {
        for(int i=0; i<getCount(); i++) {
            getItem(i).parsedText = null;
        }
        notifyDataSetInvalidated();
    }

    public static ParsedMessage formatMessageText(final Context ctx, final JuickMessage jmsg, boolean condensed) {
        if (jmsg.parsedText != null) {
            return (ParsedMessage)jmsg.parsedText;    // was parsed before
        }
        getColorTheme(ctx);
        SpannableStringBuilder ssb = new SpannableStringBuilder();
        int spanOffset = 0;
        final boolean isMainMessage = jmsg.getRID() == 0;
        final boolean doCompactComment = !isMainMessage && compactComments;
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
            if (feedlyFonts)
                tags = tags.toUpperCase();
            ssb.append(tags + "\n");
            if (tags.length() > 0) {
                ssb.setSpan(new ForegroundColorSpan(colorTheme.getColor(ColorsTheme.ColorKey.TAGS, 0xFF0000CC)), spanOffset,
                        ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                if (feedlyFonts) {
                    ssb.setSpan(new CustomTypefaceSpan("", JuickAdvancedApplication.dinWebPro), spanOffset,
                            ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
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
            if (isMainMessage) {
                messageNumberStart = ssb.length();
                ssb.append(jmsg.getDisplayMessageNo());
                messageNumberEnd = ssb.length();
            } else {
                ssb.append("/"+jmsg.getRID());
                if (jmsg.getReplyTo() != 0) {
                    ssb.append("->"+jmsg.getReplyTo());
                }
            }
            ssb.append(" ");
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
            setSSBSpan(ssb, new ForegroundColorSpan(colorTheme.getColor(ColorsTheme.ColorKey.URLS, 0xFF0000CC)), spanOffset + foundURL.getStart(), spanOffset + foundURL.getEnd(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            urls.add(foundURL.getUrl());
        }
        // bold italic underline
        setStyleSpans(ssb, spanOffset, Typeface.BOLD, "*");
        setStyleSpans(ssb, spanOffset, Typeface.ITALIC, "/");
        setStyleSpans(ssb, spanOffset, UnderlineSpan.class, "_");
        txt = ssb.subSequence(spanOffset, ssb.length()).toString(); // ssb was modified in between


        // Highlight nick
        String accountName = JuickComAuthorizer.getJuickAccountName(ctx);
        if (accountName != null) {
            accountName = accountName.toLowerCase();
            int scan = spanOffset;
            String nickScanArea = ssb.toString().toLowerCase()+" ";
            while(true) {
                int myNick = nickScanArea.indexOf("@" + accountName, scan);
                if (myNick != -1) {
                    if (!isNickPart(nickScanArea.charAt(myNick + accountName.length() + 1))) {
                        setSSBSpan(ssb, new BackgroundColorSpan(colorTheme.getColor(ColorsTheme.ColorKey.USERNAME_ME, 0xFF938e00)), myNick - 1, myNick + accountName.length() + 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                    scan = myNick + 1;
                } else {
                    break;
                }
            }
        }

        // Highlight messages #1234
        int pos = 0;
        Matcher m = msgPattern.matcher(txt);
        while (m.find(pos)) {
            ssb.setSpan(new ForegroundColorSpan(colorTheme.getColor(ColorsTheme.ColorKey.URLS, 0xFF0000CC)), spanOffset + m.start(), spanOffset + m.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            pos = m.end();
        }

        SpannableStringBuilder compactDt = null;
        if (!condensed) {

            // found messages count (search results)
            if (jmsg.myFoundCount != 0 || jmsg.hisFoundCount != 0) {
                ssb.append("\n");
                int where = ssb.length();
                if (jmsg.myFoundCount != 0) {
                    ssb.append(ctx.getString(R.string.MyReplies_)+jmsg.myFoundCount+"  ");
                }
                if (jmsg.hisFoundCount != 0) {
                    ssb.append(ctx.getString(R.string.UserReplies_)+jmsg.hisFoundCount);
                }
                ssb.setSpan(new ForegroundColorSpan(colorTheme.getColor(ColorsTheme.ColorKey.TRANSLATED_LABEL, 0xFF4ec856)), where, ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }


            int rightPartOffset = spanOffset = ssb.length();

            if (!doCompactComment) {
                //
                // TIME (bottom of message)
                //

                try {
                    DateFormat df = new SimpleDateFormat("HH:mm dd/MMM/yy");
                    String date = jmsg.Timestamp != null ? df.format(jmsg.Timestamp) : "[bad date]";
                    ssb.append("\n" + date + " ");
                } catch (Exception e) {
                    ssb.append("\n[fmt err] ");
                }

                ssb.setSpan(new ForegroundColorSpan(colorTheme.getColor(ColorsTheme.ColorKey.DATE, 0xFFAAAAAA)), spanOffset, ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                compactDt = new SpannableStringBuilder();
                try {
                    if (true || jmsg.deltaTime!= Long.MIN_VALUE) {
                        compactDt.append(XMPPIncomingMessagesActivity.toRelaviteDate(jmsg.Timestamp.getTime(), russian));
                    } else {
                        DateFormat df = new SimpleDateFormat("HH:mm dd/MMM/yy");
                        String date = jmsg.Timestamp != null ? df.format(jmsg.Timestamp) : "[bad date]";
                        compactDt.append(date);
                    }
                } catch (Exception e) {
                    compactDt.append("\n[fmt err] ");
                }
                compactDt.setSpan(new ForegroundColorSpan(colorTheme.getColor(ColorsTheme.ColorKey.DATE, 0xFFAAAAAA)), 0, compactDt.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

            }

            spanOffset = ssb.length();

            //
            // Number of REPLIES
            //
            if (jmsg.replies > 0) {
                String replies = Replies + jmsg.replies;
                ssb.append(" " + replies);
                ssb.setSpan(new ForegroundColorSpan(colorTheme.getColor(ColorsTheme.ColorKey.NUMBER_OF_COMMENTS, 0xFFC8934E)), spanOffset, ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                ssb.setSpan(new WrapTogetherSpan(){}, spanOffset, ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (!doCompactComment) {
                // right align
                ssb.setSpan(new AlignmentSpan.Standard(Alignment.ALIGN_OPPOSITE), rightPartOffset, ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }


        LeadingMarginSpan.LeadingMarginSpan2 userpicSpan = null;
        if (showUserpics(ctx) && !condensed) {
            userpicSpan = new LeadingMarginSpan.LeadingMarginSpan2() {
                @Override
                public int getLeadingMarginLineCount() {
                    if (_wrapUserpics) {
                        return 2;
                    } else {
                        return 22222;
                    }
                }

                @Override
                public int getLeadingMargin(boolean first) {
                    if (first) {
                        return (int) (2 * getLineHeight(ctx, (double)textScale));
                    } else {
                        return 0;
                    }
                }

                @Override
                public void drawLeadingMargin(Canvas c, Paint p, int x, int dir, int top, int baseline, int bottom, CharSequence text, int start, int end, boolean first, Layout layout) {
                }
            };
            ssb.setSpan(userpicSpan, 0, ssb.length(), 0);
        }
        ParsedMessage parsedMessage = new ParsedMessage(ssb, urls);
        parsedMessage.userpicSpan = userpicSpan;
        parsedMessage.userNameBoldSpan = userNameBoldSpan;
        parsedMessage.userNameColorSpan = userNameColorSpan;
        parsedMessage.userNameStart = userNameStart;
        parsedMessage.userNameEnd = userNameEnd;
        parsedMessage.messageNumberStart = messageNumberStart;
        parsedMessage.messageNumberEnd = messageNumberEnd;
        parsedMessage.compactDate = compactDt;
        return parsedMessage;
    }

    private static void setSSBSpan(SpannableStringBuilder ssb, Object span, int start, int end, int flags) {
        if (end > ssb.length()) {
            end = ssb.length();
        }
        if (start > ssb.length()) return;
        if (start >= end) return;
        ssb.setSpan(span, start, end, flags);
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
        if (feedlyFonts) {
            tags = tags.toUpperCase();
        }
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
            if (feedlyFonts) {
                ssb.setSpan(new CustomTypefaceSpan("", JuickAdvancedApplication.dinWebPro), 0, tags.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
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
        ArrayList<JuickMessage> toAdd = new ArrayList<JuickMessage>();
        for (JuickMessage message : messages) {
            boolean canAdd = true;
            if (message.getRID() > 0) {
                // don't add duplicate replies coming from any source (XMPP/websocket)
                int limit = getCount();

                for(int q=0; q<limit; q++) {
                    JuickMessage item = getItem(q);
                    if (item.getRID() == message.getRID()) {
                        canAdd = false;     // already there
                        break;
                    }
                }
            }
            if (canAdd) {
                toAdd.add(message);
            }
        }
        try {
            addAll(toAdd);
        } catch (Throwable th) {
            // unimplemented?
            for (JuickMessage juickMessage : toAdd) {
                add(juickMessage);
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
        String status;
        TextView progressBarText;
        DefaultHttpClient httpClient;
        HttpGet httpGet;
        boolean notOnlyImage;
        boolean deletedInvalid;
        ImageGallery gallery;

        String suffix;

        public ImageLoader(ImageGallery gallery, final String url, View imageHolder, int destHeight, LinearLayout listRow, final boolean forceOriginalImage, boolean notOnlyImage) {
            this.gallery = gallery;
            this.url = url;
            this.listRow = listRow;
            this.destHeight = destHeight;
            this.notOnlyImage = notOnlyImage;
            activity = (Activity)getContext();
            this.imageHolder = imageHolder;
            updateUIBindings();
            final File destFile = getDestFile(getContext(), url);
            suffix = getSuffix(destFile);
            loadFromFileOrNetwork(url, imageHolder, forceOriginalImage, destFile);
        }

        private void loadFromFileOrNetwork(final String url, final View imageHolder, final boolean forceOriginalImage, final File destFile) {
            if (destFile.exists() && destFile.length() > 1024) {
                new Thread() {
                    @Override
                    public void run() {
                        try {
                            final Bitmap bitmap = BitmapFactory.decodeFile(destFile.getPath());
                            if (bitmap == null) {
                                if (!deletedInvalid) {
                                    destFile.delete();  // bad bad image
                                    updateStatus("Deleted bad existing image.");
                                    deletedInvalid = true;
                                    handler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            loadFromFileOrNetwork(url, imageHolder, forceOriginalImage, destFile);
                                        }
                                    });
                                } else {
                                    updateStatus("Bad cached image ;(");
                                }
                            } else {
                                handler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            updateImageView(destFile, bitmap);
                                        } catch (OutOfMemoryError e) {
                                            handleOOM(activity, e);
                                            updateStatus("Out of memory");
                                        }
                                    }
                                });
                            }
                        } catch (OutOfMemoryError e) {
                            handleOOM(activity, e);
                            updateStatus("Out of memory");
                        }
                    }
                }.start();
            } else {
                progressBarText.setText("Connect..");
                httpClient = new DefaultHttpClient();
                String loadURl = url;

                if (!forceOriginalImage && !isHTMLSource(getContext(), url)) {
                    loadURl = setupProxyForURL(url);
                }
                try {
                    final File tmpFile = new File(destFile.getPath()+".tmp");
                    final long tmpFileLength = tmpFile.exists() ? tmpFile.length() : -1;  // flag for partial download
                    httpGet = new HttpGet(loadURl);
                    if (tmpFileLength != -1) {
                        httpGet.addHeader("Range","bytes="+tmpFileLength+"-");
                    }
                    loading = true;
                    final View progressBar = imageHolder.findViewById(R.id.progressbar);
                    progressBar.setVisibility(View.VISIBLE);
                    TextView progressBarText = (TextView) imageHolder.findViewById(R.id.progressbar_text);
                    progressBarText.setVisibility(View.VISIBLE);
                    final ImageView imageView = (ImageView) imageHolder.findViewById(R.id.non_webview);
                    imageView.setVisibility(View.INVISIBLE);
                    final GIFView gifView = (GIFView) imageHolder.findViewById(R.id.gif_view);
                    gifView.setVisibility(View.GONE);
                    new Thread("Image downloadhttpGeter") {
                        @Override
                        public void run() {
                            try {

                                httpClient.execute(httpGet, new ResponseHandler<HttpResponse>() {
                                    @Override
                                    public HttpResponse handleResponse(HttpResponse response) throws ClientProtocolException, IOException {
                                        updateStatus("Load..");
                                        int fileOffset = 0;
                                        HttpEntity entity = response.getEntity();
                                        long len = entity != null ? entity.getContentLength() : 0;
                                        int statusCode = response.getStatusLine().getStatusCode();
                                        if (tmpFileLength != -1 && statusCode == 206) { // partial content
                                            final Header[] headers = response.getHeaders("Content-Range");
                                            if (headers == null || headers.length != 1) {
                                                // spec failed
                                                destFile.delete();
                                                updateStatus("bad partial content");
                                                return response;
                                            }
                                            final String partialContent = headers[0].getValue();
                                            final Matcher matcher = Pattern.compile("bytes (.*)-(.*)/(.*)").matcher(partialContent);
                                            if( matcher.find()) {
                                                fileOffset = Integer.parseInt(matcher.group(1));
                                                len = Integer.parseInt(matcher.group(3));
                                            } else {
                                                updateStatus("bad partial content");
                                                return response;
                                            }
                                            // gut
                                        } else if (statusCode != 200) {
                                            destFile.delete();
                                            updateStatus("ERR:" + response.getStatusLine().getStatusCode());
                                            return response;
                                        }
                                        String type = "image";
                                        Header contentType = entity.getContentType();
                                        if (contentType != null)
                                            type = contentType.getValue();
                                        String maybeTotalSize = "K";
                                        if (len > 0) {
                                            maybeTotalSize = "/" + (len / 1024) + "K";
                                        }
                                        InputStream content = entity.getContent();
                                        long l = System.currentTimeMillis();
                                        StringBuffer sb = new StringBuffer();
                                        if (content != null) {
                                            RandomAccessFile raf = new RandomAccessFile(tmpFile, "rw");
                                            try {
                                                byte[] buf = new byte[4096];
                                                while (true) {
                                                    int rd = content.read(buf);
                                                    if (rd <= 0) break;
                                                    if (terminated)
                                                        break;
                                                    raf.seek(fileOffset);
                                                    raf.write(buf, 0, rd);
                                                    fileOffset += rd;
                                                    if (type.equals("text/html")) {
                                                        try {
                                                            sb.append(new String(buf, 0, rd));
                                                        } catch (Exception e) {
                                                            // invalid encoding goes here
                                                        }
                                                    }
                                                    if (System.currentTimeMillis() - l > 300) {
                                                        updateStatus("Load.." + (fileOffset / 1024) + maybeTotalSize + " - " + suffix);
                                                        l = System.currentTimeMillis();
                                                    }
                                                }
                                            } finally {
                                                content.close();
                                                raf.close();
                                            }
                                            if (len < 1 || len == fileOffset) {        // complete file or unknown length
                                                destFile.delete();
                                                tmpFile.renameTo(destFile);
                                            } else {
                                                if (!terminated) {
                                                    updateStatus("Incomplete file");
                                                    return response;
                                                }
                                            }
                                            updateStatus("Done.." + (fileOffset / 1024) + "K" + (terminated ? " (t)" : ""));
                                            handler.post(new Runnable() {
                                                @Override
                                                public void run() {
                                                    progressBar.setVisibility(View.GONE);
                                                }
                                            });
                                            if (terminated) {
                                                updateStatus("Terminated?");
                                                destFile.delete();
                                            } else {
                                                if (type.startsWith("text")) {
                                                    String imageURL = ExtractImageURLFromHTML.extractImageURLFromHTML(sb);
                                                    if (imageURL != null) {
                                                        ImageLoader.this.url = imageURL;    // for image preview helper
                                                        suffix = "jpg";
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
                                                        terminated = true;
                                                    }
                                                }
                                            }
                                            loading = false;
                                            if (!terminated) {
                                                try {

                                                    Bitmap bmp = BitmapFactory.decodeFile(destFile.getPath());
                                                    final Bitmap finalBmp = bmp;
                                                    handler.post(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            try {
                                                                updateImageView(destFile, finalBmp);
                                                            } catch (OutOfMemoryError e) {
                                                                updateStatus("Out of memory");
                                                                handleOOM(activity, e);
                                                            }
                                                        }
                                                    });
                                                } catch (OutOfMemoryError e) {
                                                    updateStatus("Out of memory");
                                                    handleOOM(activity, e);
                                                }
                                            }
                                        }
                                        return null;
                                    }
                                });

                            } catch (Exception e) {
                                updateStatus("Error:" + e);
                            }
                        }
                    }.start();
                } catch (Exception e) {
                    updateStatus("Error: " + e.toString());
                }
            }
        }


        private String setupProxyForURL(String url) {
            if (useDirectImageMode(getContext())) {
                return url;
            }
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

        private void updateImageView(final File destFile, Bitmap bitmap) {
            final ImageView imageView = (ImageView) imageHolder.findViewById(R.id.non_webview);
            final GIFView gifView = (GIFView) imageHolder.findViewById(R.id.gif_view);
            if (imageView != null) {      // concurrent remove
                if (destFile.getPath().equals(imageView.getTag())) return;    // already there
                imageView.setTag(destFile.getPath());
                BitmapFactory.Options opts = new BitmapFactory.Options();
                boolean isGif = false;
                final byte[] arr = XMPPService.readFile(destFile, 1024);
                if (arr != null && arr.length >= 4) {
                    isGif = arr[0] == 'G' && arr[1] == 'I' && arr[2] == 'F' && arr[3] == '8';
                }
                if (isGif) {
                    if (bitmap != null) {
                        BitmapCounts.releaseBitmap(bitmap);
                        bitmap = null;
                    }
                }
                if (bitmap != null) {
                    imageW = bitmap.getWidth();
                    imageH = bitmap.getHeight();
                } else {
                    opts.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(destFile.getPath(), opts);
                    imageW = opts.outWidth;
                    imageH = opts.outHeight;
                }
                int screenWidth = ((Activity) getContext()).getWindow().getWindowManager().getDefaultDisplay().getWidth();
                double scaleFactor = (((double)destHeight) / imageH);
                double screenWidthLimiter = notOnlyImage ? 0.8 : 0.96;   // gallery scrolls poorly if image width = screen width
                if (scaleFactor * imageW > (screenWidth * screenWidthLimiter)) {
                    // image does not fit by width, gallery will crop it. Preventing this:
                    scaleFactor = ((screenWidth * screenWidthLimiter)) / imageW;
                    final Gallery gallery = (Gallery) listRow.getChildAt(1);        // gallery is here, maybe
                    if (gallery != null && gallery.getAdapter() != null && gallery.getAdapter().getCount() == 1) { // no other children
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
                    if (imageView.getTag(MyImageView.DESTROYED_TAG) == null) {
                        View progressBar = imageHolder.findViewById(R.id.progressbar);
                        progressBar.setVisibility(View.INVISIBLE);
                        TextView progressBarText = (TextView) imageHolder.findViewById(R.id.progressbar_text);
                        progressBarText.setVisibility(View.INVISIBLE);
                        imageView.setVisibility(!isGif ? View.VISIBLE: View.GONE);
                        gifView.setVisibility(isGif ? View.VISIBLE: View.GONE);
                        imageView.getLayoutParams().height = destHeight;
                        gifView.getLayoutParams().height = destHeight;

                        try {
                            gallery.blockLayoutRequest = true;
                            if (isGif) {
                                gifView.setMovieFile(destFile);
                            } else {
                                if (bitmap != null) {
                                    BitmapCounts.retainBitmap(bitmap);
                                    imageView.setImageDrawable(new BitmapDrawable(bitmap));
                                } else {
                                    imageView.setImageURI(Uri.fromFile(destFile));
                                }
                            }
                            gallery.blockLayoutRequest = false;
                            Rect rect = new Rect();
                            gallery.getHitRect(rect);
                            gallery.forceLayout();
                            gallery.measure(rect.right - rect.left, rect.bottom - rect.top);
                            gallery.layout(rect.left,  rect.top, rect.right, rect.bottom);
                            if (!sameThread) {
                                resetAdapter();
                            }
                        } catch (Exception e) {
                            Log.e("JuickAdvanced","Exception in MyImageView.loaddata:  "+e.toString());
                            // that webview is probably destroyed some way
                        }
                    }
                }
            }
        }

        private void resetAdapter() {
            final Gallery gallery = (Gallery) listRow.getChildAt(1);
            Parcelable parcelable = gallery.onSaveInstanceState();
            gallery.setAdapter(gallery.getAdapter());
            gallery.onRestoreInstanceState(parcelable);
        }


        public void setDestinationView(View destinationView) {
            this.imageHolder = destinationView;
            updateUIBindings();
            final File destFile = getDestFile(getContext(), url);
            if (destFile.exists() && !loading) {
                new Thread() {
                    @Override
                    public void run() {
                        try {
                            final Bitmap bitmap = BitmapFactory.decodeFile(destFile.getPath());
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        updateImageView(destFile, bitmap);
                                    } catch (OutOfMemoryError e) {
                                        handleOOM(activity, e);
                                        updateStatus("Out of memory");
                                    }
                                }
                            });
                        } catch (OutOfMemoryError e) {
                            handleOOM(activity, e);
                            updateStatus("Out of memory");
                        }
                    }
                }.start();
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
            File destFile = getDestFile(getContext(), url);
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

    private void handleOOM(final Activity activity, OutOfMemoryError e) {
        ACRA.getErrorReporter().handleException(new RuntimeException("OOM: " + XMPPControlActivity.getMemoryStatusString(), e));
        handler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(activity, "Out of memory", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        instanceCount--;
    }

    public static String urlToFileName(Context ctx, String url) {
        StringBuilder sb = new StringBuilder();
        String curl = Uri.encode(url);
        int ask = url.indexOf('?');
        if (ask != -1) {
            String suburl = url.substring(0, ask);
            if (isValidImageURl(ctx, suburl)) {
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
        String retval = sb.toString();
        if (retval.length() > 120) {
            String md5DigestForString = Utils.getMD5DigestForString(retval).replace("/","_");
            int q = retval.lastIndexOf('.');
            return "longname_"+md5DigestForString+retval.substring(q);
        }
        return retval;
    }

    public static File getDestFile(Context ctx, String url) {
        final File destFile;
        if (url.startsWith("file://")) {
            return new File("/"+new URLParser(url).getPathPart());
        } else {
            File cacheDir = new File(ctx.getCacheDir(), "image_cache");
            cacheDir.mkdirs();
            return new File(cacheDir, urlToFileName(ctx, url));
        }


    }

    public static String formatTimeDiff(long timediff, boolean russian) {
        StringBuilder sb = new StringBuilder();
        if (timediff < 0) {
            return russian ? "раньше" : "earlier";
        }
        timediff /= 1000;
        timediff /= 60;
        long minutes = timediff % 60;
        timediff /= 60;
        long hours = timediff % 24;
        timediff /= 24;
        long days = timediff;
        sb.append("+");
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
        if (sb.length() == 1) {
            sb.setLength(0);
            sb.append(russian ? "сразу" : "immed.");
        }
        return sb.toString();
    }



}
