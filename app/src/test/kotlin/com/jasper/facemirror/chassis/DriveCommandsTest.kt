package com.jasper.facemirror.chassis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveCommandsTest {

    @Test
    fun unnamedMotionIsNotADriveCommand() {
        assertNull(DriveCommands.parseAny(listOf("едь вперед")))
        assertNull(DriveCommands.parseAny(listOf("едь вперёд")))
        assertNull(DriveCommands.parseAny(listOf("едь")))
        assertNull(DriveCommands.parseAny(listOf("едем вперед")))
        assertNull(DriveCommands.parseAny(listOf("поехали")))
        assertNull(DriveCommands.parseAny(listOf("вперед")))
        assertNull(DriveCommands.parseAny(listOf("назад")))
        assertNull(DriveCommands.parseAny(listOf("налево")))
    }

    @Test
    fun namedGoForwardWorksForJasperVariants() {
        assertEquals(DriveAction.FORWARD, DriveCommands.parseAny(listOf("джаспер едь вперед")))
        assertEquals(DriveAction.FORWARD, DriveCommands.parseAny(listOf("джаспер вперед")))
        assertEquals(DriveAction.FORWARD, DriveCommands.parseAny(listOf("жаспер вперед")))
        assertEquals(DriveAction.FORWARD, DriveCommands.parseAny(listOf("аспер вперед")))
        assertEquals(DriveAction.FORWARD, DriveCommands.parseAny(listOf("Аспер вперед")))
        assertEquals(DriveAction.FORWARD, DriveCommands.parseAny(listOf("джазпер вперед")))
        assertEquals(DriveAction.FORWARD, DriveCommands.parseAny(listOf("эй джаспер поехали")))
    }

    @Test
    fun namedGoBackAndTurns() {
        assertEquals(DriveAction.BACKWARD, DriveCommands.parseAny(listOf("джаспер назад")))
        assertEquals(DriveAction.BACKWARD, DriveCommands.parseAny(listOf("аспер назад")))
        assertEquals(DriveAction.BACKWARD, DriveCommands.parseAny(listOf("жаспер назад")))
        assertEquals(DriveAction.ROTATE_LEFT, DriveCommands.parseAny(listOf("джаспер налево")))
        assertEquals(DriveAction.ROTATE_RIGHT, DriveCommands.parseAny(listOf("аспер направо")))
    }

    @Test
    fun stopWorksWithoutTheName() {
        assertEquals(DriveAction.STOP, DriveCommands.parseAny(listOf("стоп")))
        assertEquals(DriveAction.STOP, DriveCommands.parseAny(listOf("стой")))
        assertEquals(DriveAction.STOP, DriveCommands.parseAny(listOf("остановись")))
        assertEquals(DriveAction.STOP, DriveCommands.parseAny(listOf("тормоз")))
        assertEquals(DriveAction.STOP, DriveCommands.parseAny(listOf("джаспер стоп")))
        assertTrue(DriveCommands.containsStopWord("стоп"))
        assertTrue(DriveCommands.containsStopWord("ну стоп"))
        assertFalse(DriveCommands.containsStopWord("вперед"))
    }

    @Test
    fun chatAndGamesAreNotDriveCommands() {
        assertNull(DriveCommands.parseAny(listOf("давай поиграем в слова")))
        assertNull(DriveCommands.parseAny(listOf("давай в слова")))
        assertNull(DriveCommands.parseAny(listOf("привет")))
        assertNull(DriveCommands.parseAny(listOf("я хочу ехать")))
        assertNull(DriveCommands.parseAny(listOf("земля")))
        assertNull(DriveCommands.parseAny(listOf("арбуз")))
    }

    @Test
    fun namedGoBackWinsOverGoWhenBothPresent() {
        assertEquals(DriveAction.BACKWARD, DriveCommands.parseAny(listOf("джаспер едь назад")))
        assertEquals(DriveAction.ROTATE_LEFT, DriveCommands.parseAny(listOf("аспер едь налево")))
    }

    @Test
    fun chassisTalkNeedsTheName() {
        assertFalse(DriveCommands.isChassisTalk("едь вперед"))
        assertFalse(DriveCommands.isChassisTalk("едь"))
        assertFalse(DriveCommands.isChassisTalk("вперед"))
        assertFalse(DriveCommands.isChassisTalk("давай поиграем в слова"))
        assertTrue(DriveCommands.isChassisTalk("джаспер едь"))
        assertTrue(DriveCommands.isChassisTalk("аспер вперед"))
        assertTrue(DriveCommands.isChassisTalk("жаспер назад"))
    }

    @Test
    fun sequenceRequiresNameOnEachMoveButNotOnStop() {
        assertEquals(
            listOf(DriveAction.FORWARD, DriveAction.BACKWARD),
            DriveCommands.parseSequence("джаспер вперед джаспер назад"),
        )
        assertEquals(
            listOf(DriveAction.FORWARD, DriveAction.ROTATE_LEFT),
            DriveCommands.parseSequence("аспер вперед жаспер налево"),
        )
        assertEquals(emptyList<DriveAction>(), DriveCommands.parseSequence("вперед назад"))
        assertEquals(listOf(DriveAction.STOP), DriveCommands.parseSequence("стоп"))
        assertEquals(
            listOf(DriveAction.FORWARD, DriveAction.STOP),
            DriveCommands.parseSequence("джаспер вперед стоп"),
        )
    }

    @Test
    fun nameAndCommandMaySplitAcrossAlternatives() {
        assertEquals(
            DriveAction.FORWARD,
            DriveCommands.parseAny(listOf("джаспер", "вперед")),
        )
        assertNull(DriveCommands.parseAny(listOf("вперед", "поехали")))
    }
}
