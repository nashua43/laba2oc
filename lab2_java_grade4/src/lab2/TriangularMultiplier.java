package lab2;

import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Умножение нижнетреугольной матрицы A на обычную матрицу B.
 * A хранится компактно: A[i] содержит только i+1 элементов.
 */
public final class TriangularMultiplier {
    private TriangularMultiplier() {}

    public static final class Result {
        public final long[][] matrix;
        public final int completedRows;
        public final long elapsedNanos;

        Result(long[][] matrix, int completedRows, long elapsedNanos) {
            this.matrix = matrix;
            this.completedRows = completedRows;
            this.elapsedNanos = elapsedNanos;
        }
    }

    public static long[][] createLowerTriangular(long[][] square) {
        int n = MatrixMultiplier.validate(square, "A");
        if (square.length != n) throw new IllegalArgumentException("Для A нужна квадратная матрица.");
        long[][] compact = new long[n][];
        for (int i = 0; i < n; i++) {
            compact[i] = new long[i + 1];
            System.arraycopy(square[i], 0, compact[i], 0, i + 1);
        }
        return compact;
    }

    public static Result multiply(long[][] a, long[][] b, int threadCount,
                                  boolean dynamic, boolean showProgress) throws InterruptedException {
        validateTriangular(a);
        int n = a.length;
        int k = MatrixMultiplier.validate(b, "B");
        if (b.length != n) throw new IllegalArgumentException("Число строк B должно совпадать с размером A.");
        if (threadCount < 1) throw new IllegalArgumentException("Число потоков должно быть положительным.");

        long[][] c = new long[n][k];
        AtomicInteger completed = new AtomicInteger();
        AtomicInteger nextRow = new AtomicInteger();
        Thread[] workers = new Thread[threadCount];

        int base = n / threadCount;
        int rem = n % threadCount;
        int cursor = 0;

        for (int t = 0; t < threadCount; t++) {
            final int from = cursor;
            final int to = from + base + (t < rem ? 1 : 0);
            cursor = to;

            workers[t] = new Thread(() -> {
                if (dynamic) {
                    int row;
                    while ((row = nextRow.getAndIncrement()) < n) {
                        calculateRow(a, b, c, row, k);
                        completed.incrementAndGet();
                    }
                } else {
                    for (int row = from; row < to; row++) {
                        calculateRow(a, b, c, row, k);
                        completed.incrementAndGet();
                    }
                }
            }, (dynamic ? "dynamic-" : "static-") + t);
        }

        long start = System.nanoTime();
        for (Thread worker : workers) worker.start();
        waitForWorkers(workers, showProgress, completed, n);
        long elapsed = System.nanoTime() - start;
        return new Result(c, completed.get(), elapsed);
    }

    private static void calculateRow(long[][] a, long[][] b, long[][] c, int i, int k) {
        // Порядок p -> j обрабатывает строки B и C последовательно,
        // улучшая локальность доступа к памяти.
        for (int p = 0; p <= i; p++) {
            long value = a[i][p];
            for (int j = 0; j < k; j++) c[i][j] += value * b[p][j];
        }
    }

    private static void waitForWorkers(Thread[] workers, boolean showProgress,
                                       AtomicInteger completed, int total) throws InterruptedException {
        int last = -1;
        boolean alive;
        do {
            alive = false;
            for (Thread worker : workers) {
                if (worker.isAlive()) {
                    alive = true;
                    worker.join(100);
                }
            }
            if (showProgress) {
                int percent = (int) (100L * completed.get() / total);
                if (percent != last) {
                    System.out.println("Прогресс: " + percent + "%");
                    last = percent;
                }
            }
        } while (alive);
        for (Thread worker : workers) worker.join();
        if (showProgress && last != 100) System.out.println("Прогресс: 100%");
    }

    public static long[][] multiplySequential(long[][] a, long[][] b) {
        validateTriangular(a);
        int n = a.length;
        int k = MatrixMultiplier.validate(b, "B");
        if (b.length != n) throw new IllegalArgumentException("Несовместимые размеры.");
        long[][] c = new long[n][k];
        for (int i = 0; i < n; i++) calculateRow(a, b, c, i, k);
        return c;
    }

    public static BigInteger checksum(long[][] matrix) {
        return MatrixMultiplier.checksum(matrix);
    }

    private static void validateTriangular(long[][] a) {
        if (a == null || a.length == 0) throw new IllegalArgumentException("Матрица A пуста.");
        for (int i = 0; i < a.length; i++) {
            if (a[i] == null || a[i].length != i + 1)
                throw new IllegalArgumentException("A должна храниться как нижнетреугольная ступенчатая матрица.");
        }
    }
}
