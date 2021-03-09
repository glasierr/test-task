package com.vlasenko.task.throttling;

import java.util.Optional;

public interface ThrottlingService {
    /**
     * @return true if request is within allowed request per second (RPS) or false otherwise
     */
    boolean isRequestAllowed(Optional<String> token);
}
