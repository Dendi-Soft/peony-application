package com.dendi.peony.repository;

import com.dendi.peony.entity.House;
import com.dendi.peony.entity.Plant;
import com.dendi.peony.entity.Room;
import com.dendi.peony.entity.UserPlant;

/**
 * A caller's individual plant with its catalog plant and optional location.
 *
 * <p>Loaded by one joined query so listing or reading an individual does not issue a query per row
 * or per location. {@code room} and {@code house} are null when the individual has no location, or
 * when the location's house does not belong to the caller.
 *
 * @param userPlant the caller's individual record
 * @param plant the catalog plant
 * @param room the individual's room, if it has one and the caller owns its house
 * @param house the room's house, if present and caller-owned
 */
public record UserPlantWithPlantAndLocation(
    UserPlant userPlant, Plant plant, Room room, House house) {}
