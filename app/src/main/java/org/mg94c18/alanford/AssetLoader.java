package org.mg94c18.alanford;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import static org.mg94c18.alanford.Logger.LOG_V;
import static org.mg94c18.alanford.Logger.TAG;

public final class AssetLoader {
    public static final String TITLES = "titles";
    public static final String NUMBERS = "numbers";
    public static final String DATES = "dates";

    private static final String ASSET_PREFS_NAME = "asset_prefs";
    private static final long TIME_TOO_MUCH_IN_PAST = 1500000000000L; // Thu Jul 13 19:40:00 PDT 2017
    private static final long TIME_TOO_MUCH_IN_FUTURE = 2000000000000L; // Tue May 17 20:33:20 PDT 2033

    // Pokriva jedan strip, a ako je *4 onda pokriva sve epizode
    private static final int CAPACITY = 128;

    public static final String ASSETS = "assets_";

    private static Set<String> currentAssets = Collections.emptySet();

    public static synchronized @NonNull Set<String> getCurrentAssets(AssetManager assetManager) {
        if (!currentAssets.isEmpty()) {
            return currentAssets;
        }

        String[] apkAssetArray;
        try {
            apkAssetArray = assetManager.list("");
        } catch (IOException ioe) {
            Log.wtf(TAG, "Can't list assets", ioe);
            return Collections.emptySet();
        }
        if (apkAssetArray == null || apkAssetArray.length == 0) {
            Log.wtf(TAG, "Unexpected list of assets");
            return Collections.emptySet();
        }
        currentAssets = new HashSet<>(Arrays.asList(apkAssetArray));
        return currentAssets;
    }

    private interface StreamCreator {
        InputStream createStream() throws IOException;
        void cleanup() throws IOException;
    }

    private static long getAssetUpdateTime(String assetName, @NonNull File assetDir) {
        final String assetUpdatePrefix = assetName + "_";
        String[] assetFiles = assetDir.list(new FilenameFilter() {
            @Override
            public boolean accept(File file, String s) {
                return s != null && s.startsWith(assetUpdatePrefix);
            }
        });
        if (assetFiles == null || assetFiles.length != 1 || assetFiles[0] == null) {
            return -1;
        }
        final long assetUpdateTimestamp;
        try {
            assetUpdateTimestamp = Long.parseLong(assetFiles[0].substring(assetUpdatePrefix.length()));
        } catch (NumberFormatException nfe) {
            Log.wtf(TAG, "Can't convert a number", nfe);
            return -1;
        }
        return assetUpdateTimestamp;
    }

    public static long getApkInstallTime(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(ASSET_PREFS_NAME, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        String installTimeKey = "installTime_" + BuildConfig.VERSION_CODE;
        long assetTimestamp = preferences.getLong(installTimeKey, now);
        if (assetTimestamp == now) {
            if (now < TIME_TOO_MUCH_IN_PAST || now > TIME_TOO_MUCH_IN_FUTURE) {
                return -1;
            }
            preferences.edit().putLong(installTimeKey, assetTimestamp).apply();
        }
        return assetTimestamp;
    }

    public @NonNull static List<String> loadFromAssetOrUpdate(final Context context, final String assetName, final long syncIndex) {
        List<String> fromAsset = loadFromAsset(assetName, context.getAssets());
        if (syncIndex < 0) {
            if (BuildConfig.DEBUG) { LOG_V("SyncIndex < 0"); }
            return fromAsset;
        }
        File assetDir = new File (context.getFilesDir(), ASSETS + syncIndex);
        if (!assetDir.exists()) {
            if (BuildConfig.DEBUG) { LOG_V("Asset dir doesn't exist: " + assetDir); }
            return fromAsset;
        }
        long assetTimestamp = getApkInstallTime(context);
        if (assetTimestamp == -1) {
            if (BuildConfig.DEBUG) { LOG_V("Can't get APK install time"); }
            return fromAsset;
        }
        long assetUpdateTimestamp = getAssetUpdateTime(assetName, assetDir);
        if (assetUpdateTimestamp == -1) {
            if (BuildConfig.DEBUG) { LOG_V("No update for asset " + assetName); }
            return fromAsset;
        }
        if (assetUpdateTimestamp <= assetTimestamp) {
            if (BuildConfig.DEBUG) { LOG_V("Asset " + assetName + " is already up to date"); }
            return fromAsset;
        }

        List<String> fromUpdate = loadFromFile(new File(assetDir, assetName + "_" + assetUpdateTimestamp));
        if (fromUpdate == null || fromUpdate.size() < fromAsset.size()) {
            if (BuildConfig.DEBUG) { LOG_V("Can't load updates from update file"); }
            return fromAsset;
        }

        if (fromAsset.isEmpty()) {
            if (BuildConfig.DEBUG) { LOG_V("Assets from APK are empty"); }
            return fromUpdate;
        }

        for (int i = 0; i < fromUpdate.size(); i++) {
            String updatedLine = fromUpdate.get(i);
            if (i >= fromAsset.size()) {
                fromAsset.add(updatedLine);
            } else if (!updatedLine.isEmpty()) {
                fromAsset.set(i, updatedLine);
            }
        }
        return fromAsset;
    }

    public @NonNull static List<String> loadFromAsset(final String name, final AssetManager assetManager) {
        if (!getCurrentAssets(assetManager).contains(name)) {
            if (BuildConfig.DEBUG) { LOG_V("Asset " + name + " not present in APK"); }
            return Collections.emptyList();
        }

        List<String> list = load(new StreamCreator() {
            @Override
            public InputStream createStream() throws IOException {
                return assetManager.open(name);
            }

            @Override
            public void cleanup() {
            }
        });
        if (list == null) {
            return Collections.emptyList();
        }
        return list;
    }

    public @Nullable static List<String> loadFromUrl(final String url) {
        return load(new StreamCreator() {
            private HttpURLConnection connection;

            @Override
            public InputStream createStream() throws IOException {
                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.connect();
                return connection.getInputStream();
            }

            @Override
            public void cleanup() {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    public @Nullable static List<String> loadFromFile(final File file) {
        if (!file.exists()) {
            return null;
        }
        return load(new StreamCreator() {
            @Override
            public InputStream createStream() throws IOException {
                return new FileInputStream(file);
            }

            @Override
            public void cleanup() {
            }
        });
    }

    private @Nullable static List<String> load(StreamCreator streamCreator) {
        InputStream inputStream = null;
        GZIPInputStream gzipInputStream = null;
        Scanner scanner = null;
        try {
            ArrayList<String> list = new ArrayList<>(CAPACITY);
            inputStream = streamCreator.createStream();
            gzipInputStream = new GZIPInputStream(inputStream);
            scanner = new Scanner(gzipInputStream);

            while (scanner.hasNextLine()) {
                list.add(scanner.nextLine());
            }

            scanner.close();
            scanner = null;
            gzipInputStream.close();
            inputStream.close();

            return list;
        } catch (IOException e) {
            Log.wtf(TAG, "Can't read asset", e);
        } finally {
            try {
                if (scanner != null) scanner.close();
                IOUtils.closeQuietly(gzipInputStream);
                IOUtils.closeQuietly(inputStream);
                streamCreator.cleanup();
            } catch (IOException e) {
                Log.wtf(TAG, "Can't close", e);
            }
        }
        return null;
    }
}
