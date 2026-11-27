# Delta: kotlin-dsl

## ADDED Requirements

### Requirement: `ref(...)` supports the `project` attribute of `@Ref`

The plain-ref DSL function SHALL accept an optional `project` parameter
(`ref(value: String, url: String = "", project: String = "")`) and the materialized
`@Ref` / `@Refs` annotation proxies SHALL expose it via `Ref.project()`, mirroring the
Java annotation API. The default SHALL be `""` (no explicit grouping).

#### Scenario: Project reaches the single `@Ref` proxy
- **WHEN** a spec declares `refs = listOf(ref("5", url = "https://github.com/mictaege/arete/issues/", project = "arete"))`
- **THEN** the emitted `@Ref` annotation's `project()` returns `"arete"`

#### Scenario: Project reaches every entry of the `@Refs` container
- **WHEN** a spec declares `refs = listOf(ref("5", url = "...", project = "arete"), ref("6", url = "..."))`
- **THEN** the emitted `@Refs` container holds two `@Ref` annotations with `project()`
  `"arete"` and `""` respectively

#### Scenario: Default project is blank
- **WHEN** `ref("16", url = "https://github.com/mictaege/arete-gradle/issues/")` is used without `project`
- **THEN** the emitted `@Ref` annotation's `project()` returns `""`
