import os
import pandas as pd
import matplotlib.pyplot as plt

# Constants
SUMMARY_DIR = "output"
PLOT_DIR = "output/plots"
os.makedirs(PLOT_DIR, exist_ok=True)

# Month names and their hour counts
month_map = {
    "jan": 31, "feb": 28, "mar": 31, "apr": 30, "may": 31, "jun": 30,
    "jul": 31, "aug": 31, "sep": 30, "oct": 31, "nov": 30, "dec": 31
}
months = list(month_map.keys())
month_hours = [h * 24 for h in month_map.values()]  # Convert days to hours

def load_and_compute_power(filepath):
    df = pd.read_csv(filepath)
    df.set_index("Node", inplace=True)

    # Sum of energy (kWh) per month
    monthly_kwh = df.sum()
    # Convert to average power (kW)
    monthly_kw = [kwh / hr for kwh, hr in zip(monthly_kwh, month_hours)]
    return pd.Series(monthly_kw, index=months)

# Load data
classical_path = os.path.join(SUMMARY_DIR, "classical23.csv")
blockchain_path = os.path.join(SUMMARY_DIR, "blockchain23.csv")

classical_kw = load_and_compute_power(classical_path)
blockchain_kw = load_and_compute_power(blockchain_path)

# Plotting
x = range(len(months))
width = 0.35

plt.figure(figsize=(14, 6))
plt.bar([i - width/2 for i in x], classical_kw, width=width, label='Classical', color='skyblue')
plt.bar([i + width/2 for i in x], blockchain_kw, width=width, label='Blockchain', color='salmon')

# Averages
avg_classical = classical_kw.mean()
avg_blockchain = blockchain_kw.mean()
plt.axhline(avg_classical, color='blue', linestyle='--', linewidth=1, label=f'Classical Avg = {avg_classical:.2f} kW')
plt.axhline(avg_blockchain, color='red', linestyle='--', linewidth=1, label=f'Blockchain Avg = {avg_blockchain:.2f} kW')

# Labels
plt.xticks(ticks=x, labels=months, fontsize=10)
plt.ylabel("Average Power (kW)")
plt.title("Monthly Average Power Consumption (kW) - Blockchain vs Classical")
plt.grid(True, axis='y', linestyle='--', alpha=0.6)
plt.legend(bbox_to_anchor=(1.02, 1), loc="upper left", borderaxespad=0)
plt.tight_layout()

# Save
plot_path = os.path.join(PLOT_DIR, "compare_power_kW.png")
plt.savefig(plot_path, bbox_inches='tight')
plt.close()
print(f"✅ Saved power comparison plot to {plot_path}")
