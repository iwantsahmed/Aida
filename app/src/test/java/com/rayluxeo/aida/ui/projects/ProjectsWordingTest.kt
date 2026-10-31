package com.rayluxeo.aida.ui.projects

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.model.ProjectFilter
import com.rayluxeo.aida.domain.model.ProjectStatus
import org.junit.Test

/**
 * The Projects phrasebook is the one place the screen gets its words, so a missed translation here
 * is not a red build — it is an English label sitting in a Bengali sheet, which nobody notices
 * until Abu does.
 *
 * Every assertion walks all of the members rather than sampling one: the fault this guards against
 * is a line pasted across from English and never translated, or a member added in one language and
 * forgotten in the other. The filter and status labels are walked across the whole enum, so a new
 * status that is left unworded is caught here.
 */
class ProjectsWordingTest {

    private val english = projectsWording(SpokenLanguage.ENGLISH)
    private val bengali = projectsWording(SpokenLanguage.BENGALI)

    @Test
    fun `each language resolves to its own phrasebook`() {
        assertThat(english).isSameInstanceAs(EnglishProjectsWording)
        assertThat(bengali).isSameInstanceAs(BengaliProjectsWording)
    }

    @Test
    fun `every word is written in both languages`() {
        listOf(english, bengali).forEach { words ->
            words.everything().forEach { line ->
                assertThat(line).isNotEmpty()
            }
        }
    }

    @Test
    fun `no line was left in English on the Bengali side`() {
        english.everything().zip(bengali.everything()).forEach { (englishLine, bengaliLine) ->
            assertThat(bengaliLine).isNotEqualTo(englishLine)
        }
    }

    /**
     * Bengali counts in its own digits. A chip that reads "3টি খোলা কাজ" in the middle of a Bengali
     * sheet is the one slip that reads as broken even to someone who cannot read the words around
     * it, so the counted line and the Bengali budget hint carry no Latin digit.
     */
    @Test
    fun `nothing Bengali is written with Latin digits`() {
        val counted = listOf(
            bengali.openTasksCount(3),
            bengali.budgetPlaceholder,
        )
        counted.forEach { line ->
            assertThat(line.none { it in '0'..'9' }).isTrue()
            assertThat(line).isNotEmpty()
        }
    }

    /** Walks every member, so an untranslated one cannot hide between two that are translated. */
    private fun ProjectsWording.everything(): List<String> = buildList {
        add(title)
        add(searchPlaceholder)
        add(addProject)
        add(noProjects)
        ProjectFilter.entries.forEach { add(filterLabel(it)) }
        ProjectStatus.entries.forEach { add(statusLabel(it)) }
        add(personal)
        add(clientDetail("Ahmed"))
        add(startedDetail("1 Jan 2026"))
        add(dueDetail("1 Jan 2026"))
        add(budgetDetail("50000"))
        add(descriptionLabel)
        add(openTasksCount(3))
        add(statusSection)
        add(editProjectAction)
        add(deleteProjectAction)
        add(deleteTitle)
        add(deleteMessage("Rayluxeo"))
        add(delete)
        add(cancel)
        add(undo)
        add(projectAdded("Rayluxeo"))
        add(projectMovedToBin)
        add(newProject)
        add(editProject)
        add(nameLabel)
        add(namePlaceholder)
        add(descriptionFieldLabel)
        add(clientSection)
        add(budgetLabel)
        add(budgetPlaceholder)
        add(startedLabel)
        add(dueLabel)
        add(pickDate)
        add(setDate)
        add(saveProject)
    }
}
