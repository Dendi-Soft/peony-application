# Recognition-training image cache

The recognition model needs clean, correctly licensed reference photos for every species
in the curated seed dataset. This document describes the committed manifest of Wikimedia
Commons images and the script that builds, downloads, and validates it.

The cache is **for recognition training only**. It is not a source of catalogue images
and never feeds `plant_images`; see the [dataset README](README.md#catalogue-images-are-out-of-scope)
and [ADR-008](../adr/plant-images-adr.md) for that boundary.

- Manifest (committed): [`dataset/image-cache-manifest.json`](../../dataset/image-cache-manifest.json)
- Script: [`scripts/fetch-recognition-image-cache.py`](../../scripts/fetch-recognition-image-cache.py)
- Images (not committed): `dataset/image-cache/` (gitignored)
- Species list: [`src/main/resources/dataset/plants-seed.json`](../../src/main/resources/dataset/plants-seed.json)

## Source and licence policy

All images come from **Wikimedia Commons**, queried through the public MediaWiki API
(`https://commons.wikimedia.org/w/api.php`). Only licences that permit commercial reuse
and adaptation are accepted:

| Canonical manifest value | Accepted Commons terms |
|--------------------------|------------------------|
| `cc0` | CC0, CC Zero, Creative Commons Zero |
| `public-domain` | Public domain, PD |
| `cc-by` | CC BY (any version) |
| `cc-by-sa` | CC BY-SA (any version) |

NonCommercial (`NC`), NoDerivatives (`ND`), GFDL-only, unknown, and missing licences are
rejected. The check treats the four canonical values above as an exact whitelist, so a
manifest cannot contain an ambiguous licence string.

Each image records its `author` (plain text extracted from Commons `Artist`, falling back
to `Credit`) and a `licenseUrl`, so attribution can be produced when an image is used.
The `descriptionUrl` points at the Commons file page for full provenance.

## Image selection rules

The builder aims for **10-20 images per species**. It:

1. searches Commons namespace 6 (files) by scientific name, then by common name when the
   scientific-name search yields fewer than 20 acceptable results;
2. keeps only `image/jpeg`, `image/png`, and `image/webp` files of at least 320 px on the
   short edge and 480 px on the long edge;
3. filters out herbarium sheets, type specimens, maps, distribution/range maps, diagrams,
   drawings, illustrations, charts, labels, and other non-photographic material based on
   the file title and Commons categories;
4. rejects any file whose title/categories name a **different binomial**, then accepts a
   file only when the target's full binomial appears or when the species **epithet**
   matches. A synonym that keeps the epithet (for example `Calathea orbifolia` for
   *Goeppertia orbifolia*) is accepted, while a congener such as `Calathea fasciata` is
   rejected even if a generic common-name search surfaced it;
5. ranks candidates by relevance (full binomial, epithet, common name, iNaturalist
   provenance, leaf/habit cues) and keeps the best 20.

The builder is deliberately polite: it sleeps 0.3-0.5 s between API calls, uses a
descriptive `User-Agent`, and retries `429`/`5xx` responses up to three times with
exponential backoff.

## Manifest format

```json
{
  "source": "Wikimedia Commons",
  "generatedAt": "2026-11-06",
  "licenses": ["cc0", "public-domain", "cc-by", "cc-by-sa"],
  "species": [
    {
      "scientificName": "Dracaena trifasciata",
      "commonName": "Snake Plant",
      "images": [
        {
          "title": "File:Dracaena Trifasciata Plant.jpg",
          "url": "https://upload.wikimedia.org/wikipedia/commons/3/3d/Dracaena_Trifasciata_Plant.jpg",
          "descriptionUrl": "https://commons.wikimedia.org/wiki/File:Dracaena_Trifasciata_Plant.jpg",
          "author": "Fanti Salms",
          "license": "cc-by-sa",
          "licenseUrl": "https://creativecommons.org/licenses/by-sa/4.0"
        }
      ]
    }
  ]
}
```

There is exactly one entry per seeded species. The manifest is ordered like the seed
file, and `generatedAt` records the build date (the build is deterministic for a given
Commons state apart from newly indexed files).

## Commands

All commands run from the repository root and use Python 3 with the standard library
only - no third-party packages.

### Build the manifest

```sh
python3 scripts/fetch-recognition-image-cache.py --build-manifest
```

Queries Commons for all 50 seeded species and writes
`dataset/image-cache-manifest.json` atomically. The run takes a few minutes because of
the request pacing. A species that fails to search is reported as a warning and simply
gets fewer images; the builder never crashes on a thin or empty result.

To refresh a single species without touching the others:

```sh
python3 scripts/fetch-recognition-image-cache.py --build-manifest --only "Dracaena trifasciata"
```

### Download the images

```sh
python3 scripts/fetch-recognition-image-cache.py --download
```

Writes `dataset/image-cache/<scientific-name-kebab>/<NN>-<file-slug>.<ext>`, skipping
files that already exist. Non-200 responses and non-image bodies are reported as errors
and make the command exit non-zero. `--only` restricts the download to one species.

### Download safety

The manifest is data, not a command, so the downloader refuses to turn an edited URL into an
arbitrary server-side fetch:

- the URL must use `https` and the standard port (443);
- the host must be in the allowlist `upload.wikimedia.org` or `commons.wikimedia.org`;
- redirects are followed by the HTTP client, so the **final** URL is checked against the
  same allowlist before the body is accepted;
- the response `Content-Type` must be `image/jpeg`, `image/png`, or `image/webp`, the body
  must start with the matching image signature, and the body must be at most 25 MiB.

`--check` applies the scheme and host allowlist to every manifest image URL, so a manifest
that points at any other host fails validation offline.

### Validate the committed manifest

```sh
python3 scripts/fetch-recognition-image-cache.py --check
```

Runs offline and exits non-zero if the manifest is missing, does not contain exactly one
entry per seeded scientific name, has an image without a required field, has a
non-`https://` URL or a URL on a host outside the download allowlist, uses a licence outside
the whitelist, or has a species with zero images. It prints per-species counts and warns
(without failing) when a species has fewer than 10 images, because those shortfalls are
documented below.

## Refresh procedure

1. Run `--build-manifest` (or `--build-manifest --only "<species>"` for one species).
2. Review the diff: licences, titles, and counts should remain plausible; reject any
   file that is obviously not a plant photo by tightening the title/category filters.
3. Run `--check` and update the shortfall table below if the counts changed.
4. Delete and re-run `--download` if the local cache must match the manifest exactly.
5. Commit the manifest and this document; never commit `dataset/image-cache/`.

## Known gaps and shortfalls

The check reports a warning (not a failure) when a species has fewer than 10 acceptable
images. At the 2026-11-06 build the manifest contains 991 images across the 50 seeded
species. **No species is below the 10-image minimum**, but one species is short of the
20-image target:

| Species | Images | Why |
|---------|--------|-----|
| *Goeppertia orbifolia* (Calathea) | 11 | Commons has few files that name the species epithet `orbifolia`. Files matching the generic common name "Calathea" but naming another binomial (for example `Calathea fasciata`) were rejected rather than used as mislabelled training data. |

No image was padded with a non-whitelisted licence or a wrong species to reach the target.
All other 49 species have 20 images each (median 20, minimum 11, maximum 20).

The 2026-11-06 refresh tightened the relevance gate and rebuilt the three affected
species. It removed four congener photos that the earlier epithet-only gate let through:
`Calathea fasciata` and `Calathea orbiculata` from *Goeppertia orbifolia*, `Dypsis
decaryi` from *Dypsis lutescens*, and `Maranta depressa` from *Maranta leuconeura*. The
current manifest contains none of those titles, and each rebuilt species still has at
least 11 images.

The remaining limitations are about image quality rather than coverage:

- **Identification is text-based.** The builder trusts Commons titles and categories; it
  does not botanically verify each photo. It does at least reject any file whose title or
  categories name a different binomial, while accepting the species epithet or full
  binomial. Historical synonyms are accepted when they keep the same epithet (for example
  `Sansevieria trifasciata` for *Dracaena trifasciata*).
- **Rebuild drift.** A rebuild reflects the Commons index at the time it runs. New
  uploads, deletions, and re-indexing change counts, ordering, and `generatedAt`, so two
  builds are not byte-identical. The committed manifest is the reviewed snapshot.
- **Source concentration.** Several species draw on a small number of prolific Commons
  photographers, so some images per species come from the same session and are visually
  similar. This is acceptable for training but does not add much variety.
- **No habitat/geography targeting.** Images are selected for the plant, not for
  geographic or habitat diversity.

Licence mix in the committed manifest (991 images):

| Licence | Images |
|---------|--------|
| CC BY-SA | 684 |
| CC BY | 205 |
| CC0 | 74 |
| Public domain | 28 |

No NonCommercial, NoDerivatives, GFDL-only, or unknown-licence image is included. Five
images expose neither an `Artist` nor a `Credit` in Commons metadata; their `author` is
recorded as `Unknown` rather than invented.

## Legal note

The manifest is metadata only. Images remain under their respective Creative Commons or
public-domain terms and are not redistributed in this repository. Any downstream use of
the downloaded cache must preserve the recorded author and licence attribution.
