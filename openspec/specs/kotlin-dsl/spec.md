# Kotlin DSL Specification

## Purpose

`arete-ktx` provides an idiomatic Kotlin DSL for writing arete specs, as an alternative to the
Java annotation API. A spec is a class extending `Spec` with the definitions in a constructor
lambda (`class BookingProcessSpec : Spec({ spec("Booking Process Spec") { ... } })`), executed by
a custom JUnit Platform `TestEngine` (`arete-ktx`) that emits the same descriptor tree and arete
report as the Java API.

## Requirements

### Requirement: Full arete feature coverage in the DSL

The DSL SHALL cover all arete authoring styles and metadata: Gherkin (`spec`/`feature`/`scenario`/
`given`/`when`/`then`), descriptive (`describe`/`itShould`), journeys (`journey`/
`variableJourney`/`step` with phase and variant), examples (`examples`/`exampleGrid`/`exampleCsv`
with typed `given`/`when`/`then`/`expect` parameters), documentation (`doc(...)` with narrative,
PlantUML, images, attachments, refs, seeAlso, tags), step-level (`attachToStep`) and
narrative-level (`attachToNarrative`) dynamic attachments, screenshots (`screenshots`), and
`disabled(reason, hiddenIfDisabled = ...)` on every container scope.

#### Scenario: Kotlin reference specs produce the Java report
- **WHEN** the `travel_agency_ktx` Kotlin specs in `arete-gradle-test/kotlin-specs` are run
- **THEN** the arete HTML report is identical in structure to the one produced by the Java
  `travel_agency` specs (same spec pages, narratives, diagrams, refs, tags, attachments)

### Requirement: Descriptor tree mirrors the Java @Nested hierarchy

Each DSL scope SHALL emit its own `TestDescriptor` (spec/feature/scenario/describe/journey/steps),
so the IDE run tree and the report have the same granularity as Java.

#### Scenario: Run tree shows nested structure
- **WHEN** a Kotlin spec is executed
- **THEN** the run tree contains one node per DSL scope, with the same display names as Java

### Requirement: Known limitation — whole-spec execution only

Unlike Java `@Nested` classes, individual DSL scopes SHALL NOT be executable on their own:
Gradle `--tests` filters at the spec-class level and the IDE runs whole spec classes (a no-arg
`@Test` marker on `Spec` provides the class-level gutter icon). This limitation SHALL be
documented in `Readme.md` ("Known Limitations").

#### Scenario: Single scenario cannot be run via Gradle
- **WHEN** `./gradlew test --tests "...BookingProcessSpec.*Successful Booking*"` is executed
- **THEN** the whole spec class runs (scenario-level filtering has no effect)

### Requirement: DSL scoping prevents invalid nesting

Builder methods SHALL only be callable where semantically valid (`given`/`when`/`then` inside
`scenario`, `scenario` inside `spec`/`feature`, `doc` in containers, …), enforced with a
`@DslMarker`.

#### Scenario: scenario inside given is a compile error
- **WHEN** `scenario(...)` is called inside a `given { }` block
- **THEN** the Kotlin compiler rejects it

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
