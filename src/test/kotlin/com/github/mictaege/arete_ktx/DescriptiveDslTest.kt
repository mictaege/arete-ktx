package com.github.mictaege.arete_ktx

import org.junit.jupiter.api.Test
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder
import org.junit.platform.launcher.core.LauncherFactory
import org.junit.platform.launcher.listeners.SummaryGeneratingListener
import org.junit.platform.engine.discovery.DiscoverySelectors.selectClass

class DescriptiveDslTest {

    companion object {
        val executed = mutableListOf<String>()
    }

    class CalculatorDescriptive : Spec({
        spec("Calculator") {
            describe("a calculator") {
                itShould("add two numbers") { executed.add("add") }
                itShould("subtract two numbers") { executed.add("subtract") }
            }
        }
    })

    @Test
    fun descriptiveSpecRunsExpectations() {
        executed.clear()
        val listener = SummaryGeneratingListener()
        LauncherFactory.openSession().use { session ->
            val request = LauncherDiscoveryRequestBuilder.request()
                .selectors(selectClass(CalculatorDescriptive::class.java))
                .build()
            session.launcher.execute(request, listener)
        }
        assert(listener.summary.testsSucceededCount >= 2) { "expected >= 2 successful, got ${listener.summary.testsSucceededCount}" }
        assert(executed.toSet() == setOf("add", "subtract")) { "missing expectations: $executed" }
    }
}
