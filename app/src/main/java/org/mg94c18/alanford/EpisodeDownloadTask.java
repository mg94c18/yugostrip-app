package org.mg94c18.alanford;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Toast;

import static org.mg94c18.alanford.Logger.LOG_V;
import static org.mg94c18.alanford.Logger.TAG;

import java.io.File;
import java.io.FilenameFilter;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

public class EpisodeDownloadTask extends AsyncTask<Void, Integer, Boolean> implements DialogInterface.OnCancelListener {
    private WeakReference<MainActivity> activityRef;
    private ProgressDialog progressDialog;
    private List<Integer> episodeIds;
    private File cacheDir;
    private Destination destination;
    private List<String> episodesToDelete;
    private int currentEpisode;
    private int currentEpisodePageCount;
    private static final String DOWNLOADED_MARK_SUFFIX = ".success.txt";
    private static final String DOWNLOADED_MARK_FOLDER = "completed";

    enum Destination {
        INTERNAL_MEMORY,
        SD_CARD
    }

    EpisodeDownloadTask(List<String> episodesToDelete, MainActivity activity, List<Integer> episodeIds, File cacheDir, Destination destination) {
        this.activityRef = new WeakReference<>(activity);
        this.episodeIds = episodeIds;
        this.cacheDir = cacheDir;
        this.destination = destination;
        this.episodesToDelete = episodesToDelete;
        this.currentEpisode = -1;
    }

    private void keepScreenOn(boolean on) {
        final Activity activity = activityRef.get();
        if (activity == null) {
            return;
        }
        if (on) {
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private static String getDestinationName(Destination destination) {
        if (destination == Destination.INTERNAL_MEMORY) {
            return "internu memoriju";
        } else {
            return "SD card";
        }
    }

    @Override
    public void onPreExecute() {
        final Activity activity = activityRef.get();
        if (activity == null) {
            return;
        }
        if (BuildConfig.DEBUG) { LOG_V("onPreExecute"); }
        progressDialog = new ProgressDialog(activity);
        progressDialog.setTitle("Malo strpljenja...");
        progressDialog.setMessage("Snimam na " + getDestinationName(destination));
        progressDialog.setCancelable(true);
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setOnCancelListener(this);
        progressDialog.show();
        keepScreenOn(true);
    }

    @Override
    public void onCancel(DialogInterface dialogInterface) {
        if (BuildConfig.DEBUG) { LOG_V("onCancel"); }
        this.cancel(true);
        closeProgressDialog();
    }

    @Override
    public void onCancelled(Boolean result) {
        if (BuildConfig.DEBUG) { LOG_V("onCancelled"); }
        closeProgressDialog();
    }

    @Override
    protected void onProgressUpdate(Integer... progressArray) {
        MainActivity mainActivity = activityRef.get();
        if (progressDialog == null || progressArray == null || mainActivity == null) {
            return;
        }

        int progress = progressArray[0];
        final String title;
        if (progress == 0) {
            currentEpisode++;
            progressDialog.setTitle("Malo strpljenja... (" + (currentEpisode + 1) + "/" + episodeIds.size() + ")");
            title = mainActivity.titles.get(episodeIds.get(currentEpisode));

            String message = "Snimam '" + title + "' na " + getDestinationName(destination);
            if (destination == Destination.SD_CARD) {
                int index = cacheDir.getAbsolutePath().indexOf("Android/data");
                if (index != -1) {
                    message = message + " (" + cacheDir.getAbsolutePath().substring(index) + ")";
                }
            }
            message = message + ".";
            progressDialog.setMessage(message);
            progressDialog.setMax(currentEpisodePageCount);
            progressDialog.setProgress(0);
        }

        progressDialog.incrementProgressBy(1);
    }

    @Override
    protected Boolean doInBackground(Void... voids) {
        if (cacheDir == null) {
            return Boolean.FALSE;
        }
        for (String episodeToDelete : episodesToDelete) {
            deleteOldEpisode(cacheDir, episodeToDelete);
        }
        for (Integer episodeIndex : episodeIds) {
            MainActivity activity = activityRef.get();
            if (activity == null) {
                return null;
            }
            String episodeId = activity.numbers.get(episodeIndex);
            List<String> links = AssetLoader.loadFromAssetOrUpdate(activity, episodeId, MainActivity.syncIndex);
            currentEpisodePageCount = links.size();
            Boolean result = doInBackground(episodeId, links);
            if (result == null || !result) {
                return result;
            }
        }
        return Boolean.TRUE;
    }

    private Boolean doInBackground(String episodeId, List<String> links) {
        if (BuildConfig.DEBUG) { LOG_V("doInBackground"); }
        MainActivity activity;
        String filename;
        File file;
        Bitmap bitmap;
        for (int i = 0; i < links.size() && !isCancelled(); i++) {
            if (BuildConfig.DEBUG) { LOG_V("publishProgress(" + i + ")"); }
            publishProgress(i);

            activity = activityRef.get();
            if (activity == null) {
                return null;
            }

            filename = DownloadAndSave.fileNameFromLink(links.get(i), episodeId, i);
            file = new File(cacheDir, filename);
            if (file.exists()) {
                if (BuildConfig.DEBUG) { LOG_V(filename + " already exists"); }
                continue;
            }
            if (BuildConfig.DEBUG) { LOG_V("Downloading " + filename); }
            bitmap = DownloadAndSave.downloadAndSave(links.get(i), file, 0, 0);
            if (bitmap == null) {
                return Boolean.FALSE;
            }
        }
        if (!isCancelled()) {
            markSuccessForEpisode(episodeId, cacheDir);
        }
        return Boolean.TRUE;
    }

    public static void markSuccessForEpisode(String episodeId, File destinationDir) {
        if (BuildConfig.DEBUG) { LOG_V("Marking success for " + episodeId); }
        boolean markedSuccess = DownloadAndSave.createEmptyFile(destinationDir, DOWNLOADED_MARK_FOLDER, episodeId + DOWNLOADED_MARK_SUFFIX);
        if (!markedSuccess) {
            Log.wtf(TAG, "Can't mark we downloaded the episode " + episodeId);
        }
    }

    @Override
    protected void onPostExecute(Boolean success) {
        if (BuildConfig.DEBUG) { LOG_V("onPostExecute"); }
        keepScreenOn(false);
        closeProgressDialog();
        final Activity activity = activityRef.get();
        if (activity == null) {
            return;
        }
        Toast.makeText(activity, (success != null && success) ? "Uspelo je :)  Uživajte u čitanju!" : "Nažalost, nije uspelo :(", Toast.LENGTH_LONG).show();
    }

    private void closeProgressDialog() {
        if (BuildConfig.DEBUG) { LOG_V("closeProgressDialog"); }
        if (progressDialog != null) {
            progressDialog.cancel();
            progressDialog.dismiss();
            progressDialog = null;
        }
    }

    public static synchronized void deleteOldEpisode(@NonNull File dir, final String episodeId) {
        if (BuildConfig.DEBUG) { LOG_V("Deleting episode " + episodeId); }
        MainActivity.deleteFile(new File(new File(dir, DOWNLOADED_MARK_FOLDER), episodeId + DOWNLOADED_MARK_SUFFIX));
        File[] files = dir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File file, String s) {
                return s.startsWith(episodeId + "_");
            }
        });
        if (files == null) {
            Log.wtf(TAG, "Nothing to delete for " + episodeId);
            return;
        }
        for (File file : files) {
            MainActivity.deleteFile(file);
        }
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
}
