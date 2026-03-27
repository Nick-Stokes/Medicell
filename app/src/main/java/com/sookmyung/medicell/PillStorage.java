package com.sookmyung.medicell;

import android.app.Application;
import android.graphics.Bitmap;

public class PillStorage extends Application {
    private static Bitmap pendingBitmap;

    public static void setPendingBitmap(Bitmap bmp) {
        pendingBitmap = bmp;
    }

    public static Bitmap getPendingBitmap() {
        Bitmap b  = pendingBitmap;
        pendingBitmap = null;
        return b;
    }
}