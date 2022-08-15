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

package tv.danmaku.ijk.media.example.activities;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.tutk.IOTC.AVAPIs;
import com.tutk.IOTC.IOTCAPIs;
import com.tutk.IOTC.NebulaAPIs;
import com.tutk.IOTC.St_AVClientStartInConfig;
import com.tutk.IOTC.St_AVClientStartOutConfig;
import com.tutk.IOTC.TUTKGlobalAPIs;
import com.tutk.IOTC.TUTKRegion;

import tv.danmaku.ijk.media.example.R;
import tv.danmaku.ijk.media.example.webrtc.NebulaImp;
import tv.danmaku.ijk.media.player.widget.media.IjkTextureView;
import tv.danmaku.ijk.webrtc.NebulaParameter;
import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.misc.IjkFrame;
import tv.danmaku.ijk.media.player.widget.media.AndroidMediaController;
import tv.danmaku.ijk.media.player.widget.media.IjkVideoView;

public class VideoActivity extends AppCompatActivity
{
    private static final String TAG = "VideoActivity";
    private static final String VIDEO_RECORD_PATH = "/sdcard/Documents/record.mp4";
    private static final String IOTC_LICENSE_KEY = "your_license_key";
    private static final String AVAPI3_UID = "your_uid";
    private static final String AVAPI3_ACCOUNT = "your_account";
    private static final String AVAPI3_PASSWORD = "your_password";
    private static final String AVAPI4_UDID = "your_uid";
    private static final String AVAPI4_CREDENTIAL = "your_credential";
    private static final String AVAPI4_DMTOKEN = "your_dmtoken";
    private static final String AVAPI4_REALM = "your_realm";
    private static final String AVAPI4_FILENAME = "20200518013511";
    private static final String WEBRTC_UDID = "your_udid";
    private static final String WEBRTC_CREDENTIAL = "your_credential";
    private static final String WEBRTC_DMTOKEN = "your_dmtoken";
    private static final String WEBRTC_REALM = "your_realm";
    private static final String WEBRTC_UDID_2 = "your_udid";
    private static final String WEBRTC_CREDENTIAL_2 = "your_credential";

    private static final int AVAPI_CHANNEL = 0;
    private static final int MSG_DRAW_OBJECT_TRACKING = 1001;
    private static final int MSG_UPDATE_CACHE_INFO = 1002;

    private String mVideoPath;
    private String mVideoPath2;
    private String mUdid;
    private String mCredential;
    private String mDmToken;
    private String mRealm;
    private AndroidMediaController mMediaController;
    private IjkVideoView mVideoView;
    private IjkVideoView mVideoView2;
    private IjkTextureView mTextureVideo;
    private IjkTextureView mTextureSubVideo;
    private TextView mCacheInfo;
    private boolean mDemoVideoRecord = false;
    private boolean mDemoAvtechSeek = false;
    private boolean mDemoAVAPI3 = false;
    private boolean mDemoAVAPI4 = false;
    private boolean mDemoWebRTC = false;
    private boolean mDemoObjectTracking = false;
    private boolean mDemoToMp4 = false;
    private boolean mDemoPlayRawMp4 = false;
    private int mSeesionId;
    private int mAvIndex;
    private long [] mClientCtx = new long[1];
    private int mWebRTCClientCount = 1;

    @SuppressLint("HandlerLeak")
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch(msg.what) {
                case MSG_DRAW_OBJECT_TRACKING:
                    IjkFrame frame = mVideoView.getFrame();
                    mVideoView.draw(frame, mTextureVideo, mTextureSubVideo, IjkVideoView.Mode.PIP);
                    sendEmptyMessageDelayed(MSG_DRAW_OBJECT_TRACKING, 50);
                    break;
                case MSG_UPDATE_CACHE_INFO:
                    long v = mVideoView.getVideoCachedDuration();
                    long a = mVideoView.getAudioCachedDuration();
                    String info = "a/v cache (msec): " + a + " / " + v;
                    mCacheInfo.setText(info);
                    sendEmptyMessageDelayed(MSG_UPDATE_CACHE_INFO, 1000);
                    break;
            }
        }
    };

    private NebulaAPIs.NebulaClientConnectStateFn mCB = new NebulaAPIs.NebulaClientConnectStateFn() {
        @Override
        public void connect_state_handler(long clientCtx, int state) {
        }
    };

    public static Intent newIntent(Context context, String videoPath, String videoTitle) {
        Intent intent = new Intent(context, VideoActivity.class);
        intent.putExtra("videoPath", videoPath);
        intent.putExtra("videoTitle", videoTitle);
        return intent;
    }

    public static void intentTo(Context context, String videoPath, String videoTitle) {
        context.startActivity(newIntent(context, videoPath, videoTitle));
    }

    public static void intentTo(Context context, String udid, String credential, String dmToken, String realm) {
        Intent intent = new Intent(context, VideoActivity.class);
        intent.putExtra("udid", udid);
        intent.putExtra("credential", credential);
        intent.putExtra("dmToken", dmToken);
        intent.putExtra("realm", realm);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mVideoPath = getIntent().getStringExtra("videoPath");
        mUdid = getIntent().getStringExtra("udid");
        mCredential = getIntent().getStringExtra("credential");
        mDmToken = getIntent().getStringExtra("dmToken");
        mRealm = getIntent().getStringExtra("realm");

        ActionBar actionBar = getSupportActionBar();
        mMediaController = new AndroidMediaController(this, false);
        mMediaController.setSupportActionBar(actionBar);

        mVideoView = (IjkVideoView) findViewById(R.id.video_view);
        mVideoView.setMediaController(mMediaController);
        mVideoView2 = (IjkVideoView) findViewById(R.id.video_view2);
        mVideoView2.setMediaController(mMediaController);
        if(!mDemoWebRTC || (mDemoWebRTC && mWebRTCClientCount == 1)) {
            mVideoView2.setVisibility(View.INVISIBLE);
            mVideoView.getLayoutParams().height = LinearLayout.LayoutParams.MATCH_PARENT;
        }
        mTextureVideo = (IjkTextureView) findViewById(R.id.texture_video);
        mTextureSubVideo = (IjkTextureView) findViewById(R.id.texture_subvideo);
        mCacheInfo = (TextView) findViewById(R.id.cache_info);

        if (mDemoToMp4) {
            mVideoView.toMp4(mVideoPath, VIDEO_RECORD_PATH, result -> Log.i(TAG, "mp4 generated"));
            return;
        }

        if (mDemoObjectTracking) {
            mDemoAVAPI4 = true;
        }
        if (mDemoAvtechSeek) {
            mVideoView.enableAvtechSeek();
        }

        TUTKGlobalAPIs.TUTK_SDK_Set_License_Key(IOTC_LICENSE_KEY);
        if (mDemoAVAPI3) {
            IOTCAPIs.IOTC_Initialize2(0);
            AVAPIs.avInitialize(3);

            long [] avAPIs = new long[1];
            AVAPIs.avGetAPIs(avAPIs);
            mVideoView.setAVAPI(avAPIs[0], 3);

            mSeesionId = IOTCAPIs.IOTC_Get_SessionID();
            IOTCAPIs.IOTC_Connect_ByUID_Parallel(AVAPI3_UID, mSeesionId);

            St_AVClientStartInConfig avConfig = new St_AVClientStartInConfig();
            St_AVClientStartOutConfig avOutConfig = new St_AVClientStartOutConfig();
            avConfig.iotc_channel_id = AVAPI_CHANNEL;
            avConfig.resend = 1;
            avConfig.auth_type = 0;
            avConfig.security_mode = 2;
            avConfig.timeout_sec = 20;
            avConfig.iotc_session_id = mSeesionId;
            avConfig.account_or_identity = AVAPI3_ACCOUNT;
            avConfig.password_or_token = AVAPI3_PASSWORD;
            mAvIndex = AVAPIs.avClientStartEx(avConfig, avOutConfig);

            //
            // <INFO>: If live url channel is not 0, need to add account, password, and session-id parameters to url.
            //
            final String AVAPI_LIVE_URL = "avapi://tutk.com/live?av-index=" + mAvIndex;
            final String AVAPI_PLAYBACK_URL = "avapi://tutk.com/playback?session-id=" + mSeesionId + "&channel=" + AVAPI_CHANNEL +
                    "&account=" + AVAPI3_ACCOUNT + "&password=" + AVAPI3_PASSWORD + "&start-time=1580882907&av-index=" + mAvIndex;
            mVideoPath = AVAPI_LIVE_URL;
        }
        if (mDemoAVAPI4 || mUdid != null) {
            String udid = mUdid != null ? mUdid : AVAPI4_UDID;
            String credential = mCredential != null ? mCredential : AVAPI4_CREDENTIAL;
            String dmToken = mDmToken != null ? mDmToken : AVAPI4_DMTOKEN;
            String realm = mRealm != null ? mRealm : AVAPI4_REALM;
            //
            // <INFO> set region to CN if your device is added to CN region
            //
            TUTKGlobalAPIs.TUTK_SDK_Set_Region(TUTKRegion.REGION_US);
            IOTCAPIs.IOTC_Initialize2(0);
            AVAPIs.avInitialize(100);
            NebulaAPIs.Nebula_Initialize();

            long [] avAPIs = new long[1];
            AVAPIs.avGetAPIs(avAPIs);
            mVideoView.setAVAPI(avAPIs[0], 4);
            mAvIndex = getAvIndex(udid, credential, dmToken, realm);

            final String AVAPI_LIVE_URL = "avapi://tutk.com/live?av-index=" + mAvIndex;
            final String AVAPI_PLAYBACK_URL = "avapi://tutk.com/playback?session-id=" + mSeesionId + "&channel=1" +
                    "&filename=" + AVAPI4_FILENAME + "&av-index=" + mAvIndex;
            mVideoPath = AVAPI_LIVE_URL;
        }
        if(mDemoWebRTC) {
            NebulaAPIs.Nebula_Initialize();

            long [] ctx = new long[1];
            NebulaAPIs.Nebula_Client_New_From_String(WEBRTC_UDID, WEBRTC_CREDENTIAL, ctx);
            NebulaAPIs.Nebula_Client_Connect(ctx[0], new NebulaAPIs.NebulaClientConnectStateFn() {
                @Override
                public void connect_state_handler(long client_ctx, int state) {
                }
            }, 30000, null);
            NebulaParameter param = new NebulaParameter(WEBRTC_DMTOKEN, WEBRTC_REALM, 1, IjkVideoView.STREAM_TYPE_AUDIO_AND_VIDEO, null, null, null);
            long id = mVideoView.startWebRTC(getApplicationContext(), getDisplayMetrics(), new NebulaImp(ctx[0]), param);
            if(id != IjkVideoView.INVALID_WEBRTC_ID) {
                mVideoPath = "webrtc://tutk.com?pc_id=" + id;
            }else {
                Log.e(TAG, "StartWebRTC failed");
            }
            mVideoView.setWebRTCMic(true);

            if(mWebRTCClientCount == 2) {
                long [] ctx2 = new long[1];
                NebulaAPIs.Nebula_Client_New_From_String(WEBRTC_UDID_2, WEBRTC_CREDENTIAL_2, ctx2);
                NebulaAPIs.Nebula_Client_Connect(ctx2[0], new NebulaAPIs.NebulaClientConnectStateFn() {
                    @Override
                    public void connect_state_handler(long client_ctx, int state) {
                    }
                }, 30000, null);
                param = new NebulaParameter(WEBRTC_DMTOKEN, WEBRTC_REALM);
                id = mVideoView2.startWebRTC(getApplicationContext(), getDisplayMetrics(), new NebulaImp(ctx2[0]), param);
                if (id != IjkVideoView.INVALID_WEBRTC_ID) {
                    mVideoPath2 = "webrtc://tutk.com?pc_id=" + id;
                } else {
                    Log.e(TAG, "StartWebRTC failed");
                }
            }
        }

        if (mDemoObjectTracking) {
            mTextureVideo.setVisibility(View.VISIBLE);
            mTextureSubVideo.setVisibility(View.VISIBLE);
            mVideoView.enableGetFrame();
        }
        if (!mDemoObjectTracking) {
            mVideoView.enableMediaCodec();
            mVideoView2.enableMediaCodec();
        }
        if (mDemoPlayRawMp4) {
            mVideoPath = "android.resource://tv.danmaku.ijk.media.example/" + R.raw.h265;
        }

        mVideoView.setVideoPath(mVideoPath);
        mVideoView.start();
        mVideoView.setSpeed(1.0f);

        if(mDemoWebRTC && mWebRTCClientCount == 2) {
            mVideoView2.setVideoPath(mVideoPath2);
            mVideoView2.start();
            mVideoView2.setSpeed(1.0f);
        }

        mVideoView.setOnPreparedListener(new IMediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(IMediaPlayer mp) {
                if (mDemoObjectTracking) {
                    mHandler.sendEmptyMessageDelayed(MSG_DRAW_OBJECT_TRACKING, 0);
                }
                mHandler.sendEmptyMessageDelayed(MSG_UPDATE_CACHE_INFO, 0);

                //
                // <INFO>: video record only works well for rtsp source
                //
                if (mDemoVideoRecord) {
                    mVideoView.startVideoRecord(VIDEO_RECORD_PATH);
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            mVideoView.stopVideoRecord();
                        }
                    }, 10000);
                }

                if (mDemoAvtechSeek) {
                    mVideoView.seekTo(System.currentTimeMillis() - 60 * 60 * 1000);
                }
            }
        });

        mVideoView.setOnInfoListener(new IMediaPlayer.OnInfoListener() {
            @Override
            public boolean onInfo(IMediaPlayer mp, int what, int extra) {
                switch (what) {
                    case IMediaPlayer.MEDIA_INFO_FRAME_DROPPED:
                        Log.i(TAG, "frame dropped");
                        break;
                    case IMediaPlayer.MEDIA_INFO_FRAME_NOT_DROPPED:
                        Log.i(TAG, "resume from frame dropped");
                        break;
                    case IMediaPlayer.MEDIA_INFO_VIDEO_RECORD_COMPLETE:
                        if (extra == 0) {
                            Log.i(TAG, "video record success");
                        } else {
                            Log.i(TAG, "video record fail");
                        }
                        break;
                }
                return false;
            }
        });

        mVideoView.setOnSeekCompleteListener(new IMediaPlayer.OnSeekCompleteListener() {
            @Override
            public void onSeekComplete(IMediaPlayer mp) {
                Log.i(TAG, "seek complete");
            }
        });
    }

    private int getAvIndex(String udid, String certificate, String dmToken, String realm) {
        NebulaAPIs.Nebula_Client_New_From_String(udid, certificate, mClientCtx);
        mSeesionId = IOTCAPIs.IOTC_Client_Connect_By_Nebula(mClientCtx[0], dmToken, realm, 30000, null);
        Log.d(TAG, "IOTC_Client_Connect_By_Nebula ret: " + mSeesionId);

        St_AVClientStartInConfig avConfig = new St_AVClientStartInConfig();
        St_AVClientStartOutConfig avOutConfig = new St_AVClientStartOutConfig();
        avConfig.iotc_channel_id = AVAPI_CHANNEL;
        avConfig.iotc_session_id = mSeesionId;
        avConfig.resend = 1;
        avConfig.auth_type = 2;
        avConfig.security_mode = 2;
        avConfig.timeout_sec = 20;
        return AVAPIs.avClientStartEx(avConfig, avOutConfig);
    }

    @Override
    protected void onDestroy() {
        if (mDemoObjectTracking) {
            mHandler.removeMessages(MSG_DRAW_OBJECT_TRACKING);
        }
        mVideoView.stopPlayback();
        mVideoView.release(true);
        if(mDemoWebRTC && mWebRTCClientCount == 2) {
            mVideoView2.stopPlayback();
            mVideoView2.release(true);
        }
        if (mDemoAVAPI3) {
            AVAPIs.avClientExit(mAvIndex, mSeesionId);
            AVAPIs.avClientStop(mAvIndex);
            IOTCAPIs.IOTC_Session_Close(mSeesionId);
        }
        if (mDemoAVAPI4) {
            NebulaAPIs.Nebula_Client_Delete(mClientCtx[0]);
            AVAPIs.avClientStop(mAvIndex);
            IOTCAPIs.IOTC_Session_Close(mSeesionId);
        }
        if(mDemoWebRTC) {
            mVideoView.stopWebRTC();
            if(mWebRTCClientCount == 2) {
                mVideoView2.stopWebRTC();
            }
        }
        super.onDestroy();
    }

    @TargetApi(17)
    private DisplayMetrics getDisplayMetrics() {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        WindowManager windowManager =
                (WindowManager) getApplication().getSystemService(Context.WINDOW_SERVICE);
        windowManager.getDefaultDisplay().getRealMetrics(displayMetrics);
        return displayMetrics;
    }
}
