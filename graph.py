# scripts/plot_energy.py
import os
import sys
import pandas as pd
import matplotlib.pyplot as plt

def plot_csv(csv_path):
    df = pd.read_csv(csv_path)
    month = os.path.basename(csv_path).split('_')[1]
    days = df['Day']
    energy = df['Energy(kWh)']

    plt.figure()
    plt.plot(days, energy, marker='o')
    plt.title(f"Energy Consumption - Month {month}")
    plt.xlabel("Day")
    plt.ylabel("Energy (kWh)")
    plt.grid(True)
    plt.tight_layout()

    out_dir = "output/plots"
    os.makedirs(out_dir, exist_ok=True)
    out_path = os.path.join(out_dir, f"month_{month}_energy_plot.png")
    plt.savefig(out_path)
    plt.close()
    print(f"✅ Plot saved to {out_path}")

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python graph.py <csv_directory>")
        sys.exit(1)

    csv_dir = sys.argv[1]
    for file in os.listdir(csv_dir):
        if file.endswith(".csv"):
            plot_csv(os.path.join(csv_dir, file))
