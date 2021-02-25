
package tv.danmaku.ijk.media.player.widget.media;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.TextureView;

public class IjkTextureView extends TextureView {
    private Paint mDrawPaint = new Paint();
    private Rect mDrawRect = new Rect();
    public int offsetX = 0;
    public int offsetY = 0;
    public int gravity = Gravity.CENTER;

    public IjkTextureView(Context context) {
        super(context);
    }

    public IjkTextureView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public IjkTextureView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    private Rect getDrawRect(Bitmap bmp) {
        int targetWidth = getWidth();
        int targetHeight = (bmp.getHeight() * targetWidth) / bmp.getWidth();
        if (targetHeight > getHeight()) {
            targetHeight = getHeight();
            targetWidth = (bmp.getWidth() * targetHeight) / bmp.getHeight();
        }

        if (gravity == Gravity.CENTER) {
            int left = getWidth() / 2 - targetWidth / 2 + offsetX;
            int top = getHeight() / 2 - targetHeight / 2 + offsetY;
            int right = left + targetWidth;
            int bottom = top + targetHeight;
            return new Rect(left, top, right, bottom);
        } else {
            //LEFT and TOP
            int left = offsetX;
            int top = offsetY;
            int right = left + targetWidth;
            int bottom = top + targetHeight;
            return new Rect(left, top, right, bottom);
        }
    }

    public void getCanvasDrawRect(Rect rect){
        rect.set(mDrawRect);
    }

    public void drawFromBitmap(Bitmap bitmap) {
        mDrawRect.set(getDrawRect(bitmap));
        Canvas canvas = lockCanvas();
        canvas.drawBitmap(bitmap, null, mDrawRect, mDrawPaint);
        unlockCanvasAndPost(canvas);
    }
}