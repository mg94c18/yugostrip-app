package org.mg94c18.alanford;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.view.WindowManager;
import android.widget.Toast;

import static org.mg94c18.alanford.Logger.LOG_V;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.List;

public class EpisodeDownloadTask extends AsyncTask<Void, Integer, Boolean> implements DialogInterface.OnCancelListener {
    private WeakReference<Activity> activityRef;
    private ProgressDialog progressDialog;
    private List<String> links;
    private String title;
    private String episodeId;
    private File cacheDir;
    private Destination destination;
    private long fileLimit;

    enum Destination {
        INTERNAL_MEMORY,
        SD_CARD
    }

    EpisodeDownloadTask(long fileLimit, Activity activity, String episodeId, List<String> links, String title, File cacheDir, Destination destination) {
        this.activityRef = new WeakReference<>(activity);
        this.links = links;
        this.title = title;
        this.episodeId = episodeId;
        this.cacheDir = cacheDir;
        this.destination = destination;
        this.fileLimit = fileLimit;
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

        String message = "Snimam '" + title + "' na " + getDestinationName(destination);
        if (destination == Destination.SD_CARD) {
            int index = cacheDir.getAbsolutePath().indexOf("Android/data");
            if (index != -1) {
                message = message + " (" + cacheDir.getAbsolutePath().substring(index) + ")";
            }
        }
        message = message + ".";
        progressDialog.setMessage(message);
        progressDialog.setCancelable(true);
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.setMax(links.size());
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setProgress(0);
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
    protected void onProgressUpdate(Integer... progress) {
        if (progressDialog != null) {
            progressDialog.incrementProgressBy(1);
        }
    }

    @Override
    protected Boolean doInBackground(Void... voids) {
        if (BuildConfig.DEBUG) { LOG_V("doInBackground"); }
        if (fileLimit > -1) {
            MainActivity.deleteOldSavedFiles(cacheDir, fileLimit);
        }
        for (int i = 0; i < links.size() && !isCancelled(); i++) {
            if (BuildConfig.DEBUG) { LOG_V("publishProgress(" + i + ")"); }
            publishProgress(i);

            final Activity activity = activityRef.get();
            if (activity == null) {
                return null;
            }

            String filename = DownloadAndSave.fileNameFromLink(links.get(i), episodeId, i);
            if (cacheDir == null) {
                return null;
            }
            File file = new File(cacheDir, filename);
            if (file.exists()) {
                if (BuildConfig.DEBUG) { LOG_V(filename + " already exists"); }
                continue;
            }
            if (BuildConfig.DEBUG) { LOG_V("Downloading " + filename); }
            Bitmap bitmap = DownloadAndSave.downloadAndSave(links.get(i), file, 0, 0);
            if (bitmap == null) {
                return Boolean.FALSE;
            }
        }
        return Boolean.TRUE;
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
}
