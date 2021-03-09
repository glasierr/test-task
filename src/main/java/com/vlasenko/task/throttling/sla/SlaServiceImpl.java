package com.vlasenko.task.throttling.sla;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

@Service
public class SlaServiceImpl implements SlaService {
    private static final Map<String, SLA> map = new HashMap<>();

    static {
        var user1 = new SLA("user1", 1);
        map.put("token11", user1);
        map.put("token12", user1);
        map.put("token2", new SLA("user2", 2));
    }

    @Override
    public CompletableFuture<SLA> getSlaByToken(String token) {
        var completableFuture = new CompletableFuture<SLA>();

        Executors.newCachedThreadPool().submit(() -> {
            Thread.sleep(1_500);
            completableFuture.complete(map.get(token));
            return null;
        });

        return completableFuture;
    }
}
