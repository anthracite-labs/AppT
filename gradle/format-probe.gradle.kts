import java.util.Base64

/**
 * TEMPORARY probe (remove before completion): applies the pinned Spotless/ktfmt formatter in this
 * run and reports what changed, so the pinned formatter's verdict is available without a local JVM.
 * It lives in its own script so deleting this file leaves no formatting residue elsewhere.
 */
val formatProbeTask =
    tasks.register("formatProbe") {
        group = "verification"
        dependsOn(tasks.named("spotlessApply"))
        doLast {
            val root = rootProject.projectDir
            fun git(vararg args: String): String {
                val process =
                    ProcessBuilder(listOf("git", "--no-pager") + args)
                        .directory(root)
                        .redirectErrorStream(true)
                        .start()
                val text = process.inputStream.bufferedReader().readText()
                process.waitFor()
                return text
            }
            val diff = git("diff", "--unified=3", "--", "*.kt")
            val files = git("diff", "--numstat", "--", "*.kt").lines().filter { it.isNotBlank() }
            println("::warning title=fmt-count::" + files.size + " kotlin file(s) differ")
            Base64.getEncoder()
                .encodeToString(diff.toByteArray())
                .chunked(3000)
                .take(5)
                .forEachIndexed { index, chunk ->
                    println("::warning title=fmt-b64-" + index + "::" + chunk)
                }
            val gradleFiles =
                git("diff", "--numstat", "--", "*.gradle.kts", "*.kts")
                    .lines()
                    .filter { it.isNotBlank() }
            println("::warning title=fmt-kts::" + gradleFiles.joinToString(" | ").take(400))
            gradleFiles.take(2).forEach { line ->
                val path = line.split("\t").getOrNull(2) ?: return@forEach
                git("diff", "--unified=0", "--", path)
                    .lineSequence()
                    .filter { it.startsWith("+") && !it.startsWith("+++") }
                    .take(4)
                    .forEach { text ->
                        println("::warning title=fmt-kts+::" + path + " " + text.trim().take(140))
                    }
            }
            logger.lifecycle("formatProbe: " + files.size + " kotlin file(s) differ")
        }
    }

project(":app").tasks.configureEach {
    if (name == "verifyReleaseEngineeringBoundaries") dependsOn(formatProbeTask)
}
