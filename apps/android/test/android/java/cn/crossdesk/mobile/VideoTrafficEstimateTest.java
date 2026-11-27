package cn.crossdesk.mobile;

import org.junit.Test;
import static org.junit.Assert.*;

public class VideoTrafficEstimateTest {
    @Test public void matchesTheEncoderModel() {
        // 1080p at 30 fps with quality priority: 2.7 * 1920 * 1080 ≈ 5.6 Mbps.
        assertEquals(5_598_720, VideoTrafficEstimate.bitsPerSecond(1, 30, 1, 1920, 1080));
        // 60 fps costs sqrt(2) more, not double.
        assertEquals(Math.round(5_598_720 * Math.sqrt(2)), VideoTrafficEstimate.bitsPerSecond(1, 60, 1, 1920, 1080));
        // Low quality caps a 4K desktop at 720p.
        assertEquals(VideoTrafficEstimate.bitsPerSecond(0, 30, 1, 1280, 720),
                     VideoTrafficEstimate.bitsPerSecond(0, 30, 1, 3840, 2160));
        // High quality never exceeds the network cap.
        assertEquals(VideoTrafficEstimate.MAX_NETWORK_BPS, VideoTrafficEstimate.bitsPerSecond(2, 144, 0, 3840, 2160));
        // Unknown source sizes assume 1080p.
        assertEquals(VideoTrafficEstimate.bitsPerSecond(2, 60, 2, 1920, 1080),
                     VideoTrafficEstimate.bitsPerSecond(2, 60, 2, 0, 0));
    }

    @Test public void formatsRates() {
        assertEquals("0.70 MB/s", VideoTrafficEstimate.megabytesPerSecond(5_598_720));
        assertEquals("2.5 MB/s", VideoTrafficEstimate.megabytesPerSecond(20_000_000));
        assertEquals("125 KB/s", VideoTrafficEstimate.rate(1_000_000));
        assertEquals("1.5 MB/s", VideoTrafficEstimate.rate(12_000_000));
        assertEquals("—", VideoTrafficEstimate.rate(-1));
    }
}
