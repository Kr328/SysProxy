package com.github.kr328.sysproxy;

import android.net.IConnectivityManager;
import android.net.ProxyInfo;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class ShizukuRemote extends IShizukuRemote.Stub {
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
                final String cmd;
                if (new File("/system/bin/settings").exists()) {
                    cmd = "/system/bin/settings";
                } else {
                    cmd = "settings";
                }

                final java.lang.Process process = new ProcessBuilder()
                        .command(cmd, "put", "global", "http_proxy", newProxy)
                        .redirectErrorStream(true)
                        .start();

                final byte[] buffer = new byte[1024];
                final StringBuilder output = new StringBuilder();
                try (final InputStream reader = process.getInputStream()) {
                    int len;
                    while ((len = reader.read(buffer)) != -1) {
                        output.append(new String(buffer, 0, len, StandardCharsets.UTF_8));
                    }
                }

                final int ret = process.waitFor();
                if (ret != 0) {
                    throw new IllegalStateException("Set global proxy failed: " + output.toString().trim());
                }
            } catch (final Exception e) {
                throw new IllegalStateException(e.getMessage());
            }
        }
    }
}
