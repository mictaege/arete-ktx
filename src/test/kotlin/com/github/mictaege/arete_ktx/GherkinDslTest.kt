package com.github.mictaege.arete_ktx

import org.junit.jupiter.api.Test
import org.junit.platform.launcher.core.LauncherFactory
import org.junit.platform.launcher.listeners.SummaryGeneratingListener
import org.junit.platform.engine.discovery.DiscoverySelectors.selectClass
import java.io.PrintWriter

class GherkinDslTest {

    companion object {
        val executed = mutableListOf<String>()
    }

    class Calculator : Spec({
        spec("Calculator") {
            scenario("adds 5 to 10") {
                given { executed.add("given") }
                `when` { executed.add("when") }
                then { executed.add("then") }
            }
        }
    })

    @Test
    fun gherkinSpecRunsStepsInOrder() {
        executed.clear()
        val listener = SummaryGeneratingListener()
        LauncherFactory.openSession().use { session ->
            val request = org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder.request()
                .selectors(selectClass(Calculator::class.java))
                .build()
            session.launcher.execute(request, listener)
        }
        val summary = listener.summary
        summary.printTo(PrintWriter(System.out))
        assert(summary.testsSucceededCount >= 3) { "expected at least 3 successful tests, got ${summary.testsSucceededCount}" }
        assert(executed == listOf("given", "when", "then")) { "steps ran out of order: $executed" }
    }
}
