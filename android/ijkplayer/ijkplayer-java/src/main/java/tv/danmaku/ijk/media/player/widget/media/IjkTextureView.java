
package tv.danmaku.ijk.media.player.widget.media;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.util.AttributeSet;
import android.view.Gravity;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

class BitmapRenderer implements GLSurfaceView.Renderer {
    private int[] mTextures = null;
    private Bitmap mBitmap = null;
    private Rect mDrawRect = new Rect();
    private Matrix mTransform = null;
    private int mWidth = 0;
    private int mHeight = 0;

    public void setTransform(Matrix transform) {
        mTransform = transform;
    }

    public void setBitmap(Bitmap bitmap, Rect rect) {
        mBitmap = bitmap;
        mDrawRect = rect;
    }

    private static final float[] VERTEX_COORDINATES = new float[] {
            -1.0f, +1.0f, 0.0f,
            +1.0f, +1.0f, 0.0f,
            -1.0f, -1.0f, 0.0f,
            +1.0f, -1.0f, 0.0f
    };

    private static final float[] TEXTURE_COORDINATES = new float[] {
            0.0f, 0.0f,
            1.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 1.0f
    };

    private static final Buffer TEXCOORD_BUFFER = ByteBuffer.allocateDirect(TEXTURE_COORDINATES.length * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().put(TEXTURE_COORDINATES).rewind();
    private static final Buffer VERTEX_BUFFER = ByteBuffer.allocateDirect(VERTEX_COORDINATES.length * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().put(VERTEX_COORDINATES).rewind();

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        mWidth = width;
        mHeight = height;
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        if (mBitmap == null) {
            return;
        }

        if (mTextures == null) {
            mTextures = new int[1];
        } else {
            gl.glDeleteTextures(1, mTextures, 0);
        }

        float scaleX = 1.f;
        float scaleY = 1.f;
        float translateX = 0.f;
        float translateY = 0.f;
        int drawWidth = mDrawRect.width();
        int drawHeight = mDrawRect.height();
        int offsetH = 0;

        if (mTransform != null)
        {
            float [] values = new float[9];
            mTransform.getValues(values);

            scaleX = 1.0f / values[Matrix.MSCALE_X];
            scaleY = 1.0f / values[Matrix.MSCALE_Y];

            drawHeight = (int)(mDrawRect.height() / scaleY);
            drawHeight = Math.min(drawHeight, mHeight);
            offsetH = (drawHeight - mDrawRect.height()) / 2;
            drawWidth = drawWidth * drawHeight / mDrawRect.height();

            if (values[Matrix.MSCALE_X] == 1) {
                translateX = (-values[Matrix.MTRANS_X] / mWidth);
            } else {
                float ratio = (1.0f - (float)mWidth / drawWidth) / (values[Matrix.MSCALE_X] - 1);
                translateX = (-values[Matrix.MTRANS_X] / mWidth) * (1 + ratio);
            }

            //
            // <HACK> -0.01f to fix screen bottom wield when zoom in
            //
            translateY = -values[Matrix.MTRANS_Y] / mHeight - 0.01f;
            translateY = Math.max(translateY, 0.f);
        }

        gl.glViewport(mDrawRect.left, mDrawRect.top - offsetH, drawWidth, drawHeight);
        gl.glEnable(GL10.GL_TEXTURE_2D);
        gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
        gl.glEnableClientState(GL10.GL_TEXTURE_COORD_ARRAY);

        gl.glGenTextures(1, mTextures, 0);
        gl.glBindTexture(GL10.GL_TEXTURE_2D, mTextures[0]);

        gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
        gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
        gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
        gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);

        GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, mBitmap, 0);

        gl.glActiveTexture(GL10.GL_TEXTURE0);
        gl.glBindTexture(GL10.GL_TEXTURE_2D, mTextures[0]);

        gl.glVertexPointer(3, GL10.GL_FLOAT, 0, VERTEX_BUFFER);
        gl.glTexCoordPointer(2, GL10.GL_FLOAT, 0, TEXCOORD_BUFFER);

        if (mTransform != null) {
            gl.glMatrixMode(GL10.GL_TEXTURE);
            gl.glLoadIdentity();
            gl.glScalef(scaleX, scaleY, 1.0f);
            gl.glTranslatef(translateX, translateY, 0.0f);
        }

        gl.glClear(GL10.GL_COLOR_BUFFER_BIT | GL10.GL_DEPTH_BUFFER_BIT);
        gl.glDrawArrays(GL10.GL_TRIANGLE_STRIP, 0, 4);
    }

    public Rect getDrawRect() {
        return mDrawRect;
    }
}

public class IjkTextureView extends GLSurfaceView {
    public int offsetX = 0;
    public int offsetY = 0;
    public int gravity = Gravity.CENTER;
    BitmapRenderer mRender = new BitmapRenderer();

    public IjkTextureView(Context context) {
        super(context);
        setRenderer(mRender);
    }

    public IjkTextureView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setRenderer(mRender);
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

    public void drawFromBitmap(Bitmap bitmap) {
        Rect rect = getDrawRect(bitmap);
        mRender.setBitmap(bitmap, rect);
    }

    public void getCanvasDrawRect(Rect rect) {
        rect.set(mRender.getDrawRect());
    }

    public void setTransform(Matrix transform) {
        mRender.setTransform(transform);
    }
}
