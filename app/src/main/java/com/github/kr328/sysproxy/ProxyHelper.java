package com.github.kr328.sysproxy;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Proxy;
import android.net.ProxyInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import java.util.Arrays;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class ProxyHelper {
    private static final String SETTINGS_GLOBAL_HTTP_PROXY_HOST = "global_http_proxy_host";
    private static final String SETTINGS_GLOBAL_HTTP_PROXY_PORT = "global_http_proxy_port";
    private static final String SETTINGS_GLOBAL_HTTP_PROXY_EXCLUSION_LIST = "global_http_proxy_exclusion_list";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Context context;
    private final ShizukuHelper shizuku;

    private final CopyOnWriteArrayList<OnStateChangedListener> listeners = new CopyOnWriteArrayList<>();

    private State state = new State.Unknown();

    private final BroadcastReceiver changedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            if (intent != null && Proxy.PROXY_CHANGE_ACTION.equals(intent.getAction())) {
                refresh();
            }
        }
    };

    public ProxyHelper(final Context context, final ShizukuHelper shizuku) {
        this.context = context;
        this.shizuku = shizuku;

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

    public void addOnStateChangedListener(final OnStateChangedListener listener) {
        listeners.add(listener);
    }

    public void removeOnStateChangedListener(final OnStateChangedListener listener) {
        listeners.remove(listener);
    }

    private ProxyInfo getSystemProxyBySettings() {
        final ContentResolver resolver = context.getContentResolver();

        final String host = Settings.Global.getString(resolver, SETTINGS_GLOBAL_HTTP_PROXY_HOST);
        if (TextUtils.isEmpty(host)) {
            return null;
        }

        final int port = Settings.Global.getInt(resolver, SETTINGS_GLOBAL_HTTP_PROXY_PORT, 8080);
        if (port <= 0) {
            return null;
        }

        String exclusionList = Settings.Global.getString(
                resolver,
                SETTINGS_GLOBAL_HTTP_PROXY_EXCLUSION_LIST
        );
        if (exclusionList == null) {
            exclusionList = "";
        }

        return ProxyInfo.buildDirectProxy(
                host,
                port,
                Arrays.stream(exclusionList.split(","))
                        .map(String::trim)
                        .filter(s -> !TextUtils.isEmpty(s))
                        .collect(Collectors.toList())
        );
    }

    private void refresh() {
        handler.post(() -> {
            try {
                final ProxyInfo proxy;
                if (shizuku.getState() instanceof final ShizukuHelper.State.Ready ready && ready.remote.asBinder().pingBinder()) {
                    proxy = ready.remote.getGlobalProxy();
                } else {
                    proxy = getSystemProxyBySettings();
                }

                if (proxy != null) {
                    state = new State.Enabled(proxy);
                } else {
                    state = new State.Disabled();
                }
            } catch (final Throwable e) {
                Log.w("SysProxy", "Failed to refresh state", e);

                state = new State.Unknown();
            }

            for (final OnStateChangedListener listener : listeners) {
                listener.onSystemProxyChanged();
            }
        });
    }

    public void setSystemProxy(final ProxyInfo proxy) throws RemoteException {
        if (shizuku.getState() instanceof final ShizukuHelper.State.Ready ready) {
            ready.remote.setGlobalProxy(proxy);
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

            public Enabled(final ProxyInfo proxy) {
                this.proxy = proxy;
            }
        }

        public static final class Disabled extends State {
        }
    }
}
