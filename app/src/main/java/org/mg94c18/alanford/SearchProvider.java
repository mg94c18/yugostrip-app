package org.mg94c18.alanford;

import android.app.SearchManager;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SearchProvider extends ContentProvider {
    public static List<String> TITLES;
    public static List<String> NUMBERS;
    public static List<String> DATES;

    private static String[] MANDATORY_COLUMNS = {
            BaseColumns._ID,
            SearchManager.SUGGEST_COLUMN_TEXT_1,
            SearchManager.SUGGEST_COLUMN_TEXT_2,
            SearchManager.SUGGEST_COLUMN_INTENT_EXTRA_DATA
    };

    @Override
    public boolean onCreate() {
        return true;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] strings, @Nullable String s, @Nullable String[] strings1, @Nullable String s1) {
        String query = uri.getLastPathSegment().toLowerCase();
        MainActivity.LOG_V("query(" + query + ")");
        MatrixCursor cursor = new MatrixCursor(MANDATORY_COLUMNS);

        Set<String> querySet = new HashSet<>();
        querySet.add(query);
        querySet.add(query.replace('c', 'ć'));
        querySet.add(query.replace('s', 'š'));
        querySet.add(query.replace('c', 'č'));
        querySet.add(query.replace('z', 'ž'));
        querySet.add(query.replace("dj", "đ"));
        querySet.add(query.replace("e", "je"));
        querySet.add(query.replace("e", "ije"));
        querySet.add(query.replace("je", "e"));
        querySet.add(query.replace("ije", "e"));

        List<String> titlesLowercase = new ArrayList<>(TITLES.size());
        for (String title : TITLES) {
            titlesLowercase.add(title.toLowerCase());
        }

        for (int i = 0; i < TITLES.size(); i++) {
            boolean addThis = false;
            if (NUMBERS.get(i).contains(query)) {
                addThis = true;
            } else if (!query.equals(".") && DATES.get(i).contains(uri.getLastPathSegment())) {
                addThis = true;
            } else {
                for (String q : querySet) {
                    if (titlesLowercase.get(i).contains(q)) {
                        addThis = true;
                        break;
                    }
                }
            }

            if (addThis) {
                MatrixCursor.RowBuilder builder = cursor.newRow();
                builder.add(i); // BaseColumns._ID
                builder.add(TITLES.get(i)); // SearchManager.SUGGEST_COLUMN_TEXT_1
                builder.add("broj " + NUMBERS.get(i) + ", " + DATES.get(i)); // SearchManager.SUGGEST_COLUMN_TEXT_2
                builder.add(Integer.toString(i)); // SearchManager.SUGGEST_COLUMN_INTENT_EXTRA_DATA
            }
        }
        return cursor;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return null;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues contentValues) {
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String s, @Nullable String[] strings) {
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues contentValues, @Nullable String s, @Nullable String[] strings) {
        return 0;
    }
}
