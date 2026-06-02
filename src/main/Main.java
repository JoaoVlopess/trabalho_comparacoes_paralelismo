package main;

import main.algoritmos.ParallelCPU;
import main.algoritmos.ParallelGPU;
import main.algoritmos.SerialCPU;
import main.infra.ExportadorCsv;
import main.infra.LeitorTexto;
import main.infra.Resultado;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Main {

    private static final int    REPETICOES    = 3;
    private static final int[]  CONFIGS_THREADS = {2, 4, 8};
    private static final String PALAVRA_ALVO  = "que";

    public static void main(String[] args) throws IOException {
        String[][] amostras = {
            {"data/amostra_pequena.txt", "Pequena"},
            {"data/amostra_media.txt",   "Media"},
            {"data/amostra_grande.txt",  "Grande"},
        };

        List<Resultado> todos = new ArrayList<>();

        for (String[] amostra : amostras) {
            String caminho = amostra[0];
            String nome    = amostra[1];

            System.out.println("\n========================================");
            System.out.println("Amostra: " + nome);
            System.out.println("========================================");

            String[] palavras = LeitorTexto.carregarETratarTexto(caminho);
            System.out.println("Total de palavras carregadas: " + palavras.length);

            // ── Versão serial ─────────────────────────────────────────────────
            System.out.println("\n[SerialCPU]");
            for (int i = 0; i < REPETICOES; i++) {
                todos.add(SerialCPU.executar(palavras, PALAVRA_ALVO, nome));
            }

            // ── Versão paralela CPU com diferentes quantidades de threads ──────
            for (int numThreads : CONFIGS_THREADS) {
                System.out.println("\n[ParallelCPU - " + numThreads + " threads]");
                for (int i = 0; i < REPETICOES; i++) {
                    todos.add(ParallelCPU.executar(palavras, PALAVRA_ALVO, nome, numThreads));
                }
            }

            // ── Versão paralela GPU ────────────────────────────────────────────
            System.out.println("\n[ParallelGPU]");
            for (int i = 0; i < REPETICOES; i++) {
                todos.add(ParallelGPU.executar(palavras, PALAVRA_ALVO, nome));
            }
        }

        ExportadorCsv.exportar(todos, "resultados.csv");
        System.out.println("\nConcluído! Resultados em resultados.csv");
        System.out.println("Execute: python gerar_graficos.py  (para gerar os gráficos)");
    }
}

// lopes e peter
