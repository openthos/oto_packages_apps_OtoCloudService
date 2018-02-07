package com.openthos.seafile;

import android.app.Activity;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.IBinder;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

public class SeafileService extends Service {
    private StartSeafileThread mStartSeafileThread;
    public SeafileAccount mAccount;
    public SeafileUtils.SeafileSQLConsole mConsole;
    public String mLibrary;
    private boolean mIsStart = false;

    private static final String APPSTORE_PKGNAME_SEAFILE_PATH =
                         "/cloudFolder/appstore/appPkgNames.txt";

    @Override
    public void onCreate() {
        super.onCreate();
        SeafileUtils.init();
    }

    private void initData() {
        ContentResolver mResolver = SeafileService.this.getContentResolver();
        Uri uriQuery = Uri.parse(SeafileUtils.OPENTHOS_URI);
        Cursor cursor = mResolver.query(uriQuery, null, null, null, null);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                //current openthos id and password
                SeafileUtils.mUserId = cursor.getString(cursor.getColumnIndex("openthosID"));
                SeafileUtils.mUserPassword =
                        cursor.getString(cursor.getColumnIndex("password"));
                break;
            }
            cursor.close();
        }
        if (TextUtils.isEmpty(SeafileUtils.mUserId)
                || TextUtils.isEmpty(SeafileUtils.mUserPassword)) {
            return;
        }
        mStartSeafileThread = new StartSeafileThread();
        mStartSeafileThread.start();
    }

    private class StartSeafileThread extends Thread {
        private boolean isExistsSetting = false;
        private boolean isExistsFileManager = false;
        private String id = "";
        private String settingId = "";

        @Override
        public void run() {
            super.run();
            SeafileUtils.start();
            mLibrary = getLibrarys();
            mAccount = new SeafileAccount();
            mAccount.mUserName = SeafileUtils.mUserId;
            mConsole = new SeafileUtils.SeafileSQLConsole(SeafileService.this);
            mAccount.mUserId = mConsole.queryAccountId(mAccount.mUserName);
            try {
                if (mLibrary == null || TextUtils.isEmpty(mLibrary)) {
                    mLibrary = getSharedPreferences(SeafileUtils.SEAFILE_DATA,
                            Context.MODE_PRIVATE).getString(SeafileUtils.SEAFILE_DATA, "");
                    isExistsFileManager = true;
                }
                JSONArray jsonArray = new JSONArray(mLibrary);
                JSONObject jsonObject = null;
                for (int i = 0; i < jsonArray.length(); i++) {
                    SeafileLibrary seafileLibrary = new SeafileLibrary();
                    jsonObject = jsonArray.getJSONObject(i);
                    seafileLibrary.libraryName = jsonObject.getString("name");
                    seafileLibrary.libraryId = jsonObject.getString("id");
                    if (seafileLibrary.libraryName.equals(SeafileUtils.SETTING_SEAFILE_NAME)) {
                        isExistsSetting = true;
                        settingId = seafileLibrary.libraryId;
                        continue;
                    }
                    if (!seafileLibrary.libraryName.equals(SeafileUtils.FILEMANAGER_SEAFILE_NAME)) {
                        continue;
                    }
                    isExistsFileManager = true;
                    id = seafileLibrary.libraryId;
                    mAccount.mLibrarys.add(seafileLibrary);
                }
                getSharedPreferences(SeafileUtils.SEAFILE_DATA, Context.MODE_PRIVATE).edit()
                        .putString(SeafileUtils.SEAFILE_DATA, mLibrary).commit();
            } catch (JSONException e) {
                e.printStackTrace();
            }
            if (!isExistsFileManager) {
                SeafileLibrary seafileLibrary = new SeafileLibrary();
                seafileLibrary.libraryName = SeafileUtils.FILEMANAGER_SEAFILE_NAME;
                seafileLibrary.libraryId
                        = SeafileUtils.create(SeafileUtils.FILEMANAGER_SEAFILE_NAME);
                mAccount.mLibrarys.add(seafileLibrary);
            }
            if (mAccount.mLibrarys.size() > 0) {
                for (SeafileLibrary seafileLibrary : mAccount.mLibrarys) {
                    String name = seafileLibrary.libraryName;
                    int isSync = mConsole.queryFile(mAccount.mUserId,
                            seafileLibrary.libraryId, seafileLibrary.libraryName);
                    seafileLibrary.isSync = isSync;
                    if (isSync == SeafileUtils.SYNC) {
                        SeafileUtils.sync(seafileLibrary.libraryId,
                                "/" + SeafileUtils.mUserId + "/" + seafileLibrary.libraryName);
                    }
                }
            }
            if (!isExistsSetting) {
                settingId = SeafileUtils.create(SeafileUtils.SETTING_SEAFILE_NAME);
            }
            SeafileUtils.sync(settingId, "/" + SeafileUtils.mUserId +
                    SeafileUtils.SETTING_SEAFILE_PROOT_PATH);
            mIsStart = true;
        }
    }

    private String getLibrarys() {
        if (!SeafileUtils.isNetworkOn(this)) {
            return "";
        }
        String token = SeafileUtils.getToken(this);
        if (token == null || TextUtils.isEmpty(token)) {
            return "";
        }
        return SeafileUtils.getResult(token);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        NetworkReceiver networkReceiver = new NetworkReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(networkReceiver, intentFilter);
        return super.onStartCommand(intent, flags, startId);
    }

    private class NetworkReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case ConnectivityManager.CONNECTIVITY_ACTION:
                    ConnectivityManager manager = (ConnectivityManager) context
                            .getSystemService(Context.CONNECTIVITY_SERVICE);
                    NetworkInfo activeNetwork = manager.getActiveNetworkInfo();
                    if (activeNetwork != null) {
                        if (activeNetwork.isConnected() && !mIsStart) {
                            initData();
                        }
                    }
                    break;
            }
        }
    }

    private ISeafileService.Stub mBinder = new ISeafileService.Stub() {

        public void sync(String libraryId, String libraryName, String filePath) {
            mConsole.updateSync(0, libraryId, libraryName, SeafileUtils.SYNC);
            SeafileUtils.sync(libraryId, filePath);
        }

        public void desync(String libraryId, String libraryName, String filePath) {
            mConsole.updateSync(0, libraryId, libraryName, SeafileUtils.UNSYNC);
            SeafileUtils.desync(filePath);
        }

        public String getLibrary() {
            return mLibrary;
        }

        public int getUserId() {
            return mAccount.mUserId;
        }

        public String getUserName() {
            return SeafileUtils.mUserId;
        }

        public String getUserPassword() {
            return SeafileUtils.mUserPassword;
        }

        public int isSync(String libraryId, String libraryName) {
            return mConsole.queryFile(mAccount.mUserId, libraryId, libraryName);
        }

        public void updateAccount() {
            initData();
        }

        public void stopAccount() {
            SeafileUtils.stop();
            mStartSeafileThread = null;
        }

        public void restoreSettings(boolean wallpaper, boolean wifi, boolean email, boolean appdata,
                boolean startupmenu, boolean browser, boolean appstore) {
            if (appstore) {
                ArrayList<String> pkgNames = new ArrayList();
                File file = new File("APPSTORE_PKGNAME_SEAFILE_PATH");
                try {
                    BufferedReader appReader = new BufferedReader(
                            new FileReader(SeafileUtils.SEAFILE_DATA_PATH_REAlLY + "/" +
                                    SeafileUtils.mUserId + APPSTORE_PKGNAME_SEAFILE_PATH));
                    String line = null;
                    while ((line = appReader.readLine()) != null) {
                        pkgNames.add(line);
                    }
                    appReader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                // download apps from appstore
                Intent intent = new Intent();
                intent.setComponent(new ComponentName("com.openthos.appstore",
                        "com.openthos.appstore.download.DownloadService"));
                intent.putStringArrayListExtra("packageNames", pkgNames);
                startService(intent);
            }
        }

        public void saveSettings(boolean wallpaper, boolean wifi, boolean email, boolean appdata,
                boolean startupmenu, boolean browser, boolean appstore) {}
    };

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
}
