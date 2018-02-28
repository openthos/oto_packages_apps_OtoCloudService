package com.openthos.seafile;

import android.app.Activity;
import android.app.Service;
import android.app.WallpaperManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.IBinder;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class SeafileService extends Service {
    private StartSeafileThread mStartSeafileThread;
    public SeafileAccount mAccount;
    public SeafileUtils.SeafileSQLConsole mConsole;
    public String mLibrary;
    private boolean mIsStart = false;
    private File mCloudFolder;
    private Timer mTimer;
    private SaveSettingsTask mSettingsTask;
    private boolean mIsTimer;
    private boolean mWallpaper, mWifi, mEmail, mAppdata, mStartupmenu, mBrowser, mAppstore;
    private boolean mImportBusy, mExportBusy;
    private String mUserPath;

    private static final String SYSTEM_PATH_WALLPAPER = "data/system/users/0/wallpaper";
    private static final String SYSTEM_PATH_WIFI = "data/misc/wifi";
    private static final String SYSTEM_PATH_WIFI_INFO = "data/misc/wifi/wpa_supplicant.conf";
    private static final String SYSTEM_PATH_EMAIL = "data/data/com.android.email";
    private static final String SYSTEM_PATH_PREFS = SYSTEM_PATH_EMAIL + "/shared_prefs";
    private static final String SYSTEM_PATH_STATUSBAR_DB =
                                "data/data/com.android.systemui/databases/Status_bar_database.db";
    private static final String SYSTEM_PATH_BROWSER = "data/data/org.mozilla.fennec_root/files";
    private static final String SYSTEM_PATH_BROWSER_INFO =
                                "data/data/org.mozilla.fennec_root/files/mozilla";
    private static final String SYSTEM_PATH_APPSTORE = "data/data/com.openthos.appstore/";

    private static final String SEAFILE_PATH_WALLPAPER = "/cloudFolder/wallpaper";
    private static final String SEAFILE_PATH_WIFI = "/cloudFolder/wifi";
    private static final String SEAFILE_PATH_WIFI_INFO = "/cloudFolder/wifi/wpa_supplicant.conf";
    private static final String SEAFILE_PATH_WALLPAPER_IMAGE =
                                SEAFILE_PATH_WALLPAPER + "/wallpaper";
    private static final String SEAFILE_PATH_EMAIL = "/cloudFolder/email";
    private static final String SEAFILE_PATH_PREFS = SEAFILE_PATH_EMAIL + "/shared_prefs";
    private static final String SEAFILE_PATH_STARTUPMENU = "/cloudFolder/startupmenu";
    private static final String SEAFILE_PATH_STATUSBAR_DB =
                         SEAFILE_PATH_STARTUPMENU + "/Status_bar_database.db";
    private static final String SEAFILE_PATH_BROWSER = "/cloudFolder/browser";
    private static final String SEAFILE_PATH_BROWSER_INFO = "/cloudFolder/browser/mozilla";
    private static final String SEAFILE_PATH_APPSTORE = "/cloudFolder/appstore";
    private static final String SEAFILE_PATH_APPSTORE_PKGNAME =
                                "/cloudFolder/appstore/appPkgNames.txt";
    private static final String ROOT_COMMOND = "chmod -R 777 ";
    private static final String TAG = "CloudServiceFragment";
    private boolean DEBUG = false;

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
        mUserPath = SeafileUtils.SEAFILE_DATA_PATH_REAlLY + "/" + SeafileUtils.mUserId;
        mCloudFolder = new File(mUserPath + SeafileUtils.SETTING_SEAFILE_PROOT_PATH);
        mStartSeafileThread = new StartSeafileThread();
        mStartSeafileThread.start();
        startTimer();
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
            SeafileUtils.sync(settingId, "/" + SeafileUtils.mUserId +
                    SeafileUtils.SETTING_SEAFILE_PROOT_PATH);
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
            if (mImportBusy || !mCloudFolder.exists()) {
                return;
            }
            if (mExportBusy) {
                Toast.makeText(SeafileService.this, getResources().
                        getString(R.string.export_busy_warn), Toast.LENGTH_SHORT).show();
                return;
            }
            mImportBusy = true;
            if (wallpaper) {
                importWallpaperFiles();
            }
            if (wifi) {
                importWifiFiles();
            }
            if (email) {
                importEmailFiles();
            }
            if (startupmenu) {
                importStatusBarFiles();
            }
            if (browser) {
                importBrowserFiles();
            }
            if (appstore) {
                ArrayList<String> pkgNames = new ArrayList();
                File file = new File("SEAFILE_PATH_APPSTORE_PKGNAME");
                try {
                    BufferedReader appReader = new BufferedReader(
                            new FileReader(mUserPath + SEAFILE_PATH_APPSTORE_PKGNAME));
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
            mImportBusy = false;
            Toast.makeText(SeafileService.this, getResources().
                    getString(R.string.import_reboot_info_warn), Toast.LENGTH_SHORT).show();
        }

        public void saveSettings(boolean wallpaper, boolean wifi, boolean email, boolean appdata,
                boolean startupmenu, boolean browser, boolean appstore) {
            if (mExportBusy) {
                return;
            }
            if (mImportBusy) {
                Toast.makeText(SeafileService.this, getResources().
                        getString(R.string.import_busy_warn), Toast.LENGTH_SHORT).show();
                return;
            }
            mExportBusy = true;
            mWallpaper = wallpaper;
            mWifi = wifi;
            mEmail = email;
            mAppdata = appdata;
            mStartupmenu = startupmenu;
            mBrowser = browser;
            mAppstore = appstore;
            if (mIsTimer) {
                if (!mCloudFolder.exists()) {
                    mCloudFolder.mkdirs();
                }
                if (wallpaper) {
                    File wallpaperSeafile = new File(mUserPath + SEAFILE_PATH_WALLPAPER);
                    if (!wallpaperSeafile.exists()) {
                        wallpaperSeafile.mkdirs();
                    }
                    exportWallpaperFiles();
                }
                if (wifi) {
                    File wifiInfoSeafile = new File(mUserPath + SEAFILE_PATH_WIFI);
                    if (!wifiInfoSeafile.exists()) {
                        wifiInfoSeafile.mkdirs();
                    }
                    exportWifiFiles();
                }
                if (email) {
                    File emailFile = new File (mUserPath + SEAFILE_PATH_EMAIL);
                    if (emailFile.exists()) {
                        FileUtils.deleteGeneralFile(mUserPath + SEAFILE_PATH_EMAIL);
                    }
                    emailFile.mkdirs();
                    exportEmailFiles();
                }
                if (startupmenu) {
                    File startupMenuFile = new File (mUserPath + SEAFILE_PATH_STARTUPMENU);
                    if (startupMenuFile.exists()) {
                        FileUtils.deleteGeneralFile(mUserPath + SEAFILE_PATH_STARTUPMENU);
                    }
                    startupMenuFile.mkdirs();
                    exportStartupmenuFiles();
                }
                if (browser) {
                    File browserInfoSeafile = new File(mUserPath + SEAFILE_PATH_BROWSER);
                    if (!browserInfoSeafile.exists()) {
                        browserInfoSeafile.mkdirs();
                    }
                    exportBrowserFiles();
                }
                if (appstore) {
                    File appstoreDirSeafile = new File(mUserPath + SEAFILE_PATH_APPSTORE);
                    if (!appstoreDirSeafile.exists()) {
                        appstoreDirSeafile.mkdirs();
                    }
                    File appstoreSeafile = new File(mUserPath + SEAFILE_PATH_APPSTORE_PKGNAME);
                    if (!appstoreSeafile.exists()) {
                        try {
                            appstoreSeafile.createNewFile();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    exportAppstoreFiles();
                }
            } else {
                stopTimer();
                startTimer();
            }
            mIsTimer = false;
            mExportBusy = false;
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    private void importWallpaperFiles() {
        try {
            WallpaperManager.getInstance(this).setStream(
                    new FileInputStream(mUserPath + SEAFILE_PATH_WALLPAPER_IMAGE));
        } catch (IOException exception) {
            try {
                WallpaperManager.getInstance(this).setResource(
                                  com.android.internal.R.drawable.default_wallpaper);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void importEmailFiles() {
        File emailFiles = new File(mUserPath + SEAFILE_PATH_EMAIL);
        if (emailFiles.exists()){
            File emailNewDevicePrefs = new File(SYSTEM_PATH_PREFS);
            if (emailNewDevicePrefs.exists()) {
                SeafileUtils.exec(ROOT_COMMOND + SYSTEM_PATH_PREFS);
                FileUtils.deleteGeneralFile(SYSTEM_PATH_PREFS);
            }
            SeafileUtils.exec(ROOT_COMMOND + SYSTEM_PATH_EMAIL);
            SeafileUtils.exec(ROOT_COMMOND + mUserPath + SEAFILE_PATH_PREFS);
            if (FileUtils.copyGeneralFile(mUserPath + SEAFILE_PATH_PREFS, SYSTEM_PATH_EMAIL)) {
                if (DEBUG) Log.i(TAG,"seafile email sync to new device sucessful!");
            } else {
                if (DEBUG) Log.i(TAG,"seafile email sync to new device fail!");
            }
        }
    }

    private void importStatusBarFiles() {
        File statusbarDbFiles = new File(SEAFILE_PATH_STATUSBAR_DB);
        if (statusbarDbFiles.exists()) {
            SQLiteDatabase statusbarDb = SQLiteDatabase.openDatabase(
                          SEAFILE_PATH_STATUSBAR_DB, null, SQLiteDatabase.OPEN_READWRITE);
            Cursor cursor = statusbarDb.rawQuery("select * from status_bar_tb", null);
            if (cursor != null) {
                List<PackageInfo> pkgInfos = getPackageManager().getInstalledPackages(0);
                ArrayList<String> pkgNameLists = new ArrayList();
                while(cursor.moveToNext()) {
                    String pkgName = cursor.getString(cursor.getColumnIndex("pkgname"));
                    for (PackageInfo pkgInfo : pkgInfos) {
                        if (pkgInfo.packageName.equals(pkgName)) {
                            pkgNameLists.add(pkgName);
                        }
                    }
                }
                Intent intent = new Intent(Intent.STATUS_BAR_SEAFILE);
                intent.putStringArrayListExtra("pkgname", pkgNameLists);
                sendBroadcast(intent);
            }
        }
    }

    private void importWifiFiles() {
        SeafileUtils.exec(ROOT_COMMOND + mUserPath + SEAFILE_PATH_WIFI_INFO);
        SeafileUtils.exec(ROOT_COMMOND + SYSTEM_PATH_WIFI);
        SeafileUtils.exec("cp -f " + mUserPath + SEAFILE_PATH_WIFI_INFO +
                          " " + SYSTEM_PATH_WIFI);
    }

    private void importBrowserFiles() {
        SeafileUtils.exec(ROOT_COMMOND + SYSTEM_PATH_BROWSER);
        SeafileUtils.exec(ROOT_COMMOND + mUserPath + SEAFILE_PATH_BROWSER_INFO);
        SeafileUtils.exec("cp -rf " + mUserPath + SEAFILE_PATH_BROWSER_INFO +
                " " + SYSTEM_PATH_BROWSER);
    }

    private void exportWallpaperFiles() {
        SeafileUtils.exec(ROOT_COMMOND + SYSTEM_PATH_WALLPAPER);
        SeafileUtils.exec(ROOT_COMMOND + mUserPath + SEAFILE_PATH_WALLPAPER);
        SeafileUtils.exec("cp -f " + SYSTEM_PATH_WALLPAPER + " " + mUserPath + SEAFILE_PATH_WALLPAPER);
    }

    private void exportStartupmenuFiles() {
        File statusbarDb = new File(SYSTEM_PATH_STATUSBAR_DB);
        if (statusbarDb.exists()) {
            SeafileUtils.exec(ROOT_COMMOND + SYSTEM_PATH_STATUSBAR_DB);
            if (FileUtils.copyGeneralFile(SYSTEM_PATH_STATUSBAR_DB,
                    mUserPath + SEAFILE_PATH_STARTUPMENU)) {
                if (DEBUG) Log.i(TAG,"statusbar sync to seafile sucessful!");
            } else {
                if (DEBUG) Log.i(TAG,"statusbar sync to seafile fail!");
            }
        }
    }

    private void exportEmailFiles() {
        File emailSharedPrefs = new File(SYSTEM_PATH_PREFS);
        if (emailSharedPrefs.exists()) {
            SeafileUtils.exec(ROOT_COMMOND + SYSTEM_PATH_PREFS);
            SeafileUtils.exec(ROOT_COMMOND + mUserPath + SEAFILE_PATH_EMAIL);
            if (FileUtils.copyGeneralFile(SYSTEM_PATH_PREFS, mUserPath + SEAFILE_PATH_EMAIL)) {
                if (DEBUG) Log.i(TAG,"email sync to seafile sucessful!");
            } else {
                if (DEBUG) Log.i(TAG,"email sync to seafile fail!");
            }
        }
    }

    private void exportWifiFiles() {
        SeafileUtils.exec(ROOT_COMMOND + SYSTEM_PATH_WIFI_INFO);
        SeafileUtils.exec(ROOT_COMMOND + mUserPath + SEAFILE_PATH_WIFI);
        SeafileUtils.exec("cp -f " + SYSTEM_PATH_WIFI_INFO + " " +
                mUserPath + SEAFILE_PATH_WIFI);
    }

    private void exportBrowserFiles() {
        SeafileUtils.exec(ROOT_COMMOND + SYSTEM_PATH_BROWSER_INFO);
        SeafileUtils.exec(ROOT_COMMOND + mUserPath + SEAFILE_PATH_BROWSER);
        SeafileUtils.exec("cp -rf "+SYSTEM_PATH_BROWSER_INFO + " " +
                mUserPath + SEAFILE_PATH_BROWSER);
    }

    private void exportAppstoreFiles() {
        List<PackageInfo> pkgInfos = getPackageManager().getInstalledPackages(0);
        try {
            BufferedWriter appWriter = new BufferedWriter(
                    new FileWriter(mUserPath + SEAFILE_PATH_APPSTORE_PKGNAME));
            for (PackageInfo pkgInfo : pkgInfos) {
                appWriter.write(pkgInfo.packageName);
                appWriter.newLine();
                appWriter.flush();
            }
            appWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void startTimer() {
        mTimer = new Timer();
        mSettingsTask = new SaveSettingsTask();
        mTimer.schedule(mSettingsTask, 0, 3600000);
    }

    private void stopTimer() {
        if (mTimer != null) {
            mTimer.cancel();
            mTimer = null;
        }
        if (mSettingsTask != null) {
            mSettingsTask.cancel();
            mSettingsTask = null;
        }
    }

    private class SaveSettingsTask extends TimerTask {
        @Override
        public void run() {
            mIsTimer = true;
            try {
                mBinder.saveSettings(mWallpaper, mWifi, mEmail, mAppdata,
                                     mStartupmenu, mBrowser, mAppstore);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }
}
