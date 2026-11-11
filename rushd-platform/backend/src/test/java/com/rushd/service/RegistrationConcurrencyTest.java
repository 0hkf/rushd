package com.rushd.service;

import com.rushd.dto.RegisterRequest;
import com.rushd.entity.Role;
import com.rushd.exception.EmailAlreadyExistsException;
import com.rushd.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class RegistrationConcurrencyTest {
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired AuthenticationManager authentication;

    @Test void twoRegistrationsThatBothPassedPrecheckYieldOneSuccessAndOneEmailConflict() throws Exception {
        String email = "race-" + UUID.randomUUID() + "@example.invalid";
        CyclicBarrier bothPassedPrecheck = new CyclicBarrier(2);
        UserRepository racingUsers = (UserRepository) Proxy.newProxyInstance(UserRepository.class.getClassLoader(),
                new Class<?>[] {UserRepository.class}, (proxy, method, args) -> {
                    try {
                        Object result = method.invoke(users, args);
                        if (method.getName().equals("existsByEmail") && email.equals(args[0])) {
                            assertEquals(false, result);
                            bothPassedPrecheck.await(10, TimeUnit.SECONDS);
                        }
                        return result;
                    } catch (InvocationTargetException failure) { throw failure.getCause(); }
                });
        AuthService service = new AuthService(racingUsers, passwords, authentication);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> register = () -> {
                RegisterRequest request = new RegisterRequest();
                request.setName("Concurrent user"); request.setEmail(email);
                request.setPassword("Password123"); request.setRole(Role.BUYER);
                try { service.register(request); return 201; }
                catch (EmailAlreadyExistsException conflict) { return 409; }
            };
            var results = pool.invokeAll(List.of(register, register), 20, TimeUnit.SECONDS);
            assertEquals(List.of(201, 409), results.stream().map(result -> {
                try { return result.get(); } catch (Exception failure) { throw new AssertionError(failure); }
            }).sorted().toList());
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
            users.findByEmail(email).ifPresent(users::delete);
        }
    }
}
