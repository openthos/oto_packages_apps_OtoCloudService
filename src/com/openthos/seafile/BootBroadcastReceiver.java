package com.openthos.seafile;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent i) {
        if (i.getAction().equals(Intent.ACTION_BOOT_COMPLETED)){
            Intent intent = new Intent();
            intent.setClassName("com.openthos.seafile", "com.openthos.seafile.SeafileService");
            context.startService(intent);
        }
    }
}
