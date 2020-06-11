/*
MIT License

Copyright (c) 2020 Jeffrey deRienzo and Quang V. Tran

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
 */
package net.emirac.medusa;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Xfermode;
import android.os.Handler;
import android.service.wallpaper.WallpaperService;
import android.view.MotionEvent;
import android.view.SurfaceHolder;

/**
 * Created by jeff derienzo on 28 March 2020.
 */
public class MedusaWallpaper extends WallpaperService {

    private static final String TAG                = WallpaperService.class.getSimpleName();
    private static final long   REDRAW_INTERVAL    = 60L;
    private static final float  MIN_VX             = 10f;
    private static final float  MIN_VY             = 10f;
    private static final float  MAX_VX             = 100f;
    private static final float  MAX_VY             = 100f;
    private static final int    EYES_TOUCH_ALPHA   = 255;
    private static final int    EYES_ALPHA         = 85;
    private static final int    SLIDER_TOUCH_ALPHA = 255;
    private static final int    SLIDER_ALPHA       = 100;
    // size of main bitmap
    private static final int    RAW_WIDTH          = 2297;
    private static final int    RAW_HEIGHT         = 2290;
    // location of eyes mask wrt main bitmap in unclipped main bitmap coords
    private static final int    EYES_LOC_X         = 915;
    private static final int    EYES_LOC_Y         = 950;

    @Override
    public Engine onCreateEngine() {
        return (new MedusaEngine(this));
    }

    class MedusaEngine extends Engine {

        private final Paint    paint           = new Paint();
        private final Paint    sliderPaint     = new Paint();
        private final Paint    eyesPaint       = new Paint();
        private final Paint    screenPaint     = new Paint();
        private final Handler  mainHandler     = new Handler();
        private final Runnable frameTask       = new Runnable(){
            @Override
            public void run() {
                MedusaEngine.this.doOneFrame();
                return;
            }
        };
        private final Rect     sliderRectDelta = new Rect();
        private final Rect     sliderRect      = new Rect();
        private final Rect     imageRect       = new Rect();
        private final Rect     eyesRect        = new Rect();

        private Canvas   bufferCanvas       = null;
        private Canvas   sliderCanvas       = null;
        private Canvas   eyesCanvas         = null;
        private Bitmap   bufferBitmap       = null;
        private Bitmap   sliderBitmap       = null;
        private Bitmap   sliderCanvasBitmap = null;
        private Bitmap   eyesBitmap         = null;
        private Bitmap   eyesMaskBitmap     = null;
        private Bitmap   eyesCanvasBitmap   = null;
        private Bitmap   darkBitmap         = null;
        private Bitmap   lightBitmap        = null;
        private Bitmap   overlayBitmap      = null;
        private Xfermode defaultMode;
        private float    sliderCenterX      = 0;
        private float    sliderCenterY      = 0;
        private int      sliderWidth        = 0;
        private int      sliderHeight       = 0;
        private int      eyesX;
        private int      eyesY;
        private Rect     screenRect;
        private Rect     eyesMaskRect;
        private boolean  isTouched          = false;
        private boolean  isVisible          = false;
        private float    currentX           = 0;
        private float    currentY           = 0;
        private int      surfaceWidth       = 0;
        private int      surfaceHeight      = 0;
        private float    surfaceToBitmapX   = 0;
        private float    surfaceToBitmapY   = 0;
        private float    vX                 = MIN_VY;
        private float    vY                 = MIN_VY;

        MedusaEngine(WallpaperService ws) {
            this.sliderPaint.setXfermode(new PorterDuffXfermode(Mode.DST_IN));
            this.eyesPaint.setXfermode(new PorterDuffXfermode(Mode.DST_IN));
            this.screenPaint.setXfermode(new PorterDuffXfermode(Mode.SRC));
            this.sliderPaint.setAlpha(SLIDER_ALPHA);
            this.eyesPaint.setAlpha(EYES_ALPHA);
        }

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            this.defaultMode = this.paint.getXfermode();
            setTouchEventsEnabled(true);
        }

        @Override
        public void onOffsetsChanged(float xOffset, float yOffset, float xOffsetStep, float yOffsetStep, int xPixelOffset, int yPixelOffset) {
            super.onOffsetsChanged(xOffset, yOffset, xOffsetStep, yOffsetStep, xPixelOffset, yPixelOffset);
        }

        @Override
        public void onDesiredSizeChanged(int desiredWidth, int desiredHeight) {
            super.onDesiredSizeChanged(desiredWidth, desiredHeight);
        }

        @Override
        public void onSurfaceRedrawNeeded(SurfaceHolder holder) {
            super.onSurfaceRedrawNeeded(holder);
        }

        @Override
        public void onDestroy() {
            this.mainHandler.removeCallbacks(this.frameTask);
            if (this.bufferBitmap != null) {
                this.bufferBitmap.recycle();
                this.bufferBitmap = null;
            }
            if (this.darkBitmap != null) {
                this.darkBitmap.recycle();
                this.darkBitmap = null;
            }
            if (this.lightBitmap != null) {
                this.lightBitmap.recycle();
                this.lightBitmap = null;
            }
            if (this.sliderBitmap != null) {
                this.sliderBitmap.recycle();
                this.sliderBitmap = null;
            }
            if (this.sliderCanvasBitmap != null) {
                this.sliderCanvasBitmap.recycle();
                this.sliderCanvasBitmap = null;
            }
            if (this.eyesBitmap != null) {
                this.eyesBitmap.recycle();
                this.eyesBitmap = null;
            }
            if (this.eyesMaskBitmap != null) {
                this.eyesMaskBitmap.recycle();
                this.eyesMaskBitmap = null;
            }
            if (this.eyesCanvasBitmap != null) {
                this.eyesCanvasBitmap.recycle();
                this.eyesCanvasBitmap = null;
            }
            if (this.overlayBitmap != null) {
                this.overlayBitmap.recycle();
                this.overlayBitmap = null;
            }
            super.onDestroy();
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            this.isVisible = visible;
            if (visible) {
                this.mainHandler.removeCallbacks(this.frameTask);
                this.mainHandler.post(this.frameTask);
            }
            else {
                this.mainHandler.removeCallbacks(this.frameTask);
            }
            return;
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            this.surfaceWidth = width;
            this.surfaceHeight = height;
            this.screenRect = new Rect(0, 0, this.surfaceWidth, this.surfaceHeight);
            this.loadBitmaps();
            this.currentX = this.darkBitmap.getWidth() / 2;
            this.currentY = this.darkBitmap.getHeight() / 2;
            this.mainHandler.removeCallbacks(this.frameTask);
            this.mainHandler.post(this.frameTask);
        }

        @Override
        public void onTouchEvent(MotionEvent event) {
            float prevX = this.currentX;
            float prevY = this.currentY;
            this.currentX = event.getX() * this.surfaceToBitmapX;
            this.currentY = event.getY() * this.surfaceToBitmapY;
            switch(event.getAction()){
                case MotionEvent.ACTION_DOWN:
                    this.isTouched = true;
                    this.sliderPaint.setAlpha(SLIDER_TOUCH_ALPHA);
                    this.eyesPaint.setAlpha(EYES_TOUCH_ALPHA);
                    this.mainHandler.post(this.frameTask);
                    break;
                case MotionEvent.ACTION_UP:
                    this.isTouched = false;
                    this.sliderPaint.setAlpha(SLIDER_ALPHA);
                    this.eyesPaint.setAlpha(EYES_ALPHA);
                    this.vX = (this.currentX - prevX);
                    if(vX == 0.0){
                        vX = MedusaWallpaper.MIN_VX;
                    }
                    else if(Math.abs(this.vX) < MedusaWallpaper.MIN_VX){
                        this.vX = MedusaWallpaper.MIN_VX * Math.signum(vX);
                    }
                    else if(Math.abs(this.vX) > MedusaWallpaper.MAX_VX){
                        this.vX = MedusaWallpaper.MAX_VX * Math.signum(vX);
                    }
                    this.vY = (this.currentY - prevY);
                    if(vY == 0.0){
                        vY = MedusaWallpaper.MIN_VY;
                    }
                    else if(Math.abs(this.vY) < MedusaWallpaper.MIN_VY){
                        this.vY = MedusaWallpaper.MIN_VY * Math.signum(vY);
                    }
                    else if(Math.abs(this.vY) > MedusaWallpaper.MAX_VY){
                        this.vY = MedusaWallpaper.MAX_VY * Math.signum(vY);
                    }
                    this.mainHandler.post(this.frameTask);
                    break;
                case MotionEvent.ACTION_MOVE:
                    this.mainHandler.post(this.frameTask);
                    break;
            }
            super.onTouchEvent(event);
        }

        private void loadBitmaps() {
            double aspect = (double)this.surfaceWidth / (double)this.surfaceHeight;
            // the light picture
            this.lightBitmap = this.makeBitmap(R.drawable.light, this.surfaceWidth, this.surfaceHeight);
            this.imageRect.set(0, 0, this.lightBitmap.getWidth(), this.lightBitmap.getHeight());
            // the slider (light follower)
            int m = Math.min(this.surfaceWidth, this.surfaceHeight);
            this.sliderBitmap = this.makeBitmap(R.drawable.slider, m, m);
            // the the dark picture
            this.darkBitmap = this.makeBitmap(R.drawable.dark, this.surfaceWidth, this.surfaceHeight);
            this.eyesX = EYES_LOC_X - ((RAW_WIDTH - this.darkBitmap.getWidth()) / 2);
            this.eyesY = EYES_LOC_Y - ((RAW_HEIGHT - this.darkBitmap.getHeight()) / 2);
            // the eyes
            this.eyesBitmap = this.makeBitmap(R.drawable.eyes, 0, 0);
            this.eyesMaskBitmap = this.makeBitmap(R.drawable.eyes_mask2, 0, 0);
            this.eyesMaskRect = new Rect(0, 0, this.eyesMaskBitmap.getWidth() , this.eyesMaskBitmap.getHeight());
            // overlay
            this.overlayBitmap = this.makeBitmap(R.drawable.overlay, this.surfaceWidth, this.surfaceHeight);
            // canvas to put the bitmaps together
            this.bufferBitmap = Bitmap.createBitmap(this.darkBitmap.getWidth(), this.darkBitmap.getHeight(), Bitmap.Config.ARGB_8888);
            this.bufferCanvas = new Canvas(this.bufferBitmap);
            // canvas to build the sliding light
            this.sliderCanvasBitmap = Bitmap.createBitmap(this.sliderBitmap.getWidth(), this.sliderBitmap.getHeight(), Bitmap.Config.ARGB_8888);
            this.sliderCanvas = new Canvas(this.sliderCanvasBitmap);
            // canvas to build the eyes
            this.eyesCanvasBitmap = Bitmap.createBitmap(this.eyesBitmap.getWidth(), this.eyesBitmap.getHeight(), Bitmap.Config.ARGB_8888);
            this.eyesCanvas = new Canvas(this.eyesCanvasBitmap);

            // to convert touch points from surface coords to bitmap coords
            this.surfaceToBitmapX = ((float)this.darkBitmap.getWidth()/(this.surfaceWidth));
            this.surfaceToBitmapY = ((float)this.darkBitmap.getHeight()/(this.surfaceHeight));
            // slider info (we might need to move it round a lot in doOneFrame)
            this.sliderWidth = this.sliderBitmap.getWidth();
            this.sliderHeight = this.sliderBitmap.getHeight();
            this.sliderCenterX = this.sliderWidth / 2;
            this.sliderCenterY = this.sliderHeight / 2;
        }

        private Bitmap makeBitmap(int id, int dstWidth, int dstHeight){
            Bitmap result;
            // load source bitmap into result
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            opts.inScaled = false;
            result = BitmapFactory.decodeResource(MedusaWallpaper.this.getResources(), id, opts);
            if((dstHeight == 0) || (dstWidth == 0)){
                return(result);
            }
            // scale & translate arithmetic
            final double dstAspect = ((double)dstWidth) / ((double)dstHeight);
            final double srcAspect = (double)(result.getWidth()) / (double)(result.getHeight());
            final int wSrc;
            final int hSrc;
            final int xSrc;
            final int ySrc;
            if(dstAspect > srcAspect){
                wSrc = result.getWidth();
                hSrc = (int) ((double)(result.getWidth()) *  (double)dstHeight / (double)dstWidth);
                xSrc = 0;
                ySrc = (result.getHeight() - hSrc) / 2;
            } else {
                wSrc = (int) ((double)(result.getHeight()) *  (double)dstWidth / (double)dstHeight);
                hSrc = result.getHeight();
                xSrc = (result.getWidth() - wSrc) / 2;
                ySrc = 0;
            }
            result = Bitmap.createBitmap(result, xSrc, ySrc, wSrc, hSrc);
            return(result);
        }

        private void doOneFrame() {
            if ((this.bufferCanvas != null) && this.isVisible) { // if the bitmaps loaded ok
                Canvas screenCanvas = null;
                final SurfaceHolder holder = getSurfaceHolder();
                // draw everything into the buffer:
                // background
                this.bufferCanvas.drawBitmap(this.darkBitmap, null, this.imageRect, null);
                // slider:
                // draw slider into full bright
                this.sliderRectDelta.set(
                        (int) (this.currentX - this.sliderCenterX),
                        (int) (this.currentY - this.sliderCenterY),
                        (int) (this.currentX - this.sliderCenterX + this.sliderWidth),
                        (int) (this.currentY - this.sliderCenterY + this.sliderHeight)
                );
                this.sliderRect.set(0, 0, this.sliderWidth, this.sliderWidth);
                // draw the correct piece of the top into the slider buffer
                this.sliderCanvas.drawBitmap(this.lightBitmap, this.sliderRectDelta, this.sliderRect, null);
                // porter-duff the slider light into the slider buffer
                this.sliderCanvas.drawBitmap(this.sliderBitmap, null, this.sliderRect, this.sliderPaint);
                // copy slider into background
                this.bufferCanvas.drawBitmap(this.sliderCanvasBitmap, null, this.sliderRectDelta, null);
                // eyes:
                // draw the eyes into the eyes canvas
                int x = ((((int) this.currentX) - (this.darkBitmap.getWidth() / 2) + 2) * 70) / this.darkBitmap.getWidth();
                int y = ((((int) this.currentY) - (this.darkBitmap.getHeight() / 2) + 2) * 14) / this.darkBitmap.getHeight();
                this.eyesRect.set(x, y, this.eyesBitmap.getWidth() + x, this.eyesBitmap.getHeight() + y);
                this.eyesCanvas.drawBitmap(this.eyesBitmap,
                        null,
                        this.eyesRect,
                        null);
                // porter-duff the eyes mask into the eyes canvas
                this.eyesCanvas.drawBitmap(this.eyesMaskBitmap, null, this.eyesMaskRect, this.eyesPaint);
                // copy the eyes into buffer
                this.bufferCanvas.drawBitmap(this.eyesCanvasBitmap, this.eyesX, this.eyesY, null);
                // overlay
                if (this.isTouched) {
                    this.bufferCanvas.drawBitmap(this.overlayBitmap, null, this.imageRect, null);
                }
                // copy the buffer to the screen canvas:
                if (holder.getSurface().isValid()) {
                    try {
                        screenCanvas = holder.lockCanvas();
                        if (screenCanvas != null) {
                            screenCanvas.drawBitmap(this.bufferBitmap, null, this.screenRect, this.screenPaint);
                        }
                    } finally {
                        if (screenCanvas != null) {
                            holder.unlockCanvasAndPost(screenCanvas);
                        }
                    }
                }
                // maybe re-schedule
                if(this.isTouched) { // user moves the slider
                    this.mainHandler.removeCallbacks(this.frameTask);
                }
                else{ // wallpaper moves the slider based on (vX, vY)
                    // keep the current point inside the bufferBitmap dimensions
                    this.currentX += this.vX;
                    if(this.currentX < 0.0){
                        this.vX = -this.vX;
                        this.currentX = 0.0f;
                    }
                    else if(this.currentX > this.bufferBitmap.getWidth()){
                        this.vX = -this.vX;
                        this.currentX =this.bufferBitmap.getWidth();
                    }
                    this.currentY += this.vY;
                    if(this.currentY < 0.0){
                        this.vY = -this.vY;
                        this.currentY = 0.0f;
                    }
                    else if(this.currentY > this.bufferBitmap.getHeight()){
                        this.vY = -this.vY;
                        this.currentY = this.bufferBitmap.getHeight();
                    }
                    // re-schedule
                    this.mainHandler.postDelayed(this.frameTask, MedusaWallpaper.REDRAW_INTERVAL);
                }
            }
        }
    }
}