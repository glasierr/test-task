package com.vlasenko.task.throttling.sla;

import java.util.concurrent.CompletableFuture;

public interface SlaService {

    CompletableFuture<SLA> getSlaByToken(String token);

    class SLA {
        private final String user;
        private final int rps;

        public SLA(String user, int rps) {
            this.user = user;
            this.rps = rps;
        }

        public String getUser() {
            return user;
        }

        public int getRps() {
            return rps;
        }
    }
}
