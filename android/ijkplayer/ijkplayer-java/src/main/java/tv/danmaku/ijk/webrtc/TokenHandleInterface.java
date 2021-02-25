package tv.danmaku.ijk.webrtc;

public interface TokenHandleInterface {
    public void onReceivedToken(String accessToken, String refreshToken, String clientId, String clientSecret);
}
