package com.dendi.peony.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Curated catalogue image seeding configuration (issue #9 follow-up).
 *
 * <p>{@code peony.dataset.images.enabled} is {@code false} by default so a normal application start
 * never downloads images. The one-shot run sets it to {@code true}; see {@code
 * docs/runbooks/dataset-images.md}.
 *
 * @param enabled whether the catalogue image seeder runs at startup
 * @param longEdge long edge of the stored catalogue image, in pixels
 * @param requestIntervalMs pause between downloads, so Wikimedia does not rate-limit the run
 * @param file classpath-relative location of the committed catalogue image selection
 */
@ConfigurationProperties(prefix = "peony.dataset.images")
public record CatalogueImageProperties(
    boolean enabled,
    @DefaultValue("512") int longEdge,
    @DefaultValue("500") int requestIntervalMs,
    @DefaultValue("dataset/catalogue-images.json") String file) {}
