package com.fivehundredpx.android.blur;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.support.v8.renderscript.Allocation;
import android.support.v8.renderscript.Element;
import android.support.v8.renderscript.RenderScript;
import android.support.v8.renderscript.ScriptIntrinsicBlur;
import android.util.AttributeSet;
import android.view.View;

/**
 * A custom view for presenting a dynamically blurred version of another view's content.
 * <p/>
 * Use {@link #setBlurredView(android.view.View)} to set up the reference to the view to be blurred.
 * After that, call {@link #invalidate()} to trigger blurring whenever necessary.
 */
public class BlurringView extends View {

    public BlurringView(Context context) {
        this(context, null);
    }

    public BlurringView(Context context, AttributeSet attrs) {
        super(context, attrs);

        final Resources res = getResources();
        final int defaultBlurRadius = res.getInteger(R.integer.default_blur_radius);
        final int defaultDownsampleFactor = res.getInteger(R.integer.default_downsample_factor);
        final int defaultOverlayColor = res.getColor(R.color.default_overlay_color);

        initializeRenderScript(context);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.PxBlurringView);
        setBlurRadius(a.getInt(R.styleable.PxBlurringView_blurRadius, defaultBlurRadius));
        setDownsampleFactor(a.getInt(R.styleable.PxBlurringView_downsampleFactor,
                defaultDownsampleFactor));
        setOverlayColor(a.getColor(R.styleable.PxBlurringView_overlayColor, defaultOverlayColor));
        a.recycle();
    }

    public void setBlurredView(View blurredView) {
        mBlurredView = blurredView;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mBlurredView != null) {
            if (prepare()) {
                // If the background of the blurred view is a color drawable, we use it to clear
                // the blurring canvas, which ensures that edges of the child views are blurred
                // as well; otherwise we clear the blurring canvas with a transparent color.
                if (mBlurredView.getBackground() != null && mBlurredView.getBackground() instanceof ColorDrawable) {
                    mBitmapToBlurClip.eraseColor(((ColorDrawable) mBlurredView.getBackground()).getColor());
                } else {
                    mBitmapToBlurClip.eraseColor(Color.TRANSPARENT);
                }

                mBlurringCanvas.save();
                mBlurringCanvas.translate(mBlurredView.getX() - getX(), mBlurredView.getY() - getY());
                mBlurredView.draw(mBlurringCanvas);
                mBlurringCanvas.restore();

                blur();

                canvas.save();
                canvas.scale(mDownsampleFactor, mDownsampleFactor);
                canvas.drawBitmap(mBlurredBitmapClip, 0, 0, null);
                canvas.restore();
                mBitmapToBlurClip.eraseColor(Color.TRANSPARENT);
            }
            canvas.drawColor(mOverlayColor);
        }
    }

    public void setBlurRadius(int radius) {
        mBlurScript.setRadius(radius);
    }

    public void setDownsampleFactor(int factor) {
        if (factor <= 0) {
            throw new IllegalArgumentException("Downsample factor must be greater than 0.");
        }

        if (mDownsampleFactor != factor) {
            mDownsampleFactor = factor;
            mDownsampleFactorChanged = true;
        }
    }

    public void setOverlayColor(int color) {
        mOverlayColor = color;
    }

    private void initializeRenderScript(Context context) {
        mRenderScript = RenderScript.create(context);
        mBlurScript = ScriptIntrinsicBlur.create(mRenderScript, Element.U8_4(mRenderScript));
    }

    protected boolean prepare() {
        int scaledWidth = getWidth() / mDownsampleFactor;
        int scaledHeight = getHeight() / mDownsampleFactor;
        if (getWidth() % mDownsampleFactor != 0) {
            scaledWidth += 1;
        }
        if (getHeight() % mDownsampleFactor != 0) {
            scaledHeight += 1;
        }

        if (scaledWidth <= 0 || scaledHeight <= 0) {
            return false;
        }

        boolean sizeChanged = false;

        if (mBitmapToBlurClip == null || mBitmapToBlurClip.getWidth() != scaledWidth
                || mBitmapToBlurClip.getHeight() != scaledHeight) {
            mBitmapToBlurClip = Bitmap.createBitmap(scaledWidth, scaledHeight,
                    Bitmap.Config.ARGB_8888);
            if (mBitmapToBlurClip == null) {
                return false;
            }

            mBlurredBitmapClip = Bitmap.createBitmap(scaledWidth, scaledHeight,
                    Bitmap.Config.ARGB_8888);
            if (mBlurredBitmapClip == null) {
                return false;
            }
            sizeChanged = true;
        }
        if (mBlurringCanvas == null || mDownsampleFactorChanged || sizeChanged) {
            mDownsampleFactorChanged = false;

            mBlurringCanvas = new Canvas(mBitmapToBlurClip);
            mBlurringCanvas.scale(1f / mDownsampleFactor, 1f / mDownsampleFactor);

            mBlurInput = Allocation.createFromBitmap(mRenderScript, mBitmapToBlurClip,
                    Allocation.MipmapControl.MIPMAP_NONE, Allocation.USAGE_SCRIPT);
            mBlurOutput = Allocation.createTyped(mRenderScript, mBlurInput.getType());
        }
        return true;
    }

    protected void blur() {
        mBlurInput.copyFrom(mBitmapToBlurClip);
        mBlurScript.setInput(mBlurInput);
        mBlurScript.forEach(mBlurOutput);
        mBlurOutput.copyTo(mBlurredBitmapClip);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mRenderScript != null) {
            mRenderScript.destroy();
        }
    }

    private int mDownsampleFactor;
    private int mOverlayColor;

    private View mBlurredView;

    private boolean mDownsampleFactorChanged;
    private Bitmap mBitmapToBlurClip, mBlurredBitmapClip;
    private Canvas mBlurringCanvas;
    private RenderScript mRenderScript;
    private ScriptIntrinsicBlur mBlurScript;
    private Allocation mBlurInput, mBlurOutput;

}