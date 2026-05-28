import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
import matplotlib.ticker as ticker
import os

CSV_PATH = "resultados.csv"
OUTPUT_DIR = "graficos"

os.makedirs(OUTPUT_DIR, exist_ok=True)

df = pd.read_csv(CSV_PATH)
df.columns = df.columns.str.strip()

ORDEM_AMOSTRAS = ["Pequena", "Media", "Grande"]
CORES_METODOS = {
    "SerialCPU":      "#2196F3",
    "ParallelCPU-2T": "#4CAF50",
    "ParallelCPU-4T": "#FF9800",
    "ParallelCPU-8T": "#F44336",
}
ORDEM_METODOS = list(CORES_METODOS.keys())

media = (
    df.groupby(["metodo", "amostra"])["tempo_ms"]
    .mean()
    .reset_index()
    .rename(columns={"tempo_ms": "tempo_medio"})
)

# ── 1. Barras agrupadas: tempo médio por método para cada amostra ─────────────
fig, axes = plt.subplots(1, 3, figsize=(15, 5), sharey=False)
fig.suptitle("Tempo Médio de Execução por Método e Tamanho de Entrada", fontsize=14, fontweight="bold")

for ax, amostra in zip(axes, ORDEM_AMOSTRAS):
    subset = media[media["amostra"] == amostra].set_index("metodo").reindex(ORDEM_METODOS)
    bars = ax.bar(
        subset.index,
        subset["tempo_medio"],
        color=[CORES_METODOS[m] for m in subset.index],
        edgecolor="white",
        width=0.6,
    )
    for bar in bars:
        h = bar.get_height()
        ax.text(bar.get_x() + bar.get_width() / 2, h + h * 0.02,
                f"{h:.3f}", ha="center", va="bottom", fontsize=8)
    ax.set_title(f"Amostra {amostra}", fontsize=11)
    ax.set_ylabel("Tempo (ms)" if amostra == "Pequena" else "")
    ax.set_xlabel("Método")
    ax.tick_params(axis="x", rotation=25)
    ax.grid(axis="y", alpha=0.3)
    ax.set_ylim(0, subset["tempo_medio"].max() * 1.25)

plt.tight_layout()
plt.savefig(f"{OUTPUT_DIR}/1_tempo_medio_por_metodo.png", dpi=150, bbox_inches="tight")
plt.close()
print("Salvo: 1_tempo_medio_por_metodo.png")

# ── 2. Linhas: tempo médio x número de threads por amostra ───────────────────
threads_map = {"SerialCPU": 1, "ParallelCPU-2T": 2, "ParallelCPU-4T": 4, "ParallelCPU-8T": 8}
media["threads"] = media["metodo"].map(threads_map)

fig, ax = plt.subplots(figsize=(9, 5))
for amostra in ORDEM_AMOSTRAS:
    sub = media[media["amostra"] == amostra].sort_values("threads")
    ax.plot(sub["threads"], sub["tempo_medio"], marker="o", linewidth=2, label=f"Amostra {amostra}")

ax.set_title("Tempo Médio vs Número de Threads por Entrada", fontsize=13, fontweight="bold")
ax.set_xlabel("Número de Threads")
ax.set_ylabel("Tempo Médio (ms)")
ax.set_xticks([1, 2, 4, 8])
ax.legend()
ax.grid(alpha=0.3)
plt.tight_layout()
plt.savefig(f"{OUTPUT_DIR}/2_tempo_vs_threads.png", dpi=150, bbox_inches="tight")
plt.close()
print("Salvo: 2_tempo_vs_threads.png")

# ── 3. Speedup relativo ao Serial por amostra ────────────────────────────────
serial_tempo = media[media["metodo"] == "SerialCPU"].set_index("amostra")["tempo_medio"]

fig, ax = plt.subplots(figsize=(9, 5))
for amostra in ORDEM_AMOSTRAS:
    sub = media[media["amostra"] == amostra].sort_values("threads")
    speedup = serial_tempo[amostra] / sub["tempo_medio"].values
    ax.plot(sub["threads"].values, speedup, marker="s", linewidth=2, label=f"Amostra {amostra}")

ax.axhline(1.0, color="gray", linestyle="--", linewidth=1, label="Speedup = 1 (baseline)")
ax.set_title("Speedup em Relação ao Serial por Entrada", fontsize=13, fontweight="bold")
ax.set_xlabel("Número de Threads")
ax.set_ylabel("Speedup (Serial / Paralelo)")
ax.set_xticks([1, 2, 4, 8])
ax.legend()
ax.grid(alpha=0.3)
plt.tight_layout()
plt.savefig(f"{OUTPUT_DIR}/3_speedup.png", dpi=150, bbox_inches="tight")
plt.close()
print("Salvo: 3_speedup.png")

# ── 4. Box plot: distribuição dos tempos por método e amostra ─────────────────
fig, axes = plt.subplots(1, 3, figsize=(15, 5), sharey=False)
fig.suptitle("Distribuição dos Tempos de Execução (3 execuções por configuração)", fontsize=13, fontweight="bold")

for ax, amostra in zip(axes, ORDEM_AMOSTRAS):
    grupos = [df[(df["amostra"] == amostra) & (df["metodo"] == m)]["tempo_ms"].values for m in ORDEM_METODOS]
    bp = ax.boxplot(grupos, patch_artist=True, widths=0.5)
    for patch, metodo in zip(bp["boxes"], ORDEM_METODOS):
        patch.set_facecolor(CORES_METODOS[metodo])
        patch.set_alpha(0.8)
    ax.set_xticklabels(ORDEM_METODOS, rotation=25, fontsize=8)
    ax.set_title(f"Amostra {amostra}", fontsize=11)
    ax.set_ylabel("Tempo (ms)" if amostra == "Pequena" else "")
    ax.grid(axis="y", alpha=0.3)

plt.tight_layout()
plt.savefig(f"{OUTPUT_DIR}/4_boxplot_distribuicao.png", dpi=150, bbox_inches="tight")
plt.close()
print("Salvo: 4_boxplot_distribuicao.png")

# ── 5. Heatmap: tempo médio (amostra × método) ───────────────────────────────
pivot = media.pivot(index="amostra", columns="metodo", values="tempo_medio").reindex(
    index=ORDEM_AMOSTRAS, columns=ORDEM_METODOS
)

fig, ax = plt.subplots(figsize=(8, 4))
im = ax.imshow(pivot.values, cmap="YlOrRd", aspect="auto")
plt.colorbar(im, ax=ax, label="Tempo Médio (ms)")
ax.set_xticks(range(len(ORDEM_METODOS)))
ax.set_xticklabels(ORDEM_METODOS, rotation=20)
ax.set_yticks(range(len(ORDEM_AMOSTRAS)))
ax.set_yticklabels(ORDEM_AMOSTRAS)
ax.set_title("Heatmap: Tempo Médio por Amostra e Método", fontsize=13, fontweight="bold")

for i in range(len(ORDEM_AMOSTRAS)):
    for j in range(len(ORDEM_METODOS)):
        val = pivot.values[i, j]
        ax.text(j, i, f"{val:.3f}", ha="center", va="center", fontsize=9,
                color="black" if val < pivot.values.max() * 0.6 else "white")

plt.tight_layout()
plt.savefig(f"{OUTPUT_DIR}/5_heatmap_tempo_medio.png", dpi=150, bbox_inches="tight")
plt.close()
print("Salvo: 5_heatmap_tempo_medio.png")

print(f"\nTodos os gráficos salvos em '{OUTPUT_DIR}/'")
