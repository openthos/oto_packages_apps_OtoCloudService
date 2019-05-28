package org.openthos.seafile;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

public abstract class BaseService extends Service {

    @Override
    public void onCreate() {
        super.onCreate();
    }

    public static void startService(Context context, Intent intent) {
        context.startService(intent);
    }
}
