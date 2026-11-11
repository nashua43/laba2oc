#!/usr/bin/env python3
"""Построение графиков лабораторной работы № 2 по CSV-файлам."""
import argparse
import csv
import os
from collections import defaultdict
from statistics import mean

import matplotlib.pyplot as plt


def trimmed_mean(values):
    """По методике: удалить максимум и минимум, усреднить оставшиеся 3 из 5."""
    values = sorted(values)
    if len(values) >= 3:
        values = values[1:-1]
    return mean(values)


def read_rows(path):
    if not os.path.isfile(path):
        return []
    with open(path, newline="", encoding="utf-8") as stream:
        return list(csv.DictReader(stream))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--physical-cores", type=int, default=None,
                        help="число физических ядер для вертикальной линии")
    parser.add_argument("--logical-processors", type=int, default=None,
                        help="число логических процессоров для вертикальной линии")
    parser.add_argument("--folder", default="graphs", help="папка для графиков")
    args = parser.parse_args()
    os.makedirs(args.folder, exist_ok=True)
    created = []

    # Оценка 3: обычное умножение матриц.
    rows = read_rows("matrix_benchmark.csv")
    if rows:
        groups = defaultdict(list)
        for row in rows:
            groups[(int(row["size"]), int(row["threads"]))].append(float(row["time_seconds"]))
        sizes = sorted({key[0] for key in groups})
        plt.figure()
        for size in sizes:
            points = sorted((threads, trimmed_mean(values))
                            for (n, threads), values in groups.items() if n == size)
            plt.plot([x for x, _ in points], [y for _, y in points], marker="o", label=f"{size} × {size}")
        plt.yscale("log")
        plt.xlabel("Количество потоков")
        plt.ylabel("Среднее время умножения, с (логарифмическая шкала)")
        plt.title("Обычное умножение: время от числа потоков")
        plt.grid(True)
        plt.legend()
        plt.tight_layout()
        path = os.path.join(args.folder, "matrix_time_all_sizes.png")
        plt.savefig(path, dpi=180)
        plt.close()
        created.append(path)

        plt.figure()
        for size in sizes:
            points = sorted((threads, trimmed_mean(values))
                            for (n, threads), values in groups.items() if n == size)
            baseline = next((value for t, value in points if t == 1), None)
            if baseline:
                plt.plot([x for x, _ in points], [baseline / y for _, y in points],
                         marker="o", label=f"{size} × {size}")
        max_t = max(int(row["threads"]) for row in rows)
        plt.plot(range(1, max_t + 1), range(1, max_t + 1), "--", label="Идеальное ускорение S = T")
        if args.physical_cores:
            plt.axvline(args.physical_cores, linestyle=":", label=f"Физические ядра: {args.physical_cores}")
        if args.logical_processors:
            plt.axvline(args.logical_processors, linestyle="-.", label=f"Логические процессоры: {args.logical_processors}")
        plt.xlabel("Количество потоков")
        plt.ylabel("Ускорение S(T)")
        plt.title("Обычное умножение: ускорение")
        plt.grid(True)
        plt.legend()
        plt.tight_layout()
        path = os.path.join(args.folder, "matrix_speedup_all_sizes.png")
        plt.savefig(path, dpi=180)
        plt.close()
        created.append(path)

        plt.figure()
        for size in sizes:
            points = sorted((threads, trimmed_mean(values))
                            for (n, threads), values in groups.items() if n == size)
            baseline = next((value for t, value in points if t == 1), None)
            if baseline:
                plt.plot([x for x, _ in points], [baseline / y / x for x, y in points],
                         marker="o", label=f"{size} × {size}")
        plt.xlabel("Количество потоков")
        plt.ylabel("Эффективность E(T)")
        plt.title("Обычное умножение: эффективность")
        plt.grid(True)
        plt.legend()
        plt.tight_layout()
        path = os.path.join(args.folder, "matrix_efficiency.png")
        plt.savefig(path, dpi=180)
        plt.close()
        created.append(path)

    # Оценка 4: статическое и динамическое распределение строк.
    rows = read_rows("triangle_benchmark.csv")
    if rows:
        groups = defaultdict(list)
        for row in rows:
            groups[(int(row["size"]), int(row["threads"]), row["distribution"])].append(float(row["time_seconds"]))
        for size in sorted({key[0] for key in groups}):
            plt.figure()
            for mode in ("static", "dynamic"):
                points = sorted((threads, trimmed_mean(values))
                                for (n, threads, distribution), values in groups.items()
                                if n == size and distribution == mode)
                if points:
                    plt.plot([x for x, _ in points], [y for _, y in points],
                             marker="o", label=mode)
            plt.xlabel("Количество потоков")
            plt.ylabel("Среднее время умножения, с")
            plt.title(f"Нижнетреугольная матрица {size} × {size}: static / dynamic")
            plt.grid(True)
            plt.legend()
            plt.tight_layout()
            path = os.path.join(args.folder, f"triangle_{size}_time.png")
            plt.savefig(path, dpi=180)
            plt.close()
            created.append(path)

    # IPC: генерация/передача данных сравнивается с последующим умножением.
    rows = read_rows("ipc_benchmark.csv")
    if rows:
        sizes = [int(row["size"]) for row in rows]
        transfer = [float(row["transfer_seconds"]) for row in rows]
        mult = [float(row["multiplication_seconds"]) for row in rows]
        x = list(range(len(sizes)))
        width = 0.36
        plt.figure()
        plt.bar([v - width / 2 for v in x], transfer, width, label="Получение данных по IPC")
        plt.bar([v + width / 2 for v in x], mult, width, label="Умножение")
        plt.xticks(x, [f"{n} × {n}" for n in sizes])
        plt.xlabel("Размер матриц")
        plt.ylabel("Время, с")
        plt.title("Время получения данных по IPC и время умножения")
        plt.grid(axis="y")
        plt.legend()
        plt.tight_layout()
        path = os.path.join(args.folder, "ipc_vs_multiplication.png")
        plt.savefig(path, dpi=180)
        plt.close()
        created.append(path)

    if not created:
        raise SystemExit("CSV-файлы не найдены. Сначала выполните полные экспериментальные серии.")
    print("Созданы графики:")
    for path in created:
        print(" - " + path)


if __name__ == "__main__":
    main()
