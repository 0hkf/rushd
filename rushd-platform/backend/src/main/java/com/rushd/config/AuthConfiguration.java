package com.rushd.config;

import com.rushd.security.AuthRateLimitFilter;
import com.rushd.security.JwtAuthenticationFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties({JwtProperties.class, AuthProperties.class, AuthRateLimitProperties.class})
public class AuthConfiguration {
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(JwtAuthenticationFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<AuthRateLimitFilter> rateFilterRegistration(AuthRateLimitFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public Clock authClock() {
        return Clock.systemUTC();
    }
}
