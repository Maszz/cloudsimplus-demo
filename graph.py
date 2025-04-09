import os
import pandas as pd
import matplotlib.pyplot as plt

# Fixed directory paths
CSV_DIR = "output/csv/day"
PLOT_DIR = "output/plots"
SUMMARY_DIR = "output"
os.makedirs(PLOT_DIR, exist_ok=True)

# Month mapping
month_map = {
    "1": "jan", "2": "feb", "3": "mar", "4": "apr",
    "5": "may", "6": "jun", "7": "jul", "8": "aug",
    "9": "sep", "10": "oct", "11": "nov", "12": "dec"
}

# Summary collectors
classical_summary = {}
blockchain_summary = {}

def process_csv(file_path, system_type, month_num):
    df = pd.read_csv(file_path)
    x_label = "Tick" if "Tick" in df.columns else "Day"
    node_columns = df.columns.drop(x_label)

    avg_energy_per_tick = df[node_columns].mean(axis=1)
    avg_of_avg = avg_energy_per_tick.mean()
    sum_of_avg = avg_energy_per_tick.sum()
    total_energy = df[node_columns].sum(axis=1)
    total_sum = total_energy.sum()

    plt.figure(figsize=(12, 6))
    node_cumulative = {}

    for node in node_columns:
        avg_node = df[node].mean()
        cum_node = df[node].sum()
        node_cumulative[node] = cum_node
        label = f"{node} (avg={avg_node:.2f}, ∑={cum_node:.2f})"
        plt.plot(df[x_label], df[node], alpha=0.4, linewidth=1, label=label)

    avg_label = f"Avg of Nodes (avg={avg_of_avg:.2f}, ∑={sum_of_avg:.2f})"
    total_label = f"Total (∑={total_sum:.2f})"
    plt.plot(df[x_label], avg_energy_per_tick, color='black', linewidth=2, label=avg_label)
    # plt.plot(df[x_label], total_energy, color='orange', linestyle='--', linewidth=2, label=total_label)
    plt.axhline(y=avg_of_avg, color='red', linestyle='--')

    plt.title(f"Energy Consumption - {system_type} Month {month_num} ({total_sum:.2f} kW)")
    plt.xlabel(x_label)
    plt.ylabel("Energy (kWh)")
    plt.grid(True)
    plt.legend(fontsize="small", bbox_to_anchor=(1.02, 1), loc='upper left', borderaxespad=0)
    plt.tight_layout()

    plot_file = os.path.join(PLOT_DIR, f"{system_type}_month{month_num}_energy.png")
    plt.savefig(plot_file, bbox_inches='tight')
    plt.close()
    print(f"✅ Saved: {plot_file}")

    # Save node cumulative energy to summary
    month_key = month_map[month_num]
    summary = classical_summary if system_type == "classical" else blockchain_summary
    for node, cum in node_cumulative.items():
        if node not in summary:
            summary[node] = {}
        summary[node][month_key] = f"{cum:.2f}"

def save_summary_csv(summary_dict, filename):
    df = pd.DataFrame(summary_dict).T
    df.insert(0, "Node", df.index)
    df = df.reindex(columns=["Node"] + [month_map[str(i)] for i in range(1, 13)])
    out_path = os.path.join(SUMMARY_DIR, filename)
    df.to_csv(out_path, index=False)
    print(f"📊 Saved summary: {filename}")
    return out_path

def plot_summary(summary_path, system_type):
    df = pd.read_csv(summary_path)
    df.set_index("Node", inplace=True)
    df_float = df.apply(pd.to_numeric, errors='coerce')
    df_t = df_float.T

    plt.figure(figsize=(14, 7))
    label_info = []

    for node in df_t.columns:
        plt.plot(df_t.index, df_t[node], label=node, alpha=0.4)
        node_avg = df_t[node].mean()
        node_sum = df_t[node].sum()
        label_info.append(f"{node} (avg={node_avg:.2f}, ∑={node_sum:.2f})")

    monthly_avg = df_t.mean(axis=1)
    monthly_total = df_t.sum(axis=1)
    avg_all = monthly_avg.mean()
    sum_all = monthly_avg.sum()
    total_sum = monthly_total.sum()

    avg_label = f"Avg Line (avg={avg_all:.2f}, ∑={sum_all:.2f})"
    total_label = f"Total Line (∑={total_sum:.2f})"
    plt.plot(df_t.index, monthly_avg, color='black', linewidth=2, label=avg_label)

    plt.title(f"Monthly Cumulative Energy Summary - {system_type.capitalize()} ({total_sum:.2f})")
    plt.xlabel("Month")
    plt.ylabel("Cumulative Energy (kWh)")
    plt.grid(True)

    final_labels = label_info + [avg_label, total_label]
    plt.legend(final_labels, fontsize="small", bbox_to_anchor=(1.02, 1), loc='upper left', borderaxespad=0)
    plt.tight_layout()

    plot_file = os.path.join(PLOT_DIR, f"{system_type}_summary.png")
    plt.savefig(plot_file, bbox_inches='tight')
    plt.close()
    print(f"📈 Saved summary plot: {plot_file}")

# Process all monthly CSVs
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

# Save and plot summaries
classical_path = save_summary_csv(classical_summary, "classical23.csv")
blockchain_path = save_summary_csv(blockchain_summary, "blockchain23.csv")
plot_summary(classical_path, "classical")
plot_summary(blockchain_path, "blockchain")
