package main.algoritmos;

import main.infra.Resultado;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ParallelCPU {

    public static Resultado executar(String[] palavras, String palavraAlvo, String nomeAmostra, int numThreads) {
        long tempoInicio = System.nanoTime();

        // Cria um pool de threads fixo com o número de núcleos desejado
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        int tamanhoTotal = palavras.length;
        int tamanhoFatia = (int) Math.ceil((double) tamanhoTotal / numThreads);

        List<Future<Integer>> resultadosFuturos = new ArrayList<>();

        // Divisão do trabalho (Fatiamento do Array)
        for (int i = 0; i < numThreads; i++) {
            final int inicio = i * tamanhoFatia;
            final int fim = Math.min(inicio + tamanhoFatia, tamanhoTotal);

            // Se a fatia for válida, submete para uma thread processar
            if (inicio < tamanhoTotal) {
                Callable<Integer> tarefa = () -> {
                    int contagemLocal = 0;
                    for (int j = inicio; j < fim; j++) {
                        if (palavras[j].equals(palavraAlvo)) {
                            contagemLocal++;
                        }
                    }
                    return contagemLocal;
                };
                resultadosFuturos.add(executor.submit(tarefa));
            }
        }

        // Redução: Aglutinar os resultados das threads
        int contagemTotal = 0;
        try {
            for (Future<Integer> resultado : resultadosFuturos) {
                contagemTotal += resultado.get(); // Bloqueia até a thread terminar
            }
        } catch (Exception e) {
            System.err.println("Erro na execução paralela: " + e.getMessage());
        } finally {
            // Extremamente importante no S.O.: fechar o pool para liberar os recursos do sistema
            executor.shutdown();
        }

        long tempoFim = System.nanoTime();
        double tempoMs = (tempoFim - tempoInicio) / 1_000_000.0;

        System.out.printf("ParallelCPU (%d Threads) [%s]: %d ocorrências em %.2f ms%n",
                numThreads, nomeAmostra, contagemTotal, tempoMs);

        return new Resultado("ParallelCPU-" + numThreads + "T", nomeAmostra, palavraAlvo,
                contagemTotal, tempoMs, numThreads);
    }
}

// Lopes
