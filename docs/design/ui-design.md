# Peony — UI Design Document

| | |
|---|---|
| **Status** | v0.2 — initial draft, pending review; auth aligned with the shipped JWT backend |
| **Date** | 2026-10-31 |
| **Scope** | React Native (Expo) UI for web + Android + iOS, plus the web marketing site |
| **Wire contract** | `openapi/peony-api.yaml` (v0.2.0) in the backend repo — single source of truth |
| **Related** | Backend ADR-003 (tiering), ADR-004 (API versioning), ADR-005 (JWT auth), `docs/api-tier-contracts.md` |

> This doc lives temporarily in the backend repo. It moves to the frontend repo
> (suggested name: `peony-app`) when that repo is created — see §13.1.

## 1. Overview

Peony is a **plant identification + care companion**: point your camera (or pick a
photo) at a plant, get a species-level identification via PlantNet, and bridge the
result into curated care information.

- **Audience**: public product — app store listings, marketing landing page, strangers.
- **Platforms**: web, Android, iOS — **one codebase** (Expo + Expo Router + React Native Web).
- **Build order**: **web → Android → iOS**.
- **v1 language**: English only (i18n architecture noted, not implemented).

### 1.1 Goals

1. A fast, trustworthy identify → learn loop on all three platforms.
2. A polished, **SEO-ready** marketing site (a v1 requirement, not a stretch goal).
3. A thin, boring JWT auth layer that gets out of the way.
4. UI that degrades gracefully against an API that is still being finished
   (unseeded catalog, 1 MB upload cap, remaining gaps in §14).

### 1.2 What "stateless" means in this product

Plant **information** (catalog, care data, taxonomy) is **shared** — it is never
per-user. Per-user **data** (the user's own plants, notes, locations) **is** in v1.
The UI is a tool with a thin auth layer — no data-management-heavy screens (see §15).

## 2. Product model

| | Free | Premium |
|---|---|---|
| Recognition results | Top-1 hit (scientific name + common names) | Up to 10 hits, full taxonomy, GBIF/POWO ids |
| Plant scans | 5 per calendar month | Unlimited (counter hidden) |
| Plant detail fields | family, tags, light, water, toxicity | + humidity, temp range, difficulty, native region, mature size |
| Catalog / locations / notes | ✓ | ✓ |
| Multi-device sync, advanced analytics | — (capability flag only, **no UI in v1**) | — (capability flag only, **no UI in v1**) |

- Tier is read from `ProfileResponse.tier` + `capabilities`. Never hard-coded.
- Upgrade: `POST /api/v1/subscription/upgrade` exists but is **not wired in v1**
  (no payment flow). Upgrade CTAs are "coming soon" (§4.5, §4.11).
- Quota: the API never returns a remaining count — the client tracks it locally
  (§9). Note: recognition attempts that reach the PlantNet call consume quota,
  including PlantNet failures; requests rejected as unreadable/unsupported before
  the counter (400/415) do not.

## 3. Information architecture

### 3.1 Sitemap (mobile)

```
Welcome ─┬─ Sign in
         └─ Sign up
Home (tab) ─ Scan CTA ─→ Recognition (capture → loading → result)
My plants (tab) ─ Plant detail ─ Locations editor
                  └ Notes (inline)
  └ + ─→ Add / link plant (search → link | register)
Catalog (tab) ─ Plant detail
Profile (tab)
```

### 3.2 Sitemap (web)

Same graph, top navigation: `Home · My plants · Catalog` + primary `Scan` button +
profile menu. No bottom tabs.

### 3.3 Expo Router route tree

```
app/
├── _layout.tsx            # root: theme, providers, auth guard
├── index.tsx              # redirect: authed → (tabs)/home, else (auth)/welcome
├── (auth)/
│   ├── _layout.tsx
│   ├── welcome.tsx
│   ├── signin.tsx
│   └── signup.tsx
├── (tabs)/
│   ├── _layout.tsx        # bottom tabs (native) / top nav (web)
│   ├── home.tsx
│   ├── my-plants.tsx
│   ├── catalog.tsx
│   └── profile.tsx
├── plant/
│   ├── add.tsx
│   └── [id]/
│       ├── index.tsx      # detail
│       └── locations.tsx
└── recognize.tsx          # capture → loading → result (internal stage state)
```

## 4. Screen specs

All wireframes are mobile-first; web differences are noted per screen.

### 4.1 Welcome

```
┌───────────────────────────┐
│          ( logo )         │
│         Peony             │
│  Know your plants         │
│  at a glance              │
│                           │
│  [ Sign in ]   (primary)  │
│  [ Create account ]       │
└───────────────────────────┘
```

- Sign in → §4.2; Create account → §4.3.
- Web: same content in a centered card, max-width 420px.

### 4.2 Sign in

```
┌───────────────────────────┐
│  ←   Sign in              │
│  Username or email [____] │
│  Password        [____]   │
│  [ Sign in ]              │
│  New here? Create account │
└───────────────────────────┘
```

- `POST /api/v1/auth/signin` (accepts username **or** email).
- 401 → inline "Invalid username or password".
- 400 errors (`"<field>: <message>"`) → under the offending field.
- Success → store the token pair (§8) → Home.

### 4.3 Sign up

```
┌───────────────────────────┐
│  ←   Create account       │
│  First [___] Last [___]   │
│  Username    [________]   │
│  Email       [________]   │
│  Password    [________]   │
│  [ Create account ]       │
│  By signing up you agree… │
└───────────────────────────┘
```

- All five fields required (API: username 3–50 unique, email unique, password 8–100, firstName, lastName).
- 400 → message under the first invalid field; 409 → "Username or email already taken".
- Privacy-policy link (store requirement) → marketing site page (§12).
- Success → store the token pair (§8) → Home.

### 4.4 Home

```
┌───────────────────────────┐
│ Peony            (avatar) │
│ ┌───────────────────────┐ │
│ │        [ 📷 ]          │ │
│ │   Scan a plant         │ │
│ │   camera · gallery     │ │
│ │   3 of 5 scans left    │ │
│ └───────────────────────┘ │
│ [ My plants    4  →  ]    │
│ [ Catalog     50  →  ]    │
├───────────────────────────┤
│ Home  MyPlants Cat Profile│
└───────────────────────────┘
```

- Scan CTA → §4.5 (picker: camera or photo library).
- Quota row (free only): `photoUploadsPerMonth` minus local attempt count (§9); hidden for premium.
- Cards deep-link into the tabs. Empty My plants → the card CTA becomes "Add your first plant".
- Web: hero-style scan panel on top, cards below, top nav bar.

### 4.5 Recognition (capture → loading → result)

One route (`/recognize`), internal stage state. Cancel returns to Home.

**Loading** (synchronous upstream; worst case ~3 min with retries):

```
┌───────────────────────────┐
│  Analyzing your photo…    │
│  [shimmer / progress]     │
│  PlantNet can be slow on  │
│  busy days.               │
│  [ Cancel ]               │
└───────────────────────────┘
```

- Cancel visible immediately (no minimum wait). Client timeout: 180 s.

**Result — free tier** (single hit):

```
┌───────────────────────────┐
│  [photo]  92%             │
│  Ajuga reptans            │
│  Carpet bugle             │
│  [ See care details ]     │
│  [ Retake ]               │
└───────────────────────────┘
```

**Result — premium** (up to 10 hits): same header + ranked list; each row shows
rank, confidence bar, scientific name, common names.

Confidence bands (tunable, §16):

| Score | Presentation |
|---|---|
| ≥ 0.5 | Confident: "This is most likely **X**" |
| 0.3 – 0.5 | "Best guess" badge + retake nudge |
| < 0.3 | "Couldn't identify this one clearly" + retake tips (light, whole plant, plain background) |

- **Bridge to care info** (recognition returns no care data): look the top hit up by
  scientific name via `GET /plants?q=`:
  - match → plant detail (§4.7);
  - no match → detail view with "Not in our catalog yet" + "Add it" (§4.9).
- 429 (monthly quota) → upgrade modal: "You've used your 5 free scans this month" +
  **coming soon** button + "check back on the 1st".
- 429 (PlantNet rate) → "Hang tight — try again in a minute", retry disabled ~60 s.

### 4.6 Catalog

```
┌───────────────────────────┐
│  [ Search plants…      ]  │
│ ┌───────────────────────┐ │
│ │🖼 Pothos  Epipremnum… │ │
│ │🖼 Snake Plant  Sansev.│ │
│ │🖼 ZZ Plant   Zamiocul.│ │
│ └───────────────────────┘ │
└───────────────────────────┘
```

- Typahead: 250 ms debounce, `GET /api/v1/plants?q=` (substring on name / scientific
  name / tags). Empty `q` = whole catalog. **No pagination** (MVP: 50 plants).
- Row: thumbnail (authed fetch, 404 → leaf placeholder), common name, scientific name
  (italic), water-frequency chip.
- **Empty state (pre-seed, backend issue #9)**: "The curated catalog is on its way"
  + CTA "Add your own plants meanwhile" → §4.9.
- Row tap → §4.7.

### 4.7 Plant detail

```
┌───────────────────────────┐
│ ←  [thumbnail]             │
│  Pothos                    │
│  Epipremnum aureum         │
│  #easy-care #purifier      │
│ ┌ Care ─────────────────┐  │
│ │ ☀ Light      Bright…  │  │
│ │ 💧 Water      Every 7d │  │
│ │ 🌡 Temp       18–28°C  │  │
│ │ 💦 Humidity   Medium   │  │
│ │ 📏 Mature     3–5 m    │  │
│ └───────────────────────┘  │
│  ⚠ Toxic to pets           │
│  [ + Add to my plants ]    │  ← when owned, instead:
│                            │    [ Edit notes ] [ Locations ]
└───────────────────────────┘
```

- `GET /api/v1/plants/{id}`. Render **only present fields** (tier-filtered; absent
  ≠ null). Premium-only rows (humidity, temp range, difficulty, native region,
  mature size) simply don't render for free.
- Toxicity: `toPets` / `toHumans` booleans → badges ("Toxic to pets", "Toxic to
  humans"); both false → positive "Pet safe" badge.
- Gallery: horizontal strip from `GET /plants/{id}/gallery/{index}` (0-based);
  404 → omit. Pre-seed: strip hidden.
- **Owned** (plant present in `GET /me/plants`): buttons become **Edit notes**
  (inline field, `PATCH /plants/{id}/notes`, ≤ 255 chars with live counter) and
  **Locations** (§4.10).
- **Not owned**: **Add to my plants** → `POST /plants` echoing the catalog's
  commonName / scientificName / waterFrequency (dedupes on scientific name, links
  the user) → toast → buttons switch to the owned state.
- Web: two-column layout (image left, care right) at ≥ 768px.

### 4.8 My plants

```
┌───────────────────────────┐
│  My plants          [ + ] │
│ ┌───────────────────────┐ │
│ │🖼 "Fern"  Boston Fern  │ │
│ │   💧 every 3d · 🚪 2  │ │
│ ├───────────────────────┤ │
│ │🖼 Pothos     Epiprem…  │ │
│ │   💧 every 7d · 🚪 1  │ │
│ └───────────────────────┘ │
└───────────────────────────┘
```

- Source: `GET /api/v1/me/plants` (new endpoint, §14). Row: nickname (falls back to
  common name), water frequency, location count, thumbnail.
- **Empty state**: "No plants yet" + [ Add your first plant ] → §4.9.
- `+` (FAB on mobile, header button on web) → §4.9.
- Watering-schedule fields exist in the DB but have **no endpoint** — not shown in
  v1 (§15).

### 4.9 Add / link plant

Two stages on one route.

**Stage 1 — search existing:**

```
┌───────────────────────────┐
│  ←  Add a plant           │
│  [ Search the catalog… ]  │
│  Matches:                 │
│  [ Pothos — Add to my… ]  │
│  …                        │
│  Can't find it?           │
│  [ Register new species ] │
└───────────────────────────┘
```

- Typahead over `GET /plants?q=`.
- **Add to my plants** → `POST /plants` with the catalog values (reuses the row,
  links the user) → plant detail.

**Stage 2 — register:**

```
┌───────────────────────────┐
│  ←  Register a species    │
│  Common name   [____]*    │
│  Scientific    [____]*    │
│  Water every   [ 7 ] days*│
│  ─ More details (optional)│
│  Family [__] Light [__]   │
│  Tags   [__]             │
│  ☐ Toxic to pets  ☐ …hum.│
│  [ Save ]                 │
└───────────────────────────┘
```

- Required: `commonName`, `scientificName`, `waterFrequency` (API-enforced).
- Optional core: `family`, `lightNeeds`, `tags`, `toPets`, `toHumans`. Premium fields
  (`humidity`, `tempRange`, `difficulty`, `nativeRegion`, `matureSize`) are accepted
  by the API for all tiers — v1 form keeps them in a collapsed "More details" section.
- 400 → field messages; duplicate scientific name → 201 (dedupe + link, success).

### 4.10 Location editor (per plant)

```
┌───────────────────────────┐
│ ←  Locations — Pothos     │
│  🏠 Home · 🚪 Living room │
│  🏠 Studio · 🚪 Desk      │
│  [ + Add location ]       │
└───────────────────────────┘
   [ + ] → sheet/modal:
   House:  [ existing ▾ | + new name ]
   Room:   [ existing ▾ | + new name ]
   [ Assign ]
```

- List: `GET /plants/{id}/locations` (oldest first).
- Create house `POST /plants/{id}/locations/houses {name}`; create room
  `POST /houses/{houseId}/rooms {name}`; assign `POST /plants/{id}/locations {roomId}`.
- 409 "Plant is already assigned to the room" → toast, no state change.
- Existing-house/room pickers need `GET /api/v1/me/houses` (new endpoint, §14).
  Until it ships: "create new house/room" flow with locally remembered ids.
- The API has **no delete endpoints** → no remove UI in v1 (§15).

### 4.11 Profile

```
┌───────────────────────────┐
│  (A)  Ada Lovelace        │
│  ada@example.com          │
│  [ FREE ]  3 of 5 left    │
│  ┌ Premium card ────────┐ │
│  │ Unlimited scans,     │ │
│  │ full results…        │ │
│  │ [ Upgrade — coming soon ] │
│  └──────────────────────┘ │
│  [ Sign out ]             │
│  Peony 0.1.0 · Privacy   │
└───────────────────────────┘
```

- `GET /api/v1/auth/profile`. Tier badge from `tier`; quota row free only.
- Upgrade CTA: **disabled / "coming soon" toast** — nothing wired (§2). The endpoint
  exists for the future payment flow.
- Sign out: discard tokens locally (§8); server-side revocation is optional (§14).
- App version + privacy-policy link (store requirement).

## 5. Component inventory

| Component | Notes |
|---|---|
| `Button` | primary / secondary / ghost; loading + disabled states |
| `TextField` | label, inline error, secure option, character counter (notes ≤ 255) |
| `PickerSheet` | bottom sheet (native) / dropdown (web); "existing or new" pattern for house/room |
| `PlantCard` | thumb + names + meta chips; row & grid variants |
| `PlantThumb` | authed fetch → blob → URI; 404 → leaf placeholder |
| `ConfidenceBar` | 0–1 scale, whole-% label |
| `Badge` | tier, toxicity, "best guess" |
| `QuotaPill` | "X of 5 scans left" |
| `EmptyState` | illustration + title + CTA |
| `PatientLoader` | message + progress + Cancel |
| `Toast` | success / warning / error, ~3 s auto-dismiss |
| `Sheet` / `Modal` | one primitive, platform-rendered |
| `CareGrid` | renders only present fields |
| `HitRow` | ranked recognition-result row |
| `UpgradeCard` | "coming soon" state |
| `TabBar` / `NavBar` | bottom tabs (native) / top nav (web) |
| `ImageStrip` | gallery carousel, 404-tolerant |

## 6. Routing table

| Route | Mobile | Web path |
|---|---|---|
| `index` | redirect (auth gate) | `/` |
| `(auth)/welcome` | push | `/welcome` |
| `(auth)/signin` | push | `/signin` |
| `(auth)/signup` | push | `/signup` |
| `(tabs)/home` | tab | `/` (authed default) |
| `(tabs)/my-plants` | tab | `/my-plants` |
| `(tabs)/catalog` | tab | `/catalog` |
| `(tabs)/profile` | tab | `/profile` |
| `recognize` | push | `/recognize` |
| `plant/add` | push | `/plants/add` |
| `plant/[id]` | push | `/plants/:id` |
| `plant/[id]/locations` | push | `/plants/:id/locations` |

- Auth guard (root layout): unauthenticated → only `(auth)/*` reachable;
  authenticated visiting `(auth)/*` → Home.
- Deep links / universal links: **out of v1** (§15).

## 7. API mapping

| UI action | Endpoint | Notes |
|---|---|---|
| Sign up | `POST /api/v1/auth/signup` | → `{token, refreshToken, profile}` (§8) |
| Sign in | `POST /api/v1/auth/signin` | → `{token, refreshToken, profile}` (§8) |
| Refresh | `POST /api/v1/auth/refresh` | rotates the refresh token (single-use, §8) |
| Profile | `GET /api/v1/auth/profile` | tier, capabilities, quota |
| Scan | `POST /api/v1/plantrecognition/recognize` | multipart `file`; timeout 180 s |
| Catalog / typahead | `GET /api/v1/plants?q=` | no pagination |
| Plant detail | `GET /api/v1/plants/{id}` | tier-filtered fields |
| Add / link plant | `POST /api/v1/plants` | dedupes on `scientificName` |
| Edit notes | `PATCH /api/v1/plants/{id}/notes` | ≤ 255 chars |
| My plants | `GET /api/v1/me/plants` | **new** (§14) |
| My houses (+ rooms) | `GET /api/v1/me/houses` | **new** (§14) |
| Create house | `POST /api/v1/plants/{id}/locations/houses` | |
| Create room | `POST /api/v1/houses/{houseId}/rooms` | |
| Assign location | `POST /api/v1/plants/{id}/locations` | 409 on duplicate |
| List locations | `GET /api/v1/plants/{id}/locations` | oldest first |
| Thumbnail | `GET /api/v1/plants/{id}/thumb` | authed fetch → blob |
| Gallery | `GET /api/v1/plants/{id}/gallery/{index}` | 0-based |
| Upgrade | `POST /api/v1/subscription/upgrade` | **not wired in v1** |

Client: types generated from `openapi/peony-api.yaml` (openapi-typescript); a thin
fetch wrapper handles Bearer injection, refresh-on-401, and the
`{"error": "…"}` envelope. Note the mixed wire casing in recognition responses
(`api_version`, `remaining_identification_requests` are snake_case) — never
hand-write these fields.

## 8. Authentication (JWT)

**Shipped** (backend spec #17 / ADR-005):

1. Sign up / sign in return `{token, refreshToken, profile}`; `token` = JWT
   **access token, 1 h** (configurable via `JWT_ACCESS_TOKEN_TTL` within the agreed
   1–5 h band, §16).
2. **Refresh token, 30 days**, opaque and rotated on every use (single-use; replaying
   a rotated token fails 401). Both stored in **SecureStore** (native) /
   **localStorage** (web — XSS trade-off accepted for MVP; httpOnly cookie refresh is
   a v2 hardening item).
3. Every request: `Authorization: Bearer <access>`.
4. **Silent refresh**: on 401 → single-flight refresh (one in-flight refresh shared
   across concurrent 401s) → retry the original request once → on failure: sign out +
   redirect to sign-in with a "Session expired" toast.
5. **Sign out** = discard both tokens locally (+ optional server revocation, §14 item 7).
6. Session-expiry UX: no hard walls — the next action triggers silent refresh; the
   user only sees a toast when refresh itself fails.

**Backend status**: JWT is live under `/api/v1` — sign-up, sign-in and refresh return
`AuthResponse { token, refreshToken, profile }`; protected routes require
`Authorization: Bearer <token>`; no `JSESSIONID` is issued. Refresh failures (unknown,
already rotated, expired) all return the same `401 {"error":"Authentication required"}`
envelope. The client's credential adapter is therefore Bearer-only.

**CORS note**: with Bearer auth, the web app is no longer forced same-origin — a
CORS allowlist for the web origin (§14 item 2) unlocks EAS Hosting on its own domain.

## 9. Data & state

- **Server state**: TanStack Query v5. Keys mirror routes (`["plants","q",q]`,
  `["plant",id]`, `["me","plants"]`, …); mutations invalidate affected keys.
  Recognition is **not cached** (every call consumes quota).
- **Auth state**: small module (no global store) — token accessors, single-flight
  refresh, sign out — exposed to the API wrapper via React context.
- **Quota counter** (free tier):
  - Source: `profile.photoUploadsPerMonth` (5) at sign-in / profile fetch.
  - Decrement **locally** after success and after any PlantNet failure (rejected
    image 400, upstream 429, 5xx): the backend records the upload after image
    validation and before the PlantNet call. Do **not** decrement on
    image-validation 400/415 or on a quota 429. Pure network failures (no server
    response) do **not** decrement.
  - Persist per calendar month (`yyyy-MM` key); on a quota 429, force the counter to 0.
  - Display: `QuotaPill` on Home + Profile; hidden for premium.
- **Image pipeline** (before upload):
  1. `expo-image-picker` (camera or photo library).
  2. `expo-image-manipulator`: resize long edge → **1280 px**, JPEG **q0.7**.
  3. If > 900 KB → re-encode q0.5; if still > 1 MB → resize to 1024 px
     (backend multipart cap: **1 MB**).
  4. Send as multipart `file` part + `format=image/jpeg`. **Never** the base64
     query-param path (URL length limits).
  5. Client timeout: 180 s.
- **Authed images**: `fetch` with Bearer → blob → `expo-file-system` URI (native) /
  object URL (web). 404 → placeholder.
- **Form state**: local to the screen; no global form store.

## 10. Error & rate-limit UX

Every error body is `{"error": "<message>"}`; branch on HTTP status and surface the
message verbatim where it is user-grade.

| Status | Meaning (backend message) | UX |
|---|---|---|
| 400 | validation / unreadable image / PlantNet rejected | inline field message (forms) / "We couldn't read that photo — try another" (scan) |
| 401 | `Authentication required` | silent refresh → retry once → sign-in + "Session expired" toast |
| 409 | `Username or email already taken` / `Plant is already assigned to the room` | inline / toast, no state change |
| 413 / 415 | size exceeded / unsupported format | "That photo is too large" / "Use a JPEG, PNG, or WebP photo" |
| 429 (quota) | `Free tier limit of 5 photo uploads per month reached…` | upgrade modal (coming soon) + "check back on the 1st"; quota → 0 |
| 429 (upstream) | `PlantNet API rate limit exceeded…` | "Try again in a minute" — retry disabled ~60 s |
| 502 | `PlantNet API is (temporarily unavailable / unreachable)…` | "The identification service is down — try again in a bit" + retry |
| 504 / timeout | client 180 s elapsed | treated as 502 |
| network | offline / DNS | "No connection" banner + retry |

Rules: never show raw JSON/stacks; at most one retry affordance per failure
(no retry-spamming the quota); a failed scan never loses the user's photo.

## 11. Design brief

> The visual system is **TBD via experimentation** with agentic design tools
> (§11.4). This brief is the contract every candidate must satisfy.

**Personality**: friendly, botanical, calm, trustworthy. A gardening companion, not
a tech dashboard. Warmth over cleverness.

**Candidate tokens** (starting point, not a decision):

| Token | Candidate |
|---|---|
| `color.primary` (peony) | `#D96C8B` |
| `color.deep` (green) | `#2E5B3E` |
| `color.bg` (warm neutral) | `#FAF7F2` |
| `color.surface` | `#FFFFFF` |
| `color.ink` | `#26301F` |
| `color.success / warning / danger` | `#3E7C4F / #C77E2D / #B4452F` |
| radii | card 16, button 12, pill 999 |
| spacing | 4-pt grid; sections 24 / 32 |
| icons | 24 px line, rounded caps |
| type (display) | Fraunces (soft serif) — *candidate* |
| type (UI) | Inter — *candidate* |

**Copy tone**: plain, warm, first-person plural ("We couldn't identify this one").
No jargon ("species", never "taxonomic hit"). Never fake precision (whole-% confidence).

**Do / Don't**

- Do: generous whitespace, real plant photography, one primary CTA per screen, light-first with a dark mode.
- Don't: dense tables, stock "tech" imagery, more than one accent color per screen, alert-dialog spam.

**Accessibility**: WCAG AA contrast, 44 px touch targets, full keyboard navigation +
visible focus states on web, honor reduced-motion settings.

### 11.4 Agentic design workflow

1. Freeze this brief + IA (§3) + wireframes (§4).
2. Generate **3 landing-page candidates** (Astro) with 2–3 tools: v0, Lovable, Bolt
   (Figma AI optional for hi-fi app screens). Feed each the brief + hero copy + route map.
3. Critique each candidate against the brief (checklist: tone, palette discipline,
   LCP < 2.5 s, a11y AA, mobile-first, one CTA).
4. Pick a winner → extract **`tokens.json`** → single source for the Astro Tailwind
   config *and* the RN theme object.
5. Re-render the §4 wireframes at hi-fi from the winner; diff against the brief; iterate.
6. Converge: tokens + component styles committed; hi-fi per screen stored in
   `docs/design/` of the app repo.

## 12. Marketing site (Astro)

Separate static site in the app repo (`marketing/`), served at the **domain root**;
the RN web app lives on a subpath or subdomain (§13.2). SEO is a v1 target.

### 12.1 Pages

| Page | Path | Notes |
|---|---|---|
| Landing | `/` | §12.2 |
| Privacy policy | `/privacy` | **required** for store listings; text is a legal-review item (§16) |
| 404 | `*` | "This page wilted" + home link |

### 12.2 Landing sections (in order)

1. **Hero** — H1: "Know your plants at a glance". Sub: species-level ID + care
   guidance. CTAs: **App Store + Google Play badges** (primary) +
   "Try it in your browser" (secondary → web app).
2. **How it works** — 3 steps: *Snap* → *Identify* → *Care* (one line each).
3. **Features** — species-level identification (10,000+ species via PlantNet); works
   on web, Android, and iOS; free account with 5 scans a month; curated houseplant
   care guide (50+ plants).
4. **Screenshots** — app captures (post hi-fi, §11.4) with captions.
5. **FAQ** — How accurate is it? / What happens to my photo? / What's free? / Do I
   need an account? / Which platforms?
6. **Footer** — privacy policy, contact email, © + "Identification by PlantNet"
   attribution.

Copy rules per §11. Free tier only in v1 marketing (premium = "coming soon", §4.11).

### 12.3 SEO spec

- `<title>` / meta description per page; canonical URL; Open Graph + Twitter cards;
  OG image 1200×630.
- `sitemap.xml`, `robots.txt` (allow all).
- JSON-LD: `WebApplication` (hero) + `FAQPage` (FAQ section).
- Performance: LCP < 2.5 s (Astro static, zero JS by default; images AVIF/WebP with
  explicit width/height).
- Analytics: self-hosted **Plausible** (one instance shared with the web app);
  events: `signup_click`, `store_click`, `web_app_click`.

## 13. Deployment & environments

### 13.1 Repo & tooling (future `peony-app` repo)

```
peony-app/
├── app/                  # Expo Router routes (§3.3)
├── src/
│   ├── api/              # generated types + fetch wrapper + credential adapter
│   ├── components/       # §5 inventory
│   ├── features/         # per-screen modules
│   ├── lib/              # auth module, image pipeline, quota, query client
│   └── theme/            # tokens.json → RN theme
├── marketing/            # Astro site (§12)
├── assets/
├── app.config.ts         # EXPO_PUBLIC_API_URL, scheme, web baseUrl
├── eas.json              # dev / preview / production
├── metro.config.js       # dev proxy: /api → http://localhost:8080 (web)
└── tsconfig.json         # strict
```

Stack: Expo (latest SDK) + TypeScript strict + Expo Router + React Native Web;
TanStack Query v5; expo-image-picker; expo-image-manipulator; expo-secure-store;
openapi-typescript (types generated from the backend spec — the backend's pending
"RN client generation" follow-up is absorbed here).

### 13.2 Environments

| Env | API | Web app | Marketing |
|---|---|---|---|
| dev | `docker compose up` → `localhost:8080` | Expo dev server + Metro `/api` proxy; Android emulator: `10.0.2.2:8080` | `astro dev` |
| prod | Docker (existing compose) + CORS allowlist (§14) | `expo export` → **EAS Hosting** at `app.<domain>` (or `<domain>/app`) | static host (Cloudflare Pages / Netlify) at `<domain>` |

- Domain: **not owned yet** (§16). Placeholder `peony.example.com` in config until it is.
- App env vars: `EXPO_PUBLIC_API_URL` (defaults to same-origin `/api/v1`), Plausible endpoint.
- Backend caveat: `plant-images/` has **no volume mount** in docker-compose —
  covered by §14 item 5.

### 13.3 Store-submission checklist

- [ ] App Store Connect + Google Play Console accounts
- [ ] App name **Peony**, bundle id `com.dendi.peony` (org per backend package `com.dendi`)
- [ ] Privacy policy URL (marketing `/privacy`)
- [ ] Icon 1024 px; screenshot sets (iOS 6.9" / 6.5"; Play phone + large phone);
      listing copy + short description
- [ ] Content-rating questionnaire (expected: everyone); support email
- [ ] No IAP in v1 → declare "no in-app purchases"
- [ ] TestFlight / internal-testing tracks with EAS preview builds
- [ ] Universal / deep links: **v2** (§15)

## 14. Backend requirements (what the UI depends on)

| # | Requirement | Priority | Unblocks |
|---|---|---|---|
| 1 | **JWT migration** — **shipped** (spec #17 / ADR-005): sign-up/sign-in return `{token, refreshToken, profile}`; protected routes accept `Authorization: Bearer`; `POST /api/v1/auth/refresh` rotates single-use; session cookies retired | Done | all auth flows (§8) |
| 2 | **CORS allowlist** for the web-app origin (safe with Bearer auth) | P0 | web on its own host (§13.2) |
| 3 | **`GET /api/v1/me/plants`** — user-linked plants: shared plant fields + `nickname`, `lastWateredAt`, `nextWaterDueAt`, `locations[]` | P0 | My plants (§4.8) |
| 4 | **`GET /api/v1/me/houses`** — caller's houses with nested rooms | P1 | location-editor pickers (§4.10); until it ships the client uses create-new + locally remembered ids |
| 5 | **Catalog seed (backend issue #9) + persistent volume for `plant-images`** in docker-compose | P1 | catalog, detail pages, non-404 thumbnails |
| 6 | Raise multipart `max-file-size` 1 MB → 10 MB (safety valve; client already compresses to < 900 KB) | P2 | headroom for future higher-res uploads |
| 7 | `POST /api/v1/auth/signout` (revoke the caller's refresh token) | P2 | real sign-out; local discard is sufficient until it ships |

> Items 3–4 were added because per-user screens entered v1 (§4.8, §4.10): the
> current API has no way to list a user's plants or houses.

## 15. Out of scope (v1)

- Watering reminders / schedule UI (DB fields exist, no endpoints)
- Any **delete** actions (no DELETE endpoints)
- Multi-device sync, advanced analytics (capability flags only — nothing implemented behind them)
- Payments / checkout (upgrade endpoint exists, unwired — "coming soon")
- Push notifications, offline mode, background processing
- i18n (English only; architecture-ready)
- Deep links / universal links
- Guest / anonymous mode
- Gallery image upload (serving only; backend's "future upload flow")

## 16. Open items

| Item | Owner | Blocks |
|---|---|---|
| Final visual system (§11.4 workflow) | design experiments | hi-fi screens, store screenshots |
| Domain + hosting accounts | product | prod env (§13.2), store listings |
| Privacy policy text (legal review) | product + legal | store listings, sign-up link |
| Confidence-band thresholds (0.5 / 0.3) | product, after first real data | §4.5 copy variants |
| Production token lifetimes — defaults shipped (1 h access / 30 d refresh); set final values within the 1–5 h band via `JWT_ACCESS_TOKEN_TTL` / `JWT_REFRESH_TOKEN_TTL` | backend / ops | §8 |
| App name / bundle id confirmation (`com.dendi.peony`) | product | store accounts |
| `PLANTNET_API_KEY` for prod (free public tier = shared upstream 5 req/min) | backend / ops | production rate-limit headroom |

## Appendix A — Decision log (grill session, 2026-10-22)

| # | Decision |
|---|---|
| 1 | Public product: real marketing page + app store listings; marketing covers the free tier only |
| 2 | Frontend in its own repo (`peony-app`, not yet created); doc drafted in the backend repo, moves at creation |
| 3 | Expo + Expo Router + React Native Web, single codebase |
| 4 | Marketing = separate Astro static site (SEO is a v1 target); web app on subpath/subdomain |
| 5 | Visuals via agentic-tool experimentation; doc ships a design brief, final system TBD |
| 6 | "Stateless" = plant *information* is shared; per-user *data* (plants, notes, locations) is in v1 |
| 7 | Camera + gallery only; no push/offline in v1 |
| 8 | Build order web → Android → iOS |
| 9 | JWT target: 1–5 h access (default 1 h) + refresh flow (30 d, rotation); SecureStore/localStorage |
| 10 | Tier-aware UI everywhere; upgrade CTA = "coming soon" (endpoint unwired) |
| 11 | Confidence bands 0.5 / 0.3 (tunable); patient loader with cancel; local quota counter |
| 12 | Client-side compression < 900 KB; multipart `file` part only; 180 s request timeout |
| 13 | Backend requirements: JWT, CORS, `/me/plants`, `/me/houses`, seed + image volume; optional multipart cap + signout |
| 14 | English-only v1; self-hosted Plausible on web surfaces |
| 15 | Doc lives at `docs/design/ui-design.md` (backend repo) until the app repo exists |

