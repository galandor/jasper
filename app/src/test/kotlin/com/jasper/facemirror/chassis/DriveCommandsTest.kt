package com.jasper.facemirror.chassis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveCommandsTest {

    @Test
    fun unnamedGoForwardIsADriveCommand() {
        assertEquals(DriveAction.FORWARD, DriveCommands.parseAny(listOf("едь вперед")))
        assertEquals(DriveAction.FORWARD, DriveCommands.parseAny(listOf("едь вперёд")))
        assertEquals(DriveAction.FORWARD, DriveCommands.parseAny(listOf("едь")))
        assertEquals(DriveAction.FORWARD, DriveCommands.parseAny(listOf("едем вперед")))
        assertEquals(DriveAction.FORWARD, DriveCommands.parseAny(listOf("поехали")))
        assertEquals(DriveAction.FORWARD, DriveCommands.parseAny(listOf("вперед")))
    }

    @Test
    fun namedGoForwardStillWorks() {
        assertEquals(DriveAction.FORWARD, DriveCommands.parseAny(listOf("джаспер едь вперед")))
        assertEquals(DriveAction.FORWARD, DriveCommands.parseAny(listOf("джазпер вперед")))
        assertEquals(DriveAction.FORWARD, DriveCommands.parseAny(listOf("эй джаспер поехали")))
    }

    @Test
    fun chatAboutGamesIsNotADriveCommand() {
        assertNull(DriveCommands.parseAny(listOf("давай поиграем в слова")))
        assertNull(DriveCommands.parseAny(listOf("давай в слова")))
        assertNull(DriveCommands.parseAny(listOf("привет")))
        assertNull(DriveCommands.parseAny(listOf("я хочу ехать")))
    }

    @Test
    fun goBackWinsOverGoWhenBothPresent() {
        assertEquals(DriveAction.BACKWARD, DriveCommands.parseAny(listOf("едь назад")))
        assertEquals(DriveAction.ROTATE_LEFT, DriveCommands.parseAny(listOf("едь налево")))
    }

    @Test
    fun chassisTalkIncludesEd() {
        assertTrue(DriveCommands.isChassisTalk("едь вперед"))
        assertTrue(DriveCommands.isChassisTalk("едь"))
        assertTrue(DriveCommands.isChassisTalk("джаспер едь"))
        assertFalse(DriveCommands.isChassisTalk("давай поиграем в слова"))
    }
}
