package com.juick.android;

import android.app.DownloadManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import com.juickadvanced.R;

import java.io.File;

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

    public ImagePreviewHelper(final ViewGroup view) {
        this.view = view;
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
                MessageMenu.confirmAction(R.string.ReallyDownload, view.getContext(), false, new Runnable() {
                    @Override
                    public void run() {
                        DownloadManager mgr= (DownloadManager)view.getContext().getSystemService(Context.DOWNLOAD_SERVICE);
                        Uri parse = Uri.parse(url);
                        DownloadManager.Request req=new DownloadManager.Request(parse);
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                                .mkdirs();
                        String name = new File(parse.getPath()).getName();
                        if (name != null && name.toLowerCase().endsWith(".jpg") || name.toLowerCase().endsWith(".png") || name.toLowerCase().endsWith(".gif")) {
                            // ok
                        } else {
                            name = "JUICK-DL-"+System.currentTimeMillis()+".jpg";
                        }
                        req.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI
                                | (sp.getBoolean("downloadImagesOverMobile", true) ? DownloadManager.Request.NETWORK_MOBILE : 0))
                                .setAllowedOverRoaming(false)
                                .setTitle("Fullsize Image (Juick)")
                                .setDestinationInExternalPublicDir(Environment.DIRECTORY_PICTURES, name)
                                .setDescription(url);

                        mgr.enqueue(req);
                    }
                });
            }
        });
    }

    Bitmap bitmap;

    public void startWithImage(Drawable drawable, String info, String url) {
        if (drawable instanceof BitmapDrawable) {
            bitmap = ((BitmapDrawable) drawable).getBitmap();
            BitmapCounts.retainBitmap(bitmap);
        }
        view.setVisibility(View.VISIBLE);
        ImageView iv = (ImageView)view.findViewById(R.id.imagepreview_image);
        TextView infotv = (TextView)view.findViewById(R.id.imagepreview_info);
        infotv.setText(info);
        iv.setImageDrawable(drawable);
        this.url = url;
    }

    public void hide(){
        view.setVisibility(View.GONE);
        if (bitmap != null) {
            BitmapCounts.releaseBitmap(bitmap);
            bitmap = null;
        }
    }

    public boolean handleBack() {
        if (view.getVisibility() == View.VISIBLE) {
            hide();
            return true;
        }
        return false;
    }

}
