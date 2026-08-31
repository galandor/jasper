# OpenBot-style learning for Jasper

Front camera, phone as the face, tilted slightly down. The app records the **bottom of the frame** (floor / furniture), not the eyes.

## Joystick vs car Bluetooth

They do **not** fight.

| Device | Profile | Pairing |
|--------|---------|---------|
| Machine (HC-06) | Bluetooth Classic **SPP** serial | already in the app |
| DualShock 4 / Xbox / cheap BT pad | Bluetooth **HID** (like a keyboard) | Android Settings → Bluetooth |

The phone can keep both links. Pair the pad in system settings, not inside Jasper.

### DualShock 4
1. Hold **PS + Share** until the light blinks fast.
2. Phone → Bluetooth → DualShock.
3. Left stick — drive (OpenBot joystick mode).
4. **R2 / L2** if present — gas / reverse (OpenBot game mode).
5. **Cross (X)** — start/stop recording.
6. **Triangle** — autopilot on/off (needs a trained model).
7. **Circle** — emergency stop.

### Cheap Bluetooth stick
Pair it the same way. If it only has one stick, that is enough.

### USB
OTG cable into the phone. No Bluetooth for the pad at all — useful if two BT devices act up.

## Record the apartment

1. Tilt the face ~5–10° down so the floor is in the lower third.
2. Cross / say «записывай».
3. Drive a slow loop: corridors, around chairs, recover from almost-hits.
4. 20–40 minutes total, several sessions, different lighting.
5. Cross again / «стоп запись».
6. Pull files:

```bash
adb pull /sdcard/Android/data/com.jasper.facemirror/files/OpenBot ./policy/dataset/train_data/
```

Move **one** session folder into `policy/dataset/test_data/`.

## Train

```bash
pip install tensorflow pillow numpy
python policy/train.py --data policy/dataset --out policy/models/autopilot_float.tflite
```

Push the model:

```bash
adb push policy/models/autopilot_float.tflite /sdcard/Android/data/com.jasper.facemirror/files/OpenBot/autopilot_float.tflite
```

Restart the app. Triangle / «автопилот». Keep the pad in hand — the policy will crash into things.

Logs follow OpenBot (`rgbFrames.txt`, `ctrlLog.txt`, `sonarLog.txt`, `*_crop.jpeg`).
Ultrasound is logged as `timestamp[ns],distance[cm]` about 8 times a second. `0` means no echo.

Flash `arduino/jasper_chassis/jasper_chassis.ino` after wiring trig=`A3`, echo=`A2`. The sketch must print `D:<cm>` — without that the phone has nothing to record.
