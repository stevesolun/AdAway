package org.adaway.vpn;

import static android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET;
import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_VPN;
import static android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED;
import static android.net.NetworkCapabilities.TRANSPORT_BLUETOOTH;
import static android.net.NetworkCapabilities.TRANSPORT_CELLULAR;
import static android.net.NetworkCapabilities.TRANSPORT_ETHERNET;
import static android.net.NetworkCapabilities.TRANSPORT_VPN;
import static android.net.NetworkCapabilities.TRANSPORT_WIFI;

import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Handler;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Tracks callback-owned network state on the service handler, never on a worker thread. */
final class VpnNetworkMonitor extends ConnectivityManager.NetworkCallback {
    private final Runnable mChanged;
    private final Map<Network, State> mNetworks = new LinkedHashMap<>();
    private final List<ConnectivityManager.NetworkCallback> mRegistered = new ArrayList<>();
    private Network mDefaultNetwork;
    private Capabilities mDefaultCapabilities;
    private Network mBestNetwork;
    private Selection mSelection;
    private boolean mClosed;

    final ConnectivityManager.NetworkCallback defaultCallback =
            new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    if (mClosed) {
                        return;
                    }
                    mDefaultNetwork = network;
                    mDefaultCapabilities = null;
                    publishSelection();
                }

                @Override
                public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
                    updateDefaultCapabilities(network, Capabilities.from(caps));
                }

                @Override
                public void onLost(Network network) {
                    if (!mClosed && network.equals(mDefaultNetwork)) {
                        mDefaultNetwork = null;
                        mDefaultCapabilities = null;
                        publishSelection();
                    }
                }
            };

    final ConnectivityManager.NetworkCallback bestCallback =
            new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    if (!mClosed) {
                        mBestNetwork = network;
                        publishSelection();
                    }
                }

                @Override
                public void onLost(Network network) {
                    if (!mClosed && network.equals(mBestNetwork)) {
                        mBestNetwork = null;
                        publishSelection();
                    }
                }
            };

    VpnNetworkMonitor(Runnable changed) {
        mChanged = changed;
    }

    void register(ConnectivityManager manager, Handler handler) {
        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NET_CAPABILITY_INTERNET)
                .addCapability(NET_CAPABILITY_NOT_VPN)
                .build();
        try {
            manager.registerNetworkCallback(request, this, handler);
            mRegistered.add(this);
            manager.registerDefaultNetworkCallback(defaultCallback, handler);
            mRegistered.add(defaultCallback);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                manager.registerBestMatchingNetworkCallback(request, bestCallback, handler);
                mRegistered.add(bestCallback);
            }
        } catch (RuntimeException exception) {
            unregister(manager);
            throw exception;
        }
    }

    void unregister(ConnectivityManager manager) {
        close();
        for (ConnectivityManager.NetworkCallback callback : mRegistered) {
            manager.unregisterNetworkCallback(callback);
        }
        mRegistered.clear();
    }

    void close() {
        mClosed = true;
        mNetworks.clear();
        mSelection = null;
        mDefaultNetwork = null;
        mDefaultCapabilities = null;
        mBestNetwork = null;
    }

    @Override
    public void onAvailable(Network network) {
        if (!mClosed) {
            mNetworks.putIfAbsent(network, new State());
        }
    }

    @Override
    public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) {
        updateCapabilities(network, Capabilities.from(capabilities));
    }

    void updateCapabilities(Network network, Capabilities capabilities) {
        State state = mNetworks.get(network);
        if (!mClosed && state != null) {
            state.mCapabilities = capabilities;
            publishSelection();
        }
    }

    void updateDefaultCapabilities(Network network, Capabilities capabilities) {
        if (!mClosed && network.equals(mDefaultNetwork)) {
            mDefaultCapabilities = capabilities;
            publishSelection();
        }
    }

    @Override
    public void onLinkPropertiesChanged(Network network, LinkProperties properties) {
        State state = mNetworks.get(network);
        if (!mClosed && state != null) {
            state.mDns = new ArrayList<>(properties.getDnsServers());
            publishSelection();
        }
    }

    @Override
    public void onBlockedStatusChanged(Network network, boolean blocked) {
        State state = mNetworks.get(network);
        if (!mClosed && state != null) {
            state.mBlocked = blocked;
            publishSelection();
        }
    }

    @Override
    public void onLost(Network network) {
        if (!mClosed && mNetworks.remove(network) != null) {
            publishSelection();
        }
    }

    Selection getSelection() {
        return mSelection;
    }

    private void publishSelection() {
        Network selected = null;
        int bestScore = Integer.MIN_VALUE;
        for (Map.Entry<Network, State> entry : mNetworks.entrySet()) {
            State state = entry.getValue();
            if (!state.isUsable()) {
                continue;
            }
            Network network = entry.getKey();
            int score = score(network, state.mCapabilities);
            if (score > bestScore || (score == bestScore && mSelection != null
                    && network.equals(mSelection.network))) {
                selected = network;
                bestScore = score;
            }
        }
        Selection next = selected == null ? null
                : new Selection(selected, mNetworks.get(selected).mDns);
        if (!Objects.equals(mSelection, next)) {
            mSelection = next;
            mChanged.run();
        }
    }

    private int score(Network network, Capabilities caps) {
        if (network.equals(mBestNetwork)) {
            return 10000;
        }
        if (network.equals(mDefaultNetwork)) {
            return 9000;
        }
        int score = caps.validated ? 100 : 0;
        // Before API31 the app's default may be its VPN; only transport hints are exposed.
        if (mDefaultCapabilities != null) {
            for (int transport : new int[]{TRANSPORT_ETHERNET, TRANSPORT_WIFI,
                    TRANSPORT_CELLULAR, TRANSPORT_BLUETOOTH}) {
                if (caps.hasTransport(transport)
                        && mDefaultCapabilities.hasTransport(transport)) {
                    score += 1000;
                    break;
                }
            }
        }
        return score + (caps.hasTransport(TRANSPORT_ETHERNET) ? 3
                : caps.hasTransport(TRANSPORT_WIFI) ? 2
                : caps.hasTransport(TRANSPORT_CELLULAR) ? 1 : 0);
    }

    private static final class State {
        private Capabilities mCapabilities;
        private List<InetAddress> mDns = Collections.emptyList();
        private boolean mBlocked;

        boolean isUsable() {
            return !mBlocked && !mDns.isEmpty() && mCapabilities != null
                    && mCapabilities.internet
                    && mCapabilities.notVpn
                    && !mCapabilities.hasTransport(TRANSPORT_VPN);
        }
    }

    /** Immutable selection inputs, separate from Android's read-only callback payload. */
    static final class Capabilities {
        final boolean internet;
        final boolean notVpn;
        final boolean validated;
        private final int transports;

        Capabilities(boolean internet, boolean notVpn, boolean validated, int transports) {
            this.internet = internet;
            this.notVpn = notVpn;
            this.validated = validated;
            this.transports = transports;
        }

        static Capabilities from(NetworkCapabilities source) {
            int transports = 0;
            for (int transport : new int[]{TRANSPORT_ETHERNET, TRANSPORT_WIFI,
                    TRANSPORT_CELLULAR, TRANSPORT_BLUETOOTH, TRANSPORT_VPN}) {
                if (source.hasTransport(transport)) transports |= 1 << transport;
            }
            return new Capabilities(source.hasCapability(NET_CAPABILITY_INTERNET),
                    source.hasCapability(NET_CAPABILITY_NOT_VPN),
                    source.hasCapability(NET_CAPABILITY_VALIDATED), transports);
        }

        boolean hasTransport(int transport) {
            return (transports & (1 << transport)) != 0;
        }
    }

    static final class Selection {
        final Network network;
        final List<InetAddress> dnsServers;

        Selection(Network network, List<InetAddress> dnsServers) {
            this.network = network;
            this.dnsServers = Collections.unmodifiableList(new ArrayList<>(dnsServers));
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Selection)) {
                return false;
            }
            Selection selection = (Selection) other;
            return network.equals(selection.network) && dnsServers.equals(selection.dnsServers);
        }

        @Override
        public int hashCode() {
            return Objects.hash(network, dnsServers);
        }
    }
}
