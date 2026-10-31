package com.dendi.peony.repository;

import com.dendi.peony.entity.Plant;
import com.dendi.peony.entity.UserPlant;

/**
 * A caller's user plant paired with its catalog plant.
 *
 * <p>Loaded by one joined query so listing a caller's plants does not issue a query per row.
 *
 * @param userPlant the caller's per-user record
 * @param plant the catalog plant
 */
public record UserPlantWithPlant(UserPlant userPlant, Plant plant) {}
