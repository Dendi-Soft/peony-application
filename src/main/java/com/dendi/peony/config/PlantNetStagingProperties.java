package com.dendi.peony.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * PlantNet species-image staging configuration (issue #45, ADR-008).
 *
 * <p>{@code peony.plantnet-staging.ttl} bounds how long a recognized-but-not-yet-kept image
 * candidate stays available to fill a catalogue entry. The default in {@code application.yml} is
 * {@code 7d}; production may override it with {@code PEONY_PLANTNET_STAGING_TTL}.
 *
 * @param ttl how long a staged candidate remains usable
 */
@ConfigurationProperties(prefix = "peony.plantnet-staging")
public record PlantNetStagingProperties(Duration ttl) {}
