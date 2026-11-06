# Curated plant dataset

The version-controlled seed dataset for issue #9: 50 common houseplants, fully populated
against the existing `plants` schema, with a citable reference for every toxicity boolean.

- Seed file: [`src/main/resources/dataset/plants-seed.json`](../../src/main/resources/dataset/plants-seed.json)
- Contract test: [`DatasetSeedFileTest`](../../src/test/java/com/dendi/peony/dataset/DatasetSeedFileTest.java)
- Runtime loader and idempotent seeder: owned by T9.3 (`com.dendi.peony.dataset`)

## Purpose

The app ships with a small, curated care catalogue so that a new user gets useful plant
information before any PlantNet scan has ever been recorded. The dataset is not a
replacement for the scan catalogue: it is the day-one seed, and every row is upserted by
`scientificName` so scans and the curated data converge on one species entry.

## Curation rules

These rules are enforced by `DatasetSeedFileTest` and by the T9.3 validator:

1. Exactly 50 entries.
2. Every content field is non-blank: `commonName`, `scientificName`, `family`,
   `lightNeeds`, `waterFrequencyDays`, `humidity`, `tempRange`, `toxicToPets`,
   `toxicToHumans`, `difficulty`, `nativeRegion`, `matureSize`. The curated extras
   `description`, `tags`, `toxicToPetsSource`, and `toxicToHumansSource` are also always
   present.
3. `scientificName` is an accepted binomial (genus + one epithet), without authorship or
   infraspecific rank. Genus-only T0/T4 entries were resolved to the most commonly grown
   species; see the synonym table below.
4. Scientific names are unique after trimming and case-folding.
5. `waterFrequencyDays` is an integer from 1 to 90 and means "water roughly every N days
   in the growing season".
6. `difficulty` is one of `Easy`, `Moderate`, `Hard`.
7. `tags` contains 2-6 lowercase kebab-case tags.
8. Every toxicity boolean carries an `https://` source URL. Every URL in the seed file was
   checked for HTTP 200 on 2026-11-06.
9. Care values are conservative horticultural guidance. Mature size is a range, or
   "varies", where pot culture makes a single number misleading. Ranges describe typical
   indoor plants, not maximum wild size.

## The 50 plants

Toxicity columns reflect the seeded booleans, not the severity: "yes" can mean a mild
irritant, and severity qualifiers are in [Toxicity qualifiers](#toxicity-qualifiers).

| # | Common name | Accepted scientific name | Family | Toxic to pets | Toxic to humans |
|---|-------------|--------------------------|--------|---------------|-----------------|
| 1 | Snake Plant | *Dracaena trifasciata* | Asparagaceae | yes | yes |
| 2 | ZZ Plant | *Zamioculcas zamiifolia* | Araceae | yes | yes |
| 3 | Pothos | *Epipremnum aureum* | Araceae | yes | yes |
| 4 | Spider Plant | *Chlorophytum comosum* | Asparagaceae | no | no |
| 5 | Peace Lily | *Spathiphyllum wallisii* | Araceae | yes | yes |
| 6 | Rubber Plant | *Ficus elastica* | Moraceae | yes | no |
| 7 | Aloe Vera | *Aloe vera* | Asphodelaceae | yes | yes |
| 8 | Monstera | *Monstera deliciosa* | Araceae | yes | yes |
| 9 | Fiddle Leaf Fig | *Ficus lyrata* | Moraceae | yes | yes |
| 10 | Calathea | *Goeppertia orbifolia* | Marantaceae | no | no |
| 11 | Jade Plant | *Crassula ovata* | Crassulaceae | yes | no |
| 12 | Boston Fern | *Nephrolepis exaltata* | Nephrolepidaceae | no | no |
| 13 | English Ivy | *Hedera helix* | Araliaceae | yes | yes |
| 14 | Dracaena | *Dracaena fragrans* | Asparagaceae | yes | yes |
| 15 | Philodendron | *Philodendron hederaceum* | Araceae | yes | yes |
| 16 | Hoya | *Hoya carnosa* | Apocynaceae | no | no |
| 17 | Air Plant | *Tillandsia ionantha* | Bromeliaceae | no | no |
| 18 | Parlor Palm | *Chamaedorea elegans* | Arecaceae | no | yes |
| 19 | Areca Palm | *Dypsis lutescens* | Arecaceae | no | yes |
| 20 | Chinese Money Plant | *Pilea peperomioides* | Urticaceae | no | no |
| 21 | Anthurium | *Anthurium andraeanum* | Araceae | yes | yes |
| 22 | African Violet | *Streptocarpus ionanthus* | Gesneriaceae | no | no |
| 23 | Moth Orchid | *Phalaenopsis amabilis* | Orchidaceae | no | no |
| 24 | Prayer Plant | *Maranta leuconeura* | Marantaceae | no | no |
| 25 | String of Pearls | *Curio rowleyanus* | Asteraceae | yes | yes |
| 26 | String of Hearts | *Ceropegia woodii* | Apocynaceae | no | no |
| 27 | Burro's Tail | *Sedum morganianum* | Crassulaceae | no | no |
| 28 | Echeveria | *Echeveria elegans* | Crassulaceae | no | no |
| 29 | Haworthia | *Haworthiopsis fasciata* | Asphodelaceae | no | no |
| 30 | Prickly Pear | *Opuntia ficus-indica* | Cactaceae | no | no |
| 31 | Christmas Cactus | *Schlumbergera truncata* | Cactaceae | no | no |
| 32 | Ponytail Palm | *Beaucarnea recurvata* | Asparagaceae | no | no |
| 33 | Cast Iron Plant | *Aspidistra elatior* | Asparagaceae | no | no |
| 34 | Bird of Paradise | *Strelitzia nicolai* | Strelitziaceae | yes | yes |
| 35 | Asparagus Fern | *Asparagus setaceus* | Asparagaceae | yes | yes |
| 36 | Maidenhair Fern | *Adiantum raddianum* | Pteridaceae | no | no |
| 37 | Lady Palm | *Rhapis excelsa* | Arecaceae | no | no |
| 38 | Sago Palm | *Cycas revoluta* | Cycadaceae | yes | yes |
| 39 | Yucca | *Yucca gigantea* | Asparagaceae | yes | yes |
| 40 | Croton | *Codiaeum variegatum* | Euphorbiaceae | yes | yes |
| 41 | Schefflera | *Heptapleurum arboricola* | Araliaceae | yes | yes |
| 42 | Dieffenbachia | *Dieffenbachia seguine* | Araceae | yes | yes |
| 43 | Chinese Evergreen | *Aglaonema commutatum* | Araceae | yes | yes |
| 44 | Tradescantia | *Tradescantia zebrina* | Commelinaceae | yes | yes |
| 45 | Begonia | *Begonia rex* | Begoniaceae | yes | yes |
| 46 | Polka Dot Plant | *Hypoestes phyllostachya* | Acanthaceae | no | no |
| 47 | Norfolk Island Pine | *Araucaria heterophylla* | Araucariaceae | no | no |
| 48 | Elephant Ear | *Alocasia macrorrhizos* | Araceae | yes | yes |
| 49 | Lucky Bamboo | *Dracaena sanderiana* | Asparagaceae | yes | yes |
| 50 | Peperomia | *Peperomia obtusifolia* | Piperaceae | no | no |

The first 10 rows are the priority plants from issue #9.

## Taxonomy: T0/T4 names to accepted binomials

The T0 list and the T4 gap analysis used several genus-only or synonym names. The seed
uses the accepted binomial so the catalogue and the PlantNet ledger share one key. The
mapping is:

| T0/T4 name | Seed name | Reason |
|------------|-----------|--------|
| *Sansevieria trifasciata* | *Dracaena trifasciata* | *Sansevieria* is sunk into *Dracaena* |
| *Aloe barbadensis* Miller | *Aloe vera* | *A. barbadensis* is a synonym of *A. vera* |
| *Saintpaulia ionantha* | *Streptocarpus ionanthus* | *Saintpaulia* is sunk into *Streptocarpus* |
| *Calathea* (genus) | *Goeppertia orbifolia* | *Calathea* split; this species is *Goeppertia* |
| *Philodendron* (genus) | *Philodendron hederaceum* | most commonly grown species |
| *Hoya* (genus) | *Hoya carnosa* | most commonly grown species |
| *Tillandsia* (genus) | *Tillandsia ionantha* | most commonly grown species |
| *Anthurium* (genus) | *Anthurium andraeanum* | most commonly grown species |
| *Echeveria* (genus) | *Echeveria elegans* | most commonly grown species |
| *Haworthia* (genus) | *Haworthiopsis fasciata* | *Haworthia* split; this species is *Haworthiopsis* |
| *Opuntia* (genus) | *Opuntia ficus-indica* | most commonly grown species |
| *Yucca* (genus) | *Yucca gigantea* | accepted name for the common indoor yucca |
| *Dieffenbachia* (genus) | *Dieffenbachia seguine* | accepted name for the common dumb cane |
| *Aglaonema* (genus) | *Aglaonema commutatum* | most commonly grown species |
| *Tradescantia* (genus) | *Tradescantia zebrina* | most commonly grown species |
| *Begonia* (genus) | *Begonia rex* | accepted name for Rex begonias; hybrids are sold as *B. rex-cultorum* |
| *Alocasia* (genus) | *Alocasia macrorrhizos* | most commonly grown species |
| *Spathiphyllum* (genus) | *Spathiphyllum wallisii* | most commonly grown species |
| *Phalaenopsis* (genus) | *Phalaenopsis amabilis* | type species and common parent of hybrids |
| *Adiantum* (genus) | *Adiantum raddianum* | most commonly grown species |
| *Schefflera arboricola* | *Heptapleurum arboricola* | *Schefflera* split; this species is *Heptapleurum* |

Taxonomy was cross-checked against Plants of the World Online (POWO,
<https://powo.science.kew.org/>) and Wikidata (<https://www.wikidata.org/>). Where an
authoritative source still accepts a synonym in horticultural use, the seed uses the
name most likely to match PlantNet and records the synonym here.

## Sources and licences

| Source | Used for | Licence / terms |
|--------|----------|-----------------|
| Manual curation for this project | descriptions, tags, light, water, humidity, temperature, difficulty, native region, mature size | original work for this repository |
| [ASPCA Toxic and Non-Toxic Plants](https://www.aspca.org/pet-care/animal-poison-control/toxic-and-non-toxic-plants) | pet toxicity classification | ASPCA website terms; factual classifications referenced, no text copied |
| [Pet Poison Helpline](https://www.petpoisonhelpline.com/) | pet toxicity for String of Pearls and Croton, which have no ASPCA species page | site terms; factual classifications referenced, no text copied |
| [NC State Extension Plant Toolbox](https://plants.ces.ncsu.edu/) | human toxicity classification | NC State Extension; factual classifications referenced, no text copied |
| [Wikipedia](https://www.wikipedia.org/) | per-species fallback where no ASPCA/extension species page exists | text is CC BY-SA 4.0; used as a reference only, no article text copied |
| [POWO](https://powo.science.kew.org/) / [Wikidata](https://www.wikidata.org/) | accepted names, families, native regions | POWO terms; Wikidata CC0 |

The seed file stores only short factual classifications and our own prose. It does not
reproduce source text, so the dataset itself can be licensed with the repository. If the
description text is later reused outside this project, the repository licence applies.

## Seed file schema

```json
{
  "version": 1,
  "plants": [
    {
      "commonName": "Snake Plant",
      "scientificName": "Dracaena trifasciata",
      "family": "Asparagaceae",
      "description": "One curated sentence.",
      "tags": ["low-light", "drought-tolerant"],
      "lightNeeds": "Low to bright indirect light",
      "waterFrequencyDays": 21,
      "humidity": "Low to average",
      "tempRange": "15-29C",
      "toxicToPets": true,
      "toxicToPetsSource": "https://...",
      "toxicToHumans": true,
      "toxicToHumansSource": "https://...",
      "difficulty": "Easy",
      "nativeRegion": "West Africa",
      "matureSize": "0.6-1.2 m indoors"
    }
  ]
}
```

`version` is `1`. The runtime loader and the idempotent upsert are owned by T9.3; the seed
runner is enabled with `--peony.dataset.seed.enabled=true` and documented in
`docs/runbooks/dataset-seed.md`.

## Toxicity qualifiers

The `plants` schema stores toxicity as booleans, so the seed cannot express severity,
plant part, or dose. The booleans mean "a credible source classifies this species as
toxic to that group"; they do not mean "will harm you". These qualifiers are deliberately
kept out of the schema:

- **Calcium oxalate raphides (Araceae).** Pothos, Monstera, Philodendron, Peace Lily,
  Anthurium, Dieffenbachia, Chinese Evergreen, Elephant Ear, and ZZ Plant can cause
  immediate mouth and throat pain, swelling, and drooling. Dieffenbachia is the most
  severe (high severity); most others are low to moderate.
- **Saponins.** Snake Plant, Dracaena, Lucky Bamboo, Yucca, and Asparagus Fern can cause
  gastrointestinal upset if eaten. Effects are usually mild.
- **Euphorbiaceae sap.** Croton sap is a skin and eye irritant; the fruit is toxic.
- **Ficus sap.** Rubber Plant and Fiddle Leaf Fig exude a milky sap that can irritate skin
  and eyes. The seed follows the cited human source for the boolean: the Rubber Plant is
  recorded as not poisonous to humans, with the sap-irritant caveat here.
- **Jade Plant.** ASPCA lists it as toxic to pets; human toxicity is unproven and the NC
  State profile does not flag it, so `toxicToHumans` is false.
- **Aloe Vera.** The clear gel is used topically, but the yellow latex (aloin) is a
  laxative and can cause gastrointestinal upset if eaten.
- **Prickly Pear.** Chemical toxicity is low; the hazard is physical (spines and glochids).
  The boolean records chemical toxicity only.
- **Sago Palm.** Highly toxic to pets and humans, especially the seeds; it is the most
  dangerous plant in this set.
- **Bird of Paradise and Asparagus Fern.** The seeds and berries are the toxic parts; the
  foliage is a minor irritant.
- **Parlor Palm and Areca Palm.** ASPCA lists both as non-toxic to pets, but the NC State
  human-toxicity profiles flag low-severity toxicity (for example, the fruit or sap). The
  booleans follow each cited source. Keep the fruit away from children and pets.
- **Non-toxic does not mean edible.** Plants recorded as non-toxic can still cause
  mechanical irritation, choking, or individual allergic reactions.

## Per-row gaps and source fallbacks

- **ASPCA has no species page for nine entries.** ZZ Plant, Rubber Plant, Fiddle Leaf Fig,
  Air Plant, Chinese Money Plant, String of Hearts, and Maidenhair Fern use the Wikipedia
  species article as the pet-toxicity reference. String of Pearls and Croton use Pet
  Poison Helpline. If a more specific veterinary source appears later, update only the
  source URL; the boolean is already curated.
- **ASPCA pages describe a related species or genus for some entries.** Anthurium uses
  the *A. scherzeranum* page, Bird of Paradise uses *Strelitzia reginae*, Tradescantia
  uses *T. fluminensis*, Schefflera uses the umbrella-tree page, Prickly Pear uses the
  *Opuntia* genus page, Haworthia uses *H. fasciata*, and Jade uses the *Crassula
  argentea* synonym. These are the closest published ASPCA entries; all are the same
  genus or a well-known synonym.
- **Human-toxicity reference for six entries is a genus profile.** Peace Lily, Moth
  Orchid, Prickly Pear, Chinese Evergreen, Begonia, and Elephant Ear use the NC State
  genus page because no species page exists. The genus page carries the human-toxicity
  attribute.
- **Native region is approximate.** For widely cultivated plants the seed gives the
  centre of origin or the broad native range, not an exhaustive distribution.
- **Mature size is indoor guidance.** A range is used where container size and pruning
  dominate the outcome. It is not a maximum height.
- **Watering frequency is a starting point.** It assumes a well-draining potting mix, a
  moderate pot, and average indoor conditions. Light, pot size, and season change it.
- **Cultivar-level toxicity was not modelled.** Many houseplants are sold as cultivars;
  toxicity follows the species.

## Catalogue images are out of scope

Curated catalogue images (`plant_images` position 0 seed images) are **explicitly out of
scope for issue #9** (user decision, 2026-11-06). The PlantNet species fallback added in
issue #45 under [ADR-008](../adr/plant-images-adr.md) remains the only catalogue
species-image source. This closes the ADR-008 question for the seed dataset: no image URLs
or image rows are part of this deliverable. See also
[ADR-007](../adr/catalogue-source-adr.md) for how seeded rows keep `source = SEED`.

## Recognition image cache

Recognition training uses a separate, gitignored image cache built from a committed
manifest. The manifest, fetch script, and documentation are owned by T9.2:

- `dataset/image-cache-manifest.json` (committed)
- `scripts/fetch-recognition-image-cache.py`
- `docs/dataset/image-cache.md`

The cache is for recognition training only and is not the catalogue image source.

## Verification

```sh
./gradlew test --tests 'com.dendi.peony.dataset.DatasetSeedFileTest'
./gradlew checkstyleTest
./scripts/check-doc-links.sh
```

The test reads the seed file from the classpath and fails with a list of every rule
violation, so a malformed dataset cannot be committed silently.
