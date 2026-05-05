"""
MindType — Train → Test → Export Pipeline
==========================================
Replicates the EXACT results from the Windows screenshot:

  Accuracy : 0.8756844491978609
  F1 Score : 0.8482871125611745
  Confusion Matrix: [[395, 38], [55, 260]]
  Class 0  : precision=0.88, recall=0.91, f1=0.89, support=433
  Class 1  : precision=0.87, recall=0.83, f1=0.85, support=315

Pipeline (mobile_testing.py exact logic):
  1. Merge all CSVs in mb_dataset/datasets/ → final_mobile_dataset.csv
  2. Clean → clean_mobile_dataset.csv
  3. Fix labels: keep only Calm / Mild_Stress / High_Stress
  4. Binary encode: Calm=0, rest=1
  5. Feature engineering: combined_behavior, dwell_flight_ratio (8 features)
  6. 70/30 stratified split  random_state=42
  7. StandardScaler + XGBClassifier(n_estimators=300, max_depth=4, lr=0.1)
  8. Export to ONNX → app/src/main/assets/mindtype_model.onnx

Usage:
    python train_and_export.py
"""

import os, sys, shutil
import numpy as np
import pandas as pd
import joblib
import glob
import warnings
warnings.filterwarnings("ignore")

ASSETS_DIR = os.path.join(os.path.dirname(__file__), "app", "src", "main", "assets")
MB_DIR     = os.path.join(os.path.dirname(__file__), "mb_dataset")
DATASETS_DIR     = os.path.join(MB_DIR, "datasets")
FINAL_CSV        = os.path.join(MB_DIR, "final_mobile_dataset.csv")
CLEAN_CSV        = os.path.join(MB_DIR, "clean_mobile_dataset.csv")
ONNX_OUTPUT      = os.path.join(os.path.dirname(__file__), "mindtype_model.onnx")

N_FEATURES = 8
FEATURE_COLS = [
    "mean_dwell", "std_dwell", "mean_flight", "std_flight",
    "typing_speed", "backspace_rate",
    "combined_behavior", "dwell_flight_ratio"
]

# ─── Step 1: Merge all individual CSVs ───────────────────────────────────────
print(f"\n{'='*60}")
print(f"  MindType — Train → Test → Export")
print(f"{'='*60}")
print(f"\n[1/6] Merging all datasets from {DATASETS_DIR} ...")

files = glob.glob(os.path.join(DATASETS_DIR, "*.csv"))
print(f"      Files found: {len(files)}")
combined = pd.concat([pd.read_csv(f) for f in files], ignore_index=True)
combined.to_csv(FINAL_CSV, index=False)
print(f"      Combined shape: {combined.shape}  → saved to final_mobile_dataset.csv")

# ─── Step 2: Clean (clean_final_dataset.py logic) ────────────────────────────
print(f"\n[2/6] Cleaning dataset...")
df = pd.read_csv(FINAL_CSV)
df = df.replace([np.inf, -np.inf], np.nan)
numeric_cols = df.select_dtypes(include=[np.number]).columns
for col in numeric_cols:
    df[col] = df.groupby("mapped_class")[col].transform(lambda x: x.fillna(x.median()))
df[numeric_cols] = df[numeric_cols].fillna(df[numeric_cols].median())
df[numeric_cols] = df[numeric_cols].clip(-1e5, 1e5)
df.to_csv(CLEAN_CSV, index=False)
print(f"      Cleaned shape: {df.shape}  → saved to clean_mobile_dataset.csv")

# ─── Step 3: Fix labels (train_mobile_model.py exact logic) ──────────────────
print(f"\n[3/6] Fixing labels (keeping only canonical Calm/Mild_Stress/High_Stress)...")
df = pd.read_csv(CLEAN_CSV)
print(f"      Shape (before fix): {df.shape}")

df["mapped_class"] = df["mapped_class"].astype(str).str.lower().str.strip()
df["mapped_class"] = df["mapped_class"].replace({
    "calm":        "Calm",
    "high_stress": "High_Stress",
    "mild_stress": "Mild_Stress"
})
df = df.replace([np.inf, -np.inf], np.nan)
if "predicted_class" in df.columns:
    df["mapped_class"] = df["mapped_class"].fillna(df["predicted_class"])
numeric_cols = df.select_dtypes(include=[np.number]).columns
df[numeric_cols] = df[numeric_cols].fillna(df[numeric_cols].median())
# Keep ONLY the 3 canonical class labels (drops CALM, HIGH_STRESS, MILD_STRESS etc.)
df = df[df["mapped_class"].isin(["Calm", "Mild_Stress", "High_Stress"])]

print(f"      Shape (after fix) : {df.shape}")
print(f"\n      Class distribution:")
print("     ", df["mapped_class"].value_counts().to_string().replace("\n", "\n      "))

# ─── Step 4: Binary encode + feature engineering (mobile_testing.py logic) ───
print(f"\n[4/6] Binary encoding + feature engineering...")

# Binary: Calm=0, Mild_Stress/High_Stress=1
df["mapped_class"] = df["mapped_class"].apply(lambda x: 0 if x == "Calm" else 1)
print(f"      CALM (0)     : {(df['mapped_class']==0).sum()}")
print(f"      STRESSED (1) : {(df['mapped_class']==1).sum()}")

# Clean inf/nan
df = df.replace([np.inf, -np.inf], np.nan)
df = df.fillna(df.median(numeric_only=True))

# Feature engineering — exactly as mobile_testing.py
df["combined_behavior"]  = (df["mean_dwell"] * df["typing_speed"]) / (df["backspace_rate"] + 1e-6)
df["dwell_flight_ratio"] = df["mean_dwell"] / (df["mean_flight"] + 1e-6)

X = df[FEATURE_COLS].values.astype(np.float32)
y = df["mapped_class"].values.astype(int)

print(f"\n      NaNs in X: {np.isnan(X).sum()}")
print(f"      NaNs in y: {np.isnan(y.astype(float)).sum()}")

# ─── Step 5: Train + Test ─────────────────────────────────────────────────────
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import StandardScaler
from sklearn.pipeline import Pipeline
from sklearn.metrics import (accuracy_score, f1_score, confusion_matrix,
                              classification_report, auc, precision_recall_curve)
from xgboost import XGBClassifier

print(f"\n[5/6] Training (70/30 split, random_state=42)...")

X_train, X_test, y_train, y_test = train_test_split(
    X, y, test_size=0.3, random_state=42, stratify=y
)
print(f"      Train: {len(X_train)}  |  Test: {len(X_test)}")
print(f"      Test  class 0 support: {(y_test==0).sum()}  class 1 support: {(y_test==1).sum()}")

# Exact model from mobile_testing.py
pipeline = Pipeline([
    ("scaler", StandardScaler()),
    ("xgb", XGBClassifier(
        n_estimators=300,
        max_depth=4,
        learning_rate=0.1,
        eval_metric="logloss",
        random_state=42,
        verbosity=0,
        n_jobs=-1,
    ))
])

pipeline.fit(X_train, y_train)
y_pred = pipeline.predict(X_test)

# ─── Exact test output matching mobile_testing.py ────────────────────────────
acc = accuracy_score(y_test, y_pred)
f1  = f1_score(y_test, y_pred, average="weighted")
cm  = confusion_matrix(y_test, y_pred)

print(f"\n{'='*60}")
print(f"  ✅  TEST RESULTS")
print(f"{'='*60}")
print(f"\nAccuracy: {acc}")
print(f"F1 Score: {f1}")
print(f"\nConfusion Matrix:")
print(cm)
print(f"\nClassification Report:")
print(classification_report(y_test, y_pred, digits=2))
print(f"  Target Accuracy ~0.8757 : {'✅' if abs(acc-0.8757)<0.002 else '⚠️ '} {acc:.4f}")
print(f"  Target F1      ~0.8483  : {'✅' if abs(f1 -0.8483)<0.002 else '⚠️ '} {f1:.4f}")
print(f"{'='*60}\n")

# Save pkl
joblib.dump(pipeline, "mindtype_model.pkl")
print(f"      Saved: mindtype_model.pkl")

# ─── Step 6: Export to ONNX ───────────────────────────────────────────────────
print(f"\n[6/6] Exporting {N_FEATURES}-feature model to ONNX...")

from skl2onnx import convert_sklearn, update_registered_converter
from skl2onnx.common.data_types import FloatTensorType
from skl2onnx.common.shape_calculator import calculate_linear_classifier_output_shapes
from onnxmltools.convert.xgboost.operator_converters.XGBoost import convert_xgboost as convert_xgb_to_onnx

xgb_step = pipeline.named_steps["xgb"]
booster   = xgb_step.get_booster()
booster.feature_names = [f"f{i}" for i in range(N_FEATURES)]

update_registered_converter(
    XGBClassifier, "XGBClassifier",
    calculate_linear_classifier_output_shapes,
    convert_xgb_to_onnx,
    options={"nocl": [True, False], "zipmap": [True, False, "columns"]}
)

onnx_model = convert_sklearn(
    pipeline,
    initial_types=[("float_input", FloatTensorType([None, N_FEATURES]))],
    target_opset={"": 17, "ai.onnx.ml": 3}
)

with open(ONNX_OUTPUT, "wb") as f:
    f.write(onnx_model.SerializeToString())
size_kb = os.path.getsize(ONNX_OUTPUT) / 1024
print(f"      Saved ONNX: {ONNX_OUTPUT} ({size_kb:.1f} KB)")

import onnxruntime as ort
sess = ort.InferenceSession(ONNX_OUTPUT)
inp  = sess.get_inputs()[0].name
outs = [o.name for o in sess.get_outputs()]
print(f"      Input  : '{inp}'  shape={sess.get_inputs()[0].shape}")
for o in sess.get_outputs():
    print(f"      Output : '{o.name}'  shape={o.shape}")

os.makedirs(ASSETS_DIR, exist_ok=True)
dest = os.path.join(ASSETS_DIR, "mindtype_model.onnx")
shutil.copy2(ONNX_OUTPUT, dest)
print(f"\n  ✅ Copied to Android assets: {dest}")
print(f"  🚀 Rebuild APK in Android Studio!\n")
