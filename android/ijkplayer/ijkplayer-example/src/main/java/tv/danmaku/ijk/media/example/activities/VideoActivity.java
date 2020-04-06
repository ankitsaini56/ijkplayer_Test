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

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;

import com.tutk.IOTC.AVAPIs;
import com.tutk.IOTC.IOTCAPIs;
import com.tutk.IOTC.St_AVClientStartInConfig;
import com.tutk.IOTC.St_AVClientStartOutConfig;

import tv.danmaku.ijk.media.example.R;
import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.widget.media.AndroidMediaController;
import tv.danmaku.ijk.media.player.widget.media.IjkVideoView;

public class VideoActivity extends AppCompatActivity {
    private static final String TAG = "VideoActivity";
    private static final String VIDEO_RECORD_PATH = "/sdcard/record.mp4";
    private static final String AVTECH_RTSP_URL = "YOUR_RTSP_URL";
    private static final String AVAPI_UID = "YOUR_UID";
    private static final String AVAPI_ACCOUNT = "YOUR_ACCOUNT";
    private static final String AVAPI_PASSWORD = "YOUR_PASSWORD";
    private static final int AVAPI_CHANNEL = 0;

    private String mVideoPath;
    private AndroidMediaController mMediaController;
    private IjkVideoView mVideoView;
    private boolean mDemoVideoRecord = false;
    private boolean mDemoAvtechSeek = false;
    private boolean mDemoAVAPI = false;
    private int mSeesionId;
    private int mAvIndex;

    public static Intent newIntent(Context context, String videoPath, String videoTitle) {
        Intent intent = new Intent(context, VideoActivity.class);
        intent.putExtra("videoPath", videoPath);
        intent.putExtra("videoTitle", videoTitle);
        return intent;
    }

    public static void intentTo(Context context, String videoPath, String videoTitle) {
        context.startActivity(newIntent(context, videoPath, videoTitle));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mVideoPath = getIntent().getStringExtra("videoPath");

        ActionBar actionBar = getSupportActionBar();
        mMediaController = new AndroidMediaController(this, false);
        mMediaController.setSupportActionBar(actionBar);

        mVideoView = (IjkVideoView) findViewById(R.id.video_view);
        mVideoView.setMediaController(mMediaController);

        mVideoView.enableAEC();
        mVideoView.disableMultithreadDelaying();
        if (mDemoAvtechSeek) {
            mVideoView.enableAvtechSeek();
            mVideoPath = AVTECH_RTSP_URL;
        }
        if (mDemoAVAPI) {
            IOTCAPIs.IOTC_Initialize2(0);
            AVAPIs.avInitialize(3);

            long [] avAPIs = new long[1];
            AVAPIs.avGetAPIs(avAPIs);
            mVideoView.setAVAPI(avAPIs[0]);

            mSeesionId = IOTCAPIs.IOTC_Get_SessionID();
            IOTCAPIs.IOTC_Connect_ByUID_Parallel(AVAPI_UID, mSeesionId);

            St_AVClientStartInConfig avConfig = new St_AVClientStartInConfig();
            St_AVClientStartOutConfig avOutConfig = new St_AVClientStartOutConfig();
            avConfig.iotc_channel_id = AVAPI_CHANNEL;
            avConfig.resend = 1;
            avConfig.auth_type = 0;
            avConfig.security_mode = 0;
            avConfig.timeout_sec = 20;
            avConfig.iotc_session_id = mSeesionId;
            avConfig.account_or_identity = AVAPI_ACCOUNT;
            avConfig.password_or_token = AVAPI_PASSWORD;
            mAvIndex = AVAPIs.avClientStartEx(avConfig, avOutConfig);

            //
            // <INFO>: If live url channel is not 0, need to add account, password, and session-id parameters to url.
            //
            final String AVAPI_LIVE_URL = "avapi://tutk.com/live?channel=" + AVAPI_CHANNEL + "&av-index=" + mAvIndex;

            final String AVAPI_PLAYBACK_URL = "avapi://tutk.com/playback?session-id=" + mSeesionId + "&channel=" + AVAPI_CHANNEL +
                    "&account=" + AVAPI_ACCOUNT + "&password=" + AVAPI_PASSWORD + "&start-time=1580882907&av-index=" + mAvIndex;
            mVideoPath = AVAPI_PLAYBACK_URL;
        }

        mVideoView.enableMediaCodec();
        mVideoView.setVideoPath(mVideoPath);
        mVideoView.start();
        mVideoView.setSpeed(1.0f);

        mVideoView.setOnPreparedListener(new IMediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(IMediaPlayer mp) {
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
                        Log.i(TAG, "video record complete");
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

    @Override
    protected void onDestroy() {
        mVideoView.stopPlayback();
        mVideoView.release(true);
        if (mDemoAVAPI) {
            AVAPIs.avClientStop(mAvIndex);
            IOTCAPIs.IOTC_Session_Close(mSeesionId);
            AVAPIs.avDeInitialize();
            IOTCAPIs.IOTC_DeInitialize();
        }
        super.onDestroy();
    }
}
