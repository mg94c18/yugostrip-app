package org.mg94c18.alanford;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

class IOUtils {
    static void copy(InputStream inputStream, OutputStream outputStream) throws IOException {
        byte[] buffer = new byte[2048];
        int bytesRead;

        while ((bytesRead = inputStream.read(buffer)) > 0) {
            outputStream.write(buffer, 0, bytesRead);
        }
    }

    static void closeQuietly(Closeable closeable) {
        if (closeable == null) {
            return;
        }

        try {
            closeable.close();
        } catch (IOException e) {
            // Quietly means quietly
            // Log.wtf(TAG, "Can't closeQuietly", e);
        }
    }
}
