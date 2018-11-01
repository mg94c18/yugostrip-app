package org.mg94c18.alanford;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.AppCompatImageView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements AdapterView.OnItemClickListener {
    public static final String TAG = "AlanFord";
    private static final long MAX_DOWNLOADED_IMAGES = 150;
    private static final String SHARED_PREFS_NAME = "config";
    private static final String EPISODE = "episode";
    private static final String DRAWER = "drawer";
    private static final String DRAWER_SELECTION = "drawer_selection";
    private static final String CONTACT_EMAIL = "mg94c18@tesla.rcub.bg.ac.rs";

    ViewPager viewPager;
    MyPagerAdapter pagerAdapter;
    DrawerLayout drawerLayout;
    List<String> episodes;
    ListView drawerList;
    List<String> titles;
    List<String> numbers;
    int selectedEpisode = 0;
    EpisodeDownloadTask downloadTask;

    public static void LOG_V(String s) {
        if (BuildConfig.DEBUG) {
            Log.v(TAG, s);
        }
    }

    private class MyArrayAdapter extends ArrayAdapter<String> {
        public MyArrayAdapter(@NonNull Context context, int resource) {
            super(context, resource, titles);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            String title = titles.get(position);
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(android.R.layout.simple_list_item_1, parent, false);
            }
            TextView textView = (TextView) convertView;
            textView.setText(title);
            if (position == selectedEpisode) {
                textView.setTypeface(null, Typeface.BOLD);
            } else {
                textView.setTypeface(null, Typeface.NORMAL);
            }

            return textView;
        }

        @Override
        public int getCount() {
            return titles.size();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        downloadTask = null;
        setContentView(R.layout.activity_main);

        episodes = AssetLoader.load("episodes", getAssets());
        numbers = AssetLoader.load("numbers", getAssets());
        titles = AssetLoader.load("titles", getAssets());

        viewPager = findViewById(R.id.pager);
        //viewPager.setSystemUiVisibility(View.STATUS_BAR_HIDDEN);

        drawerLayout = findViewById(R.id.drawer_layout);
        drawerList = findViewById(R.id.navigation);
        drawerList.setAdapter(new MyArrayAdapter(this, android.R.layout.simple_list_item_1));
        drawerList.setOnItemClickListener(this);

        selectEpisode(findSavedEpisode(savedInstanceState));

        if (savedInstanceState != null && savedInstanceState.containsKey(DRAWER)) {
            Parcelable drawerState = savedInstanceState.getParcelable(DRAWER);
            if (drawerState != null) {
                Log.v(TAG, "Restoring drawer instance state");
                drawerList.onRestoreInstanceState(drawerState);
            } else {
                Log.e(TAG, "Can't restore drawer instance state");
            }
        } else {
            SharedPreferences preferences = getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE);
            if (preferences.contains(DRAWER_SELECTION)) {
                int drawerListPosition = selectedEpisode; //preferences.getInt(DRAWER_SELECTION, 0);
                LOG_V("Loading drawer list position " + drawerListPosition);
                drawerList.setSelection(drawerListPosition);
            } else {
                Log.e(TAG, "Can't load drawer list position");
            }
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (downloadTask != null) {
            downloadTask.cancel(true);
            downloadTask = null;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu items for use in the action bar
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.glavni_meni, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle presses on the action bar items
        switch (item.getItemId()) {
            case R.id.action_email:
                Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
                emailIntent.setData(Uri.parse("mailto:"));
                String[] emails = {CONTACT_EMAIL};
                emailIntent.putExtra(Intent.EXTRA_EMAIL, emails);
                emailIntent.putExtra(Intent.EXTRA_SUBJECT, getResources().getString(R.string.app_name) + " App");
                if (emailIntent.resolveActivity(getPackageManager()) != null) {
                    startActivity(emailIntent);
                }
                return true;
            case R.id.action_download:
                if (!internetAvailable(this)) {
                    Toast.makeText(this, "Internet Problem", Toast.LENGTH_SHORT).show();
                    return true;
                }

                downloadTask = new EpisodeDownloadTask(this, pagerAdapter.episode, pagerAdapter.links, titles.get(selectedEpisode));
                downloadTask.execute();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

//        private static final String CURRENT_PAGE = "current_page";
//        final int loadedPage;
//        if (savedInstanceState != null && savedInstanceState.containsKey(CURRENT_PAGE)) {
//            loadedPage = savedInstanceState.getInt(CURRENT_PAGE);
//                  LOG_V("Restored current page from bundle: " + loadedPage);
//        } else {
//            final SharedPreferences preferences = getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE);
//            if (preferences.contains(CURRENT_PAGE)) {
//                loadedPage = preferences.getInt(CURRENT_PAGE, 0);
//            } else {
//                  LOG_V("Can't restore current page");
//                loadedPage = 0;
//            }
//        }
//        currentPage = loadedPage;
//        onSaveInstanceState: instanceState.putInt(CURRENT_PAGE, viewPager.getCurrentItem());
//        // TODO: ovo je bolje da ide u onPageSelected
//        SharedPreferences preferences = context.getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE);
//        preferences.edit().putInt(CURRENT_PAGE, position).apply();

    @Override
    protected void onSaveInstanceState(Bundle instanceState) {
        LOG_V("Saving selectedEpisode=" + selectedEpisode);
        instanceState.putInt(EPISODE, selectedEpisode);
        instanceState.putParcelable(DRAWER, drawerList.onSaveInstanceState());
        super.onSaveInstanceState(instanceState);
    }

    private static boolean internetAvailable(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            LOG_V("Can't get connectivityManager");
            return false;
        }
        LOG_V("Got connectivityManager");

        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        if (networkInfo == null) {
            LOG_V("getActiveNetworkInfo returned null");
            return false;
        }

        boolean connected = networkInfo.isConnected();
        LOG_V("internetAvailable: " + connected);
        return connected;
    }

    private int findSavedEpisode(Bundle savedInstanceState) {
        final int savedEpisode;
        if (savedInstanceState != null && savedInstanceState.containsKey(EPISODE)) {
            savedEpisode = savedInstanceState.getInt(EPISODE);
            LOG_V("Loaded episode from bundle: " + savedEpisode);
        } else {
            savedEpisode = getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE).getInt(EPISODE, 0);
            LOG_V("Loaded episode from shared prefs: " + savedEpisode);
        }
        LOG_V("Returning savedEpisode=" + savedEpisode);
        return savedEpisode;
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
        LOG_V("onItemClick: " + position);
        drawerList.setItemChecked(position, !drawerList.isItemChecked(position));
        drawerLayout.closeDrawer(drawerList);
        selectEpisode(position);
    }

    private void selectEpisode(int position) {
        setTitle(titles.get(position));
        pagerAdapter = new MyPagerAdapter(this, getSupportFragmentManager(), episodes.get(position), getAssets());
        viewPager.setAdapter(pagerAdapter);
        selectedEpisode = position;
        int drawerListPosition = drawerList.getSelectedItemPosition();
        LOG_V("Saving episode " + selectedEpisode + " and drawer list position " + drawerListPosition);
        getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE).edit()
                .putInt(EPISODE, selectedEpisode)
                .putInt(DRAWER_SELECTION, drawerListPosition)
                .apply();
    }

    public static class MyPagerAdapter extends FragmentStatePagerAdapter {
        List<String> links;
        String episode;
        Context context;

        MyPagerAdapter(Context context, FragmentManager fm, String episode, AssetManager assetManager) {
            super(fm);
            links = AssetLoader.load(episode, assetManager);
            this.episode = episode;
            this.context = context;
        }

        @Override
        public Fragment getItem(int position) {
            Fragment f = new MyFragment();
            Bundle args = new Bundle();
            args.putString(MyFragment.FILENAME, episode + "_" + position);
            args.putString(MyFragment.LINK, links.get(position));
            if (position + 1 < links.size()) {
                args.putString(MyFragment.NEXT_FILENAME, episode + "_" + (position + 1));
                args.putString(MyFragment.NEXT_LINK, links.get(position + 1));
            }
            f.setArguments(args);
            return f;
        }

        @Override
        public int getCount() {
            return links.size();
        }

        public static class MyFragment extends Fragment {
            public static final String FILENAME = "filename";
            public static final String LINK = "link";
            public static final String NEXT_FILENAME = "nextFilename";
            public static final String NEXT_LINK = "nextLink";
            String filename;
            String link;
            String nextFilename;
            String nextLink;

            private static final Map<String, MyLoadTask> pendingDownloads = new HashMap<>();

            private static synchronized void removePendingDownload(String link) {
                LOG_V("removePendingDownload(" + link + ")");
                pendingDownloads.remove(link);
            }

            private static synchronized void markPendingDownloadAsAbandoned(String link) {
                MyLoadTask loadTask = pendingDownloads.get(link);
                if (loadTask != null) {
                    loadTask.cancel(true);
                }
            }

            @Override
            public void onCreate(Bundle savedInstanceState) {
                super.onCreate(savedInstanceState);
                restore();
            }

            @Override
            public void onDestroy() {
                super.onDestroy();
                LOG_V("onDestroy(" + filename + ")");
                markPendingDownloadAsAbandoned(link);
            }

            private void restore() {
                Bundle args = getArguments();
                if (args != null) {
                    filename = args.getString(FILENAME);
                    link = args.getString(LINK);
                    nextFilename = args.getString(NEXT_FILENAME);
                    nextLink = args.getString(NEXT_LINK);
                }
            }

            @Override
            public void onActivityCreated(Bundle savedInstanceState) {
                super.onActivityCreated(savedInstanceState);
                restore();
            }

            @Override
            public void onSaveInstanceState(Bundle outState) {
                super.onSaveInstanceState(outState);
                if (outState == null) {
                    return;
                }
                outState.putString(FILENAME, filename);
                outState.putString(LINK, link);
                outState.putString(NEXT_FILENAME, nextFilename);
                outState.putString(NEXT_LINK, nextLink);
            }

            @Override
            public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
                View pageView = inflater.inflate(R.layout.image, container, false);
                AppCompatImageView imageView = pageView.findViewById(R.id.imageView);
                ProgressBar progressBar = (ProgressBar) pageView.findViewById(R.id.progressBar);
                imageView.setTag(progressBar);

                LOG_V("onCreate(" + filename + ")");

                File cacheDir = getContext().getCacheDir();
                File pictureFile = new File(cacheDir, filename);
                if (!internetAvailable(getContext()) && !pictureFile.exists()) {
                    //Toast.makeText(getContext(), "Internet Problem", Toast.LENGTH_SHORT).show();
                    imageView.setImageResource(R.drawable.internet_problem);
                    return pageView;
                }

                loadPicture(pictureFile, link, imageView);
                // FragmentStatePagerAdapter does this automatically :)
//                if (nextFilename != null) {
//                    loadPicture(new File(cacheDir, nextFilename), nextLink, null);
//                }

                return pageView;
            }

            private static synchronized void loadPicture(File imageFile, String link, AppCompatImageView imageView) {
                LOG_V("loadPicture:(" + imageFile + " ," + link + ")");
                MyLoadTask loadTask = pendingDownloads.get(link);
                if (loadTask != null) {
                    LOG_V("Remembering new image view: " + imageFile);
                    loadTask.setImageView(imageView);
                } else {
                    loadTask = new MyLoadTask(link, imageFile, imageView);
                    pendingDownloads.put(link, loadTask);
                    LOG_V("Added pending download:" + link);
                    LOG_V("Executing download: " + loadTask);
                    loadTask.execute();
                }
            }

            private static class MyLoadTask extends AsyncTask<Void, Void, Bitmap> {
                File imageFile;
                WeakReference<ImageView> imageView;
                String link;

                MyLoadTask(String link, File imageFile, ImageView imageView) {
                    this.imageFile = imageFile;
                    this.link = link;
                    setImageView(imageView);
                }

                synchronized void setImageView(ImageView imageView) {
                    this.imageView = new WeakReference<>(imageView);
                }

                @Override
                protected Bitmap doInBackground(Void[] voids) {
                    LOG_V("doInBackground(" + imageFile + ")");
                    Bitmap savedBitmap = null;
                    if (imageFile.exists()) {
                        LOG_V("The file " + imageFile + " exists.");
                        if (getImageView() == null || isCancelled()) {
                            return null;
                        } else {
                            LOG_V("Decoding image from " + imageFile);
                            savedBitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath());
                        }
                    }
                    if (savedBitmap != null) {
                        return savedBitmap;
                    }

                    deleteOldSavedFiles(imageFile);
                    if (isCancelled()) {
                        return null;
                    } else {
                        ImageView destinationView = imageView.get();
                        if (destinationView == null) {
                             return null;
                        }
                        return DownloadAndSave.downloadAndSave(link, imageFile, destinationView.getWidth(), destinationView.getHeight());
                    }
                }

                private static synchronized void deleteOldSavedFiles(File imageFile) {
                    File[] files = imageFile.getParentFile().listFiles();
                    if (files != null && files.length > MAX_DOWNLOADED_IMAGES) {
                        LOG_V("Found " + files.length + " cached images");
                        Arrays.sort(files, new Comparator<File>() {
                            @Override
                            public int compare(File o1, File o2) {
                                long diff = o1.lastModified() - o2.lastModified();
                                if (diff < 0) return -1;
                                else if (diff > 0) return 1;
                                else return 0;
                            }
                        });

                        for (int i = 0; i < files.length - MAX_DOWNLOADED_IMAGES; i++) {
                            deleteFile(files[i]);
                        }
                    }
                }

                private synchronized ImageView getImageView() {
                    return imageView.get();
                }

                @Override
                protected void onPostExecute(Bitmap bitmap) {
                    ImageView view = getImageView();
                    ProgressBar progressBar = view != null ? (ProgressBar) view.getTag() : null;
                    try {
                        removePendingDownload(link);
                        LOG_V("onPostExecute(" + imageFile + ")");
                        if (bitmap == null) {
                            return;
                        }
                        if (view != null) {
                            LOG_V("Loading into ImageView");
                            view.setImageBitmap(bitmap);
                        }
                    } finally {
                        if (progressBar != null) {
                            progressBar.setVisibility(View.GONE);
                        }
                    }
                }

                @Override
                protected void onCancelled(Bitmap b) {
                    LOG_V("onCancelled(" + imageFile + ")");
                    onPostExecute(null);
                }
            }
        }
    }

    public static void deleteFile(File file) {
        if (file.delete()) {
            LOG_V("Deleted " + file);
        } else {
            Log.wtf(TAG, "Can't delete " + file);
        }
    }

    private static class EpisodeDownloadTask extends AsyncTask<Void, Integer, Boolean> implements DialogInterface.OnCancelListener {
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
}
