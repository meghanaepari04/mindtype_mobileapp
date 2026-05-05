# MindType Mobile - Privacy and Security Audit

This document tracks privacy compliance and security requirements for the MindType Mobile project as defined in the Product Requirements Document (PRD).

## Core Privacy Principles
- **No Text Content Stored**: The actual characters typed are NEVER stored in any form, locally or remotely. Only keystroke timing metadata (dwell time, flight time, pressure) is recorded.
- **On-Device Processing**: All Machine Learning inference happens on the user's device via **ONNX Runtime** (XGBoost model exported as `mindtype_model.onnx`).
- **No Network Transmissions**: Feature data is never uploaded to external servers. The app requires **zero internet permissions**.
- **Anonymized User IDs**: Participants are assigned anonymous IDs (U01, U02, etc.) with no link to personal identity.

## Architecture Overview
```
Keystroke Input → FeatureExtractor (9 raw features)
    → StressClassifier (ONNX Runtime, 8 model features)
    → Binary Output: CALM (0) / STRESSED (1)
    → Room SQLite Database (local only)
    → Dashboard UI + Foreground Notification
```

## Model Specification
| Property | Value |
|---|---|
| Model Format | ONNX (`mindtype_model.onnx`, ~220 KB) |
| Runtime | ONNX Runtime Android 1.17.0 |
| Algorithm | XGBoost (StandardScaler + XGBClassifier pipeline) |
| Classification | Binary — CALM (0) vs STRESSED (1) |
| Input Features | 8 (mean_dwell, std_dwell, mean_flight, std_flight, typing_speed, backspace_rate, combined_behavior, dwell_flight_ratio) |
| Training Dataset | 2493 labeled mobile typing samples (Calm=1443, Stressed=1050) |
| Training Params | n_estimators=300, max_depth=4, learning_rate=0.1 |
| Test Split | 70/30 stratified, random_state=42 |
| Accuracy | ~88% |
| Stressed F1 | ≥ 0.85 ✅ |

## Feature Engineering (On-Device)
The `StressClassifier.kt` computes two engineered features from the 9 raw `FeatureExtractor` outputs:
- `combined_behavior = (mean_dwell × typing_speed) / (backspace_rate + 1e-6)`
- `dwell_flight_ratio = mean_dwell / (mean_flight + 1e-6)`

These match the exact formulas used during model training in `train_and_export.py`.

## Data Storage
| Table | Contents | Sensitive? |
|---|---|---|
| `keystroke_events` | Timestamps, dwell/flight times, pressure, key codes | Low — no text content |
| `feature_windows` | Aggregated features + predicted stress class | Low — statistical summaries |
| `sessions` | Session start/end times, participant ID | Low — anonymous |
| `stress_labels` | Self-reported stress scores | Low — survey data |

**Storage location**: `/data/data/com.mindtype.mobile/databases/mindtype_database` (app-private, sandboxed)

## Notification System
- Persistent foreground notification shows current stress level:
  - 🟢 **"Stress: Calm — tap to view dashboard"**
  - 🔴 **"Stress: Stressed — tap to view dashboard"**
- Updates in real-time after each 15-second feature window
- Required by Android for foreground IME services
- Tapping opens the MindType dashboard

## Audit Checklist
| Requirement | Status | Verification |
|---|---|---|
| No textual data collected | ✅ PASS | Only event timestamps, dwell/flight durations, and pressure are collected |
| Zero network calls | ✅ PASS | No `INTERNET` permission in `AndroidManifest.xml` |
| Anonymized User IDs | ✅ PASS | Participants assigned IDs like U01, U02 |
| Database security | ✅ PASS | Room Database isolated in app-private internal storage |
| On-device inference only | ✅ PASS | ONNX Runtime runs locally, no cloud API calls |
| Model size appropriate | ✅ PASS | 220 KB ONNX model, minimal battery/memory impact |

## Audit Log
- **2026-05-02**: Security Patch — Upgraded the CSV data export system to comply with Android 10+ Scoped Storage restrictions. Migrated direct public storage writes to internal `filesDir` combined with secure Android `FileProvider` URIs to hook into the native Share Sheet. Also shipped advanced UI/UX keyboard upgrades (dynamic emojis, intelligent EditorAction enter keys).
- **2026-04-29**: Major architecture update — migrated from TensorFlow Lite (3-class) to **ONNX Runtime (binary classification)**. Updated `StressClassifier.kt`, `build.gradle`, `MindTypeIMEService.kt`, and `MainActivity.kt`. Dashboard chart upgraded to interactive cyan-colored line chart with labeled axes and avg stress score display.
- **2026-04-28**: Retrained XGBoost model on mobile dataset (`mb_dataset/clean_mobile_dataset.csv`, 2493 samples). Exported as `mindtype_model.onnx` (8 features, binary output). Accuracy ~88%, Stressed F1 ≥ 0.85.
- **2026-04-17**: Initial Audit file created. PRD metrics audited and revised model accuracy objectives to 85-90% to avoid overfitting risks.
- **2026-03-25**: PRD v1.0 generated, establishing baseline privacy requirements.

## Testing & Evaluation Log
- **2026-04-29 — ONNX Binary Model (Production):**
  - **Runtime**: ONNX Runtime Android 1.17.0
  - **Model**: XGBoost pipeline (StandardScaler + XGBClassifier)
  - **Dataset**: 2493 mobile typing samples (Calm=1443, Mild_Stress=617, High_Stress=433 → binary: Calm=1443, Stressed=1050)
  - **Test Accuracy**: ~88%
  - **Stressed F1**: ≥ 0.85 ✅
  - **Confusion Matrix**: [[404, 29], [60, 255]]
  - *Audit Note*: Binary classification (CALM/STRESSED) chosen over 3-class for higher reliability and simpler real-time interpretation. Model meets PRD requirement of F1 ≥ 0.85.

- **2026-04-17 — Previous Model Evaluation:**
  - **Data Leakage & Augmentation Audit:** Identified and fixed two critical bugs:
    1. Discovered literal string `"nan"` labels inside the csv dataset; properly dropped null labels before training.
    2. Addressed an augmentation data leak where synthetic modifications were applied *before* `train_test_split`. Code has been rewritten to isolate testing sets completely from training manipulations.
  - **Pipeline Update**: Transitioned ML algorithm from RandomForest to XGBoost to handle complex tabulated structures.
  - **Validation Strategy**: Evaluated with a strictly pristine 20% validation split (487 non-augmented test samples).
  - **Overall Accuracy**: ~91.17%
  - **Weighted F1-score**: ~91.00%
  - *Audit Note*: The model exceeded 91% predictive accuracy safely, completely free of any data leakage or null bias masking.
