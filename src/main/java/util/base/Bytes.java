package util.base;


import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * A utility class to perform I/O operations such as copying InputStream to OutputStream.
 */
public class Bytes {
    public static List<Byte> asList(Number ... bytes) {
        List<Byte> byteList = new ArrayList<>();
        for (Number b : bytes) {
            byteList.add(b.byteValue());
        }
        return byteList;
    }

    public static long copy(InputStream from, OutputStream to) throws IOException {
        byte[] buf = new byte[8192];
        long total = 0;
        while (true) {
            int r = from.read(buf);
            if (r == -1) {
                break;
            }
            to.write(buf, 0, r);
            total += r;
        }
        return total;
    }

    public static long copy(InputStream from, File to) throws IOException {
        return copy(from, new FileOutputStream(to));
    }

    /**
     * Read all bytes from the input stream.
     * @param in input stream
     * @return byte array
     */
    public static byte[] readFully(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024 * 16];
        int read;
        while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
        return out.toByteArray();
    }
}
