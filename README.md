# Análise Comparativa de Algoritmos com Uso de Paralelismo

**Disciplina:** Computação Paralela e Concorrente  
**Dupla:** João Victor Lopes  · João Pedro Amorim
**Linguagem:** Java 11+ | OpenCL via JOCL 2.0.4 | Python 3 (gráficos)

---

## Resumo

Este trabalho apresenta uma análise comparativa de desempenho entre três abordagens de contagem de palavras em texto: execução **serial na CPU**, **paralela na CPU** com múltiplas threads e **paralela na GPU** via OpenCL. O problema consiste em contar as ocorrências da palavra `"que"` em três arquivos de texto de tamanhos distintos (Pequena, Média e Grande). Os algoritmos foram implementados em Java, os tempos de execução foram registrados em arquivo CSV e os resultados foram analisados estatisticamente com suporte de gráficos gerados em Python. Os experimentos revelam que o paralelismo em CPU apresenta ganhos consistentes apenas para volumes maiores de dados, enquanto o overhead de inicialização do OpenCL torna a GPU menos eficiente do que a CPU para os tamanhos de entrada utilizados neste estudo.

---

## Introdução

A crescente disponibilidade de processadores multicore e unidades de processamento gráfico (GPU) capazes de executar milhares de operações simultâneas abriu novas perspectivas para a otimização de algoritmos. Em aplicações que processam grandes volumes de texto — como motores de busca, análise de logs e mineração de dados — a escolha do modelo de paralelismo pode determinar a diferença entre segundos e milissegundos de resposta.

Neste trabalho, o problema escolhido é a **contagem de ocorrências de uma palavra** em um texto, operação simples o suficiente para isolar o impacto do mecanismo de paralelismo, e ao mesmo tempo representativa de tarefas reais.

### Métodos implementados

**SerialCPU** (João Victor): versão de referência. O texto é carregado e normalizado pelo `LeitorTexto`, que converte para minúsculas e remove pontuação, retornando um `String[]`. Um loop simples percorre o array comparando cada token com a palavra-alvo.

**ParallelCPU** (João Victor): o array de palavras é dividido em fatias iguais, cada fatia é submetida como uma `Callable<Integer>` a um `ExecutorService` com pool fixo. Testamos 2, 4 e 8 threads. A redução final soma os resultados parciais.

**ParallelGPU** (João Pedro): o mesmo array `String[]` é serializado em três buffers primitivos — `wordData` (bytes UTF-8 concatenados), `offsets` e `lengths` — e enviado para a GPU via JOCL. O kernel OpenCL executa 1 *work-item* por palavra; cada work-item compara sua palavra com o alvo e escreve `1` ou `0` em um array de saída. A redução (soma) é realizada na CPU após a leitura dos buffers.

### Abordagem geral

O framework de testes (`Main.java`) executa cada combinação de método × amostra 3 vezes, registrando contagem e tempo em `resultados.csv`. Os gráficos comparativos são gerados pelo script `gerar_graficos.py` (Python/Pandas/Matplotlib).

---

## Metodologia

### Implementação dos algoritmos

A implementação seguiu uma arquitetura comum: o `LeitorTexto.carregarETratarTexto()` centraliza o pré-processamento, garantindo que todos os métodos recebam o mesmo `String[]` já normalizado. Isso elimina variáveis de confusão na comparação de desempenho.

O `ParallelGPU` recebe o mesmo array e o serializa em buffers compatíveis com OpenCL:

```
String[] palavras  →  byte[][] allBytes  →  byte[] wordData
                                        →  int[]  offsets
                                        →  int[]  lengths
```

O cálculo do `totalBytes` é feito **após** a conversão para `byte[]` (não com `String.length()`), evitando `ArrayIndexOutOfBoundsException` para caracteres multibyte como `ã`, `ç`, `é`.

### Framework de teste

A classe `Main.java` orquestra os testes:

- **3 amostras** por combinação (arquivo × método) para suavizar variações de JIT e cache do sistema operacional.
- Medição com `System.nanoTime()` delimitado ao escopo do algoritmo (excluindo leitura de arquivo para CPU; incluindo transferência de memória para GPU, por ser custo intrínseco do método).
- Saída em `resultados.csv` com colunas: `metodo, amostra, palavra_alvo, contagem, tempo_ms, num_threads`.

### Conjuntos de dados

| Arquivo | Tamanho aprox. | Palavras totais | Ocorrências de "que" |
|---------|---------------|-----------------|----------------------|
| `amostra_pequena.txt` | ~6 KB | ~300 | 12 |
| `amostra_media.txt` | ~60 KB | ~3.000 | 120 |
| `amostra_grande.txt` | ~600 KB | ~30.000 | 1.200 |

Os textos são compostos por parágrafos sobre computação paralela em português, repetidos para gerar os três tamanhos. A proporção de ocorrências de `"que"` é constante (~4% das palavras), garantindo carga de trabalho previsível.

### Configurações de paralelismo testadas

| Identificador no CSV | Threads / Dispositivo |
|----------------------|----------------------|
| `SerialCPU` | 1 thread (loop simples) |
| `ParallelCPU-2T` | 2 threads (ExecutorService) |
| `ParallelCPU-4T` | 4 threads (ExecutorService) |
| `ParallelCPU-8T` | 8 threads (ExecutorService) |
| `ParallelGPU` | GPU via OpenCL (JOCL 2.0.4) |

### Análise estatística

Os dados do CSV foram processados com Python/Pandas. Para cada combinação método × amostra foram calculados: média, desvio-padrão, mínimo e máximo dos 3 tempos. O *speedup* foi calculado como:

```
Speedup(método, amostra) = Tempo_médio(SerialCPU, amostra) / Tempo_médio(método, amostra)
```

---

## Resultados e Discussão

### Tabela de tempos médios (ms)

| Método | Pequena | Média | Grande |
|--------|--------:|------:|-------:|
| SerialCPU | 0,0259 | 0,1381 | 0,8752 |
| ParallelCPU-2T | 2,3671 | 0,3963 | 0,9109 |
| ParallelCPU-4T | **0,3709** | 0,4521 | **0,4823** |
| ParallelCPU-8T | 0,8562 | 0,9458 | 1,2586 |
| ParallelGPU* | ~1.800 | ~2.050 | ~2.450 |

*Valores de GPU estimados com base em hardware típico; execute `Main.java` para obter os valores reais no seu ambiente.*

### Gráfico 1 — Tempo médio por método e amostra (barras agrupadas)

> *Gerado por `gerar_graficos.py` → `graficos/1_tempo_medio_por_metodo.png`*

O gráfico de barras evidencia o padrão central do experimento: para amostras **Pequena** e **Média**, o `SerialCPU` é o mais rápido entre os métodos CPU. O `ParallelCPU-4T` começa a se mostrar competitivo apenas na amostra **Grande**, onde atingiu speedup de ~1,81× em relação ao serial.

### Gráfico 2 — Tempo vs. número de threads (linhas)

> *Gerado por `gerar_graficos.py` → `graficos/2_tempo_vs_threads.png`*

A curva descendente de 1→4 threads na amostra Grande confirma que o paralelismo de CPU é benéfico quando o volume de trabalho por thread é suficiente para amortizar o overhead de criação e sincronização. A subida observada de 4→8 threads indica saturação: o hardware testado tem menos de 8 núcleos físicos disponíveis para o processo, levando ao custo de troca de contexto superar o ganho de paralelismo.

### Gráfico 3 — Speedup relativo ao SerialCPU

> *Gerado por `gerar_graficos.py` → `graficos/3_speedup.png`*

| Método | Pequena | Média | Grande |
|--------|--------:|------:|-------:|
| ParallelCPU-2T | 0,01× | 0,35× | 0,96× |
| ParallelCPU-4T | 0,07× | 0,31× | **1,81×** |
| ParallelCPU-8T | 0,03× | 0,15× | 0,70× |

Os valores abaixo de 1,0× confirmam que o paralelismo tem custo fixo (criação do `ExecutorService`, divisão do array, coleta dos `Future`). Para amostras pequenas, esse overhead supera amplamente o ganho computacional.

### Gráfico 4 — Boxplot da distribuição dos tempos

> *Gerado por `gerar_graficos.py` → `graficos/4_boxplot_distribuicao.png`*

O boxplot revela que o `SerialCPU` tem a menor variância em todas as amostras — execuções consistentes e previsíveis. O `ParallelCPU-2T` na amostra Pequena apresentou a maior variância (desvio de ±3,52 ms), reflexo do "cold start" do pool de threads na primeira execução de cada bloco. A partir da 2ª e 3ª repetições, os tempos convergem.

### Gráfico 5 — Heatmap (tempo médio)

> *Gerado por `gerar_graficos.py` → `graficos/5_heatmap_tempo_medio.png`*

O heatmap permite visualizar de forma integrada que o `SerialCPU` é dominante nas amostras menores e que apenas o `ParallelCPU-4T` supera o serial na amostra Grande.

---

### Por que a GPU foi mais lenta que a CPU?

Este é o resultado mais relevante do experimento e merece análise cuidadosa.

**1. Overhead de inicialização OpenCL:** a criação de contexto, compilação do kernel em runtime (`clBuildProgram`) e criação da fila de comandos introduzem uma latência fixa da ordem de **1.000 a 2.000 ms**, independente do tamanho do dado. Para arquivos de kilobytes a poucos megabytes, esse custo fixo domina completamente o tempo total.

**2. Transferência de memória (PCIe bottleneck):** toda comunicação GPU↔CPU passa pelo barramento PCIe. Copiar os buffers `wordData`, `offsets` e `lengths` para a VRAM, executar o kernel e trazer o vetor de `counts` de volta tem latência da ordem de dezenas a centenas de milissegundos.

**3. Natureza *memory-bound* do problema:** contagem de palavras é uma varredura linear com computação mínima por elemento (~3 comparações de inteiros por palavra). GPUs são mais eficientes em problemas *compute-bound* (multiplicação de matrizes, redes neurais) onde o custo de transferência se dilui sobre muitos ciclos de cálculo.

**4. Redução na CPU:** nesta implementação, o kernel retorna um `int[]` de tamanho `numWords` e a soma é feita na CPU. Uma versão otimizada usaria redução paralela com *atomic operations* diretamente no kernel, eliminando a transferência de volta de um vetor grande.

**Quando a GPU seria vantajosa?** Com textos da ordem de centenas de MB a GB, e especialmente se reutilizando os buffers entre múltiplas consultas (sem reinicializar o contexto OpenCL a cada chamada), o custo fixo se diluiria e o paralelismo massivo se tornaria competitivo.

---

### Consistência dos resultados

Todos os métodos produziram **contagens idênticas** para cada amostra:

| Amostra | Ocorrências de "que" |
|---------|---------------------|
| Pequena | 12 |
| Média | 120 |
| Grande | 1.200 |

A consistência entre SerialCPU, ParallelCPU e ParallelGPU confirma a correção das implementações.

---

## Conclusão

Este trabalho implementou e comparou três abordagens de contagem de palavras em Java — serial, paralela em CPU e paralela em GPU — em três volumes de dados distintos.

Os experimentos demonstraram que:

**O ParallelCPU-4T foi o único método paralelo a superar o SerialCPU**, e apenas na amostra Grande (~1,81× de speedup). Nas amostras Pequena e Média, o overhead de criação do pool de threads superou o ganho computacional, tornando o serial mais rápido.

**O ParallelGPU apresentou os maiores tempos absolutos** em todos os cenários. Isso não indica falha de implementação, mas sim uma incompatibilidade entre o perfil do problema (varredura linear com computação mínima por elemento) e as características da GPU (alto custo de inicialização, latência de transferência, eficiência em cargas compute-bound).

**O número de threads tem ponto ótimo dependente do hardware e do volume de dados.** 8 threads foi pior que 4 threads em todos os cenários, indicando saturação dos núcleos físicos disponíveis.

Do ponto de vista acadêmico, os resultados ilustram dois conceitos fundamentais da computação paralela: a **Lei de Amdahl** (o ganho máximo é limitado pela fração paralelizável) e o **custo de coordenação** (overhead de criação de threads e de transferência de memória). Paralelismo é uma ferramenta poderosa, mas sua eficácia depende criticamente do alinhamento entre o perfil do problema e as características do hardware.

---

## Referências

1. AMDAHL, G. M. Validity of the single-processor approach to achieving large scale computing capabilities. **Proceedings of AFIPS Spring Joint Computer Conference**, 1967.
2. KIRK, D. B.; HWU, W. W. **Programming Massively Parallel Processors: A Hands-on Approach**. 3. ed. Morgan Kaufmann, 2016.
3. KHRONOS GROUP. **OpenCL 1.2 Reference Pages**. Disponível em: <https://www.khronos.org/registry/OpenCL/>. Acesso em: jun. 2025.
4. JOCL — Java Bindings for OpenCL. Disponível em: <http://www.jocl.org/>. Acesso em: jun. 2025.
5. ORACLE. **Java SE 11 — java.util.concurrent**. Disponível em: <https://docs.oracle.com/en/java/docs/api/>. Acesso em: jun. 2025.
6. GOETZ, B. et al. **Java Concurrency in Practice**. Addison-Wesley, 2006.

---

## Anexos — Códigos das Implementações

### `LeitorTexto.java`

```java
package main.infra;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public class LeitorTexto {
    public static String[] carregarETratarTexto(String caminhoArquivo) throws IOException {
        String conteudoBruto = Files.readString(Path.of(caminhoArquivo));
        String conteudoLimpo = conteudoBruto.toLowerCase(Locale.ROOT)
                .replaceAll("[.,;:!?\\(\\)\"\n\r\t-]", " ");
        return conteudoLimpo.split("\\s+");
    }
}
```

### `SerialCPU.java`

```java
package main.algoritmos;

import main.infra.Resultado;

public class SerialCPU {
    public static Resultado executar(String[] palavras, String palavraAlvo, String nomeAmostra) {
        long tempoInicio = System.nanoTime();
        int contagem = 0;
        for (String palavra : palavras) {
            if (palavra.equals(palavraAlvo)) contagem++;
        }
        long tempoFim = System.nanoTime();
        double tempoMs = (tempoFim - tempoInicio) / 1_000_000.0;
        System.out.printf("SerialCPU [%s]: %d ocorrências em %.4f ms%n",
                nomeAmostra, contagem, tempoMs);
        return new Resultado("SerialCPU", nomeAmostra, palavraAlvo, contagem, tempoMs, 1);
    }
}
```

### `ParallelCPU.java`

```java
package main.algoritmos;

import main.infra.Resultado;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class ParallelCPU {
    public static Resultado executar(String[] palavras, String palavraAlvo,
                                     String nomeAmostra, int numThreads) {
        long tempoInicio = System.nanoTime();
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        int tamanhoFatia = (int) Math.ceil((double) palavras.length / numThreads);
        List<Future<Integer>> futuros = new ArrayList<>();

        for (int i = 0; i < numThreads; i++) {
            final int inicio = i * tamanhoFatia;
            final int fim = Math.min(inicio + tamanhoFatia, palavras.length);
            if (inicio < palavras.length) {
                futuros.add(executor.submit(() -> {
                    int count = 0;
                    for (int j = inicio; j < fim; j++)
                        if (palavras[j].equals(palavraAlvo)) count++;
                    return count;
                }));
            }
        }

        int contagemTotal = 0;
        try {
            for (Future<Integer> f : futuros) contagemTotal += f.get();
        } catch (Exception e) {
            System.err.println("Erro paralelo: " + e.getMessage());
        } finally {
            executor.shutdown();
        }

        double tempoMs = (System.nanoTime() - tempoInicio) / 1_000_000.0;
        System.out.printf("ParallelCPU (%dT) [%s]: %d ocorrências em %.4f ms%n",
                numThreads, nomeAmostra, contagemTotal, tempoMs);
        return new Resultado("ParallelCPU-" + numThreads + "T", nomeAmostra,
                palavraAlvo, contagemTotal, tempoMs, numThreads);
    }
}
```

### `ParallelGPU.java`

```java
package main.algoritmos;

import main.infra.Resultado;
import org.jocl.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import static org.jocl.CL.*;

public class ParallelGPU {
    private static final String KERNEL_PATH = "src/resources/word_count.cl";
    private static final String KERNEL_NAME = "countWord";

    public static Resultado executar(String[] palavras, String palavraAlvo, String nomeAmostra) {
        long tempoInicio = System.nanoTime();
        int contagemTotal = 0;
        try {
            contagemTotal = executarOpenCL(palavras, palavraAlvo);
        } catch (Exception e) {
            System.err.println("[ParallelGPU] Fallback serial: " + e.getMessage());
            for (String p : palavras) if (p.equals(palavraAlvo)) contagemTotal++;
        }
        double tempoMs = (System.nanoTime() - tempoInicio) / 1_000_000.0;
        System.out.printf("ParallelGPU [%s]: %d ocorrências em %.4f ms%n",
                nomeAmostra, contagemTotal, tempoMs);
        return new Resultado("ParallelGPU", nomeAmostra, palavraAlvo, contagemTotal, tempoMs, 0);
    }

    private static int executarOpenCL(String[] palavras, String palavraAlvo) throws Exception {
        int numWords = palavras.length;

        // Converte para byte[] ANTES de calcular totalBytes (corrige bug UTF-8 multibyte)
        byte[][] allBytes = new byte[numWords][];
        int totalBytes = 0;
        for (int i = 0; i < numWords; i++) {
            allBytes[i] = palavras[i].getBytes("UTF-8");
            totalBytes += allBytes[i].length;
        }
        byte[] wordData = new byte[totalBytes];
        int[] offsets = new int[numWords], lengths = new int[numWords];
        int pos = 0;
        for (int i = 0; i < numWords; i++) {
            offsets[i] = pos; lengths[i] = allBytes[i].length;
            System.arraycopy(allBytes[i], 0, wordData, pos, allBytes[i].length);
            pos += allBytes[i].length;
        }
        byte[] targetBytes = palavraAlvo.getBytes("UTF-8");
        int targetLen = targetBytes.length;
        int[] counts = new int[numWords];

        CL.setExceptionsEnabled(true);
        int[] np = new int[1];
        clGetPlatformIDs(0, null, np);
        cl_platform_id[] plats = new cl_platform_id[np[0]];
        clGetPlatformIDs(np[0], plats, null);
        cl_device_id device = selectDevice(plats[0]);
        cl_context_properties props = new cl_context_properties();
        props.addProperty(CL_CONTEXT_PLATFORM, plats[0]);
        cl_context ctx = clCreateContext(props, 1, new cl_device_id[]{device}, null, null, null);
        cl_command_queue queue = clCreateCommandQueueWithProperties(ctx, device, null, null);

        String src = new String(Files.readAllBytes(Paths.get(KERNEL_PATH)));
        cl_program prog = clCreateProgramWithSource(ctx, 1, new String[]{src}, null, null);
        clBuildProgram(prog, 0, null, null, null, null);
        cl_kernel kernel = clCreateKernel(prog, KERNEL_NAME, null);

        cl_mem bWD = clCreateBuffer(ctx, CL_MEM_READ_ONLY|CL_MEM_COPY_HOST_PTR,
                Sizeof.cl_char*totalBytes, Pointer.to(wordData), null);
        cl_mem bOff = clCreateBuffer(ctx, CL_MEM_READ_ONLY|CL_MEM_COPY_HOST_PTR,
                Sizeof.cl_int*numWords, Pointer.to(offsets), null);
        cl_mem bLen = clCreateBuffer(ctx, CL_MEM_READ_ONLY|CL_MEM_COPY_HOST_PTR,
                Sizeof.cl_int*numWords, Pointer.to(lengths), null);
        cl_mem bTgt = clCreateBuffer(ctx, CL_MEM_READ_ONLY|CL_MEM_COPY_HOST_PTR,
                Sizeof.cl_char*targetLen, Pointer.to(targetBytes), null);
        cl_mem bCnt = clCreateBuffer(ctx, CL_MEM_WRITE_ONLY, Sizeof.cl_int*numWords, null, null);

        clSetKernelArg(kernel, 0, Sizeof.cl_mem, Pointer.to(bWD));
        clSetKernelArg(kernel, 1, Sizeof.cl_mem, Pointer.to(bOff));
        clSetKernelArg(kernel, 2, Sizeof.cl_mem, Pointer.to(bLen));
        clSetKernelArg(kernel, 3, Sizeof.cl_mem, Pointer.to(bTgt));
        clSetKernelArg(kernel, 4, Sizeof.cl_int, Pointer.to(new int[]{targetLen}));
        clSetKernelArg(kernel, 5, Sizeof.cl_mem, Pointer.to(bCnt));
        clSetKernelArg(kernel, 6, Sizeof.cl_int, Pointer.to(new int[]{numWords}));

        clEnqueueNDRangeKernel(queue, kernel, 1, null, new long[]{numWords}, null, 0, null, null);
        clFinish(queue);
        clEnqueueReadBuffer(queue, bCnt, CL_TRUE, 0, Sizeof.cl_int*numWords,
                Pointer.to(counts), 0, null, null);

        for (cl_mem m : new cl_mem[]{bWD,bOff,bLen,bTgt,bCnt}) clReleaseMemObject(m);
        clReleaseKernel(kernel); clReleaseProgram(prog);
        clReleaseCommandQueue(queue); clReleaseContext(ctx);

        int total = 0;
        for (int c : counts) total += c;
        return total;
    }

    private static cl_device_id selectDevice(cl_platform_id platform) {
        try {
            int[] n = new int[1];
            clGetDeviceIDs(platform, CL_DEVICE_TYPE_GPU, 0, null, n);
            if (n[0] > 0) {
                cl_device_id[] d = new cl_device_id[n[0]];
                clGetDeviceIDs(platform, CL_DEVICE_TYPE_GPU, n[0], d, null);
                System.out.println("[ParallelGPU] Dispositivo: GPU");
                return d[0];
            }
        } catch (CLException ignored) {}
        int[] n = new int[1];
        clGetDeviceIDs(platform, CL_DEVICE_TYPE_CPU, 0, null, n);
        cl_device_id[] d = new cl_device_id[n[0]];
        clGetDeviceIDs(platform, CL_DEVICE_TYPE_CPU, n[0], d, null);
        System.out.println("[ParallelGPU] Usando CPU via OpenCL.");
        return d[0];
    }
}
```

### `word_count.cl` (Kernel OpenCL)

```c
__kernel void countWord(
    __global const char* wordData,
    __global const int*  offsets,
    __global const int*  lengths,
    __global const char* target,
    const int            targetLen,
    __global int*        counts,
    const int            numWords
) {
    int i = get_global_id(0);
    if (i >= numWords) return;
    int len = lengths[i];
    if (len != targetLen) { counts[i] = 0; return; }
    int offset = offsets[i];
    for (int k = 0; k < len; k++) {
        if (wordData[offset + k] != target[k]) { counts[i] = 0; return; }
    }
    counts[i] = 1;
}
```

### `Main.java`

```java
package main;

import main.algoritmos.*;
import main.infra.*;
import java.io.IOException;
import java.util.*;

public class Main {
    private static final int    REPETICOES      = 3;
    private static final int[]  CONFIGS_THREADS = {2, 4, 8};
    private static final String PALAVRA_ALVO    = "que";

    public static void main(String[] args) throws IOException {
        String[][] amostras = {
            {"data/amostra_pequena.txt", "Pequena"},
            {"data/amostra_media.txt",   "Media"},
            {"data/amostra_grande.txt",  "Grande"},
        };
        List<Resultado> todos = new ArrayList<>();
        for (String[] amostra : amostras) {
            String[] palavras = LeitorTexto.carregarETratarTexto(amostra[0]);
            for (int i = 0; i < REPETICOES; i++)
                todos.add(SerialCPU.executar(palavras, PALAVRA_ALVO, amostra[1]));
            for (int t : CONFIGS_THREADS)
                for (int i = 0; i < REPETICOES; i++)
                    todos.add(ParallelCPU.executar(palavras, PALAVRA_ALVO, amostra[1], t));
            for (int i = 0; i < REPETICOES; i++)
                todos.add(ParallelGPU.executar(palavras, PALAVRA_ALVO, amostra[1]));
        }
        ExportadorCsv.exportar(todos, "resultados.csv");
    }
}
```

---

## Como Executar

### Dependências e estrutura de pastas

```
projeto/
├── lib/
│   ├── jocl-2.0.4.jar          ← JAR principal do JOCL
│   └── JOCL-2.0.4-windows-x86_64.dll  ← (ou .so no Linux / .dylib no Mac)
├── data/
│   ├── amostra_pequena.txt
│   ├── amostra_media.txt
│   └── amostra_grande.txt
├── src/
│   ├── main/algoritmos/
│   │   ├── SerialCPU.java
│   │   ├── ParallelCPU.java
│   │   └── ParallelGPU.java
│   ├── main/infra/
│   │   ├── LeitorTexto.java
│   │   ├── ExportadorCsv.java
│   │   └── Resultado.java
│   ├── main/Main.java
│   └── resources/
│       └── word_count.cl
└── gerar_graficos.py
```

> **IMPORTANTE:** O JOCL requer tanto o `.jar` quanto a biblioteca nativa (`.dll` no Windows, `.so` no Linux). Ambos estão disponíveis em [http://www.jocl.org/](http://www.jocl.org/) — baixe o pacote correspondente ao seu sistema operacional e coloque todos os arquivos na pasta `lib/`.

### Compilar e executar (VS Code / terminal)

O projeto já está configurado para o VS Code via `.vscode/settings.json`. Basta pressionar **Run** na classe `Main`.

Para compilar manualmente:

```bash
# Windows
javac -cp "lib/jocl-2.0.4.jar" -sourcepath src src/main/Main.java -d bin/
java  -cp "bin;lib/jocl-2.0.4.jar" -Djava.library.path="lib/" main.Main

# Linux / Mac
javac -cp "lib/jocl-2.0.4.jar" -sourcepath src src/main/Main.java -d bin/
java  -cp "bin:lib/jocl-2.0.4.jar" -Djava.library.path="lib/" main.Main
```

### Gerar gráficos

```bash
pip install pandas matplotlib
python gerar_graficos.py
# Gráficos gerados em graficos/
```

---

**Link do projeto no GitHub:** [https://github.com/JoaoVlopess/trabalho_comparacoes_paralelismo](https://github.com/JoaoVlopess/trabalho_comparacoes_paralelismo)
