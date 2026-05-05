# MindType Mobile

Mental State Detection Using Keystroke Dynamics — Android Application

## Project Overview
MindType Mobile is an Android application that passively monitors a user's mental stress state by analyzing how they type on their smartphone — specifically the timing patterns of keystrokes. It operates as a custom Android keyboard (Input Method Editor) and runs silently in the background during normal phone usage.

## Features
- **Privacy First**: No typed text content is ever recorded or stored. Only timing metadata (dwell time, flight time, pressure) is captured.
- **On-Device ML Inference**: Uses an **ONNX Runtime** XGBoost model (`mindtype_model.onnx`) for real-time stress classification directly on the device — no cloud APIs.
- **Binary Stress Classification**: Classifies mental states as **CALM** or **STRESSED** based on 8 behavioral typing features.
- **Interactive Dashboard**: Real-time stress trend chart (cyan-colored, interactive with drag/zoom) showing stress history over the last 24 hours, with labeled axes and average stress score.
- **Foreground Notifications**: Persistent notification shows current stress state (🟢 Calm / 🔴 Stressed) with tap-to-open dashboard.
- **Advanced Keyboard UI**: Premium, responsive glassmorphic UI with dynamic shift states, dynamic Action keys (Search/Go/Next), and native Emoji support via Unicode mapping.
- **Dataset Collection & Secure Export**: Built for research contexts to collect keystroke features mapped with self-reported stress scores. Securely exports datasets via Android `FileProvider` to respect modern Scoped Storage restrictions.

## Architecture
```
User Types → MindType Keyboard (IME Service)
    → FeatureExtractor (15s window, 9 raw features)
    → StressClassifier (ONNX Runtime)
        → Computes 2 engineered features on-device
        → Feeds 8 features to XGBoost ONNX model
        → Output: CALM (0) or STRESSED (1)
    → Room SQLite Database (local storage)
    → Dashboard UI + Foreground Notification
```

## ML Pipeline

### Model Specification
| Property | Value |
|---|---|
| Format | ONNX (`mindtype_model.onnx`, ~220 KB) |
| Runtime | ONNX Runtime Android 1.17.0 |
| Algorithm | XGBoost (StandardScaler + XGBClassifier) |
| Classification | Binary — CALM (0) vs STRESSED (1) |
| Training Dataset | 2493 mobile typing samples |
| Accuracy | ~88% |
| Stressed F1 | ≥ 0.85 |

### Input Features (8)
| # | Feature | Source |
|---|---|---|
| 1 | `mean_dwell` | Mean key hold duration (ms) |
| 2 | `std_dwell` | Std dev of dwell times |
| 3 | `mean_flight` | Mean inter-key gap (ms) |
| 4 | `std_flight` | Std dev of flight times |
| 5 | `typing_speed` | Keys per minute |
| 6 | `backspace_rate` | Ratio of backspace events |
| 7 | `combined_behavior` | Engineered: `(mean_dwell × typing_speed) / (backspace_rate + 1e-6)` |
| 8 | `dwell_flight_ratio` | Engineered: `mean_dwell / (mean_flight + 1e-6)` |

### Retraining
```bash
# From the project root:
python train_and_export.py
```
This script:
1. Merges all CSVs from `mb_dataset/datasets/`
2. Cleans and labels the data (binary: Calm=0, Stressed=1)
3. Trains XGBoost with GridSearchCV
4. Exports `mindtype_model.onnx`
5. Copies the model to `app/src/main/assets/`

## Setup Requirements
- **Android**: API Level 26+ (Android 8.0 Oreo and above)
- **IDE**: Android Studio (latest stable)
- **ML Training** (optional): Python 3.9+, `xgboost`, `scikit-learn`, `skl2onnx`, `onnxruntime`

## Build & Install
```bash
# Clean build
./gradlew clean assembleDebug

# Install on connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Dependencies
| Library | Purpose |
|---|---|
| `onnxruntime-android:1.17.0` | On-device ML inference |
| `MPAndroidChart:v3.1.0` | Interactive stress trend chart |
| `Room 2.6.1` | Local SQLite database |
| `WorkManager 2.9.0` | Background task scheduling |
| `Coroutines 1.7.3` | Async operations |

## Privacy & Security
- ✅ No text content stored — only keystroke timing metadata
- ✅ Zero network permissions — no internet access
- ✅ All ML inference on-device — no cloud APIs
- ✅ Anonymous participant IDs (U01, U02, etc.)
- ✅ Database sandboxed in app-private storage
- ✅ Secure Export: Datasets are exported via `FileProvider` internally to bypass external storage vulnerabilities and respect Android 10+ Scoped Storage restrictions.

See [AUDIT.md](AUDIT.md) for detailed privacy audit and compliance checklist.

## Target Accuracy Objectives
The target metric for the ML pipeline is a **Stressed F1-Score ≥ 0.85**. We focus on binary classification (CALM vs STRESSED) for higher reliability and simpler real-time interpretation on mobile devices.

## Research Context
This application is part of an academic research project at VIT-AP University, Department of Networking and Security, under the supervision of Dr. Udit Narayana Kar.
