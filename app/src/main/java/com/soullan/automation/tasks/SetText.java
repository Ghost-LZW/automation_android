package com.soullan.automation.tasks;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.annotation.SuppressLint;
import android.app.UiAutomation;
import android.os.Bundle;
import android.os.HandlerThread;
import android.os.Looper;
import android.view.accessibility.AccessibilityNodeInfo;

import java.lang.reflect.Constructor;

// Sets the text of the focused input field through accessibility, like
// `uiautomator` does from the shell. Unlike injected key events it does not go
// through the input method, so any text works (e.g. CJK) and composing input
// methods can not capture it.
@SuppressLint({"PrivateApi", "DiscouragedPrivateApi"})
public final class SetText {
    private SetText() {}

    /** Returns null on success, or the error. */
    public static String run(String text, boolean append) {
        HandlerThread thread = new HandlerThread("automation-uiautomation");
        thread.start();
        UiAutomation automation = null;
        try {
            automation = connect(thread.getLooper());
            AccessibilityServiceInfo info = automation.getServiceInfo();
            info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                    | AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
            automation.setServiceInfo(info);

            AccessibilityNodeInfo node = null;
            // The accessibility tree may take a moment after connecting.
            for (int i = 0; i < 20 && node == null; ++i) {
                node = automation.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
                if (node == null) {
                    node = findFocusedEditable(automation.getRootInActiveWindow());
                }
                if (node == null) {
                    Thread.sleep(50);
                }
            }
            if (node == null) {
                return "no focused input field, tap it first";
            }
            if (!node.isEditable()) {
                return "the focused element is not editable";
            }

            CharSequence value = text;
            if (append && !node.isPassword() && !node.isShowingHintText()
                    && node.getText() != null) {
                value = node.getText().toString() + text;
            }
            Bundle args = new Bundle();
            args.putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value);
            if (!node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                return "the input field refused the text";
            }
            // Keep the cursor at the end, as if typed.
            Bundle selection = new Bundle();
            selection.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, value.length());
            selection.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, value.length());
            node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selection);
            return null;
        } catch (Exception e) {
            return "accessibility failed: " + e;
        } finally {
            if (automation != null) {
                try {
                    UiAutomation.class.getDeclaredMethod("disconnect").invoke(automation);
                } catch (Exception ignored) {
                    // Exiting the process releases it as well.
                }
            }
            thread.quitSafely();
        }
    }

    private static AccessibilityNodeInfo findFocusedEditable(AccessibilityNodeInfo node) {
        if (node == null) {
            return null;
        }
        if (node.isFocused() && node.isEditable()) {
            return node;
        }
        for (int i = 0; i < node.getChildCount(); ++i) {
            AccessibilityNodeInfo found = findFocusedEditable(node.getChild(i));
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static UiAutomation connect(Looper looper) throws Exception {
        Class<?> connectionClass = Class.forName("android.app.UiAutomationConnection");
        Class<?> connectionInterface = Class.forName("android.app.IUiAutomationConnection");
        Object connection = connectionClass.getDeclaredConstructor().newInstance();
        Constructor<UiAutomation> constructor =
                UiAutomation.class.getDeclaredConstructor(Looper.class, connectionInterface);
        constructor.setAccessible(true);
        UiAutomation automation = constructor.newInstance(looper, connection);
        UiAutomation.class.getDeclaredMethod("connect").invoke(automation);
        return automation;
    }
}
