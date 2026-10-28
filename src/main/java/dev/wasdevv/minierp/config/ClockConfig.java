package dev.wasdevv.minierp.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /** The shop's zone: dates are stored as instants and shown, and grouped into days, in this zone. */
    @Bean
    public ZoneId shopZone(@Value("${minierp.time-zone:America/Sao_Paulo}") String zone) {
        return ZoneId.of(zone);
    }
}
