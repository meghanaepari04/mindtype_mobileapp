import pandas as pd
import glob

# Read from datasets folder
files = glob.glob("C:/Users/admin/OneDrive/Desktop/MindType_Mobile/mindtype_mobileapp/mb_dataset/datasets/*.csv")

print("Files found:", files)

df_list = []

for file in files:
    df = pd.read_csv(file)
    df_list.append(df)

combined_df = pd.concat(df_list, ignore_index=True)

# Save merged file
combined_df.to_csv("final_mobile_dataset.csv", index=False)

print("Merged dataset shape:", combined_df.shape)