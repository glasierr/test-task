package com.vlasenko.task.profile.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ProfileService {
    private static final Map<String, String> map = new HashMap<>();

    static {
        map.put("token11", "user1");
        map.put("token12", "user1");
        map.put("token2", "user2");
    }

    public Optional<String> findByToken(String token) {
        return Optional.ofNullable(map.get(token));
    }
}
