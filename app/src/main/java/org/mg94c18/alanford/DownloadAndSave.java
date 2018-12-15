package org.mg94c18.alanford;

import android.graphics.Bitmap;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.Pair;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.zip.GZIPOutputStream;

import static org.mg94c18.alanford.Logger.LOG_V;
import static org.mg94c18.alanford.Logger.TAG;

public class DownloadAndSave {
    private static final String TMP_SUFFIX = ".tmp";
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
            if (BuildConfig.DEBUG) { LOG_V(">> downloadAndSave(" + imageFile.getName() + ")"); }
            connection = (HttpURLConnection) new URL(link).openConnection();
            connection.connect();
            inputStream = connection.getInputStream();
            Bitmap bitmap = GoogleBitmapHelper.decodeSampledBitmapFromStream(
                    inputStream,
                    width,
                    height,
                    tempFile);
            if (bitmap == null) {
                return Pair.create(null, Boolean.FALSE);
            }
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
            return Pair.create(null, interrupted);
        } finally {
            if (tempFile.exists()) {
                MainActivity.deleteFile(tempFile);
            }
            IOUtils.closeQuietly(inputStream);
            IOUtils.closeQuietly(fileOutputStream);
            if (connection != null) connection.disconnect();
            if (BuildConfig.DEBUG) { LOG_V("<< downloadAndSave(" + imageFile.getName() + ")"); }
        }
    }

    public static boolean saveUrlToFile(String url, File file) {
        HttpURLConnection connection = null;
        InputStream inputStream = null;
        FileOutputStream fileOutputStream = null;
        try {
            if (BuildConfig.DEBUG) { LOG_V(">> saveUrlToFile(" + file.getName() + ")"); }
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.connect();
            inputStream = connection.getInputStream();
            fileOutputStream = new FileOutputStream(file);
            IOUtils.copy(inputStream, fileOutputStream);
            fileOutputStream.close();
            return true;
        } catch (IOException e) {
            if (file.exists()) {
                MainActivity.deleteFile(file);
            }
            return false;
        } finally {
            IOUtils.closeQuietly(inputStream);
            IOUtils.closeQuietly(fileOutputStream);
            if (connection != null) connection.disconnect();
            if (BuildConfig.DEBUG) { LOG_V("<< saveUrlToFile(" + file.getName() + ")"); }
        }
    }

    public static String fileNameFromLink(String link, String episodeId, int page) {
        String extension = link.trim().endsWith(PNG_SUFFIX) ? "png" : "jpg";
        return String.format(Locale.US, "%s_%03d.%s", episodeId, page, extension);
    }

    public static boolean saveUrlDiffToFile(String url, List<String> currentContent, File destination) {
        List<String> remoteContent = AssetLoader.loadFromUrl(url);
        if (remoteContent == null) {
            if (BuildConfig.DEBUG) { LOG_V("Can't loadFromUrl: " + url); }
            return false;
        }

        OutputStream outputStream = null;
        GZIPOutputStream gzipOutputStream = null;
        OutputStreamWriter writer = null;
        try {
            outputStream = new FileOutputStream(destination);
            gzipOutputStream = new GZIPOutputStream(outputStream);
            writer = new OutputStreamWriter(gzipOutputStream);

            int contentSize = Math.max(remoteContent.size(), currentContent.size());
            for (int i = 0; i < contentSize; i++) {
                if (i >= remoteContent.size()) {
                    writer.write(currentContent.get(i));
                } else if (i >= currentContent.size()) {
                    writer.write(remoteContent.get(i));
                } else {
                    if (remoteContent.get(i).isEmpty()) {
                        writer.write(currentContent.get(i));
                    } else if (!remoteContent.get(i).equals(currentContent.get(i))) {
                        writer.write(remoteContent.get(i));
                    }
                }
                writer.write('\n');
            }

            writer.close();
            gzipOutputStream.close();
            outputStream.close();
            return true;
        } catch (IOException e) {
            Log.wtf(TAG, "Can't read asset", e);
            MainActivity.deleteFile(destination);
        } finally {
            IOUtils.closeQuietly(writer);
            IOUtils.closeQuietly(gzipOutputStream);
            IOUtils.closeQuietly(outputStream);
        }
        return false;
    }
}
