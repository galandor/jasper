# Jasper

Интерактивный неоновый персонаж для Android — говорит, выражает эмоции и смотрит на вас через фронтальную камеру.

## Что это

Jasper — мультяшное лицо на чёрном экране: глаза, брови, рот в неоновом стиле. Персонаж ведёт голосовой диалог на русском, реагирует на фразы и улыбку, моргает и «дышит» в idle.

Характер — любопытный 5-летний ребёнок: короткие тёплые ответы, радуется похвале и играм.

## Возможности

- **Голосовой диалог** — говорите с Jasper, он отвечает (Gemini LLM или локальные правила)
- **9 эмоций** — happy, playful, sad, angry, afraid, sleepy и др.
- **Живая анимация** — моргание, saccades, дыхание, squash & stretch, lip sync при речи
- **Взгляд** — зрачки следуют за поворотом вашей головы
- **Реакция на улыбку** — Jasper замечает улыбку и радуется
- **Стоп-команды** — «стоп», «тихо», «подожди» мгновенно прерывают речь

## Скриншот

Чёрный экран, landscape, только неоновое лицо — без текста и кнопок (кроме запроса разрешений).

## Стек

| Компонент | Технология |
|-----------|------------|
| UI | Kotlin, Jetpack Compose, Canvas |
| Камера | CameraX + ML Kit Face Detection |
| Речь | Android STT + TTS (`ru-RU`) |
| LLM | Google Gemini REST API |
| Сборка | Gradle 8.9, AGP 8.7, Java 17 |

## Быстрый старт

### Требования

- Android Studio Ladybug или новее
- JDK 17
- Устройство Android 8+ (API 26) с фронтальной камерой и микрофоном
- Интернет (STT через Google; LLM — опционально)

### Запуск

1. Клонируйте репозиторий
2. Откройте в Android Studio
3. (Опционально) Скопируйте `local.properties.example` → `local.properties` и добавьте ключ Gemini:

   ```properties
   gemini.api.key=YOUR_KEY_HERE
   ```

   Ключ: [Google AI Studio](https://aistudio.google.com/apikey)

4. Run → `app`
5. Разрешите доступ к камере и микрофону

Без ключа Gemini Jasper отвечает только на ключевые слова (привет, злюсь, боюсь и т.д.).

### Сборка из терминала

```bash
./gradlew assembleDebug
```

## Структура проекта

```
app/src/main/kotlin/com/jasper/facemirror/
├── MainActivity.kt
├── model/          — FaceState, FaceExpression, DialogPhase, GreetingReply
├── camera/         — FaceAnalyzer (ML Kit)
├── speech/         — STT, LLM, локальные правила, стоп-команды
├── llm/            — GeminiClient
├── audio/          — TTS, звуки
└── ui/             — FaceMirrorScreen, NeonFace

.memory-bank/       — продуктовая и техническая документация
```

## Документация

| Файл | Описание |
|------|----------|
| `.memory-bank/business.md` | Продуктовая спецификация |
| `.memory-bank/technical.md` | Техническая спецификация |
| `.memory-bank/roadmap.md` | План развития |
| `.memory-bank/features/visual.md` | Визуальный модуль |
| `.memory-bank/features/voice.md` | Голосовой модуль |

## Ветки

| Ветка | Назначение |
|-------|------------|
| `main` | Стабильная версия |
| `jasper2` | Текущая разработка (спринт 2) |

## Ограничения

- Горизонтальная ориентация экрана
- Во время ответа Jasper перебить голосом нельзя — только стоп-командой
- Качество распознавания и TTS зависят от устройства
- Нужен интернет для STT

## Лицензия

См. репозиторий.
