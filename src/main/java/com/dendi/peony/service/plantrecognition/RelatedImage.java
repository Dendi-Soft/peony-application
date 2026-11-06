package com.dendi.peony.service.plantrecognition;

/**
 * One acceptable related image returned by the PlantNet identify call.
 *
 * <p>The identify response is not part of the public recognition wire contract; this internal
 * record carries the reference that the species-image staging service stores until the user keeps
 * the plant.
 *
 * @param scientificName normalized binomial used as the staging key
 * @param organ organ shown in the image, as returned by PlantNet; null when absent
 * @param author image author, when returned
 * @param license accepted licence mapped to its canonical form
 * @param citation citation text, when returned
 * @param mediumUrl absolute URL of the medium-size image ({@code url.m})
 */
public record RelatedImage(
    String scientificName,
    String organ,
    String author,
    License license,
    String citation,
    String mediumUrl) {}
