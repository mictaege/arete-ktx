# Arete KTX

Idiomatic Kotlin DSL for [Arete](https://github.com/mictaege/arete) — the JUnit 6 BDD extension.
See the [Arete Readme](https://github.com/mictaege/arete#readme) for the arete concepts and
semantics (specs, scenarios, journeys, examples, narratives, tags, refs) in general; this Readme
only shows how to express them with the Kotlin DSL.

[![Maven Central](https://img.shields.io/maven-central/v/io.github.mictaege/arete-ktx.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22io.github.mictaege%22%20AND%20a:%22arete-ktx")
[![Apache License 2.0](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0.html)

## Known Limitations

In contrast to the Java annotation API, only **whole specs** can be run — both in the IDE and with
Gradle `--tests`. Each DSL scope (`spec`, `feature`, `scenario`, `describe`, `journey`) does emit
its own `TestDescriptor`, so the run tree and the arete report have the same granularity as Java —
but unlike Java `@Nested` classes (which are real classes and thus selectable via `--tests` or the
IDE), the Kotlin DSL scopes live in a single class constructor lambda. Gradle `--tests` therefore
filters at the spec-class level only, and the IDE (even with the `@Test` marker on `Spec`) can only
run the whole spec class; individual scenarios/features/journeys appear in the run tree but cannot
be executed individually.

## Usage

Add the dependency:

```kotlin
dependencies {
    testImplementation("io.github.mictaege:arete-ktx:2026.8")
}
```

Define a spec class extending `Spec` and pass the spec definitions in the constructor lambda:

```kotlin
class CalculatorSpec : Spec({
    spec("Calculator") {
        // ...
    }
})
```

Documentation (narrative, PlantUML, images, attachments, refs, tags) goes into `doc(...)` inside
the corresponding scope:

```kotlin
spec("Booking Process") {
    doc(
        narrative = "As a traveler I want to book a room ...",
        tags = listOf(ActorTraveller::class, EntityRoom::class)
    )
    // ...
}
```

## Styles

### Gherkin

```kotlin
class CalculatorGherkinSpec : Spec({
    spec("Calculator") {
        feature("Addition") {
            scenario("Should add two numbers") {
                given { a = 5; b = 10 }
                `when` { c = calculator.add(a, b) }
                then { assertThat(c, `is`(15)) }
            }
        }
    }
})
```

### Descriptive

```kotlin
class CalculatorDescriptiveSpec : Spec({
    spec("Calculator") {
        describe("A Calculator") {
            itShould("add two numbers") {
                assertThat(calculator.add(5, 10), `is`(15))
            }
            itShould("subtract two numbers") {
                assertThat(calculator.subtract(10, 5), `is`(5))
            }
        }
    }
})
```

### Journey / Variable Journey

```kotlin
class ComplaintMediationSpec : Spec({
    spec("Complaint Mediation") {
        variableJourney("Complaint Mediation") {
            step("an accommodation offers some rooms", phase = Phase.BOOKING) { }
            step("after arrival the traveller complains", phase = Phase.COMPLAINING, variant = Variant.JUSTIFIED) { }
            step("after arrival the traveller complains", phase = Phase.COMPLAINING, variant = Variant.UNJUSTIFIED) { }
            step("then the accommodation is told to refund the traveller", phase = Phase.MEDIATION, variant = Variant.JUSTIFIED) { }
            step("then the traveller is told that his complaint has been rejected", phase = Phase.MEDIATION, variant = Variant.UNJUSTIFIED) { }
        }
    }
})
```

## Examples

```kotlin
scenario("Calculate total costs") {
    examples(
        name = "Calculate total costs for different number of days and prices per night",
        pattern = "{0} days, {1} € per night => Total: {2} €",
        source = {
            example("Single day", given(1), given(45.33), then(45.33))
            example("Whole week", given(7), given(74.66), then(522.62))
        }
    ) { row ->
        val days = row[1] as Int
        val pricePerNight = row[2] as Double
        val expectedTotal = row[3] as Double
        assertThat(days * pricePerNight, `is`(expectedTotal))
    }
}
```

### Grid Examples

```kotlin
scenario("Availability for different booking situations and dates") {
    exampleGrid(
        name = "Availability for different booking situations and dates:",
        columns = arrayOf("Booked Room From", "Booked Room To", "Request From", "Request To", "Room Available?"),
        source = {
            row(
                given(LocalDate.of(2026, 1, 5)),
                given(LocalDate.of(2026, 1, 10)),
                `when`(LocalDate.of(2026, 1, 12)),
                `when`(LocalDate.of(2026, 1, 14)),
                then(true)
            )
            row(
                given(LocalDate.of(2026, 1, 5)),
                given(LocalDate.of(2026, 1, 10)),
                `when`(LocalDate.of(2026, 1, 6)),
                `when`(LocalDate.of(2026, 1, 8)),
                then(false)
            )
        }
    ) { row ->
        // ...
    }
}
```

See [arete-gradle-test](https://github.com/mictaege/arete-gradle-test/tree/master/kotlin-specs)
for full example specs (`travel_agency_ktx`).

## Enable / Disable / Hide

Inside any container scope (`spec`, `feature`, `scenario`, `describe`, `journey`) you can call
`disabled(reason, hiddenIfDisabled = ...)` (like JUnit `@Disabled`; with `hiddenIfDisabled = true`
the element is hidden from the report instead of shown as skipped, like arete's
`@HiddenIfDisabled`):

```kotlin
scenario("Booking Process Interrupted") {
    disabled("Needs refinement")
    // ...
}

scenario("Show Advertisement") {
    disabled("Monetization feature disabled", hiddenIfDisabled = true)
    // ...
}
```

