package org.fourz.rvnkcore.service.netpeers;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.Optional;

/**
 * Decides whether an address is a public IPv4 address (#2351).
 *
 * <p>z-office allowlists the address it receives, so a private, shared or reserved address must
 * never be sent. z-office refuses private and loopback addresses itself, but RVNKCore checks first so
 * that a player on the house LAN causes no call at all.</p>
 */
public final class PublicIpv4 {

    private PublicIpv4() { }

    /** The dotted address when {@code address} is a public IPv4 address, otherwise empty. */
    public static Optional<String> of(InetAddress address) {
        if (!(address instanceof Inet4Address)) return Optional.empty();
        byte[] b = address.getAddress();
        int a0 = b[0] & 0xff, a1 = b[1] & 0xff, a2 = b[2] & 0xff;
        boolean reserved =
                a0 == 0                                     // 0.0.0.0/8 "this network"
                || a0 == 10                                 // 10.0.0.0/8 private
                || (a0 == 100 && a1 >= 64 && a1 <= 127)     // 100.64.0.0/10 carrier-grade NAT
                || a0 == 127                                // 127.0.0.0/8 loopback
                || (a0 == 169 && a1 == 254)                 // 169.254.0.0/16 link-local
                || (a0 == 172 && a1 >= 16 && a1 <= 31)      // 172.16.0.0/12 private
                || (a0 == 192 && a1 == 0 && a2 == 0)        // 192.0.0.0/24 IETF protocol
                || (a0 == 192 && a1 == 0 && a2 == 2)        // 192.0.2.0/24 documentation
                || (a0 == 192 && a1 == 168)                 // 192.168.0.0/16 private
                || (a0 == 198 && (a1 == 18 || a1 == 19))    // 198.18.0.0/15 benchmarking
                || (a0 == 198 && a1 == 51 && a2 == 100)     // 198.51.100.0/24 documentation
                || (a0 == 203 && a1 == 0 && a2 == 113)      // 203.0.113.0/24 documentation
                || a0 >= 224;                               // multicast, reserved, broadcast
        return reserved ? Optional.empty() : Optional.of(address.getHostAddress());
    }
}
