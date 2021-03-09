package com.vlasenko.task.throttling;

import com.vlasenko.task.profile.service.ProfileService;
import com.vlasenko.task.throttling.sla.SlaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ThrottlingServiceImplTest {

    @Mock
    private ProfileService profileService;

    @Mock
    private SlaService slaService;

    private final int guestRps = 5;

    private ThrottlingService service;

    @BeforeEach
    void setUp() {
        service = new ThrottlingServiceImpl(guestRps, slaService, profileService);
    }

    @Test
    void shouldAllowGuestAccess() {
        assertTrue(service.isRequestAllowed(Optional.empty()));
    }

    @Test
    void shouldAllowGuestAccessForUnderLimitRps() {
        waitTillTheStartOfTheSecond();

        for (int i = 0; i < guestRps; i++) {
            assertTrue(service.isRequestAllowed(Optional.empty()));
        }
    }

    @Test
    void shouldNotAllowGuestAccessIfExceededRps() {
        waitTillTheStartOfTheSecond();

        for (int i = 0; i < guestRps; i++) {
            service.isRequestAllowed(Optional.empty());
        }
        assertFalse(service.isRequestAllowed(Optional.empty()));
    }

    @Test
    void shouldAllowGuestAccessOnTheNextSecondAfterExceededRps() {
        waitTillTheStartOfTheSecond();
        for (int i = 0; i < guestRps; i++) {
            service.isRequestAllowed(Optional.empty());
        }

        waitTillTheStartOfTheSecond();
        for (int i = 0; i < guestRps; i++) {
            assertTrue(service.isRequestAllowed(Optional.empty()));
        }
    }

    @Test
    void shouldAllowGuestAccessWithinLimitsForConcurrentAccess() throws Exception {
        var allowed = new AtomicInteger();
        var rejected = new AtomicInteger();
        var countDownLatch = new CountDownLatch(5);
        waitTillTheStartOfTheSecond();

        for (int i = 0; i < 5; i++) {
            new Thread(() -> {
                for (int j = 0; j < 3; j++) {
                    if (service.isRequestAllowed(Optional.empty())) {
                        allowed.incrementAndGet();
                    } else {
                        rejected.incrementAndGet();
                    }
                }
                countDownLatch.countDown();
            }).start();
        }

        countDownLatch.await();
        assertEquals(5, allowed.get());
        assertEquals(10, rejected.get());
    }

    @Test
    void shouldAllowAsGuestIfFirstTimeUser() {
        var token = "tok";
        var user = "user";
        when(profileService.findByToken(token)).thenReturn(Optional.of(user));
        when(slaService.getSlaByToken(token)).thenReturn(new CompletableFuture<>());

        assertTrue(service.isRequestAllowed(Optional.of(token)));
    }

    @Test
    void shouldAllowAsGuestUntilUserRpsIsEvaluated() {
        var token = "tok";
        var user = "user";
        when(profileService.findByToken(token)).thenReturn(Optional.of(user));
        when(slaService.getSlaByToken(token)).thenReturn(new CompletableFuture<>());

        for (int i = 0; i < guestRps; i++) {
            assertTrue(service.isRequestAllowed(Optional.of(token)));
        }
    }

    @Test
    void shouldNotAllowUserAsGuestIfGuestRpsExceeded() {
        var token = "tok";
        var user = "user";
        when(profileService.findByToken(token)).thenReturn(Optional.of(user));
        when(slaService.getSlaByToken(token)).thenReturn(new CompletableFuture<>());
        waitTillTheStartOfTheSecond();

        for (int i = 0; i < guestRps; i++) {
            assertTrue(service.isRequestAllowed(Optional.empty()));
        }
        assertFalse(service.isRequestAllowed(Optional.of(token)));
    }

    @Test
    void shouldAllowUserWithHisRpsWhenCached() {
        var token = "tok";
        var user = "user";
        var future = new CompletableFuture<SlaService.SLA>();
        when(profileService.findByToken(token)).thenReturn(Optional.of(user));
        when(slaService.getSlaByToken(token)).thenReturn(future);

        // Create future
        service.isRequestAllowed(Optional.of(token));
        // Cache user's RPS
        future.complete(new SlaService.SLA(user, 10));
        waitTillTheStartOfTheSecond();

        for (int i = 0; i < 10; i++) {
            assertTrue(service.isRequestAllowed(Optional.of(token)));
        }
    }

    @Test
    void shouldNotAllowUserWhenHisRpsExceeded() {
        var token = "tok";
        var user = "user";
        var future = new CompletableFuture<SlaService.SLA>();
        when(profileService.findByToken(token)).thenReturn(Optional.of(user));
        when(slaService.getSlaByToken(token)).thenReturn(future);

        // Create future
        service.isRequestAllowed(Optional.of(token));
        // Cache user's RPS
        future.complete(new SlaService.SLA(user, 10));
        waitTillTheStartOfTheSecond();

        for (int i = 0; i < 10; i++) {
            service.isRequestAllowed(Optional.of(token));
        }
        assertFalse(service.isRequestAllowed(Optional.of(token)));
    }

    @Test
    void shouldAllowUserOnTheNextSecond() {
        var token = "tok";
        var user = "user";
        var future = new CompletableFuture<SlaService.SLA>();
        when(profileService.findByToken(token)).thenReturn(Optional.of(user));
        when(slaService.getSlaByToken(token)).thenReturn(future);

        // Create future
        service.isRequestAllowed(Optional.of(token));
        // Cache user's RPS
        future.complete(new SlaService.SLA(user, 2));
        waitTillTheStartOfTheSecond();
        for (int i = 0; i < 2; i++) {
            service.isRequestAllowed(Optional.of(token));
        }
        assertFalse(service.isRequestAllowed(Optional.of(token)));

        waitTillTheStartOfTheSecond();
        for (int i = 0; i < 2; i++) {
            service.isRequestAllowed(Optional.of(token));
        }
        assertFalse(service.isRequestAllowed(Optional.of(token)));
    }

    @Test
    void shouldUseSameUserDataForDifferentUserTokens() {
        var user = "user";
        var future = new CompletableFuture<SlaService.SLA>();
        when(profileService.findByToken(anyString())).thenReturn(Optional.of(user));
        when(slaService.getSlaByToken(anyString())).thenReturn(future);

        // Create future
        service.isRequestAllowed(Optional.of("123"));
        // Cache user's RPS
        future.complete(new SlaService.SLA(user, 1));
        waitTillTheStartOfTheSecond();

        assertTrue(service.isRequestAllowed(Optional.of("one")));
        assertFalse(service.isRequestAllowed(Optional.of("two")));
        assertFalse(service.isRequestAllowed(Optional.of("three")));
        assertFalse(service.isRequestAllowed(Optional.of("four")));
    }

    @Test
    void shouldAllowDifferentUsersAccordingToTheirRps() {
        var user1 = "user";
        var user2 = "user2";
        var token1 = "t1";
        var token2 = "t2";
        var future1 = new CompletableFuture<SlaService.SLA>();
        var future2 = new CompletableFuture<SlaService.SLA>();
        when(profileService.findByToken(anyString())).then(invocation -> {
            if (invocation.getArgument(0).equals(token1)) {
                return Optional.of(user1);
            }
            return Optional.of(user2);
        });
        when(slaService.getSlaByToken(anyString())).then(invocation -> {
            if (invocation.getArgument(0).equals(token1)) {
                return future1;
            }
            return future2;
        });

        // Create future
        service.isRequestAllowed(Optional.of(token1));
        service.isRequestAllowed(Optional.of(token2));
        // Cache user's RPS
        future1.complete(new SlaService.SLA(user1, 1));
        future2.complete(new SlaService.SLA(user2, 2));
        waitTillTheStartOfTheSecond();

        assertTrue(service.isRequestAllowed(Optional.of(token1)));
        assertFalse(service.isRequestAllowed(Optional.of(token1)));

        assertTrue(service.isRequestAllowed(Optional.of(token2)));
        assertTrue(service.isRequestAllowed(Optional.of(token2)));
        assertFalse(service.isRequestAllowed(Optional.of(token2)));
    }

    @Test
    void shouldEvaluateGuestsWithAverage5msTime() {
        List<Long> list = new ArrayList<>(1000);
        for (int i = 0; i < 1000; i++) {
            var curr = System.currentTimeMillis();
            service.isRequestAllowed(Optional.empty());
            list.add(System.currentTimeMillis() - curr);
        }
        assertTrue(list.stream().mapToLong(Long::valueOf).average().orElse(100) <= 5.0);
    }

    @Test
    void shouldEvaluateUsersWithAverage5msTime() {
        var token1 = "t1";
        var token2 = "t2";
        var user1 = "u1";
        var user2 = "u2";
        var future1 = new CompletableFuture<SlaService.SLA>();
        var future2 = new CompletableFuture<SlaService.SLA>();
        when(profileService.findByToken(anyString())).then(invocation -> {
            if (invocation.getArgument(0).equals(token1)) {
                return Optional.of(user1);
            }
            return Optional.of(user2);
        });
        when(slaService.getSlaByToken(anyString())).then(invocation -> {
            if (invocation.getArgument(0).equals(token1)) {
                return future1;
            }
            return future2;
        });

        var first = true;
        var list = new LinkedList<Long>();
        for (int i = 0; i < 1000; i++) {
            if (i == 123) {
                future1.complete(new SlaService.SLA(user1, 35));
            } else if (i == 234) {
                future2.complete(new SlaService.SLA(user2, 135));
            }
            var curr = System.currentTimeMillis();
            service.isRequestAllowed(Optional.of(first ? token1 : token2));
            first = !first;
            list.add(System.currentTimeMillis() - curr);
        }

        assertTrue(list.stream().mapToLong(Long::valueOf).average().orElse(100) <= 5.0);
    }

    private void waitTillTheStartOfTheSecond() {
        try {
            Thread.sleep(1001 - LocalTime.now().getNano() / 1_000_000);
        } catch (InterruptedException ignored) {
        }
    }
}
