package org.openthos.seafile;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent i) {
        if (i.getAction().equals(Intent.ACTION_BOOT_COMPLETED)){
            Intent intent = new Intent();
            intent.setClassName("org.openthos.seafile", "org.openthos.seafile.SeafileService");
            context.startService(intent);

            Intent intents = new Intent();
            intents.setClassName("org.openthos.seafile", "org.openthos.seafile.RecoveryService");
            context.startService(intents);
        }
    }
}
