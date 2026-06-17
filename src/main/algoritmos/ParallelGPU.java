package main.algoritmos;

import main.infra.Resultado;
import org.jocl.*;

import java.nio.file.Files;
import java.nio.file.Paths;

import static org.jocl.CL.*;

/**
 * ParallelGPU — Contagem de palavras usando OpenCL (JOCL 2.0.4).
 *
 * Otimização: redução feita NA GPU com atomic_inc num contador único.
 * Compatível com OpenCL 1.2 (usa clCreateCommandQueue; ...WithProperties é 2.0).
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
        return new Resultado("ParallelGPU", nomeAmostra, palavraAlvo, contagemTotal, tempoMs, 0);
    }

    private static int executarOpenCL(String[] palavras, String palavraAlvo) throws Exception {

        int numWords = palavras.length;

        // 1. Serializa String[] em arrays primitivos.
        //    Converte para byte[] UTF-8 ANTES de somar totalBytes, evitando
        //    estouro de array com caracteres multibyte (ã, ç, é...).
        byte[][] allBytes = new byte[numWords][];
        int totalBytes = 0;
        for (int i = 0; i < numWords; i++) {
            allBytes[i] = palavras[i].getBytes("UTF-8");
            totalBytes += allBytes[i].length;
        }

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

        // Contador ÚNICO de saída, iniciado em 0 (a GPU soma tudo aqui via atomic_inc).
        int[] count = new int[]{0};

        // 2. Inicialização OpenCL
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
        cl_context context = clCreateContext(props, 1, new cl_device_id[]{device}, null, null, null);

        // OpenCL 1.2 (macOS): clCreateCommandQueue, NÃO o ...WithProperties (2.0).
        cl_command_queue queue = clCreateCommandQueue(context, device, 0, null);

        // 3. Compila o kernel
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

        // 4. Buffers de entrada (read-only) + buffer de saída (1 inteiro)
        cl_mem bufWordData = clCreateBuffer(context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                Sizeof.cl_char * totalBytes, Pointer.to(wordData), null);
        cl_mem bufOffsets = clCreateBuffer(context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                Sizeof.cl_int * numWords, Pointer.to(offsets), null);
        cl_mem bufLengths = clCreateBuffer(context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                Sizeof.cl_int * numWords, Pointer.to(lengths), null);
        cl_mem bufTarget = clCreateBuffer(context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                Sizeof.cl_char * targetLen, Pointer.to(targetBytes), null);

        // READ_WRITE porque o atomic_inc lê e escreve; COPY_HOST_PTR zera o contador na GPU.
        cl_mem bufCount = clCreateBuffer(context, CL_MEM_READ_WRITE | CL_MEM_COPY_HOST_PTR,
                Sizeof.cl_int, Pointer.to(count), null);

        // 5. Argumentos do kernel
        clSetKernelArg(kernel, 0, Sizeof.cl_mem, Pointer.to(bufWordData));
        clSetKernelArg(kernel, 1, Sizeof.cl_mem, Pointer.to(bufOffsets));
        clSetKernelArg(kernel, 2, Sizeof.cl_mem, Pointer.to(bufLengths));
        clSetKernelArg(kernel, 3, Sizeof.cl_mem, Pointer.to(bufTarget));
        clSetKernelArg(kernel, 4, Sizeof.cl_int, Pointer.to(new int[]{targetLen}));
        clSetKernelArg(kernel, 5, Sizeof.cl_mem, Pointer.to(bufCount));
        clSetKernelArg(kernel, 6, Sizeof.cl_int, Pointer.to(new int[]{numWords}));

        // 6. Executa: 1 work-item por palavra
        long[] globalWorkSize = {numWords};
        clEnqueueNDRangeKernel(queue, kernel, 1, null, globalWorkSize, null, 0, null, null);
        clFinish(queue);

        // 7. Lê o resultado (apenas 1 inteiro = 4 bytes)
        clEnqueueReadBuffer(queue, bufCount, CL_TRUE, 0, Sizeof.cl_int, Pointer.to(count), 0, null, null);

        // 8. Libera recursos
        clReleaseMemObject(bufWordData);
        clReleaseMemObject(bufOffsets);
        clReleaseMemObject(bufLengths);
        clReleaseMemObject(bufTarget);
        clReleaseMemObject(bufCount);
        clReleaseKernel(kernel);
        clReleaseProgram(program);
        clReleaseCommandQueue(queue);
        clReleaseContext(context);

        // 9. A GPU já fez a redução via atomic_inc — sem loop na CPU.
        return count[0];
    }

    /** Seleciona o dispositivo OpenCL: GPU se disponível, CPU como fallback. */
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
        int[] n = new int[1];
        clGetDeviceIDs(platform, CL_DEVICE_TYPE_CPU, 0, null, n);
        if (n[0] == 0) throw new RuntimeException("Nenhum dispositivo OpenCL (GPU ou CPU) encontrado.");
        cl_device_id[] devices = new cl_device_id[n[0]];
        clGetDeviceIDs(platform, CL_DEVICE_TYPE_CPU, n[0], devices, null);
        System.out.println("[ParallelGPU] GPU não encontrada — usando CPU via OpenCL.");
        return devices[0];
    }
}