# Análise Comparativa de Algoritmos com Uso de Paralelismo

**Disciplina:** Computação Paralela e Concorrente  
**Dupla:** João Victor Lopes · João Pedro Amorim  
**Linguagem:** Java 11+ | OpenCL via JOCL 2.0.4 | Python 3 (gráficos)

---

## Resumo

Este trabalho apresenta uma análise comparativa de desempenho entre três abordagens de contagem de palavras em texto: execução **serial na CPU**, **paralela na CPU** com múltiplas threads e **paralela na GPU** via OpenCL. O problema consiste em contar as ocorrências da palavra `"que"` em três arquivos de texto de tamanhos distintos (Pequena, Média e Grande). Os algoritmos foram implementados em Java, os tempos de execução foram registrados em arquivo CSV e os resultados foram analisados estatisticamente com suporte de gráficos gerados em Python. Os experimentos revelam que o paralelismo em CPU apresenta ganhos consistentes apenas para volumes maiores de dados, enquanto o overhead de inicialização do OpenCL torna a GPU menos eficiente do que a CPU para os tamanhos de entrada utilizados neste estudo — mesmo após otimizarmos a redução para ser feita na própria GPU com operações atômicas.

---

## Introdução

A crescente disponibilidade de processadores multicore e unidades de processamento gráfico (GPU) capazes de executar milhares de operações simultâneas abriu novas perspectivas para a otimização de algoritmos. Em aplicações que processam grandes volumes de texto — como motores de busca, análise de logs e mineração de dados — a escolha do modelo de paralelismo pode determinar a diferença entre segundos e milissegundos de resposta.

Neste trabalho, o problema escolhido é a **contagem de ocorrências de uma palavra** em um texto, operação simples o suficiente para isolar o impacto do mecanismo de paralelismo, e ao mesmo tempo representativa de tarefas reais.

### Métodos implementados

**SerialCPU** (João Victor): versão de referência. O texto é carregado e normalizado pelo `LeitorTexto`, que converte para minúsculas e remove pontuação, retornando um `String[]`. Um loop simples percorre o array comparando cada token com a palavra-alvo.

**ParallelCPU** (João Victor): o array de palavras é dividido em fatias iguais, cada fatia é submetida como uma `Callable<Integer>` a um `ExecutorService` com pool fixo. Testamos 2, 4 e 8 threads. A redução final soma os resultados parciais.

**ParallelGPU** (João Pedro): o mesmo array `String[]` é serializado em três buffers primitivos — `wordData` (bytes UTF-8 concatenados), `offsets` e `lengths` — e enviado para a GPU via JOCL. O kernel OpenCL executa 1 *work-item* por palavra; cada work-item compara sua palavra com o alvo e, em caso de igualdade, incrementa um **único contador global** via operação atômica (`atomic_inc`). A **redução é feita na própria GPU**, e apenas um inteiro é transferido de volta para a CPU.

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

A saída do kernel é um **único inteiro** (o contador global), e não um vetor do tamanho do texto — ver a seção "Otimização aplicada".

### Framework de teste

A classe `Main.java` orquestra os testes:

- **3 repetições** por combinação (arquivo × método) para suavizar variações de JIT e cache do sistema operacional.
- Medição com `System.nanoTime()` delimitado ao escopo do algoritmo (excluindo leitura de arquivo para CPU; incluindo transferência de memória para GPU, por ser custo intrínseco do método).
- Saída em `resultados.csv` com colunas: `metodo, amostra, palavra_alvo, contagem, tempo_ms, num_threads`.

### Conjuntos de dados

| Arquivo | Tamanho aprox. | Palavras totais | Ocorrências de "que" |
|---------|---------------|-----------------|----------------------|
| `amostra_pequena.txt` | ~6 KB | ~400 | 12 |
| `amostra_media.txt` | ~60 KB | ~4.000 | 120 |
| `amostra_grande.txt` | ~600 KB | ~40.000 | 1.200 |

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

## Otimização aplicada: redução na GPU com operações atômicas

A primeira versão do kernel escrevia `1` ou `0` em um vetor de saída do tamanho do texto (`counts[numWords]`), e a soma final era feita na CPU. Isso tinha dois custos: um buffer de saída grande (um inteiro por palavra) transferido de volta pela memória, e um laço de soma na CPU.

A versão final faz a **redução dentro da GPU**: existe um **único contador global** e cada work-item que encontra a palavra-alvo executa um incremento **atômico** (`atomic_inc`) nesse contador.

- **Uso do buffer:** o buffer de saída passou de `numWords` inteiros (ex.: ~40.000 × 4 bytes na amostra Grande) para **apenas 1 inteiro (4 bytes)**, eliminando também o laço de soma na CPU.
- **Atomicidade:** como milhares de work-items podem incrementar o mesmo contador simultaneamente, o `atomic_inc` garante que a operação leitura-incremento-escrita seja indivisível, evitando condição de corrida (incrementos perdidos). A prova de que está correto é que as contagens da GPU batem exatamente com as da CPU (12 / 120 / 1.200).

> Observação de portabilidade: em macOS, que implementa apenas **OpenCL 1.2**, usa-se `clCreateCommandQueue` (a função `clCreateCommandQueueWithProperties` é do OpenCL 2.0 e não é suportada).

---

## Resultados e Discussão

> Os tempos absolutos dependem da máquina; o que importa são os **padrões**. Tabelas geradas a partir de `resultados.csv`; gráficos por `gerar_graficos.py`. Reexecute para atualizar os valores no seu ambiente.

### Tabela de tempos médios (ms)

| Método | Pequena | Média | Grande |
|--------|--------:|------:|-------:|
| SerialCPU | **0,012** | **0,058** | 0,383 |
| ParallelCPU-2T | 1,239 | 0,360 | 0,340 |
| ParallelCPU-4T | 0,145 | 0,228 | **0,259** |
| ParallelCPU-8T | 0,221 | 0,292 | 0,366 |
| ParallelGPU | 120,567 | 4,059 | 5,970 |

### Gráfico 1 — Tempo médio por método e amostra (barras agrupadas)

![Tempo médio por método e amostra](graficos/1_tempo_medio_por_metodo.png)

A GPU é o método mais lento em todas as amostras. O valor alto na amostra Pequena (~120 ms de média) **não** é causado pelo tamanho do dado, e sim pelo *cold start*: a primeira chamada OpenCL do processo paga um custo único de inicialização do driver da GPU, aquecimento do JIT e compilação do kernel (na nossa execução, ~350 ms na primeira repetição; as demais ficaram em ~4 ms). Como há apenas 3 repetições, esse outlier infla a média.

### Gráfico 2 — Tempo vs. número de threads (linhas)

![Tempo vs número de threads](graficos/2_tempo_vs_threads.png)

Na amostra Grande, o tempo cai de 1→4 threads e volta a subir em 8, indicando saturação dos núcleos físicos: o ponto ótimo de threads acompanha o número de núcleos disponíveis na máquina. Acima disso, o custo de troca de contexto supera o ganho de paralelismo.

### Gráfico 3 — Speedup relativo ao SerialCPU

![Speedup relativo ao SerialCPU](graficos/3_speedup.png)

| Método | Pequena | Média | Grande |
|--------|--------:|------:|-------:|
| ParallelCPU-2T | 0,01× | 0,16× | 1,13× |
| ParallelCPU-4T | 0,08× | 0,26× | **1,48×** |
| ParallelCPU-8T | 0,06× | 0,20× | 1,05× |
| ParallelGPU | 0,00× | 0,01× | 0,06× |

O paralelismo de CPU só supera o serial (speedup > 1×) na amostra Grande, com o `ParallelCPU-4T` atingindo ~1,48×. Nas amostras menores, o overhead de criar o `ExecutorService` e dividir o array supera o ganho computacional.

### Gráfico 4 — Boxplot da distribuição dos tempos

![Distribuição dos tempos (boxplot)](graficos/4_boxplot_distribuicao.png)

O boxplot revela a altíssima variância da GPU na amostra Pequena, reflexo do outlier de *cold start* da primeira execução. Os métodos de CPU apresentam distribuições estreitas e consistentes.

### Gráfico 5 — Heatmap (tempo médio)

![Heatmap do tempo médio](graficos/5_heatmap_tempo_medio.png)

O heatmap integra a visão geral: o `SerialCPU` domina nas amostras menores e o `ParallelCPU-4T` é o melhor na amostra Grande, enquanto a GPU se destaca como a mais lenta.

---

### Por que a GPU foi mais lenta que a CPU?

Este é o resultado mais relevante do experimento e merece análise cuidadosa.

**1. Overhead de inicialização (cold start):** a primeira chamada OpenCL do processo paga um custo único alto — inicialização do driver da GPU, aquecimento do JIT/JNI e compilação do kernel em runtime (`clBuildProgram`). Na nossa execução isso ficou em torno de ~350 ms, contra ~4 ms das chamadas seguintes. Para arquivos pequenos esse custo fixo domina o tempo total.

**2. Transferência de memória:** copiar os buffers `wordData`, `offsets` e `lengths` para a memória da GPU e sincronizar a execução tem latência própria, que não se justifica para poucos KB/MB de dados.

**3. Natureza *memory-bound* do problema:** contagem de palavras é uma varredura linear com computação mínima por elemento (poucas comparações por palavra). GPUs brilham em problemas *compute-bound* (multiplicação de matrizes, redes neurais), onde o custo de transferência se dilui sobre muitos ciclos de cálculo.

**4. Redução já otimizada na GPU:** nesta versão, a soma é feita na própria GPU com `atomic_inc` num contador único, e apenas 1 inteiro é transferido de volta (ver "Otimização aplicada"). Isso reduz a transferência e elimina o laço de soma na CPU — mas, dado o tamanho dos dados, não é suficiente para a GPU superar a CPU.

**Quando a GPU seria vantajosa?** Com textos da ordem de centenas de MB a GB, e especialmente reutilizando contexto e buffers entre múltiplas consultas (sem reinicializar o OpenCL a cada chamada), o custo fixo se diluiria e o paralelismo massivo se tornaria competitivo.

---

### Consistência dos resultados

Todos os métodos produziram **contagens idênticas** para cada amostra:

| Amostra | Ocorrências de "que" |
|---------|---------------------|
| Pequena | 12 |
| Média | 120 |
| Grande | 1.200 |

A consistência entre SerialCPU, ParallelCPU e ParallelGPU confirma a correção das implementações — inclusive da redução atômica na GPU.

---

## Conclusão

Este trabalho implementou e comparou três abordagens de contagem de palavras em Java — serial, paralela em CPU e paralela em GPU — em três volumes de dados distintos.

Os experimentos demonstraram que:

**O ParallelCPU-4T foi o único método paralelo a superar o SerialCPU**, e apenas na amostra Grande (~1,48× de speedup). Nas amostras Pequena e Média, o overhead de criação do pool de threads superou o ganho computacional, tornando o serial mais rápido.

**O ParallelGPU apresentou os maiores tempos absolutos** em todos os cenários. Isso não indica falha de implementação — a redução foi inclusive otimizada para a GPU com operações atômicas — mas sim uma incompatibilidade entre o perfil do problema (varredura linear, memory-bound, dados pequenos) e as características da GPU (alto custo de inicialização e de transferência).

**O número de threads tem ponto ótimo dependente do hardware.** 8 threads foi pior que 4 threads, indicando saturação dos núcleos físicos disponíveis.

Do ponto de vista acadêmico, os resultados ilustram conceitos fundamentais da computação paralela: a **Lei de Amdahl** (o ganho máximo é limitado pela fração paralelizável), o **custo de coordenação** (overhead de criação de threads e de transferência de memória) e a importância da **atomicidade** na redução de dados compartilhados. Paralelismo é uma ferramenta poderosa, mas sua eficácia depende criticamente do alinhamento entre o perfil do problema e as características do hardware.

---

## Referências

1. AMDAHL, G. M. Validity of the single-processor approach to achieving large scale computing capabilities. **Proceedings of AFIPS Spring Joint Computer Conference**, 1967.
2. KIRK, D. B.; HWU, W. W. **Programming Massively Parallel Processors: A Hands-on Approach**. 3. ed. Morgan Kaufmann, 2016.
3. KHRONOS GROUP. **OpenCL 1.2 Reference Pages**. Disponível em: <https://www.khronos.org/registry/OpenCL/>. Acesso em: jun. 2025.
4. JOCL — Java Bindings for OpenCL. Disponível em: <http://www.jocl.org/>. Acesso em: jun. 2025.
5. ORACLE. **Java SE 11 — java.util.concurrent**. Disponível em: <https://docs.oracle.com/en/java/docs/api/>. Acesso em: jun. 2025.
6. GOETZ, B. et al. **Java Concurrency in Practice**. Addison-Wesley, 2006.

---

## Como Executar

### Dependências e estrutura de pastas

```
projeto/
├── lib/
│   └── jocl-2.0.4.jar          ← único JAR (bibliotecas nativas já embutidas)
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

> **JOCL:** o JOCL 2.0.4 está disponível como um único JAR no Maven Central, com as bibliotecas nativas já embutidas (extraídas automaticamente em tempo de execução). Baixe em
> <https://repo1.maven.org/maven2/org/jocl/jocl/2.0.4/jocl-2.0.4.jar> e coloque na pasta `lib/`.
> É necessário ter um runtime OpenCL instalado (normalmente já vem com o driver de vídeo Intel/NVIDIA/AMD; no macOS já é nativo). Em macOS, que suporta apenas OpenCL 1.2, o código usa `clCreateCommandQueue`.

### Compilar e executar (terminal)

```bash
# Windows PowerShell
javac -cp ".\lib\*" -sourcepath ".\src" -d ".\bin" ".\src\main\Main.java"
java '-Djava.library.path=.\lib' -cp '.\bin;.\lib\*' main.Main

# Linux / Mac
javac -cp "lib/*" -sourcepath src -d bin src/main/Main.java
java -Djava.library.path="lib" -cp "bin:lib/*" main.Main
```

### Gerar gráficos

```bash
# Windows
pip install pandas matplotlib
python gerar_graficos.py

# Linux / Mac
pip3 install pandas matplotlib
python3 gerar_graficos.py

# Gráficos gerados em graficos/
```

---

**Link do projeto no GitHub:** https://github.com/JoaoVlopess/trabalho_comparacoes_paralelismo