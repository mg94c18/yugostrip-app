package org.mg94c18.alanford;

import android.util.Log;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static org.mg94c18.alanford.Logger.TAG;

class IOUtils {
    public static boolean copy(File source, File destination) {
        InputStream inputStream = null;
        OutputStream outputStream = null;
        try {
            inputStream = new FileInputStream(source);
            outputStream = new FileOutputStream(destination);
            copy(inputStream, outputStream);
            inputStream.close();
            outputStream.close();
            return true;
        } catch (IOException e) {
            Log.wtf(TAG, "Can't copy " + source + " to " + destination, e);
            return false;
        } finally {
            closeQuietly(inputStream);
            closeQuietly(outputStream);
        }
    }

    static void copy(InputStream inputStream, OutputStream outputStream) throws IOException {
        byte[] buffer = new byte[2048];
        int bytesRead;

        while ((bytesRead = inputStream.read(buffer)) > 0) {
            outputStream.write(buffer, 0, bytesRead);
        }
    }

    static boolean equals(byte[] A, byte[] B) {
        if (A.length != B.length) {
            return false;
        }

        for (int i = 0; i < A.length; i++) {
            if (A[i] != B[i]) {
                return false;
            }
        }

        return true;
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
