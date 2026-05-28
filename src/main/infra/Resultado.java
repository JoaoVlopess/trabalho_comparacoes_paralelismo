package main.infra;

public class Resultado {
    public final String metodo;
    public final String nomeAmostra;
    public final String palavraAlvo;
    public final int contagem;
    public final double tempoMs;
    public final int numThreads;

    public Resultado(String metodo, String nomeAmostra, String palavraAlvo,
                     int contagem, double tempoMs, int numThreads) {
        this.metodo = metodo;
        this.nomeAmostra = nomeAmostra;
        this.palavraAlvo = palavraAlvo;
        this.contagem = contagem;
        this.tempoMs = tempoMs;
        this.numThreads = numThreads;
    }
}
