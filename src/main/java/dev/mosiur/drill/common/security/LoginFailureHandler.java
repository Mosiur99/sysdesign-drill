package dev.mosiur.drill.common.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

class LoginFailureHandler implements AuthenticationFailureHandler {

    static final String TRIES_LEFT = "loginTriesLeft";

    private final LoginThrottle throttle;

    LoginFailureHandler(LoginThrottle throttle) {
        this.throttle = throttle;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        int left = throttle.recordFailure(request.getRemoteAddr());
        request.getSession().setAttribute(TRIES_LEFT, left);
        response.sendRedirect(request.getContextPath() + (left == 0 ? "/login?locked" : "/login?error"));
    }
}
