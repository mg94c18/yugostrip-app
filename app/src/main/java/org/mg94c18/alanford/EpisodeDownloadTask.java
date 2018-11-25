package org.mg94c18.alanford;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.view.WindowManager;
import android.widget.Toast;

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

            String filename = DownloadAndSave.fileNameFromLink(links.get(i), episodeId, i);
            // TODO: use activity.getExternalCacheDir()
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
            Bitmap bitmap = DownloadAndSave.downloadAndSave(links.get(i), file, 0, 0);
            if (bitmap == null) {
                return Boolean.FALSE;
            }
        }
        return Boolean.TRUE;
    }

    @Override
    protected void onPostExecute(Boolean success) {
        LOG_V("onPostExecute");
        keepScreenOn(false);
        closeProgressDialog();
        Toast.makeText(activity, success ? "Uspelo je :)" : "Nažalost, nije uspelo :(", Toast.LENGTH_SHORT).show();
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
