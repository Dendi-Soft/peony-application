package com.dendi.peony.dataset;

/**
 * One seeded species and its selected catalogue image (issue #9 follow-up).
 *
 * @param scientificName accepted binomial, matching {@code dataset/plants-seed.json}
 * @param commonName common display name
 * @param image the single catalogue image at position 0
 */
public record CatalogueImageEntry(String scientificName, String commonName, CatalogueImage image) {}
