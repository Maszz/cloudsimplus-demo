import os
import pandas as pd
import matplotlib.pyplot as plt

# === Config ===
SUMMARY_CSV = "output/classical23.csv"  # <-- change if needed
PLOT_PATH = "output/plots/classical_summary_kw.png"
os.makedirs("output/plots", exist_ok=True)

month_labels = ["jan", "feb", "mar", "apr", "may", "jun",
                "jul", "aug", "sep", "oct", "nov", "dec"]
month_days = {
    "jan": 31, "feb": 28, "mar": 31, "apr": 30, "may": 31, "jun": 30,
    "jul": 31, "aug": 31, "sep": 30, "oct": 31, "nov": 30, "dec": 31
}

# === Load CSV ===
df = pd.read_csv(SUMMARY_CSV)
df.set_index("Node", inplace=True)
df = df[month_labels].apply(pd.to_numeric, errors="coerce")

# === Plot ===
plt.figure(figsize=(14, 7))
labels = []

total_kWh_all_nodes = 0.0

for node in df.index:
    plt.plot(month_labels, df.loc[node], label=node, alpha=0.4)

    # Each node's energy per month = kW * hours
    node_monthly_kWh = df.loc[node] * [month_days[m] * 24 for m in month_labels]
    node_total_kWh = node_monthly_kWh.sum()
    node_avg_kWh = node_monthly_kWh.mean()

    total_kWh_all_nodes += node_total_kWh
    labels.append(f"{node} (avg={node_avg_kWh:.2f} kWh, ∑={node_total_kWh:.2f} kWh)")

# === Monthly average across nodes (kW)
monthly_avg_kW = df.mean(axis=0)
avg_all_kW = monthly_avg_kW.mean()

avg_label = f"Avg Line (avg={avg_all_kW:.2f} kW, ∑={total_kWh_all_nodes:.2f} kWh)"
plt.plot(month_labels, monthly_avg_kW, color="black", linewidth=2, label=avg_label)

# === Final touches ===
plt.title(f"Monthly Avg Power Summary - Classical (∑={total_kWh_all_nodes:.2f} kWh)")
plt.xlabel("Month")
plt.ylabel("Power (kW)")
plt.grid(True)
plt.legend(labels + [avg_label], fontsize="small", bbox_to_anchor=(1.02, 1), loc="upper left")
plt.tight_layout()
plt.savefig(PLOT_PATH, bbox_inches='tight')
plt.close()

print(f"📈 Fixed summary plot saved: {PLOT_PATH}")
