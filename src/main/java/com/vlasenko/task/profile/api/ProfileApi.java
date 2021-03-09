package com.vlasenko.task.profile.api;

import com.vlasenko.task.throttling.ThrottlingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
public class ProfileApi {

    @Autowired
    private ThrottlingService throttlingService;

    @Value("${app.throttling.enabled:true}")
    private boolean throttlingEnabled;

    @GetMapping(path = "/greetings")
    public String greetThemAll(@RequestHeader(value = "token", required = false) String token) {
        if (throttlingEnabled && !throttlingService.isRequestAllowed(Optional.ofNullable(token))) {
            throw new ThrottlingException("Noo");
        }
        return "Hello there!!";
    }

    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    private static class ThrottlingException extends RuntimeException {
        public ThrottlingException(String message) {
            super(message);
        }
    }
}
