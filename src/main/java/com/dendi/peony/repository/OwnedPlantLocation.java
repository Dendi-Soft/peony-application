package com.dendi.peony.repository;

import com.dendi.peony.entity.House;
import com.dendi.peony.entity.PlantLocation;
import com.dendi.peony.entity.Room;

/**
 * A plant assignment together with the room and house that make it caller-owned.
 *
 * <p>Loaded by one joined query so the my-plants listing can resolve locations without separate
 * room and house lookups.
 *
 * @param location the plant-to-room assignment
 * @param room the assigned room
 * @param house the room's owning house, which belongs to the caller
 */
public record OwnedPlantLocation(PlantLocation location, Room room, House house) {}
