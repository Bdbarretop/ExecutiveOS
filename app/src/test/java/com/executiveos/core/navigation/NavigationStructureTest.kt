package com.executiveos.core.navigation

import com.executiveos.R
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationStructureTest {
    @Test
    fun bottomBarHasFiveSlotsEndingInMore() {
        assertEquals(5, TopLevelDestination.entries.size)
        assertEquals(TopLevelDestination.MORE, TopLevelDestination.entries.last())
    }

    @Test
    fun theEightSectionsOfTheBriefAreReachableInOrderWithoutRepetition() {
        val sectionLabels = (TopLevelDestination.entries - TopLevelDestination.MORE)
            .map { it.labelRes } + MoreDestination.entries.map { it.labelRes }

        val expected = listOf(
            R.string.section_home,
            R.string.section_inbox,
            R.string.section_tasks,
            R.string.section_projects,
            R.string.section_planning,
            R.string.section_learning,
            R.string.section_reviews,
            R.string.section_settings,
        )
        assertEquals(expected, sectionLabels)
    }

    @Test
    fun everyDestinationHasItsOwnRoute() {
        val routes = TopLevelDestination.entries.map { it.route } +
            MoreDestination.entries.map { it.route }

        assertEquals(routes.size, routes.toSet().size)
    }
}
