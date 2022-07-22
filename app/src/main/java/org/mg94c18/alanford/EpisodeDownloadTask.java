package org.mg94c18.alanford;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Toast;

import static org.mg94c18.alanford.Logger.LOG_V;
import static org.mg94c18.alanford.Logger.TAG;

import java.io.File;
import java.io.FilenameFilter;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;

public class EpisodeDownloadTask {
    private WeakReference<MainActivity> activityRef;
    private List<Integer> episodeIds;
    private File cacheDir;
    private List<String> episodesToDelete;
    private int allEpisodesPageCount;
    private int downloadedPageCount;
    private static final String DOWNLOADED_MARK_SUFFIX = ".success.txt";
    private static final String DOWNLOADED_MARK_FOLDER = "completed";
    private boolean canceled;
    private static final int MAX_DOWNLOAD_THREADS = 5;
    private ExecutorService executorService = new ScheduledThreadPoolExecutor(MAX_DOWNLOAD_THREADS);
    private Set<Future<Boolean>> activeFutures = new HashSet<>();
    private Map<String, List<String>> linksToDownload = new HashMap<>();
    public static final String EPISODES_FOLDER = DOWNLOADED_MARK_FOLDER;
    private boolean completed;
    private File otherCacheDir;

    public boolean completed() {
        return completed;
    }

    enum Destination {
        INTERNAL_MEMORY,
        SD_CARD
    }

    EpisodeDownloadTask(List<String> episodesToDelete, MainActivity activity, List<Integer> episodeIds, File cacheDir) {
        this.activityRef = new WeakReference<>(activity);
        this.episodeIds = episodeIds;
        this.cacheDir = cacheDir;
        this.episodesToDelete = episodesToDelete;
        this.canceled = false;
        this.allEpisodesPageCount = 0;
        this.downloadedPageCount = 0;
        this.completed = false;
    }

    private static void keepScreenOn(@NonNull Activity activity, boolean on) {
        if (on) {
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    public void execute() {
        final Activity activity = activityRef.get();
        if (activity == null) {
            return;
        }
        if (BuildConfig.DEBUG) { LOG_V("onPreExecute"); }
        keepScreenOn(activity,true);

        Toast.makeText(activity, "Download u pozadini...", Toast.LENGTH_LONG).show();

        activeFutures.add(executorService.submit(new Callable<Boolean>() {
            @Override
            public Boolean call() {
                return handleAsyncResult(kickOffDownloadTasks());
            }
        }));
    }

    public void cancel() {
        if (canceled) {
            Log.wtf(TAG, "Already canceled");
            return;
        }
        canceled = true;
        onPostExecute(null);
    }

    private void completeInitializing(Map<String, List<String>> linksToDownload) {
        MainActivity mainActivity = activityRef.get();
        if (mainActivity == null) {
            return;
        }

        for (Map.Entry<String, List<String>> entry : linksToDownload.entrySet()) {
            this.linksToDownload.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
    }

    protected synchronized void onPageSuccess(final String episodeId, final String link) {
        final MainActivity mainActivity = activityRef.get();
        if (mainActivity == null) {
            return;
        }

        mainActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (canceled) {
                    return;
                }
                downloadedPageCount++;
                List<String> links = linksToDownload.get(episodeId);
                if (links == null) {
                    Log.wtf(TAG, "Can't find links for episode " + episodeId);
                } else {
                    boolean wasPresent = links.remove(link);
                    if (!wasPresent) {
                        Log.wtf(TAG, "Link " + link + " was not present for episode " + episodeId);
                    }
                    if (links.size() == 0) {
                        linksToDownload.remove(episodeId);
                    }
                }
                if (downloadedPageCount == allEpisodesPageCount) {
                    onPostExecute(Boolean.TRUE);
                }
                mainActivity.onDownloadProgress(downloadedPageCount, allEpisodesPageCount);
            }
        });
    }

    private static File findOtherCacheDir(Context context, File cacheDir) {
        List<File> cacheDirs = new ArrayList<>();
        cacheDirs.add(ExternalStorageHelper.getExternalCacheDir(context));
        cacheDirs.add(ExternalStorageHelper.getInternalOfflineDir(context));

        if (cacheDirs.remove(cacheDir)) {
            return cacheDirs.get(0);
        }
        return null;
    }

    protected Boolean kickOffDownloadTasks() {
        if (cacheDir == null) {
            return Boolean.FALSE;
        }
        deleteOldEpisodes(cacheDir, episodesToDelete);
        MainActivity activity = activityRef.get();
        if (activity == null) {
            return null;
        }
        otherCacheDir = findOtherCacheDir(activity, cacheDir);
        final Map<String, List<String>> linksToDownload = new HashMap<>();
        for (Integer episodeIndex : episodeIds) {
            String episodeId = activity.numbers.get(episodeIndex);
            List<String> links = AssetLoader.loadFromAssetOrUpdate(activity, episodeId, MainActivity.syncIndex);
            allEpisodesPageCount += links.size();
            linksToDownload.put(episodeId, links);
        }
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                completeInitializing(linksToDownload);
                for (final Map.Entry<String, List<String>> entry : linksToDownload.entrySet()) {
                    activeFutures.add(executorService.submit(new Callable<Boolean>() {
                        @Override
                        public Boolean call() {
                            return handleAsyncResult(doInBackground(entry.getKey(), entry.getValue()));
                        }
                    }));
                }
            }
        });
        return Boolean.TRUE;
    }

    private Boolean handleAsyncResult(Boolean success) {
        if (success == null || !success) {
            MainActivity mainActivity = activityRef.get();
            if (mainActivity != null) {
                mainActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!canceled) {
                            onPostExecute(Boolean.FALSE);
                        }
                    }
                });
            }
        }
        return success;
    }

    public static @Nullable File getOrCreateEpisodeDir(File cacheDir, String episodeId) {
        if (cacheDir == null) {
            return null;
        }
        File episodesDir = new File(cacheDir, DOWNLOADED_MARK_FOLDER);
        File episodeDir = new File(episodesDir, episodeId);
        File[] dirsToCreate = { episodesDir, episodeDir };
        for (File dirToCreate : dirsToCreate) {
            if (!dirToCreate.exists()) {
                if (!dirToCreate.mkdir()) {
                    Log.wtf(TAG, "Can't create dir: " + dirToCreate.getAbsolutePath());
                    return null;
                }
            }
        }
        return episodeDir;
    }

    private Boolean doInBackground(String episodeId, List<String> links) {
        if (BuildConfig.DEBUG) { LOG_V("doInBackground"); }
        String filename;
        File file;
        Bitmap bitmap;
        File otherFile;
        boolean usedOtherFile;

        File episodeDir = getOrCreateEpisodeDir(cacheDir, episodeId);
        if (episodeDir == null) {
            return null;
        }

        File otherEpisodeDir = getOrCreateEpisodeDir(otherCacheDir, episodeId);

        String link;
        for (int i = 0; i < links.size() && !isCancelled(); i++) {
            link = links.get(i);
            filename = DownloadAndSave.fileNameFromLink(link, episodeId, i);
            file = new File(episodeDir, filename);
            if (file.exists()) {
                if (BuildConfig.DEBUG) { LOG_V(filename + " already exists"); }
                onPageSuccess(episodeId, link);
                continue;
            }
            usedOtherFile = false;
            if (otherCacheDir != null) {
                otherFile = new File(otherEpisodeDir, filename);
                if (otherFile.exists() && IOUtils.copy(otherFile, file)) {
                    if (BuildConfig.DEBUG) { LOG_V("Copied instead of download: " + filename); }
                    usedOtherFile = true;
                }
            }

            if (!usedOtherFile) {
                if (BuildConfig.DEBUG) { LOG_V("Downloading " + filename); }
                bitmap = DownloadAndSave.downloadAndSave(link, file, 0, 0, 5);
                if (bitmap == null) {
                    return Boolean.FALSE;
                }
            }
            onPageSuccess(episodeId, link);
        }
        if (!isCancelled()) {
            markSuccessForEpisode(episodeId, cacheDir);
        }
        return Boolean.TRUE;
    }

    private boolean isCancelled() {
        return canceled;
    }

    public static void markSuccessForEpisode(String episodeId, File destinationDir) {
        if (BuildConfig.DEBUG) { LOG_V("Marking success for " + episodeId); }
        boolean markedSuccess = DownloadAndSave.createEmptyFile(destinationDir, DOWNLOADED_MARK_FOLDER, episodeId + DOWNLOADED_MARK_SUFFIX);
        if (!markedSuccess) {
            Log.wtf(TAG, "Can't mark we downloaded the episode " + episodeId);
        }
    }

    protected void onPostExecute(Boolean success) {
        if (BuildConfig.DEBUG) { LOG_V("onPostExecute"); }
        MainActivity activity = activityRef.get();
        if (activity == null) {
            return;
        }
        keepScreenOn(activity,false);
        if (success != null) {
            final String toastText;
            if (success) {
                toastText = "Download (" + episodeIds.size() + ") uspeo :)";
            } else {
                toastText = "Nažalost, nije uspelo :(";
            }
            completed = true;
            Toast.makeText(activity, toastText, Toast.LENGTH_LONG).show();
        }
        if (executorService != null) {
            executorService.shutdown();
            for (Future<Boolean> future : activeFutures) {
                if (!future.isDone()) {
                    future.cancel(true);
                }
            }
            executorService = null;
        }

        if (BuildConfig.DEBUG) {
            if (success != null && success) {
                try {
                    for (Future<Boolean> future : activeFutures) {
                        if (!future.isCancelled() && !future.get()) {
                            Log.wtf(TAG, "A task didn't complete successfully: " + future, new Throwable());
                        }
                    }
                } catch (InterruptedException|ExecutionException e) {
                    Log.wtf(TAG, e);
                }
            }
        }
        activeFutures = null;
        activity.onDownloadProgress(-1, -1);
    }

    private static void deleteFolderIfExists(File dir) {
        if (!dir.exists()) {
            return;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            MainActivity.deleteFile(file);
        }
        MainActivity.deleteFile(dir);
    }

    public static synchronized void deleteOldEpisodes(@NonNull File dir, final Collection<String> episodeIds) {
        if (BuildConfig.DEBUG) { LOG_V("Deleting episodes " + episodeIds); }
        if (episodeIds.isEmpty()) {
            return;
        }

        for (String episodeId : episodeIds) {
            MainActivity.deleteFile(new File(new File(dir, DOWNLOADED_MARK_FOLDER), episodeId + DOWNLOADED_MARK_SUFFIX));
            deleteFolderIfExists(new File(new File(dir, EPISODES_FOLDER), episodeId));
        }

        File[] files = dir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File file, String s) {
                for (String episodeId : episodeIds) {
                    if (s.startsWith(episodeId + "_")) {
                        return true;
                    }
                }
                return false;
            }
        });
        if (files != null) {
            for (File file : files) {
                MainActivity.deleteFile(file);
            }
        }

        if (BuildConfig.DEBUG) { LOG_V("Finished deleting " + episodeIds); }
    }

    @NonNull public static LinkedHashMap<String, Long> getCompletelyDownloadedEpisodes(@NonNull File destinationDir) {
        File completedDir = new File(destinationDir, DOWNLOADED_MARK_FOLDER);
        File[] files = completedDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File file, String s) {
                return s != null && s.endsWith(DOWNLOADED_MARK_SUFFIX);
            }
        });
        if (files == null) {
            return new LinkedHashMap<>();
        }
        Arrays.sort(files, new Comparator<File>() {
            @Override
            public int compare(File f1, File f2) {
                long lm1 = f1.lastModified();
                long lm2 = f2.lastModified();
                if (lm1 < lm2) {
                    return -1;
                } else if (lm1 == lm2) {
                    return 0;
                } else {
                    return 1;
                }
            }
        });
        LinkedHashMap<String, Long> completelyDownloadedEpisodes = new LinkedHashMap<>();
        for (File file : files) {
            int index = file.getName().indexOf(DOWNLOADED_MARK_SUFFIX);
            if (index == -1) {
                Log.wtf(TAG, "Can't find the filtered suffix in " + file.getName());
                continue;
            }

            completelyDownloadedEpisodes.put(file.getName().substring(0, index), file.lastModified());
        }
        return completelyDownloadedEpisodes;
    }

    public static boolean migrateDownloadedEpisode(File destinationDir, String episodeId, String newEpisodeId) {
        File completedDir = new File(destinationDir, DOWNLOADED_MARK_FOLDER);
        File oldEpisodeMark = new File(completedDir, episodeId + DOWNLOADED_MARK_SUFFIX);
        File newEpisodeMark = new File(completedDir, newEpisodeId + DOWNLOADED_MARK_SUFFIX);
        File oldEpisodeDir = new File(destinationDir, episodeId);
        File newEpisodeDir = new File(destinationDir, newEpisodeId);
        if (!oldEpisodeMark.exists() || !oldEpisodeDir.exists()) {
            Log.wtf(TAG, "Can't migrate episode " + episodeId + " because the file/dir is not there");
            return false;
        }
        // TODO: onCreateView(126_proba_088.jpg) ne radi; da li preimenujem i fajlove?
        return oldEpisodeMark.renameTo(newEpisodeMark) && oldEpisodeDir.renameTo(newEpisodeDir);
    }
}
