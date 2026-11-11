package lab2;

import java.math.BigInteger;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public final class MatrixMultiplier {
    private MatrixMultiplier() {}

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

    public static long[][] randomMatrix(int rows, int cols, Random random) {
        if (rows < 1 || cols < 1) throw new IllegalArgumentException("Размеры должны быть положительными.");
        long[][] result = new long[rows][cols];
        for (int i = 0; i < rows; i++)
            for (int j = 0; j < cols; j++)
                result[i][j] = random.nextInt(10);
        return result;
    }

    public static Result multiply(long[][] a, long[][] b, int threadCount, boolean showProgress)
            throws InterruptedException {
        int m = validate(a, "A");
        int k = validate(b, "B");
        if (m != b.length) throw new IllegalArgumentException("Число столбцов A должно совпадать с числом строк B.");
        if (threadCount < 1) throw new IllegalArgumentException("Число потоков должно быть положительным.");

        int n = a.length;
        long[][] c = new long[n][k];
        AtomicInteger completed = new AtomicInteger();
        Thread[] workers = new Thread[threadCount];

        int base = n / threadCount;
        int remainder = n % threadCount;
        int next = 0;

        for (int t = 0; t < threadCount; t++) {
            final int from = next;
            final int to = from + base + (t < remainder ? 1 : 0);
            next = to;
            workers[t] = new Thread(() -> {
                for (int i = from; i < to; i++) {
                    for (int p = 0; p < m; p++) {
                        long value = a[i][p];
                        for (int j = 0; j < k; j++) c[i][j] += value * b[p][j];
                    }
                    completed.incrementAndGet();
                }
            }, "multiply-" + t);
        }

        long start = System.nanoTime();
        for (Thread worker : workers) worker.start();
        waitForWorkers(workers, showProgress, completed, n);
        long elapsed = System.nanoTime() - start;
        return new Result(c, completed.get(), elapsed);
    }

    private static void waitForWorkers(Thread[] workers, boolean showProgress,
                                       AtomicInteger completed, int total) throws InterruptedException {
        int last = -1;
        try {
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
        } catch (InterruptedException e) {
            for (Thread worker : workers) {
                boolean interrupted = false;
                while (worker.isAlive()) {
                    try { worker.join(); }
                    catch (InterruptedException ignored) { interrupted = true; }
                }
                if (interrupted) Thread.currentThread().interrupt();
            }
            throw e;
        }
        if (showProgress && last != 100) System.out.println("Прогресс: 100%");
    }

    public static long[][] multiplySequential(long[][] a, long[][] b) {
        int m = validate(a, "A");
        int k = validate(b, "B");
        if (m != b.length) throw new IllegalArgumentException("Несовместимые размеры матриц.");
        long[][] c = new long[a.length][k];
        for (int i = 0; i < a.length; i++)
            for (int p = 0; p < m; p++)
                for (int j = 0; j < k; j++)
                    c[i][j] += a[i][p] * b[p][j];
        return c;
    }

    public static int validate(long[][] matrix, String name) {
        if (matrix == null || matrix.length == 0 || matrix[0] == null || matrix[0].length == 0)
            throw new IllegalArgumentException("Матрица " + name + " должна быть непустой.");
        int cols = matrix[0].length;
        for (long[] row : matrix)
            if (row == null || row.length != cols)
                throw new IllegalArgumentException("Матрица " + name + " должна быть прямоугольной.");
        return cols;
    }

    public static BigInteger checksum(long[][] matrix) {
        BigInteger sum = BigInteger.ZERO;
        for (long[] row : matrix)
            for (long value : row)
                sum = sum.add(BigInteger.valueOf(value));
        return sum;
    }

    public static boolean equal(long[][] x, long[][] y) {
        if (x.length != y.length) return false;
        for (int i = 0; i < x.length; i++) {
            if (x[i].length != y[i].length) return false;
            for (int j = 0; j < x[i].length; j++)
                if (x[i][j] != y[i][j]) return false;
        }
        return true;
    }
}
