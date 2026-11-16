public class MatrixExperiment {
    static final int N = 8192;
    static final int SIZE = N * N;
    // Обход массива по строкам
    static long sumRows(int[] a) {
        long sum = 0;
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                sum += a[i * N + j];
            }
        }
        return sum;
    }
    // Обход массива по столбцам
    static long sumColumns(int[] a) {
        long sum = 0;
        for (int j = 0; j < N; j++) {
            for (int i = 0; i < N; i++) {
                sum += a[i * N + j];
            }
        }
        return sum;
    }
    public static void main(String[] args) {
        // Создание и заполнение массива
        int[] a = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            a[i] = i % 100;
        }
        // Прогрев методов перед измерениями
        sumRows(a);
        sumColumns(a);
        long[] rowTimes = new long[3];
        long[] columnTimes = new long[3];
        long rowSum = 0;
        long columnSum = 0;
        long checksum = 0;
        // Три измерения каждого способа
        for (int k = 0; k < 3; k++) {
            long start = System.nanoTime();
            rowSum = sumRows(a);
            long end = System.nanoTime();
            rowTimes[k] = end - start;
            checksum += rowSum;
            start = System.nanoTime();
            columnSum = sumColumns(a);
            end = System.nanoTime();
            columnTimes[k] = end - start;
            checksum += columnSum;
        }
        // Вывод контрольных сумм
        System.out.println("Сумма по строкам: " + rowSum);
        System.out.println("Сумма по столбцам: " + columnSum);
        System.out.println("Контрольная сумма: " + checksum);
        System.out.println();
        System.out.println("Обход по строкам:");
        long rowTotal = 0;
        for (int i = 0; i < 3; i++) {
            rowTotal += rowTimes[i];
            System.out.printf("Измерение %d: %.2f мс%n",
                    i + 1, rowTimes[i] / 1_000_000.0);
        }
        System.out.printf("Среднее: %.2f мс%n",
                rowTotal / 3_000_000.0);
        System.out.println();
        System.out.println("Обход по столбцам:");
        long columnTotal = 0;
        for (int i = 0; i < 3; i++) {
            columnTotal += columnTimes[i];
            System.out.printf("Измерение %d: %.2f мс%n",
                    i + 1, columnTimes[i] / 1_000_000.0);
        }
        System.out.printf("Среднее: %.2f мс%n",
                columnTotal / 3_000_000.0);
    }
}
