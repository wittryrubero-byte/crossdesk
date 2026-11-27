package cn.crossdesk.mobile;

import org.junit.Test;
import static org.junit.Assert.*;

public class TwoFingerScrollTest {
    private int total, events, notches;
    private final TwoFingerScroll.Sink sink = (amount, precise) -> {
        events++;
        if (precise) total += amount; else notches += amount;
    };

    @Test public void preciseScrollFollowsTheFingers() {
        TwoFingerScroll scroll = new TwoFingerScroll();
        scroll.begin(100, 100, 0, true);
        assertFalse("within slop", scroll.move(100, 105, 16, sink));
        assertEquals(0, events);
        // Crossing the slop scrolls the whole travel so far: 3 units per dp, down = positive.
        assertTrue(scroll.move(100, 112, 32, sink));
        assertEquals(36, total);
        assertTrue(scroll.move(100, 152, 48, sink));
        assertEquals(156, total);
        scroll.move(100, 132, 64, sink);
        assertEquals(96, total);
    }

    @Test public void horizontalSwipesDoNotScroll() {
        TwoFingerScroll scroll = new TwoFingerScroll();
        scroll.begin(100, 100, 0, true);
        assertFalse(scroll.move(130, 104, 16, sink));
        assertFalse(scroll.move(100, 160, 32, sink));
        assertFalse(scroll.scrolling());
        assertEquals(0, events);
    }

    @Test public void legacyHostsReceiveWholeNotches() {
        TwoFingerScroll scroll = new TwoFingerScroll();
        scroll.begin(0, 0, 0, false);
        scroll.move(0, 10, 16, sink);  // locks; 10 dp is less than one notch
        assertEquals(0, notches);
        scroll.move(0, 43, 32, sink);  // 43 dp in total: two notches, 11 dp carried
        assertEquals(2, notches);
        scroll.move(0, 10, 48, sink);  // back 33 dp: -22 dp, one notch up
        assertEquals(1, notches);
    }

    @Test public void fastLiftKeepsScrollingAndDecays() {
        TwoFingerScroll scroll = new TwoFingerScroll();
        scroll.begin(0, 0, 0, true);
        for (int t = 16, y = 0; t <= 96; t += 16) { y += 24; scroll.move(0, y, t, sink); }
        int beforeFling = total;
        assertTrue(scroll.release(100));
        long t = 100; int steps = 0;
        while (scroll.step(t += 16, sink)) { steps++; assertTrue(steps < 500); }
        assertTrue("inertia adds distance", total > beforeFling);
        assertFalse(scroll.flinging());
        assertFalse(scroll.scrolling());
    }

    @Test public void pausingBeforeLiftStops() {
        TwoFingerScroll scroll = new TwoFingerScroll();
        scroll.begin(0, 0, 0, true);
        for (int t = 16, y = 0; t <= 96; t += 16) { y += 24; scroll.move(0, y, t, sink); }
        assertFalse(scroll.release(400));
        assertFalse(scroll.step(416, sink));
    }

    @Test public void rebaselineKeepsAnActiveScroll() {
        TwoFingerScroll scroll = new TwoFingerScroll();
        scroll.begin(0, 0, 0, true);
        scroll.move(0, 20, 16, sink);
        assertTrue(scroll.scrolling());
        // A third finger lands: the focus jumps but no scroll is generated for it.
        int before = total;
        scroll.begin(40, 60, 20, true);
        assertTrue(scroll.scrolling());
        scroll.move(40, 70, 36, sink);
        assertEquals(before + 30, total);
    }
    @Test public void slidingLocksTheGestureAgainstPinching() {
        TwoFingerScroll scroll = new TwoFingerScroll();
        scroll.begin(100, 100, 0, true);
        scroll.move(101, 104, 16, sink);
        assertFalse("still undecided inside the slop", scroll.locked());
        assertEquals(5, Math.round(scroll.travel(103, 104)));
        scroll.move(100, 112, 32, sink);
        assertTrue(scroll.locked());
        // Horizontal swipes also commit the gesture, even though they do not scroll.
        TwoFingerScroll sideways = new TwoFingerScroll();
        sideways.begin(0, 0, 0, true);
        sideways.move(20, 0, 16, sink);
        assertTrue(sideways.locked());
        assertFalse(sideways.scrolling());
    }
}
