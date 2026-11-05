# Runbook: Account deletion (manual, email-only)

Peony has no self-service account deletion. A user requests deletion by email; an operator
performs it against the production database and the image volume. This runbook covers the complete
removal, including the user's private plant photos (issue #44, ADR-008).

## Important: database cascades do not delete files

`user_plant_images.user_plant_id` references `user_plants(id) ON DELETE CASCADE`, so deleting an
individual removes its image **rows**. Account deletion is not a single statement, however:
`user_plants.user_id` (`fk_user_plants_users`, changeset 001) and `user_upload_counters.user_id`
(`fk_user_upload_counters_users`, changeset 003) have **no** `ON DELETE CASCADE`, so the account
row cannot be deleted until those rows are gone. `houses.user_id` (changeset 004) and
`refresh_tokens.user_id` (changeset 005) do cascade; rooms cascade from their house.

The JPEG bytes under `peony.user-images.dir` are not database-managed and must be removed
explicitly. Collect the file names **before** deleting any rows.

## Prerequisites

- Access to the production database and the backend host.
- The requester's verified username or email.
- The image directory. In Docker Compose it is the named volume `user_images` mounted at
  `/app/user-images`; outside Docker it is `PEONY_USER_IMAGES_DIR` (default `user-images`).

## Steps

1. Verify the request comes from the account owner (reply to the address on file).

2. Resolve the account id.

   ```sql
   SELECT id, username, email FROM users WHERE email = :email OR username = :username;
   ```

3. **Before deleting anything**, collect the image file names for every individual the user owns.

   ```sql
   SELECT upi.filename, upi.thumb_filename
   FROM user_plant_images upi
   JOIN user_plants up ON up.id = upi.user_plant_id
   WHERE up.user_id = :user_id;
   ```

   Save the result. Each row names two files.

4. Delete the collected files from the mounted directory, one pair at a time. Refuse any path that
   is not a plain file name; the application only ever writes `<image-id>.jpg` and
   `<image-id>-thumb.jpg`.

   ```bash
   docker compose exec peony sh -c 'rm -f /app/user-images/<file1> /app/user-images/<file2>'
   ```

   If the volume is mounted elsewhere, substitute the path. Deleting a missing file is harmless.

5. Delete the account rows in foreign-key order. Deleting the individuals cascades their
   `user_plant_images` rows, and deleting the account then cascades houses, rooms, and refresh
   tokens.

   ```sql
   DELETE FROM user_plants WHERE user_id = :user_id;          -- cascades user_plant_images
   DELETE FROM user_upload_counters WHERE user_id = :user_id; -- no cascade; delete explicitly
   DELETE FROM users WHERE id = :user_id;                     -- cascades houses/rooms, refresh_tokens
   ```

6. Verify both halves are gone.

   ```sql
   SELECT COUNT(*) FROM user_plants WHERE user_id = :user_id;          -- must be 0
   SELECT COUNT(*) FROM user_upload_counters WHERE user_id = :user_id; -- must be 0
   SELECT COUNT(*) FROM user_plant_images upi
   JOIN user_plants up ON up.id = upi.user_plant_id
   WHERE up.user_id = :user_id;                                        -- must be 0

   SELECT COUNT(*) FROM users WHERE id = :user_id;                     -- must be 0
   ```

   ```bash
   docker compose exec peony sh -c 'ls /app/user-images | grep -E "<file1>|<file2>"'; echo $?
   # 1 means no matching files remain
   ```

7. Record the deletion in the support ticket: account id, date, operator, and the count of files
   removed. Do not retain copies of the photos.

## What the orphan sweeper does not do

The startup sweeper (`docs/runbooks/user-images.md`) only removes files that have no database row
and are older than one hour. It cannot find files whose user rows were deleted for a different
account, and it is not a substitute for step 4: a file deleted from the database but still on disk
is *unreferenced*, so the next backend restart would eventually remove it, but that is best-effort
and delayed. Always collect and delete the files in the same operation.
