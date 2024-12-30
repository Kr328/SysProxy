package android.net;

import android.os.IInterface;

public interface IConnectivityManager extends IInterface {
    ProxyInfo getGlobalProxy() throws android.os.RemoteException;
    void setGlobalProxy(ProxyInfo proxyInfo) throws android.os.RemoteException;

    abstract class Stub extends android.os.Binder implements IConnectivityManager {
        public static IConnectivityManager asInterface(android.os.IBinder obj) {
            throw new RuntimeException("Stub!");
        }
    }
}
