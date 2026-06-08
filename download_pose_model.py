#!/usr/bin/env python3
"""
Скрипт для скачивания MediaPipe Pose Landmarker модели
"""
import urllib.request
import os

# URL модели MediaPipe Pose Landmarker (lite версия)
MODEL_URL = "https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/1/pose_landmarker_lite.task"

# Путь куда сохранить
OUTPUT_PATH = "app/src/main/assets/pose_landmarker_lite.task"

def download_model():
    print("Скачивание MediaPipe Pose Landmarker...")
    print(f"URL: {MODEL_URL}")
    print(f"Сохранение в: {OUTPUT_PATH}")
    
    # Создаем папку если не существует
    os.makedirs(os.path.dirname(OUTPUT_PATH), exist_ok=True)
    
    # Скачиваем
    urllib.request.urlretrieve(MODEL_URL, OUTPUT_PATH)
    
    # Проверяем размер
    size_mb = os.path.getsize(OUTPUT_PATH) / (1024 * 1024)
    print(f"✓ Модель скачана! Размер: {size_mb:.2f} MB")

if __name__ == "__main__":
    download_model()
