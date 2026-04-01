import pandas as pd
import numpy as np

from sklearn.model_selection import StratifiedKFold, cross_val_score, GridSearchCV
from sklearn.ensemble import RandomForestClassifier
from sklearn.preprocessing import StandardScaler
from sklearn.pipeline import Pipeline

# -------------------------------
# LOAD DATA
# -------------------------------
df = pd.read_csv("clean_mobile_dataset.csv")

print("Dataset shape:", df.shape)
print("\nClass distribution:\n", df["mapped_class"].value_counts())

# -------------------------------
# FEATURE ENGINEERING (NEW)
# -------------------------------
# Add useful derived features
df["dwell_flight_ratio"] = df["mean_dwell"] / (df["mean_flight"] + 1e-6)
df["speed_error_ratio"] = df["typing_speed"] / (df["backspace_rate"] + 1e-6)

# -------------------------------
# FEATURES & LABEL
# -------------------------------
X = df[[
    "mean_dwell",
    "std_dwell",
    "mean_flight",
    "std_flight",
    "typing_speed",
    "backspace_rate",
    "pause_count",
    "mean_pressure",
    "gyro_std",
    "dwell_flight_ratio",      # NEW
    "speed_error_ratio"        # NEW
]]

y = df["mapped_class"]

# -------------------------------
# MODEL PIPELINE
# -------------------------------
pipeline = Pipeline([
    ("scaler", StandardScaler()),
    ("rf", RandomForestClassifier(class_weight="balanced", random_state=42))
])

# -------------------------------
# HYPERPARAMETER TUNING (IMPORTANT)
# -------------------------------
param_grid = {
    "rf__n_estimators": [100, 200, 300],
    "rf__max_depth": [None, 5, 10, 15],
    "rf__min_samples_split": [2, 5, 10],
    "rf__min_samples_leaf": [1, 2, 4]
}

skf = StratifiedKFold(n_splits=5, shuffle=True, random_state=42)

grid = GridSearchCV(
    pipeline,
    param_grid,
    cv=skf,
    scoring="f1_weighted",
    n_jobs=-1
)

# -------------------------------
# TRAIN
# -------------------------------
grid.fit(X, y)

print("\nBest Parameters:", grid.best_params_)

# -------------------------------
# FINAL EVALUATION
# -------------------------------
best_model = grid.best_estimator_

accuracy = cross_val_score(best_model, X, y, cv=skf, scoring="accuracy")
f1 = cross_val_score(best_model, X, y, cv=skf, scoring="f1_weighted")

print("\nFinal Results:")
print("Accuracy:", np.mean(accuracy))
print("F1-score:", np.mean(f1))