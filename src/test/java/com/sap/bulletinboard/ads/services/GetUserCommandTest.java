package com.sap.bulletinboard.ads.services;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

import java.util.function.Supplier;

import org.junit.After;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.netflix.hystrix.Hystrix;
import com.netflix.hystrix.exception.HystrixBadRequestException;
import com.sap.bulletinboard.ads.services.UserServiceClient.User;

/**
 * This is a learning test for Hystrix and to test the GetUserCommand implementation.
 */
public class GetUserCommandTest {
    private static final User FALLBACK_USER = new User();
    private static final User USER = new User();

    @Test
    public void responseReturnedSynchronously() {
        TestableUserCommand command = new TestableUserCommand(this::dummyUser).respondWithOkUser();
        User user = command.execute();
        assertThat(user, is(USER));
    }

    @Test
    public void responseReturnedAsynchronously() throws Exception {
        TestableUserCommand command = new TestableUserCommand(this::dummyUser).respondWithOkUser();
        User user = command.queue().get();
        assertThat(user, is(USER));
    }

    @Test
    public void responseTimedOutFallback() {
        TestableUserCommand command = new TestableUserCommand(this::dummyUser).provokeTimeout();
        User user = command.execute();
        assertThat(user, is(FALLBACK_USER));
    }

    @Test
    public void responseErrorFallback() {
        TestableUserCommand command = new TestableUserCommand(this::dummyUser).respondWithError();
        User user = command.execute();
        assertThat(user, is(FALLBACK_USER));
    }

    @Test(expected = HystrixBadRequestException.class)
    public void responseHystrixBadRequest() {
        TestableUserCommand command = new TestableUserCommand(this::dummyUser).respondWithBadRequest();
        User user = null;
        try {
            user = command.execute();
        } finally {
            assertThat(user, is(nullValue())); // fallback is not be called in case of HystrixBadRequestException
        }
    }

    // useful for optional exercise step
    private User dummyUser() {
        return FALLBACK_USER;
    }

    @After
    public void reset() {
        Hystrix.reset(); // to clear out all HystrixPlugins, allowing you to set a new one
    }

    // This command implementation does not send network requests, but instead behaves as configured using the
    // responseWith methods.
    private static class TestableUserCommand extends GetUserCommand {
        private ResponseEntity<User> response;
        private boolean provokeTimeout;

        TestableUserCommand(Supplier<User> fallbackFunction) {
            super(null);
            setFallbackFunction(fallbackFunction);
        }

        TestableUserCommand respondWithOkUser() {
            this.response = ResponseEntity.ok(USER);
            return this;
        }

        TestableUserCommand respondWithError() {
            this.response = ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
            return this;
        }

        TestableUserCommand respondWithBadRequest() {
            this.response = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
            return this;
        }

        TestableUserCommand provokeTimeout() {
            this.provokeTimeout = true;
            return this;
        }

        @Override
        protected ResponseEntity<User> sendRequest() {
            if (provokeTimeout) {
                try {
                    Thread.sleep(getTimeoutInMs() + 100);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
            return response;
        }
    }
}