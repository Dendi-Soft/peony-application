#!/usr/bin/env python3
"""Fetch and validate the recognition-training image cache.

The catalogue of seeds lives in ``src/main/resources/dataset/plants-seed.json``.
This script keeps a committed manifest of suitably licensed Wikimedia Commons
images for every seeded species.  The images themselves are *not* committed;
``--download`` populates the gitignored ``dataset/image-cache/`` directory.

Modes
-----
``--build-manifest``
    Query the Wikimedia Commons API for every seeded species and write
    ``dataset/image-cache-manifest.json``.
``--download``
    Download every manifest image into
    ``dataset/image-cache/<scientific-name-kebab>/``.
``--check``
    Validate the committed manifest offline (default if no mode is given).

Only CC0, public domain, CC BY and CC BY-SA images are accepted; NonCommercial,
NoDerivatives, unknown and missing licences are rejected.
"""

from __future__ import annotations

import argparse
import datetime
import html
import json
import os
import random
import re
import sys
import time
import unicodedata
import urllib.error
import urllib.parse
import urllib.request

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SEED_PATH = os.path.join(REPO_ROOT, "src", "main", "resources", "dataset", "plants-seed.json")
MANIFEST_PATH = os.path.join(REPO_ROOT, "dataset", "image-cache-manifest.json")
CACHE_DIR = os.path.join(REPO_ROOT, "dataset", "image-cache")

COMMONS_API = "https://commons.wikimedia.org/w/api.php"
USER_AGENT = (
    "PeonyRecognitionImageCache/1.0 "
    "(https://github.com/Dendi-Soft/peony; educational plant-recognition training)"
)

LICENSE_WHITELIST = ("cc0", "public-domain", "cc-by", "cc-by-sa")
LICENSE_URLS = {
    "cc0": "https://creativecommons.org/publicdomain/zero/1.0/",
    "public-domain": "https://creativecommons.org/publicdomain/mark/1.0/",
    "cc-by": "https://creativecommons.org/licenses/by/4.0/",
    "cc-by-sa": "https://creativecommons.org/licenses/by-sa/4.0/",
}

# Terms that indicate a file is not a useful whole-plant/leaf photograph.
JUNK_TERMS = (
    "herbarium",
    "specimen",
    "lectotype",
    "holotype",
    "isotype",
    "syntype",
    "paratype",
    "type specimen",
    "range map",
    "distribution map",
    "distribution",
    "map of",
    "maps of",
    "diagram",
    "drawing",
    "illustration",
    "lithograph",
    "engraving",
    "woodcut",
    "botanical plate",
    "biodiversity heritage library",
    "label",
    "chart",
    "graph",
    "microscope",
    "pollen",
    "karyotype",
    "chromosome",
    "icon of",
    "logo",
    "stamp",
    "coin",
)

ALLOWED_MIME = ("image/jpeg", "image/png", "image/webp")

# ``--download`` may only fetch from Wikimedia's own media hosts. A manifest URL is
# data, not a command, so an edited manifest must not turn this script into an SSRF
# primitive.
ALLOWED_DOWNLOAD_HOSTS = frozenset({"upload.wikimedia.org", "commons.wikimedia.org"})
ALLOWED_IMAGE_TYPES = frozenset(ALLOWED_MIME)
MAX_DOWNLOAD_BYTES = 25 * 1024 * 1024

# File titles mix taxon names with English phrases ("Areca palm", "Peace lily").
# A capitalised-lowercase pair is only treated as a binomial when neither word is
# one of these common non-taxon words, so ordinary prose cannot trigger a false
# "different species" rejection.
NON_TAXON_WORDS = frozenset(
    """
    aerial african and athens baby bamboo boston botanic botanical buds cactus center
    chinese christmas closed common corner costilla creative damaged de del delicate
    der des di die do du during dying ear edema elephant en english false female fern
    fior fleur flor flora flower flowers fruto garden gardens grass green guttation
    hardware healthy hind house houseplant indoor inflorescence island jardin large
    leaf leaves lemon les lily lime little los lucky macro macroscopic male mature
    money moss moth native new old orchid outdoor palm peace photo plant plants pothos
    potted prayer public rabo red residence roots rose royal running sago schaugarten
    serres silver small snake spineless succulent su survey sweet the tree und united
    variegated vine violet wild world y young yucca zoo cv for from spp ssp subsp syn
    var with
    """.split()
)

# A binomial in a file title/category: capitalised genus followed by a lower-case
# epithet of at least three letters. Requiring three letters skips abbreviations
# such as "sp.", "spp.", and "var.".
BINOMIAL_PATTERN = re.compile(r"\b([A-Z][a-z]{2,})\s+([a-z]{3,}(?:-[a-z]+)*)\b")


class ManifestError(Exception):
    """Raised when the committed manifest is structurally invalid."""


def load_seed():
    """Return the seeded species as a list of ``(scientificName, commonName)``."""
    with open(SEED_PATH, "r", encoding="utf-8") as handle:
        data = json.load(handle)
    return [(p["scientificName"].strip(), p["commonName"].strip()) for p in data["plants"]]


def classify_license(short_name, machine_name):
    """Map Wikimedia licence metadata to one of ``LICENSE_WHITELIST`` or None."""
    text = " ".join(part for part in (short_name, machine_name) if part).strip().lower()
    if not text:
        return None
    # Reject NonCommercial / NoDerivatives before matching the permissive families.
    if re.search(r"(^|[^a-z])nc([^a-z]|$)", text) or "noncommercial" in text or "non-commercial" in text:
        return None
    if re.search(r"(^|[^a-z])nd([^a-z]|$)", text) or "noderiv" in text or "no-deriv" in text:
        return None
    if "cc0" in text or "cc-zero" in text or "cc zero" in text:
        return "cc0"
    if "public domain" in text or "publicdomain" in text or re.search(r"(^|[^a-z])pd([^a-z]|$)", text):
        return "public-domain"
    if "by-sa" in text or "by sa" in text or "attribution-sharealike" in text or "attribution share alike" in text:
        return "cc-by-sa"
    if re.search(r"(^|[^a-z])by([^a-z]|$)", text) or "attribution" in text:
        return "cc-by"
    return None


def strip_html(value):
    """Turn an extmetadata HTML fragment into plain text."""
    if not value:
        return ""
    text = re.sub(r"<[^>]+>", " ", value)
    text = html.unescape(text)
    return re.sub(r"\s+", " ", text).strip()


def kebab(value):
    """Lower-case ASCII slug (used for cache directory names)."""
    ascii_value = unicodedata.normalize("NFKD", value).encode("ascii", "ignore").decode("ascii")
    slug = re.sub(r"[^a-zA-Z0-9]+", "-", ascii_value).strip("-").lower()
    return slug or "species"


# ---------------------------------------------------------------------------
# Wikimedia Commons access
# ---------------------------------------------------------------------------


def commons_get(params, timeout=30):
    """GET the Commons API as JSON, retrying 429/5xx and transient network errors."""
    url = COMMONS_API + "?" + urllib.parse.urlencode(params)
    last_error = None
    for attempt in range(4):
        # Politeness: keep well below the API's request-rate guidance.
        time.sleep(random.uniform(0.3, 0.5))
        request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
        try:
            with urllib.request.urlopen(request, timeout=timeout) as response:
                return json.load(response)
        except urllib.error.HTTPError as exc:
            last_error = exc
            if (exc.code == 429 or 500 <= exc.code < 600) and attempt < 3:
                backoff = 2 ** attempt
                print(f"  retry {attempt + 1}/3 after HTTP {exc.code} (sleep {backoff}s)", file=sys.stderr)
                time.sleep(backoff)
                continue
            raise
        except (urllib.error.URLError, TimeoutError) as exc:
            last_error = exc
            if attempt < 3:
                backoff = 2 ** attempt
                print(f"  retry {attempt + 1}/3 after network error (sleep {backoff}s)", file=sys.stderr)
                time.sleep(backoff)
                continue
            raise
    raise RuntimeError(f"Commons request failed: {last_error}")


def search_files(term, max_pages=2):
    """Return Commons File pages matching ``term`` (namespace 6)."""
    params = {
        "action": "query",
        "format": "json",
        "generator": "search",
        "gsrsearch": term,
        "gsrnamespace": "6",
        "gsrlimit": "100",
        "prop": "imageinfo",
        "iiprop": "url|mime|size|extmetadata",
    }
    pages = {}
    for _ in range(max_pages):
        data = commons_get(params)
        for page in data.get("query", {}).get("pages", {}).values():
            pages[page.get("title", "")] = page
        continuation = data.get("continue")
        if not continuation:
            break
        params.update(continuation)
    return list(pages.values())


def _metadata(extmetadata, key):
    return (extmetadata.get(key) or {}).get("value", "") or ""


def _contains_word(haystack, token):
    """True when ``token`` appears as a whole word (not as a substring)."""
    pattern = r"(?<![a-z0-9])" + re.escape(token) + r"(?![a-z0-9])"
    return re.search(pattern, haystack) is not None


def _contains_phrase(haystack, phrase):
    """True when ``phrase`` appears with word boundaries in a normalized haystack."""
    pattern = r"(?<![a-z0-9])" + re.escape(phrase) + r"(?![a-z0-9])"
    return re.search(pattern, haystack) is not None


def _normalize_taxon_text(value):
    """Lower-case and collapse whitespace/underscores before name matching."""
    return re.sub(r"\s+", " ", value.replace("_", " ")).strip().lower()


def _names_conflicting_binomial(raw_text, target_epithet):
    """True when ``raw_text`` names a binomial with an epithet other than the target's.

    Titles are matched case-sensitively, so ordinary title-cased prose ("Peace Lily")
    is not mistaken for a binomial. Synonyms that keep the target's epithet (for
    example ``Calathea orbifolia`` for *Goeppertia orbifolia*) are compatible.
    """
    target_head = target_epithet.split("-")[0]
    for genus, epithet in BINOMIAL_PATTERN.findall(raw_text):
        genus = genus.lower()
        epithet = epithet.lower()
        if genus in NON_TAXON_WORDS or epithet in NON_TAXON_WORDS:
            continue
        if epithet.split("-")[0] == target_head:
            continue
        return True
    return False


def build_candidate(page, scientific_name, common_name):
    """Turn a Commons page into a manifest image, or None when unusable."""
    infos = page.get("imageinfo") or []
    if not infos:
        return None
    info = infos[0]
    mime = info.get("mime", "")
    if mime not in ALLOWED_MIME:
        return None

    width = info.get("width") or 0
    height = info.get("height") or 0
    if min(width, height) < 320 or max(width, height) < 480:
        return None

    extmetadata = info.get("extmetadata") or {}
    family = classify_license(
        _metadata(extmetadata, "LicenseShortName"),
        _metadata(extmetadata, "License"),
    )
    if family is None:
        return None

    title = page.get("title", "")
    categories = _metadata(extmetadata, "Categories")
    raw_text = f"{title} {categories}"
    haystack = raw_text.lower()
    if any(term in haystack for term in JUNK_TERMS):
        return None

    # Commons search is fuzzy and a generic common name ("Calathea", "Haworthia")
    # matches congeners. Reject any file whose title/categories name a different
    # binomial, then accept it only when the target's full binomial appears or the
    # species epithet matches (which survives synonym renames that keep the epithet).
    normalized_text = _normalize_taxon_text(raw_text)
    target_binomial = _normalize_taxon_text(scientific_name)
    target_tokens = target_binomial.split()
    target_epithet = target_tokens[1] if len(target_tokens) > 1 else ""
    if _names_conflicting_binomial(raw_text, target_epithet):
        return None
    full_name_match = _contains_phrase(normalized_text, target_binomial)
    epithet_match = bool(target_epithet) and _contains_word(
        normalized_text, target_epithet
    )
    if not full_name_match and not epithet_match:
        return None

    url = (info.get("url") or "").split("?")[0]
    description_url = info.get("descriptionurl") or ""
    if not url.startswith("https://") or not description_url.startswith("https://"):
        return None

    author = strip_html(_metadata(extmetadata, "Artist"))
    if not author:
        author = strip_html(_metadata(extmetadata, "Credit"))
    if not author:
        author = "Unknown"

    license_url = _metadata(extmetadata, "LicenseUrl").strip()
    if not license_url.startswith("https://"):
        license_url = LICENSE_URLS[family]

    score = 10 if full_name_match else 5
    if common_name and common_name.lower() in haystack:
        score += 2
    if "inaturalist" in haystack:
        score += 1
    if "leaf" in haystack or "leaves" in haystack:
        score += 1

    return {
        "title": title,
        "url": url,
        "descriptionUrl": description_url,
        "author": author,
        "license": family,
        "licenseUrl": license_url,
        "_score": score,
    }


def collect_images(scientific_name, common_name, limit=20, exclude_urls=None):
    """Collect up to ``limit`` acceptable images for one species."""
    exclude_urls = exclude_urls or set()
    candidates = {}
    queries = [scientific_name]
    if common_name and common_name.lower() != scientific_name.lower():
        queries.append(common_name)

    for term in queries:
        try:
            pages = search_files(term)
        except (urllib.error.URLError, RuntimeError) as exc:
            print(f"  WARN: search failed for {term!r}: {exc}", file=sys.stderr)
            continue
        for page in pages:
            candidate = build_candidate(page, scientific_name, common_name)
            if candidate is not None and candidate["url"] not in exclude_urls:
                # Same file can appear in both searches; key on the stable URL.
                candidates.setdefault(candidate["url"], candidate)
        if len(candidates) >= limit:
            break

    ordered = sorted(candidates.values(), key=lambda item: (-item["_score"], item["title"].lower()))
    selected = ordered[:limit]
    for item in selected:
        del item["_score"]
    return selected


def build_species_entry(scientific_name, common_name, limit=20, exclude_urls=None):
    images = collect_images(scientific_name, common_name, limit=limit, exclude_urls=exclude_urls)
    warning = ""
    if len(images) < 10:
        warning = f" (WARN: only {len(images)} acceptable images)"
    print(f"  {scientific_name}: {len(images)} images{warning}")
    return {
        "scientificName": scientific_name,
        "commonName": common_name,
        "images": images,
    }


def build_manifest(only=None):
    """Query Commons for each seeded species and write the manifest."""
    seed = load_seed()
    if only:
        wanted = only.strip().lower()
        seed = [pair for pair in seed if pair[0].lower() == wanted]
        if not seed:
            print(f"ERROR: species not in seed file: {only}", file=sys.stderr)
            return 1

    # When filtering, preserve the other entries from an existing manifest so a
    # single-species refresh does not throw away the rest of the dataset.
    existing = {}
    if only and os.path.exists(MANIFEST_PATH):
        try:
            with open(MANIFEST_PATH, "r", encoding="utf-8") as handle:
                for entry in json.load(handle).get("species", []):
                    existing[entry.get("scientificName", "")] = entry
        except (OSError, json.JSONDecodeError) as exc:
            print(f"WARN: could not read existing manifest: {exc}", file=sys.stderr)

    # One Commons file must describe exactly one species in the cache: a group
    # photo shared by several entries is not a useful training reference.
    used_urls = {
        image["url"]
        for name, entry in existing.items()
        if name not in {item for item, _ in seed}
        for image in entry.get("images", [])
    }

    print(f"Querying Wikimedia Commons for {len(seed)} species...")
    entries = []
    for scientific_name, common_name in seed:
        entry = build_species_entry(
            scientific_name, common_name, exclude_urls=used_urls
        )
        entries.append(entry)
        used_urls.update(image["url"] for image in entry["images"])

    if only:
        for name, entry in existing.items():
            if name and name not in {item["scientificName"] for item in entries}:
                entries.append(entry)
        order = {name: index for index, (name, _) in enumerate(load_seed())}
        entries.sort(key=lambda item: order.get(item["scientificName"], 10**6))

    manifest = {
        "source": "Wikimedia Commons",
        "generatedAt": datetime.date.today().isoformat(),
        "licenses": list(LICENSE_WHITELIST),
        "species": entries,
    }
    os.makedirs(os.path.dirname(MANIFEST_PATH), exist_ok=True)
    temporary_path = MANIFEST_PATH + ".tmp"
    with open(temporary_path, "w", encoding="utf-8") as handle:
        json.dump(manifest, handle, indent=2, ensure_ascii=False)
        handle.write("\n")
    os.replace(temporary_path, MANIFEST_PATH)
    total = sum(len(entry["images"]) for entry in entries)
    print(f"Wrote {MANIFEST_PATH} ({len(entries)} species, {total} images)")
    return 0


# ---------------------------------------------------------------------------
# --download
# ---------------------------------------------------------------------------


def _url_extension(url):
    suffix = os.path.splitext(urllib.parse.urlparse(url).path)[1].lower()
    if suffix in (".jpg", ".jpeg", ".png", ".webp"):
        return suffix
    return ".jpg"


def _looks_like_image(data):
    if data.startswith(b"\xff\xd8\xff"):
        return True
    if data.startswith(b"\x89PNG\r\n\x1a\n"):
        return True
    return len(data) >= 12 and data[:4] == b"RIFF" and data[8:12] == b"WEBP"


def _validate_download_url(url):
    """Reject a download URL unless it is https on an allowlisted Wikimedia host."""
    parsed = urllib.parse.urlparse(url)
    if parsed.scheme != "https":
        raise RuntimeError(f"URL is not https: {url!r}")
    try:
        port = parsed.port
    except ValueError as exc:
        raise RuntimeError(f"URL has an invalid port: {url!r}") from exc
    if port not in (None, 443):
        raise RuntimeError(f"URL uses a non-standard port: {url!r}")
    host = (parsed.hostname or "").lower()
    if host not in ALLOWED_DOWNLOAD_HOSTS:
        raise RuntimeError(f"host {host or '<missing>'!r} is not an allowed image host: {url!r}")
    return parsed


def _download_one(url, destination):
    _validate_download_url(url)
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            # Redirects are followed by urllib; re-check the final destination so an
            # allowlisted URL cannot bounce the download to an arbitrary host.
            _validate_download_url(response.geturl() or url)
            status = getattr(response, "status", None) or response.getcode()
            if status != 200:
                raise RuntimeError(f"HTTP {status}")
            content_type = response.headers.get("Content-Type", "")
            media_type = content_type.split(";", 1)[0].strip().lower()
            if media_type not in ALLOWED_IMAGE_TYPES:
                raise RuntimeError(
                    f"non-image response (Content-Type: {content_type or 'unknown'})"
                )
            data = response.read(MAX_DOWNLOAD_BYTES + 1)
    except urllib.error.HTTPError as exc:
        raise RuntimeError(f"HTTP {exc.code} {exc.reason}") from exc
    except urllib.error.URLError as exc:
        raise RuntimeError(f"network error: {exc.reason}") from exc

    if len(data) > MAX_DOWNLOAD_BYTES:
        limit_mib = MAX_DOWNLOAD_BYTES // (1024 * 1024)
        raise RuntimeError(f"image exceeds the {limit_mib} MiB size cap")
    if not _looks_like_image(data):
        raise RuntimeError(
            f"response body is not a recognised image (Content-Type: {content_type})"
        )

    temporary_path = destination + ".part"
    with open(temporary_path, "wb") as handle:
        handle.write(data)
    os.replace(temporary_path, destination)


def download_images(only=None):
    """Download the manifest's images into the gitignored cache directory."""
    if not os.path.exists(MANIFEST_PATH):
        print(f"ERROR: manifest not found: {MANIFEST_PATH}", file=sys.stderr)
        return 1
    with open(MANIFEST_PATH, "r", encoding="utf-8") as handle:
        manifest = json.load(handle)

    species = manifest.get("species", [])
    if only:
        wanted = only.strip().lower()
        species = [entry for entry in species if entry.get("scientificName", "").lower() == wanted]
        if not species:
            print(f"ERROR: species not in manifest: {only}", file=sys.stderr)
            return 1

    os.makedirs(CACHE_DIR, exist_ok=True)
    downloaded = 0
    skipped = 0
    failures = []

    for entry in species:
        scientific_name = entry["scientificName"]
        directory = os.path.join(CACHE_DIR, kebab(scientific_name))
        os.makedirs(directory, exist_ok=True)
        print(f"{scientific_name} -> {directory}")
        for index, image in enumerate(entry.get("images", []), start=1):
            title = image.get("title", "")
            stem = os.path.splitext(title.split(":", 1)[-1])[0]
            filename = f"{index:02d}-{kebab(stem)[:60]}{_url_extension(image['url'])}"
            destination = os.path.join(directory, filename)
            if os.path.exists(destination) and os.path.getsize(destination) > 0:
                skipped += 1
                continue
            try:
                _download_one(image["url"], destination)
                downloaded += 1
                print(f"  ok   {filename}")
            except RuntimeError as exc:
                failures.append(f"{scientific_name}: {image.get('url')} ({exc})")
                print(f"  FAIL {filename}: {exc}", file=sys.stderr)

    print(f"Downloaded {downloaded}, skipped {skipped}, failed {len(failures)}")
    if failures:
        print(f"{len(failures)} download failure(s):", file=sys.stderr)
        for failure in failures:
            print(f"  ERROR: {failure}", file=sys.stderr)
        return 1
    return 0


# ---------------------------------------------------------------------------
# --check
# ---------------------------------------------------------------------------


def check_manifest(only=None):
    """Validate the committed manifest offline.  Return a process exit code."""
    errors = []
    warnings = []

    if not os.path.exists(MANIFEST_PATH):
        print(f"ERROR: manifest not found: {MANIFEST_PATH}", file=sys.stderr)
        return 1

    seed = load_seed()
    seed_names = [name for name, _ in seed]
    seed_common = {name: common for name, common in seed}

    try:
        with open(MANIFEST_PATH, "r", encoding="utf-8") as handle:
            manifest = json.load(handle)
    except (OSError, json.JSONDecodeError) as exc:
        print(f"ERROR: cannot read manifest: {exc}", file=sys.stderr)
        return 1

    species_entries = manifest.get("species")
    if not isinstance(species_entries, list):
        print("ERROR: manifest has no 'species' list", file=sys.stderr)
        return 1

    seen = {}
    for entry in species_entries:
        if not isinstance(entry, dict):
            errors.append("manifest species entry is not an object")
            continue
        name = entry.get("scientificName")
        if not isinstance(name, str) or not name.strip():
            errors.append("manifest species entry has no scientificName")
            continue
        name = name.strip()
        if name in seen:
            errors.append(f"duplicate species entry: {name}")
            continue
        seen[name] = entry

    missing = [name for name in seed_names if name not in seen]
    extra = [name for name in seen if name not in set(seed_names)]
    if not only:
        if missing:
            errors.append(f"manifest is missing {len(missing)} seeded species: {', '.join(missing)}")
        if extra:
            errors.append(f"manifest has {len(extra)} species absent from the seed file: {', '.join(extra)}")

    names_to_check = [name for name in seed_names if name in seen]
    if only:
        wanted = only.strip().lower()
        names_to_check = [name for name in names_to_check if name.lower() == wanted]
        if not names_to_check:
            print(f"ERROR: --only species not found in manifest: {only}", file=sys.stderr)
            return 1

    counts = []
    for name in names_to_check:
        entry = seen[name]
        common = entry.get("commonName")
        if not isinstance(common, str) or not common.strip():
            errors.append(f"{name}: missing commonName")
        elif common.strip() != seed_common.get(name):
            warnings.append(
                f"{name}: commonName {common!r} differs from seed {seed_common.get(name)!r}"
            )

        images = entry.get("images")
        if not isinstance(images, list):
            errors.append(f"{name}: 'images' is not a list")
            counts.append((name, 0))
            continue

        seen_urls = set()
        for index, image in enumerate(images, start=1):
            prefix = f"{name} image #{index}"
            if not isinstance(image, dict):
                errors.append(f"{prefix}: not an object")
                continue
            for field in ("title", "url", "descriptionUrl", "author", "license", "licenseUrl"):
                value = image.get(field)
                if not isinstance(value, str) or not value.strip():
                    errors.append(f"{prefix}: missing {field}")
            for field in ("url", "descriptionUrl", "licenseUrl"):
                value = image.get(field)
                if isinstance(value, str) and value.strip() and not value.strip().startswith("https://"):
                    errors.append(f"{prefix}: {field} is not https:// ({value!r})")
            license_name = image.get("license")
            if isinstance(license_name, str) and license_name.strip():
                if license_name.strip() not in LICENSE_WHITELIST:
                    errors.append(f"{prefix}: licence {license_name!r} is not allowed")
            url = image.get("url")
            if isinstance(url, str) and url.strip():
                try:
                    _validate_download_url(url.strip())
                except RuntimeError as exc:
                    errors.append(f"{prefix}: {exc}")
                if url.strip() in seen_urls:
                    warnings.append(f"{prefix}: duplicate url {url!r}")
                seen_urls.add(url.strip())

        count = len(images)
        counts.append((name, count))
        if count == 0:
            errors.append(f"{name}: has 0 images")
        elif count < 10:
            warnings.append(f"{name}: only {count} images (below the 10-image target)")
        elif count > 20:
            warnings.append(f"{name}: {count} images (above the 20-image cap)")

    print(f"Manifest: {MANIFEST_PATH}")
    print(f"Species checked: {len(counts)} / {len(seed)} seeded")
    print()
    print(f"{'images':>6}  species")
    print(f"{'------':>6}  -------")
    for name, count in counts:
        marker = " (WARN: <10)" if count < 10 else ""
        print(f"{count:>6}  {name}{marker}")

    if counts:
        values = sorted(count for _, count in counts)
        median = values[len(values) // 2] if len(values) % 2 else (values[len(values) // 2 - 1] + values[len(values) // 2]) // 2
        print()
        print(f"Total images: {sum(values)}; min {values[0]}, max {values[-1]}, median {median}")

    if warnings:
        print()
        print(f"{len(warnings)} warning(s):")
        for warning in warnings:
            print(f"  WARN: {warning}")

    if errors:
        print()
        print(f"{len(errors)} error(s):", file=sys.stderr)
        for error in errors:
            print(f"  ERROR: {error}", file=sys.stderr)
        return 1

    print()
    print("OK: manifest is valid")
    return 0


def main(argv=None):
    parser = argparse.ArgumentParser(
        description="Build, download and validate the recognition image cache.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--build-manifest", action="store_true", help="query Commons and write the manifest")
    mode.add_argument("--download", action="store_true", help="download manifest images into dataset/image-cache/")
    mode.add_argument("--check", action="store_true", help="validate the committed manifest offline")
    parser.add_argument("--only", metavar="SCIENTIFIC_NAME", help="restrict to one species")
    args = parser.parse_args(argv)

    if args.build_manifest:
        return build_manifest(only=args.only)
    if args.download:
        return download_images(only=args.only)
    return check_manifest(only=args.only)


if __name__ == "__main__":
    sys.exit(main())
