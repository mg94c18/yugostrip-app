package org.mg94c18.alanford.sync;

import android.accounts.Account;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SyncResult;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.util.Pair;
import android.webkit.URLUtil;

import org.mg94c18.alanford.AssetLoader;
import org.mg94c18.alanford.BuildConfig;
import org.mg94c18.alanford.DownloadAndSave;
import org.mg94c18.alanford.EpisodeDownloadTask;
import org.mg94c18.alanford.ExternalStorageHelper;
import org.mg94c18.alanford.MainActivity;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.mg94c18.alanford.Logger.LOG_V;
import static org.mg94c18.alanford.Logger.TAG;

/**
 * Handle the transfer of data between a server and an
 * app, using the Android sync adapter framework.
 */
public class SyncAdapter extends AbstractThreadedSyncAdapter {
    // Shared prefs to use for sync stuff
    static final String SHARED_PREFS_NAME = "sync";
    private static final String LAST_SYNC_TIME = "lastSyncTime";
    private static final String LAST_SYNC_INDEX = "lastSyncIndex";
    private static final String LAST_SYNC_APK_VERSION_CODE = "lastSyncVersionCode";
    private static final String SYNC_SLOT = "syncSlot";
    // The authority for the sync adapter's content provider
    private static final String AUTHORITY = BuildConfig.APPLICATION_ID + ".sync.StubProvider";

    private static final long SECONDS_PER_DAY = 86400;
    private static final String UPDATES = "updates";
    private static long lastAcquiredIndex = -1;

    public static void setPeriodicSync(Context context) {
        Account account = DummyAccount.getSyncAccount(context);
        if (account == null) {
            Log.wtf(TAG, "Null sync account");
            return;
        }
        if (BuildConfig.DEBUG) { LOG_V("Sync account=" + account.name); }
        ContentResolver.setSyncAutomatically(account, AUTHORITY, true);
        ContentResolver.addPeriodicSync(account, AUTHORITY, Bundle.EMPTY, SECONDS_PER_DAY);
        if (BuildConfig.DEBUG) { LOG_V("Configured periodic sync"); }
    }

    public static void requestSyncNow(Context context) {
        Account account = DummyAccount.getSyncAccount(context);
        if (account == null) {
            Log.wtf(TAG, "Null sync account");
            return;
        }

        Bundle bundle = new Bundle();
        bundle.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
        bundle.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);

        if (BuildConfig.DEBUG) { LOG_V("Sync account=" + account.name); }
        ContentResolver.requestSync(account, AUTHORITY, bundle);
        if (BuildConfig.DEBUG) { LOG_V("Requested manual expedited sync."); }
    }

    /**
     * Set up the sync adapter
     */
    public SyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
    }

    /**
     * Set up the sync adapter. This form of the
     * constructor maintains compatibility with Android 3.0
     * and later platform versions
     */
    public SyncAdapter(
            Context context,
            boolean autoInitialize,
            boolean allowParallelSyncs) {
        super(context, autoInitialize, allowParallelSyncs);
    }

    /*
     * Specify the code you want to run in the sync adapter. The entire
     * sync adapter runs in a background thread, so you don't have to set
     * up your own background processing.
     */
    @Override
    public void onPerformSync(
            Account account,
            Bundle extras,
            String authority,
            ContentProviderClient provider,
            SyncResult syncResult) {
        if (BuildConfig.DEBUG) { LOG_V("onPerformSync"); }

        if (!AUTHORITY.equals(authority)) {
            Log.wtf(TAG, "Sync authority doesn't match: " + authority + " vs. " + AUTHORITY);
            return;
        }

        Context context = getContext();
        if (context == null) {
            return;
        }

        Account expectedAccount = DummyAccount.getSyncAccount(context);
        if (expectedAccount == null) {
            Log.wtf(TAG, "Sync account is null");
            return;
        }

        // Handle clear data by removing the old sync
        if (!expectedAccount.equals(account)) {
            if (BuildConfig.DEBUG) { LOG_V("Removing old sync for " + account.name); }
            ContentResolver.setSyncAutomatically(account, AUTHORITY, false);
            ContentResolver.removePeriodicSync(account, AUTHORITY, extras);
            return;
        }

        syncResult.clear();
        try {
            Log.i(TAG, "Syncing");
            boolean success = performSync(context);
            Log.i(TAG, "performSync() returned " + success);

            if (success) {
                SharedPreferences preferences = context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
                success = preferences.edit()
                        .putLong(LAST_SYNC_TIME, System.currentTimeMillis())
                        .putInt(LAST_SYNC_APK_VERSION_CODE, BuildConfig.VERSION_CODE)
                        .commit();

                cleanupDownloadedPages(context, preferences.getLong(LAST_SYNC_INDEX, -1));
                Log.i(TAG, "cleanupDownloadedPages() finished");
            }

            if (!success) {
                syncResult.stats.numIoExceptions++;
            }

        } catch (Throwable throwable) {
            Log.wtf(TAG, "Failed to sync", throwable);
            syncResult.stats.numIoExceptions++;
        } finally {
            syncResult.stats.numUpdates++;
        }
    }

    private boolean performSync(@NonNull Context context) {
        SharedPreferences preferences = context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
        final Pair<Long, Long> indices = acquireSyncIndices(context);

        File filesDir = context.getFilesDir();
        File[] oldAssetDirs = filesDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File file, String s) {
                return !s.equals(AssetLoader.ASSETS + indices.first) && !s.equals(AssetLoader.ASSETS + indices.second);
            }
        });
        deleteAllDirs(oldAssetDirs);

        long thisSyncIndex = indices.second + 1;
        if (thisSyncIndex == indices.first) {
            Log.wtf(TAG, "Unexpected indices: " + indices);
            return false;
        }
        File thisSyncDir = new File(filesDir, AssetLoader.ASSETS + thisSyncIndex);
        if (!thisSyncDir.mkdir()) {
            Log.wtf(TAG, "Can't create dir: " + thisSyncDir);
            return false;
        }

        List<String> syncUrls = AssetLoader.loadFromAsset(AssetLoader.SYNC_URLS, context.getAssets());
        if (syncUrls.isEmpty()) {
            Log.wtf(TAG, "Can't load sync URLs");
            return false;
        }
        final int mySyncSlot;
        if (preferences.contains(SYNC_SLOT)) {
            mySyncSlot = preferences.getInt(SYNC_SLOT, -1);
            if (mySyncSlot == -1) {
                Log.wtf(TAG, "Can't load slot although it exists");
                return false;
            }
            if (mySyncSlot < 0 || mySyncSlot >= syncUrls.size()) {
                Log.wtf(TAG, "Invalid slot: " + mySyncSlot);
                return false;
            }
        } else {
            mySyncSlot = new Random().nextInt(syncUrls.size());
            if (!preferences.edit().putInt(SYNC_SLOT, mySyncSlot).commit()) {
                Log.wtf(TAG, "Can't save sync slot");
                return false;
            }
        }
        File thisSyncUpdates = new File(thisSyncDir, UPDATES);
        boolean downloaded = DownloadAndSave.saveUrlToFile(syncUrls.get(mySyncSlot), thisSyncUpdates);
        if (!downloaded) {
            if (BuildConfig.DEBUG) { LOG_V("Can't save master link"); }
            return false;
        }

        List<String> updates = AssetLoader.loadFromFile(thisSyncUpdates);
        if (updates == null) {
            if (BuildConfig.DEBUG) { LOG_V("Can't read master link"); }
            return false;
        }

        File lastSyncDir = new File(filesDir, AssetLoader.ASSETS + indices.second);
        {
            List<String> lastSyncUpdates = AssetLoader.loadFromFile(new File(lastSyncDir, UPDATES));
            if (updates.equals(lastSyncUpdates)) {
                if (BuildConfig.DEBUG) { LOG_V("Further sync not needed as there are no new changes"); }
                return true;
            }
        }

        if (BuildConfig.DEBUG) { LOG_V("Syncing asset updates into new folder: " + thisSyncDir); }

        long apkAssetTime = AssetLoader.getApkAssetTime(context);
        if (apkAssetTime == -1) {
            Log.wtf(TAG, "Can't get APK asset time");
            return false;
        }

        Set<String> apkAssets = AssetLoader.getCurrentAssets(context.getAssets());
        if (apkAssets.isEmpty()) {
            // Error already logged
            return false;
        }

        String assetUpdateName;
        long assetUpdateTime;
        String assetUpdateLink;
        Pattern pattern = Pattern.compile("^([^ ]+) +([^ ]+) +([^ ]+)$");
        Matcher matcher;
        File destination;
        for (String update : updates) {
            matcher = pattern.matcher(update);
            if (!matcher.matches()) {
                Log.wtf(TAG, "Update string doesn't match: " + update);
                return false;
            }
            assetUpdateName = matcher.group(1);
            try {
                assetUpdateTime = Long.parseLong(matcher.group(2));
            } catch (NumberFormatException nfe) {
                Log.wtf(TAG, "Update line timestamp invalid: " + update);
                return false;
            }
            assetUpdateLink = matcher.group(3);

            if (apkAssets.contains(assetUpdateName) && apkAssetTime >= assetUpdateTime) {
                if (BuildConfig.DEBUG) { LOG_V("Skipping asset " + assetUpdateName + " as it is already up to date"); }
                continue;
            }

            // Either we don't have the asset, or there is an update
            destination = new File(thisSyncDir, assetUpdateName + "_" + assetUpdateTime);
            if (apkAssets.contains(assetUpdateName)) {
                List<String> currentContent = AssetLoader.loadFromAsset(assetUpdateName, context.getAssets());
                if (BuildConfig.DEBUG) { LOG_V("Saving diff for asset " + assetUpdateName); }
                downloaded = DownloadAndSave.saveUrlDiffToFile(assetUpdateLink, currentContent, destination);
            } else {
                if (BuildConfig.DEBUG) { LOG_V("Saving full asset " + assetUpdateName); }
                downloaded = DownloadAndSave.saveUrlToFile(assetUpdateLink, destination);
            }
            if (!downloaded) {
                Log.wtf(TAG, "Can't download update: " + assetUpdateLink + " for asset " + assetUpdateName);
                return false;
            }
        }

        if (updates.isEmpty()) {
            if (BuildConfig.DEBUG) { LOG_V("No updates available"); }
        } else {
            if (!consistentAssets(context, thisSyncIndex) || !consistentHiddenAssets(context, thisSyncIndex)) {
                if (BuildConfig.DEBUG) { LOG_V("Failed consistency check"); }
                return false;
            }
        }

        return preferences.edit().putLong(LAST_SYNC_INDEX, thisSyncIndex).commit();
    }

    private static boolean isImageFile(File file) {
        return isImageFile(file.getName());
    }

    private static boolean isImageFile(String fileName) {
        return fileName.endsWith(".jpg") || fileName.endsWith(".png");
    }

    public static void cleanupDownloadedPages(Context context, long syncIndex) {
        if (syncIndex < 0) {
            Log.wtf(TAG, "Invalid sync index");
            return;
        }
        File downloadDir = ExternalStorageHelper.getExternalCacheDir(context);
        if (downloadDir == null) {
            downloadDir = ExternalStorageHelper.getInternalOfflineDir(context);
        }
        final Map<String, Set<File>> pagesToMove = new HashMap<>();
        File[] filesInCache = downloadDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File file, String name) {
                int index = name.lastIndexOf('_');
                if (index == -1) {
                    return false;
                }
                String episode = name.substring(0, index);
                if (!isImageFile(name)) {
                    return false;
                }
                Set<File> episodePagesToMove = pagesToMove.get(episode);
                if (episodePagesToMove == null) {
                    episodePagesToMove = new HashSet<>();
                    pagesToMove.put(episode, episodePagesToMove);
                }
                episodePagesToMove.add(new File(file, name));
                return true;
            }
        });
        String episode;
        for (Map.Entry<String, Set<File>> entry : pagesToMove.entrySet()) {
            episode = entry.getKey();
            File episodeDir = EpisodeDownloadTask.getOrCreateEpisodeDir(downloadDir, episode);
            if (episodeDir == null) {
                return;
            }
            File destinationFile;
            for (File fileToMove : entry.getValue()) {
                destinationFile = new File(episodeDir, fileToMove.getName());
                if (destinationFile.exists() && isImageFile(destinationFile)) {
                    MainActivity.deleteFile(fileToMove);
                    continue;
                }
                if (!fileToMove.renameTo(destinationFile)) {
                    Log.wtf(TAG, "Can't rename " + fileToMove.getAbsolutePath() + " to " + destinationFile.getAbsolutePath());
                    return;
                }
            }
        }

        LinkedHashMap<String, Long> completelyDownloadedEpisodes = EpisodeDownloadTask.getCompletelyDownloadedEpisodes(downloadDir);
        List<String> episodesToDelete = new ArrayList<>();
        for (Map.Entry<String, Long> entry : completelyDownloadedEpisodes.entrySet()) {
            String episodeId = entry.getKey();
            if (entry.getValue() >= System.currentTimeMillis() - 1000 * SECONDS_PER_DAY) {
                if (BuildConfig.DEBUG) { LOG_V("Episode " + episodeId + " is fresh, skipping it"); }
                continue;
            }
            File[] downloadedPages = downloadedPagesForEpisode(downloadDir, episodeId);
            if (downloadedPages == null) {
                Log.wtf(TAG, "Episode " + episodeId + " failed to enumerate, skipping it for now");
                continue;
            }
            List<String> assetPages = AssetLoader.loadFromAssetOrUpdate(context, episodeId, syncIndex);

            if (downloadedPages.length < assetPages.size()) {
                if (BuildConfig.DEBUG) {
                    LOG_V("Episode " + episodeId + " downloaded long ago (" + entry.getValue() + ") and not complete, mark for deletion");
                }
                episodesToDelete.add(episodeId);
            }
        }
        EpisodeDownloadTask.deleteOldEpisodes(downloadDir, episodesToDelete);
    }

    @Nullable private static File[] downloadedPagesForEpisode(File downloadDir, String episodeId) {
        File episodeDir = EpisodeDownloadTask.getOrCreateEpisodeDir(downloadDir, episodeId);
        if (episodeDir == null) {
            return null;
        }
        final Pattern pattern = Pattern.compile("^(.+)_([0-9][0-9][0-9])(\\.(png|jpg))");
        File[] files = episodeDir.listFiles(new FilenameFilter() {
            Matcher matcher;

            @Override
            public boolean accept(File file, String name) {
                matcher = pattern.matcher(name);
                if (!matcher.matches()) {
                    Log.wtf(TAG, "Unexpected file: " + name + " at " + file.getAbsolutePath());
                    return false;
                }
                if (!isImageFile(name)) {
                    Log.wtf(TAG, "Unexpected file name: " +  name);
                    return false;
                }
                return true;
            }
        });
        return files;
    }

    private static boolean consistentHiddenAssets(Context context, long syncIndex) {
        List<String> hiddenTitles = AssetLoader.loadFromAssetOrUpdate(context, AssetLoader.HIDDEN_TITLES, syncIndex);
        List<String> hiddenNumbers = AssetLoader.loadFromAssetOrUpdate(context, AssetLoader.HIDDEN_NUMBERS, syncIndex);
        List<String> hiddenMatches = AssetLoader.loadFromAssetOrUpdate(context, AssetLoader.HIDDEN_MATCHES, syncIndex);

        int hiddenCount = hiddenTitles.size();
        if (hiddenNumbers.size() != hiddenCount || hiddenMatches.size() != hiddenCount) {
            Log.wtf(TAG, "Hidden sizes don't match: " + hiddenTitles.size() + "/" + hiddenNumbers.size() + "/" + hiddenMatches.size());
            return false;
        }

        for (String hiddenNumber : hiddenNumbers) {
            List<String> messages = AssetLoader.loadFromAssetOrUpdate(context, hiddenNumber, syncIndex);
            if (messages.isEmpty()) {
                if (BuildConfig.DEBUG) { LOG_V("Empty messages for hiddenNumber " + hiddenNumber); }
                return false;
            }
        }
        return true;
    }

    private static boolean consistentAssets(Context context, long syncIndex) {
        if (BuildConfig.DEBUG) { LOG_V("Performing consistency check"); }

        List<String> titles = AssetLoader.loadFromAssetOrUpdate(context, AssetLoader.TITLES, syncIndex);
        List<String> numbers = AssetLoader.loadFromAssetOrUpdate(context, AssetLoader.NUMBERS, syncIndex);
        List<String> dates = AssetLoader.loadFromAssetOrUpdate(context, AssetLoader.DATES, syncIndex);
        int episodeCount = titles.size();
        if (numbers.size() != episodeCount || dates.size() != episodeCount) {
            Log.wtf(TAG, "Sizes don't match: " + titles.size() + "/" + numbers.size() + "/" + dates.size());
            return false;
        }

        for (String number : numbers) {
            List<String> pages = AssetLoader.loadFromAssetOrUpdate(context, number, syncIndex);
            if (pages.isEmpty()) {
                if (BuildConfig.DEBUG) { LOG_V("Empty pages for number " + number); }
                return false;
            }
            for (String page : pages) {
                if (!URLUtil.isValidUrl(page)) {
                    if (BuildConfig.DEBUG) { LOG_V("Invalid page for " + number + ": " + page); }
                    return false;
                }
            }
        }
        return true;
    }

    private static void deleteAllDirs(@Nullable File[] dirs) {
        if (dirs == null) {
            return;
        }
        for (File dir : dirs) {
            deleteDir(dir);
        }
    }

    private static void deleteDir(@NonNull File dir) {
        File[] entries = dir.listFiles();
        if (entries != null) {
            for (File entry : entries) {
                if (entry.isDirectory()) {
                    deleteDir(entry);
                } else {
                    MainActivity.deleteFile(entry);
                }
            }
        }
        MainActivity.deleteFile(dir);
    }

    public static synchronized long acquireSyncIndex(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
        int lastSyncCode = preferences.getInt(LAST_SYNC_APK_VERSION_CODE, -1);
        if (lastSyncCode == BuildConfig.VERSION_CODE) {
            lastAcquiredIndex = preferences.getLong(LAST_SYNC_INDEX, -1);
        } else {
            lastAcquiredIndex = -1;
        }
        return lastAcquiredIndex;
    }

    // Returns lastAcquiredIndex and lastSyncIndex, which may be the same
    private static synchronized @NonNull Pair<Long, Long> acquireSyncIndices(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
        long syncIndex = preferences.getLong(LAST_SYNC_INDEX, -1);
        return Pair.create(lastAcquiredIndex, syncIndex);
    }
}
