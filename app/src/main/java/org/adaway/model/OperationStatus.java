package org.adaway.model;

import android.content.Context;

import androidx.annotation.StringRes;

/** Retain operation details without retaining the locale of the publishing thread. */
public final class OperationStatus {
    @StringRes private final int message;
    private final Object[] arguments;

    public OperationStatus(@StringRes int message, Object... arguments) {
        this.message = message;
        this.arguments = arguments.clone();
    }

    public String resolve(Context context) {
        return context.getString(this.message, this.arguments);
    }
}
