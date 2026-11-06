package com.dendi.peony.entity;

/**
 * Origin of a catalogue image.
 *
 * <p>{@code SEED} marks an image supplied by the curated dataset (#9); {@code PLANTNET} marks a
 * fallback copied from a PlantNet identify response (#45). Rows that predate attribution have a
 * null source.
 */
public enum PlantImageSource {
  SEED,
  PLANTNET
}
