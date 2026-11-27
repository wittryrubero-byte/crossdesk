package cn.crossdesk.mobile;

import java.util.Locale;

/**
 * Estimates the stream bitrate for a quality/frame-rate choice, mirroring
 * MiniRTC's screen-content model (resolution_adapter.cpp): about 2.7 bits per
 * pixel per second at 30 fps, scaled by sqrt(fps / 30) and the adaptation
 * preference's headroom. This is the bitrate needed to keep the full
 * resolution while the picture is changing; a still screen uses far less,
 * and on a fast network the encoder may go up to the 20 Mbps network cap.
 */
final class VideoTrafficEstimate {
    static final long MAX_NETWORK_BPS = 20_000_000L;
    private static final double BITS_PER_PIXEL_30FPS = 2.70;

    private VideoTrafficEstimate() {}

    static long maxPixels(int quality) {
        return quality >= 2 ? 3840L * 2160 : quality == 1 ? 1920L * 1080 : 1280L * 720;
    }

    /** preference: 0 frame rate, 1 quality, 2 balanced (crossdesk::VideoSettings). */
    static long bitsPerSecond(int quality, int fps, int preference, int sourceWidth, int sourceHeight) {
        long source = sourceWidth > 0 && sourceHeight > 0 ? (long) sourceWidth * sourceHeight : 1920L * 1080;
        long pixels = Math.min(source, maxPixels(quality));
        double headroom = preference == 0 ? 1.25 : preference == 2 ? 1.12 : 1.0;
        double bps = BITS_PER_PIXEL_30FPS * pixels * Math.sqrt(Math.max(1, fps) / 30.0) * headroom;
        return Math.min(MAX_NETWORK_BPS, Math.round(bps));
    }

    /** "1.3 MB/s", or "0.45 MB/s" below 1 MB/s. */
    static String megabytesPerSecond(long bitsPerSecond) {
        double value = bitsPerSecond / 8.0 / 1_000_000.0;
        return String.format(Locale.ROOT, value < 1 ? "%.2f MB/s" : "%.1f MB/s", value);
    }

    /** Live traffic for the status line: KB/s below 1 MB/s. */
    static String rate(long bitsPerSecond) {
        if (bitsPerSecond < 0) return "—";
        double bytes = bitsPerSecond / 8.0;
        if (bytes >= 1_000_000) return String.format(Locale.ROOT, "%.1f MB/s", bytes / 1_000_000);
        return String.format(Locale.ROOT, "%.0f KB/s", bytes / 1000);
    }
}
