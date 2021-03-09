package com.vlasenko.task.throttling;

import com.vlasenko.task.profile.service.ProfileService;
import com.vlasenko.task.throttling.sla.SlaService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ThrottlingServiceImpl implements ThrottlingService {
    private final SlaService slaService;
    private final ProfileService profileService;

    public ThrottlingServiceImpl(@Value("${app.throttling.guest-rps:10}") int guestRPS,
                                 SlaService slaService, ProfileService profileService) {
        this.slaService = slaService;
        this.profileService = profileService;
        this.guestRpsData = new RpsLimit(guestRPS);
    }

    private final RpsLimit guestRpsData;
    private final Map<String, RpsLimit> userRpsCache = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<SlaService.SLA>> futures = new ConcurrentHashMap<>();

    @Override
    public boolean isRequestAllowed(Optional<String> tokenOpt) {
        if (tokenOpt.isPresent()) {
            var token = tokenOpt.get();
            var user = profileService.findByToken(token).orElseThrow();
            if (userRpsCache.containsKey(user)) {
                return userRpsCache.get(user).countAndGet() >= 0;
            } else {
                findOutUserRps(token, user);
            }
        }
        return guestRpsData.countAndGet() >= 0;
    }

    private void findOutUserRps(String token, String user) {
        if (!futures.containsKey(user)) {
            var future = slaService.getSlaByToken(token);
            futures.put(user, future);
            future.whenComplete((sla, ex) -> {
                userRpsCache.put(sla.getUser(), new RpsLimit(sla.getRps()));
                futures.remove(sla.getUser());
            });
        }
    }

    private static class RpsLimit {
        private final int limit;
        private final AtomicInteger count;
        private long second = Instant.now().getEpochSecond();

        public RpsLimit(int limit) {
            this.limit = limit;
            this.count = new AtomicInteger(limit);
        }

        public synchronized int countAndGet() {
            checkTime();
            return count.decrementAndGet();
        }

        private void checkTime() {
            var now = Instant.now().getEpochSecond();
            if (second != now) {
                second = now;
                count.set(limit);
            }
        }
    }
}