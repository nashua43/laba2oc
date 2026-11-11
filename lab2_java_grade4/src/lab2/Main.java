package lab2;

import java.util.Locale;
import java.util.Random;

public final class Main {
    private Main() {}

    public static void main(String[] args) {
        try {
            if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
                printHelp();
                return;
            }
            String mode = args[0].toLowerCase(Locale.ROOT);
            if ("multiply".equals(mode)) runMultiply(args);
            else if ("ipc".equals(mode)) runIpc(args);
            else if ("triangle".equals(mode)) runTriangle(args);
            else {
                System.err.println("Неизвестный режим: " + args[0]);
                printHelp();
                System.exit(1);
            }
        } catch (IllegalArgumentException e) {
            System.err.println("Ошибка: " + e.getMessage());
            System.exit(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Вычисление прервано.");
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Ошибка выполнения: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void runMultiply(String[] args) throws InterruptedException {
        if (args.length != 5) throw new IllegalArgumentException("multiply n m k T");
        int n = positive(args[1]), m = positive(args[2]), k = positive(args[3]), t = positive(args[4]);
        Random random = new Random(42);
        long[][] a = MatrixMultiplier.randomMatrix(n, m, random);
        long[][] b = MatrixMultiplier.randomMatrix(m, k, random);

        MatrixMultiplier.Result result = MatrixMultiplier.multiply(a, b, t, true);
        long[][] expected = MatrixMultiplier.multiplySequential(a, b);
        if (!MatrixMultiplier.equal(result.matrix, expected))
            throw new IllegalStateException("Параллельный результат не совпал с последовательным.");

        printResult(result.matrix, result.elapsedNanos, result.completedRows);
        System.out.println("Проверка с последовательным алгоритмом: успешно.");
    }

    private static void runIpc(String[] args) throws Exception {
        if (args.length != 5) throw new IllegalArgumentException("ipc n m k T");
        int n = positive(args[1]), m = positive(args[2]), k = positive(args[3]), t = positive(args[4]);
        MatrixIpc.Generated generated = MatrixIpc.generateInSeparateProcess(n, m, k, 42L);
        System.out.printf(Locale.ROOT, "Запуск дочернего процесса: %.6f с%n",
                generated.processLaunchNanos / 1_000_000_000.0);
        System.out.printf(Locale.ROOT, "Генерация матриц в дочернем процессе: %.6f с%n",
                generated.generationNanos / 1_000_000_000.0);
        System.out.printf(Locale.ROOT, "Получение данных по каналу (IPC): %.6f с%n",
                generated.transferNanos / 1_000_000_000.0);
        System.out.printf(Locale.ROOT, "Общее время запуска и получения матриц: %.6f с%n",
                generated.elapsedNanos / 1_000_000_000.0);

        MatrixMultiplier.Result result = MatrixMultiplier.multiply(generated.a, generated.b, t, true);
        long[][] expected = MatrixMultiplier.multiplySequential(generated.a, generated.b);
        if (!MatrixMultiplier.equal(result.matrix, expected))
            throw new IllegalStateException("Параллельный результат не совпал с последовательным.");

        System.out.printf(Locale.ROOT, "Время умножения после получения матриц: %.6f с%n",
                result.elapsedNanos / 1_000_000_000.0);
        System.out.println("Завершено строк: " + result.completedRows + " из " + result.matrix.length);
        System.out.println("Контрольная сумма: " + MatrixMultiplier.checksum(result.matrix));
        if (result.matrix.length <= 10 && result.matrix[0].length <= 10) {
            System.out.println("Матрица результата:");
            for (long[] row : result.matrix) {
                for (long value : row) System.out.print(value + " ");
                System.out.println();
            }
        }
        System.out.println("Проверка с последовательным алгоритмом: успешно.");
    }

    private static void runTriangle(String[] args) throws InterruptedException {
        if (args.length != 4) throw new IllegalArgumentException("triangle n T static|dynamic");
        int n = positive(args[1]), t = positive(args[2]);
        String distribution = args[3].toLowerCase(Locale.ROOT);
        if (!"static".equals(distribution) && !"dynamic".equals(distribution))
            throw new IllegalArgumentException("Распределение должно быть static или dynamic.");

        Random random = new Random(42);
        long[][] fullA = MatrixMultiplier.randomMatrix(n, n, random);
        long[][] b = MatrixMultiplier.randomMatrix(n, n, random);
        long[][] a = TriangularMultiplier.createLowerTriangular(fullA);

        boolean dynamic = "dynamic".equals(distribution);
        TriangularMultiplier.Result result =
                TriangularMultiplier.multiply(a, b, t, dynamic, true);
        long[][] expected = TriangularMultiplier.multiplySequential(a, b);
        if (!MatrixMultiplier.equal(result.matrix, expected))
            throw new IllegalStateException("Результат не совпал с последовательным вычислением.");

        printResult(result.matrix, result.elapsedNanos, result.completedRows);
        System.out.println("Распределение строк: " + distribution);
        System.out.println("Проверка с последовательным алгоритмом: успешно.");
        System.out.println("Хранение A: ступенчатый массив; элементов: " + (n * (n + 1L) / 2));
    }

    private static int positive(String value) {
        int number = Integer.parseInt(value);
        if (number < 1) throw new IllegalArgumentException("Параметры должны быть положительными.");
        return number;
    }

    private static void printResult(long[][] matrix, long nanos, int completed) {
        System.out.printf(Locale.ROOT, "Время вычисления: %.6f с%n", nanos / 1_000_000_000.0);
        System.out.println("Завершено строк: " + completed + " из " + matrix.length);
        System.out.println("Контрольная сумма: " + MatrixMultiplier.checksum(matrix));
        if (matrix.length <= 10 && matrix[0].length <= 10) {
            System.out.println("Матрица результата:");
            for (long[] row : matrix) {
                for (long value : row) System.out.print(value + " ");
                System.out.println();
            }
        }
    }

    private static void printHelp() {
        System.out.println("Лабораторная работа №2 — многопоточное умножение матриц");
        System.out.println("Команды:");
        System.out.println("  multiply n m k T       обычное параллельное умножение");
        System.out.println("  ipc n m k T            генерация матриц отдельным процессом и передача через pipe");
        System.out.println("  triangle n T static    нижнетреугольная A, статическое распределение");
        System.out.println("  triangle n T dynamic   нижнетреугольная A, динамическое распределение");
        System.out.println("  help                   справка");
    }
}
