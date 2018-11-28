package org.mg94c18.alanford;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.os.Environment;

import java.io.File;

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
}
