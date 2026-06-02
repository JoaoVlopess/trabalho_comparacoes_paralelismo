"""
gerar_graficos.py — atualizado para incluir ParallelGPU
========================================================
Lê resultados.csv gerado pelo Main.java e produz 5 gráficos comparativos.

Dependências:
    pip install pandas matplotlib

Uso:
    python gerar_graficos.py
    (execute na raiz do projeto, onde fica resultados.csv)
"""

import os
import pandas as pd
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import matplotlib.ticker as ticker
import numpy as np

# ─── Configurações ────────────────────────────────────────────────────────────
CSV_PATH    = "resultados.csv"
OUTPUT_DIR  = "graficos"

os.makedirs(OUTPUT_DIR, exist_ok=True)

df = pd.read_csv(CSV_PATH)
df.columns = df.columns.str.strip()

ORDEM_AMOSTRAS = ["Pequena", "Media", "Grande"]

# Detecta automaticamente quais métodos existem no CSV
METODOS_CPU = [m for m in ["SerialCPU", "ParallelCPU-2T", "ParallelCPU-4T", "ParallelCPU-8T"]
               if m in df["metodo"].unique()]
TEM_GPU = "ParallelGPU" in df["metodo"].unique()
ORDEM_METODOS = METODOS_CPU + (["ParallelGPU"] if TEM_GPU else [])

# Paleta de cores
CORES = {
    "SerialCPU":      "#2196F3",
    "ParallelCPU-2T": "#4CAF50",
    "ParallelCPU-4T": "#FF9800",
    "ParallelCPU-8T": "#F44336",
    "ParallelGPU":    "#9C27B0",
}

print(f"Métodos encontrados: {ORDEM_METODOS}")
print(f"GPU presente: {TEM_GPU}")

media = (
    df.groupby(["metodo", "amostra"])["tempo_ms"]
    .mean()
    .reset_index()
    .rename(columns={"tempo_ms": "tempo_medio"})
)

# ═══════════════════════════════════════════════════════════════════════════════
# Gráfico 1 — Barras agrupadas: tempo médio por método e amostra
# ═══════════════════════════════════════════════════════════════════════════════
fig, axes = plt.subplots(1, 3, figsize=(16, 6), sharey=False)
fig.suptitle("Tempo Médio de Execução por Método e Tamanho de Entrada\n"
             "(CPU Serial vs CPU Paralela vs GPU)", fontsize=13, fontweight="bold")

for ax, amostra in zip(axes, ORDEM_AMOSTRAS):
    subset = media[media["amostra"] == amostra].set_index("metodo").reindex(ORDEM_METODOS)
    vals = subset["tempo_medio"].fillna(0)
    bars = ax.bar(
        range(len(ORDEM_METODOS)),
        vals,
        color=[CORES.get(m, "#888") for m in ORDEM_METODOS],
        edgecolor="white",
        linewidth=0.8,
        width=0.6,
    )
    for bar, val in zip(bars, vals):
        if val > 0:
            ax.text(bar.get_x() + bar.get_width() / 2,
                    bar.get_height() + vals.max() * 0.02,
                    f"{val:.3f}", ha="center", va="bottom", fontsize=7.5)

    ax.set_title(f"Amostra {amostra}", fontsize=11)
    ax.set_ylabel("Tempo (ms)" if amostra == "Pequena" else "")
    ax.set_xticks(range(len(ORDEM_METODOS)))
    ax.set_xticklabels(ORDEM_METODOS, rotation=30, ha="right", fontsize=8)
    ax.grid(axis="y", alpha=0.3, linestyle="--")
    ax.set_axisbelow(True)

plt.tight_layout()
plt.savefig(f"{OUTPUT_DIR}/1_tempo_medio_por_metodo.png", dpi=150, bbox_inches="tight")
plt.close()
print("✓ 1_tempo_medio_por_metodo.png")

# ═══════════════════════════════════════════════════════════════════════════════
# Gráfico 2 — Linhas: tempo médio x número de threads (somente CPU)
# ═══════════════════════════════════════════════════════════════════════════════
threads_map = {"SerialCPU": 1, "ParallelCPU-2T": 2, "ParallelCPU-4T": 4, "ParallelCPU-8T": 8}
media_cpu = media[media["metodo"].isin(METODOS_CPU)].copy()
media_cpu["threads"] = media_cpu["metodo"].map(threads_map)

fig, ax = plt.subplots(figsize=(9, 5))
for amostra in ORDEM_AMOSTRAS:
    sub = media_cpu[media_cpu["amostra"] == amostra].sort_values("threads")
    ax.plot(sub["threads"], sub["tempo_medio"],
            marker="o", linewidth=2, label=f"Amostra {amostra}")

ax.set_title("Tempo Médio (CPU) vs Número de Threads", fontsize=13, fontweight="bold")
ax.set_xlabel("Número de Threads")
ax.set_ylabel("Tempo Médio (ms)")
ax.set_xticks([1, 2, 4, 8])
ax.legend()
ax.grid(alpha=0.3, linestyle="--")
ax.set_axisbelow(True)
plt.tight_layout()
plt.savefig(f"{OUTPUT_DIR}/2_tempo_vs_threads.png", dpi=150, bbox_inches="tight")
plt.close()
print("✓ 2_tempo_vs_threads.png")

# ═══════════════════════════════════════════════════════════════════════════════
# Gráfico 3 — Speedup relativo ao Serial (CPU + GPU)
# ═══════════════════════════════════════════════════════════════════════════════
serial_tempo = media[media["metodo"] == "SerialCPU"].set_index("amostra")["tempo_medio"]
METODOS_SPEEDUP = [m for m in ORDEM_METODOS if m != "SerialCPU"]

fig, ax = plt.subplots(figsize=(10, 5))
for metodo in METODOS_SPEEDUP:
    speedups = []
    for amostra in ORDEM_AMOSTRAS:
        row = media[(media["metodo"] == metodo) & (media["amostra"] == amostra)]
        if not row.empty and amostra in serial_tempo.index:
            speedups.append(serial_tempo[amostra] / row["tempo_medio"].values[0])
        else:
            speedups.append(None)

    x = range(len(ORDEM_AMOSTRAS))
    ax.plot(x, speedups, marker="o", linewidth=2,
            label=metodo, color=CORES.get(metodo, "#888"))
    for xi, yi in zip(x, speedups):
        if yi is not None:
            ax.annotate(f"{yi:.2f}×", (xi, yi),
                        textcoords="offset points", xytext=(0, 8),
                        ha="center", fontsize=8, color=CORES.get(metodo, "#888"))

ax.axhline(1.0, color="gray", linestyle="--", linewidth=1, label="baseline Serial (1×)")
ax.set_title("Speedup em Relação ao SerialCPU", fontsize=13, fontweight="bold")
ax.set_xlabel("Amostra")
ax.set_ylabel("Speedup")
ax.set_xticks(range(len(ORDEM_AMOSTRAS)))
ax.set_xticklabels(ORDEM_AMOSTRAS)
ax.legend(fontsize=9)
ax.grid(alpha=0.3, linestyle="--")
ax.set_axisbelow(True)
plt.tight_layout()
plt.savefig(f"{OUTPUT_DIR}/3_speedup.png", dpi=150, bbox_inches="tight")
plt.close()
print("✓ 3_speedup.png")

# ═══════════════════════════════════════════════════════════════════════════════
# Gráfico 4 — Boxplot: distribuição dos tempos por método e amostra
# ═══════════════════════════════════════════════════════════════════════════════
fig, axes = plt.subplots(1, 3, figsize=(16, 5), sharey=False)
fig.suptitle("Distribuição dos Tempos de Execução (3 amostras por configuração)",
             fontsize=13, fontweight="bold")

for ax, amostra in zip(axes, ORDEM_AMOSTRAS):
    grupos = [df[(df["amostra"] == amostra) & (df["metodo"] == m)]["tempo_ms"].values
              for m in ORDEM_METODOS]
    bp = ax.boxplot(grupos, patch_artist=True, widths=0.5,
                    medianprops={"color": "black", "linewidth": 2})
    for patch, metodo in zip(bp["boxes"], ORDEM_METODOS):
        patch.set_facecolor(CORES.get(metodo, "#888"))
        patch.set_alpha(0.8)

    ax.set_xticks(range(1, len(ORDEM_METODOS) + 1))
    ax.set_xticklabels(ORDEM_METODOS, rotation=30, ha="right", fontsize=8)
    ax.set_title(f"Amostra {amostra}", fontsize=11)
    ax.set_ylabel("Tempo (ms)" if amostra == "Pequena" else "")
    ax.grid(axis="y", alpha=0.3, linestyle="--")
    ax.set_axisbelow(True)

plt.tight_layout()
plt.savefig(f"{OUTPUT_DIR}/4_boxplot_distribuicao.png", dpi=150, bbox_inches="tight")
plt.close()
print("✓ 4_boxplot_distribuicao.png")

# ═══════════════════════════════════════════════════════════════════════════════
# Gráfico 5 — Heatmap: tempo médio (amostra × método)
# ═══════════════════════════════════════════════════════════════════════════════
pivot = (media.pivot(index="amostra", columns="metodo", values="tempo_medio")
              .reindex(index=ORDEM_AMOSTRAS, columns=ORDEM_METODOS))

fig, ax = plt.subplots(figsize=(max(8, len(ORDEM_METODOS) * 1.6), 4))
im = ax.imshow(pivot.values, cmap="YlOrRd", aspect="auto")
plt.colorbar(im, ax=ax, label="Tempo Médio (ms)")
ax.set_xticks(range(len(ORDEM_METODOS)))
ax.set_xticklabels(ORDEM_METODOS, rotation=25, ha="right")
ax.set_yticks(range(len(ORDEM_AMOSTRAS)))
ax.set_yticklabels(ORDEM_AMOSTRAS)
ax.set_title("Heatmap: Tempo Médio por Amostra e Método", fontsize=13, fontweight="bold")

vmax = np.nanmax(pivot.values)
for i in range(len(ORDEM_AMOSTRAS)):
    for j in range(len(ORDEM_METODOS)):
        val = pivot.values[i, j]
        if not np.isnan(val):
            cor = "white" if val > vmax * 0.6 else "black"
            ax.text(j, i, f"{val:.3f}", ha="center", va="center", fontsize=9, color=cor)

plt.tight_layout()
plt.savefig(f"{OUTPUT_DIR}/5_heatmap_tempo_medio.png", dpi=150, bbox_inches="tight")
plt.close()
print("✓ 5_heatmap_tempo_medio.png")

print(f"\nTodos os gráficos salvos em '{OUTPUT_DIR}/'")
