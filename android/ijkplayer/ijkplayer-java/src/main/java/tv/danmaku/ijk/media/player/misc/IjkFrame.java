
package tv.danmaku.ijk.media.player.misc;

import java.util.List;

public class IjkFrame {
    public enum PixelFormat {
        RGBA,
    }

    public byte [] pixels;
    public int width;
    public int height;
    public PixelFormat pixelFormat;
    public List<ObjectTrackingInfo> trackingInfo;
}