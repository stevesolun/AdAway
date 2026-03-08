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

    @Override
    public void onReceive(Context context, Intent intent) {
        if (SEND_COMMAND_ACTION.equals(intent.getAction())) {
            AdBlockModel adBlockModel = ((AdAwayApplication) context.getApplicationContext()).getAdBlockModel();
            Command command = Command.readFromIntent(intent);
            Timber.d("CommandReceiver invoked with command %s", command);
            AppExecutors.getInstance().diskIO().execute(() -> executeCommand(adBlockModel, command));
        }
    }

    private void executeCommand(AdBlockModel adBlockModel, Command command) {
        try {
            Timber.d("executeCommand: %s", command);
            switch (command) {
                case START:
                    Timber.d("Calling adBlockModel.apply()");
                    adBlockModel.apply();
                    break;
                case STOP:
                    Timber.d("Calling adBlockModel.revert()");
                    adBlockModel.revert();
                    break;
                case UNKNOWN:
                    Timber.d("Failed to run an unsupported command.");
                    break;
            }
        } catch (HostErrorException e) {
            Timber.d(e, "Failed to apply ad block command %s.", command);
        }
    }
}
