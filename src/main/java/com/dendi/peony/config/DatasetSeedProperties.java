package com.dendi.peony.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Curated-dataset seeding configuration (issue #9).
 *
 * <p>{@code peony.dataset.seed.enabled} is {@code false} by default so a normal application start
 * never writes to the catalogue. The one-shot seeder run sets it to {@code true}; see {@code
 * docs/runbooks/dataset-seed.md}.
 *
 * @param enabled whether the curated-dataset seeder runs at startup
 */
@ConfigurationProperties(prefix = "peony.dataset.seed")
public record DatasetSeedProperties(boolean enabled) {}
