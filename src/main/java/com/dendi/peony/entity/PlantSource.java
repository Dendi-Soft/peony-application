package com.dendi.peony.entity;

/**
 * Creation origin of a catalog plant.
 *
 * <p>{@code PLANTNET_SCAN} marks a row first created from a recognition hit, {@code MANUAL} a row
 * first created by the since-removed manual registration or by the pre-source backfill and kept as
 * a legitimate legacy origin, and {@code SEED} a row first created by the curated dataset (#9).
 * Later activity (curated care enrichment or a legacy manual re-registration) never changes the
 * origin.
 */
public enum PlantSource {
  PLANTNET_SCAN,
  MANUAL,
  SEED
}
