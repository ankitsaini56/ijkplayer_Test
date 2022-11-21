/*
 * Copyright (C) 2015 Bilibili
 * Copyright (C) 2015 Zhang Rui <bbcallen@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tv.danmaku.ijk.media.player.widget.media;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.PixelCopy;
import android.view.ScaleGestureDetector;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.MediaController;
import android.widget.TextView;

import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.EglBase;
import org.webrtc.FileVideoCapturer;
import org.webrtc.IceCandidate;
import org.webrtc.Logging;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SessionDescription;
import org.webrtc.StatsReport;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import tv.danmaku.ijk.media.example.webrtc.NebulaRTCClient;
import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;
import tv.danmaku.ijk.media.player.IjkTimedText;
import tv.danmaku.ijk.media.player.RawDataSourceProvider;
import tv.danmaku.ijk.media.player.misc.ITrackInfo;
import tv.danmaku.ijk.media.player.misc.IjkAudioFrame;
import tv.danmaku.ijk.media.player.misc.IjkFrame;
import tv.danmaku.ijk.media.player.misc.ObjectTrackingInfo;
import tv.danmaku.ijk.media.player.misc.Rect;
import tv.danmaku.ijk.webrtc.AppRTCClient;
import tv.danmaku.ijk.webrtc.AppRTCClient.*;
import tv.danmaku.ijk.webrtc.NebulaInterface;
import tv.danmaku.ijk.webrtc.NebulaParameter;
import tv.danmaku.ijk.webrtc.PeerConnectionClient;
import tv.danmaku.ijk.webrtc.PeerConnectionClient.*;

public class IjkVideoView extends FrameLayout implements
        MediaController.MediaPlayerControl,
        AppRTCClient.SignalingEvents,
        PeerConnectionClient.PeerConnectionEvents {

    public interface Listener {
        void onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY);
    }

    public enum Mode {
        EPAN,
        PIP,
        OBJECT_DETECT,
        NORMAL,
    }

    private static String TAG = "IjkVideoView";
    private static String VERSION = "0.9.35";

    // settable by the client
    private Uri mUri = null;

    // all possible internal states
    public static final int ERROR_HTTP_UNAUTHORIZED = ERRTAG((byte)0xF8, '4', '0', '1');
    public static final int STATE_ERROR = -1;
    public static final int STATE_IDLE = 0;
    public static final int STATE_PREPARING = 1;
    public static final int STATE_PREPARED = 2;
    public static final int STATE_PLAYING = 3;
    public static final int STATE_PAUSED = 4;
    public static final int STATE_PLAYBACK_COMPLETED = 5;
    public static final long INVALID_WEBRTC_ID = 0;
    public static final String STREAM_TYPE_AUDIO_AND_VIDEO = "audioAndVideo";
    public static final String STREAM_TYPE_AUDIO_AND_SUBVIDEO = "audioAndSubVideo";
    private static final int MIN_DISTANCE = 5;
    private static final float TRACKING_SPEED = 0.05f;
    private static final int TRACKING_THRESHOLD_IN_SECONDS = 3;
    private static final int AV_SECURITY_SIMPLE = 0;
    private static final int AV_SECURITY_DTLS = 1;
    private static final int AV_SECURITY_AUTO = 2;
    private static final int MSG_CHECK = 1001;
    private static final int MSG_UPDATE_UI = 1002;
    private static final int MSG_START = 1003;
    private static final float MIN_SCALE = 1.f;
    private static final float MAX_SCALE = 3.f;
    private static final long IGNORE_THRESHOLD_IN_MS = 150;
    private static final float PAINT_TEXT_SIZE_IN_PX = 50.0f;

    // mCurrentState is a VideoView object's current state.
    // mTargetState is the state that a method caller intends to reach.
    // For instance, regardless the VideoView object's current state,
    // calling pause() intends to bring the object to a target state
    // of STATE_PAUSED.
    public int mCurrentState = STATE_IDLE;
    private int mTargetState = STATE_IDLE;

    // All the stuff we need for playing and showing a video
    private IRenderView.ISurfaceHolder mSurfaceHolder = null;
    public IMediaPlayer mMediaPlayer = null;
    private int mVideoWidth;
    private int mVideoHeight;
    private int mSurfaceWidth;
    private int mSurfaceHeight;
    private int mVideoRotationDegree;
    private IMediaController mMediaController;
    private IMediaPlayer.OnCompletionListener mOnCompletionListener;
    private IMediaPlayer.OnPreparedListener mOnPreparedListener;
    private int mCurrentBufferPercentage;
    private IMediaPlayer.OnErrorListener mOnErrorListener;
    private IMediaPlayer.OnInfoListener mOnInfoListener;
    private IMediaPlayer.OnSeekCompleteListener mOnSeekCompleteListener;
    private IMediaPlayer.DownloadListener mDownloadListener;
    private long mSeekWhenPrepared;  // recording the seek position while preparing

    private boolean mUsingMediaCodec = false;
    private String mPixelFormat = "fcc-_es2";
    private float mSpeed = 1.0f;
    private int mEnableGetFrame = 0;
    private int mEnableAvtechSeek = 0;
    private long mAvAPIs3 = 0;
    private long mAvAPIs4 = 0;
    private int mDtls = AV_SECURITY_AUTO;
    private long mWebRTCAPIs = 0;
    private Map<String, String> mHttpHeaders = null;
    private String mUserAgent = null;
    private boolean mEnableOpenVideoOnSurfaceCreate = true;
    private int mCodecThreads = 0;
    private int mAccurateSeek = 1;
    private int mLowDelay = 0;
    private int mLowDelayStartThreshold = 0;
    private int mLowDelayStopThreshold = 0;
    private String mMp4Path = null;
    private int mVideoRecordTimeout = 0;
    private long mVideoRecordStartTime = 0;
    private int mMaxBufferSize = -1;
    private int mHackClaireControl = 0;
    private int mEasyMode = 0;
    private int mBufferThreshold = 0;
    private int mAvapiTimeout = 0;
    private boolean mDebug = true;
    private int mAudioSessionId = AudioManager.AUDIO_SESSION_ID_GENERATE;
    private long mSocketTimeoutUS = 0;

    private Context mAppContext;
    private IRenderView mRenderView;
    private int mVideoSarNum;
    private int mVideoSarDen;

    private TextView subtitleDisplay;
    private int mCurrentX = -1;
    private int mCurrentY = -1;
    private Rect mRect = new Rect();
    private long mLastFoundObjectTime = -1;

    private AppRTCClient mRtcClient;
    private PeerConnectionClient peerConnectionClient;
    private SignalingParameters signalingParameters = null;
    private PeerConnectionParameters peerConnectionParameters;
    private final ProxyVideoSink localProxyVideoSink = new ProxyVideoSink();
    private final List<VideoSink> remoteSinks = new ArrayList<>();
    private final ProxyVideoSink remoteProxyRenderer = new ProxyVideoSink();
    private AppRTCClient.RoomConnectionParameters roomConnectionParameters;
    private long callStartedTimeMs;
    private long mWebrtcId = -1;
    private ConditionVariable cond;
    private ScaleGestureDetector mScaleDetector;
    private GestureDetector mDetector;
    private float mScaleFactor = 1.f;
    private float mOffsetX = 0.f;
    private float mOffsetY = 0.f;
    private int mOriginalWidth;
    private int mOriginalHeight;
    private float mMaxScale = MAX_SCALE;
    private boolean mOnScaling = false;
    private long mScaleOrDragEndTime = 0;
    private Listener mListener = null;
    private HandlerThread mHandlerThread = null;
    private Handler mBackgoundHandler = null;
    private Object mWebRTClock = new Object();

    private static int ERRTAG( byte a, char b, char c, char d) {
        int tag = (a & 0xFF) + ((b & 0xFF) << 8) + ((c & 0xFF) << 16) + ((d & 0xFF) << 24);
        return -tag;
    }

    @SuppressLint("HandlerLeak")
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MSG_UPDATE_UI) {
                requestLayout();
                invalidate();
                return;
            }

            if (msg.what != MSG_CHECK) {
                return;
            }

            long curTime = System.currentTimeMillis();
            if (curTime - mVideoRecordStartTime >= mVideoRecordTimeout * 1000L) {
                stopVideoRecord();
                return;
            }

            sendEmptyMessageDelayed(MSG_CHECK, 1000);
        }
    };

    private final ScaleGestureDetector.SimpleOnScaleGestureListener mScaleListener = new ScaleGestureDetector.SimpleOnScaleGestureListener() {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            mScaleFactor *= detector.getScaleFactor();
            mScaleFactor = Math.max(MIN_SCALE, Math.min(mScaleFactor, mMaxScale));
            scale(mScaleFactor);
            invalidate();
            return true;
        }

        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            mOnScaling = true;
            updateRenderViewSize();
            return true;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            super.onScaleEnd(detector);
            mOnScaling = false;
        }
    };

    private final GestureDetector.SimpleOnGestureListener mGestureListener = new GestureDetector.SimpleOnGestureListener() {
        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            updateRenderViewSize();
            mOffsetX -= distanceX;
            mOffsetY -= distanceY;
            View view = mRenderView.getView();
            int maxX = (int)(((mScaleFactor - 1.0f) / 2.0f) * mOriginalWidth);
            int maxY = (int)(((mScaleFactor - 1.0f) / 2.0f) * mOriginalHeight);
            mOffsetX = Math.max(-maxX, Math.min(mOffsetX, maxX));
            mOffsetY = Math.max(-maxY, Math.min(mOffsetY, maxY));
            view.setTranslationX(mOffsetX);
            view.setTranslationY(mOffsetY);
            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            if (mListener != null) {
                mListener.onFling(e1, e2, velocityX, velocityY);
            }
            return true;
        }
    };

    public IjkVideoView(Context context) {
        super(context);
        initVideoView(context);
    }

    public IjkVideoView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initVideoView(context);
    }

    public IjkVideoView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initVideoView(context);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public IjkVideoView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        initVideoView(context);
    }

    public void setWebRTCMic(boolean enable) {
        if (peerConnectionClient != null) {
            peerConnectionClient.setAudioEnabled(enable);
        }
    }

    private void initVideoView(Context context) {
        Log.i(TAG, "ijkvideoview version: " + VERSION);
        mAppContext = context.getApplicationContext();

        initRenders();

        mVideoWidth = 0;
        mVideoHeight = 0;
        setFocusable(true);
        setFocusableInTouchMode(true);
        requestFocus();
        mCurrentState = STATE_IDLE;
        mTargetState = STATE_IDLE;
        mEnableOpenVideoOnSurfaceCreate = true;

        subtitleDisplay = new TextView(context);
        subtitleDisplay.setTextSize(24);
        subtitleDisplay.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams layoutParams_txt = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        addView(subtitleDisplay, layoutParams_txt);
        mScaleDetector = new ScaleGestureDetector(context, mScaleListener);
        mDetector = new GestureDetector(context, mGestureListener);
    }

    public void setRenderView(IRenderView renderView) {
        if (mRenderView != null) {
            if (mMediaPlayer != null)
                mMediaPlayer.setDisplay(null);

            View renderUIView = mRenderView.getView();
            mRenderView.removeRenderCallback(mSHCallback);
            mRenderView = null;
            removeView(renderUIView);
        }

        if (renderView == null)
            return;

        mRenderView = renderView;
        renderView.setAspectRatio(mCurrentAspectRatio);
        if (mVideoWidth > 0 && mVideoHeight > 0)
            renderView.setVideoSize(mVideoWidth, mVideoHeight);
        if (mVideoSarNum > 0 && mVideoSarDen > 0)
            renderView.setVideoSampleAspectRatio(mVideoSarNum, mVideoSarDen);

        View renderUIView = mRenderView.getView();
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        renderUIView.setLayoutParams(lp);
        addView(renderUIView);

        mRenderView.addRenderCallback(mSHCallback);
        mRenderView.setVideoRotation(mVideoRotationDegree);
    }

    public void setRender(int render) {
        switch (render) {
            case RENDER_NONE:
                setRenderView(null);
                break;
            case RENDER_TEXTURE_VIEW: {
                TextureRenderView renderView = new TextureRenderView(getContext());
                if (mMediaPlayer != null) {
                    renderView.getSurfaceHolder().bindToMediaPlayer(mMediaPlayer);
                    renderView.setVideoSize(mMediaPlayer.getVideoWidth(), mMediaPlayer.getVideoHeight());
                    renderView.setVideoSampleAspectRatio(mMediaPlayer.getVideoSarNum(), mMediaPlayer.getVideoSarDen());
                    renderView.setAspectRatio(mCurrentAspectRatio);
                }
                setRenderView(renderView);
                break;
            }
            case RENDER_SURFACE_VIEW: {
                SurfaceRenderView renderView = new SurfaceRenderView(getContext());
                setRenderView(renderView);
                break;
            }
            default:
                Log.e(TAG, String.format(Locale.getDefault(), "invalid render %d\n", render));
                break;
        }
    }

    /**
     * Sets video path.
     *
     * @param path the path of the video.
     */
    public void setVideoPath(String path) {
        setVideoPath(path, null);
    }

    public void setVideoPath(String path, Map<String, String> headers) {
        mHttpHeaders = headers;
        setVideoURI(Uri.parse(path));
    }

    /**
     * Sets video URI.
     *
     * @param uri the URI of the video.
     */
    private void setVideoURI(Uri uri) {
        mUri = uri;
        mSeekWhenPrepared = 0;
        openVideo();
        mHandler.sendEmptyMessage(MSG_UPDATE_UI);
    }

    public void stopPlayback() {
        mHandler.removeCallbacksAndMessages(null);
        if (mBackgoundHandler != null) {
            mBackgoundHandler.removeCallbacksAndMessages(null);
        }
        if (mHandlerThread != null) {
            mHandlerThread.quit();
            mHandlerThread.interrupt();
            mHandlerThread = null;
        }

        if (mMediaPlayer != null) {
            mCurrentState = STATE_IDLE;
            mTargetState = STATE_IDLE;
            mEnableOpenVideoOnSurfaceCreate = false;
            AudioManager am = (AudioManager) mAppContext.getSystemService(Context.AUDIO_SERVICE);
            am.abandonAudioFocus(null);
            mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    private void openVideo() {
        if (mUri == null || mSurfaceHolder == null) {
            // not ready for playback just yet, will try again later
            return;
        }
        // we shouldn't clear the target state, because somebody might have
        // called start() previously
        release(false);

        AudioManager am = (AudioManager) mAppContext.getSystemService(Context.AUDIO_SERVICE);
        am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);

        try {
            mMediaPlayer = createPlayer();

            // TODO: create SubtitleController in MediaPlayer, but we need
            // a context for the subtitle renderers
            final Context context = getContext();

            mMediaPlayer.setOnPreparedListener(mPreparedListener);
            mMediaPlayer.setOnVideoSizeChangedListener(mSizeChangedListener);
            mMediaPlayer.setOnCompletionListener(mCompletionListener);
            mMediaPlayer.setOnErrorListener(mErrorListener);
            mMediaPlayer.setOnInfoListener(mInfoListener);
            mMediaPlayer.setOnBufferingUpdateListener(mBufferingUpdateListener);
            mMediaPlayer.setOnSeekCompleteListener(mSeekCompleteListener);
            mMediaPlayer.setOnTimedTextListener(mOnTimedTextListener);
            mCurrentBufferPercentage = 0;

            if (ContentResolver.SCHEME_ANDROID_RESOURCE.equalsIgnoreCase(mUri.getScheme())) {
                int resId = Integer.parseInt(mUri.getPath().substring(1));
                AssetFileDescriptor afd = getResources().openRawResourceFd(resId);
                RawDataSourceProvider sourceProvider = new RawDataSourceProvider(afd);
                mMediaPlayer.setDataSource(sourceProvider);
            } else {
                mMediaPlayer.setDataSource(mAppContext, mUri, null);
            }

            bindSurfaceHolder(mMediaPlayer, mSurfaceHolder);
            mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mMediaPlayer.setScreenOnWhilePlaying(true);
            mMediaPlayer.prepareAsync();

            // we don't set the target state here either, but preserve the
            // target state that was there before.
            mCurrentState = STATE_PREPARING;
            attachMediaController();
        } catch (IOException ex) {
            Log.w(TAG, "Unable to open content: " + mUri, ex);
            mCurrentState = STATE_ERROR;
            mTargetState = STATE_ERROR;
            mErrorListener.onError(mMediaPlayer, MediaPlayer.MEDIA_ERROR_UNKNOWN, 0);
        } catch (IllegalArgumentException ex) {
            Log.w(TAG, "Unable to open content: " + mUri, ex);
            mCurrentState = STATE_ERROR;
            mTargetState = STATE_ERROR;
            mErrorListener.onError(mMediaPlayer, MediaPlayer.MEDIA_ERROR_UNKNOWN, 0);
        }
    }

    public void setMediaController(IMediaController controller) {
        if (mMediaController != null) {
            mMediaController.hide();
        }
        mMediaController = controller;
        attachMediaController();
    }

    private void attachMediaController() {
        if (mMediaPlayer != null && mMediaController != null) {
            mMediaController.setMediaPlayer(this);
            View anchorView = this.getParent() instanceof View ?
                    (View) this.getParent() : this;
            mMediaController.setAnchorView(anchorView);
            mMediaController.setEnabled(isInPlaybackState());
        }
    }

    IMediaPlayer.OnVideoSizeChangedListener mSizeChangedListener =
            new IMediaPlayer.OnVideoSizeChangedListener() {
                public void onVideoSizeChanged(IMediaPlayer mp, int width, int height, int sarNum, int sarDen) {
                    mVideoWidth = mp.getVideoWidth();
                    mVideoHeight = mp.getVideoHeight();
                    mVideoSarNum = mp.getVideoSarNum();
                    mVideoSarDen = mp.getVideoSarDen();
                    if (mVideoWidth != 0 && mVideoHeight != 0) {
                        if (mRenderView != null) {
                            mRenderView.setVideoSize(mVideoWidth, mVideoHeight);
                            mRenderView.setVideoSampleAspectRatio(mVideoSarNum, mVideoSarDen);
                        }
                        requestLayout();
                        updateRenderViewSize();
                    }
                }
            };

    IMediaPlayer.OnPreparedListener mPreparedListener = new IMediaPlayer.OnPreparedListener() {
        public void onPrepared(IMediaPlayer mp) {
            mCurrentState = STATE_PREPARED;

            // Get the capabilities of the player for this stream

            if (mOnPreparedListener != null) {
                mOnPreparedListener.onPrepared(mMediaPlayer);
            }
            if (mMediaController != null) {
                mMediaController.setEnabled(true);
            }
            mVideoWidth = mp.getVideoWidth();
            mVideoHeight = mp.getVideoHeight();

            long seekToPosition = mSeekWhenPrepared;  // mSeekWhenPrepared may be changed after seekTo() call
            if (seekToPosition != 0) {
                seekTo(seekToPosition);
            }
            if (mVideoWidth != 0 && mVideoHeight != 0) {
                //Log.i("@@@@", "video size: " + mVideoWidth +"/"+ mVideoHeight);
                if (mRenderView != null) {
                    mRenderView.setVideoSize(mVideoWidth, mVideoHeight);
                    mRenderView.setVideoSampleAspectRatio(mVideoSarNum, mVideoSarDen);
                    if (!mRenderView.shouldWaitForResize() || mSurfaceWidth == mVideoWidth && mSurfaceHeight == mVideoHeight) {
                        // We didn't actually change the size (it was already at the size
                        // we need), so we won't get a "surface changed" callback, so
                        // start the video here instead of in the callback.
                        if (mTargetState == STATE_PLAYING) {
                            start();
                        }
                    }
                }
            } else {
                // We don't know the video size yet, but should start anyway.
                // The video size might be reported to us later.
                if (mTargetState == STATE_PLAYING) {
                    start();
                }
            }
        }
    };

    private IMediaPlayer.OnCompletionListener mCompletionListener =
            new IMediaPlayer.OnCompletionListener() {
                public void onCompletion(IMediaPlayer mp) {
                    mCurrentState = STATE_PLAYBACK_COMPLETED;
                    mTargetState = STATE_PLAYBACK_COMPLETED;
                    if (mMediaController != null) {
                        mMediaController.hide();
                    }
                    if (mOnCompletionListener != null) {
                        mOnCompletionListener.onCompletion(mMediaPlayer);
                    }
                }
            };

    private IMediaPlayer.OnInfoListener mInfoListener =
            new IMediaPlayer.OnInfoListener() {
                public boolean onInfo(IMediaPlayer mp, int arg1, int arg2) {
                    if (mOnInfoListener != null) {
                        mOnInfoListener.onInfo(mp, arg1, arg2);
                    }
                    switch (arg1) {
                        case IMediaPlayer.MEDIA_INFO_VIDEO_TRACK_LAGGING:
                            Log.d(TAG, "MEDIA_INFO_VIDEO_TRACK_LAGGING:");
                            break;
                        case IMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START:
                            Log.d(TAG, "MEDIA_INFO_VIDEO_RENDERING_START:");
                            break;
                        case IMediaPlayer.MEDIA_INFO_BUFFERING_START:
                            Log.d(TAG, "MEDIA_INFO_BUFFERING_START:");
                            break;
                        case IMediaPlayer.MEDIA_INFO_BUFFERING_END:
                            Log.d(TAG, "MEDIA_INFO_BUFFERING_END:");
                            break;
                        case IMediaPlayer.MEDIA_INFO_NETWORK_BANDWIDTH:
                            Log.d(TAG, "MEDIA_INFO_NETWORK_BANDWIDTH: " + arg2);
                            break;
                        case IMediaPlayer.MEDIA_INFO_BAD_INTERLEAVING:
                            Log.d(TAG, "MEDIA_INFO_BAD_INTERLEAVING:");
                            break;
                        case IMediaPlayer.MEDIA_INFO_NOT_SEEKABLE:
                            Log.d(TAG, "MEDIA_INFO_NOT_SEEKABLE:");
                            break;
                        case IMediaPlayer.MEDIA_INFO_METADATA_UPDATE:
                            Log.d(TAG, "MEDIA_INFO_METADATA_UPDATE:");
                            break;
                        case IMediaPlayer.MEDIA_INFO_UNSUPPORTED_SUBTITLE:
                            Log.d(TAG, "MEDIA_INFO_UNSUPPORTED_SUBTITLE:");
                            break;
                        case IMediaPlayer.MEDIA_INFO_SUBTITLE_TIMED_OUT:
                            Log.d(TAG, "MEDIA_INFO_SUBTITLE_TIMED_OUT:");
                            break;
                        case IMediaPlayer.MEDIA_INFO_VIDEO_ROTATION_CHANGED:
                            mVideoRotationDegree = arg2;
                            Log.d(TAG, "MEDIA_INFO_VIDEO_ROTATION_CHANGED: " + arg2);
                            if (mRenderView != null)
                                mRenderView.setVideoRotation(arg2);
                            break;
                        case IMediaPlayer.MEDIA_INFO_AUDIO_RENDERING_START:
                            Log.d(TAG, "MEDIA_INFO_AUDIO_RENDERING_START:");
                            break;
                        case IMediaPlayer.MEDIA_INFO_VIDEO_RECORD_COMPLETE:
                            if (mMp4Path != null) {
                                mMp4Path = null;
                                stopPlayback();
                                if (mDownloadListener != null) {
                                    mDownloadListener.onComplete(arg2);
                                }
                            }
                            break;
                    }
                    return true;
                }
            };

    private IMediaPlayer.OnErrorListener mErrorListener =
            new IMediaPlayer.OnErrorListener() {
                public boolean onError(IMediaPlayer mp, int framework_err, int impl_err) {
                    Log.d(TAG, "Error: " + framework_err + "," + impl_err);
                    mCurrentState = STATE_ERROR;
                    mTargetState = STATE_ERROR;

                    /* If an error handler has been supplied, use it and finish. */
                    if (mMp4Path != null) {
                        mMp4Path = null;
                        if (mDownloadListener != null) {
                            mDownloadListener.onComplete(framework_err);
                        }
                    } else if (mOnErrorListener != null) {
                        if (mOnErrorListener.onError(mMediaPlayer, framework_err, impl_err)) {
                            return true;
                        }
                    }

                    return true;
                }
            };

    private IMediaPlayer.OnBufferingUpdateListener mBufferingUpdateListener =
            new IMediaPlayer.OnBufferingUpdateListener() {
                public void onBufferingUpdate(IMediaPlayer mp, int percent) {
                    mCurrentBufferPercentage = percent;
                }
            };

    private IMediaPlayer.OnSeekCompleteListener mSeekCompleteListener = new IMediaPlayer.OnSeekCompleteListener() {

        @Override
        public void onSeekComplete(IMediaPlayer mp) {
            if (mOnSeekCompleteListener != null) {
                mOnSeekCompleteListener.onSeekComplete(mp);
            }
        }
    };

    private IMediaPlayer.OnTimedTextListener mOnTimedTextListener = new IMediaPlayer.OnTimedTextListener() {
        @Override
        public void onTimedText(IMediaPlayer mp, IjkTimedText text) {
            if (text != null) {
                subtitleDisplay.setText(text.getText());
            }
        }
    };

    /**
     * Register a callback to be invoked when the media file
     * is loaded and ready to go.
     *
     * @param l The callback that will be run
     */
    public void setOnPreparedListener(IMediaPlayer.OnPreparedListener l) {
        mOnPreparedListener = l;
    }

    /**
     * Register a callback to be invoked when the end of a media file
     * has been reached during playback.
     *
     * @param l The callback that will be run
     */
    public void setOnCompletionListener(IMediaPlayer.OnCompletionListener l) {
        mOnCompletionListener = l;
    }

    /**
     * Register a callback to be invoked when an error occurs
     * during playback or setup.  If no listener is specified,
     * or if the listener returned false, VideoView will inform
     * the user of any errors.
     *
     * @param l The callback that will be run
     */
    public void setOnErrorListener(IMediaPlayer.OnErrorListener l) {
        mOnErrorListener = l;
    }

    /**
     * Register a callback to be invoked when an informational event
     * occurs during playback or setup.
     *
     * @param l The callback that will be run
     */
    public void setOnInfoListener(IMediaPlayer.OnInfoListener l) {
        mOnInfoListener = l;
    }

    // REMOVED: mSHCallback
    private void bindSurfaceHolder(IMediaPlayer mp, IRenderView.ISurfaceHolder holder) {
        if (mp == null)
            return;

        if (holder == null) {
            mp.setDisplay(null);
            return;
        }

        holder.bindToMediaPlayer(mp);
    }

    IRenderView.IRenderCallback mSHCallback = new IRenderView.IRenderCallback() {
        @Override
        public void onSurfaceChanged(@NonNull IRenderView.ISurfaceHolder holder, int format, int w, int h) {
            if (holder.getRenderView() != mRenderView) {
                Log.e(TAG, "onSurfaceChanged: unmatched render callback\n");
                return;
            }

            mSurfaceWidth = w;
            mSurfaceHeight = h;
            boolean isValidState = (mTargetState == STATE_PLAYING);
            boolean hasValidSize = !mRenderView.shouldWaitForResize() || (mVideoWidth == w && mVideoHeight == h);
            if (mMediaPlayer != null && isValidState && hasValidSize) {
                if (mSeekWhenPrepared != 0) {
                    seekTo(mSeekWhenPrepared);
                }
                start();
            }
        }

        @Override
        public void onSurfaceCreated(@NonNull IRenderView.ISurfaceHolder holder, int width, int height) {
            if (holder.getRenderView() != mRenderView) {
                Log.e(TAG, "onSurfaceCreated: unmatched render callback\n");
                return;
            }

            mSurfaceHolder = holder;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    if (mMediaPlayer != null)
                        bindSurfaceHolder(mMediaPlayer, holder);
                    else if (mEnableOpenVideoOnSurfaceCreate)
                        openVideo();
                }
            }).start();
        }

        @Override
        public void onSurfaceDestroyed(@NonNull IRenderView.ISurfaceHolder holder) {
            if (holder.getRenderView() != mRenderView) {
                Log.e(TAG, "onSurfaceDestroyed: unmatched render callback\n");
                return;
            }

            // after we return from this we can't use the surface any more
            mSurfaceHolder = null;
            if (mMediaPlayer != null) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        mMediaPlayer.setDisplay(null);
                    }
                }).start();
            }
        }
    };

    /*
     * release the media player in any state
     */
    public void release(boolean cleartargetstate) {
        if (cleartargetstate) {
            mUri = null;
            mEnableOpenVideoOnSurfaceCreate = true;
        }

        if (mMediaPlayer != null) {
            mMediaPlayer.reset();
            mMediaPlayer.release();
            mMediaPlayer = null;
            mCurrentState = STATE_IDLE;
            if (cleartargetstate) {
                mTargetState = STATE_IDLE;
            }
            AudioManager am = (AudioManager) mAppContext.getSystemService(Context.AUDIO_SERVICE);
            am.abandonAudioFocus(null);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        boolean ret = mScaleDetector.onTouchEvent(ev);
        boolean onDragging = mDetector.onTouchEvent(ev);
        ret = ret || onDragging;

        if (onDragging || mOnScaling ) {
            mScaleOrDragEndTime = System.currentTimeMillis();
        } else {
            if (mScaleOrDragEndTime == 0 || System.currentTimeMillis() - mScaleOrDragEndTime > IGNORE_THRESHOLD_IN_MS) {
                if (isInPlaybackState() && mMediaController != null) {
                    toggleMediaControlsVisiblity();
                }
                ret = super.onTouchEvent(ev) || ret;
                mScaleOrDragEndTime = 0;
            }
        }
        return ret;
    }

    @Override
    public boolean onTrackballEvent(MotionEvent ev) {
        if (isInPlaybackState() && mMediaController != null) {
            toggleMediaControlsVisiblity();
        }
        return false;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        boolean isKeyCodeSupported = keyCode != KeyEvent.KEYCODE_BACK &&
                keyCode != KeyEvent.KEYCODE_VOLUME_UP &&
                keyCode != KeyEvent.KEYCODE_VOLUME_DOWN &&
                keyCode != KeyEvent.KEYCODE_VOLUME_MUTE &&
                keyCode != KeyEvent.KEYCODE_MENU &&
                keyCode != KeyEvent.KEYCODE_CALL &&
                keyCode != KeyEvent.KEYCODE_ENDCALL;
        if (isInPlaybackState() && isKeyCodeSupported && mMediaController != null) {
            if (keyCode == KeyEvent.KEYCODE_HEADSETHOOK ||
                    keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                if (mMediaPlayer.isPlaying()) {
                    pause();
                    mMediaController.show();
                } else {
                    start();
                    mMediaController.hide();
                }
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY) {
                if (!mMediaPlayer.isPlaying()) {
                    start();
                    mMediaController.hide();
                }
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_MEDIA_STOP
                    || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) {
                if (mMediaPlayer.isPlaying()) {
                    pause();
                    mMediaController.show();
                }
                return true;
            } else {
                toggleMediaControlsVisiblity();
            }
        }

        return super.onKeyDown(keyCode, event);
    }

    private void toggleMediaControlsVisiblity() {
        if (mMediaController.isShowing()) {
            mMediaController.hide();
        } else {
            mMediaController.show();
        }
    }

    @Override
    public void start() {
        if (mEnableGetFrame != 0 && mUsingMediaCodec) {
            throw new RuntimeException("EnableGetFrame & UsingMediaCodec can't be enabled at the same time!!");
        }

        if (mHandlerThread == null) {
            mHandlerThread = new HandlerThread("HandlerThread");
            mHandlerThread.start();
        }

        mBackgoundHandler = new Handler(mHandlerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                if (isInPlaybackState()) {
                    mMediaPlayer.start();
                }
            }
        };

        if (isInPlaybackState()) {
            mBackgoundHandler.sendEmptyMessage(MSG_START);
            mCurrentState = STATE_PLAYING;
        }
        mTargetState = STATE_PLAYING;
    }

    @Override
    public void pause() {
        if (isInPlaybackState()) {
            if (mMediaPlayer.isPlaying()) {
                mMediaPlayer.pause();
                mCurrentState = STATE_PAUSED;
            }
        }
        mTargetState = STATE_PAUSED;
    }

    @Override
    public int getDuration() {
        if (isInPlaybackState()) {
            return (int) mMediaPlayer.getDuration();
        }

        return -1;
    }

    @Override
    public int getCurrentPosition() {
        if (isInPlaybackState()) {
            return (int) mMediaPlayer.getCurrentPosition();
        }
        return 0;
    }

    public int getRecordingPosition() {
        if (isInPlaybackState()) {
            return (int) mMediaPlayer.getRecordingPosition();
        }
        return 0;
    }

    public int getRealTime() {
        if (isInPlaybackState()) {
            return (int) mMediaPlayer.getRealTime();
        }
        return 0;
    }

    public int getAvtechPlaybackStatus() {
        if (isInPlaybackState()) {
            return (int) mMediaPlayer.getAvtechPlaybackStatus();
        }
        return 0;
    }

    @Override
    public void seekTo(int msec) {
        seekTo((long) msec);
    }

    public void seekTo(long msec) {
        if (isInPlaybackState()) {
            mMediaPlayer.seekTo(msec);
            mSeekWhenPrepared = 0;
        } else {
            mSeekWhenPrepared = msec;
        }
    }

    @Override
    public boolean isPlaying() {
        return isInPlaybackState() && mMediaPlayer.isPlaying();
    }

    @Override
    public int getBufferPercentage() {
        if (mMediaPlayer != null) {
            return mCurrentBufferPercentage;
        }
        return 0;
    }

    private boolean isInPlaybackState() {
        return (mMediaPlayer != null &&
                mCurrentState != STATE_ERROR &&
                mCurrentState != STATE_IDLE &&
                mCurrentState != STATE_PREPARING);
    }

    @Override
    public boolean canPause() {
        return true;
    }

    @Override
    public boolean canSeekBackward() {
        return true;
    }

    @Override
    public boolean canSeekForward() {
        return true;
    }

    @Override
    public int getAudioSessionId() {
        return 0;
    }

    //-------------------------
    // Extend: Aspect Ratio
    //-------------------------

    private int mCurrentAspectRatio = IRenderView.AR_ASPECT_FIT_PARENT;

    public void setAspectRatio(int aspectRatio) {
        mCurrentAspectRatio = aspectRatio;
        if (mRenderView != null) {
            mRenderView.setAspectRatio(mCurrentAspectRatio);
        }
    }

    //-------------------------
    // Extend: Render
    //-------------------------
    public static final int RENDER_NONE = 0;
    public static final int RENDER_SURFACE_VIEW = 1;
    public static final int RENDER_TEXTURE_VIEW = 2;

    private void initRenders() {
        setRender(RENDER_SURFACE_VIEW);
    }

    public IMediaPlayer createPlayer() {

        if (mUri == null) {
            return null;
        }

        if (mBufferThreshold > 0 && mLowDelay > 0) {
            Log.e(TAG, "bufferThreshold should not be used with lowDelay mode!!!");
        }

        IjkMediaPlayer ijkMediaPlayer = new IjkMediaPlayer();
        IjkMediaPlayer.native_setLogLevel(mDebug ? IjkMediaPlayer.IJK_LOG_DEBUG: IjkMediaPlayer.IJK_LOG_SILENT);
        ijkMediaPlayer.setSpeed(mSpeed);

        if (mBufferThreshold == 0 && mUri.getScheme() != null && (mUri.getScheme().equalsIgnoreCase("rtsp") || mUri.getScheme().equalsIgnoreCase("avapi") || mUri.getScheme().equalsIgnoreCase("webrtc"))) {
            ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "analyzemaxduration", 100L);
            ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "probesize", 10240L);
            ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "flush_packets", 1L);
            ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "packet-buffering", 0);
            mAccurateSeek = 0;
        }

        if (mBufferThreshold > 0) {
            ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "next-high-water-mark-ms", mBufferThreshold);
            ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "last-high-water-mark-ms", mBufferThreshold);
        }

        if (mUri.getScheme() != null && mUri.getScheme().equalsIgnoreCase("rtsp")) {
            ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "rtsp_flags", "prefer_tcp");
        }

        if (mCodecThreads > 0) {
            ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "threads", mCodecThreads);
        }

        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "low-delay", mLowDelay);
        if (mLowDelayStartThreshold > 0) {
            ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "low-delay-start-threshold", mLowDelayStartThreshold);
        }
        if (mLowDelayStopThreshold > 0) {
            ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "low-delay-stop-threshold", mLowDelayStopThreshold);
        }
        if (mAvapiTimeout > 0) {
            ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "avapi_timeout", mAvapiTimeout);
        }
        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "enable-accurate-seek", mAccurateSeek);
        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", mUsingMediaCodec ? 1 : 0);
        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-all-videos", mUsingMediaCodec ? 1 : 0);

        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "overlay-format", mPixelFormat);
        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 10);
        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 0);

        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "http-detect-range-support", 0);

        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "enable-get-frame", mEnableGetFrame);
        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "avtech_seek", mEnableAvtechSeek);
        if (mEnableAvtechSeek != 0 && mUserAgent == null) {
            mUserAgent = "TUTK Application";
        }
        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "user-agent", mUserAgent);
        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "av_api3", mAvAPIs3);
        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "av_api4", mAvAPIs4);
        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "dtls", mDtls);
        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "avapidec_av_api4", mAvAPIs4);
        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "webrtc_api", mWebRTCAPIs);
        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "hack-claire-control", mHackClaireControl);
        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "easy_mode", mEasyMode);

        if (mHttpHeaders != null) {
            String httpHeader = "";
            for (String key : mHttpHeaders.keySet()) {
                String value = mHttpHeaders.get(key);
                httpHeader = httpHeader + key + ": " + value + "\r\n";
            }
            ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "headers", httpHeader);
        }

        if (mMp4Path != null) {
            ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "video-record-path", mMp4Path);
            ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "infbuf", 1);
            ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "volume", 0);
        }

        if (mMaxBufferSize >= 0) {
            ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max-buffer-size", mMaxBufferSize);
        }

        if (mSocketTimeoutUS > 0) {
            ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "stimeout", mSocketTimeoutUS);
        }

        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "audio-session-id", mAudioSessionId);

        return ijkMediaPlayer;
    }

    public ITrackInfo[] getTrackInfo() {
        if (mMediaPlayer == null)
            return null;

        return mMediaPlayer.getTrackInfo();
    }

    public void selectTrack(int stream) {
        MediaPlayerCompat.selectTrack(mMediaPlayer, stream);
    }

    public void deselectTrack(int stream) {
        MediaPlayerCompat.deselectTrack(mMediaPlayer, stream);
    }

    public int getSelectedTrack(int trackType) {
        return MediaPlayerCompat.getSelectedTrack(mMediaPlayer, trackType);
    }

    public void setSpeed(float speed) {
        mSpeed = speed;
        IjkMediaPlayer ijkMediaPlayer = (IjkMediaPlayer) mMediaPlayer;
        if (ijkMediaPlayer != null) {
            ijkMediaPlayer.setSpeed(speed);
        }
    }

    public IjkFrame getFrame() {
        if (mMediaPlayer == null) {
            return null;
        }

        IjkFrame ret = ((IjkMediaPlayer) mMediaPlayer).getFrame();
        return ret;
    }

    public IjkAudioFrame getAudioFrame() {
        if (mMediaPlayer == null) {
            return null;
        }

        IjkAudioFrame frame = new IjkAudioFrame();
        int [] sampleRate = new int[1];
        int [] channels = new int[1];
        int [] bitsPerSample = new int[1];
        byte[] data = ((IjkMediaPlayer) mMediaPlayer).getAudioFrame(sampleRate, channels, bitsPerSample);
        if (data.length <= 0) {
            return null;
        }
        frame.data = data;
        frame.sampleRate = sampleRate[0];
        frame.channels = channels[0];
        frame.bitsPerSample = bitsPerSample[0];
        return frame;
    }

    public void enableGetFrame() {
        mEnableGetFrame = 1;
    }

    public int startVideoRecord(String path) {
        return startVideoRecord(path, 0);
    }

    public int startVideoRecord(String path, int durationInSeconds) {
        if (mMediaPlayer == null || path == null) {
            return -1;
        }

        IjkMediaPlayer ijkPlayer = (IjkMediaPlayer) mMediaPlayer;
        ijkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "video-record-duration", durationInSeconds);
        ijkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "video-record-path", path);
        return 0;
    }

    public int stopVideoRecord() {
        if (mMediaPlayer == null) {
            return -1;
        }

        IjkMediaPlayer ijkPlayer = (IjkMediaPlayer) mMediaPlayer;
        ijkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "video-record-path", "");
        return 0;
    }

    public int toMp4(String src, String dst, IMediaPlayer.DownloadListener listener) {
        return toMp4(src, dst, listener, 0);
    }

    public int toMp4(String src, String dst, IMediaPlayer.DownloadListener listener, int timeoutInSeconds) {
        if (src == null || dst == null) {
            return -1;
        }

        mVideoRecordTimeout = timeoutInSeconds;
        mVideoRecordStartTime = System.currentTimeMillis();
        mDownloadListener = listener;
        mMp4Path = dst;
        setVideoPath(src);
        start();

        if (timeoutInSeconds > 0) {
            mHandler.sendEmptyMessage(MSG_CHECK);
        }
        return 0;
    }

    public void enableAvtechSeek() {
        mEnableAvtechSeek = 1;
    }

    public void enableMediaCodec() {
        mUsingMediaCodec = true;
    }

    public void disableDebug() {
        mDebug = false;
    }

    public void setAVAPI(long avAPIs, int avapiVersion) {
        setAVAPI(avAPIs, avapiVersion, AV_SECURITY_AUTO);
    }

    public void setAVAPI(long avAPIs, int avapiVersion, int dtls) {
        if (avapiVersion == 3) {
            mAvAPIs3 = avAPIs;
            mAvAPIs4 = 0;
        } else {
            mAvAPIs3 = 0;
            mAvAPIs4 = avAPIs;
        }
        mDtls = dtls;
    }

    public void setMaxBufferSize(int size) {
        mMaxBufferSize = size;
    }

    public void setWebRTCAPI(long APIs) {
        mWebRTCAPIs = APIs;
    }

    public void setUserAgent(String userAgent) {
        mUserAgent = userAgent;
    }

    public void setCodecThreads(int num) {
        mCodecThreads = num;
    }

    public void setLowDelay(Boolean enable) {
        mLowDelay = enable ? 1 : 0;
        if (mMediaPlayer == null) {
            return;
        }

        IjkMediaPlayer ijkPlayer = (IjkMediaPlayer) mMediaPlayer;
        ijkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "low-delay", mLowDelay);
    }

    public void setLowDelayThreshold(int startThreshold, int stopThreshold) {
        mLowDelayStartThreshold = startThreshold;
        mLowDelayStopThreshold = stopThreshold;
        if (mMediaPlayer == null) {
            return;
        }

        IjkMediaPlayer ijkPlayer = (IjkMediaPlayer) mMediaPlayer;
        if (mLowDelayStartThreshold > 0 ) {
            ijkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "low-delay-start-threshold", mLowDelayStartThreshold);
        }
        if (mLowDelayStopThreshold > 0 ) {
            ijkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "low-delay-stop-threshold", mLowDelayStopThreshold);
        }
    }

    public void enableHackClaireControl() {
        mHackClaireControl = 1;
    }

    public void enableEasyMode() {
        mEasyMode = 1;
    }

    public void setAvapiTimeout(int timeoutMS) {
        mAvapiTimeout = timeoutMS;
    }

    public void setOnSeekCompleteListener(IMediaPlayer.OnSeekCompleteListener l) {
        mOnSeekCompleteListener = l;
    }

    public void setBufferThreshold(int threshold) {
        mBufferThreshold = threshold;
    }

    public void setAudioSessionId(int audioSessionId) {
        mAudioSessionId = audioSessionId;
    }

    public void setSocketTimeout(long timeoutMS) {
        mSocketTimeoutUS = timeoutMS * 1000;
    }

    public float getVolume() {
        if (mMediaPlayer == null) {
            return 1.0f;
        }

        IjkMediaPlayer ijkPlayer = (IjkMediaPlayer) mMediaPlayer;
        return ijkPlayer.getVolume();
    }

    public void setVolume(float volume) {
        if (mMediaPlayer == null) {
            return;
        }

        IjkMediaPlayer ijkPlayer = (IjkMediaPlayer) mMediaPlayer;
        ijkPlayer.setVolume(volume, volume);
    }

    public void printStatistics() {
        if (!isInPlaybackState()) {
            return;
        }

        String strDecoder;
        IjkMediaPlayer mp = (IjkMediaPlayer) mMediaPlayer;
        int vdec = mp.getVideoDecoder();
        switch (vdec) {
            case IjkMediaPlayer.FFP_PROPV_DECODER_AVCODEC:
                strDecoder = "software decoder";
                break;
            case IjkMediaPlayer.FFP_PROPV_DECODER_MEDIACODEC:
                strDecoder = "hardware decoder";
                break;
            default:
                strDecoder = "unknown decoder";
                break;
        }

        float fpsOutput = mp.getVideoOutputFramesPerSecond();
        long videoCachedDuration = mp.getVideoCachedDuration();
        long audioCachedDuration = mp.getAudioCachedDuration();
        long tcpSpeed = mp.getTcpSpeed();
        long bitRate = mp.getBitRate();
        long seekLoadDuration = mp.getSeekLoadDuration();

        String strFpsOutput = String.format(Locale.US, "%.2f", fpsOutput);
        String strVideoCachedDuration = formatedDurationMilli(videoCachedDuration);
        String strAudioCachedDuration = formatedDurationMilli(audioCachedDuration);
        String strTcpSpeed = formatedSpeed(tcpSpeed, 1000);
        String strBitRate = String.format(Locale.US, "%.2f kbs", bitRate / 1000f);
        String strSeekLoadDuration = String.format(Locale.US, "%d ms", seekLoadDuration);

        Log.i(TAG, "Decoder: " + strDecoder);
        Log.i(TAG, "FPS: " + strFpsOutput);
        Log.i(TAG, "Video Cache: " + strVideoCachedDuration);
        Log.i(TAG, "Audio Cache: " + strAudioCachedDuration);
        Log.i(TAG, "TCP Speed: " + strTcpSpeed);
        Log.i(TAG, "BitRate: " + strBitRate);
        Log.i(TAG, "Seek Cost: " + strSeekLoadDuration);
    }

    private static String formatedDurationMilli(long duration) {
        if (duration >= 1000) {
            return String.format(Locale.US, "%.2f sec", ((float) duration) / 1000);
        } else {
            return String.format(Locale.US, "%d msec", duration);
        }
    }

    private static String formatedSpeed(long bytes, long elapsed_milli) {
        if (elapsed_milli <= 0) {
            return "0 B/s";
        }

        if (bytes <= 0) {
            return "0 B/s";
        }

        float bytes_per_sec = ((float) bytes) * 1000.f / elapsed_milli;
        if (bytes_per_sec >= 1000 * 1000) {
            return String.format(Locale.US, "%.2f MB/s", ((float) bytes_per_sec) / 1000 / 1000);
        } else if (bytes_per_sec >= 1000) {
            return String.format(Locale.US, "%.1f KB/s", ((float) bytes_per_sec) / 1000);
        } else {
            return String.format(Locale.US, "%d B/s", (long) bytes_per_sec);
        }
    }

    public Bitmap toBitmap(IjkFrame frame) {
        int w = frame.width;
        int h = frame.height;
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);

        int[] pixels = new int[w * h];
        int src = 0;
        int dst = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int r = frame.pixels[src++] & 0xff;
                int g = frame.pixels[src++] & 0xff;
                int b = frame.pixels[src++] & 0xff;
                int a = frame.pixels[src++] & 0xff;
                int color = (a << 24) | (r << 16) | (g << 8) | b;
                pixels[dst++] = color;
            }
        }

        bitmap.setPixels(pixels, 0, w, 0, 0, w, h);
        return bitmap;
    }

    public long getVideoFrameTimestamp() {
        if (!isInPlaybackState()) {
            return 0;
        }

        IjkMediaPlayer mp = (IjkMediaPlayer) mMediaPlayer;
        return mp.getVideoFrameTimestamp();
    }

    public float getVideoDecodeFramesPerSecond() {
        if (!isInPlaybackState()) {
            return 0;
        }

        IjkMediaPlayer mp = (IjkMediaPlayer) mMediaPlayer;
        return mp.getVideoDecodeFramesPerSecond();
    }

    public float getVideoOutputFramesPerSecond() {
        if (!isInPlaybackState()) {
            return 0;
        }

        IjkMediaPlayer mp = (IjkMediaPlayer) mMediaPlayer;
        return mp.getVideoOutputFramesPerSecond();
    }

    public long getVideoBitRate() {
        if (!isInPlaybackState()) {
            return 0;
        }

        IjkMediaPlayer mp = (IjkMediaPlayer) mMediaPlayer;
        return mp.getBitRate();
    }

    public long getNetworkBitRate() {
        if (!isInPlaybackState()) {
            return 0;
        }

        IjkMediaPlayer mp = (IjkMediaPlayer) mMediaPlayer;
        return mp.getNetworkBitRate();
    }

    public long getVideoCachedDuration() {
        if (!isInPlaybackState()) {
            return 0;
        }

        IjkMediaPlayer mp = (IjkMediaPlayer) mMediaPlayer;
        return mp.getVideoCachedDuration();
    }

    public long getAudioCachedDuration() {
        if (!isInPlaybackState()) {
            return 0;
        }

        IjkMediaPlayer mp = (IjkMediaPlayer) mMediaPlayer;
        return mp.getAudioCachedDuration();
    }

    public float getAvdiff() {
        if (!isInPlaybackState()) {
            return 0;
        }

        IjkMediaPlayer mp = (IjkMediaPlayer) mMediaPlayer;
        return mp.getAvdiff();
    }

    private int computeStep(int delta) {
        int step = delta;
        if (Math.abs(delta) > MIN_DISTANCE) {
            step = (int) (delta * TRACKING_SPEED);
            if (step == 0) {
                if (delta < 0) {
                    step = -1;
                } else {
                    step = 1;
                }
            }
        }

        return step;
    }

    private Bitmap getSubImage(Bitmap bmp, Point center) {
        int w = bmp.getWidth();
        int h = bmp.getHeight();

        int cx = center.x;
        int cy = center.y;

        int newW = w / 2;
        int newH = h / 2;
        int x = cx - newW / 2;
        int y = cy - newH / 2;

        if (x < 0) {
            x = 0;
        }
        if (y < 0) {
            y = 0;
        }
        int delta;
        if (x + newW >= bmp.getWidth()) {
            delta = x + newW - bmp.getWidth() + 1;
            x -= delta;
        }
        if (y + newH >= bmp.getHeight()) {
            delta = y + newH - bmp.getHeight() + 1;
            y -= delta;
        }

        if (mCurrentX < 0 || mCurrentY < 0) {
            mCurrentX = w / 4;
            mCurrentY = h / 4;
        }

        int deltaX = x - mCurrentX;
        int deltaY = y - mCurrentY;
        int stepX = computeStep(deltaX);
        int stepY = computeStep(deltaY);

        mCurrentX += stepX;
        mCurrentY += stepY;

        return Bitmap.createBitmap(bmp, mCurrentX, mCurrentY, newW, newH);
    }

    private void drawRect(Bitmap bmp, List<ObjectTrackingInfo> trackingInfoList, Mode mode) {
        if (trackingInfoList.size() == 0) {
            return;
        }

        Canvas canvas = new Canvas(bmp);
        Paint p = new Paint();
        p.setStyle(Paint.Style.FILL_AND_STROKE);
        p.setAntiAlias(true);
        p.setFilterBitmap(true);
        p.setDither(true);
        p.setColor(Color.RED);
        p.setStrokeWidth(5.0f);

        Paint textPaint = new Paint();
        textPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        textPaint.setAntiAlias(true);
        textPaint.setFilterBitmap(true);
        textPaint.setDither(true);
        textPaint.setColor(Color.RED);
        textPaint.setTextSize(PAINT_TEXT_SIZE_IN_PX);

        for (ObjectTrackingInfo info : trackingInfoList) {
            Rect rect = info.rect;
            canvas.drawLine(rect.x, rect.y, rect.x + rect.width, rect.y, p);
            canvas.drawLine(rect.x, rect.y, rect.x, rect.y + rect.height, p);
            canvas.drawLine(rect.x, rect.y + rect.height, rect.x + rect.width, rect.y + rect.height, p);
            canvas.drawLine(rect.x + rect.width, rect.y, rect.x + rect.width, rect.y + rect.height, p);
            if (mode != Mode.OBJECT_DETECT) {
                break;
            }
            canvas.drawText(info.category, rect.x, rect.y - 10, textPaint);
        }
    }

    @UiThread
    public int draw(IjkFrame frame, IjkTextureView mainView, IjkTextureView subView, Mode mode) {
        if (frame == null || mainView == null || (mode == Mode.PIP && subView == null)) {
            return -1;
        }

        if (frame.trackingInfo.size() > 0) {
            mRect = frame.trackingInfo.get(0).rect;
            mLastFoundObjectTime = System.currentTimeMillis();
        }

        Bitmap full = toBitmap(frame);

        if (mLastFoundObjectTime == -1 || System.currentTimeMillis() - mLastFoundObjectTime > TRACKING_THRESHOLD_IN_SECONDS * 1000) {
            mainView.drawFromBitmap(full);
            subView.setVisibility(GONE);
            return 0;
        }

        Point rectCenter = new Point();
        if (mRect.width > 0 && mRect.height > 0) {
            rectCenter.x = mRect.x + mRect.width / 2;
            rectCenter.y = mRect.y + mRect.height / 2;
        } else {
            rectCenter.x = full.getWidth() / 2;
            rectCenter.y = full.getHeight() / 2;
        }
        Bitmap part = getSubImage(full, rectCenter);
        mainView.setOpaque(false);
        subView.setOpaque(false);

        if (mode == Mode.EPAN) {
            mainView.drawFromBitmap(part);
            subView.setVisibility(GONE);
        } else if (mode == Mode.PIP) {
            drawRect(full, frame.trackingInfo, mode);
            mainView.drawFromBitmap(part);
            subView.drawFromBitmap(full);
            subView.setVisibility(VISIBLE);
        } else if (mode == Mode.OBJECT_DETECT) {
            drawRect(full, frame.trackingInfo, mode);
            mainView.drawFromBitmap(full);
            subView.setVisibility(GONE);
        } else if (mode == Mode.NORMAL) {
            mainView.drawFromBitmap(full);
            subView.setVisibility(GONE);
        }

        return 0;
    }

    private boolean mPixelCopyDone;

    @TargetApi(Build.VERSION_CODES.N)
    public Bitmap getSurfaceViewAsBitmap() {
        if (!isInPlaybackState()) {
            return null;
        }

        if (!(mRenderView instanceof SurfaceView)) {
            Log.e(TAG, "getSurfaceViewAsBitmap only work on SurfaceView!!");
            return null;
        }

        if (mVideoWidth == 0 || mVideoHeight == 0) {
            return null;
        }

        final Bitmap bmp = Bitmap.createBitmap(mVideoWidth, mVideoHeight, Bitmap.Config.ARGB_8888);
        final HandlerThread handlerThread = new HandlerThread("PixelCopier");

        handlerThread.start();
        mPixelCopyDone = false;
        try {
            PixelCopy.request((SurfaceView) mRenderView, bmp, (copyResult) -> {
                if (copyResult != PixelCopy.SUCCESS) {
                    Log.e(TAG, "getSurfaceViewAsBitmap: PixelCopy failed!!");
                }
                handlerThread.quitSafely();
                mPixelCopyDone = true;
            }, new Handler(handlerThread.getLooper()));
        } catch (IllegalArgumentException e) {
            return null;
        }

        while (!mPixelCopyDone) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException ignored) {
            }
        }
        return bmp;
    }

    public float getMaxScale() {
        return mMaxScale;
    }

    public int setMaxScale(float maxScale) {
        if (maxScale < 1.0f) {
            return -1;
        }
        mMaxScale = maxScale;
        return 0;
    }

    public void resetView() {
        mScaleFactor = 1.0f;
        mOffsetX = 0.f;
        mOffsetY = 0.f;
        View view = mRenderView.getView();
        view.setScaleX(1.0f);
        view.setScaleY(1.0f);
        view.setTranslationX(0.f);
        view.setTranslationY(0.f);
        invalidate();
    }

    private void scale(float scale) {
        View view = mRenderView.getView();
        view.setScaleX(scale);
        view.setScaleY(scale);
        invalidate();
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    private void updateRenderViewSize() {
        View view = mRenderView.getView();
        mOriginalWidth = view.getWidth();
        mOriginalHeight = view.getHeight();
    }


    private static class ProxyVideoSink implements VideoSink {
        private VideoSink target;

        @Override
        public void onFrame(VideoFrame frame) {
            if (target == null) {
                //Logging.d(TAG, "Dropping frame in proxy because target is null.");
                return;
            }

            target.onFrame(frame);
        }

        public void setTarget(VideoSink target) {
            this.target = target;
        }
    }

    public long startWebRTC(Context context, DisplayMetrics displayMetrics, NebulaInterface nebukaAPIs, NebulaParameter param) {
        synchronized (mWebRTClock) {
            mWebrtcId = INVALID_WEBRTC_ID;
            cond = new ConditionVariable();
            mRtcClient = new NebulaRTCClient(this, nebukaAPIs);

            long webRTCApis[] = new long[1];
            final EglBase eglBase = EglBase.create();
            DataChannelParameters dataChannelParameters = null;
            peerConnectionParameters = new PeerConnectionParameters(true, false, false,
                    displayMetrics.widthPixels, displayMetrics.heightPixels, 0, 0, "H264 High",
                    true, false, 0, "OPUS", false,
                    false, false, false, false, false,
                    false, false, false, dataChannelParameters);
            peerConnectionClient = new PeerConnectionClient(
                    context, eglBase, peerConnectionParameters, this);
            PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
            peerConnectionClient.createPeerConnectionFactory(options);

            callStartedTimeMs = System.currentTimeMillis();

            remoteSinks.add(remoteProxyRenderer);
            roomConnectionParameters =
                    new RoomConnectionParameters(null, null, false, null, param);
            mRtcClient.connectToRoom(roomConnectionParameters);
            cond.block();
            if (mWebrtcId != INVALID_WEBRTC_ID) {
                peerConnectionClient.getWebRTCApi(webRTCApis);
                mWebRTCAPIs = webRTCApis[0];
            }
            return mWebrtcId;
        }
    }

    public void stopWebRTC() {
        synchronized (mWebRTClock) {
            disconnect();
        }
    }

    private void logAndToast(String msg) {
        Log.d(TAG, msg);
    }

    private boolean useCamera2() {

        return Camera2Enumerator.isSupported(getContext());
    }

    private boolean captureToTexture() {
        return true;
    }

    private @Nullable
    VideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
        final String[] deviceNames = enumerator.getDeviceNames();

        // First, try to find front facing camera
        Logging.d(TAG, "Looking for front facing cameras.");
        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                Logging.d(TAG, "Creating front facing camera capturer.");
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        // Front facing camera not found, try something else
        Logging.d(TAG, "Looking for other cameras.");
        for (String deviceName : deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                Logging.d(TAG, "Creating other camera capturer.");
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        return null;
    }

    private @Nullable
    VideoCapturer createVideoCapturer() {
        final VideoCapturer videoCapturer;
        String videoFileAsCamera = null;//getIntent().getStringExtra(EXTRA_VIDEO_FILE_AS_CAMERA);
        boolean screencaptureEnabled = false;
        if (videoFileAsCamera != null) {
            try {
                videoCapturer = new FileVideoCapturer(videoFileAsCamera);
            } catch (IOException e) {
                logAndToast("Failed to open video file for emulated camera");
                return null;
            }
        } else if (screencaptureEnabled) {
            return null;
            //return createScreenCapturer();
        } else if (useCamera2()) {
            if (!captureToTexture()) {
                logAndToast("Camera2 only supports capturing to texture. Either disable Camera2 or enable capturing to texture in the options.");
                return null;
            }

            Logging.d(TAG, "Creating capturer using camera2 API.");
            videoCapturer = createCameraCapturer(new Camera2Enumerator(getContext()));
        } else {
            Logging.d(TAG, "Creating capturer using camera1 API.");
            videoCapturer = createCameraCapturer(new Camera1Enumerator(captureToTexture()));
        }
        if (videoCapturer == null) {
            logAndToast("Failed to open camera");
            return null;
        }
        return videoCapturer;
    }

    private void disconnect() {
        remoteProxyRenderer.setTarget(null);
        localProxyVideoSink.setTarget(null);
        if (mRtcClient != null) {
            mRtcClient.disconnectFromRoom();
            mRtcClient = null;
        }
        if (peerConnectionClient != null) {
            peerConnectionClient.close();
            peerConnectionClient = null;
        }
    }

    private void onConnectedToRoomInternal(final SignalingParameters params) {
        final long delta = System.currentTimeMillis() - callStartedTimeMs;

        signalingParameters = params;
        logAndToast("Creating peer connection, delay=" + delta + "ms");
        VideoCapturer videoCapturer = null;
        if (peerConnectionParameters.videoCallEnabled) {
            videoCapturer = createVideoCapturer();
        }
        peerConnectionClient.createPeerConnection(
                localProxyVideoSink, remoteSinks, videoCapturer, signalingParameters);

        if (signalingParameters.initiator) {
            logAndToast("Creating OFFER...");
            // Create offer. Offer SDP will be sent to answering client in
            // PeerConnectionEvents.onLocalDescription event.
            peerConnectionClient.createOffer();
        } else {
            if (params.offerSdp != null) {
                peerConnectionClient.setRemoteDescription(params.offerSdp);
                logAndToast("Creating ANSWER...");
                // Create answer. Answer SDP will be sent to offering client in
                // PeerConnectionEvents.onLocalDescription event.
                peerConnectionClient.createAnswer();
            }
            if (params.iceCandidates != null) {
                // Add remote ICE candidates from room.
                for (IceCandidate iceCandidate : params.iceCandidates) {
                    peerConnectionClient.addRemoteIceCandidate(iceCandidate);
                }
            }
        }
    }

    @Override
    public void onConnectedToRoom(SignalingParameters params) {
        /*mMainLooper.post(new Runnable() {
            @Override
            public void run() {
                onConnectedToRoomInternal(params);
            }
        });*/
        onConnectedToRoomInternal(params);
    }

    @Override
    public void onRemoteDescription(SessionDescription sdp) {
        final long delta = System.currentTimeMillis() - callStartedTimeMs;
        /*mMainLooper.post(new Runnable() {
            @Override
            public void run() {
                if (peerConnectionClient == null) {
                    Log.e(TAG, "Received remote SDP for non-initilized peer connection.");
                    return;
                }
                logAndToast("Received remote " + sdp.type + ", delay=" + delta + "ms");
                peerConnectionClient.setRemoteDescription(sdp);
                if (!signalingParameters.initiator) {
                    logAndToast("Creating ANSWER...");
                    // Create answer. Answer SDP will be sent to offering client in
                    // PeerConnectionEvents.onLocalDescription event.
                    peerConnectionClient.createAnswer();
                }
            }
        });*/
        if (peerConnectionClient == null) {
            Log.e(TAG, "Received remote SDP for non-initilized peer connection.");
            return;
        }
        logAndToast("Received remote " + sdp.type + ", delay=" + delta + "ms");
        peerConnectionClient.setRemoteDescription(sdp);
        if (!signalingParameters.initiator) {
            logAndToast("Creating ANSWER...");
            // Create answer. Answer SDP will be sent to offering client in
            // PeerConnectionEvents.onLocalDescription event.
            peerConnectionClient.createAnswer();
        }
    }

    @Override
    public void onRemoteIceCandidate(IceCandidate candidate) {
        /*.post(new Runnable() {
            @Override
            public void run() {
                if (peerConnectionClient == null) {
                    Log.e(TAG, "Received ICE candidate for a non-initialized peer connection.");
                    return;
                }
                peerConnectionClient.addRemoteIceCandidate(candidate);
            }
        });*/
        if (peerConnectionClient == null) {
            Log.e(TAG, "Received ICE candidate for a non-initialized peer connection.");
            return;
        }
        peerConnectionClient.addRemoteIceCandidate(candidate);
    }

    @Override
    public void onRemoteIceCandidatesRemoved(IceCandidate[] candidates) {
        /*mMainLooper.post(new Runnable() {
            @Override
            public void run() {
                if (peerConnectionClient == null) {
                    Log.e(TAG, "Received ICE candidate removals for a non-initialized peer connection.");
                    return;
                }
                peerConnectionClient.removeRemoteIceCandidates(candidates);
            }
        });*/
        if (peerConnectionClient == null) {
            Log.e(TAG, "Received ICE candidate removals for a non-initialized peer connection.");
            return;
        }
        peerConnectionClient.removeRemoteIceCandidates(candidates);
    }

    @Override
    public void onChannelClose() {
        /*mMainLooper.post(new Runnable() {
            @Override
            public void run() {
                logAndToast("Remote end hung up; dropping PeerConnection");
                disconnect();
            }
        });*/
        logAndToast("Remote end hung up; dropping PeerConnection");
        disconnect();
    }

    @Override
    public void onChannelError(String description) {
        logAndToast(description);
        cond.open();
    }

    @Override
    public void onLocalDescription(SessionDescription sdp) {
        final long delta = System.currentTimeMillis() - callStartedTimeMs;
        /*mMainLooper.post(new Runnable() {
            @Override
            public void run() {
                if (mRtcClient != null) {
                    logAndToast("Sending " + sdp.type + ", delay=" + delta + "ms");
                    if (signalingParameters.initiator) {
                        mRtcClient.sendOfferSdp(sdp);
                    } else {
                        mRtcClient.sendAnswerSdp(sdp);
                    }
                }
                if (peerConnectionParameters.videoMaxBitrate > 0) {
                    Log.d(TAG, "Set video maximum bitrate: " + peerConnectionParameters.videoMaxBitrate);
                    peerConnectionClient.setVideoMaxBitrate(peerConnectionParameters.videoMaxBitrate);
                }
            }
        });*/
        if (mRtcClient != null) {
            logAndToast("Sending " + sdp.type + ", delay=" + delta + "ms");
            if (signalingParameters.initiator) {
                mRtcClient.sendOfferSdp(sdp);
            } else {
                mRtcClient.sendAnswerSdp(sdp);
            }
        }
        if (peerConnectionParameters.videoMaxBitrate > 0) {
            Log.d(TAG, "Set video maximum bitrate: " + peerConnectionParameters.videoMaxBitrate);
            peerConnectionClient.setVideoMaxBitrate(peerConnectionParameters.videoMaxBitrate);
        }
    }

    @Override
    public void onIceCandidate(IceCandidate candidate) {
        /*mMainLooper.post(new Runnable() {
            @Override
            public void run() {
                if (mRtcClient != null) {
                    mRtcClient.sendLocalIceCandidate(candidate);
                }
            }
        });*/
        if (mRtcClient != null) {
            mRtcClient.sendLocalIceCandidate(candidate);
        }
    }

    @Override
    public void onIceCandidatesRemoved(IceCandidate[] candidates) {
        /*mMainLooper.post(new Runnable() {
            @Override
            public void run() {
                if (mRtcClient != null) {
                    mRtcClient.sendLocalIceCandidateRemovals(candidates);
                }
            }
        });*/
        if (mRtcClient != null) {
            mRtcClient.sendLocalIceCandidateRemovals(candidates);
        }
    }

    @Override
    public void onIceConnected() {
        final long delta = System.currentTimeMillis() - callStartedTimeMs;
        /*mMainLooper.post(new Runnable() {
            @Override
            public void run() {
                logAndToast("ICE connected, delay=" + delta + "ms");
            }
        });*/
        logAndToast("ICE connected, delay=" + delta + "ms");
    }

    @Override
    public void onIceDisconnected() {
        /*mMainLooper.post(new Runnable() {
            @Override
            public void run() {
                logAndToast("ICE disconnected");
            }
        });*/
        logAndToast("ICE disconnected");
    }

    @Override
    public void onConnected() {
        final long delta = System.currentTimeMillis() - callStartedTimeMs;
        /*mMainLooper.post(new Runnable() {
            @Override
            public void run() {
                logAndToast("DTLS connected, delay=" + delta + "ms");
                //connected = true;
                //callConnected();
            }
        });*/
        logAndToast("DTLS connected, delay=" + delta + "ms");
    }

    @Override
    public void onDisconnected() {
        /*mMainLooper.post(new Runnable() {
            @Override
            public void run() {
                logAndToast("DTLS disconnected");
                //connected = false;
                disconnect();
            }
        });*/
        logAndToast("DTLS disconnected");

        //
        // <INFO>: don't trigger onError callback if player in stopped state
        //
        if (mCurrentState != STATE_IDLE) {
            mErrorListener.onError(mMediaPlayer, MediaPlayer.MEDIA_ERROR_UNKNOWN, 0);
            this.stopPlayback();
        }
        //connected = false;
        disconnect();
    }

    @Override
    public void onPeerConnectionClosed() {

    }

    @Override
    public void onPeerConnectionStatsReady(StatsReport[] reports) {

    }

    @Override
    public void onPeerConnectionError(String description) {
        logAndToast(description);
    }

    @Override
    public void onPeerConnectionCreated() {
        mWebrtcId = peerConnectionClient.getNativePeerConnecton();
        cond.open();
    }

    @Override
    public void onIceGatheringChange(PeerConnection.IceGatheringState newState) {
        if (mRtcClient != null) {
            mRtcClient.sendIceGatheringState(newState);
        }
    }
}
