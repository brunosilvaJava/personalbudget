package com.bts.personalbudget.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    @Bean
    public Clock clock(@Value("${app.time-zone:America/Sao_Paulo}") final String timeZone) {
        return Clock.system(ZoneId.of(timeZone));
    }
}