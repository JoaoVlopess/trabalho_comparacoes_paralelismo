package main.infra;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public class LeitorTexto {

    /**
     * Lê o arquivo txt, converte para minúsculas e remove pontuações,
     * retornando um array de palavras pronto para o processamento.
     */
    
    public static String[] carregarETratarTexto(String caminhoArquivo) throws IOException {
        // Carrega todo o arquivo para a memória RAM de uma vez
        String conteudoBruto = Files.readString(Path.of(caminhoArquivo));
        
        // Normalização estatística: remove caracteres especiais que grudam nas palavras
        String conteudoLimpo = conteudoBruto.toLowerCase(Locale.ROOT)
                .replaceAll("[.,;:!?\\(\\)\"\n\r\t-]", " ");
        
        // Divide o texto por qualquer quantidade de espaços em branco
        return conteudoLimpo.split("\\s+");
    }
}
// lopes
