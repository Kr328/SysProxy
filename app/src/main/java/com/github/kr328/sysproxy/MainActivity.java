package com.github.kr328.sysproxy;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.net.ProxyInfo;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.preference.SwitchPreference;
import android.view.View;
import android.widget.ListView;
import android.widget.Toast;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * @noinspection deprecation
 */
public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.main);

        final ActionBar actionBar = Objects.requireNonNull(getActionBar());
        actionBar.setElevation(0);

        getFragmentManager()
                .beginTransaction()
                .replace(R.id.preferences, new MainFragment())
                .commit();
    }

    public static class MainFragment extends PreferenceFragment implements ShizukuManager.OnStateChangedListener, SystemProxyManager.OnStateChangedListener {
        private SystemProxyManager proxyManager;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            getPreferenceManager().setSharedPreferencesName("main");

            addPreferencesFromResource(R.xml.preference_main);

            ShizukuManager.addOnStateChangedListener(this);

            findPreference("shizuku_state")
                    .setOnPreferenceClickListener(preference -> {
                        switch (ShizukuManager.getState()) {
                            case Unavailable -> ShizukuManager.startShizukuHomePage(getActivity());
                            case NoRoot, Ready -> ShizukuManager.startShizukuApp(getActivity());
                            case NoPermission -> ShizukuManager.requestPermission();
                        }

                        return true;
                    });

            findPreference("enable")
                    .setOnPreferenceChangeListener((preference, newValue) -> {
                        toggleEnable((boolean) newValue);

                        return false;
                    });

            findPreference("proxy_uri")
                    .setOnPreferenceChangeListener((preference, newValue) -> {
                        final Uri uri = Uri.parse(newValue.toString());
                        if (!"pac".equals(uri.getScheme()) && !"http".equals(uri.getScheme())) {
                            Toast.makeText(getActivity(), R.string.toast_invalid_proxy_uri, Toast.LENGTH_LONG).show();

                            return false;
                        }

                        onProxyUriChanged(uri);

                        return true;
                    });

            findPreference("exclude_list")
                    .setOnPreferenceChangeListener((preference, newValue) -> {
                        if (!ProxyUtils.isValidExcludeList(newValue.toString())) {
                            Toast.makeText(getActivity(), R.string.toast_invalid_exclude_list, Toast.LENGTH_LONG).show();

                            return false;
                        }

                        onExcludeListChanged(Arrays.asList(newValue.toString().split(",")));

                        return true;
                    });
        }

        private Uri getProxyUri() {
            return Uri.parse(getPreferenceManager().getSharedPreferences().getString("proxy_uri", getString(R.string.default_proxy_uri)));
        }

        private List<String> getExcludeList() {
            return Arrays.asList(
                    getPreferenceManager()
                            .getSharedPreferences()
                            .getString("exclude_list", getString(R.string.default_exclude_list))
                            .split(","));
        }

        private void setSystemProxy(Uri proxy) {
            try {
                if (proxy != null) {
                    if ("pac".equals(proxy.getScheme())) {
                        proxyManager.setSystemProxy(ProxyInfo.buildPacProxy(proxy.buildUpon().scheme("http").build()));
                    } else if ("http".equals(proxy.getScheme())) {
                        final String host = proxy.getHost();

                        int port = proxy.getPort();
                        if (port < 0) {
                            port = 8080;
                        }

                        proxyManager.setSystemProxy(ProxyInfo.buildDirectProxy(host, port, getExcludeList()));
                    } else {
                        throw new IllegalArgumentException("Invalid proxy scheme: " + proxy.getScheme());
                    }
                } else {
                    proxyManager.setSystemProxy(null);
                }
            } catch (Throwable e) {
                Toast.makeText(getActivity(), e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }

        private void toggleEnable(boolean enable) {
            if (enable) {
                setSystemProxy(getProxyUri());
            } else {
                setSystemProxy(null);
            }
        }

        @Override
        public void onAttach(Context context) {
            super.onAttach(context);

            proxyManager = new SystemProxyManager(context);
            proxyManager.addOnStateChangedListener(this);
        }

        @Override
        public void onDetach() {
            super.onDetach();

            proxyManager.removeOnStateChangedListener(this);
            proxyManager.onDetach();
            proxyManager = null;
        }

        @Override
        public void onResume() {
            super.onResume();

            proxyManager.onResume();
        }

        @Override
        public void onDestroy() {
            super.onDestroy();

            ShizukuManager.removeOnStateChangedListener(this);
        }

        @Override
        public void onViewCreated(View view, Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);

            ((ListView) view.findViewById(android.R.id.list)).setDivider(null);

            onShizukuStateChanged();
            onSystemProxyChanged();
            onProxyUriChanged(getProxyUri());
            onExcludeListChanged(getExcludeList());
        }

        @Override
        public void onShizukuStateChanged() {
            String shizkuStateSummary = null;
            switch (ShizukuManager.getState()) {
                case Unavailable ->
                        shizkuStateSummary = getString(R.string.shizuku_state_unavailable);
                case NoRoot -> shizkuStateSummary = getString(R.string.shizuku_state_no_root);
                case NoPermission ->
                        shizkuStateSummary = getString(R.string.shizuku_state_no_permission);
                case Ready -> shizkuStateSummary = getString(R.string.shizuku_state_ready);
            }

            findPreference("shizuku_state").setSummary(shizkuStateSummary);
            findPreference("enable").setEnabled(ShizukuManager.getState() == ShizukuManager.State.Ready);
        }

        @Override
        public void onSystemProxyChanged() {
            boolean value = false;
            String enableSummary = null;

            final SystemProxyManager.State state = proxyManager.getState();
            if (state instanceof SystemProxyManager.State.Enabled enabled) {
                Uri uri;
                if (enabled.proxy.getHost() != null) {
                    uri = new Uri.Builder()
                            .scheme("http")
                            .encodedAuthority(enabled.proxy.getHost() + ":" + enabled.proxy.getPort())
                            .build();
                } else if (enabled.proxy.getPacFileUrl() != null) {
                    uri = enabled.proxy.getPacFileUrl().buildUpon().scheme("pac").build();
                } else {
                    uri = Uri.parse("http://unknown");
                }

                value = true;
                enableSummary = getString(R.string.enable_summary_enabled, uri);
            } else if (state instanceof SystemProxyManager.State.Disabled) {
                enableSummary = getString(R.string.enable_summary_disabled);
            } else if (state instanceof SystemProxyManager.State.Unknown) {
                enableSummary = getString(R.string.enable_summary_unknown);
            }

            final SwitchPreference enable = (SwitchPreference) findPreference("enable");
            enable.setSummary(enableSummary);
            enable.setChecked(value);
        }

        void onProxyUriChanged(Uri proxyUri) {
            findPreference("proxy_uri")
                    .setSummary(getString(R.string.proxy_uri_summary, proxyUri.toString()));

            if (!"http".equals(proxyUri.getScheme())) {
                findPreference("exclude_list").setEnabled(false);
            }
        }

        void onExcludeListChanged(List<String> list) {
            findPreference("exclude_list")
                    .setSummary(getString(R.string.exclude_list_summary, list.size()));
        }
    }
}
