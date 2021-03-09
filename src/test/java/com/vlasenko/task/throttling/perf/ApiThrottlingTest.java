package com.vlasenko.task.throttling.perf;

import com.vlasenko.task.TestTaskApplication;
import com.vlasenko.task.profile.api.ProfileApi;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest(classes = TestTaskApplication.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ApiThrottlingTest {
    private static final String BASE_URL = "https://localhost:8080";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProfileApi profileApi;

    @Test
    void shouldPerformGoodUnderHighLoad() throws Exception {
        int amount = 15_000;
        var result = new long[amount];
        var tasks = new LinkedList<Callable<Void>>();
        var index = new AtomicInteger();
        var tokens = List.of("token11", "token2");
        for (int i = 0; i < amount; i++) {
            tokens.forEach(token ->
                    tasks.add(() -> {
                        var curr = System.currentTimeMillis();
                        mockMvc.perform(get(BASE_URL + "/greetings").header("token", token));
                        result[index.getAndIncrement()] = System.currentTimeMillis() - curr;
                        return null;
                    }));
        }
        Executors.newFixedThreadPool(10).invokeAll(tasks);

        assertEquals(amount * 2, index.get());
        assertTrue(Arrays.stream(result).average().orElseThrow() <= 5);
    }

    @Test
    void shouldCompareThrottlingPerformance() throws Exception {
        int amount = 15_000;

        var with = recordCallTimes(amount);

        ReflectionTestUtils.setField(profileApi, "throttlingEnabled", false);
        var without = recordCallTimes(amount);
        ReflectionTestUtils.setField(profileApi, "throttlingEnabled", true);

        var withAvg = with.stream().mapToLong(Long::valueOf).average().orElseThrow();
        System.out.println("Average call time with throttling enabled: " + withAvg);
        var withoutAvg = without.stream().mapToLong(Long::valueOf).average().orElseThrow();
        System.out.println("Average call time with throttling disabled: " + withoutAvg);

        assertTrue(withAvg > withoutAvg);
    }

    private List<Long> recordCallTimes(int callsAmount) throws Exception {
        var result = new LinkedList<Long>();
        for (int i = 0; i < callsAmount; i++) {
            if (i == 5000) {
                Thread.sleep(1000);
            }
            var curr = System.currentTimeMillis();
            mockMvc.perform(get(BASE_URL + "/greetings").header("token", "token11"));
            result.add(System.currentTimeMillis() - curr);
        }
        return result;
    }

    @Test
    void shouldAllowGuestRequest() throws Exception {
        waitTillTheStartOfTheSecond();
        mockMvc.perform(get(BASE_URL + "/greetings")).andExpect(status().isOk());
    }

    @Test
    void shouldAllowGuestRequestsWithinLimits() throws Exception {
        waitTillTheStartOfTheSecond();
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get(BASE_URL + "/greetings")).andExpect(status().isOk());
        }
    }

    @Test
    void shouldNotAllowGuestRequestsAboveLimits() throws Exception {
        waitTillTheStartOfTheSecond();
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get(BASE_URL + "/greetings")).andExpect(status().isOk());
        }
        mockMvc.perform(get(BASE_URL + "/greetings")).andExpect(status().is(HttpStatus.TOO_MANY_REQUESTS.value()));
    }

    @Test
    void shouldAllowUserWithinRpsLimits() throws Exception {
        waitTillTheStartOfTheSecond();
        // begin SLA evaluation for users
        mockMvc.perform(get(BASE_URL + "/greetings").header("token", "token11")).andExpect(status().isOk());
        mockMvc.perform(get(BASE_URL + "/greetings").header("token", "token2")).andExpect(status().isOk());
        Thread.sleep(2_000);

        mockMvc.perform(get(BASE_URL + "/greetings").header("token", "token11")).andExpect(status().isOk());
        mockMvc.perform(get(BASE_URL + "/greetings").header("token", "token12")).andExpect(status().is(HttpStatus.TOO_MANY_REQUESTS.value()));

        mockMvc.perform(get(BASE_URL + "/greetings").header("token", "token2")).andExpect(status().isOk());
        mockMvc.perform(get(BASE_URL + "/greetings").header("token", "token2")).andExpect(status().isOk());
        mockMvc.perform(get(BASE_URL + "/greetings").header("token", "token2")).andExpect(status().is(HttpStatus.TOO_MANY_REQUESTS.value()));
    }

    private void waitTillTheStartOfTheSecond() {
        try {
            Thread.sleep(1001 - LocalTime.now().getNano() / 1_000_000);
        } catch (InterruptedException ignored) {
        }
    }
}
