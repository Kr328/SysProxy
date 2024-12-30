package com.github.kr328.sysproxy;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.IConnectivityManager;
import android.net.Proxy;
import android.net.ProxyInfo;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import java.util.concurrent.CopyOnWriteArrayList;

import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuBinderWrapper;

public class SystemProxyManager {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Context context;

    private final CopyOnWriteArrayList<OnStateChangedListener> listeners = new CopyOnWriteArrayList<>();

    private State state = new State.Unknown();

    private final BroadcastReceiver changedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && Proxy.PROXY_CHANGE_ACTION.equals(intent.getAction())) {
                refresh();
            }
        }
    };

    public SystemProxyManager(Context context) {
        this.context = context;

        context.registerReceiver(changedReceiver, new IntentFilter(Proxy.PROXY_CHANGE_ACTION));
    }

    public void onDetach() {
        context.unregisterReceiver(changedReceiver);
    }

    public void onResume() {
        refresh();
    }

    public State getState() {
        return state;
    }

    public void addOnStateChangedListener(OnStateChangedListener listener) {
        listeners.add(listener);
    }

    public void removeOnStateChangedListener(OnStateChangedListener listener) {
        listeners.remove(listener);
    }

    private void refresh() {
        handler.post(() -> {
            try {
                final ProxyInfo proxy = IConnectivityManager.Stub.asInterface(ServiceManager.getService("connectivity"))
                        .getGlobalProxy();

                if (proxy != null) {
                    state = new State.Enabled(proxy);
                } else {
                    state = new State.Disabled();
                }
            } catch (Throwable e) {
                Log.w("SysProxy", "Failed to refresh state", e);

                state = new State.Unknown();
            }

            for (OnStateChangedListener listener : listeners) {
                listener.onSystemProxyChanged();
            }
        });
    }

    public void setSystemProxy(ProxyInfo proxy) throws RemoteException {
        if (Shizuku.pingBinder() && Shizuku.getUid() == 0 && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            final IBinder binder = new ShizukuBinderWrapper(ServiceManager.getService("connectivity"));

            IConnectivityManager.Stub.asInterface(binder).setGlobalProxy(proxy);

            refresh();
        }
    }

    public interface OnStateChangedListener {
        void onSystemProxyChanged();
    }

    public static sealed class State permits State.Unknown, State.Enabled, State.Disabled {
        public static final class Unknown extends State {
        }

        public static final class Enabled extends State {
            public final ProxyInfo proxy;

            public Enabled(ProxyInfo proxy) {
                this.proxy = proxy;
            }
        }

        public static final class Disabled extends State {
        }
    }
}
