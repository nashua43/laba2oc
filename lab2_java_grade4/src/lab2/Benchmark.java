package lab2;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Экспериментальная программа для лабораторной работы № 2.
 * Без аргументов: исследование обычного многопоточного умножения (оценка 3).
 * triangle: сравнение static/dynamic для нижнетреугольной матрицы (оценка 4).
 * ipc: сравнение времени получения матриц отдельным процессом и времени умножения.
 */
public final class Benchmark {
    private static final int[] SIZES = {500, 1500, 3000};
    private static final int REPEATS = 5;

    private Benchmark() {}

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && "--quick".equals(args[0])) {
            runMatrixBenchmark(new int[]{20, 40}, new int[]{1, 2, 4}, 2,
                    "matrix_benchmark_quick.csv", true);
            return;
        }
        if (args.length == 2 && "triangle".equalsIgnoreCase(args[0]) && "--quick".equals(args[1])) {
            runTriangleBenchmark(new int[]{20, 40}, new int[]{1, 2, 4}, 2,
                    "triangle_benchmark_quick.csv", true);
            return;
        }
        if (args.length == 2 && "ipc".equalsIgnoreCase(args[0])) {
            int threads = positive(args[1]);
            runIpcBenchmark(new int[]{500, 1500, 3000}, threads, "ipc_benchmark.csv");
            return;
        }
        if (args.length == 1 && "triangle".equalsIgnoreCase(args[0])) {
            runTriangleBenchmark(SIZES, threadCounts(), REPEATS, "triangle_benchmark.csv", false);
            return;
        }
        if (args.length == 0) {
            runMatrixBenchmark(SIZES, threadCounts(), REPEATS, "matrix_benchmark.csv", false);
            return;
        }
        throw new IllegalArgumentException("Использование: Benchmark [--quick | triangle [--quick] | ipc T]");
    }

    /**
     * Набор потоков по заданию: все значения 1..2L, затем 4L и 8L,
     * где L — число логических процессоров, доступных JVM.
     */
    private static int[] threadCounts() {
        int logical = Runtime.getRuntime().availableProcessors();
        List<Integer> values = new ArrayList<Integer>();
        for (int t = 1; t <= logical; t++) values.add(t);
        for (int t = logical + 1; t <= 2 * logical; t++) values.add(t);
        values.add(4 * logical);
        values.add(8 * logical);
        int[] result = new int[values.size()];
        for (int i = 0; i < values.size(); i++) result[i] = values.get(i);
        System.out.println("Доступно логических процессоров JVM: " + logical);
        System.out.print("Проверяемые значения T: ");
        for (int i = 0; i < result.length; i++) System.out.print((i == 0 ? "" : ", ") + result[i]);
        System.out.println();
        return result;
    }

    private static void runMatrixBenchmark(int[] sizes, int[] threads, int repeats,
                                           String fileName, boolean quick) throws Exception {
        try (BufferedWriter out = Files.newBufferedWriter(Paths.get(fileName), StandardCharsets.UTF_8)) {
            out.write("size,threads,run,time_seconds,checksum\n");
            for (int n : sizes) {
                String[] expectedByRun = new String[repeats + 1];
                for (int t : threads) {
                    for (int r = 1; r <= repeats; r++) {
                        Random random = new Random(seed(n, r));
                        long[][] a = MatrixMultiplier.randomMatrix(n, n, random);
                        long[][] b = MatrixMultiplier.randomMatrix(n, n, random);
                        MatrixMultiplier.multiply(a, b, t, false); // прогрев JVM, вне замера
                        MatrixMultiplier.Result result = MatrixMultiplier.multiply(a, b, t, false);
                        String checksum = MatrixMultiplier.checksum(result.matrix).toString();
                        if (expectedByRun[r] == null) expectedByRun[r] = checksum;
                        else if (!expectedByRun[r].equals(checksum))
                            throw new IllegalStateException("Контрольная сумма отличается: n=" + n + ", T=" + t + ", повтор=" + r);
                        double seconds = result.elapsedNanos / 1_000_000_000.0;
                        writeRow(out, String.format(Locale.ROOT, "%d,%d,%d,%.9f,%s%n", n, t, r, seconds, checksum));
                        System.out.printf(Locale.ROOT, "%d x %d | T=%d | %d/%d | %.6f с%n", n, n, t, r, repeats, seconds);
                    }
                }
            }
        }
        System.out.println("Результаты обычного умножения сохранены в " + fileName
                + (quick ? " (только проверочный режим; не для отчёта)." : "."));
    }

    private static void runTriangleBenchmark(int[] sizes, int[] threads, int repeats,
                                             String fileName, boolean quick) throws Exception {
        try (BufferedWriter out = Files.newBufferedWriter(Paths.get(fileName), StandardCharsets.UTF_8)) {
            out.write("size,threads,distribution,run,time_seconds,checksum\n");
            for (int n : sizes) {
                String[] expectedByRun = new String[repeats + 1];
                for (int t : threads) {
                    for (int r = 1; r <= repeats; r++) {
                        // Одинаковые исходные матрицы для static и dynamic;
                        // для следующей комбинации они воспроизводятся по тому же seed.
                        Random random = new Random(seed(n, r));
                        long[][] a = TriangularMultiplier.createLowerTriangular(
                                MatrixMultiplier.randomMatrix(n, n, random));
                        long[][] b = MatrixMultiplier.randomMatrix(n, n, random);
                        for (String mode : new String[]{"static", "dynamic"}) {
                            boolean dynamic = "dynamic".equals(mode);
                            TriangularMultiplier.multiply(a, b, t, dynamic, false); // прогрев
                            TriangularMultiplier.Result result =
                                    TriangularMultiplier.multiply(a, b, t, dynamic, false);
                            String checksum = TriangularMultiplier.checksum(result.matrix).toString();
                            if (expectedByRun[r] == null) expectedByRun[r] = checksum;
                            else if (!expectedByRun[r].equals(checksum))
                                throw new IllegalStateException("Результат отличается: n=" + n + ", T=" + t + ", mode=" + mode + ", повтор=" + r);
                            double seconds = result.elapsedNanos / 1_000_000_000.0;
                            writeRow(out, String.format(Locale.ROOT, "%d,%d,%s,%d,%.9f,%s%n",
                                    n, t, mode, r, seconds, checksum));
                            System.out.printf(Locale.ROOT, "%d x %d | T=%d | %s | %d/%d | %.6f с%n",
                                    n, n, t, mode, r, repeats, seconds);
                        }
                    }
                }
            }
        }
        System.out.println("Результаты сравнения static/dynamic сохранены в " + fileName
                + (quick ? " (только проверочный режим; не для отчёта)." : "."));
    }

    private static void runIpcBenchmark(int[] sizes, int threads, String fileName) throws Exception {
        try (BufferedWriter out = Files.newBufferedWriter(Paths.get(fileName), StandardCharsets.UTF_8)) {
            out.write("size,threads,process_launch_seconds,generation_seconds,transfer_seconds,total_ipc_seconds,multiplication_seconds,checksum\n");
            for (int n : sizes) {
                MatrixIpc.Generated generated = MatrixIpc.generateInSeparateProcess(n, n, n, seed(n, 1));
                // Прогрев вычислителя выполняется после получения данных и не входит в измерение.
                MatrixMultiplier.multiply(generated.a, generated.b, threads, false);
                MatrixMultiplier.Result result = MatrixMultiplier.multiply(generated.a, generated.b, threads, false);
                double launch = generated.processLaunchNanos / 1_000_000_000.0;
                double generation = generated.generationNanos / 1_000_000_000.0;
                double transfer = generated.transferNanos / 1_000_000_000.0;
                double totalIpc = generated.elapsedNanos / 1_000_000_000.0;
                double multiply = result.elapsedNanos / 1_000_000_000.0;
                String checksum = MatrixMultiplier.checksum(result.matrix).toString();
                writeRow(out, String.format(Locale.ROOT, "%d,%d,%.9f,%.9f,%.9f,%.9f,%.9f,%s%n",
                        n, threads, launch, generation, transfer, totalIpc, multiply, checksum));
                System.out.printf(Locale.ROOT,
                        "%d x %d | T=%d | запуск: %.6f с | генерация: %.6f с | IPC: %.6f с | умножение: %.6f с%n",
                        n, n, threads, launch, generation, transfer, multiply);
            }
        }
        System.out.println("Сравнение IPC и умножения сохранено в " + fileName + ".");
    }

    private static void writeRow(BufferedWriter out, String row) throws IOException {
        out.write(row);
        out.flush(); // сохраняем уже завершённые измерения при остановке эксперимента
    }

    private static long seed(int n, int run) {
        return 20261111L + 31L * n + 1009L * run;
    }

    private static int positive(String value) {
        int result = Integer.parseInt(value);
        if (result < 1) throw new IllegalArgumentException("Количество потоков должно быть положительным.");
        return result;
    }
}
