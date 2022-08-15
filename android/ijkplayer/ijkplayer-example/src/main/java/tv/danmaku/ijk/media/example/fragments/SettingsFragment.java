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

package tv.danmaku.ijk.media.example.fragments;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;

import org.json.JSONException;
import org.json.JSONObject;

import cn.bingoogolapple.qrcode.core.QRCodeView;
import cn.bingoogolapple.qrcode.zxing.ZXingView;
import tv.danmaku.ijk.media.example.R;
import tv.danmaku.ijk.media.example.activities.VideoActivity;

public class SettingsFragment extends Fragment implements QRCodeView.Delegate {
    private static final int MSG_HANDLE_QRCODE = 1001;
    private EditText mEditUrl;
    private Button mButton;
    private ZXingView mQRCodeView;
    private ProgressBar mProgressBar;
    private String mQRCodeResult = "";

    @SuppressLint("HandlerLeak")
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            try {
                JSONObject json = new JSONObject(mQRCodeResult);
                String udid = json.optString("udid");
                String credential = json.optString("credential");
                String dmToken = json.optString("dmToken");
                String realm = json.optString("realm");
                VideoActivity.intentTo(getContext(), udid, credential, dmToken, realm);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            mProgressBar.setVisibility(View.GONE);
        }
    };

    public static SettingsFragment newInstance() {
        SettingsFragment f = new SettingsFragment();
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        ViewGroup viewGroup = (ViewGroup) inflater.inflate(R.layout.fragment_input_url, container, false);
        mEditUrl = (EditText) viewGroup.findViewById(R.id.edit_url);
        mButton = (Button) viewGroup.findViewById(R.id.btn_play);
        mQRCodeView = (ZXingView) viewGroup.findViewById(R.id.preview);
        mProgressBar = (ProgressBar) viewGroup.findViewById(R.id.pbLoading);

        mButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String url = mEditUrl.getText().toString();
                if (url.length() > 0) {
                    VideoActivity.intentTo(getContext(), url, "input url");
                }
            }
        });

        mQRCodeView.setDelegate(this);
        mQRCodeView.startCamera();
        return viewGroup;
    }

    @Override
    public void onResume() {
        super.onResume();
        mQRCodeView.startSpotAndShowRect();
    }

    @Override
    public void onPause() {
        mQRCodeView.stopSpotAndHiddenRect();
        super.onPause();
    }

    @Override
    public void onScanQRCodeSuccess(String s) {
        mQRCodeResult = s;
        mProgressBar.setVisibility(View.VISIBLE);
        mHandler.sendEmptyMessageDelayed(MSG_HANDLE_QRCODE, 500);
    }

    @Override
    public void onCameraAmbientBrightnessChanged(boolean b) {

    }

    @Override
    public void onScanQRCodeOpenCameraError() {

    }
}
