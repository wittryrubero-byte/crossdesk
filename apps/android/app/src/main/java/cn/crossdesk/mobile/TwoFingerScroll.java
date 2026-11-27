package cn.crossdesk.mobile;

/**
 * Two-finger vertical scrolling with inertia, kept free of Android views so
 * it can be unit tested. Positions are in dp and times in milliseconds.
 *
 * Fingers moving down scroll the remote content down with them (positive
 * wheel), matching the existing notch behaviour. Hosts that advertise
 * precise wheel input receive 1/120-notch units for smooth scrolling;
 * others receive whole notches as before.
 */
final class TwoFingerScroll {
    interface Sink { void scroll(int amount, boolean precise); }

    /** Precise units per dp of finger travel: 40 dp moves one notch (120). */
    static final float UNITS_PER_DP = 3f;
    /** Finger travel per notch for hosts without precise wheel input. */
    static final float LEGACY_DP_PER_NOTCH = 16f;
    /** Travel before the gesture commits to scrolling or to doing nothing. */
    static final float SLOP_DP = 8f;
    /** Lift-off speed needed to keep scrolling after the fingers leave. */
    static final float MIN_FLING_DP_PER_S = 300f;
    /** Inertia ends below this speed. */
    static final float STOP_DP_PER_S = 30f;
    /** Exponential decay time constant of the inertia. */
    static final float FLING_TAU_S = .33f;
    private static final float MAX_DP_PER_S = 8000f;

    private boolean precise, active, locked, scrolling, flinging;
    private float startX, startY, lastY, remainder, velocity;
    private long lastTime;

    boolean scrolling() { return scrolling; }
    boolean flinging() { return flinging; }

    /** Starts a gesture, or re-baselines it when the finger count changes. */
    void begin(float xDp, float yDp, long timeMs, boolean precise) {
        boolean keep = active && scrolling;
        this.precise = precise; active = true; flinging = false;
        lastY = yDp; lastTime = timeMs;
        if (keep) return;
        startX = xDp; startY = yDp;
        locked = scrolling = false; remainder = 0; velocity = 0;
    }

    /** Feeds the fingers' focus point; returns true once the gesture scrolls. */
    boolean move(float xDp, float yDp, long timeMs, Sink sink) {
        if (!active) return false;
        if (!locked) {
            float dx = xDp - startX, dy = yDp - startY;
            if (Math.hypot(dx, dy) < SLOP_DP) return false;
            // Mostly horizontal swipes do not scroll; this gesture stays idle.
            locked = true; scrolling = Math.abs(dy) >= .5f * Math.abs(dx);
            if (!scrolling) return false;
        }
        if (!scrolling) return false;
        float delta = yDp - lastY;
        long dt = timeMs - lastTime;
        if (dt > 0) {
            float instant = Math.max(-MAX_DP_PER_S, Math.min(MAX_DP_PER_S, delta * 1000f / dt));
            // Favour recent motion so a deliberate stop before lifting ends the scroll.
            velocity = dt > 100 ? instant : .6f * instant + .4f * velocity;
        }
        lastY = yDp; lastTime = timeMs;
        emit(delta, sink);
        return true;
    }

    /** Called when fewer than two fingers remain; returns true if inertia starts. */
    boolean release(long timeMs) {
        active = false;
        // A pause before lifting means the user stopped on purpose.
        if (!scrolling || timeMs - lastTime > 80 || Math.abs(velocity) < MIN_FLING_DP_PER_S) {
            cancel();
            return false;
        }
        flinging = true; lastTime = timeMs;
        return true;
    }

    /** Advances inertia to timeMs; returns false once it has stopped. */
    boolean step(long timeMs, Sink sink) {
        if (!flinging) return false;
        float dt = Math.max(0, Math.min(.1f, (timeMs - lastTime) / 1000f));
        lastTime = timeMs;
        float next = velocity * (float) Math.exp(-dt / FLING_TAU_S);
        // Distance covered by the exponentially decaying speed over dt.
        emit((velocity - next) * FLING_TAU_S, sink);
        velocity = next;
        if (Math.abs(velocity) < STOP_DP_PER_S) { cancel(); return false; }
        return true;
    }

    void cancel() {
        active = flinging = locked = scrolling = false;
        remainder = 0; velocity = 0;
    }

    private void emit(float deltaDp, Sink sink) {
        if (precise) {
            remainder += deltaDp * UNITS_PER_DP;
            int units = (int) remainder;
            if (units == 0) return;
            remainder -= units;
            sink.scroll(units, true);
        } else {
            remainder += deltaDp;
            while (Math.abs(remainder) >= LEGACY_DP_PER_NOTCH) {
                int notch = remainder > 0 ? 1 : -1;
                remainder -= notch * LEGACY_DP_PER_NOTCH;
                sink.scroll(notch, false);
            }
        }
    }
}
