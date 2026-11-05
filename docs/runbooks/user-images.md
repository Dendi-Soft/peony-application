# Runbook: Personal user images

Private per-individual plant photos (issue #44, ADR-008).

## Layout and configuration

- Display files and thumbnails live in `peony.user-images.dir`, configured by
  `PEONY_USER_IMAGES_DIR` (default `user-images`). In Docker Compose the directory is the named
  volume `user_images` mounted at `/app/user-images`.
- One image slot per individual (`user_plants` row), recorded in `user_plant_images`. The row holds
  the immutable `image_id` used in client URLs, both file names, the display dimensions, the
  display byte size, and the creation timestamp.
- File names are opaque and derived from the image id:
  - `<image-id>.jpg` — display image, long edge at most 1280 px
  - `<image-id>-thumb.jpg` — server-generated thumbnail, long edge at most 256 px
- Both files are JPEGs re-encoded by the server. Uploads pass through EXIF orientation
  normalisation and are re-encoded without EXIF or GPS metadata.
- The directory is flat. Do not create subdirectories; the sweeper only examines top-level regular
  files.

## Access and caching

- The bytes are served only by the owner-checked endpoints
  `GET /api/v1/me/plants/{userPlantId}/image` and `/image/thumb`.
- Responses carry `Cache-Control: private`. Clients version the URL with the individual's
  `imageId` as a query parameter, so a cached response is per-image and a future replacement
  changes the URL. The server accepts and ignores the parameter in v1; it is not validated
  against the stored image.
- There are no public URLs; never expose the directory through a static file server.

## Backup and restore

Back up the database and the volume together. A row whose files are missing breaks both image
endpoints: the orphan sweeper only deletes, it never recreates bytes, and a re-upload is rejected
with 409 while the row still exists. Recover by restoring the volume, or by deleting the
individual and adding it again (there is no replace endpoint in v1).

Back up the volume (Compose prefixes the project name, e.g. `peony_user_images`; check
`docker volume ls`):

```bash
docker run --rm \
  -v peony_user_images:/data \
  -v "$PWD":/backup \
  alpine sh -c 'tar czf /backup/user-images-backup.tar.gz -C /data .'
```

Restore the database **first**, then the volume, so the rows and file names line up:

```bash
docker run --rm \
  -v peony_user_images:/data \
  -v "$PWD":/backup \
  alpine sh -c 'cd /data && tar xzf /backup/user-images-backup.tar.gz'
```

After a restore, spot-check a few individuals: `hasImage` is true in `GET /api/v1/me/plants`, both
sizes return 200, and the file names in `user_plant_images` exist in the volume.

## Orphan sweeper

There is no scheduler in the backend. `UserPlantImageOrphanSweeper` runs once on **every backend
startup** (`ApplicationRunner`) and:

1. lists the regular files in `peony.user-images.dir`;
2. keeps every file named by a `filename` or `thumb_filename` row;
3. deletes unreferenced files whose modification time is older than **one hour**.

The one-hour safety age protects an upload that has written files but not yet committed its row.
The sweep logs one summary line:

```
User image sweep: checked <N> file(s), deleted <M> orphan(s)
```

Manual trigger: restart the backend (`docker compose restart peony`) and read the log line. A
missing directory is a no-op, not an error. File-delete failures are logged and do not fail
startup.

When to expect orphans: a crash between writing the files and committing the row, or a lost
unique-race insert. Restoring an old database snapshot onto a newer volume makes many files look
orphaned; check the backup pairing above before letting a sweep run.

## Deleting an individual or an account

- Deleting an individual removes its row and both files in the same request; files are deleted
  after the database commit, and a failed file delete is logged rather than failing the request.
- Account deletion is manual and must remove the files explicitly; see
  `docs/runbooks/account-deletion.md`.
