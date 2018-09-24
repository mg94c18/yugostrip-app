package org.mg94c18.alanford;

import android.content.res.AssetManager;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.zip.GZIPInputStream;

public final class AssetLoader {
    // Pokriva jedan strip, a ako je *4 onda pokriva sve epizode
    private static final int CAPACITY = 128;
    private static final String TAG = "AssetLoader";

    public static ArrayList<String> load(String name, AssetManager assetManager) {
        ArrayList<String> list = new ArrayList<>(CAPACITY);

        InputStream inputStream = null;
        GZIPInputStream gzipInputStream = null;
        Scanner scanner = null;
        try {
            inputStream = assetManager.open(name);
            gzipInputStream = new GZIPInputStream(inputStream);
            scanner = new Scanner(gzipInputStream);

            while (scanner.hasNextLine()) {
                list.add(scanner.nextLine());
            }
        } catch (IOException e) {
            Log.wtf(TAG, "Can't read asset", e);
        } finally {
            try {
                if (scanner != null) scanner.close();
                if (gzipInputStream != null) gzipInputStream.close();
                if (inputStream != null) inputStream.close();
            } catch (IOException e) {
                Log.wtf(TAG, "Can't close", e);
            }
        }

        return list;
    }
}
