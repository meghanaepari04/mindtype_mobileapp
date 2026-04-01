import pandas as pd
import numpy as np

df = pd.read_csv("final_mobile_dataset.csv")

print("Before cleaning:", df.shape)

# Remove duplicates
df = df.drop_duplicates()

# Remove missing values
df = df.dropna()

# -------------------------------
# FIX INF VALUES
# -------------------------------
df = df.replace([np.inf, -np.inf], np.nan)
df = df.dropna()

# -------------------------------
# APPLY CLIP ONLY TO NUMERIC COLUMNS
# -------------------------------
numeric_cols = df.select_dtypes(include=[np.number]).columns
df[numeric_cols] = df[numeric_cols].clip(-1e6, 1e6)

# -------------------------------
# LABEL CHECK
# -------------------------------
print("\nLabel Distribution:\n", df["mapped_class"].value_counts())

# Save cleaned dataset
df.to_csv("clean_mobile_dataset.csv", index=False)

print("\nAfter cleaning:", df.shape)