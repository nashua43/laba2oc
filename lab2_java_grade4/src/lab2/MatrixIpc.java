package lab2;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

/** Запуск отдельной JVM и получение матриц через канал stdout дочернего процесса. */
public final class MatrixIpc {
    private MatrixIpc() {}

    public static final class Generated {
        public final long[][] a;
        public final long[][] b;
        public final long elapsedNanos;
        public final long processLaunchNanos;
        public final long generationNanos;
        public final long transferNanos;

        Generated(long[][] a, long[][] b, long elapsedNanos,
                  long processLaunchNanos, long generationNanos, long transferNanos) {
            this.a = a;
            this.b = b;
            this.elapsedNanos = elapsedNanos;
            this.processLaunchNanos = processLaunchNanos;
            this.generationNanos = generationNanos;
            this.transferNanos = transferNanos;
        }
    }

    public static Generated generateInSeparateProcess(int n, int m, int k, long seed)
            throws IOException, InterruptedException {
        if (n < 1 || m < 1 || k < 1) throw new IllegalArgumentException("Размеры должны быть положительными.");

        String java = Paths.get(System.getProperty("java.home"), "bin",
                isWindows() ? "java.exe" : "java").toString();
        String classPath = System.getProperty("java.class.path");
        ProcessBuilder builder = new ProcessBuilder(java, "-cp", classPath,
                "lab2.GeneratorProcess", String.valueOf(n), String.valueOf(m),
                String.valueOf(k), String.valueOf(seed));
        builder.redirectError(ProcessBuilder.Redirect.INHERIT);

        long totalStart = System.nanoTime();
        long launchStart = System.nanoTime();
        Process process = builder.start();
        long launchNanos = System.nanoTime() - launchStart;
        long[][] a;
        long[][] b;
        long generationNanos;
        long transferNanos;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(process.getInputStream()))) {
            int gotN = in.readInt();
            int gotM = in.readInt();
            int gotK = in.readInt();
            generationNanos = in.readLong();
            if (gotN != n || gotM != m || gotK != k)
                throw new IOException("Генератор передал неожиданные размеры матриц.");

            a = new long[n][m];
            b = new long[m][k];
            long transferStart = System.nanoTime();
            for (int i = 0; i < n; i++)
                for (int j = 0; j < m; j++) a[i][j] = in.readLong();
            for (int i = 0; i < m; i++)
                for (int j = 0; j < k; j++) b[i][j] = in.readLong();
            transferNanos = System.nanoTime() - transferStart;
        } catch (IOException e) {
            process.destroyForcibly();
            throw e;
        }

        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("Процесс-генератор не завершился за отведённое время.");
        }
        if (process.exitValue() != 0)
            throw new IOException("Процесс-генератор завершился с кодом " + process.exitValue());
        return new Generated(a, b, System.nanoTime() - totalStart,
                launchNanos, generationNanos, transferNanos);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}
