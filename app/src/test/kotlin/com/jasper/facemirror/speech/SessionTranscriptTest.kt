package com.jasper.facemirror.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTranscriptTest {

    @Test
    fun keepsUserAndJasperTurnsInOrder() {
        val session = SessionTranscript()
        session.addUser("давай в слова")
        session.addJasper("Я загадал арбуз!")
        session.addUser("земля")

        val snapshot = session.snapshot()
        assertEquals(3, snapshot.size)
        assertFalse(snapshot[0].fromJasper)
        assertEquals("давай в слова", snapshot[0].text)
        assertTrue(snapshot[1].fromJasper)
        assertEquals("Я загадал арбуз!", snapshot[1].text)
        assertEquals("земля", snapshot[2].text)
    }

    @Test
    fun dropsOldestTurnsPastTheCap() {
        val session = SessionTranscript(maxTurns = 4)
        repeat(3) { index ->
            session.addUser("u$index")
            session.addJasper("j$index")
        }

        val snapshot = session.snapshot()
        assertEquals(4, snapshot.size)
        assertEquals("u1", snapshot.first().text)
        assertEquals("j2", snapshot.last().text)
    }

    @Test
    fun ignoresBlankLines() {
        val session = SessionTranscript()
        session.addUser("   ")
        session.addJasper("")
        assertEquals(0, session.size)
    }

    @Test
    fun clearDropsTheGameTurns() {
        val session = SessionTranscript()
        session.addUser("давай в слова")
        session.addJasper("Назови слово на А.")
        session.clear()
        assertEquals(0, session.size)
        assertTrue(session.snapshot().isEmpty())
    }
}

class JasperLlmPromptTest {

    @Test
    fun emptySessionHasNoTranscriptBlock() {
        val prompt = JasperLlmPrompt.build("привет")
        assertFalse(prompt.contains("This session so far"))
        assertTrue(prompt.contains("User said: \"привет\""))
        assertTrue(prompt.contains("Games are OFF"))
        assertTrue(prompt.contains("Давай играть"))
        assertTrue(prompt.contains("Car commands always start with the name"))
        assertTrue(prompt.contains("аспер"))
        assertFalse(prompt.contains("1) Слова"))
        assertFalse(prompt.contains("Start a game only if the user asks to play"))
    }

    @Test
    fun chatModeDoesNotListGames() {
        val prompt = JasperLlmPrompt.build("что умеешь")
        assertTrue(prompt.contains("Games are OFF"))
        assertFalse(prompt.contains("1) Слова"))
        assertFalse(prompt.contains("2) Угадай слово"))
        assertFalse(prompt.contains("Данетки"))
    }

    @Test
    fun promptListsOnlyTheFourGamesWhenGameIsOn() {
        val prompt = JasperLlmPrompt.build("давай играть", gameActive = true)
        assertTrue(prompt.contains("5-7 years old named Jasper"))
        assertTrue(prompt.contains("A game is ON"))
        assertTrue(prompt.contains("1) Слова"))
        assertTrue(prompt.contains("2) Угадай слово"))
        assertTrue(prompt.contains("3) Загадки"))
        assertTrue(prompt.contains("4) Данетки"))
        assertTrue(prompt.contains("ONLY these four"))
        assertTrue(prompt.contains("20 attempts"))
        assertTrue(prompt.contains("5 attempts"))
        assertTrue(prompt.contains("сдаюсь"))
        assertTrue(prompt.contains("expression sad"))
        assertFalse(prompt.contains("Games are OFF"))
    }

    @Test
    fun sessionIncludesJasperReplySoGameCanContinue() {
        val session = listOf(
            ChatTurn(fromJasper = false, text = "давай играть"),
            ChatTurn(fromJasper = true, text = "Давай в слова! Назови на А."),
        )
        val prompt = JasperLlmPrompt.build("арбуз", session, gameActive = true)
        assertTrue(prompt.contains("User: давай играть"))
        assertTrue(prompt.contains("Jasper: Давай в слова! Назови на А."))
        assertTrue(prompt.contains("User said: \"арбуз\""))
        assertTrue(prompt.contains("1) Слова"))
        assertFalse(prompt.contains("Recent user lines"))
    }
}
