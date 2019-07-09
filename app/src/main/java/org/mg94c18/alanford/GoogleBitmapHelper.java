package org.mg94c18.alanford;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

class GoogleBitmapHelper {
    private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (reqWidth == 0 || reqHeight == 0) {
            return inSampleSize;
        }

        if (height > reqHeight || width > reqWidth) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) >= reqHeight
                    && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    /**
     * Creates sampled bitmap from input stream, using given file for temp storage.
     * @param inputStream the stream to read from and consume; we don't call .close on the stream
     * @param reqWidth width for the destination view, or 0 if not available
     * @param reqHeight height for the destination view, or 0 if not available
     * @param tempFile the caller is responsible for deleting the file
     * @return the scaled bitmap from the input stream
     * @throws IOException in case reading from the stream or writing to file fails
     */
    static Bitmap decodeSampledBitmapFromStream(InputStream inputStream, int reqWidth, int reqHeight, File tempFile) throws IOException {
        FileOutputStream fileOutputStream = null;

        try {
            fileOutputStream = new FileOutputStream(tempFile);
            IOUtils.copy(inputStream, fileOutputStream);
            fileOutputStream.close();

            // First decode with inJustDecodeBounds=true to check dimensions
            final BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(tempFile.getAbsolutePath(), options);

            // Calculate inSampleSize
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

            // Decode bitmap with inSampleSize set
            options.inJustDecodeBounds = false;
            return BitmapFactory.decodeFile(tempFile.getAbsolutePath(), options);
        } finally {
            IOUtils.closeQuietly(fileOutputStream);
        }
    }
}
