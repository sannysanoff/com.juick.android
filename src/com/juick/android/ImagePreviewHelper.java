package com.juick.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.*;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import com.juickadvanced.R;
import org.acra.ACRA;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 3/22/13
 * Time: 4:59 PM
 * To change this template use File | Settings | File Templates.
 */
public class ImagePreviewHelper {

    ViewGroup view;
    String url;
    public boolean visible;
    public static List<String> queueToDisplay = Collections.synchronizedList(new ArrayList<String>());
    Activity activity;
    long lastJob;

    public ImagePreviewHelper(final ViewGroup view, final Activity activity) {
        this.view = view;
        this.activity = activity;
        final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(view.getContext());
        View close = view.findViewById(R.id.imagepreview_close);
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hide();
            }
        });
        View download = view.findViewById(R.id.imagepreview_download);
        download.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    final DownloadManager mgr = (DownloadManager) view.getContext().getSystemService(Context.DOWNLOAD_SERVICE);
                    final Uri parse = Uri.parse(url);
                    final DownloadManager.Request req = new DownloadManager.Request(parse);

                    MessageMenu.confirmAction(R.string.ReallyDownload, view.getContext(), false, new Runnable() {
                        @Override
                        public void run() {
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                                    .mkdirs();
                            String name = new File(parse.getPath()).getName();
                            if (name != null && name.toLowerCase().endsWith(".jpg") || name.toLowerCase().endsWith(".png") || name.toLowerCase().endsWith(".gif")) {
                                // ok
                            } else {
                                name = "JUICK-DL-" + System.currentTimeMillis() + ".jpg";
                            }
                            req.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI
                                    | (sp.getBoolean("downloadImagesOverMobile", true) ? DownloadManager.Request.NETWORK_MOBILE : 0))
                                    .setAllowedOverRoaming(false)
                                    .setTitle("Fullsize Image (Juick)")
                                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_PICTURES, name)
                                    .setDescription(url);
                            lastJob = mgr.enqueue(req);
                        }
                    });
                } catch (IllegalArgumentException e) {
                    new AlertDialog.Builder(view.getContext())
                            .setTitle("Probably HTTPS image.")
                            .setMessage("Cannot use Download Manager (see message below). Load manually? Error was:" + e.toString())
                            .setPositiveButton(R.string.OK, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    new Thread() {
                                        @Override
                                        public void run() {
                                            final File destFile = JuickMessagesAdapter.getDestFile(view.getContext(), url);
                                            DefaultHttpClient httpClient = new DefaultHttpClient();
                                            HttpGet httpGet = new HttpGet(url);
                                            String loadURl = url;
                                            try {
                                                httpClient.execute(httpGet, new ResponseHandler<HttpResponse>() {
                                                    @Override
                                                    public HttpResponse handleResponse(HttpResponse response) throws IOException {
                                                        if (response.getStatusLine().getStatusCode() != 200) {
                                                            activity.runOnUiThread(new Runnable() {
                                                                @Override
                                                                public void run() {
                                                                    Toast.makeText(activity, "Error downloading full image", Toast.LENGTH_LONG).show();
                                                                }
                                                            });
                                                            destFile.delete();
                                                            return response;
                                                        }
                                                        HttpEntity entity = response.getEntity();
                                                        InputStream content = entity.getContent();
                                                        if (content != null) {
                                                            OutputStream outContent = new FileOutputStream(destFile);
                                                            byte[] buf = new byte[4096];
                                                            while (true) {
                                                                int rd = content.read(buf);
                                                                if (rd <= 0) break;
                                                                outContent.write(buf, 0, rd);
                                                            }
                                                            content.close();
                                                            outContent.close();
                                                        }
                                                        scheduleReadyImage(url, ImagePreviewHelper.this);
                                                        return null;
                                                    }
                                                });
                                            } catch (IOException e1) {
                                                e1.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                                            }
                                        }
                                    }.start();
                                }
                            })
                            .setCancelable(true)
                            .setNegativeButton(R.string.Cancel, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                }
                            }).show();
                }
            }
        });
    }

    Bitmap bitmap;

    // called in bg thread
    public static void scheduleReadyImage(final String networkURL, final ImagePreviewHelper maybeCurrent) {
        if (maybeCurrent != null && maybeCurrent.visible) {
            if (maybeCurrent.activity.getWindow().isActive()) {
                maybeCurrent.activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        doWithDownloadedURL(maybeCurrent, networkURL);
                    }
                });
                return;
            }
        }
        // otherwise
        queueToDisplay.add(networkURL);
    }

    public void scheduleDownloadedImage(long job, final String url) {
        if (job == lastJob && visible && activity.getWindow().isActive()) {
            doWithDownloadedURL(this, url);
        } else {
            queueToDisplay.add(url);
            if (!visible && activity.getWindow().isActive()) {
                reopenWithURL(url);
            }
        }
    }

    public static void doWithDownloadedURL(ImagePreviewHelper maybeCurrent, String url) {
        MyScrollImageView iv = (MyScrollImageView) maybeCurrent.view.findViewById(R.id.imagepreview_image);
        TextView infotv = (TextView) maybeCurrent.view.findViewById(R.id.imagepreview_info);
        try {
            File destFile = JuickMessagesAdapter.getDestFile(maybeCurrent.activity, url);
            maybeCurrent.bitmap = null;
            Toast.makeText(maybeCurrent.activity, "Filesize: "+destFile.length(), Toast.LENGTH_LONG).show();
            releaseBitmap(iv);
            iv.setImageURI(Uri.fromFile(destFile));
            iv.refresh();
            if (iv.getDrawable() instanceof BitmapDrawable) {
                BitmapDrawable bd = (BitmapDrawable)iv.getDrawable();
                infotv.setText("Image "+bd.getBitmap().getWidth()+" x "+bd.getBitmap().getHeight());
            }
        } catch (OutOfMemoryError e) {
            iv.setImageURI(null);
            ACRA.getErrorReporter().handleException(new RuntimeException("OOM: "+ XMPPControlActivity.getMemoryStatusString(), e));
            Toast.makeText(maybeCurrent.activity, "Out of memory.", Toast.LENGTH_LONG).show();
        } catch (Throwable ex) {
            Toast.makeText(maybeCurrent.activity, ex.toString(), Toast.LENGTH_LONG).show();
        }
    }

    private static void releaseBitmap(MyScrollImageView iv) {
        final Drawable drawable = iv.getDrawable();
        if (drawable instanceof BitmapDrawable) {
            final BitmapDrawable bd = (BitmapDrawable) drawable;
            BitmapCounts.releaseBitmap(bd.getBitmap());
        }
        iv.setImageDrawable(null);
    }

    public static void updateInfo(ImagePreviewHelper maybeCurrent) {
        ImageView iv = (ImageView) maybeCurrent.view.findViewById(R.id.imagepreview_image);
        TextView infotv = (TextView) maybeCurrent.view.findViewById(R.id.imagepreview_info);
        if (iv.getDrawable() instanceof BitmapDrawable) {
            BitmapDrawable bd = (BitmapDrawable)iv.getDrawable();
            infotv.setText("Image "+bd.getBitmap().getWidth()+" x "+bd.getBitmap().getHeight());
        }
    }

    public void startWithImage(Drawable drawable, String info, String url, boolean isFull) {
        if (drawable instanceof BitmapDrawable) {
            bitmap = ((BitmapDrawable) drawable).getBitmap();
            BitmapCounts.retainBitmap(bitmap);
        }
        view.setVisibility(View.VISIBLE);
        AnimationSet as = new AnimationSet(false);
        AlphaAnimation aa = new AlphaAnimation(0, 1);
        aa.setDuration(400);
        as.addAnimation(aa);
        TranslateAnimation ta = new TranslateAnimation(0, 0, 50, 0);
        ta.setInterpolator(new OvershootInterpolator(3));
        ta.setDuration(400);
        as.addAnimation(ta);
        view.startAnimation(as);
        ImageView iv = (ImageView) view.findViewById(R.id.imagepreview_image);
        TextView infotv = (TextView) view.findViewById(R.id.imagepreview_info);
        infotv.setText(info);
        iv.setImageDrawable(drawable);
        this.url = maybeConvertJuickURLToFull(url);
        visible = true;
    }

    private String maybeConvertJuickURLToFull(String url) {
        if (url.startsWith("http://i.juick.com/p")) {
            url = url.substring(url.lastIndexOf("/"));
            url = "http://i.juick.com/p" + url;
        }
        return url;
    }

    public void hide() {
        visible = false;
        view.clearAnimation();
        AnimationSet as = new AnimationSet(false);
        AlphaAnimation aa = new AlphaAnimation(1, 0);
        aa.setDuration(400);
        aa.setInterpolator(new DecelerateInterpolator(1));
        as.addAnimation(aa);
        TranslateAnimation ta = new TranslateAnimation(0, 0, 0, 300);
        ta.setInterpolator(new AccelerateInterpolator(1));
        ta.setDuration(400);
        ta.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
                //To change body of implemented methods use File | Settings | File Templates.
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                view.setVisibility(View.GONE);
                if (bitmap != null) {
                    BitmapCounts.releaseBitmap(bitmap);
                    bitmap = null;
                }
                String newURL = null;
                synchronized (queueToDisplay) {
                    if (queueToDisplay.size() > 0) {
                        newURL = queueToDisplay.remove(0);
                    }
                }
                if (newURL != null) {
                    reopenWithURL(newURL);
                }
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
                //To change body of implemented methods use File | Settings | File Templates.
            }
        });
        as.addAnimation(ta);
        view.startAnimation(as);
    }

    private void reopenWithURL(String newURL) {
        final File destFile = JuickMessagesAdapter.getDestFile(activity, newURL);
        startWithImage(Drawable.createFromPath(destFile.getPath()), "", newURL, true);
        updateInfo(this);
    }

    public boolean handleBack() {
        if (view.getVisibility() == View.VISIBLE) {
            hide();
            return true;
        }
        return false;
    }

}
