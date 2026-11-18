import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public class MemoryExperiment {
    // 256 MiB, как требует новая версия лабораторной
    private static final long SIZE = 256L * 1024 * 1024;
    private static final Path FILE = Path.of("big.bin");

    private static class Stats {
        long vmSizeKb;
        long rssKb;
        long minorFaults;

        Stats(long vmSizeKb, long rssKb, long minorFaults) {
            this.vmSizeKb = vmSizeKb;
            this.rssKb = rssKb;
            this.minorFaults = minorFaults;
        }
    }

    // Читаем VmSize и VmRSS из /proc/self/status.
    private static Stats readStats() throws Exception {
        long vmSize = -1;
        long rss = -1;

        for (String line : Files.readAllLines(Path.of("/proc/self/status"))) {
            if (line.startsWith("VmSize:")) {
                vmSize = Long.parseLong(line.trim().split("\\s+")[1]);
            } else if (line.startsWith("VmRSS:")) {
                rss = Long.parseLong(line.trim().split("\\s+")[1]);
            }
        }

        // После последней ')' поля начинаются с поля 3 (state).
        // minor faults — поле 10, то есть индекс 7 в этом массиве.
        String stat = Files.readString(Path.of("/proc/self/stat"));
        int closingBracket = stat.lastIndexOf(')');
        String[] fields = stat.substring(closingBracket + 2)
                               .trim().split("\\s+");
        long minorFaults = Long.parseLong(fields[7]);

        return new Stats(vmSize, rss, minorFaults);
    }

    private static long getPageSize() throws Exception {
        Process process = new ProcessBuilder("getconf", "PAGESIZE")
                .redirectErrorStream(true)
                .start();

        String result = new String(process.getInputStream().readAllBytes()).trim();
        int exitCode = process.waitFor();

        if (exitCode != 0 || result.isEmpty()) {
            throw new IllegalStateException("Не удалось определить размер страницы через getconf");
        }
        return Long.parseLong(result);
    }

    private static double toMiB(long kibibytes) {
        return kibibytes / 1024.0;
    }

    private static void printRow(String stage, Stats current, Stats previous,
                                 double timeMs) {
        long faultDelta = current.minorFaults - previous.minorFaults;

        System.out.printf(Locale.US,
                "%-25s %10.2f %10.2f %14d %12s%n",
                stage,
                toMiB(current.vmSizeKb),
                toMiB(current.rssKb),
                faultDelta,
                Double.isNaN(timeMs) ? "—" : String.format(Locale.US, "%.3f", timeMs));
    }

    // В Java нет публичного метода немедленного снятия MappedByteBuffer.
    // В OpenJDK используем Unsafe.invokeCleaner(), чтобы снять отображение явно.
    private static void unmap(MappedByteBuffer buffer) throws Exception {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field field = unsafeClass.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Object unsafe = field.get(null);

        Method invokeCleaner = unsafeClass.getMethod(
                "invokeCleaner", java.nio.ByteBuffer.class);
        invokeCleaner.invoke(unsafe, buffer);
    }

    public static void main(String[] args) throws Exception {
        long pageSize = getPageSize();
        long pageCount = SIZE / pageSize;

        System.out.println("Опыт 1. «Выделить — не значит получить»");
        System.out.println("Размер блока: " + (SIZE / (1024 * 1024)) + " МиБ");
        System.out.println("Размер страницы: " + pageSize + " байт");
        System.out.println("Количество страниц в блоке: " + pageCount);
        System.out.println();
        System.out.println("Примечание: используется файловое PRIVATE-отображение "
                + "временного разреженного файла, а не анонимный mmap.");
        System.out.println();

        Stats previous = readStats();
        Stats initial = previous;

        System.out.printf("%-25s %10s %10s %14s %12s%n",
                "Этап", "VmSize, МиБ", "RSS, МиБ", "Прирост faults", "Время, мс");
        System.out.println("--------------------------------------------------------------------------------");

        // Фиксируем исходные показатели до создания и отображения файла.
        printRow("Начало", previous, previous, Double.NaN);

        // Создаём разреженный файл и отображаем его приватно.
        // setLength задаёт размер файла; содержимое предварительно не записываем.
        try (RandomAccessFile file = new RandomAccessFile(FILE.toFile(), "rw")) {
            file.setLength(SIZE);
            FileChannel channel = file.getChannel();

            MappedByteBuffer buffer = channel.map(
                    FileChannel.MapMode.PRIVATE, 0, SIZE);

            Stats afterMap = readStats();
            printRow("После выделения", afterMap, previous, Double.NaN);
            previous = afterMap;

            // Первое чтение: читаем по одному байту из каждой страницы.
            long checksum = 0;
            long start = System.nanoTime();

            for (long i = 0; i < SIZE; i += pageSize) {
                checksum += buffer.get((int) i) & 0xFF;
            }

            long end = System.nanoTime();
            double readTimeMs = (end - start) / 1_000_000.0;

            Stats afterRead = readStats();
            printRow("После первого чтения", afterRead, previous, readTimeMs);
            previous = afterRead;

            // Первая запись: записываем 1 в первый байт каждой страницы.
            start = System.nanoTime();

            for (long i = 0; i < SIZE; i += pageSize) {
                buffer.put((int) i, (byte) 1);
            }

            end = System.nanoTime();
            double firstWriteTimeMs = (end - start) / 1_000_000.0;

            Stats afterFirstWrite = readStats();
            printRow("После первой записи", afterFirstWrite,
                    previous, firstWriteTimeMs);
            previous = afterFirstWrite;

            // Повторная запись: в те же позиции записываем 2.
            start = System.nanoTime();

            for (long i = 0; i < SIZE; i += pageSize) {
                buffer.put((int) i, (byte) 2);
            }

            end = System.nanoTime();
            double secondWriteTimeMs = (end - start) / 1_000_000.0;

            Stats afterSecondWrite = readStats();
            printRow("После повторной записи", afterSecondWrite,
                    previous, secondWriteTimeMs);
            previous = afterSecondWrite;

            // Проверяем именно те позиции, в которые выполнялась запись.
            long lastPosition = ((SIZE - 1) / pageSize) * pageSize;
            int value0 = buffer.get(0) & 0xFF;
            int value1 = buffer.get((int) pageSize) & 0xFF;
            int value2 = buffer.get((int) (2 * pageSize)) & 0xFF;
            int valueLast = buffer.get((int) lastPosition) & 0xFF;

            System.out.println();
            System.out.println("Контрольная сумма первого чтения: " + checksum);
            System.out.println("Проверка записанных байтов (должно быть 2):");
            System.out.println("  buffer[0] = " + value0);
            System.out.println("  buffer[PAGE_SIZE] = " + value1);
            System.out.println("  buffer[2 * PAGE_SIZE] = " + value2);
            System.out.println("  buffer[lastPage] = " + valueLast);

            if (value0 != 2 || value1 != 2 || value2 != 2 || valueLast != 2) {
                throw new IllegalStateException("Проверка записанных значений не пройдена");
            }

            // Явно снимаем отображение до закрытия файла и удаления временного файла.
            unmap(buffer);
            buffer = null;
            channel.close();
        } finally {
            Files.deleteIfExists(FILE);
        }

        Stats afterFree = readStats();
        printRow("После освобождения", afterFree, previous, Double.NaN);

        System.out.println();
        System.out.println("Файл big.bin удалён.");
        System.out.println("Опыт завершён.");
    }
}
