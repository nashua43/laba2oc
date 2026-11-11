package lab2;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Random;

/**
 * Дочерний процесс-генератор.
 * Генерирует матрицы в собственной памяти и передаёт бинарные данные в stdout.
 * Служебные сообщения нельзя выводить в stdout: он используется как канал данных.
 */
public final class GeneratorProcess {
    private GeneratorProcess() {}

    public static void main(String[] args) throws IOException {
        if (args.length != 4) {
            System.err.println("Использование: GeneratorProcess n m k seed");
            System.exit(2);
        }
        int n = Integer.parseInt(args[0]);
        int m = Integer.parseInt(args[1]);
        int k = Integer.parseInt(args[2]);
        long seed = Long.parseLong(args[3]);
        if (n < 1 || m < 1 || k < 1) throw new IllegalArgumentException("Размеры должны быть положительными.");

        Random random = new Random(seed);
        long generationStart = System.nanoTime();
        long[][] a = new long[n][m];
        long[][] b = new long[m][k];
        for (int i = 0; i < n; i++)
            for (int j = 0; j < m; j++) a[i][j] = random.nextInt(10);
        for (int i = 0; i < m; i++)
            for (int j = 0; j < k; j++) b[i][j] = random.nextInt(10);
        long generationNanos = System.nanoTime() - generationStart;

        try (DataOutputStream out = new DataOutputStream(System.out)) {
            out.writeInt(n);
            out.writeInt(m);
            out.writeInt(k);
            out.writeLong(generationNanos);
            for (int i = 0; i < n; i++)
                for (int j = 0; j < m; j++) out.writeLong(a[i][j]);
            for (int i = 0; i < m; i++)
                for (int j = 0; j < k; j++) out.writeLong(b[i][j]);
            out.flush();
        }
    }
}
