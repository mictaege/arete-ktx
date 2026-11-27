# Proposal

## Why

The arete core annotation `@Ref` gained a `project` attribute (default `""`) that names the
reference's grouping key — the arete-gradle report groups the references page by it
(change `refs-report-page` in arete-gradle). The Kotlin DSL in arete-ktx must offer the same
capability: without it, Kotlin specs cannot set `project` and the parity rule of the arete
family (Java/Kotlin feature parity, recorded in the family store) is violated.

## What Changes

- `ref(value, url)` gains an optional `project` parameter: `ref(value: String, url: String = "", project: String = "")`.
- `PlainRef` carries `project`, and the materialized `@Ref` / `@Refs` annotation proxies
  include the `project` attribute.
- The `travel_agency_ktx` Kotlin demo specs in arete-gradle-test are aligned with the Java
  `travel_agency` specs: `GithubRef` meta-annotation gets `project = "arete-gradle"`,
  `BookingProcessSpec` gets refs 14/16/5/6 (5 with `project = "arete"`), and
  `ComplaintMediationSpec` gets ref 4 with `project = "arete"` — so both language variants
  produce the same references page groups.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `kotlin-dsl`: the `doc(refs = ...)` / `ref(...)` DSL SHALL support the `project` attribute,
  mirroring `Ref#project` in the Java annotation API.

## Impact

- `arete-ktx`: `KtxDsl.kt` (`PlainRef`, `ref(...)`, the `@Ref`/`@Refs` proxy attribute maps)
  plus a unit test asserting the `project` attribute reaches the annotation proxy.
- `arete-gradle-test` (kotlin-specs only): demo spec alignment with the Java travel_agency
  specs (no behavior change to the plugin).
- Fulfills the Java/Kotlin parity decision recorded in arete-gradle's `refs-report-page`
  proposal (Impact). Backward compatible: `project` defaults to `""`.
