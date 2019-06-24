package org.mg94c18.alanford;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.util.Log;

import java.io.File;

import static org.mg94c18.alanford.Logger.TAG;

public class ExternalStorageHelper {
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public static File getExternalCacheDir(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return null;
        }
        File[] dirs = context.getExternalCacheDirs();
        if (dirs == null) {
            return null;
        }

        for (File dir : dirs) {
            if (dir != null && !Environment.isExternalStorageEmulated(dir)) {
                return dir;
            }
        }

        return null;
    }

    public static File getInternalOfflineDir(@NonNull Context context) {
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

    public static File getAvailableCacheDir(@NonNull Context context) {
        File dir = getExternalCacheDir(context);
        if (dir != null) {
            return dir;
        }
        return getInternalOfflineDir(context);
    }

}
