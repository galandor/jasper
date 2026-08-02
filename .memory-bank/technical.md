# Jasper — техническое описание

## Стек

| Слой | Технология |
|---|---|
| Язык | Kotlin |
| UI | Jetpack Compose, Canvas |
| Камера | CameraX (`ImageAnalysis`) |
| Лицо | ML Kit Face Detection |
| Речь (STT) | Android `SpeechRecognizer` |
| Голос (TTS) | Android `TextToSpeech` |
| Звуки | `AudioTrack` (синтез тонов) |
| Сборка | Gradle 8.9, AGP 8.7, minSdk 26, targetSdk 35 |

## Архитектура

```
Камера (CameraX)
    → FaceAnalyzer (ML Kit)
    → FaceState
    → NeonFace (отрисовка)

Микрофон (SpeechRecognizer)
    → SpeechRecognizerEngine
    → SpeechState
    → RecognizedWordsOverlay (текст)
    → GreetingDetector → JasperVoiceSpeaker (TTS)

События
    → JasperSoundPlayer (звуки приветствия)
    → FaceMirrorScreen (оркестрация)
```

## Структура проекта

```
app/src/main/kotlin/com/jasper/facemirror/
├── MainActivity.kt
├── model/
│   ├── FaceState.kt       — состояние лица с камеры
│   └── SpeechState.kt     — состояние речи
├── camera/
│   └── FaceAnalyzer.kt    — ML Kit анализ кадров
├── speech/
│   ├── SpeechRecognizerEngine.kt
│   └── GreetingDetector.kt
├── audio/
│   ├── JasperSoundPlayer.kt   — звуковые эффекты
│   └── JasperVoiceSpeaker.kt  — TTS
└── ui/
    ├── FaceMirrorScreen.kt
    ├── NeonFace.kt
    └── RecognizedWordsOverlay.kt
```

## Разрешения

- `CAMERA` — фронтальная камера
- `RECORD_AUDIO` — микрофон для распознавания речи
- `INTERNET` — облачное распознавание речи Google

## Ключевые модели данных

### FaceState

```kotlin
leftEyeOpen, rightEyeOpen  // 0..1
smile                    // 0..1
yaw, pitch, roll         // углы головы
isDetected               // лицо в кадре
```

### SpeechState

```kotlin
partialText      // распознаётся сейчас
recognizedText   // последняя фраза
history          // до 5 последних фраз
isListening, isSpeaking
mouthOpen, amplitude  // не используются для UI губ (отключено)
```

## Потоки и потоки выполнения

- Анализ камеры — фоновый executor, callback на main thread
- SpeechRecognizer — callbacks на main thread
- TTS и звуки — корутины (`Dispatchers.Default`)

## Конфликт микрофона

`SpeechRecognizer` и отдельный `AudioRecord` не используются одновременно. Распознавание речи идёт только через `SpeechRecognizer`; при ответе TTS распознавание ставится на паузу (`pauseListening` / `resumeListening`).

## Сборка и git

- `app/build/` и `local.properties` в `.gitignore`
- В репозитории только исходники

## Запуск

```bash
./gradlew assembleDebug
```

Или Run в Android Studio на устройстве с фронтальной камерой.

## Зависимости (основные)

- `androidx.camera:*` 1.4.1
- `com.google.mlkit:face-detection` 16.1.7
- `androidx.compose:*` (BOM 2024.12.01)
- `kotlinx-coroutines-android` 1.9.0
