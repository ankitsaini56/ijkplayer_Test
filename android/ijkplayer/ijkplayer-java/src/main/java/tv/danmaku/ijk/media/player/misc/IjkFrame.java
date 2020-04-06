
package tv.danmaku.ijk.media.player.misc;

public class IjkFrame {
    public enum PixelFormat {
        RGBA,
    }

    public byte [] pixels;
    public int width;
    public int height;
    public PixelFormat pixelFormat;
}