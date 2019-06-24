package org.mg94c18.alanford;

import android.app.AlertDialog;
import android.app.SearchManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.AppCompatImageView;
import android.text.InputType;
import android.util.Log;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import org.mg94c18.alanford.sync.SyncAdapter;
import static org.mg94c18.alanford.Logger.LOG_V;
import static org.mg94c18.alanford.Logger.TAG;

import java.io.File;
import java.io.FileFilter;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;

public class MainActivity extends AppCompatActivity implements AdapterView.OnItemClickListener {
    private static final long MAX_DOWNLOADED_IMAGES_ONLINE = 20;
    private static final long MAX_DOWNLOADED_IMAGES_INTERNAL_OFFLINE = 500;
    private static final String SHARED_PREFS_NAME = "config";
    private static final String EPISODE_TITLE = "episode_title";
    private static final String EPISODE_NUMBER = "episode_number";
    private static final String EPISODE_INDEX = "episode";
    private static final String DRAWER = "drawer";
    private static final String CURRENT_PAGE_EPISODE = "current_page_episode";
    private static final String CURRENT_PAGE = "current_page";
    private static final String CONTACT_EMAIL = "yckopo@gmail.com";
    private static final String MY_ACTION_VIEW = BuildConfig.APPLICATION_ID + ".VIEW";
    private static final String INTERNET_PROBLEM = "Internet Problem";
    public static final String INTERNAL_OFFLINE = "offline";
    public static final String TOTAL_DOWNLOAD_OPTION = "totalDownloadOption";

    ViewPager viewPager;
    MyPagerAdapter pagerAdapter;
    DrawerLayout drawerLayout;
    ListView drawerList;
    List<String> titles;
    List<String> numbers;
    List<String> dates;
    boolean assetsLoaded = false;
    int selectedEpisode = 0;
    String selectedEpisodeTitle;
    String selectedEpisodeNumber;
    EpisodeDownloadTask downloadTask;
    AlertDialog pagePickerDialog;
    AlertDialog totalDownloadDialog;
    TotalDownloadOption totalDownloadOption = TotalDownloadOption.MODAL_DIALOG;
    static long syncIndex;
    ActionBarDrawerToggle drawerToggle;

    enum TotalDownloadOption {
        SYNC_ADAPTER,
        MODAL_DIALOG
    }

    private static int normalizePageIndex(int i, int max) {
        if (i < 0) {
            return 0;
        }

        if (max <= 2) {
            return -1;
        }

        if (i >= max) {
            return max - 1;
        }

        if (i == 1) {
            return 0;
        }

        if (i == 2) {
            i = 3;
        }

        return i - 2;
    }

    private class MyArrayAdapter extends ArrayAdapter<String> {
        MyArrayAdapter(@NonNull Context context, int resource) {
            super(context, resource, titles);
        }

        @Override
        public @NonNull View getView(int position, View convertView, @NonNull ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(android.R.layout.simple_list_item_1, parent, false);
            }
            TextView textView = (TextView) convertView;
            String title = numbers.get(position) + ". " + titles.get(position);
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
    protected void onNewIntent(Intent intent) {
        handleNewIntent(intent);
    }

    private boolean handleNewIntent(Intent intent) {
        if (intent == null) {
            if (BuildConfig.DEBUG) { LOG_V("Null intent"); }
            return false;
        }

        if (!MY_ACTION_VIEW.equals(intent.getAction())) {
            return false;
        }

        String epizodeStr = intent.getStringExtra(SearchManager.EXTRA_DATA_KEY);
        if (epizodeStr == null) {
            if (BuildConfig.DEBUG) { LOG_V("Can't get epizodeStr"); }
            return false;
        }

        if (SearchProvider.ALLOW_MANUAL_SYNC && epizodeStr.equals("sync")) {
            if (BuildConfig.DEBUG) { LOG_V("Sync intent"); }
            SyncAdapter.requestSyncNow(this);
            return true;
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
        if (BuildConfig.DEBUG) { LOG_V("onCreate"); }
        super.onCreate(savedInstanceState);
        downloadTask = null;
        setContentView(R.layout.activity_main);

        syncIndex = SyncAdapter.acquireSyncIndex(this);

        titles = Collections.emptyList();
        numbers = Collections.emptyList();
        dates = Collections.emptyList();

        viewPager = findViewById(R.id.pager);

        drawerLayout = findViewById(R.id.drawer_layout);

        drawerList = findViewById(R.id.navigation);
        drawerList.setAdapter(new MyArrayAdapter(this, android.R.layout.simple_list_item_1));
        drawerList.setOnItemClickListener(this);

        if (!handleNewIntent(getIntent())) {
            EpisodeInfo episodeInfo = findSavedEpisode(savedInstanceState);

            if (episodeInfo.migration) {
                selectedEpisode = episodeInfo.index;
                updateAssets(
                        AssetLoader.loadFromAssetOrUpdate(this, AssetLoader.TITLES, syncIndex),
                        AssetLoader.loadFromAssetOrUpdate(this, AssetLoader.NUMBERS, syncIndex),
                        AssetLoader.loadFromAssetOrUpdate(this, AssetLoader.DATES, syncIndex));
                selectEpisode(episodeInfo.index);
            } else {
                startAssetLoadingThread(this);
                selectEpisode(episodeInfo.index, episodeInfo.title, episodeInfo.number);
            }
        }

        if (savedInstanceState != null && savedInstanceState.containsKey(DRAWER)) {
            Parcelable drawerState = savedInstanceState.getParcelable(DRAWER);
            if (drawerState != null) {
                if (BuildConfig.DEBUG) { LOG_V("Restoring drawer instance state"); }
                drawerList.onRestoreInstanceState(drawerState);
            } else {
                if (BuildConfig.DEBUG) { LOG_V("Can't restore drawer instance state"); }
            }
        }

        Pair<Integer, Integer> currentPageAndEpizode = loadSavedCurrentPage(savedInstanceState);
        if (viewPager.getCurrentItem() == 0 && currentPageAndEpizode != null
                && currentPageAndEpizode.first != -1
                && currentPageAndEpizode.second != -1
                && currentPageAndEpizode.second == selectedEpisode) {
            if (BuildConfig.DEBUG) { LOG_V("setCurrentItem(" + currentPageAndEpizode.first + ")"); }
            viewPager.setCurrentItem(currentPageAndEpizode.first);
        } else {
            if (BuildConfig.DEBUG) { LOG_V("Can't load current page: " + "getCurrentItem=" + viewPager.getCurrentItem() + ", currentPageAndEpizode=" + currentPageAndEpizode + ", selectedEpisode=" + selectedEpisode); }
        }
    }

    private static void startAssetLoadingThread(MainActivity mainActivity) {
        final WeakReference<MainActivity> mainActivityRef = new WeakReference<>(mainActivity);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<String> titles;
                final List<String> numbers;
                final List<String> dates;
                Context context = mainActivityRef.get();
                if (context == null) {
                    if (BuildConfig.DEBUG) { LOG_V("Not loading, context went away"); }
                    return;
                }
                if (BuildConfig.DEBUG) { LOG_V("Begin loading: " + System.currentTimeMillis()); }
                titles = AssetLoader.loadFromAssetOrUpdate(context, AssetLoader.TITLES, syncIndex);
                numbers = AssetLoader.loadFromAssetOrUpdate(context, AssetLoader.NUMBERS, syncIndex);
                dates = AssetLoader.loadFromAssetOrUpdate(context, AssetLoader.DATES, syncIndex);
                if (BuildConfig.DEBUG) { LOG_V("End loading: " + System.currentTimeMillis()); }

                final MainActivity activity = mainActivityRef.get();
                if (activity == null) {
                    if (BuildConfig.DEBUG) { LOG_V("Not loading, activity went away"); }
                    return;
                }

                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        activity.updateAssets(titles, numbers, dates);
                    }
                });
            }
        }).start();
    }

    private void updateAssets(List<String> titles, List<String> numbers, List<String> dates) {
        SearchProvider.TITLES = this.titles = titles;
        SearchProvider.NUMBERS = this.numbers = numbers;
        SearchProvider.DATES = this.dates = dates;

        assetsLoaded = true;

        drawerList.setAdapter(new MyArrayAdapter(this, android.R.layout.simple_list_item_1));
        drawerList.setSelection(selectedEpisode);
        invalidateOptionsMenu();
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
        if (BuildConfig.DEBUG) { LOG_V("onStop"); }
        super.onStop();
        if (downloadTask != null) {
            downloadTask.cancel(true);
            downloadTask = null;
        }
        dismissAlertDialogs();
        saveCurrentPage(viewPager.getCurrentItem());

        SyncAdapter.setDailySync(this);
        if (totalDownloadOption == TotalDownloadOption.SYNC_ADAPTER) {
            SyncAdapter.setHourlySync(this);
        }
    }

    private void dismissAlertDialogs() {
        AlertDialog[] dialogs = {pagePickerDialog, totalDownloadDialog};
        for (AlertDialog dialog : dialogs) {
            if (dialog != null) {
                dialog.cancel();
                dialog.dismiss();
            }
        }
        pagePickerDialog = null;
        totalDownloadDialog = null;
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
        if (searchManager != null) {
            searchView.setSearchableInfo(
                    searchManager.getSearchableInfo(getComponentName()));
        }

        boolean sdCardReady = (ExternalStorageHelper.getExternalCacheDir(this) != null);
        if (sdCardReady) {
            menu.findItem(R.id.action_download).setVisible(true);
        } else {
            menu.findItem(R.id.action_download_internal).setVisible(true);
        }

        return super.onCreateOptionsMenu(menu);
    }

    private void readTotalDownloadOption() {
        String totalDownloadPref = getSharedPreferences().getString(TOTAL_DOWNLOAD_OPTION, null);
        if (TotalDownloadOption.SYNC_ADAPTER.toString().equals(totalDownloadPref)) {
            totalDownloadOption = TotalDownloadOption.SYNC_ADAPTER;
        } else {
            totalDownloadOption = TotalDownloadOption.MODAL_DIALOG;
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (BuildConfig.DEBUG) { LOG_V("onPrepareOptionsMenu"); }

        ActionBar actionBar = getSupportActionBar();
        if (assetsLoaded && actionBar != null) {
            menu.findItem(R.id.search).setVisible(true);
            readTotalDownloadOption();
            menu.findItem(R.id.action_total_download).setVisible(true);

            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
            drawerToggle = new ActionBarDrawerToggle(this, drawerLayout, R.string.drawer_open_accessibility, R.string.drawer_close_accessibility) {
                @Override
                public void onDrawerClosed(View view) {
                    super.onDrawerClosed(view);
                    getSupportActionBar().setTitle(selectedEpisodeTitle);
                }

                @Override
                public void onDrawerOpened(View view) {
                    super.onDrawerOpened(view);
                    getSupportActionBar().setTitle("" + titles.size() + " epizoda");
                }
            };
            drawerLayout.addDrawerListener(drawerToggle);
            drawerToggle.syncState();
        }

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (drawerToggle != null && drawerToggle.onOptionsItemSelected(item)) {
            return true;
        }

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
                final String errorMessage;
                String storageState = Environment.getExternalStorageState();
                if (internetNotAvailable(this)) {
                    errorMessage = INTERNET_PROBLEM;
                } else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(storageState)) {
                    errorMessage = "Memorijska kartica je ubačena, ali je read-only";
                } else if (!Environment.MEDIA_MOUNTED.equals(storageState)
                        || ExternalStorageHelper.getExternalCacheDir(this) == null) {
                    errorMessage = "Ne vidim memorijsku karticu";
                } else {
                    errorMessage = null;
                }
                if (errorMessage != null) {
                    Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show();
                    return true;
                }
                downloadTask = new EpisodeDownloadTask(-1, this, pagerAdapter.episode, pagerAdapter.links, selectedEpisodeTitle, ExternalStorageHelper.getExternalCacheDir(this), EpisodeDownloadTask.Destination.SD_CARD);
                downloadTask.execute();
                return true;
            case R.id.action_download_internal:
                if (internetNotAvailable(this)) {
                    Toast.makeText(this, INTERNET_PROBLEM, Toast.LENGTH_LONG).show();
                    return true;
                }
                File offlineDir = ExternalStorageHelper.getInternalOfflineDir(this);
                downloadTask = new EpisodeDownloadTask(MAX_DOWNLOADED_IMAGES_INTERNAL_OFFLINE,this, pagerAdapter.episode, pagerAdapter.links, selectedEpisodeTitle, offlineDir, EpisodeDownloadTask.Destination.INTERNAL_MEMORY);
                downloadTask.execute();
                return true;
            case R.id.action_review:
                Intent intent = PlayIntentMaker.createPlayIntent(this);
                if (intent.resolveActivity(getPackageManager()) != null) {
                    startActivity(intent);
                }
                return true;
            case R.id.go_to_page:
                final EditText input = new EditText(this);
                input.setOnEditorActionListener(
                        new TextView.OnEditorActionListener() {
                            @Override
                            public boolean onEditorAction(TextView textView, int actionId, KeyEvent keyEvent) {
                                if (actionId == EditorInfo.IME_ACTION_DONE || (keyEvent != null && keyEvent.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                                    String numberString = textView.getText().toString();
                                    dismissAlertDialogs();
                                    tryGoingToPage(numberString);
                                }
                                return false;
                            }
                        });
                dismissAlertDialogs();
                input.setInputType(InputType.TYPE_CLASS_NUMBER);
                pagePickerDialog = new AlertDialog.Builder(this)
                        .setCancelable(true)
                        .setMessage("Unesite broj strane:")
                        .setView(input)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                tryGoingToPage(input.getText().toString());
                            }
                        })
                        .create();
                pagePickerDialog.setCanceledOnTouchOutside(false);
                pagePickerDialog.show();
                return true;
            case R.id.search:
                if (BuildConfig.DEBUG) { LOG_V("Search requested"); }
                onSearchRequested();
                return true;
            case R.id.action_total_download:
                if (BuildConfig.DEBUG) { LOG_V("Total Download"); }
                dismissAlertDialogs();
                String message = "";
                if (totalDownloadOption == TotalDownloadOption.SYNC_ADAPTER) {
                    message = "Download je trenutno uključen i aktivira se kad ne koristite uređaj.";
                }
                totalDownloadDialog = new AlertDialog.Builder(this)
                        .setCancelable(true)
                        .setTitle(getResources().getString(R.string.total_download_text) + " (OPREZ: cela kolekcija je oko 15GB)!")
                        .setMessage(".  Izaberite download opciju.")
                        .setNegativeButton(R.string.total_download_option_sync, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                startTotalDownload(TotalDownloadOption.SYNC_ADAPTER);
                            }
                        })
                        .setPositiveButton(R.string.total_download_option_modal, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                startTotalDownload(TotalDownloadOption.MODAL_DIALOG);
                            }
                        })
                        //.setNegativeButton(R.string.total_download_option_back, null)
                        .create();
                totalDownloadDialog.setCanceledOnTouchOutside(false);
                totalDownloadDialog.show();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void tryGoingToPage(String numberString) {
        try {
            int pagePicked = normalizePageIndex(Integer.parseInt(numberString), viewPager.getAdapter().getCount());
            if (pagePicked != -1) {
                viewPager.setCurrentItem(pagePicked);
            }
        } catch (NumberFormatException nfe) {
            Log.wtf("Invalid input", nfe);
        }
    }

    private void startTotalDownload(TotalDownloadOption selectedOption) {
        if (BuildConfig.DEBUG) { LOG_V("startTotalDownload: " + selectedOption); }
        // TODO: ugasiti prethodno po potrebi
        if (selectedOption == TotalDownloadOption.MODAL_DIALOG && totalDownloadOption == TotalDownloadOption.SYNC_ADAPTER) {
            SyncAdapter.cancelHourlySync(this);
            getSharedPreferences().edit().remove(TOTAL_DOWNLOAD_OPTION).apply();
        } else if (selectedOption == TotalDownloadOption.SYNC_ADAPTER) {
            SyncAdapter.setHourlySync(this);
        }
        totalDownloadOption = selectedOption;
        getSharedPreferences().edit().putString(TOTAL_DOWNLOAD_OPTION, totalDownloadOption.toString()).apply();
        switch (totalDownloadOption) {
            case SYNC_ADAPTER:
            case MODAL_DIALOG:
                break;
        }
    }

    SharedPreferences getSharedPreferences() {
        return getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE);
    }

    @Override
    protected void onSaveInstanceState(Bundle instanceState) {
        if (BuildConfig.DEBUG) { LOG_V("onSaveInstanceState"); }
        if (BuildConfig.DEBUG) { LOG_V("Saving selectedEpisode=" + selectedEpisode); }
        instanceState.putInt(EPISODE_INDEX, selectedEpisode);
        instanceState.putString(EPISODE_TITLE, selectedEpisodeTitle);
        instanceState.putString(EPISODE_NUMBER, selectedEpisodeNumber);
        instanceState.putParcelable(DRAWER, drawerList.onSaveInstanceState());

        int currentPage = viewPager.getCurrentItem();
        instanceState.putInt(CURRENT_PAGE, currentPage);
        instanceState.putInt(CURRENT_PAGE_EPISODE, selectedEpisode);
        saveCurrentPage(currentPage);
        super.onSaveInstanceState(instanceState);
    }

    private void saveCurrentPage(int currentPage) {
        if (BuildConfig.DEBUG) { LOG_V("Saving currentPage=" + currentPage); }
        SharedPreferences preferences = getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE);
        preferences.edit().putInt(CURRENT_PAGE, currentPage).putInt(CURRENT_PAGE_EPISODE, selectedEpisode).apply();
    }

    private static boolean internetNotAvailable(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            if (BuildConfig.DEBUG) { LOG_V("Can't get connectivityManager"); }
            return true;
        }
        if (BuildConfig.DEBUG) { LOG_V("Got connectivityManager"); }

        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        if (networkInfo == null) {
            if (BuildConfig.DEBUG) { LOG_V("getActiveNetworkInfo returned null"); }
            return true;
        }

        boolean connected = networkInfo.isConnected();
        if (BuildConfig.DEBUG) { LOG_V("Connected=" + connected); }
        return !connected;
    }

    class EpisodeInfo {
        String title;
        String number;
        int index;
        boolean migration; // whether we're in migration from older release where just index was available but no title or number

        @Override
        public String toString() {
            return "{" + index + ": '" + title + "' (" + number + ")}";
        }
    }

    private @NonNull EpisodeInfo findSavedEpisode(Bundle state) {
        EpisodeInfo episodeInfo = new EpisodeInfo();

        if (state != null && state.containsKey(EPISODE_INDEX) && state.containsKey(EPISODE_TITLE) && state.containsKey(EPISODE_NUMBER)) {
            episodeInfo.index = state.getInt(EPISODE_INDEX);
            episodeInfo.title = state.getString(EPISODE_TITLE);
            episodeInfo.number = state.getString(EPISODE_NUMBER);
            if (BuildConfig.DEBUG) { LOG_V("Loaded episode from bundle: " + episodeInfo); }
        } else {
            SharedPreferences preferences = getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE);
            episodeInfo.migration = false;
            String defaultTitle = getResources().getString(R.string.default_episode_title);
            String defaultNumber = getResources().getString(R.string.default_episode_number);
            if (preferences.contains(EPISODE_TITLE) && preferences.contains(EPISODE_NUMBER)) {
                episodeInfo.title = preferences.getString(EPISODE_TITLE, defaultTitle);
                episodeInfo.number = preferences.getString(EPISODE_NUMBER, defaultNumber);
            } else {
                if (preferences.contains(EPISODE_INDEX)) {
                    episodeInfo.migration = true;
                } else {
                    episodeInfo.title = defaultTitle;
                    episodeInfo.number = defaultNumber;
                }
            }
            episodeInfo.index = preferences.getInt(EPISODE_INDEX, getResources().getInteger(R.integer.default_episode_index));
            if (BuildConfig.DEBUG) { LOG_V("Loaded episode from shared prefs: " + episodeInfo); }
        }
        return episodeInfo;
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
        if (BuildConfig.DEBUG) { LOG_V("onItemClick: " + position); }
        drawerList.setItemChecked(position, !drawerList.isItemChecked(position));
        drawerLayout.closeDrawer(drawerList);
        selectEpisode(position);
    }

    private void selectEpisode(int position) {
        selectEpisode(position, titles.get(position), numbers.get(position));
    }

    private @NonNull ActionBar getMyActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar == null) {
            throw new Error("Unexpected");
        }
        return actionBar;
    }

    private void selectEpisode(int position, String title, String number) {
        selectedEpisodeTitle = title;
        selectedEpisodeNumber = number;
        getMyActionBar().setTitle(selectedEpisodeTitle);
        pagerAdapter = new MyPagerAdapter(this, getSupportFragmentManager(), selectedEpisodeNumber);
        viewPager.setAdapter(pagerAdapter);
        selectedEpisode = position;
        if (BuildConfig.DEBUG) { LOG_V("Saving episode " + selectedEpisode); }
        getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE).edit()
                .putInt(EPISODE_INDEX, selectedEpisode)
                .putString(EPISODE_TITLE, title)
                .putString(EPISODE_NUMBER, number)
                .apply();
    }

    public static class MyPagerAdapter extends FragmentStatePagerAdapter {
        List<String> links;
        String episode;
        Context context;
        private static List<WeakReference<MyFragment>> FRAGMENTS_FOR_SCALING = new ArrayList<>();

        MyPagerAdapter(Context context, FragmentManager fm, String episode) {
            super(fm);
            links = AssetLoader.loadFromAssetOrUpdate(context, episode, syncIndex);
            this.episode = episode;
            this.context = context;
        }

        @Override
        public Fragment getItem(int position) {
            MyFragment f = new MyFragment();
            Bundle args = new Bundle();
            args.putString(MyFragment.FILENAME, DownloadAndSave.fileNameFromLink(links.get(position), episode, position));
            args.putString(MyFragment.LINK, links.get(position));
            args.putString(MyFragment.EPISODE_ID, episode);
            args.putInt(MyFragment.PAGE_NUMBER, position);
            cacheFragmentForScaling(f);
            f.setArguments(args);
            return f;
        }

        private static synchronized void cacheFragmentForScaling(MyFragment f) {
            FRAGMENTS_FOR_SCALING.add(new WeakReference<>(f));
        }

        private static synchronized void scaleCachedFragments(SharedPreferences preferences) {
            ListIterator<WeakReference<MyFragment>> iterator = FRAGMENTS_FOR_SCALING.listIterator();
            while (iterator.hasNext()) {
                WeakReference<MyFragment> elem = iterator.next();
                MyFragment f = elem.get();
                if (f == null) {
                    iterator.remove();
                    continue;
                }
                f.updateScaleFromPrefs(preferences);
            }
        }

        @Override
        public int getCount() {
            return links.size();
        }

        public static class MyFragment extends Fragment implements View.OnTouchListener, ScaleGestureDetector.OnScaleGestureListener {
            public static final String FILENAME = "filename";
            public static final String LINK = "link";
            public static final String EPISODE_ID = "episode";
            public static final String PAGE_NUMBER = "pageNumber";
            private static final String SCALE_X = "scale_x";
            private static final String SCALE_Y = "scale_y";
            private static final float DELTA = 0.02f;
            private static final float SCALE_MIN = 1.00f;
            private static final float SCALE_MAX = 1.20f;
            private static final float SPAN_THRESHOLD = 100f;

            String episodeId;
            String filename;
            String link;
            int pageNumber;
            MyLoadTask loadTask;
            ScaleGestureDetector mScaleDetector;
            float mScaleX = 1.0f;
            float mScaleY = 1.0f;

            @Override
            public void onCreate(Bundle savedInstanceState) {
                mScaleDetector = new ScaleGestureDetector(getContext(), this);
                super.onCreate(savedInstanceState);
                restore();
            }

            @Override
            public void onDestroy() {
                super.onDestroy();
                if (BuildConfig.DEBUG) { LOG_V("onDestroy(" + filename + ")"); }
                if (loadTask != null) {
                    loadTask.cancel(true);
                }
            }

            private void restore() {
                Bundle args = getArguments();
                if (args != null) {
                    filename = args.getString(FILENAME);
                    link = args.getString(LINK);
                    episodeId = args.getString(EPISODE_ID);
                    pageNumber = args.getInt(PAGE_NUMBER);
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
                if (BuildConfig.DEBUG) { LOG_V("onCreateView(" + filename + ")"); }

                View pageView = inflater.inflate(R.layout.image, container, false);
                AppCompatImageView imageView = pageView.findViewById(R.id.imageView);
                ProgressBar progressBar = pageView.findViewById(R.id.progressBar);
                imageView.setTag(progressBar);
                updateScaleFromPrefs(getContext().getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE), imageView);
                pageView.setOnTouchListener(this);

                loadTask = new MyLoadTask(getContext(), link, filename, imageView);
                loadTask.execute();

                return pageView;
            }

            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (BuildConfig.DEBUG) { LOG_V("Scaling: onTouch"); }
                mScaleDetector.onTouchEvent(motionEvent);
                return true;
            }

            @Override
            public boolean onScale(ScaleGestureDetector scaleGestureDetector) {
                if (BuildConfig.DEBUG) { LOG_V("Scaling: onScale"); }
                float scaleFactor = scaleGestureDetector.getScaleFactor();
                float change = Math.abs(scaleFactor - 1.0f);
                if (Float.compare(change, DELTA) < 0) {
                    return false;
                }

                if (loadTask == null) {
                    return true;
                }
                ImageView view = loadTask.getImageView();
                if (view == null) {
                    return true;
                }

                float scaleX = calculateScale(view.getScaleX(), scaleFactor, scaleGestureDetector.getCurrentSpanX());
                float scaleY = calculateScale(view.getScaleY(), scaleFactor, scaleGestureDetector.getCurrentSpanY());
                if (Float.compare(scaleX, view.getScaleX()) == 0 && Float.compare(scaleY, view.getScaleY()) == 0) {
                    return false;
                }

                SharedPreferences preferences = getContext().getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE);
                preferences.edit()
                        .putFloat(getScaleKey(SCALE_X), scaleX)
                        .putFloat(getScaleKey(SCALE_Y), scaleY)
                        .apply();

                updateScaleFromPrefs(preferences, view);
                scaleCachedFragments(preferences);
                return true;
            }

            public void updateScaleFromPrefs(SharedPreferences preferences) {
                updateScaleFromPrefs(preferences, loadTask != null ? loadTask.getImageView() : null);
            }

            private String getScaleKey(String axis) {
                String key = episodeId + axis;
                if (pageNumber == 0) {
                    key = key + ".0";
                }
                return key;
            }

            private void updateScaleFromPrefs(SharedPreferences preferences, @Nullable ImageView imageView) {
                if (imageView == null) {
                    return;
                }
                mScaleX = preferences.getFloat(getScaleKey(SCALE_X), 1.0f);
                mScaleY = preferences.getFloat(getScaleKey(SCALE_Y), 1.0f);
                if (Float.compare(mScaleX, imageView.getScaleX()) == 0 && Float.compare(mScaleY, imageView.getScaleY()) == 0) {
                    return;
                }

                imageView.setScaleX(mScaleX);
                imageView.setScaleY(mScaleY);
                imageView.invalidate();
            }

            private static float calculateScale(float scale, float scaleFactor, float currentSpan) {
                if (Float.compare(Math.abs(currentSpan), SPAN_THRESHOLD) < 0) {
                    return scale;
                }

                if (Float.compare(scaleFactor, 1.0f) < 0) {
                    scale -= DELTA;
                } else {
                    scale += DELTA;
                }

                // Don't let the object get too small or too large.
                return Math.max(SCALE_MIN, Math.min(scale, SCALE_MAX));
            }

            @Override
            public boolean onScaleBegin(ScaleGestureDetector scaleGestureDetector) {
                if (BuildConfig.DEBUG) { LOG_V("Scaling: onScaleBegin"); }
                return true;
            }

            @Override
            public void onScaleEnd(ScaleGestureDetector scaleGestureDetector) {
                if (BuildConfig.DEBUG) { LOG_V("Scaling: onScaleEnd"); }
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
                    if (BuildConfig.DEBUG) { LOG_V("doInBackground(" + imageFile + ")"); }
                    Bitmap savedBitmap = null;
                    Context context = contextRef.get();
                    if (context == null) {
                        return null;
                    }
                    List<File> cacheDirs = new ArrayList<>();
                    cacheDirs.add(context.getCacheDir());
                    cacheDirs.add(ExternalStorageHelper.getExternalCacheDir(context));
                    cacheDirs.add(new File(context.getCacheDir(), INTERNAL_OFFLINE));
                    for (File cacheDir : cacheDirs) {
                        if (cacheDir == null) {
                            continue;
                        }
                        if (savedBitmap != null) {
                            break;
                        }
                        File savedImage = new File(cacheDir, imageFile);
                        if (savedImage.exists()) {
                            if (BuildConfig.DEBUG) { LOG_V("The file " + imageFile + " exists."); }
                            if (getImageView() == null || isCancelled()) {
                                return null;
                            } else {
                                if (BuildConfig.DEBUG) { LOG_V("Decoding image from " + imageFile); }
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
                    if (internetNotAvailable(context)) {
                        return null;
                    }

                    File imageToDownload = new File(context.getCacheDir(), imageFile);
                    if (isCancelled()) {
                        return null;
                    } else {
                        deleteOldSavedFiles(imageToDownload.getParentFile(), MAX_DOWNLOADED_IMAGES_ONLINE);
                        return DownloadAndSave.downloadAndSave(link, imageToDownload, destinationView.getWidth(), destinationView.getHeight());
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
                        if (BuildConfig.DEBUG) { LOG_V("onPostExecute(" + imageFile + ")"); }
                        if (bitmap == null && !isCancelled()) {
                            Context context = contextRef.get();
                            if (view != null && context != null && internetNotAvailable(context)) {
                                view.setImageResource(R.drawable.internet_problem);
                            }
                            return;
                        }
                        if (view != null) {
                            if (BuildConfig.DEBUG) { LOG_V("Loading into ImageView(" + imageFile + ")"); }
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
                    if (BuildConfig.DEBUG) { LOG_V("onCancelled(" + imageFile + ")"); }
                    onPostExecute(null);
                }
            }
        }
    }

    public static synchronized void deleteOldSavedFiles(File dir, long maxImages) {
        File[] files = dir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                return !file.isDirectory();
            }
        });
        if (files != null && files.length > maxImages) {
            if (BuildConfig.DEBUG) { LOG_V("Found " + files.length + " cached images"); }
            Arrays.sort(files, new Comparator<File>() {
                @Override
                public int compare(File o1, File o2) {
                    long diff = o1.lastModified() - o2.lastModified();
                    if (diff < 0) return -1;
                    else if (diff > 0) return 1;
                    else return 0;
                }
            });

            for (int i = 0; i < files.length - maxImages; i++) {
                deleteFile(files[i]);
            }
        }
    }

    public static void deleteFile(File file) {
        if (file.delete()) {
            if (BuildConfig.DEBUG) { LOG_V("Deleted " + file); }
        } else {
            Log.wtf(TAG, "Can't delete " + file);
        }
    }

}
