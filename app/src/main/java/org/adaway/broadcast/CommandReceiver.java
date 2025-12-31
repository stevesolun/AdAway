package org.adaway.broadcast;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.adaway.AdAwayApplication;
import org.adaway.model.adblocking.AdBlockModel;
import org.adaway.model.error.HostErrorException;
import org.adaway.util.AppExecutors;

import timber.log.Timber;

/**
 * This broadcast receiver listens to commands from broadcast.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class CommandReceiver extends BroadcastReceiver {
    /**
     * This action allows to send commands to the application. See {@link Command}
     * for extra values.
     */
    public static final String SEND_COMMAND_ACTION = "org.adaway.action.SEND_COMMAND";
    private static final AppExecutors EXECUTORS = AppExecutors.getInstance();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (SEND_COMMAND_ACTION.equals(intent.getAction())) {
            AdBlockModel adBlockModel = ((AdAwayApplication) context.getApplicationContext()).getAdBlockModel();
            Command command = Command.readFromIntent(intent);
            android.util.Log.e("VPN_DEBUG", "CommandReceiver invoked with command " + command);
            EXECUTORS.diskIO().execute(() -> executeCommand(adBlockModel, command));
        }
    }

    private void executeCommand(AdBlockModel adBlockModel, Command command) {
        try {
            android.util.Log.e("VPN_DEBUG", "executeCommand: " + command);
            switch (command) {
                case START:
                    android.util.Log.e("VPN_DEBUG", "Calling adBlockModel.apply()");
                    adBlockModel.apply();
                    break;
                case STOP:
                    android.util.Log.e("VPN_DEBUG", "Calling adBlockModel.revert()");
                    adBlockModel.revert();
                    break;
                case UNKNOWN:
                    android.util.Log.e("VPN_DEBUG", "Failed to run an unsupported command.");
                    break;
            }
        } catch (HostErrorException e) {
            android.util.Log.e("VPN_DEBUG", "Failed to apply ad block command " + command + ".", e);
        }
    }
}
