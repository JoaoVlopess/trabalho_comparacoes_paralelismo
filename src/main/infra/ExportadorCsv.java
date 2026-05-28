package main.infra;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Locale;

public class ExportadorCsv {

    public static void exportar(List<Resultado> resultados, String caminhoArquivo) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(caminhoArquivo))) {
            writer.println("metodo,amostra,palavra_alvo,contagem,tempo_ms,num_threads");
            for (Resultado r : resultados) {
                writer.printf(Locale.US, "%s,%s,%s,%d,%.4f,%d%n",
                        r.metodo, r.nomeAmostra, r.palavraAlvo,
                        r.contagem, r.tempoMs, r.numThreads);
            }
        }
        System.out.println("\nResultados exportados para: " + caminhoArquivo);
    }
}

// Lopes
