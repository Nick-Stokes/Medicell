package com.sookmyung.medicell;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public class GuideOverlayView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int radiusPx = 80; // 표시용(분석용 r=80과 동일하게 시작)
    private boolean ok = false;

    public GuideOverlayView(Context context) { super(context); init(); }
    public GuideOverlayView(Context context, @Nullable AttributeSet attrs) { super(context, attrs); init(); }
    public GuideOverlayView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); init(); }

    private void init() {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(8f);
        paint.setColor(0xFFFF3B30); // 빨강
    }

    public void setRadiusPx(int r) {
        radiusPx = r;
        invalidate();
    }

    public void setOk(boolean ok) {
        this.ok = ok;
        paint.setColor(ok ? 0xFF34C759 : 0xFFFF3B30); // 초록/빨강
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        canvas.drawCircle(cx, cy, radiusPx, paint);
    }
}
