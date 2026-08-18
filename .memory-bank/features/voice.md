# Голосовая реализация

## Обзор

Голосовой модуль отвечает за:

1. **Распознавание речи** (STT) — непрерывное прослушивание на русском
2. **Диалог** — ответы через Gemini LLM или локальные правила
3. **Голосовой ответ** (TTS) — с эмоциональным pitch/rate и lip sync
4. **Прерывание** — стоп-команды без LLM
5. **Реакции на жесты** — короткие фразы при улыбке пользователя (без LLM)

Текстовый overlay распознанной речи **убран** — на экране только лицо.

---

## Компоненты

### SpeechRecognizerEngine

`speech/SpeechRecognizerEngine.kt`

- Офлайн STT: Vosk `SpeechService` + модель `vosk-model-small-ru-0.22` (~45MB в assets)
- Непрерывный `AudioRecord` 16 kHz / mono — без сессий Google и без `NO_MATCH`
- Partial-результаты идут потоком; команды шасси берутся с partial, чат — с endpoint (`onResult`)
- Модель скачивается Gradle-задачей `unpackVoskModel` при сборке, на устройстве копируется в `filesDir`

**Пауза при TTS:**

```kotlin
pauseListening()   // SpeechService.setPause(true) — микрофон открыт, аудио в распознаватель не идёт
resumeListening()  // reset + setPause(false) через 250 ms
acknowledgePhrase() // сброс recognizedText после обработки
consumeUtteranceAndRestart() // команда с partial: recognizer.reset(), без перезапуска микрофона
```

### ConversationBrain

`speech/ConversationBrain.kt`

- Оркестратор ответа на фразу
- Классификатор команд (`classifyDrive`) — только если regex не взял, но фраза похожа на руление
- Только имя («Джаспер») — без Gemini, ждём команду
- Если Gemini настроен → `LlmConversationResponder`, иначе → `GreetingDetector`
- `cancelPending()` — отмена предыдущего LLM-запроса при новой фразе или прерывании
- Callbacks: `onReply(GreetingReply)` / `onNoReply()` / `onDrive(DriveAction)`

### LlmConversationResponder

`speech/LlmConversationResponder.kt`

- Формирует промпт через `JasperLlmPrompt`
- Парсит JSON-ответ: `should_reply`, `reply`, `expression`, `voice`
- Маппинг в `GreetingReply` (текст + `FaceExpression` + `VoiceEmotion`)

### JasperLlmPrompt

`speech/JasperLlmPrompt.kt`

- Промпт на английском (экономия токенов)
- Персона: любопытный 5-летний ребёнок
- Ответ: только русский, max 12 слов
- Передаёт историю последних фраз пользователя

### GreetingDetector

`speech/GreetingDetector.kt`

Локальный fallback без API. Regex-триггеры:

| Паттерн | Эмоции |
|---------|--------|
| привет, приветик, здравствуй | HAPPY, PLAYFUL |
| рад видеть, скучал, люблю | HAPPY |
| злюсь, бесит, разозли | ANGRY |
| боюсь, страшно, испугал | AFRAID |
| спать, сонн, устал | SLEEPY |
| не хочу видеть, уходи | OFFENDED, SAD |

### InterruptCommands

`speech/InterruptCommands.kt`

Мгновенные команды без LLM: `стоп`, `тише`, `тихо`, `подожди`, `замолчи`, `хватит`, `заткнись`.

→ `interruptJasper()`: stop TTS, cancel LLM, `DialogPhase.INTERRUPTED`.

### JasperVoiceSpeaker

`audio/JasperVoiceSpeaker.kt`

- Android `TextToSpeech`, язык `ru-RU`
- `speakGreeting(reply: GreetingReply)` — pitch/rate из `VoiceEmotion`
- Выбор лучшего русского голоса (`pickRussianVoice`)
- `onSpeakingChanged(Boolean)` — флаг речи для UI
- `onRangeStart` → `onLipPulse(Float)` — синхронизация рта с фонемами
- Fallback-осцилляция рта, если `onRangeStart` не приходит

### JasperSoundPlayer

`audio/JasperSoundPlayer.kt`

- Синтез коротких тонов через `AudioTrack`
- `playGreeting()` — восходящая мелодия (4 ноты)
- В текущем UI **не вызывается** (legacy)

### FaceGestureReactions

`model/FaceGestureReactions.kt`

- `smileReply()` — случайная радостная фраза при улыбке пользователя
- Без LLM, cooldown 10 с

---

## Поток данных

```text
Пользователь говорит
  → Vosk onPartialResult / onResult → partialText / recognizedText
  → команда на partial или handleUserPhrase() на финале
      → InterruptCommands? → interruptJasper()
      → ConversationBrain.respondToPhrase()
          → Gemini (JSON) или GreetingDetector (regex)
          → GreetingReply
      → pauseListening()
      → JasperVoiceSpeaker.speakGreeting()
          → onSpeakingChanged(true)
          → onLipPulse (lip sync)
      → onComplete → resumeListening()
```

### Реакция на улыбку (параллельный поток)

```text
FaceAnalyzer → smile >= 0.52 (6 кадров)
  → FaceGestureReactions.smileReply()
  → respondWithVoice(ignoreCooldown = true)
```

---

## GreetingReply и VoiceEmotion

```kotlin
data class GreetingReply(
    val text: String,
    val voice: VoiceEmotion,      // CARTOON, HAPPY, WARM, PLAYFUL, ...
    val expression: FaceExpression,
)
```

`pitch` и `speechRate` вычисляются из `VoiceEmotion`:

| VoiceEmotion | pitch | rate |
|--------------|-------|------|
| CARTOON | 1.9 | 1.18 |
| HAPPY | 1.35 | 1.12 |
| PLAYFUL | 1.55 | 1.22 |
| SLEEPY | 0.82 | 0.72 |
| ANGRY | 0.78 | 1.05 |

---

## Защита от повторов и тайминги

| Константа | Значение | Назначение |
|-----------|----------|------------|
| `REPLY_COOLDOWN_MS` | 2500 | Минимальный интервал между ответами |
| `EXPRESSION_HOLD_MS` | 5000 | Удержание эмоции после ответа |
| `SMILE_REACTION_COOLDOWN_MS` | 10000 | Cooldown реакции на улыбку |

- `acknowledgePhrase()` — сбрасывает `recognizedText`, чтобы одна фраза не обрабатывалась дважды
- Во время `SPEAKING` входящие фразы отбрасываются (защита от эхо TTS)

---

## Требования

- `RECORD_AUDIO` — микрофон
- `INTERNET` — только Gemini API (опционально); STT офлайн
- `gemini.api.key` в `local.properties` для LLM-ответов

---

## Известные ограничения

- STT паузится на время TTS — barge-in голосом отложен (эхо)
- Первая установка копирует ~45MB модели в filesDir
- Качество TTS зависит от голосов устройства
- Без Gemini API — только локальные regex-реакции

---

## Возможные улучшения

- Barge-in с AEC или tap-to-interrupt
- End-of-turn debounce на partial, если команды в одной фразе режутся рано
- Piper / cloud TTS для мультяшного голоса
- `CommandRouter` для игровых команд («закрой глаза»)
