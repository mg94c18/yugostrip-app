package org.mg94c18.alanford;

import android.graphics.Bitmap;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.Pair;

import org.mg94c18.alanford.GoogleBitmapHelper;
import org.mg94c18.alanford.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

import static org.mg94c18.alanford.MainActivity.TAG;
import static org.mg94c18.alanford.MainActivity.LOG_V;

public class DownloadAndSave {
    public static final String TMP_SUFFIX = ".tmp";
    private static final int ATTEMPTS = 2;
    private static final String PNG_SUFFIX = ".png";

    public static Bitmap downloadAndSave(String link, File imageFile, int width, int height) {
        for (int i = 0; i < ATTEMPTS; i++) {
            Pair<Bitmap, Boolean> result = downloadAndSaveNoRetry(link, imageFile, width, height);
            if (result.first != null || result.second) {
                return result.first;
            }
        }
        return null;
    }

    private static @NonNull Pair<Bitmap, Boolean> downloadAndSaveNoRetry(String link, File imageFile, int width, int height) {
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
            return Pair.create(bitmap, Boolean.FALSE);
        } catch (IOException e) {
            final Boolean interrupted;
            if (e instanceof java.io.InterruptedIOException) {
                interrupted = Boolean.TRUE;
                // No logs since this is considered normal
            } else {
                interrupted = Boolean.FALSE;
                Log.wtf(TAG, "Can't downloadAndSave(" + imageFile + ")", e);
            }
            if (tempFile.exists()) {
                MainActivity.deleteFile(tempFile);
            }
            return Pair.create(null, interrupted);
        } finally {
            IOUtils.closeQuietly(inputStream);
            IOUtils.closeQuietly(fileOutputStream);
            if (connection != null) connection.disconnect();
            LOG_V("<< downloadAndSave(" + imageFile + ")");
        }
    }

    public static String fileNameFromLink(String link, String episodeId, int page) {
        String extension = link.trim().endsWith(PNG_SUFFIX) ? "png" : "jpg";
        return String.format(Locale.US, "%s_%03d.%s", episodeId, page, extension);
    }
}
