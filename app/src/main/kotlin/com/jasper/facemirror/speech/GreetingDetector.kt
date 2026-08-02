package com.jasper.facemirror.speech

object GreetingDetector {
    private val helloPattern = Regex("""привет""", RegexOption.IGNORE_CASE)

    fun isHello(text: String): Boolean = helloPattern.containsMatchIn(text.trim())
}
