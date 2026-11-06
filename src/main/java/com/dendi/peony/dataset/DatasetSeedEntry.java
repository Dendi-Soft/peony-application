package com.dendi.peony.dataset;

import java.util.List;

/**
 * One curated plant entry from {@code dataset/plants-seed.json}.
 *
 * <p>The shape mirrors the version-controlled seed file: twelve required content fields plus the
 * curated {@code description}, {@code tags}, and toxicity source references. The toxicity sources
 * are curation metadata and are not written to the {@code plants} table.
 *
 * @param commonName common display name
 * @param scientificName accepted binomial, without authorship
 * @param family botanical family
 * @param description one curated sentence for the catalogue entry
 * @param tags searchable kebab-case tags
 * @param lightNeeds curated light guidance
 * @param waterFrequencyDays days between waterings, 1-90
 * @param humidity curated humidity guidance
 * @param tempRange curated temperature range
 * @param toxicToPets whether a cited source classifies the plant as toxic to pets
 * @param toxicToPetsSource reference for {@code toxicToPets}
 * @param toxicToHumans whether a cited source classifies the plant as toxic to humans
 * @param toxicToHumansSource reference for {@code toxicToHumans}
 * @param difficulty one of {@code Easy}, {@code Moderate}, {@code Hard}
 * @param nativeRegion approximate region of origin
 * @param matureSize mature size guidance for indoor culture
 */
public record DatasetSeedEntry(
    String commonName,
    String scientificName,
    String family,
    String description,
    List<String> tags,
    String lightNeeds,
    Integer waterFrequencyDays,
    String humidity,
    String tempRange,
    Boolean toxicToPets,
    String toxicToPetsSource,
    Boolean toxicToHumans,
    String toxicToHumansSource,
    String difficulty,
    String nativeRegion,
    String matureSize) {}
