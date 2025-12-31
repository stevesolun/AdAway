package org.adaway.vpn;

import static android.content.Context.CONNECTIVITY_SERVICE;
import static android.net.NetworkCapabilities.TRANSPORT_VPN;
import static org.adaway.broadcast.Command.START;
import static org.adaway.broadcast.Command.STOP;
import static org.adaway.vpn.VpnStatus.STOPPED;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;

import org.adaway.helper.PreferenceHelper;

import java.util.Arrays;
import java.util.Objects;

/**
 * This utility class allows controlling (start and stop) the AdAway VPN
 * service.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public final class VpnServiceControls {
    /**
     * Private constructor.
     */
    private VpnServiceControls() {

    }

    /**
     * Check if the VPN service is currently running.
     *
     * @param context The application context.
     * @return {@code true} if the VPN service is currently running, {@code false}
     *         otherwise.
     */
    public static boolean isRunning(Context context) {
        boolean networkVpnCapability = checkAnyNetworkVpnCapability(context);
        VpnStatus status = PreferenceHelper.getVpnServiceStatus(context);
        if (status.isStarted() && !networkVpnCapability) {
            status = STOPPED;
            PreferenceHelper.setVpnServiceStatus(context, status);
        }
        return status.isStarted();
    }

    /**
     * Check if the VPN service is started.
     *
     * @param context The application context.
     * @return {@code true} if the VPN service is started, {@code false} otherwise.
     */
    public static boolean isStarted(Context context) {
        return PreferenceHelper.getVpnServiceStatus(context).isStarted();
    }

    /**
     * Start the VPN service.
     *
     * @param context The application context.
     * @return {@code true} if the service is started, {@code false} otherwise.
     */
    public static boolean start(Context context) {
        android.util.Log.e("VPN_DEBUG", "VpnServiceControls.start() called");
        // Check if VPN is already running
        if (isRunning(context)) {
            android.util.Log.e("VPN_DEBUG", "VPN already running");
            return true;
        }
        // Start the VPN service
        Intent intent = new Intent(context, VpnService.class);
        START.appendToIntent(intent);
        try {
            android.util.Log.e("VPN_DEBUG", "Attempting to launch service intent");
            android.content.ComponentName component;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                component = context.startForegroundService(intent);
            } else {
                component = context.startService(intent);
            }

            boolean started = component != null;
            android.util.Log.e("VPN_DEBUG", "Service launch result: " + component);

            if (started) {
                // Start the heartbeat
                VpnServiceHeartbeat.start(context);
            }
            return started;
        } catch (Exception e) {
            android.util.Log.e("VPN_DEBUG", "Failed to start service with exception", e);
            return false;
        }
    }

    /**
     * Stop the VPN service.
     *
     * @param context The application context.
     */
    public static void stop(Context context) {
        // Stop the heartbeat
        VpnServiceHeartbeat.stop(context);
        // Stop the service
        Intent intent = new Intent(context, VpnService.class);
        STOP.appendToIntent(intent);
        context.startService(intent);
    }

    private static boolean checkAnyNetworkVpnCapability(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(CONNECTIVITY_SERVICE);
        return Arrays.stream(connectivityManager.getAllNetworks())
                .map(connectivityManager::getNetworkCapabilities)
                .filter(Objects::nonNull)
                .anyMatch(networkCapabilities -> networkCapabilities.hasTransport(TRANSPORT_VPN));
    }
}
