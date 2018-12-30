package org.mg94c18.alanford;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

public class PlayIntentMaker {
    public static Intent createPlayIntent(Context context) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(
                "https://play.google.com/store/apps/details?id=" + context.getPackageName()));
        intent.setPackage("com.android.vending");
        return intent;
    }
}
