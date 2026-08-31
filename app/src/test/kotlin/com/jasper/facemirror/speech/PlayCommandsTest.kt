package com.jasper.facemirror.speech

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayCommandsTest {

    @Test
    fun startsOnlyOnDavayIgrat() {
        assertTrue(PlayCommands.isStart("Давай играть"))
        assertTrue(PlayCommands.isStart("давай играть!"))
        assertTrue(PlayCommands.isStart("давай играть в слова"))
        assertFalse(PlayCommands.isStart("давай поиграем"))
        assertFalse(PlayCommands.isStart("давай в слова"))
        assertFalse(PlayCommands.isStart("привет"))
        assertFalse(PlayCommands.isStart("сыграем?"))
        assertFalse(PlayCommands.isStart("стоп"))
    }

    @Test
    fun detectsGameInvitesFromJasper() {
        assertTrue(PlayCommands.isInvite("Сыграем в слова?"))
        assertTrue(PlayCommands.isInvite("Хочешь поиграть?"))
        assertTrue(PlayCommands.isInvite("Во что сыграем?"))
        assertFalse(PlayCommands.isInvite("Привет!"))
        assertFalse(PlayCommands.isInvite("Я рад тебя видеть."))
    }
}
