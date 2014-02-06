package jp.tomorrowkey.android.gifplayer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.*;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import com.juick.android.IGifView;

public class GifView extends View implements IGifView {

	public static final int IMAGE_TYPE_UNKNOWN = 0;
	public static final int IMAGE_TYPE_STATIC = 1;
	public static final int IMAGE_TYPE_DYNAMIC = 2;

	public static final int DECODE_STATUS_UNDECODE = 0;
	public static final int DECODE_STATUS_DECODING = 1;
	public static final int DECODE_STATUS_DECODED = 2;
	public static final int DECODE_STATUS_OOM = 3;

	private GifDecoder decoder;
	private Bitmap bitmap;

	public int imageType = IMAGE_TYPE_UNKNOWN;
	public int decodeStatus = DECODE_STATUS_UNDECODE;

	private int width;
	private int height;

	private long time;
	private int index;

	private int resId;
	private String filePath;

	private boolean playFlag = false;
    private long fileSize;

    public GifView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	/**
	 * Constructor
	 */
	public GifView(Context context) {
		super(context);
	}

	private InputStream getInputStream() {
		if (filePath != null)
			try {
				return new FileInputStream(filePath);
			} catch (FileNotFoundException e) {
			}
		if (resId > 0)
			return getContext().getResources().openRawResource(resId);
		return null;
	}

	/**
	 * set gif file path
	 * 
	 * @param filePath
	 */
	public void setGif(String filePath) {
		Bitmap bitmap = BitmapFactory.decodeFile(filePath);
		setGif(filePath, bitmap);
	}

	/**
	 * set gif file path and cache image
	 * 
	 * @param filePath
	 * @param cacheImage
	 */
	public void setGif(String filePath, Bitmap cacheImage) {
		this.resId = 0;
		this.filePath = filePath;
		imageType = IMAGE_TYPE_UNKNOWN;
		decodeStatus = DECODE_STATUS_UNDECODE;
		playFlag = false;
		bitmap = cacheImage;
		width = bitmap.getWidth();
		height = bitmap.getHeight();
	}

	/**
	 * set gif resource id
	 * 
	 * @param resId
	 */

	private void decode() {
		decoder = null;
		index = 0;

		decodeStatus = DECODE_STATUS_DECODING;
        final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());

		new Thread() {
			@Override
			public void run() {

				decoder = new GifDecoder(sp.getBoolean("use_handmade_gifviewer_downscale", true));
                try {
				    decoder.read(getInputStream());
                } catch (OutOfMemoryError err) {
                    // no luck
                    decoder = null;
                    decodeStatus = DECODE_STATUS_OOM;
                } catch (Throwable err) {
                    decodeStatus = DECODE_STATUS_OOM;
                }
                synchronized (this) {
                    if (decoder != null) {      // release() could happen
                        if (decoder.width == 0 || decoder.height == 0) {
                            imageType = IMAGE_TYPE_STATIC;
                        } else {
                            imageType = IMAGE_TYPE_DYNAMIC;
                        }
                        postInvalidate();
                        time = System.currentTimeMillis();
                        decodeStatus = DECODE_STATUS_DECODED;
                        post(new Runnable() {
                            @Override
                            public void run() {
                                invalidate();
                            }
                        });
                    }
                }
			}
		}.start();
	}

	public synchronized void release() {
        if (decoder != null) {
            decoder.terminated = true;
		    decoder = null;
        }
        if (bitmap != null) {
            bitmap.recycle();
        }
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);

		if (decodeStatus == DECODE_STATUS_UNDECODE) {
            drawDefaultBitmap(canvas);
			if (playFlag) {
				decode();
			}
		} else if (decodeStatus == DECODE_STATUS_DECODING) {
            drawDefaultBitmap(canvas);
            synchronized (this) {
                if (decoder != null) {
                    String usedMem = getMemoryStatus();
                    drawText("Decoding: " + decoder.frameCount + " (" + decoder.filepos * 100 / fileSize + "%) "+usedMem, canvas);
                }
            }
            postDelayed(new Runnable() {
                @Override
                public void run() {
                    invalidate();
                }
            }, 1000);
		} else if (decodeStatus == DECODE_STATUS_OOM) {
            drawDefaultBitmap(canvas);
            drawText("Out of memory. "+getMemoryStatus(), canvas);
		} else if (decodeStatus == DECODE_STATUS_DECODED) {
			if (imageType == IMAGE_TYPE_STATIC) {
                drawDefaultBitmap(canvas);
                drawText("Decoding", canvas);
			} else if (imageType == IMAGE_TYPE_DYNAMIC) {
				if (playFlag) {
					long now = System.currentTimeMillis();

					if (time + decoder.getDelay(index) < now) {
						time += decoder.getDelay(index);
						incrementFrameIndex();
					}
					Bitmap bitmap = decoder.getFrame(index);
					if (bitmap != null) {
                        final Matrix mat = new Matrix();
                        mat.setScale(((float)getWidth())/bitmap.getWidth(), ((float)getHeight())/bitmap.getHeight());
                        canvas.concat(mat);
						canvas.drawBitmap(bitmap, 0, 0, null);
					}
					invalidate();
				} else {
					Bitmap bitmap = decoder.getFrame(index);
                    final Matrix mat = new Matrix();
                    mat.setScale(((float)getWidth())/bitmap.getWidth(), ((float)getHeight())/bitmap.getHeight());
                    canvas.concat(mat);
					canvas.drawBitmap(bitmap, 0, 0, null);
				}
			} else {
                drawDefaultBitmap(canvas);
                drawText("Paused", canvas);
			}
		}
	}

    private String getMemoryStatus() {
        final Runtime runtime = Runtime.getRuntime();
        final long free = runtime.freeMemory();
        final long total = runtime.totalMemory();
        String usedMem;
        if (runtime.maxMemory() != Long.MAX_VALUE) {
            long potentiallyFree = free + runtime.maxMemory() - total;
            usedMem = "(RAM USAGE: "+(runtime.maxMemory()-potentiallyFree)*100/runtime.maxMemory()+"% of "+(runtime.maxMemory()/1024)+" MB";
        } else {
            usedMem = ""+(total-free)*100/total+"% of "+(runtime.maxMemory()/1024)+" MB";

        }
        return usedMem;
    }

    private void drawDefaultBitmap(Canvas canvas) {
        final Matrix mat = new Matrix();
        mat.setScale(((float)getWidth())/width, ((float)getHeight())/height);
        canvas.save();
        canvas.concat(mat);
        canvas.drawBitmap(bitmap, 0, 0, null);
        canvas.restore();
    }

    private void drawText(String text, Canvas canvas) {
        Paint p = new Paint();
        p.setColor(Color.BLACK);
        p.setStyle(Paint.Style.FILL);
        p.setTextSize(20);

        canvas.drawText(text, 0, -p.ascent()+p.descent(), p);
        p.setColor(Color.YELLOW);
        canvas.drawText(text, 0+1, -p.ascent()+p.descent()+1, p);
    }

    private void incrementFrameIndex() {
		index++;
		if (index >= decoder.getFrameCount()) {
			index = 0;
		}
	}

	private void decrementFrameIndex() {
		index--;
		if (index < 0) {
			index = decoder.getFrameCount() - 1;
		}
	}

	public void play() {
		time = System.currentTimeMillis();
		playFlag = true;
		invalidate();
	}

	public void pause() {
		playFlag = false;
		invalidate();
	}

	public void stop() {
		playFlag = false;
		index = 0;
		invalidate();
	}

	public void nextFrame() {
		if (decodeStatus == DECODE_STATUS_DECODED) {
			incrementFrameIndex();
			invalidate();
		}
	}

	public void prevFrame() {
		if (decodeStatus == DECODE_STATUS_DECODED) {
			decrementFrameIndex();
			invalidate();
		}
	}

    @Override
    public void setMovieFile(File file) {
        playFlag = true;
        setGif(file.getPath());
        playFlag = true;
        fileSize = file.length();
    }

    @Override
    public ViewGroup.LayoutParams getLayoutParams() {
        final ViewGroup.LayoutParams layoutParams = super.getLayoutParams();
        return layoutParams;    //To change body of overridden methods use File | Settings | File Templates.
    }

    @Override
    public void setLayoutParams(ViewGroup.LayoutParams params) {
        super.setLayoutParams(params);    //To change body of overridden methods use File | Settings | File Templates.
    }
}