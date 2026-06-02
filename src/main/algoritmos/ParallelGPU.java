package main.algoritmos;

import main.infra.Resultado;
import org.jocl.*;

import java.nio.file.Files;
import java.nio.file.Paths;

import static org.jocl.CL.*;

/**
 * ParallelGPU — Contagem de palavras usando OpenCL (JOCL 2.0.4).
 *
 * Integrado à arquitetura do projeto:
 *   - Recebe String[] palavras (já tratadas pelo LeitorTexto)
 *   - Retorna Resultado com mesmo contrato de SerialCPU e ParallelCPU
 *
 * Autor: Peter (Integrante B)
 */
public class ParallelGPU {

    private static final String KERNEL_PATH = "src/resources/word_count.cl";
    private static final String KERNEL_NAME = "countWord";

    public static Resultado executar(String[] palavras, String palavraAlvo, String nomeAmostra) {

        long tempoInicio = System.nanoTime();
        int contagemTotal = 0;

        try {
            contagemTotal = executarOpenCL(palavras, palavraAlvo);
        } catch (Exception e) {
            System.err.println("[ParallelGPU] Erro OpenCL — usando fallback serial: " + e.getMessage());
            for (String p : palavras) {
                if (p.equals(palavraAlvo)) contagemTotal++;
            }
        }

        long tempoFim = System.nanoTime();
        double tempoMs = (tempoFim - tempoInicio) / 1_000_000.0;

        System.out.printf("ParallelGPU [%s]: %d ocorrências em %.4f ms%n",
                nomeAmostra, contagemTotal, tempoMs);

        return new Resultado("ParallelGPU", nomeAmostra, palavraAlvo,
                contagemTotal, tempoMs, 0);
    }

    private static int executarOpenCL(String[] palavras, String palavraAlvo) throws Exception {

        int numWords = palavras.length;

        // ── 1. Serializar String[] em arrays de bytes ─────────────────────────
        //
        // PASSO 1: converte cada palavra para byte[] UTF-8 e armazena no array
        // intermediário allBytes. Só DEPOIS calcula totalBytes somando os
        // comprimentos reais. Isso evita o ArrayIndexOutOfBoundsException:
        //
        //   BUG (versão anterior):
        //     int totalChars = 0;
        //     for (String p : palavras) totalChars += p.length();  // ERRADO
        //     // p.length() conta chars Java (UTF-16), mas getBytes("UTF-8")
        //     // retorna mais bytes para caracteres fora do ASCII (ã, ç, é...).
        //     // Resultado: wordData fica MENOR que o necessário → arraycopy estoura.
        //
        //   FIX: converter primeiro, medir depois.
        //
        byte[][] allBytes = new byte[numWords][];
        int totalBytes = 0;
        for (int i = 0; i < numWords; i++) {
            allBytes[i] = palavras[i].getBytes("UTF-8");
            totalBytes += allBytes[i].length;   // tamanho real após conversão
        }

        // PASSO 2: monta wordData, offsets e lengths usando os byte[] já prontos
        byte[] wordData = new byte[totalBytes];
        int[]  offsets  = new int[numWords];
        int[]  lengths  = new int[numWords];

        int pos = 0;
        for (int i = 0; i < numWords; i++) {
            offsets[i] = pos;
            lengths[i] = allBytes[i].length;
            System.arraycopy(allBytes[i], 0, wordData, pos, allBytes[i].length);
            pos += allBytes[i].length;
        }

        byte[] targetBytes = palavraAlvo.getBytes("UTF-8");
        int    targetLen   = targetBytes.length;
        int[]  counts      = new int[numWords];

        // ── 2. Inicialização OpenCL ───────────────────────────────────────────
        CL.setExceptionsEnabled(true);

        int[] numPlatforms = new int[1];
        clGetPlatformIDs(0, null, numPlatforms);
        if (numPlatforms[0] == 0) throw new RuntimeException("Nenhuma plataforma OpenCL encontrada.");

        cl_platform_id[] platforms = new cl_platform_id[numPlatforms[0]];
        clGetPlatformIDs(platforms.length, platforms, null);
        cl_platform_id platform = platforms[0];

        cl_device_id device = selectDevice(platform);

        cl_context_properties props = new cl_context_properties();
        props.addProperty(CL_CONTEXT_PLATFORM, platform);

        cl_context       context = clCreateContext(props, 1, new cl_device_id[]{device}, null, null, null);
        cl_command_queue queue   = clCreateCommandQueueWithProperties(context, device, null, null);

        // ── 3. Compilar kernel ────────────────────────────────────────────────
        String kernelSrc = new String(Files.readAllBytes(Paths.get(KERNEL_PATH)));
        cl_program program = clCreateProgramWithSource(context, 1, new String[]{kernelSrc}, null, null);

        try {
            clBuildProgram(program, 0, null, null, null, null);
        } catch (CLException e) {
            long[] logSize = new long[1];
            clGetProgramBuildInfo(program, device, CL_PROGRAM_BUILD_LOG, 0, null, logSize);
            byte[] log = new byte[(int) logSize[0]];
            clGetProgramBuildInfo(program, device, CL_PROGRAM_BUILD_LOG, log.length, Pointer.to(log), null);
            throw new RuntimeException("Falha na compilação do kernel OpenCL:\n" + new String(log));
        }

        cl_kernel kernel = clCreateKernel(program, KERNEL_NAME, null);

        // ── 4. Criar buffers na GPU ───────────────────────────────────────────
        cl_mem bufWordData = clCreateBuffer(context,
                CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                Sizeof.cl_char * totalBytes,
                Pointer.to(wordData), null);

        cl_mem bufOffsets = clCreateBuffer(context,
                CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                Sizeof.cl_int * numWords,
                Pointer.to(offsets), null);

        cl_mem bufLengths = clCreateBuffer(context,
                CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                Sizeof.cl_int * numWords,
                Pointer.to(lengths), null);

        cl_mem bufTarget = clCreateBuffer(context,
                CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                Sizeof.cl_char * targetLen,
                Pointer.to(targetBytes), null);

        cl_mem bufCounts = clCreateBuffer(context,
                CL_MEM_WRITE_ONLY,
                Sizeof.cl_int * numWords,
                null, null);

        // ── 5. Passar argumentos para o kernel ────────────────────────────────
        clSetKernelArg(kernel, 0, Sizeof.cl_mem, Pointer.to(bufWordData));
        clSetKernelArg(kernel, 1, Sizeof.cl_mem, Pointer.to(bufOffsets));
        clSetKernelArg(kernel, 2, Sizeof.cl_mem, Pointer.to(bufLengths));
        clSetKernelArg(kernel, 3, Sizeof.cl_mem, Pointer.to(bufTarget));
        clSetKernelArg(kernel, 4, Sizeof.cl_int, Pointer.to(new int[]{targetLen}));
        clSetKernelArg(kernel, 5, Sizeof.cl_mem, Pointer.to(bufCounts));
        clSetKernelArg(kernel, 6, Sizeof.cl_int, Pointer.to(new int[]{numWords}));

        // ── 6. Executar kernel (1 work-item por palavra) ──────────────────────
        long[] globalWorkSize = {numWords};
        clEnqueueNDRangeKernel(queue, kernel, 1, null, globalWorkSize, null, 0, null, null);
        clFinish(queue);

        // ── 7. Ler resultado de volta para a JVM ──────────────────────────────
        clEnqueueReadBuffer(queue, bufCounts, CL_TRUE, 0,
                Sizeof.cl_int * numWords, Pointer.to(counts), 0, null, null);

        // ── 8. Liberar recursos OpenCL ────────────────────────────────────────
        clReleaseMemObject(bufWordData);
        clReleaseMemObject(bufOffsets);
        clReleaseMemObject(bufLengths);
        clReleaseMemObject(bufTarget);
        clReleaseMemObject(bufCounts);
        clReleaseKernel(kernel);
        clReleaseProgram(program);
        clReleaseCommandQueue(queue);
        clReleaseContext(context);

        // ── 9. Redução: soma os counts na CPU ─────────────────────────────────
        int total = 0;
        for (int c : counts) total += c;
        return total;
    }

    /**
     * Seleciona o dispositivo OpenCL: GPU se disponível, CPU como fallback.
     */
    private static cl_device_id selectDevice(cl_platform_id platform) {
        try {
            int[] n = new int[1];
            clGetDeviceIDs(platform, CL_DEVICE_TYPE_GPU, 0, null, n);
            if (n[0] > 0) {
                cl_device_id[] devices = new cl_device_id[n[0]];
                clGetDeviceIDs(platform, CL_DEVICE_TYPE_GPU, n[0], devices, null);
                System.out.println("[ParallelGPU] Dispositivo: GPU");
                return devices[0];
            }
        } catch (CLException ignored) {}

        // Sem GPU — tenta CPU via OpenCL (Intel/AMD disponibiliza driver OpenCL para CPU)
        int[] n = new int[1];
        clGetDeviceIDs(platform, CL_DEVICE_TYPE_CPU, 0, null, n);
        if (n[0] == 0) throw new RuntimeException("Nenhum dispositivo OpenCL (GPU ou CPU) encontrado.");
        cl_device_id[] devices = new cl_device_id[n[0]];
        clGetDeviceIDs(platform, CL_DEVICE_TYPE_CPU, n[0], devices, null);
        System.out.println("[ParallelGPU] GPU não encontrada — usando CPU via OpenCL.");
        return devices[0];
    }
}
