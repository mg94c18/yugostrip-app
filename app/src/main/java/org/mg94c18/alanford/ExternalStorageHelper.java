package org.mg94c18.alanford;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.File;

import static org.mg94c18.alanford.Logger.TAG;

public class ExternalStorageHelper {
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Nullable public static File getExternalCacheDir(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return null;
        }
        File[] dirs = context.getExternalCacheDirs();
        if (dirs == null) {
            return null;
        }

        for (File dir : dirs) {
            try {
                if (dir != null && !Environment.isExternalStorageEmulated(dir)) {
                    return dir;
                }
            } catch (IllegalArgumentException e) {
                Log.wtf(TAG, "Can't check if isExternalStorageEmulated(" + dir + ")", e);
            }
        }

        return null;
    }

    @NonNull public static File getInternalOfflineDir(@NonNull Context context) {
        File offlineDir = new File(context.getCacheDir(), MainActivity.INTERNAL_OFFLINE);
        if (!offlineDir.exists()) {
            boolean success = offlineDir.mkdir();
            if (!success) {
                Log.wtf(TAG, "Can't create dir");
                // proceed and hope that mkdir() lied... if it fails we'll fail for the user
            }
        }
        return offlineDir;
    }

    static long getFreeSpaceAtDir(@NonNull File dir) {
        try {
            StatFs statFs = new StatFs(dir.getPath());
            final long bytesAvailable;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                bytesAvailable = statFs.getAvailableBlocksLong() * statFs.getBlockSizeLong();
            } else {
                bytesAvailable = statFs.getAvailableBlocks() * statFs.getBlockSize();
            }
            return bytesAvailable;
        } catch (IllegalArgumentException iae) {
            Log.wtf(TAG, "Can't StatFs()", iae);
            return -1;
        }
    }
}
