package app.rubeton.toniq.config;

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.SystemDefaultDnsResolver;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

final class MegatixIpv4DnsResolver implements DnsResolver {

    static final String STAGING_HOST = "staging.testmegatix.com";

    private final Set<String> ipv4OnlyHosts;
    private final Function<String, InetAddress[]> lookup;

    MegatixIpv4DnsResolver(final String megatixBaseUrl) {
        this(resolveIpv4OnlyHosts(megatixBaseUrl), MegatixIpv4DnsResolver::lookupAll);
    }

    MegatixIpv4DnsResolver(final Set<String> ipv4OnlyHosts, final Function<String, InetAddress[]> lookup) {
        this.ipv4OnlyHosts = ipv4OnlyHosts.stream()
                .map(MegatixIpv4DnsResolver::normalizeHost)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        this.lookup = lookup;
    }

    Set<String> getIpv4OnlyHosts() {
        return ipv4OnlyHosts;
    }

    @Override
    public InetAddress[] resolve(final String host) throws UnknownHostException {
        String normalizedHost = normalizeHost(host);
        if (!ipv4OnlyHosts.contains(normalizedHost)) {
            return SystemDefaultDnsResolver.INSTANCE.resolve(host);
        }

        InetAddress[] resolved;
        try {
            resolved = lookup.apply(normalizedHost);
        } catch (MegatixDnsLookupRuntimeException e) {
            throw (UnknownHostException) e.getCause();
        }
        InetAddress[] ipv4Only = Arrays.stream(resolved)
                .filter(Inet4Address.class::isInstance)
                .toArray(InetAddress[]::new);

        if (ipv4Only.length == 0) {
            throw new UnknownHostException("Megatix host resolved without IPv4 addresses: host=" + host
                    + ", resolved=" + Arrays.toString(resolved));
        }

        return ipv4Only;
    }

    @Override
    public String resolveCanonicalHostname(final String host) throws UnknownHostException {
        return SystemDefaultDnsResolver.INSTANCE.resolveCanonicalHostname(host);
    }

    private static Set<String> resolveIpv4OnlyHosts(final String megatixBaseUrl) {
        Set<String> hosts = new LinkedHashSet<>();
        hosts.add(STAGING_HOST);

        String configuredHost = extractHost(megatixBaseUrl);
        if (configuredHost != null && !configuredHost.isBlank()) {
            hosts.add(configuredHost);
        }

        return hosts;
    }

    private static String extractHost(final String megatixBaseUrl) {
        if (megatixBaseUrl == null || megatixBaseUrl.isBlank()) {
            return null;
        }

        try {
            return URI.create(megatixBaseUrl).getHost();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String normalizeHost(final String host) {
        return host == null ? null : host.toLowerCase(Locale.ROOT);
    }

    private static InetAddress[] lookupAll(final String host) {
        try {
            return InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new MegatixDnsLookupRuntimeException(e);
        }
    }

    private static final class MegatixDnsLookupRuntimeException extends RuntimeException {

        private MegatixDnsLookupRuntimeException(final UnknownHostException cause) {
            super(Objects.requireNonNull(cause));
        }
    }
}
