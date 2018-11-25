package org.mg94c18.alanford;

import android.app.Activity;
import android.app.ProgressDialog;
import android.app.SearchManager;
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
import android.os.Build;
import android.os.Environment;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.AppCompatImageView;
import android.util.Log;
import android.util.Pair;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
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
    private static final String CURRENT_PAGE_EPISODE = "current_page_episode";
    private static final String CURRENT_PAGE = "current_page";
    private static final String CONTACT_EMAIL = "mg94c18@tesla.rcub.bg.ac.rs";
    private static final String MY_ACTION_VIEW = "org.mg94c18.alanford.VIEW";

    ViewPager viewPager;
    MyPagerAdapter pagerAdapter;
    DrawerLayout drawerLayout;
    ListView drawerList;
    List<String> titles;
    List<String> numbers;
    List<String> dates;
    int selectedEpisode = 0;
    EpisodeDownloadTask downloadTask;

    public static void LOG_V(String s) {
        if (BuildConfig.DEBUG) {
            Log.v(TAG, s);
        }
    }

    public static void LOG_D(String s) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, s);
        }
    }

    private class MyArrayAdapter extends ArrayAdapter<String> {
        public MyArrayAdapter(@NonNull Context context, int resource) {
            super(context, resource, titles);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(android.R.layout.simple_list_item_1, parent, false);
            }
            TextView textView = (TextView) convertView;
            textView.setText(numbers.get(position) + ". " + titles.get(position));
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
    protected void onNewIntent(Intent intent) {
        handleNewIntent(intent);
    }

    private boolean handleNewIntent(Intent intent) {
        if (intent == null) {
            return false;
        }

        if (!MY_ACTION_VIEW.equals(intent.getAction())) {
            return false;
        }

        String epizodeStr = intent.getStringExtra(SearchManager.EXTRA_DATA_KEY);
        if (epizodeStr == null) {
            return false;
        }

        int episode;
        try {
            episode = Integer.parseInt(epizodeStr);
        } catch (NumberFormatException nfe) {
            Log.wtf(TAG, "Can't convert the episode ID", nfe);
            return false;
        }

        selectEpisode(episode);
        drawerList.setSelection(episode);

        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LOG_V("onCreate");
        super.onCreate(savedInstanceState);
        downloadTask = null;
        setContentView(R.layout.activity_main);

        dates = AssetLoader.load("dates", getAssets());
        numbers = AssetLoader.load("numbers", getAssets());
        titles = AssetLoader.load("titles", getAssets());

        SearchProvider.TITLES = titles;
        SearchProvider.NUMBERS = numbers;
        SearchProvider.DATES = dates;

        viewPager = findViewById(R.id.pager);

        drawerLayout = findViewById(R.id.drawer_layout);
        drawerList = findViewById(R.id.navigation);
        drawerList.setAdapter(new MyArrayAdapter(this, android.R.layout.simple_list_item_1));
        drawerList.setOnItemClickListener(this);

        if (!handleNewIntent(getIntent())) {
            selectEpisode(findSavedEpisode(savedInstanceState));
        }

        if (savedInstanceState != null && savedInstanceState.containsKey(DRAWER)) {
            Parcelable drawerState = savedInstanceState.getParcelable(DRAWER);
            if (drawerState != null) {
                LOG_D("Restoring drawer instance state");
                drawerList.onRestoreInstanceState(drawerState);
            } else {
                Log.e(TAG, "Can't restore drawer instance state");
            }
        } else {
            LOG_D("savedInstanceState doesn't contain drawer state");
            drawerList.setSelection(selectedEpisode);
        }

        Pair<Integer, Integer> currentPageAndEpizode = loadSavedCurrentPage(savedInstanceState);
        if (viewPager.getCurrentItem() == 0 && currentPageAndEpizode != null
                && currentPageAndEpizode.first != -1
                && currentPageAndEpizode.second != -1
                && currentPageAndEpizode.second == selectedEpisode) {
            LOG_D("setCurrentItem(" + currentPageAndEpizode.first + ")");
            viewPager.setCurrentItem(currentPageAndEpizode.first);
        } else {
            LOG_D("Can't load current page: "
                    + "getCurrentItem=" + viewPager.getCurrentItem()
                    + "currentPageAndEpizode=" + currentPageAndEpizode
                    + "selectedEpisode=" + selectedEpisode);
        }
    }

    Pair<Integer, Integer> loadSavedCurrentPage(Bundle savedInstanceState) {
        if (savedInstanceState != null
                && savedInstanceState.containsKey(CURRENT_PAGE)
                && savedInstanceState.containsKey(CURRENT_PAGE_EPISODE)) {
            return Pair.create(savedInstanceState.getInt(CURRENT_PAGE), savedInstanceState.getInt(CURRENT_PAGE_EPISODE));
        }
        final SharedPreferences preferences = getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE);
        if (preferences.contains(CURRENT_PAGE) && preferences.contains(CURRENT_PAGE_EPISODE)) {
            return Pair.create(preferences.getInt(CURRENT_PAGE,-1), preferences.getInt(CURRENT_PAGE_EPISODE, -1));
        }
        return null;
    }

    @Override
    public void onStop() {
        LOG_V("onStop");
        super.onStop();
        if (downloadTask != null) {
            downloadTask.cancel(true);
            downloadTask = null;
        }
        saveCurrentPage(viewPager.getCurrentItem());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu items for use in the action bar
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.glavni_meni, menu);

        // Associate searchable configuration with the SearchView
        SearchManager searchManager =
                (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        SearchView searchView =
                (SearchView) menu.findItem(R.id.search).getActionView();
        searchView.setSearchableInfo(
                searchManager.getSearchableInfo(getComponentName()));

        menu.findItem(R.id.action_download).setVisible("KFTT".equals(Build.MODEL));

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
            case R.id.action_episodes:
                drawerLayout.openDrawer(Gravity.LEFT);
                return true;
            case R.id.action_download:
                final String errorMessage;
                // TODO: use Environment.getExternalStorageState();
                if (!internetAvailable(this)) {
                    errorMessage = "Internet Problem";
//                } else if (!externalStorageHelper.mExternalStorageAvailable) {
//                    errorMessage = "Memorijska kartica nije ubačena";
//                } else if (!externalStorageHelper.mExternalStorageWriteable) {
//                    errorMessage = "Memorijska kartica je read-only";
                } else {
                    errorMessage = null;
                }
                if (errorMessage != null) {
                    Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show();
                    return true;
                }
                downloadTask = new EpisodeDownloadTask(this, pagerAdapter.episode, pagerAdapter.links, titles.get(selectedEpisode));
                downloadTask.execute();
                return true;
            case R.id.action_review:
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(
                        "https://play.google.com/store/apps/details?id=" + getPackageName()));
                intent.setPackage("com.android.vending");
                if (intent.resolveActivity(getPackageManager()) != null) {
                    startActivity(intent);
                }
                return true;

            case R.id.search:
                onSearchRequested();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle instanceState) {
        LOG_V("onSaveInstanceState");
        LOG_D("Saving selectedEpisode=" + selectedEpisode);
        instanceState.putInt(EPISODE, selectedEpisode);
        instanceState.putParcelable(DRAWER, drawerList.onSaveInstanceState());

        int currentPage = viewPager.getCurrentItem();
        instanceState.putInt(CURRENT_PAGE, currentPage);
        instanceState.putInt(CURRENT_PAGE_EPISODE, selectedEpisode);
        saveCurrentPage(currentPage);
        super.onSaveInstanceState(instanceState);
    }

    private void saveCurrentPage(int currentPage) {
        LOG_D("Saving currentPage=" + currentPage);
        SharedPreferences preferences = getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE);
        preferences.edit().putInt(CURRENT_PAGE, currentPage).putInt(CURRENT_PAGE_EPISODE, selectedEpisode).apply();
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
        pagerAdapter = new MyPagerAdapter(this, getSupportFragmentManager(), numbers.get(position), getAssets());
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
            args.putString(MyFragment.FILENAME, DownloadAndSave.fileNameFromLink(links.get(position), episode, position));
            args.putString(MyFragment.LINK, links.get(position));
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
            String filename;
            String link;
            MyLoadTask loadTask;

            @Override
            public void onCreate(Bundle savedInstanceState) {
                super.onCreate(savedInstanceState);
                restore();
            }

            @Override
            public void onDestroy() {
                super.onDestroy();
                LOG_V("onDestroy(" + filename + ")");
                if (loadTask != null) {
                    loadTask.cancel(true);
                }
            }

            private void restore() {
                Bundle args = getArguments();
                if (args != null) {
                    filename = args.getString(FILENAME);
                    link = args.getString(LINK);
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
            }

            @Override
            public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
                LOG_V("onCreateView(" + filename + ")");

                View pageView = inflater.inflate(R.layout.image, container, false);
                AppCompatImageView imageView = pageView.findViewById(R.id.imageView);
                ProgressBar progressBar = pageView.findViewById(R.id.progressBar);
                imageView.setTag(progressBar);

                loadTask = new MyLoadTask(getContext(), link, filename, imageView);
                loadTask.execute();

                return pageView;
            }

            private static class MyLoadTask extends AsyncTask<Void, Void, Bitmap> {
                String imageFile;
                WeakReference<ImageView> imageView;
                String link;
                WeakReference<Context> contextRef;

                MyLoadTask(Context context, String link, String imageFile, ImageView imageView) {
                    this.imageFile = imageFile;
                    this.link = link;
                    this.contextRef = new WeakReference<>(context);
                    this.imageView = new WeakReference<>(imageView);
                }

                @Override
                protected Bitmap doInBackground(Void[] voids) {
                    LOG_V("doInBackground(" + imageFile + ")");
                    Bitmap savedBitmap = null;
                    Context context = contextRef.get();
                    if (context == null) {
                        return null;
                    }
                    List<File> cacheDirs = new ArrayList<>();
                    cacheDirs.add(context.getCacheDir());
                    // TODO: cacheDirs.add(context.getExternalCacheDir()); kad proverim kako radi na >= 4.4
                    for (File cacheDir : cacheDirs) {
                        if (cacheDir == null) {
                            continue;
                        }
                        if (savedBitmap != null) {
                            break;
                        }
                        File savedImage = new File(cacheDir, imageFile);
                        if (savedImage.exists()) {
                            LOG_V("The file " + imageFile + " exists.");
                            if (getImageView() == null || isCancelled()) {
                                return null;
                            } else {
                                LOG_V("Decoding image from " + imageFile);
                                savedBitmap = BitmapFactory.decodeFile(savedImage.getAbsolutePath());
                            }
                        }
                    }
                    if (savedBitmap != null) {
                        return savedBitmap;
                    }

                    ImageView destinationView = imageView.get();
                    if (destinationView == null) {
                        return null;
                    }
                    if (!internetAvailable(context)) {
                        return null;
                    }

                    File imageToDownload = new File(context.getCacheDir(), imageFile);
                    if (isCancelled()) {
                        return null;
                    } else {
                        deleteOldSavedFiles(imageToDownload);
                        return DownloadAndSave.downloadAndSave(link, imageToDownload, destinationView.getWidth(), destinationView.getHeight());
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
                        LOG_V("onPostExecute(" + imageFile + ")");
                        if (bitmap == null && !isCancelled()) {
                            Context context = contextRef.get();
                            if (view != null && context != null && !internetAvailable(context)) {
                                view.setImageResource(R.drawable.internet_problem);
                            }
                            return;
                        }
                        if (view != null) {
                            LOG_V("Loading into ImageView(" + imageFile + ")");
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

}
