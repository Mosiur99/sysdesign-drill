package dev.mosiur.drill.common.security;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;

class LoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private final LoginThrottle throttle;

    LoginSuccessHandler(LoginThrottle throttle) {
        this.throttle = throttle;
        setDefaultTargetUrl("/");
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        throttle.reset(request.getRemoteAddr());
        super.onAuthenticationSuccess(request, response, authentication);
    }
}
