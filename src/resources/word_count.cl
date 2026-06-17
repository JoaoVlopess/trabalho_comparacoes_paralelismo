/**
 * word_count.cl — versão otimizada (redução na GPU com atomic_inc).
 *
 * Cada work-item processa UMA palavra. Se ela for igual ao alvo, faz um
 * incremento ATÔMICO num único contador global compartilhado (globalCount).
 * A soma já sai pronta da GPU — não precisa devolver vetor para a CPU somar.
 */
__kernel void countWord(
    __global const char* wordData,
    __global const int*  offsets,
    __global const int*  lengths,
    __global const char* target,
    const int            targetLen,
    __global int*        globalCount,
    const int            numWords
) {
    int i = get_global_id(0);

    if (i >= numWords) {
        return;
    }

    // Comprimento diferente => não pode ser igual, sai sem incrementar
    if (lengths[i] != targetLen) {
        return;
    }

    int offset = offsets[i];

    // Comparação byte a byte com a palavra-alvo
    for (int k = 0; k < targetLen; k++) {
        if (wordData[offset + k] != target[k]) {
            return;   // achou diferença: não é a palavra-alvo
        }
    }

    // É igual ao alvo => incremento atômico no contador compartilhado
    atomic_inc(globalCount);
}