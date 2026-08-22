/*
 * SPDX-License-Identifier: MIT
 *
 * Java SE Desktop reimplementation of ConnectivityAndInternetAccess.
 * Original logic by Emil, str4d, and Rodrigo Sambade.
 */
package net.i2p.desktop.router.util;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class ConnectivityAndInternetAccess {

    public interface InternetCallback {
        void onResult(InternetResult result);
    }

    public static final class InternetResult {
        private final boolean reachable;
        private final String reachedHost;
        private final List<String> attemptedHosts;
        private final long elapsedMilliseconds;

        public InternetResult(
                boolean reachable,
                String reachedHost,
                List<String> attemptedHosts,
                long elapsedMilliseconds
        ) {
            this.reachable = reachable;
            this.reachedHost = reachedHost;
            this.attemptedHosts = Collections.unmodifiableList(new ArrayList<>(attemptedHosts != null ? attemptedHosts : Collections.emptyList()));
            this.elapsedMilliseconds = elapsedMilliseconds;
        }

        public boolean isReachable() {
            return reachable;
        }

        public String getReachedHost() {
            return reachedHost;
        }

        public List<String> getAttemptedHosts() {
            return attemptedHosts;
        }

        public long getElapsedMilliseconds() {
            return elapsedMilliseconds;
        }
    }

    public static final class Request {
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private volatile Future<?> future;

        public Request() {
        }

        public void cancel() {
            cancelled.set(true);
            Future<?> task = future;
            if (task != null) {
                task.cancel(true);
            }
        }

        public boolean isCancelled() {
            return cancelled.get();
        }

        private void attach(Future<?> task) {
            future = task;
            if (cancelled.get()) {
                task.cancel(true);
            }
        }
    }

    /**
     * Configuration options for NetworkObserver.
     */
    public static final class NetworkObserverConfig {
        private final long pollIntervalMillis;
        private final long debounceDelayMillis;
        private final boolean automaticDiagnosticsOnNetworkChanges;
        private final NetworkSnapshotProvider snapshotProvider;
        private final List<String> dnsResolvers;
        private final List<String> hosts;
        private final DnsProbeStrategy dnsProbeStrategy;
        private final boolean extremeMode;

        private NetworkObserverConfig(Builder builder) {
            this.pollIntervalMillis = builder.pollIntervalMillis;
            this.debounceDelayMillis = builder.debounceDelayMillis;
            this.automaticDiagnosticsOnNetworkChanges = builder.automaticDiagnosticsOnNetworkChanges;
            this.snapshotProvider = builder.snapshotProvider != null ? builder.snapshotProvider : new NetworkSnapshotProvider.SystemNetworkSnapshotProvider();
            this.dnsResolvers = builder.dnsResolvers != null ? builder.dnsResolvers : DEFAULT_DNS_RESOLVERS;
            this.hosts = builder.hosts != null ? builder.hosts : DEFAULT_HOSTS;
            this.dnsProbeStrategy = builder.dnsProbeStrategy;
            this.extremeMode = builder.extremeMode;
        }

        public long getPollIntervalMillis() {
            return pollIntervalMillis;
        }

        public long getDebounceDelayMillis() {
            return debounceDelayMillis;
        }

        public boolean isAutomaticDiagnosticsOnNetworkChanges() {
            return automaticDiagnosticsOnNetworkChanges;
        }

        public NetworkSnapshotProvider getSnapshotProvider() {
            return snapshotProvider;
        }

        public List<String> getDnsResolvers() {
            return dnsResolvers;
        }

        public List<String> getHosts() {
            return hosts;
        }

        public DnsProbeStrategy getDnsProbeStrategy() {
            return dnsProbeStrategy;
        }

        public boolean isExtremeMode() {
            return extremeMode;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private long pollIntervalMillis = 3_000L;
            private long debounceDelayMillis = 500L;
            private boolean automaticDiagnosticsOnNetworkChanges = false;
            private NetworkSnapshotProvider snapshotProvider = new NetworkSnapshotProvider.SystemNetworkSnapshotProvider();
            private List<String> dnsResolvers = DEFAULT_DNS_RESOLVERS;
            private List<String> hosts = DEFAULT_HOSTS;
            private DnsProbeStrategy dnsProbeStrategy = null;
            private boolean extremeMode = false;

            public Builder pollInterval(Duration duration) {
                if (duration != null && !duration.isNegative() && !duration.isZero()) {
                    this.pollIntervalMillis = duration.toMillis();
                }
                return this;
            }

            public Builder pollIntervalMillis(long millis) {
                if (millis > 0) {
                    this.pollIntervalMillis = millis;
                }
                return this;
            }

            public Builder debounceDelay(Duration duration) {
                if (duration != null && !duration.isNegative()) {
                    this.debounceDelayMillis = duration.toMillis();
                }
                return this;
            }

            public Builder debounceDelayMillis(long millis) {
                if (millis >= 0) {
                    this.debounceDelayMillis = millis;
                }
                return this;
            }

            public Builder automaticDiagnosticsOnNetworkChanges(boolean enable) {
                this.automaticDiagnosticsOnNetworkChanges = enable;
                return this;
            }

            public Builder snapshotProvider(NetworkSnapshotProvider provider) {
                if (provider != null) {
                    this.snapshotProvider = provider;
                }
                return this;
            }

            public Builder dnsResolvers(List<String> resolvers) {
                this.dnsResolvers = resolvers;
                return this;
            }

            public Builder hosts(List<String> hosts) {
                this.hosts = hosts;
                return this;
            }

            public Builder dnsProbeStrategy(DnsProbeStrategy strategy) {
                this.dnsProbeStrategy = strategy;
                return this;
            }

            public Builder extremeMode(boolean enable) {
                this.extremeMode = enable;
                return this;
            }

            public NetworkObserverConfig build() {
                return new NetworkObserverConfig(this);
            }
        }
    }

    private static final int CONNECT_TIMEOUT_MS = 800;
    private static final int READ_TIMEOUT_MS = 800;
    private static final int DNS_TIMEOUT_MS = 650;
    private static final long DNS_STAGE_TIMEOUT_MS = 700L;
    private static final long TOTAL_PROBE_TIMEOUT_MS = 2_000L;
    private static final int MAX_PARALLEL_PROBES = 9;
    private static final int DNS_PORT = 53;
    private static final String DNS_QUERY_NAME = "example.com";

    private static final List<String> DEFAULT_DNS_RESOLVERS =
            Collections.unmodifiableList(Arrays.asList(
                    "1.1.1.1",
                    "8.8.8.8",
                    "9.9.9.9",
                    "208.67.222.222"
            ));

    private static final List<String> DEFAULT_HOSTS =
            Collections.unmodifiableList(Arrays.asList(
                    "https://www.google.com/generate_204",
                    "https://www.facebook.com/",
                    "https://www.wolframalpha.com/",
                    "https://www.apple.com/",
                    "https://www.amazon.com/"
            ));

    private static volatile List<String> configuredHosts = DEFAULT_HOSTS;
    private static final AtomicInteger DNS_TRANSACTION_ID =
            new AtomicInteger((int) System.nanoTime());
    private static volatile NetworkState globalLastKnownNetworkState;

    private static final ExecutorService EXECUTOR =
            Executors.newCachedThreadPool(new ThreadFactory() {
                private int number;

                @Override
                public synchronized Thread newThread(Runnable runnable) {
                    Thread thread = new Thread(
                            runnable,
                            "desktop-connectivity-check-" + (++number)
                    );
                    thread.setDaemon(true);
                    return thread;
                }
            });

    private static final ExecutorService PROBE_EXECUTOR =
            Executors.newFixedThreadPool(
                    MAX_PARALLEL_PROBES,
                    new ThreadFactory() {
                        private int number;

                        @Override
                        public synchronized Thread newThread(Runnable runnable) {
                            Thread thread = new Thread(
                                    runnable,
                                    "desktop-connectivity-probe-" + (++number)
                            );
                            thread.setDaemon(true);
                            return thread;
                        }
                    }
            );

    public ConnectivityAndInternetAccess(ArrayList<String> hosts) {
        configuredHosts = normalizeHosts(hosts);
    }

    /**
     * Checks if there is any active usable network interface on the desktop system.
     * Querying NetworkInterface is purely local and generates 0 network traffic.
     */
    public static boolean isConnected() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) return false;

            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isUp() && !iface.isLoopback()) {
                    Enumeration<InetAddress> addresses = iface.getInetAddresses();
                    if (addresses.hasMoreElements()) {
                        return true;
                    }
                }
            }
        } catch (SocketException ignored) {
            return false;
        }
        return false;
    }

    /** Checks if there are operational Wi-Fi interfaces based on adapter name. */
    public static boolean isConnectedWifi() {
        return hasInterfaceMatching("wlan", "wifi", "wireless");
    }

    /** Checks if there are operational Ethernet interfaces based on adapter name. */
    public static boolean isConnectedEthernet() {
        return hasInterfaceMatching("eth", "en", "ethernet");
    }

    /** Detects if an active VPN adapter exists (TUN/TAP/PPP). */
    public static boolean vpnActive() {
        return hasInterfaceMatching("tun", "tap", "ppp", "vpn");
    }

    /** Returns the last known network state captured by any observer or system query. */
    public static NetworkState getLastKnownNetworkState() {
        NetworkState state = globalLastKnownNetworkState;
        if (state == null) {
            try {
                state = new NetworkSnapshotProvider.SystemNetworkSnapshotProvider().getSnapshot();
                globalLastKnownNetworkState = state;
            } catch (SocketException ignored) {
                state = new NetworkState(System.currentTimeMillis(), Collections.emptyList());
            }
        }
        return state;
    }

    /**
     * Starts observing local network state changes using default configuration.
     * <p>
     * While the network remains stable:
     * - 0 DNS queries
     * - 0 HTTP/HTTPS probes
     * - 0 TCP connections
     */
    public static NetworkObserver observeNetwork(NetworkChangeListener listener) {
        return observeNetwork(NetworkObserverConfig.builder().build(), listener);
    }

    /**
     * Starts observing local network state changes using custom configuration.
     */
    public static NetworkObserver observeNetwork(NetworkObserverConfig config, NetworkChangeListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener == null");
        }
        if (config == null) {
            config = NetworkObserverConfig.builder().build();
        }
        NetworkObserverImpl observer = new NetworkObserverImpl(config, listener);
        observer.start();
        return observer;
    }

    public static boolean isInternetReachable() {
        return checkInternetBlocking().isReachable();
    }

    public static boolean isInternetReachable(ArrayList<String> hosts) {
        return checkInternetBlocking(DEFAULT_DNS_RESOLVERS, normalizeHosts(hosts)).isReachable();
    }

    public static Request checkInternetAsync(InternetCallback callback) {
        return checkInternetAsync(configuredHosts, callback);
    }

    public static Request checkInternetAsync(List<String> hosts, InternetCallback callback) {
        return checkInternetAsync(DEFAULT_DNS_RESOLVERS, hosts, callback);
    }

    public static Request checkInternetAsync(
            List<String> dnsResolvers,
            List<String> hosts,
            InternetCallback callback
    ) {
        return checkInternetAsync(dnsResolvers, hosts, null, false, callback);
    }

    public static Request checkInternetAsync(
            List<String> dnsResolvers,
            List<String> hosts,
            DnsProbeStrategy dnsStrategy,
            boolean extremeMode,
            InternetCallback callback
    ) {
        if (callback == null) {
            throw new IllegalArgumentException("callback == null");
        }
        final List<String> normalizedResolvers = normalizeDnsResolvers(dnsResolvers);
        final List<String> normalizedHosts = normalizeHosts(hosts);
        final Request request = new Request();

        Future<?> future = EXECUTOR.submit(() -> {
            final InternetResult result = checkInternetBlocking(normalizedResolvers, normalizedHosts, dnsStrategy, extremeMode);
            if (!request.isCancelled()) {
                callback.onResult(result);
            }
        });

        request.attach(future);
        return request;
    }

    public static InternetResult checkInternetBlocking() {
        return checkInternetBlocking(configuredHosts);
    }

    public static InternetResult checkInternetBlocking(List<String> hosts) {
        return checkInternetBlocking(DEFAULT_DNS_RESOLVERS, hosts);
    }

    public static InternetResult checkInternetBlocking(
            List<String> dnsResolvers,
            List<String> hosts
    ) {
        return checkInternetBlocking(dnsResolvers, hosts, null, false);
    }

    public static InternetResult checkInternetBlocking(
            List<String> dnsResolvers,
            List<String> hosts,
            DnsProbeStrategy dnsStrategy,
            boolean extremeMode
    ) {
        long started = System.currentTimeMillis();
        long deadline = started + TOTAL_PROBE_TIMEOUT_MS;
        List<String> attempted = new ArrayList<>();

        if (!isConnected()) {
            return new InternetResult(
                    false,
                    null,
                    attempted,
                    System.currentTimeMillis() - started
            );
        }

        List<String> normalizedResolvers = normalizeDnsResolvers(dnsResolvers);
        List<String> normalizedHosts = normalizeHosts(hosts);

        // 1. Stage 1: DNS Probes
        if (!normalizedResolvers.isEmpty()) {
            if (dnsStrategy != null) {
                String reached = dnsStrategy.executeDnsStage(normalizedResolvers, Math.min(deadline, started + DNS_STAGE_TIMEOUT_MS));
                if (reached != null) {
                    attempted.add(reached);
                    return new InternetResult(
                            true,
                            reached,
                            attempted,
                            System.currentTimeMillis() - started
                    );
                }
            } else {
                List<ProbeAttempt> dnsAttempts = new ArrayList<>();
                for (String resolver : normalizedResolvers) {
                    dnsAttempts.add(new ProbeAttempt(
                            dnsEndpointLabel(resolver),
                            () -> isDnsResolverAvailable(resolver)
                    ));
                }

                String reached = raceProbes(
                        dnsAttempts,
                        attempted,
                        Math.min(deadline, started + DNS_STAGE_TIMEOUT_MS)
                );

                if (reached != null) {
                    return new InternetResult(
                            true,
                            reached,
                            attempted,
                            System.currentTimeMillis() - started
                    );
                }
            }
        }

        // 2. Stage 2: Fallback HTTP(S) parallel
        if (!normalizedHosts.isEmpty()) {
            List<ProbeAttempt> hostAttempts = new ArrayList<>();
            for (String host : normalizedHosts) {
                hostAttempts.add(new ProbeAttempt(
                        host,
                        () -> isHostAvailable(host)
                ));
            }

            String reached = raceProbes(hostAttempts, attempted, deadline);
            if (reached != null) {
                return new InternetResult(
                        true,
                        reached,
                        attempted,
                        System.currentTimeMillis() - started
                );
            }
        }

        // 3. Stage 3: Extreme mode direct TCP probes if explicitly enabled
        if (extremeMode) {
            List<ProbeAttempt> tcpAttempts = new ArrayList<>();
            for (String resolver : normalizedResolvers) {
                DnsResolver endpoint = parseDnsResolver(resolver);
                tcpAttempts.add(new ProbeAttempt(
                        "tcp://" + endpoint.host + ":" + endpoint.port,
                        () -> isTcpEndpointAvailable(endpoint.host, endpoint.port)
                ));
            }
            String reached = raceProbes(tcpAttempts, attempted, deadline);
            if (reached != null) {
                return new InternetResult(
                        true,
                        reached,
                        attempted,
                        System.currentTimeMillis() - started
                );
            }
        }

        return new InternetResult(
                false,
                null,
                attempted,
                System.currentTimeMillis() - started
        );
    }

    public static List<String> defaultHosts() {
        return DEFAULT_HOSTS;
    }

    public static List<String> defaultDnsResolvers() {
        return DEFAULT_DNS_RESOLVERS;
    }

    private static String raceProbes(
            List<ProbeAttempt> probes,
            List<String> attempted,
            long deadline
    ) {
        if (probes.isEmpty() || Thread.currentThread().isInterrupted()) {
            return null;
        }
        CompletionService<String> completion = new ExecutorCompletionService<>(PROBE_EXECUTOR);
        List<Future<String>> futures = new ArrayList<>();

        for (ProbeAttempt probe : probes) {
            attempted.add(probe.label);
            futures.add(completion.submit(() -> probe.operation.run() ? probe.label : null));
        }

        int remaining = futures.size();
        try {
            while (remaining-- > 0) {
                long wait = deadline - System.currentTimeMillis();
                if (wait <= 0) {
                    return null;
                }
                Future<String> completed = completion.poll(wait, TimeUnit.MILLISECONDS);
                if (completed == null) {
                    return null;
                }
                try {
                    String reached = completed.get();
                    if (reached != null) {
                        return reached;
                    }
                } catch (CancellationException | ExecutionException ignored) {
                    // Endpoint unavailable
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } finally {
            for (Future<String> future : futures) {
                future.cancel(true);
            }
        }
        return null;
    }

    private static boolean hasInterfaceMatching(String... keywords) {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) return false;

            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isUp() && !iface.isLoopback()) {
                    String name = iface.getName().toLowerCase();
                    String displayName = iface.getDisplayName().toLowerCase();
                    for (String kw : keywords) {
                        if (name.contains(kw) || displayName.contains(kw)) {
                            return true;
                        }
                    }
                }
            }
        } catch (SocketException ignored) {
            return false;
        }
        return false;
    }

    private static boolean isHostAvailable(String address) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(address);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "*/*");
            connection.setRequestProperty("Accept-Encoding", "identity");
            connection.setRequestProperty("Connection", "close");
            connection.setRequestProperty("User-Agent", "ConnectivityAndInternetAccess-Desktop/2");

            int response = connection.getResponseCode();
            return response >= 100 && response <= 599;
        } catch (IOException | RuntimeException ignored) {
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static boolean isDnsResolverAvailable(String resolver) {
        DnsResolver endpoint = parseDnsResolver(resolver);
        DatagramSocket socket = null;
        try {
            int transactionId = DNS_TRANSACTION_ID.incrementAndGet() & 0xffff;
            byte[] query = createDnsQuery(transactionId);

            socket = new DatagramSocket();
            socket.setSoTimeout(DNS_TIMEOUT_MS);
            socket.connect(new InetSocketAddress(endpoint.host, endpoint.port));
            socket.send(new DatagramPacket(query, query.length));

            byte[] buffer = new byte[512];
            DatagramPacket response = new DatagramPacket(buffer, buffer.length);
            socket.receive(response);

            return isValidDnsResponse(transactionId, response.getData(), response.getLength());
        } catch (IOException | RuntimeException ignored) {
            return false;
        } finally {
            if (socket != null) {
                socket.close();
            }
        }
    }

    private static boolean isTcpEndpointAvailable(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static byte[] createDnsQuery(int transactionId) {
        String[] labels = DNS_QUERY_NAME.split("\\.");
        int length = 12 + 1 + 4;
        for (String label : labels) {
            length += 1 + label.length();
        }
        byte[] query = new byte[length];
        query[0] = (byte) (transactionId >>> 8);
        query[1] = (byte) transactionId;
        query[2] = 0x01; // Recursion desired.
        query[5] = 0x01; // One question.
        int offset = 12;
        for (String label : labels) {
            query[offset++] = (byte) label.length();
            for (int index = 0; index < label.length(); index++) {
                query[offset++] = (byte) label.charAt(index);
            }
        }
        query[offset++] = 0x00;
        query[offset++] = 0x00;
        query[offset++] = 0x01; // QTYPE A.
        query[offset++] = 0x00;
        query[offset] = 0x01; // QCLASS IN.
        return query;
    }

    private static boolean isValidDnsResponse(int transactionId, byte[] response, int length) {
        if (response == null || length < 12) {
            return false;
        }
        int responseId = ((response[0] & 0xff) << 8) | (response[1] & 0xff);
        int flags = ((response[2] & 0xff) << 8) | (response[3] & 0xff);
        int questionCount = ((response[4] & 0xff) << 8) | (response[5] & 0xff);
        int responseCode = flags & 0x000f;

        // A valid response code from resolver (even negative like NXDOMAIN rcode 3) proves resolver responded
        return responseId == transactionId
                && (flags & 0x8000) != 0
                && (flags & 0x7800) == 0
                && questionCount > 0
                && responseCode <= 5;
    }

    private static List<String> normalizeHosts(List<String> hosts) {
        if (hosts == null) {
            return Collections.emptyList();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String raw : hosts) {
            if (raw == null) continue;
            String value = raw.trim();
            if (value.isEmpty()) continue;
            if (!value.regionMatches(true, 0, "https://", 0, 8)
                    && !value.regionMatches(true, 0, "http://", 0, 7)) {
                value = "https://" + value + "/";
            }
            if (!isValidURL(value)) {
                throw new IllegalArgumentException("Invalid HTTP(S) URL: " + value);
            }
            normalized.add(value);
        }
        return Collections.unmodifiableList(new ArrayList<>(normalized));
    }

    private static List<String> normalizeDnsResolvers(List<String> resolvers) {
        if (resolvers == null) {
            return Collections.emptyList();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String raw : resolvers) {
            if (raw == null) continue;
            String value = raw.trim();
            if (!value.isEmpty()) {
                parseDnsResolver(value);
                normalized.add(value);
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(normalized));
    }

    private static boolean isValidURL(String address) {
        if (address == null) {
            throw new IllegalArgumentException("url == null");
        }
        try {
            URL url = new URL(address);
            url.toURI();
            return "http".equalsIgnoreCase(url.getProtocol())
                    || "https".equalsIgnoreCase(url.getProtocol());
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String dnsEndpointLabel(String resolver) {
        DnsResolver endpoint = parseDnsResolver(resolver);
        return "dns://" + endpoint.host + ":" + endpoint.port;
    }

    private static DnsResolver parseDnsResolver(String resolver) {
        String host = resolver;
        int port = DNS_PORT;
        int firstColon = resolver.indexOf(':');
        int lastColon = resolver.lastIndexOf(':');
        if (firstColon > 0 && firstColon == lastColon) {
            host = resolver.substring(0, firstColon).trim();
            String portValue = resolver.substring(firstColon + 1).trim();
            try {
                port = Integer.parseInt(portValue);
            } catch (NumberFormatException error) {
                throw new IllegalArgumentException("Invalid DNS resolver port: " + resolver, error);
            }
        }
        if (host.isEmpty() || port < 1 || port > 65_535) {
            throw new IllegalArgumentException("Invalid DNS resolver: " + resolver);
        }
        return new DnsResolver(host, port);
    }

    private interface ProbeOperation {
        boolean run();
    }

    private static final class ProbeAttempt {
        private final String label;
        private final ProbeOperation operation;

        private ProbeAttempt(String label, ProbeOperation operation) {
            this.label = label;
            this.operation = operation;
        }
    }

    private static final class DnsResolver {
        private final String host;
        private final int port;

        private DnsResolver(String host, int port) {
            this.host = host;
            this.port = port;
        }
    }

    private static final class NetworkObserverImpl implements NetworkObserver {
        private final NetworkObserverConfig config;
        private final NetworkChangeListener listener;
        private final ScheduledExecutorService scheduler;
        private final AtomicLong epoch = new AtomicLong(0);
        private final AtomicBoolean closed = new AtomicBoolean(false);

        private volatile NetworkState lastConfirmedState;
        private volatile NetworkState lastRawSnapshot;
        private volatile ScheduledFuture<?> debounceFuture;
        private volatile Request currentDiagnosticRequest;

        private NetworkObserverImpl(NetworkObserverConfig config, NetworkChangeListener listener) {
            this.config = config;
            this.listener = listener;
            this.scheduler = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
                private int count;

                @Override
                public synchronized Thread newThread(Runnable r) {
                    Thread thread = new Thread(r, "desktop-network-observer-" + (++count));
                    thread.setDaemon(true);
                    return thread;
                }
            });
        }

        private void start() {
            try {
                lastConfirmedState = config.getSnapshotProvider().getSnapshot();
                lastRawSnapshot = lastConfirmedState;
                globalLastKnownNetworkState = lastConfirmedState;
            } catch (Exception ignored) {
                lastConfirmedState = new NetworkState(System.currentTimeMillis(), Collections.emptyList());
                lastRawSnapshot = lastConfirmedState;
            }

            scheduler.scheduleWithFixedDelay(
                    this::checkCycle,
                    config.getPollIntervalMillis(),
                    config.getPollIntervalMillis(),
                    TimeUnit.MILLISECONDS
            );
        }

        @Override
        public NetworkState getLastKnownNetworkState() {
            return lastConfirmedState;
        }

        @Override
        public void checkNow() {
            if (!closed.get()) {
                scheduler.submit(this::checkCycle);
            }
        }

        private synchronized void checkCycle() {
            if (closed.get()) {
                return;
            }
            try {
                NetworkState currentRaw = config.getSnapshotProvider().getSnapshot();
                // A new raw variation has occurred since the last raw snapshot
                if (currentRaw.isMaterialChange(lastRawSnapshot)) {
                    lastRawSnapshot = currentRaw;
                    cancelPendingDebounce();
                    debounceFuture = scheduler.schedule(
                            this::executeDebouncedChange,
                            config.getDebounceDelayMillis(),
                            TimeUnit.MILLISECONDS
                    );
                } else if (currentRaw.isMaterialChange(lastConfirmedState)) {
                    // Raw state differs from confirmed state, but is identical to lastRawSnapshot.
                    // If no debounce task is currently pending, schedule one.
                    ScheduledFuture<?> future = debounceFuture;
                    if (future == null || future.isDone()) {
                        debounceFuture = scheduler.schedule(
                                this::executeDebouncedChange,
                                config.getDebounceDelayMillis(),
                                TimeUnit.MILLISECONDS
                        );
                    }
                }
            } catch (Exception ignored) {
            }
        }

        private synchronized void executeDebouncedChange() {
            if (closed.get()) {
                return;
            }
            try {
                NetworkState finalSnapshot = config.getSnapshotProvider().getSnapshot();
                if (finalSnapshot.isMaterialChange(lastConfirmedState)) {
                    lastConfirmedState = finalSnapshot;
                    globalLastKnownNetworkState = finalSnapshot;
                    long currentEpoch = epoch.incrementAndGet();

                    // Cancel previous in-flight diagnostic if running
                    cancelActiveDiagnostic();

                    // 1. Notify network state change listener
                    try {
                        listener.onNetworkStateChanged(finalSnapshot);
                    } catch (Throwable ignored) {
                    }

                    // 2. Trigger automatic diagnostic if enabled
                    if (config.isAutomaticDiagnosticsOnNetworkChanges()) {
                        currentDiagnosticRequest = checkInternetAsync(
                                config.getDnsResolvers(),
                                config.getHosts(),
                                config.getDnsProbeStrategy(),
                                config.isExtremeMode(),
                                result -> {
                                    if (!closed.get() && epoch.get() == currentEpoch) {
                                        try {
                                            listener.onDiagnosticResult(finalSnapshot, result);
                                        } catch (Throwable ignored) {
                                        }
                                    }
                                }
                        );
                    }
                }
            } catch (Exception ignored) {
            }
        }

        private void cancelPendingDebounce() {
            ScheduledFuture<?> future = debounceFuture;
            if (future != null && !future.isDone()) {
                future.cancel(false);
            }
        }

        private void cancelActiveDiagnostic() {
            Request request = currentDiagnosticRequest;
            if (request != null) {
                request.cancel();
                currentDiagnosticRequest = null;
            }
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                cancelPendingDebounce();
                cancelActiveDiagnostic();
                scheduler.shutdownNow();
            }
        }

        @Override
        public boolean isClosed() {
            return closed.get();
        }
    }
}
