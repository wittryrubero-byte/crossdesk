package cn.crossdesk.mobile;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * A slim, draggable scroll wheel. Clicks and drags use touch gestures on the
 * video (tap, two-finger tap, long press), so the bar keeps only the wheel to
 * leave as much of the remote screen visible as possible.
 */
@SuppressLint("ViewConstructor")
final class VirtualMouseBar extends LinearLayout {
    static final int WIDTH_DP = 36, HEIGHT_DP = 104;
    private static final int HANDLE_DP = 18;
    final FrameLayout dragHandle;
    private final RemoteMouseInput input;
    private final MobileUi ui;
    private final WheelKey wheel;
    private int generation;

    VirtualMouseBar(MobileUi ui, RemoteMouseInput input) {
        super(ui.context); this.ui = ui; this.input = input;
        setOrientation(VERTICAL);
        GradientDrawable shell = ui.background(Color.WHITE, 18);
        shell.setStroke(ui.dp(.5f), 0xFFD4DAE2);
        setBackground(shell);
        // Let the remote screen show through the bar a little.
        setAlpha(.82f);
        dragHandle = new FrameLayout(ui.context);
        dragHandle.setContentDescription(L10n.tr("拖动滚轮"));
        View grip = new View(ui.context);
        grip.setBackground(ui.background(MobileUi.SECONDARY, 2));
        dragHandle.addView(grip, new FrameLayout.LayoutParams(ui.dp(16), ui.dp(3), Gravity.CENTER));
        addView(dragHandle, new LayoutParams(LayoutParams.MATCH_PARENT, ui.dp(HANDLE_DP)));
        wheel = new WheelKey(ui);
        setUpWheel();
        LayoutParams wheelParams = new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1);
        wheelParams.bottomMargin = ui.dp(4);
        addView(wheel, wheelParams);
        input.didCancel = this::resetTouches;
        setContentDescription(L10n.tr("滚轮"));
    }

    @SuppressLint("ClickableViewAccessibility") // Touch scrolls; performClick serves accessibility only.
    private void setUpWheel() {
        wheel.setContentDescription(L10n.tr("鼠标滚轮，上下滑动，可滑出按键范围"));
        wheel.setClickable(true); wheel.setFocusable(true);
        highlight(false);
        wheel.setOnClickListener(v -> { wheel.advance(ui.dp(10)); input.scroll(-1); });
        wheel.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setScrollable(true);
                info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
                info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
            }
            @Override public boolean performAccessibilityAction(View host, int action, android.os.Bundle args) {
                if (action == android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD || action == android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
                    int direction = action == android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD ? -1 : 1;
                    wheel.advance(-direction * ui.dp(10));
                    input.scroll(direction); return true;
                }
                return super.performAccessibilityAction(host, action, args);
            }
        });
        wheel.setOnTouchListener(new View.OnTouchListener() {
            int pointer = -1, startedGeneration;
            float lastY, remainder;
            @Override public boolean onTouch(View v, MotionEvent event) {
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) {
                    pointer = event.getPointerId(0); startedGeneration = generation;
                    lastY = event.getRawY(); remainder = 0;
                    highlight(true);
                    return true;
                }
                if (startedGeneration != generation || pointer == -1) return true;
                if (action == MotionEvent.ACTION_MOVE) {
                    int p = event.findPointerIndex(pointer);
                    if (p < 0) return true;
                    // Use screen coordinates even if another finger moves the bar.
                    float y = event.getRawY() + (event.getY(p) - event.getY());
                    float dy = y - lastY;
                    lastY = y;
                    wheel.advance(dy);
                    // Like a mouse wheel: finger down scrolls the page down (negative wheel).
                    if (input.preciseWheel()) {
                        remainder -= dy / ui.dp(1) * TwoFingerScroll.UNITS_PER_DP;
                        int units = (int) remainder;
                        if (units != 0) { remainder -= units; input.scrollPrecise(units); }
                    } else {
                        remainder += dy;
                        float step = ui.dp(10);
                        int ticks = (int)(remainder / step);
                        for (int i = 0; i < Math.min(Math.abs(ticks), 32); i++) input.scroll(ticks > 0 ? -1 : 1);
                        remainder -= ticks * step;
                    }
                }
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL ||
                    (action == MotionEvent.ACTION_POINTER_UP && event.getPointerId(event.getActionIndex()) == pointer)) {
                    pointer = -1; remainder = 0; highlight(false);
                }
                return true;
            }
        });
    }
    private void highlight(boolean pressed) {
        wheel.setPressed(pressed);
        wheel.setTextColor(pressed ? Color.WHITE : 0xB8303640);
        GradientDrawable face = ui.background(pressed ? MobileUi.BLUE : 0xFFEDF0F4, 8);
        int inset = ui.dp(5);
        wheel.setBackground(new InsetDrawable(face, inset, 0, inset, 0));
    }
    void cancelInput() {
        input.releaseAll();
    }
    private void resetTouches() {
        generation++;
        highlight(false);
    }
    /** Flat, uniformly colored treads follow the finger, wrapping every 5 dp. */
    private static final class WheelKey extends TextView {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path clip = new Path();
        private final float density;
        private float phase = 2, clipHeight;
        WheelKey(MobileUi ui) {
            super(ui.context); density = getResources().getDisplayMetrics().density;
        }
        void advance(float distance) {
            phase = (phase + distance / density * .65f) % 5;
            invalidate();
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float height = Math.max(0, getHeight() / density - 16);
            if (height != clipHeight) {
                clipHeight = height; clip.reset();
                clip.addRoundRect(new RectF(0, 0, 12, height), 2, 2, Path.Direction.CW);
            }
            canvas.save();
            canvas.translate((getWidth()-12*density)/2f, 8*density);
            canvas.scale(density,density);
            canvas.clipPath(clip);
            paint.setColor(getCurrentTextColor());
            for (int i=-1;i<=height/5+1;i++) {
                float y=i*5+phase;
                canvas.drawRect(0,y,12,y+1.5f,paint);
            }
            canvas.restore();
        }
    }
    @Override protected void onDetachedFromWindow() { cancelInput(); super.onDetachedFromWindow(); }
    @Override public void onWindowFocusChanged(boolean focused) { super.onWindowFocusChanged(focused); if (!focused) cancelInput(); }
}
