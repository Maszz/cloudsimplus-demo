import os
import pandas as pd
import matplotlib.pyplot as plt

# Paths
CSV_DIR = "output/csv/day"
PLOT_DIR = "output/plots"
SUMMARY_DIR = "output"
os.makedirs(PLOT_DIR, exist_ok=True)

# Month mapping and days
month_map = {
    "1": "jan", "2": "feb", "3": "mar", "4": "apr",
    "5": "may", "6": "jun", "7": "jul", "8": "aug",
    "9": "sep", "10": "oct", "11": "nov", "12": "dec"
}
month_days = {
    "jan": 31, "feb": 28, "mar": 31, "apr": 30,
    "may": 31, "jun": 30, "jul": 31, "aug": 31,
    "sep": 30, "oct": 31, "nov": 30, "dec": 31
}

# Summary data holders
classical_summary = {}
blockchain_summary = {}

# === DO NOT MODIFY: Process each CSV ===
def process_csv(file_path, system_type, month_num):
    df = pd.read_csv(file_path)
    x_label = "Tick" if "Tick" in df.columns else "Day"
    node_columns = df.columns.drop(x_label)

    df_ws = df.copy()
    df_ws[node_columns] = df_ws[node_columns] * 1000  # kWs → Ws

    avg_energy_per_tick = df_ws[node_columns].mean(axis=1)
    avg_of_avg_ws = avg_energy_per_tick.mean()

    total_kWs_per_tick = df[node_columns].sum(axis=1)
    total_energy_kWh = total_kWs_per_tick.sum() / 3600.0  # kWh

    plt.figure(figsize=(12, 6))
    node_cumulative_kWh = {}

    for node in node_columns:
        avg_node_ws = df[node].mean() * 1000
        cum_node_kWh = df[node].sum() / 3600.0
        node_cumulative_kWh[node] = cum_node_kWh

        label = f"{node} (avg={avg_node_ws:.2f} Ws, ∑={cum_node_kWh:.2f} kWh)"
        plt.plot(df[x_label], df[node] * 1000, alpha=0.4, linewidth=1, label=label)

    avg_label = f"Avg of Nodes (avg={avg_of_avg_ws:.2f} Ws)"
    plt.plot(df[x_label], avg_energy_per_tick, color='black', linewidth=2, label=avg_label)
    plt.axhline(y=avg_of_avg_ws, color='red', linestyle='--')

    plt.title(f"Energy Consumption - {system_type} Month {month_num} ({total_energy_kWh:.2f} kWh)")
    plt.xlabel("Second")
    plt.ylabel("Energy (Ws)")
    plt.grid(True)
    plt.legend(fontsize="small", bbox_to_anchor=(1.02, 1), loc='upper left')
    plt.tight_layout()

    plot_file = os.path.join(PLOT_DIR, f"{system_type}_month{month_num}_energy.png")
    plt.savefig(plot_file, bbox_inches='tight')
    plt.close()
    print(f"✅ Saved plot: {plot_file}")

    # Save summary for later
    month_key = month_map[month_num]
    summary = classical_summary if system_type == "classical" else blockchain_summary
    for node, cum_kWh in node_cumulative_kWh.items():
        if node not in summary:
            summary[node] = {}
        summary[node][month_key] = f"{cum_kWh:.2f}"

# === Save summary as kWh table ===
def save_summary_csv(summary_dict, filename):
    df = pd.DataFrame(summary_dict).T
    df.insert(0, "Node", df.index)
    df = df.reindex(columns=["Node"] + [month_map[str(i)] for i in range(1, 13)])
    out_path = os.path.join(SUMMARY_DIR, filename)
    df.to_csv(out_path, index=False)
    print(f"📊 Saved summary CSV: {out_path}")
    return out_path

# === Plot per-node average kW summary across months ===
def plot_summary(summary_csv, system_type):
    df = pd.read_csv(summary_csv)
    df.set_index("Node", inplace=True)
    df_float = df.apply(pd.to_numeric, errors='coerce')

    # Save original kWh separately
    df_kWh = df_float.copy()

    # Convert to kW correctly for display
    df_kW = df_float.copy()
    for month in df_kW.columns:
        hours = month_days[month] * 24
    df_kW[month] = df_kW[month] * hours


    df_t = df_kW.T
    plt.figure(figsize=(14, 7))
    label_info = []

    for node in df_kW.columns:
        plt.plot(df_kW.index, df_kW[node], label=node, alpha=0.4)
        avg_kW = df_kW[node].mean()
        total_kWh = df_kWh[node].sum()
        label_info.append(f"{node} (avg={avg_kW:.2f} kW, ∑={total_kWh:.2f} kWh)")


    monthly_avg = df_t.mean(axis=1)
    avg_all_kw = monthly_avg.mean()
    total_all_kWh = df_float.sum().sum()

    avg_label = f"Avg Line (avg={avg_all_kw:.2f} kW, ∑={total_all_kWh:.2f} kWh)"
    plt.plot(df_t.index, monthly_avg, color='black', linewidth=2, label=avg_label)

    plt.title(f"Monthly Avg Power Summary - {system_type.capitalize()} (avg={avg_all_kw:.2f} kW, ∑={total_all_kWh:.2f} kWh)")
    plt.xlabel("Month")
    plt.ylabel("Average Power (kW)")
    plt.grid(True)

    final_labels = label_info + [avg_label]
    plt.legend(final_labels, fontsize="small", bbox_to_anchor=(1.02, 1), loc='upper left')
    plt.tight_layout()

    plot_file = os.path.join(PLOT_DIR, f"{system_type}_summary.png")
    plt.savefig(plot_file, bbox_inches='tight')
    plt.close()
    print(f"📈 Saved summary plot: {plot_file}")

# === Process all CSVs ===
for file in sorted(os.listdir(CSV_DIR)):
    if file.endswith(".csv"):
        parts = file.replace(".csv", "").split("_")
        if len(parts) != 3:
            continue
        system_type, month_str, _ = parts
        if system_type not in ["classical", "blockchain"]:
            continue
        month_num = month_str.replace("month", "")
        process_csv(os.path.join(CSV_DIR, file), system_type, month_num)

# === Save and plot summary ===
classical_path = save_summary_csv(classical_summary, "classical23.csv")
blockchain_path = save_summary_csv(blockchain_summary, "blockchain23.csv")
plot_summary(classical_path, "classical")
plot_summary(blockchain_path, "blockchain")
