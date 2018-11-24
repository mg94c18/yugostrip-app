package org.mg94c18.alanford;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.view.WindowManager;

import static org.mg94c18.alanford.MainActivity.LOG_V;

import java.io.File;
import java.util.List;

public class EpisodeDownloadTask extends AsyncTask<Void, Integer, Boolean> implements DialogInterface.OnCancelListener {
    private Activity activity;
    private ProgressDialog progressDialog;
    private List<String> links;
    private String title;
    private String episodeId;

    public EpisodeDownloadTask(Activity activity, String episodeId, List<String> links, String title) {
        this.activity = activity;
        this.links = links;
        this.title = title;
        this.episodeId = episodeId;
    }

    private void keepScreenOn(boolean on) {
        if (on) {
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    @Override
    public void onPreExecute() {
        LOG_V("onPreExecute");
        progressDialog = new ProgressDialog(activity);
        progressDialog.setTitle(title);
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
        LOG_V("onCancel");
        this.cancel(true);
        closeProgressDialog();
    }

    @Override
    public void onCancelled(Boolean result) {
        LOG_V("onCancelled");
        closeProgressDialog();
    }

    @Override
    protected void onProgressUpdate(Integer... progress) {
        progressDialog.incrementProgressBy(1);
    }

    @Override
    protected Boolean doInBackground(Void... voids) {
        LOG_V("doInBackground");
        for (int i = 0; i < links.size() && !isCancelled(); i++) {
            LOG_V("publishProgress(" + i + ")");
            publishProgress(i);

            String filename = episodeId + "_" + i;
            File file = new File(activity.getCacheDir(), filename);
            if (file.exists()) {
                LOG_V(filename + " already exists");
                continue;
            }
            if (new File(activity.getCacheDir(), filename + DownloadAndSave.TMP_SUFFIX).exists()) {
                LOG_V(filename + " is already being downloaded");
                continue;
            }
            LOG_V("Downloading " + filename);
            DownloadAndSave.downloadAndSave(links.get(i), file, 0, 0);
        }
        return Boolean.TRUE;
    }

    @Override
    protected void onPostExecute(Boolean result) {
        LOG_V("onPostExecute");
        keepScreenOn(false);
        closeProgressDialog();
    }

    private void closeProgressDialog() {
        LOG_V("closeProgressDialog");
        if (progressDialog != null) {
            progressDialog.cancel();
            progressDialog.dismiss();
            progressDialog = null;
        }
    }
}
