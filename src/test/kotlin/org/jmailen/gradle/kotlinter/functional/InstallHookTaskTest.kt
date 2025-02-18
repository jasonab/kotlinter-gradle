package org.jmailen.gradle.kotlinter.functional

import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE
import org.jmailen.gradle.kotlinter.functional.utils.resolve
import org.jmailen.gradle.kotlinter.functional.utils.settingsFile
import org.jmailen.gradle.kotlinter.tasks.InstallHookTask
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

abstract class InstallHookTaskTest(
    private val taskName: String,
    private val hookFile: String,
) : WithGradleTest.Kotlin() {
    private lateinit var projectRoot: File

    @BeforeEach
    fun setup() {
        projectRoot = testProjectDir.apply {
            resolve("settings.gradle") { writeText(settingsFile) }
            resolve("build.gradle") {
                writeText(
                    """
                        plugins {
                            id("kotlin")
                            id("org.jmailen.kotlinter")
                        }
                    """.trimIndent(),
                )
            }

            build("wrapper")
        }
    }

    @Test
    fun `installs hook in project without hook directory`() {
        File(testProjectDir, ".git").apply { mkdir() }

        build(taskName).apply {
            assertEquals(SUCCESS, task(":$taskName")?.outcome)
            testProjectDir.apply {
                resolve(".git/hooks/$hookFile") {
                    assertTrue(readText().contains("${'$'}GRADLEW formatKotlin"))
                    assertTrue(canExecute())
                }
            }
        }
    }

    @Test
    fun `installs hook in project with existing hook`() {
        val existingHook =
            """
                #!/bin/bash
                This is some existing hook
            """.trimIndent()
        File(testProjectDir, ".git/hooks").apply { mkdirs() }
        File(testProjectDir, ".git/hooks/$hookFile").apply {
            writeText(existingHook)
        }

        build(taskName).apply {
            assertEquals(SUCCESS, task(":$taskName")?.outcome)
            testProjectDir.apply {
                resolve(".git/hooks/$hookFile") {
                    val hookContents = readText()
                    assertTrue(hookContents.startsWith(existingHook))
                    assertTrue(hookContents.contains("${'$'}GRADLEW formatKotlin"))
                }
            }
        }
    }

    @Test
    fun `updates previously installed kotlinter hook`() {
        val placeholder = "Not actually the hook, just a placeholder"
        File(testProjectDir, ".git/hooks").apply { mkdirs() }
        File(testProjectDir, ".git/hooks/$hookFile").apply {
            writeText(
                """
                ${InstallHookTask.startHook}
                $placeholder
                ${InstallHookTask.endHook}
                """.trimIndent(),
            )
        }

        build(taskName).apply {
            assertEquals(SUCCESS, task(":$taskName")?.outcome)
            testProjectDir.apply {
                resolve(".git/hooks/$hookFile") {
                    val hookContents = readText()
                    assertTrue(hookContents.contains("${'$'}GRADLEW formatKotlin"))
                    assertFalse(hookContents.contains(placeholder))
                }
            }
        }
    }

    @Test
    fun `up-to-date when after hook installed`() {
        File(testProjectDir, ".git").apply { mkdir() }
        lateinit var hookContent: String
        build(taskName).apply {
            assertEquals(SUCCESS, task(":$taskName")?.outcome)
            testProjectDir.apply {
                resolve(".git/hooks/$hookFile") {
                    hookContent = readText()
                    println(hookContent)
                    assertTrue(hookContent.contains("${'$'}GRADLEW formatKotlin"))
                    assertTrue(canExecute())
                }
            }
        }

        build(taskName).apply {
            assertEquals(UP_TO_DATE, task(":$taskName")?.outcome)
            testProjectDir.apply {
                resolve(".git/hooks/$hookFile") {
                    assertEquals(hookContent, readText())
                }
            }
        }
    }
}

class InstallPrePushHookTaskTest : InstallHookTaskTest("installKotlinterPrePushHook", "pre-push")
