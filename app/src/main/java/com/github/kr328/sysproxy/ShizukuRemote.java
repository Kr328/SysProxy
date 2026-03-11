package com.github.kr328.sysproxy;

import android.net.IConnectivityManager;
import android.net.ProxyInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.os.ServiceManager;
import android.os.ShellCallback;
import android.util.Log;

import java.io.File;
import java.io.FileDescriptor;
import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

public class ShizukuRemote extends IShizukuRemote.Stub {
    private static final MethodHandle shellCommandMH;

    static {
        try {
            shellCommandMH = MethodHandles.lookup()
                    .findVirtual(IBinder.class, "shellCommand",
                            MethodType.methodType(void.class, FileDescriptor.class, FileDescriptor.class, FileDescriptor.class,
                                    String[].class, ShellCallback.class, ResultReceiver.class));
        } catch (final NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public ProxyInfo getGlobalProxy() throws RemoteException {
        final IConnectivityManager connectivity = IConnectivityManager.Stub
                .asInterface(ServiceManager.getService("connectivity"));

        return connectivity.getGlobalProxy();
    }

    @Override
    public void setGlobalProxy(final ProxyInfo info) throws RemoteException {
        if (android.os.Process.myUid() == Process.ROOT_UID) {
            final IConnectivityManager connectivity = IConnectivityManager.Stub
                    .asInterface(ServiceManager.getService("connectivity"));

            connectivity.setGlobalProxy(info);
        } else {
            final String newProxy;
            if (info != null) {
                String host = "localhost";
                if (info.getHost() != null) {
                    host = info.getHost();
                }

                int port = 8080;
                if (info.getPort() > 0) {
                    port = info.getPort();
                }

                newProxy = host + ":" + port;
            } else {
                newProxy = ":0";
            }

            try {
                final CompletableFuture<Integer> code = new CompletableFuture<>();

                final IBinder settings = ServiceManager.getService("settings");

                final ParcelFileDescriptor[] stdout = ParcelFileDescriptor.createPipe();
                try (final InputStream stdoutReader = new ParcelFileDescriptor.AutoCloseInputStream(stdout[0])) {
                    try (final ParcelFileDescriptor stdoutOut = stdout[1]) {
                        try (final ParcelFileDescriptor fdNull = ParcelFileDescriptor.open(new File("/dev/null"), ParcelFileDescriptor.MODE_READ_ONLY)) {
                            shellCommandMH.invoke(
                                    settings,
                                    fdNull.getFileDescriptor(),
                                    stdoutOut.getFileDescriptor(),
                                    stdoutOut.getFileDescriptor(),
                                    new String[]{"put", "global", "http_proxy", newProxy},
                                    null,
                                    new ResultReceiver(handler) {
                                        @Override
                                        protected void onReceiveResult(final int resultCode, final Bundle resultData) {
                                            code.complete(resultCode);
                                        }
                                    }
                            );
                        }
                    }

                    final byte[] buffer = new byte[1024];
                    final StringBuilder output = new StringBuilder();
                    int len;
                    while ((len = stdoutReader.read(buffer)) != -1) {
                        output.append(new String(buffer, 0, len, StandardCharsets.UTF_8));
                    }

                    if (code.get() != 0) {
                        throw new IllegalStateException("Set global proxy failed: " + output.toString().trim());
                    }
                }

            } catch (final Throwable e) {
                Log.e("ShizukuRemote", "Set global proxy failed", e);

                throw new IllegalStateException(e.getMessage());
            }
        }
    }
}
