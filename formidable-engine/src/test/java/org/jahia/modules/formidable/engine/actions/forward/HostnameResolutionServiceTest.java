package org.jahia.modules.formidable.engine.actions.forward;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HostnameResolutionServiceTest {

    private HostnameResolutionService service;

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.deactivate();
        }
    }

    @Test
    void resolveAllCachesResultsWithinTtl() throws Exception {
        // Given repeated lookups within the cache TTL,
        // the service must return the cached addresses and perform only one resolver call.
        AtomicInteger calls = new AtomicInteger();
        AtomicLong now = new AtomicLong(1_000_000_000L);
        InetAddress[] expected = {InetAddress.getByAddress("public.example", new byte[]{1, 2, 3, 4})};

        service = new HostnameResolutionService(
                Duration.ofMillis(250),
                Duration.ofSeconds(30),
                Executors.newSingleThreadExecutor(),
                hostname -> {
                    calls.incrementAndGet();
                    return expected;
                },
                now::get
        );

        assertArrayEquals(expected, service.resolveAll("public.example"));
        assertArrayEquals(expected, service.resolveAll("public.example"));
        assertEquals(1, calls.get());
    }

    @Test
    void resolveAllRefreshesCacheAfterTtlExpires() throws Exception {
        // Given a second lookup after the cache TTL has expired,
        // the service must refresh the entry and call the resolver again.
        AtomicInteger calls = new AtomicInteger();
        AtomicLong now = new AtomicLong(1_000_000_000L);
        InetAddress[] expected = {InetAddress.getByAddress("public.example", new byte[]{1, 2, 3, 4})};

        service = new HostnameResolutionService(
                Duration.ofMillis(250),
                Duration.ofSeconds(30),
                Executors.newSingleThreadExecutor(),
                hostname -> {
                    calls.incrementAndGet();
                    return expected;
                },
                now::get
        );

        service.resolveAll("public.example");
        now.addAndGet(Duration.ofSeconds(31).toNanos());
        service.resolveAll("public.example");

        assertEquals(2, calls.get());
    }

    @Test
    void resolveAllTimesOutSlowLookups() {
        // Given a resolver that takes longer than the configured timeout,
        // the service must abort the lookup and surface a TimeoutException.
        service = new HostnameResolutionService(
                Duration.ofMillis(10),
                Duration.ofSeconds(30),
                Executors.newSingleThreadExecutor(),
                hostname -> {
                    try {
                        Thread.sleep(250);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return new InetAddress[]{InetAddress.getByAddress(hostname, new byte[]{1, 2, 3, 4})};
                },
                System::nanoTime
        );

        assertThrows(TimeoutException.class, () -> service.resolveAll("slow.example"));
    }

    @Test
    void resolveAllFailsFastWhenResolverPoolIsSaturated() throws Exception {
        // Given a non-interruptible resolver task occupying the only worker,
        // the service must fail fast instead of queueing more DNS lookups behind it.
        CountDownLatch resolverStarted = new CountDownLatch(1);
        CountDownLatch releaseResolver = new CountDownLatch(1);
        AtomicReference<Throwable> backgroundFailure = new AtomicReference<>();
        ExecutorService executor = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new SynchronousQueue<>(),
                new ThreadPoolExecutor.AbortPolicy()
        );

        service = new HostnameResolutionService(
                Duration.ofMillis(20),
                Duration.ofSeconds(30),
                executor,
                hostname -> {
                    resolverStarted.countDown();
                    while (true) {
                        try {
                            releaseResolver.await();
                            break;
                        } catch (InterruptedException ignored) {
                            // Simulate InetAddress#getAllByName staying blocked after interruption.
                        }
                    }
                    return new InetAddress[]{InetAddress.getByAddress(hostname, new byte[]{1, 2, 3, 4})};
                },
                System::nanoTime
        );

        Thread firstLookup = new Thread(() -> {
            try {
                service.resolveAll("stuck.example");
                backgroundFailure.set(new AssertionError("Expected first lookup to time out"));
            } catch (TimeoutException expected) {
                // The caller times out, but the resolver worker stays occupied until released below.
            } catch (Throwable t) {
                backgroundFailure.set(t);
            }
        });
        firstLookup.start();
        assertTrue(resolverStarted.await(1, TimeUnit.SECONDS));

        // When a second lookup arrives while the resolver pool is already saturated.
        TimeoutException exception =
                assertThrows(TimeoutException.class, () -> service.resolveAll("second.example"));

        // Then the request is rejected immediately instead of waiting behind the stuck worker.
        assertEquals("Hostname resolution executor saturated for 'second.example'", exception.getMessage());

        releaseResolver.countDown();
        firstLookup.join(1_000);
        assertNull(backgroundFailure.get());
    }
}
