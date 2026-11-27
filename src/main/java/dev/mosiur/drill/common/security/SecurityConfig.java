package dev.mosiur.drill.common.security;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    private static final String CONTENT_SECURITY_POLICY = String.join("; ",
            "default-src 'self'",
            "script-src 'self'",
            "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com",
            "font-src https://fonts.gstatic.com",
            "img-src 'self' data:",
            "form-action 'self'",
            "frame-ancestors 'none'",
            "base-uri 'self'");

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, LoginThrottle throttle, AuthProperties auth,
                                            UserDetailsService users) {
        http
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/login", "/error", "/css/**", "/js/**", "/webjars/**",
                                "/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .formLogin(login -> login
                        .loginPage("/login")
                        .successHandler(new LoginSuccessHandler(throttle))
                        .failureHandler(new LoginFailureHandler(throttle)))
                .rememberMe(rememberMe -> rememberMe
                        .key(auth.passwordHash())
                        .userDetailsService(users)
                        .tokenValiditySeconds((int) Duration.ofDays(30).toSeconds()))
                .logout(logout -> logout.logoutSuccessUrl("/login?logout"))
                .addFilterBefore(new LoginThrottleFilter(throttle), UsernamePasswordAuthenticationFilter.class)
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.SAME_ORIGIN)));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(AuthProperties auth) {
        return new InMemoryUserDetailsManager(User.withUsername(auth.username())
                .password(auth.passwordHash())
                .roles("USER")
                .build());
    }
}
