package dev.mosiur.drill.common;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    @Bean
    Clock clock(@Value("${drill.zone}") ZoneId zone) {
        return Clock.system(zone);
    }
}
