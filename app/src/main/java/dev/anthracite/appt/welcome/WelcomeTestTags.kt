package dev.anthracite.appt.welcome

/**
 * Stable test tags for the Welcome surface.
 *
 * Kept in their own file so accessibility assertions do not depend on copy, and
 * so the file name says exactly what it holds — detekt's
 * MatchingDeclarationName wants a file with a single top-level declaration to be
 * named after it. Both the JVM and the instrumented Welcome test resolve these
 * by package, so this move changes no import.
 */
object WelcomeTestTags {
    const val VALUE_PROPOSITION = "welcome.valueProposition"
    const val REASSURANCE = "welcome.reassurance"
    const val MASCOT = "welcome.mascot"
    const val PRIMARY_ACTION = "welcome.primaryAction"
}
