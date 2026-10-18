package org.fourz.rvnkcore.service.sightings;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Only a public IPv4 is ever recorded (#2351). */
class PublicIpv4Test {

    @Test
    void onlyPublicIpv4() throws Exception {
        assertEquals("8.8.8.8", PublicIpv4.of(InetAddress.getByName("8.8.8.8")).orElseThrow());
        assertEquals("172.32.0.1", PublicIpv4.of(InetAddress.getByName("172.32.0.1")).orElseThrow());
        for (String a : List.of("10.70.10.102", "192.168.1.5", "172.16.0.1", "172.31.255.255", "100.64.0.1",
                "127.0.0.1", "169.254.1.1", "0.0.0.0", "203.0.113.9", "224.0.0.1", "255.255.255.255")) {
            assertTrue(PublicIpv4.of(InetAddress.getByName(a)).isEmpty(), a);
        }
        assertTrue(PublicIpv4.of(InetAddress.getByName("2001:4860:4860::8888")).isEmpty(), "IPv6 is out of scope");
        assertTrue(PublicIpv4.of(null).isEmpty());
    }
}
