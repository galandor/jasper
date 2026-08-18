# Jasper — техническая спецификация

**Ветка:** `jasper2`  
**Пакет:** `com.jasper.facemirror`  
**Обновлено:** после спринта 1 (влит в `main`)

---

## Стек

| Слой | Технология |
|------|------------|
| Язык | Kotlin |
| UI | Jetpack Compose, Canvas |
| Камера | CameraX (`ImageAnalysis`, 640×480) |
| Лицо (детекция) | ML Kit Face Detection (FAST, CLASSIFICATION_MODE_ALL) |
| Речь (STT) | Vosk (`vosk-android` 0.3.75 + `vosk-model-small-ru-0.22`), офлайн |
| Голос (TTS) | Android `TextToSpeech`, `ru-RU` |
| LLM | Google Gemini REST API (`generateContent`) |
| Асинхронность | Kotlin Coroutines |
| Сборка | Gradle 8.9, AGP 8.7, Java 17, minSdk 26, targetSdk 35 |

---

## Архитектура

```text
┌─────────────────────────────────────────────────────────────┐
│                    FaceMirrorScreen                          │
│  (оркестрация: камера, речь, диалог, эмоции)                │
└──────┬──────────────────┬──────────────────┬────────────────┘
       │                  │                  │
       ▼                  ▼                  ▼
  FaceAnalyzer    SpeechRecognizerEngine   ConversationBrain
  (ML Kit)              (STT)            ┌────┴────┐
       │                  │         LlmConversationResponder
       ▼                  │         (GeminiClient)
  FaceState               │              │
       │                  ▼         GreetingDetector
       │            SpeechState      (локальный fallback)
       │                  │
       ▼                  ▼
   NeonFace ◄──── JasperVoiceSpeaker (TTS + lip pulse)
  (Canvas)         JasperSoundPlayer (звуки, не используется активно)
```

### Поток диалога

```text
STT onResult / onPartialResult
  → handleUserPhrase()
    → InterruptCommands? → interruptJasper()
    → ConversationBrain.respondToPhrase()
      → LlmConversationResponder (Gemini) или GreetingDetector
      → GreetingReply { text, expression, voice }
    → pauseListening() → TTS → resumeListening()
```

### Поток реакции на улыбку

```text
FaceAnalyzer → smile >= 0.52 (6 кадров подряд)
  → FaceGestureReactions.smileReply()
  → respondWithVoice() (без LLM, cooldown 10 с)
```

---

## Структура проекта

```text
app/src/main/kotlin/com/jasper/facemirror/
├── MainActivity.kt
├── model/
│   ├── FaceState.kt           — данные с камеры (глаза, улыбка, yaw/pitch/roll)
│   ├── FaceExpression.kt      — 9 эмоций + параметры рта/бровей/глаз
│   ├── DialogPhase.kt         — IDLE / LISTENING / THINKING / SPEAKING / INTERRUPTED
│   ├── SpeechState.kt         — partial/recognized text, history, флаги
│   ├── GreetingReply.kt       — text + VoiceEmotion + FaceExpression → pitch/rate
│   ├── FaceGestureReactions.kt — локальные реакции на улыбку
│   └── VoiceEmotion.kt        — (в GreetingReply.kt)
├── camera/
│   └── FaceAnalyzer.kt        — ML Kit → FaceState
├── speech/
│   ├── SpeechRecognizerEngine.kt  — Vosk STT, pause/resume, acknowledgePhrase
│   ├── VoskModelStore.kt          — распаковка model-ru из assets
│   ├── ConversationBrain.kt       — отменяемые LLM-запросы
│   ├── LlmConversationResponder.kt — парсинг JSON-ответа Gemini
│   ├── JasperLlmPrompt.kt         — английский промпт, ответы на русском
│   ├── GreetingDetector.kt        — локальные regex-триггеры
│   └── InterruptCommands.kt       — стоп-команды
├── llm/
│   └── GeminiClient.kt        — REST, fallback по моделям
├── audio/
│   ├── JasperVoiceSpeaker.kt  — TTS, onRangeStart → lipPulse
│   └── JasperSoundPlayer.kt   — синтез тонов (legacy)
└── ui/
    ├── FaceMirrorScreen.kt    — главный экран
    └── NeonFace.kt            — отрисовка лица + анимации
```

---

## Ключевые модели

### FaceState (с камеры)

```kotlin
leftEyeOpen, rightEyeOpen  // 0..1
smile                    // 0..1 (smilingProbability)
yaw, pitch, roll         // углы головы (yaw инвертирован для фронталки)
isDetected
```

### FaceExpression (от LLM / реакций)

9 значений: `NEUTRAL`, `HAPPY`, `PLAYFUL`, `SAD`, `OFFENDED`, `SURPRISED`, `ANGRY`, `AFRAID`, `SLEEPY`.

Каждое задаёт `smileAmount`, `eyeOpen`, `browInnerLift`, `browOuterLift`.

### GreetingReply

```kotlin
text: String
voice: VoiceEmotion      // → pitch + speechRate
expression: FaceExpression
```

### SpeechState

```kotlin
partialText, recognizedText, history (до 5 фраз)
isListening, isSpeaking
mouthOpen, amplitude     // внутренние, UI не использует
```

---

## LLM-интеграция

### Конфигурация

`local.properties` → `gemini.api.key` → `BuildConfig.GEMINI_API_KEY`

### Модели (fallback chain)

1. `gemini-3.1-flash-lite-preview`
2. `gemini-flash-latest`
3. `gemini-2.0-flash`

### Промпт (`JasperLlmPrompt`)

- Язык промпта: английский (экономия токенов)
- Ответ: JSON `{"should_reply", "reply", "expression", "voice"}`
- Ответ пользователю: только русский, max 12 слов
- Персона: любопытный 5-летний ребёнок

### Отмена запросов

`ConversationBrain.cancelPending()` — при новой фразе во время THINKING или при `interruptJasper()`.

---

## Анимации (`NeonFace`)

| Эффект | Реализация |
|--------|------------|
| Смена эмоции | `animateFloatAsState` (spring) для рта, бровей, глаз |
| Idle-моргание | `LaunchedEffect` + random delay 3–7 с, двойное моргание |
| Saccades | spring-анимация взгляда + idle drift |
| Дыхание | `infiniteTransition`, scale 1.0 ↔ 1.02 |
| Squash & stretch | `animateFloatAsState` при смене `expression` |
| Lip sync | `lipPulse` от TTS `onRangeStart` + fallback-осцилляция |
| Фазы диалога | `dialogBrowInnerAdjust` / `dialogBrowOuterAdjust` / gaze offset |

**Важно:** значения из `Animatable` внутри `Canvas` не триггерят recomposition — использовать `mutableFloatStateOf` + `animateFloatAsState`.

---

## Микрофон и TTS

### Конфликт STT ↔ TTS

`SpeechRecognizer` и TTS конкурируют за микрофон. На время ответа:

```kotlin
speechEngine.pauseListening()  // setPause(true), микрофон не закрывается
// ... TTS ...
speechEngine.resumeListening() // reset + setPause(false) через 250 ms
```

### Barge-in

Отложен. STT во время TTS не активен — эхо TTS вызывает ложные срабатывания и самопрерывание. Прерывание только через `InterruptCommands`.

### Пороги и тайминги (`FaceMirrorScreen`)

| Константа | Значение | Назначение |
|-----------|----------|------------|
| `REPLY_COOLDOWN_MS` | 2500 | Минимальный интервал между ответами |
| `EXPRESSION_HOLD_MS` | 5000 | Возврат к NEUTRAL после ответа |
| `SMILE_THRESHOLD` | 0.52 | Порог улыбки для реакции |
| `SMILE_HOLD_FRAMES` | 6 | Кадров удержания улыбки |
| `SMILE_REACTION_COOLDOWN_MS` | 10000 | Cooldown реакции на улыбку |

---

## Разрешения

- `CAMERA` — фронтальная камера (только `ImageAnalysis`, без preview)
- `RECORD_AUDIO` — микрофон для STT
- `INTERNET` — Gemini API (STT офлайн)

---

## Потоки выполнения

- Анализ камеры — `Executors.newSingleThreadExecutor()`, callback на main thread
- ML Kit callbacks — main executor
- Vosk SpeechService — callbacks на main thread (`Handler`)
- TTS lip pulse — main thread
- LLM-запросы — `Dispatchers.IO`, результат на `Dispatchers.Main`
- UI-анимации — Compose recomposition + coroutines в `LaunchedEffect`

---

## Сборка и конфигурация

```bash
# Скопировать и заполнить ключ (опционально)
cp local.properties.example local.properties

# Сборка (нужен Java 17)
./gradlew assembleDebug
```

- `local.properties` и `app/build/` в `.gitignore`
- `local.properties.example` — шаблон с `gemini.api.key`

---

## Зависимости

- `androidx.camera:*` 1.4.1
- `com.google.mlkit:face-detection` 16.1.7
- `androidx.compose:*` (BOM 2024.12.01)
- `androidx.compose.animation:animation`
- `kotlinx-coroutines-android` 1.9.0
- `androidx.lifecycle:lifecycle-runtime-compose` 2.8.7

---

## Известные ограничения

- Offline STT не настроен — нужен интернет
- TTS качество зависит от голосов устройства; выбирается лучший русский голос
- Локальная сборка требует JDK 17 (AGP 8.7)
- `JasperSoundPlayer` есть, но приветственный звук при появлении лица не используется в текущем UI
- `RecognizedWordsOverlay` удалён — текст на экране не показывается
