# Голосовая реализация

## Обзор

Голосовой модуль отвечает за:
1. **Распознавание речи** (STT) — что говорит пользователь
2. **Отображение текста** — неоновый overlay внизу экрана
3. **Голосовой ответ** — TTS «Привет!» на слово «привет»
4. **Звуки** — мелодия при появлении лица в кадре

Анимация губ по громкости речи **отключена** (давала ложные срабатывания).

## Компоненты

### SpeechRecognizerEngine

`speech/SpeechRecognizerEngine.kt`

- Обёртка над Android `SpeechRecognizer`
- Непрерывное прослушивание: после `onResults` / `onError` перезапуск через `scheduleRestart`
- Язык: `ru-RU`
- Partial results включены — текст обновляется в реальном времени
- `onRmsChanged` — амплитуда для внутреннего состояния (UI губ не использует)

**Пауза при TTS:**
```kotlin
pauseListening()  // cancel + paused = true
resumeListening() // paused = false + restart
```

### GreetingDetector

`speech/GreetingDetector.kt`

- Проверяет наличие слова «привет» в распознанной фразе (регистронезависимо)
- Срабатывает на финальный результат (`recognizedText`), не на partial

### JasperVoiceSpeaker

`audio/JasperVoiceSpeaker.kt`

- Android `TextToSpeech`, язык `ru-RU`
- `speakHello()` — произносит «Привет!»
- `pitch = 1.2`, `speechRate = 1.05`
- Callback `onSpeakingChanged` для UI (опционально)

### JasperSoundPlayer

`audio/JasperSoundPlayer.kt`

- Синтез коротких тонов через `AudioTrack`
- `playGreeting()` — восходящая мелодия при появлении лица (4 ноты)
- `onSpeechAmplitude()` — **не вызывается** (отключено вместе с анимацией губ)

### RecognizedWordsOverlay

`ui/RecognizedWordsOverlay.kt`

- Partial text — розовый неон (`NeonPink`)
- Final text — cyan неон (`NeonCyan`), исчезает через ~3.5 с
- История — до 2 предыдущих фраз, тусклый cyan

## Поток данных

```
Пользователь говорит
    → SpeechRecognizer.onPartialResults → partialText (розовый)
    → SpeechRecognizer.onResults → recognizedText (cyan)
    → GreetingDetector.isHello(text)?
        → да: pauseListening → TTS «Привет!» → resumeListening
```

## Защита от повторов

- `lastProcessedPhrase` — одна фраза обрабатывается один раз
- `HELLO_COOLDOWN_MS = 4000` — не чаще одного голосового ответа в 4 секунды

## Требования

- Разрешение `RECORD_AUDIO`
- Разрешение `INTERNET` (Google Speech Services)
- На устройстве должен быть доступен `RecognitionService` (обычно Google)

## Известные ограничения

- Распознавание зависит от качества микрофона и шума
- TTS и STT конкурируют за микрофон — поэтому STT паузится на время ответа
- Offline-распознавание не настроено — нужен интернет

## Возможные улучшения

- Словарь команд: «пока», «как дела», «спасибо»
- Offline STT (Vosk, ML Kit — когда появится)
- Визуальная индикация «слушаю» / «думаю»
- Вернуть анимацию губ с более высоким порогом и сглаживанием
