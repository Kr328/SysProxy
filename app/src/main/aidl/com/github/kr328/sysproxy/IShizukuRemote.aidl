package com.github.kr328.sysproxy;

import android.net.ProxyInfo;

interface IShizukuRemote {
    ProxyInfo getGlobalProxy();
    void setGlobalProxy(in ProxyInfo info);
}