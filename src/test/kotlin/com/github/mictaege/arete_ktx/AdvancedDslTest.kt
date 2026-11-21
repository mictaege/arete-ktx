package com.github.mictaege.arete_ktx

import org.junit.jupiter.api.Test
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder
import org.junit.platform.launcher.core.LauncherFactory
import org.junit.platform.launcher.listeners.SummaryGeneratingListener
import org.junit.platform.engine.discovery.DiscoverySelectors.selectClass

class AdvancedDslTest {

    companion object {
        val executed = mutableListOf<String>()
    }

    class Advanced : Spec({
        spec("Advanced") {
            scenario("adds two numbers") {
                examples(
                    "adds", "{0} + {1} => {2}",
                    source = {
                        example("{0})", given(3), `when`(4), then(7))
                        example("{0})", given(-3), `when`(4), then(1))
                    }
                ) { row -> executed.add("ex:${row[0]}+${row[1]}=${row[2]}") }
            }
            variableJourney("complaint") {
                step() { executed.add("offer") }
                step(variant = "Justified") { executed.add("complain-justified") }
                step(variant = "Unjustified") { executed.add("complain-unjustified") }
                step() { executed.add("close") }
            }
        }
    })

    @Test
    fun examplesAndVariableJourneyRun() {
        executed.clear()
        val listener = SummaryGeneratingListener()
        LauncherFactory.openSession().use { session ->
            val request = LauncherDiscoveryRequestBuilder.request()
                .selectors(selectClass(Advanced::class.java))
                .build()
            session.launcher.execute(request, listener)
        }
        val s = listener.summary
        assert(s.testsSucceededCount >= 6) { "expected >= 6 successful, got ${s.testsSucceededCount} (failures: ${s.testsFailedCount})" }
        assert(executed.count { it.startsWith("ex:") } == 2) { "examples missing: $executed" }
        assert(executed.contains("complain-justified") && executed.contains("complain-unjustified")) { "variants missing: $executed" }
    }
}

class GridCsvDslTest {
    class Grid : Spec({
        spec("Grid") {
            scenario("availability") {
                exampleGrid("Availability matrix:", arrayOf("From", "To", "Available?"), source = {
                    row(given("2026-01-01"), given("2026-01-05"), then("true"))
                    row(given("2026-01-06"), given("2026-01-10"), then("false"))
                }) { row ->
                    assert(row.size == 3)
                }
                exampleCsv(
                    name = "CSV matrix:",
                    columns = arrayOf("From", "To", "Available?"),
                    csvData = """2026-01-01,2026-01-05,true
2026-01-06,2026-01-10,false"""
                ) { row ->
                    assert(row.size == 3)
                }
            }
        }
    })

    @org.junit.jupiter.api.Test
    fun gridAndCsvRun() {
        val listener = org.junit.platform.launcher.listeners.SummaryGeneratingListener()
        org.junit.platform.launcher.core.LauncherFactory.openSession().use { session ->
            val request = org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder.request()
                .selectors(org.junit.platform.engine.discovery.DiscoverySelectors.selectClass(Grid::class.java))
                .build()
            session.launcher.execute(request, listener)
        }
        assert(listener.summary.testsSucceededCount >= 4) { "expected >= 4 successful, got ${listener.summary.testsSucceededCount}" }
    }
}
