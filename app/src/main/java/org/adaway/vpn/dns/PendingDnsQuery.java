package org.adaway.vpn.dns;

import android.system.StructPollfd;

abstract class PendingDnsQuery implements AutoCloseable {
    abstract boolean isOlderThan(long timestamp);

    abstract StructPollfd getPollfd();

    abstract boolean isAnswered();

    abstract void handleResponse();

    @Override
    public abstract void close();
}
