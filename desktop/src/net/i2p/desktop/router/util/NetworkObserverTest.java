/*
 * SPDX-License-Identifier: MIT
 *
 * Java SE Desktop reimplementation of ConnectivityAndInternetAccess.
 */
package net.i2p.desktop.router.util.test;

import net.i2p.desktop.router.util.ConnectivityAndInternetAccess;
import net.i2p.desktop.router.util.ConnectivityAndInternetAccess.InternetResult;
import net.i2p.desktop.router.util.ConnectivityAndInternetAccess.NetworkObserverConfig;
import net.i2p.desktop.router.util.DnsProbeStrategy;
import net.i2p.desktop.router.util.InterfaceState;
import net.i2p.desktop.router.util.NetworkChangeListener;
import net.i2p.desktop.router.util.NetworkObserver;
import net.i2p.desktop.router.util.NetworkSnapshotProvider;
import net.i2p.desktop.router.util.NetworkState;

import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class NetworkObserverTest {

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println(" Running ConnectivityAndInternetAccess Test Suite ");
        System.out.println("==================================================");

        int failures = 0;
        failures += runTest("1. snapshot equal -> 0 callbacks", NetworkObserverTest::testSnapshotEqualNoCallback);
        failures += runTest("2. network lost -> callback", NetworkObserverTest::testNetworkLostCallback);
        failures += runTest("3. network restored -> callback", NetworkObserverTest::testNetworkRestoredCallback);
        failures += runTest("4. diagnostics disabled -> 0 probes", NetworkObserverTest::testDiagnosticsDisabledNoProbes);
        failures += runTest("5. diagnostics enabled -> 1 probe", NetworkObserverTest::testDiagnosticsEnabledOneProbe);
        failures += runTest("6. event storm -> 1 probe after debounce", NetworkObserverTest::testEventStormCoalesced);
        failures += runTest("7. address changed -> event", NetworkObserverTest::testAddressChangedEvent);
        failures += runTest("8. stale diagnostic -> discarded", NetworkObserverTest::testStaleDiagnosticDiscarded);

        System.out.println("==================================================");
        if (failures == 0) {
            System.out.println(" ALL TESTS PASSED SUCCESSFULLY! ");
            System.out.println("==================================================");
        } else {
            System.err.println(" TEST SUITE FAILED WITH " + failures + " FAILURE(S) ");
            System.out.println("==================================================");
            System.exit(1);
        }
    }

    private interface TestRunnable {
        void run() throws Exception;
    }

    private static int runTest(String name, TestRunnable test) {
        System.out.print("[TEST] " + name + " ... ");
        try {
            test.run();
            System.out.println("PASSED");
            return 0;
        } catch (Throwable error) {
            System.out.println("FAILED");
            error.printStackTrace(System.out);
            return 1;
        }
    }

    // --- Mock Helpers ---

    private static final class TestSnapshotProvider implements NetworkSnapshotProvider {
        private volatile NetworkState currentSnapshot;

        private TestSnapshotProvider(NetworkState initial) {
            this.currentSnapshot = initial;
        }

        public void setSnapshot(NetworkState snapshot) {
            this.currentSnapshot = snapshot;
        }

        @Override
        public NetworkState getSnapshot() throws SocketException {
            return currentSnapshot;
        }
    }

    private static NetworkState createMockState(boolean hasInterface, String ipAddress) {
        List<InterfaceState> interfaces = new ArrayList<>();
        if (hasInterface) {
            List<InetAddress> addrs = new ArrayList<>();
            try {
                addrs.add(InetAddress.getByName(ipAddress != null ? ipAddress : "192.168.1.100"));
            } catch (UnknownHostException ignored) {
            }
            interfaces.add(new InterfaceState(
                    "eth0",
                    "Ethernet Adapter",
                    1,
                    true,  // isUp
                    false, // isLoopback
                    false, // isVirtual
                    1500,
                    new byte[]{0x00, 0x11, 0x22, 0x33, 0x44, 0x55},
                    addrs
            ));
        }
        return new NetworkState(System.currentTimeMillis(), interfaces);
    }

    // --- Test Scenarios ---

    private static void testSnapshotEqualNoCallback() throws Exception {
        NetworkState state1 = createMockState(true, "192.168.1.10");
        TestSnapshotProvider provider = new TestSnapshotProvider(state1);
        AtomicInteger callbacks = new AtomicInteger(0);

        NetworkObserverConfig config = NetworkObserverConfig.builder()
                .snapshotProvider(provider)
                .pollInterval(Duration.ofMillis(50))
                .debounceDelay(Duration.ofMillis(50))
                .automaticDiagnosticsOnNetworkChanges(false)
                .build();

        try (NetworkObserver observer = ConnectivityAndInternetAccess.observeNetwork(config, state -> callbacks.incrementAndGet())) {
            Thread.sleep(200);
            observer.checkNow();
            Thread.sleep(200);

            if (callbacks.get() != 0) {
                throw new AssertionError("Expected 0 callbacks for identical snapshots, but got: " + callbacks.get());
            }
        }
    }

    private static void testNetworkLostCallback() throws Exception {
        NetworkState stateUp = createMockState(true, "192.168.1.10");
        NetworkState stateDown = createMockState(false, null);
        TestSnapshotProvider provider = new TestSnapshotProvider(stateUp);

        CountDownLatch latch = new CountDownLatch(1);
        List<NetworkState> received = new ArrayList<>();

        NetworkObserverConfig config = NetworkObserverConfig.builder()
                .snapshotProvider(provider)
                .pollInterval(Duration.ofMillis(50))
                .debounceDelay(Duration.ofMillis(50))
                .build();

        try (NetworkObserver observer = ConnectivityAndInternetAccess.observeNetwork(config, state -> {
            received.add(state);
            latch.countDown();
        })) {
            provider.setSnapshot(stateDown);
            observer.checkNow();

            boolean ok = latch.await(2, TimeUnit.SECONDS);
            if (!ok) {
                throw new AssertionError("Callback timeout when network lost");
            }
            if (received.get(0).hasUsableInterface()) {
                throw new AssertionError("Expected usable interface = false upon network loss");
            }
        }
    }

    private static void testNetworkRestoredCallback() throws Exception {
        NetworkState stateDown = createMockState(false, null);
        NetworkState stateUp = createMockState(true, "192.168.1.50");
        TestSnapshotProvider provider = new TestSnapshotProvider(stateDown);

        CountDownLatch latch = new CountDownLatch(1);
        List<NetworkState> received = new ArrayList<>();

        NetworkObserverConfig config = NetworkObserverConfig.builder()
                .snapshotProvider(provider)
                .pollInterval(Duration.ofMillis(50))
                .debounceDelay(Duration.ofMillis(50))
                .build();

        try (NetworkObserver observer = ConnectivityAndInternetAccess.observeNetwork(config, state -> {
            received.add(state);
            latch.countDown();
        })) {
            provider.setSnapshot(stateUp);
            observer.checkNow();

            boolean ok = latch.await(2, TimeUnit.SECONDS);
            if (!ok) {
                throw new AssertionError("Callback timeout when network restored");
            }
            if (!received.get(0).hasUsableInterface()) {
                throw new AssertionError("Expected usable interface = true upon network restoration");
            }
        }
    }

    private static void testDiagnosticsDisabledNoProbes() throws Exception {
        NetworkState state1 = createMockState(true, "10.0.0.1");
        NetworkState state2 = createMockState(true, "10.0.0.2");
        TestSnapshotProvider provider = new TestSnapshotProvider(state1);

        AtomicInteger probeExecutions = new AtomicInteger(0);
        CountDownLatch stateLatch = new CountDownLatch(1);

        DnsProbeStrategy countingStrategy = (dnsResolvers, deadline) -> {
            probeExecutions.incrementAndGet();
            return "dns://10.0.0.1:53";
        };

        NetworkObserverConfig config = NetworkObserverConfig.builder()
                .snapshotProvider(provider)
                .pollInterval(Duration.ofMillis(50))
                .debounceDelay(Duration.ofMillis(50))
                .automaticDiagnosticsOnNetworkChanges(false) // Disabled!
                .dnsProbeStrategy(countingStrategy)
                .build();

        try (NetworkObserver observer = ConnectivityAndInternetAccess.observeNetwork(config, new NetworkChangeListener() {
            @Override
            public void onNetworkStateChanged(NetworkState state) {
                stateLatch.countDown();
            }

            @Override
            public void onDiagnosticResult(NetworkState state, InternetResult result) {
                probeExecutions.incrementAndGet();
            }
        })) {
            provider.setSnapshot(state2);
            observer.checkNow();

            stateLatch.await(2, TimeUnit.SECONDS);
            Thread.sleep(200);

            if (probeExecutions.get() != 0) {
                throw new AssertionError("Expected 0 probes when diagnostics disabled, but got: " + probeExecutions.get());
            }
        }
    }

    private static void testDiagnosticsEnabledOneProbe() throws Exception {
        NetworkState state1 = createMockState(true, "10.0.0.1");
        NetworkState state2 = createMockState(true, "10.0.0.2");
        TestSnapshotProvider provider = new TestSnapshotProvider(state1);

        AtomicInteger probeExecutions = new AtomicInteger(0);
        CountDownLatch diagLatch = new CountDownLatch(1);

        DnsProbeStrategy countingStrategy = (dnsResolvers, deadline) -> {
            probeExecutions.incrementAndGet();
            return "dns://1.1.1.1:53";
        };

        NetworkObserverConfig config = NetworkObserverConfig.builder()
                .snapshotProvider(provider)
                .pollInterval(Duration.ofMillis(50))
                .debounceDelay(Duration.ofMillis(50))
                .automaticDiagnosticsOnNetworkChanges(true) // Enabled!
                .dnsResolvers(Collections.singletonList("1.1.1.1"))
                .dnsProbeStrategy(countingStrategy)
                .build();

        try (NetworkObserver observer = ConnectivityAndInternetAccess.observeNetwork(config, new NetworkChangeListener() {
            @Override
            public void onNetworkStateChanged(NetworkState state) {
            }

            @Override
            public void onDiagnosticResult(NetworkState state, InternetResult result) {
                diagLatch.countDown();
            }
        })) {
            provider.setSnapshot(state2);
            observer.checkNow();

            boolean ok = diagLatch.await(2, TimeUnit.SECONDS);
            if (!ok) {
                throw new AssertionError("Diagnostic result callback timeout");
            }
            if (probeExecutions.get() != 1) {
                throw new AssertionError("Expected exactly 1 probe execution, but got: " + probeExecutions.get());
            }
        }
    }

    private static void testEventStormCoalesced() throws Exception {
        NetworkState state0 = createMockState(true, "10.0.0.1");
        TestSnapshotProvider provider = new TestSnapshotProvider(state0);

        AtomicInteger stateChanges = new AtomicInteger(0);
        AtomicInteger probeExecutions = new AtomicInteger(0);
        CountDownLatch diagLatch = new CountDownLatch(1);

        DnsProbeStrategy countingStrategy = (dnsResolvers, deadline) -> {
            probeExecutions.incrementAndGet();
            return "dns://1.1.1.1:53";
        };

        NetworkObserverConfig config = NetworkObserverConfig.builder()
                .snapshotProvider(provider)
                .pollInterval(Duration.ofMillis(50))
                .debounceDelay(Duration.ofMillis(300)) // 300ms debounce
                .automaticDiagnosticsOnNetworkChanges(true)
                .dnsResolvers(Collections.singletonList("1.1.1.1"))
                .dnsProbeStrategy(countingStrategy)
                .build();

        try (NetworkObserver observer = ConnectivityAndInternetAccess.observeNetwork(config, new NetworkChangeListener() {
            @Override
            public void onNetworkStateChanged(NetworkState state) {
                stateChanges.incrementAndGet();
            }

            @Override
            public void onDiagnosticResult(NetworkState state, InternetResult result) {
                diagLatch.countDown();
            }
        })) {
            // Rapid fire event storm within 300ms debounce window
            for (int i = 2; i <= 10; i++) {
                provider.setSnapshot(createMockState(true, "10.0.0." + i));
                observer.checkNow();
                Thread.sleep(25);
            }

            boolean ok = diagLatch.await(3, TimeUnit.SECONDS);
            if (!ok) {
                throw new AssertionError("Diagnostic callback timeout after event storm");
            }

            if (stateChanges.get() != 1) {
                throw new AssertionError("Expected coalesced state changes to be 1, but got: " + stateChanges.get());
            }
            if (probeExecutions.get() != 1) {
                throw new AssertionError("Expected exactly 1 probe after debounced event storm, but got: " + probeExecutions.get());
            }
        }
    }

    private static void testAddressChangedEvent() throws Exception {
        NetworkState state1 = createMockState(true, "192.168.1.10");
        NetworkState state2 = createMockState(true, "192.168.1.99");
        TestSnapshotProvider provider = new TestSnapshotProvider(state1);

        CountDownLatch stateLatch = new CountDownLatch(1);
        List<NetworkState> received = new ArrayList<>();

        NetworkObserverConfig config = NetworkObserverConfig.builder()
                .snapshotProvider(provider)
                .pollInterval(Duration.ofMillis(50))
                .debounceDelay(Duration.ofMillis(50))
                .build();

        try (NetworkObserver observer = ConnectivityAndInternetAccess.observeNetwork(config, state -> {
            received.add(state);
            stateLatch.countDown();
        })) {
            provider.setSnapshot(state2);
            observer.checkNow();

            boolean ok = stateLatch.await(2, TimeUnit.SECONDS);
            if (!ok) {
                throw new AssertionError("Address change event timeout");
            }
            String newIp = received.get(0).getPrimaryInterface().getIpv4Addresses().get(0).getHostAddress();
            if (!"192.168.1.99".equals(newIp)) {
                throw new AssertionError("Expected new IP to be 192.168.1.99 but got: " + newIp);
            }
        }
    }

    private static void testStaleDiagnosticDiscarded() throws Exception {
        NetworkState state1 = createMockState(true, "10.0.0.1");
        NetworkState state2 = createMockState(true, "10.0.0.2");
        NetworkState state3 = createMockState(true, "10.0.0.3");
        TestSnapshotProvider provider = new TestSnapshotProvider(state1);

        AtomicInteger deliveredDiagnostics = new AtomicInteger(0);

        DnsProbeStrategy slowStrategy = (dnsResolvers, deadline) -> {
            try {
                Thread.sleep(600); // Slow probe to allow rapid network change
            } catch (InterruptedException ignored) {
            }
            return "dns://1.1.1.1:53";
        };

        NetworkObserverConfig config = NetworkObserverConfig.builder()
                .snapshotProvider(provider)
                .pollInterval(Duration.ofMillis(50))
                .debounceDelay(Duration.ofMillis(50))
                .automaticDiagnosticsOnNetworkChanges(true)
                .dnsResolvers(Collections.singletonList("1.1.1.1"))
                .dnsProbeStrategy(slowStrategy)
                .build();

        try (NetworkObserver observer = ConnectivityAndInternetAccess.observeNetwork(config, new NetworkChangeListener() {
            @Override
            public void onNetworkStateChanged(NetworkState state) {
            }

            @Override
            public void onDiagnosticResult(NetworkState state, InternetResult result) {
                deliveredDiagnostics.incrementAndGet();
            }
        })) {
            // First change triggers slow diagnostic
            provider.setSnapshot(state2);
            observer.checkNow();
            Thread.sleep(150);

            // Second change occurs while first diagnostic is running -> invalidates/discards stale diagnostic
            provider.setSnapshot(state3);
            observer.checkNow();

            Thread.sleep(1500);

            // Exactly 1 diagnostic (the latest one) should be delivered, stale one discarded!
            if (deliveredDiagnostics.get() > 1) {
                throw new AssertionError("Stale diagnostic was not discarded! Delivered count: " + deliveredDiagnostics.get());
            }
        }
    }
}
