package com.soullan.automation.tasks;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Build;
import android.os.IBinder;
import android.os.IInterface;
import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

// Base on https://github.com/lcacheux/screenshotserver/blob/master/screenshotServer/src/main/java/net/cacheux/screenshotserver/ScreenshotServer.java
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
        try {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
                RectWithRotate screenSize = getScreenSize();
                return (Bitmap) Class.forName("android.view.SurfaceControl")
                        .getMethod("screenshot", Rect.class, int.class, int.class, int.class)
                        .invoke(null, screenSize.rect, screenSize.rect.width(), screenSize.rect.height(), screenSize.rotation);
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

    private static class RectWithRotate {
        Rect rect;
        int rotation;

        RectWithRotate(Rect rect, int rotation) {
            this.rect = rect;
            this.rotation = rotation;
        }
    }

    private RectWithRotate getScreenSize() {
        try {
            Object displayInfo = displayService.getClass()
                    .getMethod("getDisplayInfo", int.class)
                    .invoke(displayService, 0);
            assert displayInfo != null;
            Class<?> cls = displayInfo.getClass();
            int width = cls.getDeclaredField("logicalWidth").getInt(displayInfo);
            int height = cls.getDeclaredField("logicalHeight").getInt(displayInfo);
            int rotation = cls.getDeclaredField("rotation").getInt(displayInfo);
            return new RectWithRotate(new Rect(0, 0, width, height), rotation);
        } catch (NoSuchMethodException | NoSuchFieldException | IllegalAccessException | InvocationTargetException e) {
            Log.e(TAG, "Error getting screen info", e);
            throw new IllegalStateException("Error getting screen info", e);
        }
    }
}
