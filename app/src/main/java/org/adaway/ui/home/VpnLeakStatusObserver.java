package org.adaway.ui.home;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import org.adaway.R;
import org.adaway.util.Constants;

import timber.log.Timber;

/** Re-sample service status and VPN connectivity while the observing view is visible. */
public final class VpnLeakStatusObserver extends ConnectivityManager.NetworkCallback
        implements DefaultLifecycleObserver, SharedPreferences.OnSharedPreferenceChangeListener {
    private final ConnectivityManager connectivity;
    private final SharedPreferences preferences;
    private final String statusKey;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable refresh;
    private boolean started;
    private boolean registered;

    public VpnLeakStatusObserver(Context context, Runnable refresh) {
        this.connectivity = context.getSystemService(ConnectivityManager.class);
        this.preferences = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        this.statusKey = context.getString(R.string.pref_vpn_service_status_key);
        this.refresh = refresh;
    }

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        this.started = true;
        this.preferences.registerOnSharedPreferenceChangeListener(this);
        NetworkRequest vpn = new NetworkRequest.Builder()
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                .build();
        try {
            this.connectivity.registerNetworkCallback(vpn, this, this.main);
            this.registered = true;
        } catch (RuntimeException exception) {
            Timber.w(exception, "Cannot observe VPN network changes for Home.");
        }
        scheduleRefresh();
    }

    @Override
    public void onStop(@NonNull LifecycleOwner owner) {
        this.started = false;
        this.preferences.unregisterOnSharedPreferenceChangeListener(this);
        if (this.registered) {
            this.connectivity.unregisterNetworkCallback(this);
            this.registered = false;
        }
        this.main.removeCallbacks(this.refresh);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
        if (key == null || this.statusKey.equals(key)) scheduleRefresh();
    }

    @Override
    public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities caps) {
        scheduleRefresh();
    }

    @Override
    public void onLost(@NonNull Network network) {
        scheduleRefresh();
    }

    private void scheduleRefresh() {
        if (this.started) {
            this.main.removeCallbacks(this.refresh);
            this.main.post(this.refresh);
        }
    }
}
