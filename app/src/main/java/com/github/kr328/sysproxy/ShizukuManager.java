package com.github.kr328.sysproxy;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.CopyOnWriteArrayList;

import rikka.shizuku.Shizuku;

public final class ShizukuManager {
    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static final CopyOnWriteArrayList<OnStateChangedListener> listeners = new CopyOnWriteArrayList<>();
    private static State state = State.Unavailable;

    static {
        Shizuku.addBinderDeadListener(ShizukuManager::refreshState);
        Shizuku.addBinderReceivedListenerSticky(ShizukuManager::refreshState);
        Shizuku.addRequestPermissionResultListener((requestCode, grantResult) -> refreshState());
    }

    public static State getState() {
        return state;
    }

    public static void addOnStateChangedListener(OnStateChangedListener listener) {
        listeners.add(listener);
    }

    public static void removeOnStateChangedListener(OnStateChangedListener listener) {
        listeners.remove(listener);
    }

    public static void requestPermission() {
        Shizuku.requestPermission(0);
    }

    public static void startShizukuApp(Context context) {
        try {
            context.startActivity(context.getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api"));
        } catch (Exception ignored) {
        }
    }

    public static void startShizukuHomePage(Context context) {
        try {
            context.startActivity(new Intent(Intent.ACTION_VIEW).setData(Uri.parse("https://shizuku.rikka.app/")));
        } catch (Exception ignored) {
        }
    }

    private static void refreshState() {
        handler.post(() -> {
            if (Shizuku.pingBinder()) {
                if (Shizuku.getUid() == 0) {
                    if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                        state = State.Ready;
                    } else {
                        state = State.NoPermission;
                    }
                } else {
                    state = State.NoRoot;
                }
            } else {
                state = State.Unavailable;
            }

            for (OnStateChangedListener listener : listeners) {
                listener.onShizukuStateChanged();
            }
        });
    }

    public enum State {
        Unavailable,
        NoRoot,
        NoPermission,
        Ready,
    }

    public interface OnStateChangedListener {
        void onShizukuStateChanged();
    }
}
