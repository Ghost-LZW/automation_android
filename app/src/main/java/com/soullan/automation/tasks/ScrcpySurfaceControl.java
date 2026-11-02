package com.soullan.automation.tasks;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.ColorSpace;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.os.IBinder;
import android.os.IInterface;
import android.util.Log;
import android.view.Surface;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

// Copied from https://github.com/Genymobile/scrcpy/blob/master/server/src/main/java/com/genymobile/scrcpy/wrappers/SurfaceControl.java
@SuppressLint({"PrivateApi", "BlockedPrivateApi", "SoonBlockedPrivateApi", "DiscouragedPrivateApi"})
public final class ScrcpySurfaceControl {

    private static final Class<?> CLASS;

    // see <https://android.googlesource.com/platform/frameworks/base.git/+/pie-release-2/core/java/android/view/SurfaceControl.java#305>
    public static final int POWER_MODE_OFF = 0;
    public static final int POWER_MODE_NORMAL = 2;

    static {
        try {
            CLASS = Class.forName("android.view.SurfaceControl");
        } catch (ClassNotFoundException e) {
            throw new AssertionError(e);
        }
    }

    private static Method getBuiltInDisplayMethod;
    private static Method setDisplayPowerModeMethod;
    private static Method getPhysicalDisplayTokenMethod;
    private static Method getPhysicalDisplayIdsMethod;

    private ScrcpySurfaceControl() {
        // only static methods
    }

    public static void openTransaction() {
        try {
            CLASS.getMethod("openTransaction").invoke(null);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    public static void closeTransaction() {
        try {
            CLASS.getMethod("closeTransaction").invoke(null);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    public static void setDisplayProjection(IBinder displayToken, int orientation, Rect layerStackRect, Rect displayRect) {
        try {
            CLASS.getMethod("setDisplayProjection", IBinder.class, int.class, Rect.class, Rect.class)
                    .invoke(null, displayToken, orientation, layerStackRect, displayRect);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    public static void setDisplayLayerStack(IBinder displayToken, int layerStack) {
        try {
            CLASS.getMethod("setDisplayLayerStack", IBinder.class, int.class).invoke(null, displayToken, layerStack);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    public static void setDisplaySurface(IBinder displayToken, Surface surface) {
        try {
            CLASS.getMethod("setDisplaySurface", IBinder.class, Surface.class).invoke(null, displayToken, surface);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    public static IBinder createDisplay(String name, boolean secure) throws Exception {
        return (IBinder) CLASS.getMethod("createDisplay", String.class, boolean.class).invoke(null, name, secure);
    }

    private static Method getGetBuiltInDisplayMethod() throws NoSuchMethodException {
        if (getBuiltInDisplayMethod == null) {
            // the method signature has changed in Android Q
            // <https://github.com/Genymobile/scrcpy/issues/586>
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                getBuiltInDisplayMethod = CLASS.getMethod("getBuiltInDisplay", int.class);
            } else {
                // Removed in Android 16 (SDK 36); getPhysicalDisplayToken via
                // DisplayControl is used instead, see getBuiltInDisplay().
                try {
                    getBuiltInDisplayMethod = CLASS.getMethod("getInternalDisplayToken");
                } catch (NoSuchMethodException e) {
                    getBuiltInDisplayMethod = null;
                }
            }
        }
        return getBuiltInDisplayMethod;
    }

    public static boolean hasGetBuildInDisplayMethod() {
        try {
            return Build.VERSION.SDK_INT < 36 && getGetBuiltInDisplayMethod() != null;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    public static IBinder getBuiltInDisplay() {
        // Android 16 removed SurfaceControl.getInternalDisplayToken(); the token
        // comes from com.android.server.display.DisplayControl instead, loaded
        // with the system server class loader (same trick as scrcpy).
        if (Build.VERSION.SDK_INT >= 36 || !hasGetBuildInDisplayMethod()) {
            return getPhysicalDisplayTokenViaDisplayControl();
        }
        try {
            Method method = getGetBuiltInDisplayMethod();
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                // call getBuiltInDisplay(0)
                return (IBinder) method.invoke(null, 0);
            }

            // call getInternalDisplayToken()
            return (IBinder) method.invoke(null);
        } catch (ReflectiveOperationException e) {
            Log.e("Could not invoke method", e.toString());
            return getPhysicalDisplayTokenViaDisplayControl();
        }
    }

    private static IBinder getPhysicalDisplayTokenViaDisplayControl() {
        try {
            Class<?> classLoaderFactoryClass = Class.forName("com.android.internal.os.ClassLoaderFactory");
            Method createClassLoaderMethod = classLoaderFactoryClass.getDeclaredMethod(
                    "createClassLoader", String.class, String.class, String.class,
                    ClassLoader.class, int.class, boolean.class, String.class);
            String systemServerClasspath = System.getenv("SYSTEMSERVERCLASSPATH");
            ClassLoader classLoader = (ClassLoader) createClassLoaderMethod.invoke(
                    null, systemServerClasspath, null, null,
                    ClassLoader.getSystemClassLoader(), 0, true, null);
            Class<?> displayControlClass = classLoader.loadClass(
                    "com.android.server.display.DisplayControl");
            Method loadMethod = Runtime.class.getDeclaredMethod("loadLibrary0", Class.class, String.class);
            loadMethod.setAccessible(true);
            loadMethod.invoke(Runtime.getRuntime(), displayControlClass, "android_servers");

            long[] ids = (long[]) displayControlClass.getMethod("getPhysicalDisplayIds").invoke(null);
            if (ids == null || ids.length == 0) {
                return null;
            }
            return (IBinder) displayControlClass
                    .getMethod("getPhysicalDisplayToken", long.class)
                    .invoke(null, ids[0]);
        } catch (Throwable e) {
            Log.e("ScrcpySurfaceControl", "getPhysicalDisplayTokenViaDisplayControl failed", e);
            return null;
        }
    }

    private static Method getGetPhysicalDisplayTokenMethod() throws NoSuchMethodException {
        if (getPhysicalDisplayTokenMethod == null) {
            getPhysicalDisplayTokenMethod = CLASS.getMethod("getPhysicalDisplayToken", long.class);
        }
        return getPhysicalDisplayTokenMethod;
    }

    public static IBinder getPhysicalDisplayToken(long physicalDisplayId) {
        try {
            Method method = getGetPhysicalDisplayTokenMethod();
            return (IBinder) method.invoke(null, physicalDisplayId);
        } catch (ReflectiveOperationException e) {
            Log.e("Could not invoke method", e.toString());
            return null;
        }
    }

    private static Method getGetPhysicalDisplayIdsMethod() throws NoSuchMethodException {
        if (getPhysicalDisplayIdsMethod == null) {
            getPhysicalDisplayIdsMethod = CLASS.getMethod("getPhysicalDisplayIds");
        }
        return getPhysicalDisplayIdsMethod;
    }

    public static boolean hasGetPhysicalDisplayIdsMethod() {
        try {
            getGetPhysicalDisplayIdsMethod();
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    public static long[] getPhysicalDisplayIds() {
        try {
            Method method = getGetPhysicalDisplayIdsMethod();
            return (long[]) method.invoke(null);
        } catch (ReflectiveOperationException e) {
            Log.e("Could not invoke method", e.toString());
            return null;
        }
    }

    private static Method getSetDisplayPowerModeMethod() throws NoSuchMethodException {
        if (setDisplayPowerModeMethod == null) {
            setDisplayPowerModeMethod = CLASS.getMethod("setDisplayPowerMode", IBinder.class, int.class);
        }
        return setDisplayPowerModeMethod;
    }

    public static boolean setDisplayPowerMode(IBinder displayToken, int mode) {
        try {
            Method method = getSetDisplayPowerModeMethod();
            method.invoke(null, displayToken, mode);
            return true;
        } catch (ReflectiveOperationException e) {
            Log.e("Could not invoke method", e.toString());
            return false;
        }
    }

    public static void destroyDisplay(IBinder displayToken) {
        try {
            CLASS.getMethod("destroyDisplay", IBinder.class).invoke(null, displayToken);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    // Thanks https://github.com/Genymobile/scrcpy/issues/2727
    // In Android 16 (SDK 36) the capture API moved from android.view.SurfaceControl
    // to android.window.ScreenCapture, with the same shape.
    public static Bitmap takeScreenshot() throws Exception {
        // Android 17 (SDK 37) replaced the capture API with the asynchronous
        // android.window.ScreenCapture.ScreenCaptureParams, the old args
        // classes are gone.
        if (Build.VERSION.SDK_INT >= 37) {
            return takeScreenshot37();
        }
        boolean newApi;
        try {
            Class.forName("android.window.ScreenCapture$DisplayCaptureArgs");
            newApi = true;
        } catch (ClassNotFoundException e) {
            newApi = false;
        }

        String captureArgsName = newApi
                ? "android.window.ScreenCapture$DisplayCaptureArgs"
                : "android.view.SurfaceControl$DisplayCaptureArgs";
        String bufferClassName = newApi
                ? "android.window.ScreenCapture$ScreenshotHardwareBuffer"
                : "android.view.SurfaceControl$ScreenshotHardwareBuffer";
        Class<?> captureOwner = newApi
                ? Class.forName("android.window.ScreenCapture")
                : CLASS;

        try {
            @SuppressLint("DiscouragedPrivateApi")
            Method getService = Class.forName("android.os.ServiceManager")
                    .getDeclaredMethod("getService", String.class);
            IBinder binder = (IBinder) getService.invoke(null, "display");
            Method asInterface = Class.forName("android.hardware.display.IDisplayManager$Stub")
                    .getMethod("asInterface", IBinder.class);
            IInterface displayService = (IInterface) asInterface.invoke(null, binder);

            Object displayInfo = displayService.getClass()
                    .getMethod("getDisplayInfo", int.class)
                    .invoke(displayService, 0);
            assert displayInfo != null;
            Class<?> cls = displayInfo.getClass();
            int width_private = cls.getDeclaredField("logicalWidth").getInt(displayInfo);
            int height_private = cls.getDeclaredField("logicalHeight").getInt(displayInfo);
            Rect crop = new Rect(0, 0, width_private, height_private);
            int width = crop.width();
            int height = crop.height();
            final IBinder displayToken = getBuiltInDisplay();
            Class<?> builderClass = Class.forName(captureArgsName + "$Builder");
            Constructor<?> builderConstructor = builderClass.getDeclaredConstructor(IBinder.class);
            builderConstructor.setAccessible(true);
            Object builder = builderConstructor.newInstance(displayToken);
            Method sourceCropField = builderClass.getMethod("setSourceCrop", Rect.class);
            sourceCropField.invoke(builder, crop);

            Method sizeMethod = builderClass.getMethod("setSize", Integer.TYPE, Integer.TYPE);
            sizeMethod.setAccessible(true);
            sizeMethod.invoke(builder, width, height);

            Method build = builderClass.getMethod("build");
            build.setAccessible(true);
            Object captureArgs  = build.invoke(builder);

            Class<?> captureArgsClass = Class.forName(captureArgsName);

            Method argsMethod = captureOwner.getMethod("captureDisplay", captureArgsClass);
            argsMethod.setAccessible(true);
            Object cap = argsMethod.invoke(captureOwner, captureArgs);
            if (cap == null) {
                throw new Exception("Inject SurfaceControl captureDisplay return null");
            }
            Class<?> bufferClass = Class.forName(bufferClassName);
            return (Bitmap)bufferClass.getMethod("asBitmap").invoke(cap);
        } catch (Exception e) {
            // ignore exception
            throw e;
            // return null;
        }
    }

    // Android 17: ScreenCapture.capture(params, executor, OutcomeReceiver).
    private static Bitmap takeScreenshot37() throws Exception {
        // WindowManagerGlobal needs ApplicationSharedMemory, which zygote
        // initializes but an app_process entry point does not.
        Class<?> sharedMemory = Class.forName("com.android.internal.os.ApplicationSharedMemory");
        java.lang.reflect.Field instanceField = sharedMemory.getField("sInstance");
        if (instanceField.get(null) == null) {
            instanceField.set(null, sharedMemory.getMethod("create").invoke(null));
        }

        Class<?> screenCapture = Class.forName("android.window.ScreenCapture");
        Class<?> paramsClass = Class.forName("android.window.ScreenCapture$ScreenCaptureParams");
        Class<?> builderClass = Class.forName("android.window.ScreenCapture$ScreenCaptureParams$Builder");
        Class<?> receiverClass = Class.forName("android.os.OutcomeReceiver");

        Object builder = builderClass.getConstructor(int.class).newInstance(0);
        builderClass.getMethod("setIncludeSystemOverlays", boolean.class).invoke(builder, false);
        Object params = builderClass.getMethod("build").invoke(builder);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Object> resultRef = new AtomicReference<>();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        Object receiver = Proxy.newProxyInstance(
                ScrcpySurfaceControl.class.getClassLoader(),
                new Class<?>[]{receiverClass},
                (proxy, method, args) -> {
                    if ("onResult".equals(method.getName())) {
                        resultRef.set(args[0]);
                        latch.countDown();
                    } else if ("onError".equals(method.getName())) {
                        errorRef.set((Throwable) args[0]);
                        latch.countDown();
                    }
                    return null;
                });

        Executor executor = Runnable::run;
        screenCapture
                .getMethod("capture", paramsClass, Executor.class, receiverClass)
                .invoke(null, params, executor, receiver);
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new Exception("screen capture timed out");
        }
        if (errorRef.get() != null) {
            throw new Exception("screen capture failed", errorRef.get());
        }
        Object result = resultRef.get();
        if (result == null) {
            throw new Exception("screen capture returned null");
        }
        HardwareBuffer buffer = (HardwareBuffer) result.getClass()
                .getMethod("getHardwareBuffer")
                .invoke(result);
        ColorSpace colorSpace = (ColorSpace) result.getClass()
                .getMethod("getColorSpace")
                .invoke(result);
        Bitmap hardware = Bitmap.wrapHardwareBuffer(buffer, colorSpace);
        if (hardware == null) {
            throw new Exception("wrapHardwareBuffer returned null");
        }
        Bitmap bitmap = hardware.copy(Bitmap.Config.ARGB_8888, false);
        buffer.close();
        return bitmap;
    }
}
