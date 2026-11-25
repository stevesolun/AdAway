package org.adaway.broadcast;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import org.adaway.AdAwayApplication;
import org.adaway.model.adblocking.AdBlockModel;
import org.adaway.model.error.HostError;
import org.adaway.model.error.HostErrorException;
import org.adaway.model.source.SourceOperationCoordinator;
import org.adaway.util.AppExecutors;

import java.util.concurrent.Executor;

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
        if (intent == null) {
            return;
        }
        if (SEND_COMMAND_ACTION.equals(intent.getAction())) {
            Command command = Command.readFromIntent(intent);
            if (command == Command.UNKNOWN) {
                return;
            }
            Timber.d("CommandReceiver invoked with command %s", command);
            SourceOperationCoordinator.Lease lease = getSourceOperations(context).tryAcquire();
            if (lease == null) {
                showBusy(context);
                return;
            }
            try {
                AdBlockModel adBlockModel = getModel(context);
                getCommandExecutor().execute(() -> {
                    try {
                        executeCommand(adBlockModel, command);
                    } finally {
                        lease.close();
                    }
                });
            } catch (RuntimeException | Error exception) {
                lease.close();
                throw exception;
            }
        }
    }

    SourceOperationCoordinator getSourceOperations(Context context) {
        return ((AdAwayApplication) context.getApplicationContext()).getSourceOperations();
    }

    AdBlockModel getModel(Context context) {
        return ((AdAwayApplication) context.getApplicationContext()).getAdBlockModel();
    }

    Executor getCommandExecutor() {
        return AppExecutors.getInstance().diskIO();
    }

    void showBusy(Context context) {
        Toast.makeText(context.getApplicationContext(), HostError.UPDATE_IN_PROGRESS.getMessageKey(),
                Toast.LENGTH_SHORT).show();
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
