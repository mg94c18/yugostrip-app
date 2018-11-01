package org.mg94c18.alanford;

import android.graphics.Bitmap;
import android.util.Log;

import org.mg94c18.alanford.GoogleBitmapHelper;
import org.mg94c18.alanford.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import static org.mg94c18.alanford.MainActivity.TAG;
import static org.mg94c18.alanford.MainActivity.LOG_V;

public class DownloadAndSave {
    public static final String TMP_SUFFIX = ".tmp";

    public static Bitmap downloadAndSave(String link, File imageFile, int width, int height) {
        HttpURLConnection connection = null;
        InputStream inputStream = null;
        FileOutputStream fileOutputStream = null;
        File tempFile = new File(imageFile.getAbsolutePath() + TMP_SUFFIX);
        try {
            LOG_V(">> downloadAndSave(" + imageFile + ")");
            connection = (HttpURLConnection) new URL(link).openConnection();
            connection.connect();
            inputStream = connection.getInputStream();
            Bitmap bitmap = GoogleBitmapHelper.decodeSampledBitmapFromStream(
                    inputStream,
                    width,
                    height,
                    tempFile);

            fileOutputStream = new FileOutputStream(tempFile);
            Bitmap.CompressFormat compressFormat = link.endsWith(".png") ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG;
            bitmap.compress(compressFormat, 100, fileOutputStream);
            fileOutputStream.close();

            if (!tempFile.renameTo(imageFile)) {
                Log.wtf(TAG, "Can't rename " + tempFile + " to " + imageFile);
            }
            return bitmap;
        } catch (IOException e) {
            if (e instanceof java.io.InterruptedIOException) {
                // No logs since this is considered normal
            } else {
                Log.wtf(TAG, "Can't downloadAndSave", e);
            }
            if (tempFile.exists()) {
                MainActivity.deleteFile(tempFile);
            }
            return null;
        } finally {
            IOUtils.closeQuietly(inputStream);
            IOUtils.closeQuietly(fileOutputStream);
            if (connection != null) connection.disconnect();
            LOG_V("<< downloadAndSave(" + imageFile + ")");
        }
    }


}