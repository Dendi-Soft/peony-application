package com.dendi.peony.dataset;

/**
 * One catalogue image chosen for a seeded species (issue #9 follow-up).
 *
 * <p>The shape mirrors one entry of {@code dataset/catalogue-images.json}: the species identity
 * plus the single Wikimedia Commons image and its attribution.
 *
 * @param title Commons file title, including the {@code File:} prefix
 * @param url direct https URL on {@code upload.wikimedia.org}
 * @param descriptionUrl Commons file page with full provenance
 * @param author image author credit
 * @param license canonical licence family, one of {@link CatalogueImageValidator#ALLOWED_LICENSES}
 * @param licenseUrl canonical licence URL
 */
public record CatalogueImage(
    String title,
    String url,
    String descriptionUrl,
    String author,
    String license,
    String licenseUrl) {}
