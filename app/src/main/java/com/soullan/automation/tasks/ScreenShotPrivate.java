package com.soullan.automation.tasks;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Build;
import android.os.IBinder;
import android.os.IInterface;
import android.util.Log;
import android.view.Display;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

@SuppressLint("PrivateApi")
public class ScreenShotPrivate {
    private static final String TAG = ScreenShotPrivate.class.getSimpleName();
    private final IInterface displayService;

    public ScreenShotPrivate() {
        try {
            @SuppressLint("DiscouragedPrivateApi")
            Method getService = Class.forName("android.os.ServiceManager")
                    .getDeclaredMethod("getService", String.class);
            Log.e(TAG, "Get Server");
            IBinder binder = (IBinder) getService.invoke(null, "display");
            Method asInterface = Class.forName("android.hardware.display.IDisplayManager$Stub")
                    .getMethod("asInterface", IBinder.class);
            displayService = (IInterface) asInterface.invoke(null, binder);
        } catch (NoSuchMethodException | ClassNotFoundException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    public Bitmap takeScreenshot() {
        Rect screenSize = getScreenSize();

        try {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
                return (Bitmap) Class.forName("android.view.SurfaceControl")
                        .getMethod("screenshot", Rect.class, int.class, int.class, int.class)
                        .invoke(null, screenSize, screenSize.width(), screenSize.height(), 0);
            } else {
                return ScrcpySurfaceControl.takeScreenshot();
            }
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            Log.e(TAG, "Error taking screenshot", e);
            throw new IllegalStateException("Error taking screenshot", e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Rect getScreenSize() {
        try {
            Object displayInfo = displayService.getClass()
                    .getMethod("getDisplayInfo", int.class)
                    .invoke(displayService, 0);
            assert displayInfo != null;
            Class<?> cls = displayInfo.getClass();
            int width = cls.getDeclaredField("logicalWidth").getInt(displayInfo);
            int height = cls.getDeclaredField("logicalHeight").getInt(displayInfo);
            return new Rect(0, 0, width, height);
        } catch (NoSuchMethodException | NoSuchFieldException | IllegalAccessException | InvocationTargetException e) {
            Log.e(TAG, "Error getting screen info", e);
            throw new IllegalStateException("Error getting screen info", e);
        }
    }
}
