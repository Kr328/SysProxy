package com.github.kr328.sysproxy;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import java.util.concurrent.CopyOnWriteArrayList;

import rikka.shizuku.Shizuku;

public final class ShizukuHelper implements ServiceConnection {
    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<OnStateChangedListener> listeners = new CopyOnWriteArrayList<>();
    private State state = new State.Unavailable();

    public ShizukuHelper(final Context context) {
        this.context = context;

        Shizuku.addBinderDeadListener(this::rebindService);
        Shizuku.addBinderReceivedListenerSticky(this::rebindService);
        Shizuku.addRequestPermissionResultListener((requestCode, grantResult) -> rebindService());
    }

    public State getState() {
        return state;
    }

    public void addOnStateChangedListener(final OnStateChangedListener listener) {
        listeners.add(listener);
    }

    public void removeOnStateChangedListener(final OnStateChangedListener listener) {
        listeners.remove(listener);
    }

    public void requestPermission() {
        Shizuku.requestPermission(0);
    }

    public void startShizukuApp() {
        try {
            final Intent shizukuIntent = context.getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
            if (shizukuIntent == null) {
                context.startActivity(new Intent(Intent.ACTION_VIEW).setData(Uri.parse("https://shizuku.rikka.app/")));
            } else {
                context.startActivity(context.getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api"));
            }
        } catch (final Exception ignored) {}
    }


    public void onDetach() {
        Shizuku.unbindUserService(
                new Shizuku.UserServiceArgs(new ComponentName(context, ShizukuRemote.class)),
                this,
                true
        );
    }

    private void rebindService() {
        handler.post(() -> {
            if (state instanceof final State.Ready ready) {
                if (ready.remote.asBinder().pingBinder()) {
                    return;
                }
            }

            if (Shizuku.pingBinder()) {
                if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    Shizuku.bindUserService(
                            new Shizuku.UserServiceArgs(new ComponentName(context, ShizukuRemote.class))
                                    .daemon(false)
                                    .debuggable(false)
                                    .processNameSuffix("shizuku"),
                            this);

                    state = new State.Starting();
                } else {
                    state = new State.NoPermission();
                }
            } else {
                state = new State.Unavailable();
            }

            for (final OnStateChangedListener listener : listeners) {
                listener.onShizukuStateChanged();
            }
        });
    }

    @Override
    public void onServiceConnected(final ComponentName name, final IBinder service) {
        state = new State.Ready(IShizukuRemote.Stub.asInterface(service));

        for (final OnStateChangedListener listener : listeners) {
            listener.onShizukuStateChanged();
        }
    }

    @Override
    public void onServiceDisconnected(final ComponentName name) {
        rebindService();
    }

    public interface OnStateChangedListener {
        void onShizukuStateChanged();
    }

    public sealed static class State permits State.Unavailable, State.NoPermission, State.Starting, State.Ready {
        public static final class Unavailable extends State {
        }

        public static final class NoPermission extends State {
        }

        public static final class Starting extends State {
        }

        public static final class Ready extends State {
            public final IShizukuRemote remote;

            public Ready(final IShizukuRemote remote) {
                this.remote = remote;
            }
        }
    }
}
