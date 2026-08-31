#!/usr/bin/env python3
"""Train a tiny OpenBot-style driving policy from Jasper recordings.

Expected layout (as saved by the app):

    dataset/
      train_data/<session>/images/*_crop.jpeg
      train_data/<session>/sensor_data/rgbFrames.txt
      train_data/<session>/sensor_data/ctrlLog.txt
      test_data/<session>/...

Put ~80% of sessions into train_data and one session into test_data.

Usage:
    python policy/train.py --data policy/dataset --out policy/models/autopilot_float.tflite
"""

from __future__ import annotations

import argparse
from pathlib import Path

import numpy as np
from PIL import Image

HEIGHT = 96
WIDTH = 256


def read_log(path: Path) -> list[tuple[int, list[str]]]:
    rows: list[tuple[int, list[str]]] = []
    with path.open() as handle:
        handle.readline()
        for line in handle:
            line = line.strip().replace(",", " ")
            if not line or line.startswith("#"):
                continue
            parts = line.split()
            if len(parts) < 2:
                continue
            rows.append((int(parts[0]), parts[1:]))
    rows.sort()
    return rows


def associate(frames: list[tuple[int, list[str]]], ctrls: list[tuple[int, list[str]]]):
    if not frames or not ctrls:
        return []
    matched = []
    ctrl_idx = 0
    for ts, frame_data in frames:
        while ctrl_idx + 1 < len(ctrls) and ctrls[ctrl_idx + 1][0] <= ts:
            ctrl_idx += 1
        left = int(float(ctrls[ctrl_idx][1][0]))
        right = int(float(ctrls[ctrl_idx][1][1])) if len(ctrls[ctrl_idx][1]) > 1 else left
        if left == 0 and right == 0:
            continue
        frame_id = frame_data[0]
        matched.append((frame_id, left / 255.0, right / 255.0))
    return matched


def load_split(split_dir: Path):
    images = []
    labels = []
    for session in sorted(p for p in split_dir.iterdir() if p.is_dir()):
        sensor = session / "sensor_data"
        img_dir = session / "images"
        frames_file = sensor / "rgbFrames.txt"
        ctrl_file = sensor / "ctrlLog.txt"
        if not frames_file.exists() or not ctrl_file.exists():
            continue
        pairs = associate(read_log(frames_file), read_log(ctrl_file))
        for frame_id, left, right in pairs:
            jpeg = img_dir / f"{frame_id}_crop.jpeg"
            if not jpeg.exists():
                continue
            image = Image.open(jpeg).convert("RGB").resize((WIDTH, HEIGHT))
            images.append(np.asarray(image, dtype=np.float32) / 255.0)
            labels.append([left, right])
    if not images:
        raise SystemExit(f"No labelled frames in {split_dir}")
    return np.stack(images), np.asarray(labels, dtype=np.float32)


def build_model():
    import tensorflow as tf

    inputs = tf.keras.Input(shape=(HEIGHT, WIDTH, 3), name="img_input")
    x = tf.keras.layers.Conv2D(16, 5, strides=2, activation="relu")(inputs)
    x = tf.keras.layers.Conv2D(32, 5, strides=2, activation="relu")(x)
    x = tf.keras.layers.Conv2D(48, 3, strides=2, activation="relu")(x)
    x = tf.keras.layers.Flatten()(x)
    x = tf.keras.layers.Dense(64, activation="relu")(x)
    outputs = tf.keras.layers.Dense(2, activation="tanh", name="ctrl")(x)
    model = tf.keras.Model(inputs, outputs)
    model.compile(optimizer=tf.keras.optimizers.Adam(1e-4), loss="mse")
    return model


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--data", type=Path, default=Path("policy/dataset"))
    parser.add_argument("--out", type=Path, default=Path("policy/models/autopilot_float.tflite"))
    parser.add_argument("--epochs", type=int, default=30)
    parser.add_argument("--batch", type=int, default=16)
    args = parser.parse_args()

    x_train, y_train = load_split(args.data / "train_data")
    x_test, y_test = load_split(args.data / "test_data")
    print(f"train {len(x_train)}  test {len(x_test)}")

    model = build_model()
    model.fit(
        x_train,
        y_train,
        validation_data=(x_test, y_test),
        epochs=args.epochs,
        batch_size=args.batch,
    )

    import tensorflow as tf

    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = []
    tflite = converter.convert()
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_bytes(tflite)
    print(f"wrote {args.out} ({len(tflite)} bytes)")


if __name__ == "__main__":
    main()
