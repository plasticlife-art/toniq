package app.rubeton.toniq.config;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MegatixIpv4DnsResolverTest {

    @Test
    void returnsOnlyIpv4AddressesWhenLookupReturnsMixedFamilies() throws Exception {
        MegatixIpv4DnsResolver resolver = new MegatixIpv4DnsResolver(
                Set.of("staging.testmegatix.com"),
                host -> new InetAddress[]{
                        ipv6(host, new byte[16]),
                        ipv4(host, 203, 0, 113, 10),
                        ipv4(host, 203, 0, 113, 11)
                }
        );

        InetAddress[] resolved = resolver.resolve("staging.testmegatix.com");

        assertThat(resolved).hasSize(2);
        assertThat(resolved).allMatch(address -> address.getAddress().length == 4);
        assertThat(resolved).extracting(InetAddress::getHostAddress)
                .containsExactly("203.0.113.10", "203.0.113.11");
    }

    @Test
    void passesThroughIpv4OnlyResults() throws Exception {
        MegatixIpv4DnsResolver resolver = new MegatixIpv4DnsResolver(
                Set.of("staging.testmegatix.com"),
                host -> new InetAddress[]{
                        ipv4(host, 198, 51, 100, 20)
                }
        );

        InetAddress[] resolved = resolver.resolve("staging.testmegatix.com");

        assertThat(resolved).hasSize(1);
        assertThat(resolved[0].getHostAddress()).isEqualTo("198.51.100.20");
    }

    @Test
    void failsFastWhenLookupReturnsOnlyIpv6Addresses() {
        MegatixIpv4DnsResolver resolver = new MegatixIpv4DnsResolver(
                Set.of("staging.testmegatix.com"),
                host -> new InetAddress[]{
                        ipv6(host, new byte[16])
                }
        );

        assertThatThrownBy(() -> resolver.resolve("staging.testmegatix.com"))
                .isInstanceOf(UnknownHostException.class)
                .hasMessageContaining("resolved without IPv4 addresses")
                .hasMessageContaining("staging.testmegatix.com");
    }

    private static InetAddress ipv4(final String host, final int a, final int b, final int c, final int d) {
        try {
            return InetAddress.getByAddress(host, new byte[]{
                    (byte) a,
                    (byte) b,
                    (byte) c,
                    (byte) d
            });
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    private static InetAddress ipv6(final String host, final byte[] bytes) {
        try {
            return InetAddress.getByAddress(host, bytes);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }
}
