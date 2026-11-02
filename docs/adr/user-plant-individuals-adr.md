# ADR-006: User Plants as Individuals

**Status:** Accepted
**Date:** 2026-11-02
**Author:** Development Team
**Supersedes:** The plant-location half of decision 3 in
[ADR-003](./plant-schema-and-tiering-adr.md) (a plant assignable to multiple rooms);
ADR-003's plant-schema and tiering decisions remain in force

---

## Context

`user_plants` originally recorded one row per user per catalog plant, enforced by the
unique pair `(user_id, plant_id)`. The row held the user's nickname and watering state,
but its identity was implicit: `UserPlantResponse.id` exposed the catalog plant id, and
there was no user-plant id on the wire. A user could therefore not track two specimens
of the same species (for example a Monstera at home and another at the office).

Location was modelled separately. `plant_locations` associated a catalog `plants.id`
with a room, carried no user column, and could point at any catalog plant. The
association did not express which owned specimen sat in the room, and its scoping had
to be enforced by joins rather than by ownership.

Issue #37 makes each `user_plants` row the user's physical specimen. That changes the
schema, the API identity, and the watering schedule, so the judgement rules are recorded
here rather than left implicit in the migration and endpoints.

## Decision

1. **A user plant is an individual specimen.** Its identity is `user_plants.id`, not
   the catalog plant. The unique `(user_id, plant_id)` constraint is replaced by a
   non-unique index, so the same catalog plant may back several individuals for one
   user. Creating a catalog plant manually still creates one individual for the caller
   and returns that individual.
2. **An individual has at most one room.** `user_plants.room_id` references
   `rooms(id)` with `ON DELETE SET NULL`: removing a room clears the placement but keeps
   the individual. The separate `plant_locations` association is retired. Its rows are
   migrated to `room_id` only when the room's house owner already tracks that catalog
   plant (oldest assignment wins); assignments that cannot be mapped are dropped, and
   the table is then removed.
3. **User-plant identity is on the wire.** `UserPlantResponse.id` is the user-plant id
   and overrides the inherited `PlantResponse.id` (the catalog plant id); `plantId`
   carries the catalog plant id. Individual routes address rows by `userPlantId`, and
   every operation is scoped to the caller's own rows.
4. **Watering dates are nullable.** `last_watered_at` and `next_water_due_at` may be
   null, meaning the watering history is unknown ("I don't remember"). Null dates are
   omitted from JSON rather than serialized as null.
5. **The schedule stays tied to the catalog.** There is no per-individual frequency
   override. On any write that sets `last_watered_at`, `next_water_due_at` is
   `last_watered_at + plants.watering_frequency`; when either input is absent the next
   date is null, and clearing `last_watered_at` also clears `next_water_due_at`.

## Rationale

- **Individuals match the domain.** A specimen has its own nickname, room, and watering
  state; two specimens of one species are distinct things with distinct identities.
- **One room is the MVP location model.** The UI needs the house and room display names
  for "Home · Living room", not a general many-to-many assignment. A nullable
  `room_id` makes the placement an attribute of the individual instead of a separate
  relationship that has to be ownership-filtered on every read.
- **Retiring `plant_locations` closes an ownership gap.** The old table could be
  populated for any catalog plant because it had no user column, so ownership was only
  recoverable by joining through the room's house. Storing the room directly on the
  owned individual keeps the ownership check on one row.
- **Exposing the user-plant id removes an identity ambiguity.** Clients can address an
  individual directly for get/update/delete/water, and duplicates of one catalog plant
  remain distinguishable.
- **Catalog-owned frequency preserves one schedule invariant.** Keeping
  `watering_frequency` on the catalog plant avoids a second source of truth for #37;
  per-individual overrides are out of scope.

## Consequences

- The API is breaking and moves to version 0.3.0. Clients use `userPlantId` for
  individual routes and read the catalog id from `plantId`.
- The location endpoints that assigned a catalog plant to rooms, and the notes endpoint
  that wrote `user_plants.nickname`, are retired.
- Deleting an individual leaves the catalog plant, house, and room untouched; deleting
  a room only clears the individual's `room_id`.
- `plant_locations` no longer exists after the migration. Queries join `rooms` through
  the individual's `room_id`.
- #38 (scan backend) can add individuals through the same model without further schema
  changes to `user_plants`.

## References

- [ADR-003: Plant Schema Completion and Free/Paid Tiering](./plant-schema-and-tiering-adr.md)
- [API tier contracts](../api-tier-contracts.md)
- [Issue #37: User plants as individuals](https://github.com/Dendi-Soft/peony-application/issues/37)
