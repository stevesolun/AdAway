package org.adaway.vpn.worker;

import java.net.DatagramSocket;

interface SocketProtector {
    boolean protect(DatagramSocket socket);
}
