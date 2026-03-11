package com.github.kr328.sysproxy;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.net.ProxyInfo;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.preference.SwitchPreference;
import android.util.Log;
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
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.main);

        final ActionBar actionBar = Objects.requireNonNull(getActionBar());
        actionBar.setElevation(0);

        getFragmentManager()
                .beginTransaction()
                .replace(R.id.preferences, new MainFragment())
                .commit();
    }

    public static class MainFragment extends PreferenceFragment implements ShizukuHelper.OnStateChangedListener, ProxyHelper.OnStateChangedListener {
        private ShizukuHelper shizukuHelper;
        private ProxyHelper proxyHelper;


        @Override
        public void onCreate(final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            getPreferenceManager().setSharedPreferencesName("main");

            addPreferencesFromResource(R.xml.preference_main);

            findPreference("shizuku_state")
                    .setOnPreferenceClickListener(preference -> {
                        final ShizukuHelper.State shizukuState = shizukuHelper.getState();
                        if (shizukuState instanceof ShizukuHelper.State.Unavailable) {
                            shizukuHelper.startShizukuApp();
                        } else if (shizukuState instanceof ShizukuHelper.State.Ready || shizukuState instanceof ShizukuHelper.State.Starting) {
                            shizukuHelper.startShizukuApp();
                        } else if (shizukuState instanceof ShizukuHelper.State.NoPermission) {
                            shizukuHelper.requestPermission();
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

        private void setSystemProxy(final Uri proxy) {
            try {
                if (proxy != null) {
                    if ("pac".equals(proxy.getScheme())) {
                        proxyHelper.setSystemProxy(ProxyInfo.buildPacProxy(proxy.buildUpon().scheme("http").build()));
                    } else if ("http".equals(proxy.getScheme())) {
                        final String host = proxy.getHost();

                        int port = proxy.getPort();
                        if (port < 0) {
                            port = 8080;
                        }

                        proxyHelper.setSystemProxy(ProxyInfo.buildDirectProxy(host, port, getExcludeList()));
                    } else {
                        throw new IllegalArgumentException("Invalid proxy scheme: " + proxy.getScheme());
                    }
                } else {
                    proxyHelper.setSystemProxy(null);
                }
            } catch (final Throwable e) {
                Log.w("MainActivity", "Failed to set system proxy", e);

                Toast.makeText(getActivity(), e.toString(), Toast.LENGTH_LONG).show();
            }
        }

        private void toggleEnable(final boolean enable) {
            new Thread(() -> {
                if (enable) {
                    setSystemProxy(getProxyUri());
                } else {
                    setSystemProxy(null);
                }
            }).start();
        }

        @Override
        public void onAttach(final Context context) {
            super.onAttach(context);

            shizukuHelper = new ShizukuHelper(context);
            shizukuHelper.addOnStateChangedListener(this);

            proxyHelper = new ProxyHelper(context, shizukuHelper);
            proxyHelper.addOnStateChangedListener(this);
        }

        @Override
        public void onDetach() {
            super.onDetach();

            proxyHelper.removeOnStateChangedListener(this);
            proxyHelper.onDetach();
            proxyHelper = null;

            shizukuHelper.removeOnStateChangedListener(this);
            shizukuHelper.onDetach();
            shizukuHelper = null;
        }

        @Override
        public void onResume() {
            super.onResume();

            proxyHelper.onResume();
        }

        @Override
        public void onViewCreated(final View view, final Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);

            ((ListView) view.findViewById(android.R.id.list)).setDivider(null);

            onShizukuStateChanged();
            onSystemProxyChanged();
            onProxyUriChanged(getProxyUri());
            onExcludeListChanged(getExcludeList());
        }

        @Override
        public void onShizukuStateChanged() {
            final ShizukuHelper.State shizukuState = shizukuHelper.getState();

            String shizkuStateSummary = null;
            if (shizukuState instanceof ShizukuHelper.State.Unavailable) {
                shizkuStateSummary = getString(R.string.shizuku_state_unavailable);
            } else if (shizukuState instanceof ShizukuHelper.State.NoPermission) {
                shizkuStateSummary = getString(R.string.shizuku_state_no_permission);
            } else if (shizukuState instanceof ShizukuHelper.State.Starting) {
                shizkuStateSummary = getString(R.string.shizuku_state_starting);
            } else if (shizukuState instanceof ShizukuHelper.State.Ready) {
                shizkuStateSummary = getString(R.string.shizuku_state_ready);
            }

            findPreference("shizuku_state").setSummary(shizkuStateSummary);
            findPreference("enable").setEnabled(shizukuState instanceof ShizukuHelper.State.Ready);
        }

        @Override
        public void onSystemProxyChanged() {
            boolean value = false;
            String enableSummary = null;

            final ProxyHelper.State state = proxyHelper.getState();
            if (state instanceof final ProxyHelper.State.Enabled enabled) {
                final Uri uri;
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
            } else if (state instanceof ProxyHelper.State.Disabled) {
                enableSummary = getString(R.string.enable_summary_disabled);
            } else if (state instanceof ProxyHelper.State.Unknown) {
                enableSummary = getString(R.string.enable_summary_unknown);
            }

            final SwitchPreference enable = (SwitchPreference) findPreference("enable");
            enable.setSummary(enableSummary);
            enable.setChecked(value);
        }

        void onProxyUriChanged(final Uri proxyUri) {
            findPreference("proxy_uri")
                    .setSummary(getString(R.string.proxy_uri_summary, proxyUri.toString()));

            if (!"http".equals(proxyUri.getScheme())) {
                findPreference("exclude_list").setEnabled(false);
            }
        }

        void onExcludeListChanged(final List<String> list) {
            findPreference("exclude_list")
                    .setSummary(getString(R.string.exclude_list_summary, list.size()));
        }
    }
}
