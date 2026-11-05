package com.dendi.peony.repository;

import com.dendi.peony.entity.House;
import com.dendi.peony.entity.Plant;
import com.dendi.peony.entity.Room;
import com.dendi.peony.entity.UserPlant;
import com.dendi.peony.entity.UserPlantImage;

/**
 * A caller's individual plant with its catalog plant, optional location, and optional photo.
 *
 * <p>Loaded by one joined query so listing or reading an individual does not issue a query per row
 * or per location. {@code room} and {@code house} are null when the individual has no location, or
 * when the location's house does not belong to the caller. {@code image} is null when the
 * individual has no personal photo.
 *
 * @param userPlant the caller's individual record
 * @param plant the catalog plant
 * @param room the individual's room, if it has one and the caller owns its house
 * @param house the room's house, if present and caller-owned
 * @param image the individual's personal photo metadata, if it has one
 */
public record UserPlantProjection(
    UserPlant userPlant, Plant plant, Room room, House house, UserPlantImage image) {}
