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
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.text.Layout.Alignment;
import android.view.MotionEvent;
import android.webkit.WebView;
import android.widget.*;
import com.juick.android.api.JuickMessage;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.preference.PreferenceManager;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.AlignmentSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Log;
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

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;

/**
 *
 * @author Ugnich Anton
 */
public class JuickMessagesAdapter extends ArrayAdapter<JuickMessage> {

    private static final String PREFERENCES_SCALE = "messagesFontScale";
    public static final int TYPE_THREAD = 1;
    public static Pattern urlPattern = Pattern.compile("((?<=\\A)|(?<=\\s))(ht|f)tps?://[a-z0-9\\-\\.]+[a-z]{2,}/?[^\\s\\n]*", Pattern.CASE_INSENSITIVE);
    public static Pattern msgPattern = Pattern.compile("#[0-9]+");
//    public static Pattern usrPattern = Pattern.compile("@[a-zA-Z0-9\\-]{2,16}");
    private static String Replies;
    private int type;
    private boolean allItemsEnabled = true;
    private boolean isContinuationAdapter;

    private static Set<String> filteredOutUsers;
    private final double imageHeightPercent;
    private final String imageLoadMode;
    private final String proxyPassword;
    private final String proxyLogin;
    private final Thread mUiThread;



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
        if (System.currentTimeMillis() - lastCachedShowNumbers > 1000) {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
            _showNumbers = sp.getBoolean("showNumbers", false);
            lastCachedShowNumbers = System.currentTimeMillis();
        }
        return _showNumbers;
    }
    private SharedPreferences sp;
    private float defaultTextSize;
    private float textScale;

    public JuickMessagesAdapter(Context context, int type) {
        super(context, R.layout.listitem_juickmessage);
        sp = PreferenceManager.getDefaultSharedPreferences(context);
        mUiThread = Thread.currentThread();
        if (Replies == null) {
            Replies = context.getResources().getString(R.string.Replies_) + " ";
        }
        this.type = type;
        imageHeightPercent = Double.parseDouble(sp.getString("image.height_percent", "0.3"));
        imageLoadMode = sp.getString("image.loadMode", "off");
        proxyPassword = sp.getString("imageproxy.password", "");
        proxyLogin = sp.getString("imageproxy.login", "");
        defaultTextSize = new TextView(context).getTextSize();
        textScale = 1;
        try {
            textScale = Float.parseFloat(sp.getString(PREFERENCES_SCALE, "1.0"));
        } catch (Exception ex) {
            //
        }
    }

    public static ArrayList<JuickMessage> parseJSONpure(String jsonStr) {
        ArrayList<JuickMessage> messages = new ArrayList<JuickMessage>();
        if (jsonStr != null) {
            try {
                JSONArray json = new JSONArray(jsonStr);
                int cnt = json.length();
                for (int i = 0; i < cnt; i++) {
                    messages.add(JuickMessage.initFromJSON(json.getJSONObject(i)));
                }
            } catch (Exception e) {
                Log.e("initOpinionsAdapter", e.toString());
            }
        }
        return messages;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        JuickMessage jmsg = getItem(position);
        View v = convertView;

        if (jmsg.User != null && jmsg.Text != null) {
            if (v == null || !(v instanceof LinearLayout)) {
                LayoutInflater vi = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                v = vi.inflate(R.layout.listitem_juickmessage, null);
                TextView tv = (TextView) v.findViewById(R.id.text);
                float textSize = tv.getTextSize();
                try {
                    Float fontScale = Float.parseFloat(sp.getString(PREFERENCES_SCALE, "1.0"));
                    tv.setTextSize(textSize*fontScale);
                } catch (Exception e) {
                    //
                }
            }
            final LinearLayout ll = (LinearLayout)v;
            TextView t = (TextView) v.findViewById(R.id.text);
            t.setTextSize(defaultTextSize * textScale);

            final ParsedMessage parsedMessage;
            if (type == TYPE_THREAD && jmsg.RID == 0) {
                parsedMessage = formatFirstMessageText(jmsg);
            } else {
                parsedMessage = formatMessageText(getContext(), jmsg, position == 0 && isContinuationAdapter);
            }
            t.setText(parsedMessage.textContent);
            final ArrayList<String> images = filterImagesUrls(parsedMessage.urls);
            final Gallery gallery = (Gallery)ll.findViewById(R.id.gallery);
            gallery.setVisibility(View.VISIBLE);
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
                                    JuickMessageMenu.confirmAction(R.string.DownloadOriginal, (Activity)getContext(), true, new Runnable() {
                                        @Override
                                        public void run() {

                                            imageLoader.useOriginal = true;
                                            imageLoader.loader.getDestFile().delete();
                                            ImageLoader oldLoader = imageLoader.loader;
                                            imageLoader.loader = null;
                                            oldLoader.resetAdapter();
                                        }
                                    } );
                                }
                            }
                            lastClick = System.currentTimeMillis();
                        }
                    }
                    //To change body of implemented methods use File | Settings | File Templates.
                }
            });
            if (images.size() > 0 && !imageLoadMode.equals("off")) {
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
                        if (view == null)
                            view = ((Activity) getContext()).getLayoutInflater().inflate(R.layout.image_holder, null);
                        view.setMinimumHeight(HEIGHT);
                        ImageLoaderConfiguration imageLoader = imageLoaders.get(i);
                        if (imageLoader == null) {
                            imageLoader = new ImageLoaderConfiguration(new ImageLoader(images.get(i), view, HEIGHT, ll, false), false);
                            imageLoaders.put(i, imageLoader);
                        } else if (imageLoader.loader == null) {
                            imageLoader = new ImageLoaderConfiguration(new ImageLoader(images.get(i), view, HEIGHT, ll, imageLoader.useOriginal), imageLoader.useOriginal);
                            imageLoaders.put(i, imageLoader);
                        } else {
                            imageLoader.loader.setDestinationView(view);
                        }
                        if (view == null) {
                            System.out.println("oh");
                        }
                        //view.measure(2000, 2000);
                        return view;
                    }
                });
            } else {
                gallery.setAdapter(new BaseAdapter() {
                    @Override
                    public int getCount() {
                        return 0;  //To change body of implemented methods use File | Settings | File Templates.
                    }

                    @Override
                    public Object getItem(int i) {
                        return null;  //To change body of implemented methods use File | Settings | File Templates.
                    }

                    @Override
                    public long getItemId(int i) {
                        return 0;  //To change body of implemented methods use File | Settings | File Templates.
                    }

                    @Override
                    public View getView(int i, View view, ViewGroup viewGroup) {
                        return null;  //To change body of implemented methods use File | Settings | File Templates.
                    }
                });
                gallery.setVisibility(View.GONE);
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

            ((TextView) v).setTextSize(defaultTextSize * textScale);

            if (jmsg.Text != null) {
                ((TextView) v).setText(jmsg.Text);
            } else {
                ((TextView) v).setText("");
            }
        }

        return v;
    }

    private ArrayList<String> filterImagesUrls(ArrayList<String> urls) {
        ArrayList<String> retval = new ArrayList<String>();
        for (String url : urls) {
            String urlLower = url.toLowerCase();
            urlLower = trimRequest(urlLower);
            if (hasValidImageExtension(urlLower)) {
                retval.add(url);
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

    private boolean hasValidImageExtension(String urlLower) {
        return urlLower.endsWith(".png") || urlLower.endsWith(".gif") || urlLower.endsWith(".jpg") || urlLower.endsWith(".jpeg");
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
        insert(jmsg, position);
    }

    public void recycleView(View view) {
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup)view;
            if (vg.getChildCount() > 1 && vg.getChildAt(1) instanceof Gallery) {
                // our view
                Gallery gallery = (Gallery)vg.getChildAt(1);
                Object tag = gallery.getTag();
                if (tag instanceof HashMap) {
                    HashMap<Integer, ImageLoader> loaders = new HashMap<Integer, ImageLoader>();
                    for (ImageLoader imageLoader : loaders.values()) {
                        imageLoader.terminate();
                    }
                }
                gallery.setTag(null);
            }
        }
    }

    static class ParsedMessage {
        SpannableStringBuilder textContent;
        ArrayList<String> urls;

        ParsedMessage(SpannableStringBuilder textContent, ArrayList<String> urls) {
            this.textContent = textContent;
            this.urls = urls;
        }
    }
    public void setScale(float scale) {
        textScale *= scale;
        textScale = Math.max(0.5f, Math.min(textScale, 2.0f));
        sp.edit().putString(PREFERENCES_SCALE, ""+textScale).commit();
    }

    public static ParsedMessage formatMessageText(Context ctx, JuickMessage jmsg, boolean addContinuation) {
        SpannableStringBuilder ssb = new SpannableStringBuilder();
        int spanOffset = 0;
        if (addContinuation) {
            ctx.getResources().getString(R.string.ResumingFromLastTime);
            ssb.append("<< resuming from last time>>\n");
            ssb.setSpan(new StyleSpan(Typeface.ITALIC), spanOffset, spanOffset + ssb.length()-1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        spanOffset = ssb.length();
        String name = '@' + jmsg.User.UName;
        String tags = jmsg.getTags();
        String txt = jmsg.Text;
        if (jmsg.Photo != null) {
            txt = jmsg.Photo + "\n" + txt;
        }
        if (jmsg.Video != null) {
            txt = jmsg.Video + "\n" + txt;
        }
        ssb.append(name + ' ' + tags + "\n");
        ssb.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), spanOffset, spanOffset + name.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.setSpan(new ForegroundColorSpan(0xFFC8934E), spanOffset, spanOffset + name.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (tags.length() > 0)
            ssb.setSpan(new ForegroundColorSpan(0xFF0000CC), spanOffset + name.length() + 1,
                    spanOffset + name.length() + tags.length() + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);


        spanOffset = ssb.length();
        if (jmsg.translated) {
            ssb.append("translated: ");
            ssb.setSpan(new ForegroundColorSpan(0xFF4ec856), spanOffset, ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), spanOffset, ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        spanOffset = ssb.length();
        if (showNumbers(ctx)) {
            if (jmsg.RID > 0) {
                ssb.append("/"+jmsg.RID);
                if (jmsg.replyTo != 0) {
                    ssb.append("->"+jmsg.replyTo);
                }
                ssb.append(" ");
            } else {
                ssb.append("#"+jmsg.MID+" ");
            }
            ssb.setSpan(new ForegroundColorSpan(0xFFa0a5bd), spanOffset, ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        spanOffset = ssb.length();
        ssb.append(txt);
        // Highlight links http://example.com/
        int pos = 0;
        Matcher m = urlPattern.matcher(txt);
        ArrayList<String> urls = new ArrayList<String>();
        while (m.find(pos)) {
            urls.add(ssb.subSequence(spanOffset + m.start(), spanOffset + m.end()).toString());
            ssb.setSpan(new ForegroundColorSpan(0xFF0000CC), spanOffset + m.start(), spanOffset + m.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            pos = m.end();
        }

        // Highlight messages #1234
        pos = 0;
        m = msgPattern.matcher(txt);
        while (m.find(pos)) {
            ssb.setSpan(new ForegroundColorSpan(0xFF0000CC), spanOffset + m.start(), spanOffset + m.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            pos = m.end();
        }

        /*
        // Highlight usernames @username
        pos = 0;
        m = usrPattern.matcher(txt);
        while (m.find(pos)) {
        ssb.setSpan(new ForegroundColorSpan(0xFF0000CC), paddingt + m.start(), paddingt + m.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        pos = m.end();
        }
         */

        int rightPartOffset = spanOffset = ssb.length();

        DateFormat df = new SimpleDateFormat("HH:mm dd/MMM/yy");
        df.setTimeZone(TimeZone.getDefault());
        String date = df.format(jmsg.Timestamp);
        ssb.append("\n" + date + " ");

        ssb.setSpan(new ForegroundColorSpan(0xFFAAAAAA), spanOffset, ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        spanOffset = ssb.length();

        if (jmsg.replies > 0) {
            String replies = Replies + jmsg.replies;
            ssb.append("  " + replies + " ");
            ssb.setSpan(new ForegroundColorSpan(0xFFC8934E), spanOffset, ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        ssb.setSpan(new AlignmentSpan.Standard(Alignment.ALIGN_OPPOSITE), rightPartOffset, ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        return new ParsedMessage(ssb, urls);
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
            ssb.setSpan(new ForegroundColorSpan(0xFF000099), 0, tags.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        int paddingt = tags.length();
        int pos = 0;
        Matcher m = urlPattern.matcher(txt);
        ArrayList<String> urls = new ArrayList<String>();
        while (m.find(pos)) {
            ssb.setSpan(new ForegroundColorSpan(0xFF0000CC), paddingt + m.start(), paddingt + m.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            urls.add(ssb.subSequence(paddingt + m.start(), paddingt + m.end()).toString());
            pos = m.end();
        }
        return new ParsedMessage(ssb, urls);
    }

    public void addAllMessages(ArrayList<JuickMessage> messages) {
        for (JuickMessage message : messages) {
            if (getFilteredOutUsers(getContext()).contains(message.User.UName)) continue;
            add(message);
        }
    }

    public void setContinuationAdapter(boolean continuationAdapter) {
        isContinuationAdapter = continuationAdapter;
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


        public ImageLoader(final String url, View imageHolder, int destHeight, LinearLayout listRow, boolean forceOriginalImage) {
            this.url = url;
            this.listRow = listRow;
            this.destHeight = destHeight;
            activity = (Activity)getContext();
            this.imageHolder = imageHolder;
            updateUIBindings();
            final File destFile = getDestFile();
            final String suffix = getSuffix(destFile);
            if (destFile.exists()) {
                updateWebView(destFile);
            } else {
                progressBarText.setText("Connect..");
                httpClient = new DefaultHttpClient();
                String loadURl = url;

                if (!forceOriginalImage) {
                    if (imageLoadMode.contains("weserv")) {
                        final int HEIGHT = (int)(((Activity)getContext()).getWindow().getWindowManager().getDefaultDisplay().getHeight() * imageHeightPercent);
                        // WESERV.NL
                        if (url.startsWith("https://")) {
                            loadURl  = "http://images.weserv.nl/?url=ssl:" + Uri.encode(url.substring(8))+"&h="+HEIGHT+"&q=0.5";
                        } else if (url.startsWith("http://")) {
                            loadURl  = "http://images.weserv.nl/?url=" + Uri.encode(url.substring(7))+"&h="+HEIGHT+"&q=0.5";
                        } else  {
                            progressBarText.setText("bad scheme");
                            return;
                        }
                    }
                    if (imageLoadMode.contains("fastun") && proxyLogin.length() > 0 && proxyPassword.length() > 0) {
                        httpClient.getCredentialsProvider().setCredentials(new AuthScope("fastun.com",7000), new UsernamePasswordCredentials(proxyLogin, proxyPassword));
                        HttpHost proxy = new HttpHost("fastun.com", 7000);
                        httpClient.getParams().setParameter(ConnRoutePNames.DEFAULT_PROXY, proxy);
                    }
                }
                httpGet = new HttpGet(loadURl);
                loading = true;
                View progressBar = imageHolder.findViewById(R.id.progressbar);
                progressBar.setVisibility(View.VISIBLE);
                TextView progressBarText = (TextView) imageHolder.findViewById(R.id.progressbar_text);
                progressBarText.setVisibility(View.VISIBLE);
                final WebView webView = (WebView) imageHolder.findViewById(R.id.webview);
                webView.setVisibility(View.GONE);
                new Thread() {
                    @Override
                    public void run() {
                        try {

                            httpClient.execute(httpGet, new ResponseHandler<HttpResponse>() {
                                @Override
                                public HttpResponse handleResponse(HttpResponse response) throws ClientProtocolException, IOException {
                                    updateStatus("Load..");
                                    HttpEntity entity = response.getEntity();
                                    InputStream content = entity.getContent();
                                    long l = System.currentTimeMillis();
                                    if (content != null) {
                                        OutputStream outContent = new FileOutputStream(destFile);
                                        byte[] buf = new byte[4096];
                                        while (true) {
                                            int rd = content.read(buf);
                                            if (rd <= 0) break;
                                            totalRead += rd;
                                            outContent.write(buf, 0, rd);
                                            if (System.currentTimeMillis() - l > 300) {
                                                final int finalTotalRead = totalRead;
                                                updateStatus("Load.." + (finalTotalRead / 1024) + "K - " + suffix);
                                                l = System.currentTimeMillis();
                                            }
                                        }
                                        content.close();
                                        outContent.close();
                                        final int finalTotalRead1 = totalRead;
                                        updateStatus("Done.." + (finalTotalRead1 / 1024) + "K");
                                        loading = false;
                                        if (!terminated)
                                            updateWebView(destFile);
                                    }
                                    return null;
                                }
                            });

                        } catch (IOException e) {
                            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                        }
                    }
                }.start();
            }
        }

        private void updateStatus(final String text) {
            this.status = text;
            activity.runOnUiThread(new Runnable() {
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
            if (destFile.getPath().equals(webView.getTag())) return;    // already there
            webView.setTag(destFile.getPath());
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(destFile.getPath(), opts);
            imageW = opts.outWidth;
            imageH = opts.outHeight;
            int screenWidth = ((Activity) getContext()).getWindow().getWindowManager().getDefaultDisplay().getWidth();
            double scaleFactor = (((double)destHeight) / imageH);
            if (scaleFactor * imageW > screenWidth) {
                // image does not fit by width, gallery will crop it. Preventing this:
                scaleFactor = (((double)screenWidth) / imageW);
                final Gallery gallery = (Gallery) listRow.findViewById(R.id.gallery);
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
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
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
                        webView.getSettings().setAllowContentAccess(true);
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
                    }
                });
            }
        }

        private void resetAdapter() {
            final Gallery gallery = (Gallery) listRow.findViewById(R.id.gallery);
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
                if (hasValidImageExtension(suburl)) {
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
}
