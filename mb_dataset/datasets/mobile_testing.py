import pandas as pd
import numpy as np
import joblib
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import StandardScaler
from sklearn.pipeline import Pipeline
from sklearn.metrics import classification_report, confusion_matrix, accuracy_score, f1_score
from xgboost import XGBClassifier

# ── LOAD ──────────────────────────────────────────────
df = pd.read_csv("clean_mobile_dataset.csv")

df = df.dropna(subset=["mapped_class"])
df["mapped_class"] = df["mapped_class"].astype(str).str.lower().str.strip()
df = df[df["mapped_class"] != "nan"]

# ── BINARY LABELS (same as your testing code) ─────────
df["mapped_class"] = df["mapped_class"].apply(lambda x: 0 if x == "calm" else 1)
# 0 = Calm, 1 = Stressed

# ── CLEAN ─────────────────────────────────────────────
df = df.replace([np.inf, -np.inf], np.nan)
df = df.fillna(df.median(numeric_only=True))

# ── FEATURE ENGINEERING ───────────────────────────────
df["combined_behavior"] = (
    df["mean_dwell"] * df["typing_speed"]
) / (df["backspace_rate"] + 1e-6)

df["dwell_flight_ratio"] = df["mean_dwell"] / (df["mean_flight"] + 1e-6)

# ── FEATURES (8 features — same as testing code) ──────
X = df[[
    "mean_dwell",
    "std_dwell",
    "mean_flight",
    "std_flight",
    "typing_speed",
    "backspace_rate",
    "combined_behavior",
    "dwell_flight_ratio"
]]

y = df["mapped_class"]

# ── TRAIN/TEST SPLIT ───────────────────────────────────
X_train, X_test, y_train, y_test = train_test_split(
    X, y,
    test_size=0.3,
    random_state=42,
    stratify=y
)

# ── PIPELINE (scaler + model) ──────────────────────────
pipeline = Pipeline([
    ("scaler", StandardScaler()),
    ("xgb", XGBClassifier(
        n_estimators=300,
        max_depth=4,
        learning_rate=0.1,
        eval_metric="logloss",
        random_state=42
    ))
])

# ── TRAIN ──────────────────────────────────────────────
pipeline.fit(X_train, y_train)

# ── EVALUATE ───────────────────────────────────────────
y_pred = pipeline.predict(X_test)

print("Accuracy :", accuracy_score(y_test, y_pred))
print("F1 Score :", f1_score(y_test, y_pred))
print("\nConfusion Matrix:\n", confusion_matrix(y_test, y_pred))
print("\nClassification Report:\n", classification_report(y_test, y_pred))

# ── SAVE ───────────────────────────────────────────────
joblib.dump(pipeline, "stress_model_pipeline.pkl")
print("\n✅ Model saved as stress_model_pipeline.pkl")