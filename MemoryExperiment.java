import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
public class MemoryExperiment {
    static final long SIZE = 1L << 30;
    static final int PAGE_SIZE = 4096;
    static long getRSS() throws IOException {
        for (String line : Files.readAllLines(
                Path.of("/proc/self/status"))) {
            if (line.startsWith("VmRSS:")) {
                return Long.parseLong(
                        line.trim().split("\\s+")[1]);
            }
        }
        return -1;
    }
    static long getMinorFaults() throws IOException {
        String stat = Files.readString(
                Path.of("/proc/self/stat"));
        int end = stat.lastIndexOf(')');
        String[] fields = stat.substring(end + 2)
                .trim().split("\\s+");
        return Long.parseLong(fields[7]);
    }
    static void showStats(String stage) throws IOException {
        System.out.println("\n" + stage);
        System.out.println("RSS: " + getRSS() + " kB");
        System.out.println("Minor faults: " + getMinorFaults());
    }
    public static void main(String[] args) throws Exception {
        showStats("1. До выделения памяти");
        try (RandomAccessFile file =
                     new RandomAccessFile("big.bin", "rw")) {
            file.setLength(SIZE);
            FileChannel channel = file.getChannel();
            long start = System.nanoTime();
            MappedByteBuffer buffer = channel.map(
                    FileChannel.MapMode.PRIVATE, 0, SIZE);
            long end = System.nanoTime();
            showStats("2. После отображения 1 ГБ");
            System.out.println("Время отображения: "
                    + (end - start) / 1_000_000 + " мс");
            start = System.nanoTime();
            for (long i = 0; i < SIZE; i += PAGE_SIZE) {
                buffer.put((int) i, (byte) 1);
            }
            end = System.nanoTime();
            showStats("3. После первой записи");
            System.out.println("Время первой записи: "
                    + (end - start) / 1_000_000 + " мс");
            start = System.nanoTime();
            for (long i = 0; i < SIZE; i += PAGE_SIZE) {
                buffer.put((int) i, (byte) 2);
            }
            end = System.nanoTime();
            showStats("4. После второй записи");
            System.out.println("Время второй записи: "
                    + (end - start) / 1_000_000 + " мс");
            System.out.println();
            System.out.println("Контрольный байт: "
                    + buffer.get(12345));
        }
        // Удаление временного файла после закрытия RandomAccessFile.
        Files.deleteIfExists(Path.of("big.bin"));
    }
}
