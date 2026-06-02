/**
 * word_count.cl
 * Kernel OpenCL para contagem de palavras na GPU.
 *
 * Arquitetura compatível com o LeitorTexto.java do projeto:
 *   - O Java envia um array linear de chars onde as palavras são separadas por '\0'
 *   - Cada work-item recebe o índice de UMA palavra (offset no array de offsets)
 *   - Compara a palavra da sua posição com a palavra-alvo (case-sensitive,
 *     pois o LeitorTexto já faz toLowerCase antes de enviar)
 *
 * Parâmetros:
 *   wordData    - array de chars com todas as palavras concatenadas, separadas por '\0'
 *   offsets     - array de ints: offsets[i] = posição inicial da palavra i em wordData
 *   lengths     - array de ints: lengths[i] = comprimento da palavra i
 *   target      - a palavra-alvo como array de chars
 *   targetLen   - comprimento da palavra-alvo
 *   counts      - array de saída: counts[i] = 1 se palavra i == alvo, 0 caso contrário
 *   numWords    - número total de palavras
 */
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

    if (i >= numWords) {
        return;
    }

    int len = lengths[i];

    // Comprimentos diferentes => não pode ser igual
    if (len != targetLen) {
        counts[i] = 0;
        return;
    }

    int offset = offsets[i];

    // Comparação char a char
    for (int k = 0; k < len; k++) {
        if (wordData[offset + k] != target[k]) {
            counts[i] = 0;
            return;
        }
    }

    counts[i] = 1;
}
