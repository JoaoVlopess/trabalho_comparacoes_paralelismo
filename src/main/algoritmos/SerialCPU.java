package main.algoritmos;

import main.infra.Resultado;

public class SerialCPU {

    public static Resultado executar(String[] palavras, String palavraAlvo, String nomeAmostra) {
        long tempoInicio = System.nanoTime();

        int contagem = 0;
        for (String palavra : palavras) {
            if (palavra.equals(palavraAlvo)) {
                contagem++;
            }
        }

        long tempoFim = System.nanoTime();
        double tempoMs = (tempoFim - tempoInicio) / 1_000_000.0;

        System.out.printf("SerialCPU [%s]: %d ocorrências em %.2f ms%n",
                nomeAmostra, contagem, tempoMs);

        return new Resultado("SerialCPU", nomeAmostra, palavraAlvo, contagem, tempoMs, 1);
    }
}

// Peter
