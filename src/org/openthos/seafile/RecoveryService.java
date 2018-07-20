package org.openthos.seafile;

import android.app.Service;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.HashMap;
import java.util.Map;

public class RecoveryService extends Service {
    private static final String SYSTEM_PATH_DATA = "/data/data/";
    private static final String SYSTEM_PATH_WALLPAPER = "/data/system/users/0/wallpaper";
    private static final String SYSTEM_PATH_WIFI = "/data/misc/wifi";
    private static final String SYSTEM_PATH_WIFI_INFO = "/data/misc/wifi";
    private static final String SYSTEM_PATH_STATUSBAR = "com.android.systemui";
    private static final String SEAFILE_PATH_WALLPAPER = "/wallpaper";
    private static final String SEAFILE_PATH_APPSTORE = "/app_pkg_names";
    private static final String SEAFILE_PATH_WIFI = "/wifi.tar.gz";
    private static final String SEAFILE_PATH_WIFI_INFO = "/wifi/wpa_supplicant.conf";
    private static final String SEAFILE_PATH_BROWSER = "/browser/";
    private static final String SEAFILE_PATH_APPDATA = "/appdata/";
    private static final String SEAFILE_PATH_STARTUPMENU = "/UserConfig/startupmenu";
    private static final String SEAFILE_PATH_STATUSBAR_DB =
                         SEAFILE_PATH_STARTUPMENU + "/Status_bar_database.db";
    private boolean mWallpaper, mWifi, mAppdata, mStartupmenu, mBrowser, mAppstore;
    private List<ResolveInfo> mAllAppdataList = new ArrayList();
    private List<ResolveInfo> mAllBrowserList = new ArrayList();
    private List<ResolveInfo> mImportList = new ArrayList();
    private List<String> mSyncAppdata = new ArrayList();
    private List<String> mSyncBrowsers = new ArrayList();
    private Intent mAppdataIntent;
    private Intent mBrowserIntent;
    private boolean mImportBusy, mExportBusy;
    private boolean mTempStartupMenu = false;
    private ServiceBinder mBinder = new ServiceBinder();
    private PackageManager mPackageManager;
    private Timer mTimer;
    private AutoBackupTask mAutoTask;
    private boolean mIsTimer;
    public static String mConfigPath;

    @Override
    public void onCreate() {
        super.onCreate();
        mPackageManager = getPackageManager();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public class ServiceBinder extends Binder {

        public RecoveryService getService() {
            return RecoveryService.this;
        }
    }

    private void initAppIntent() {
        mBrowserIntent = new Intent(Intent.ACTION_VIEW);
        mBrowserIntent.addCategory(Intent.CATEGORY_BROWSABLE);
        Uri uri = Uri.parse("http://");
        mBrowserIntent.setData(uri);
        mAppdataIntent = new Intent(Intent.ACTION_MAIN, null);
        mAppdataIntent.addCategory(Intent.CATEGORY_LAUNCHER);
    }

    public List<ResolveInfo> getAppsInfo(int tag) {
        initAppIntent();
        mAllBrowserList = mPackageManager.queryIntentActivities(
                mBrowserIntent, PackageManager.GET_INTENT_FILTERS);
        mAllAppdataList = mPackageManager.queryIntentActivities(mAppdataIntent, 0);
        mAllAppdataList.removeAll(mAllBrowserList);
        mImportList.clear();
        switch (tag) {
            case SeafileUtils.TAG_APPDATA_EXPORT:
                mAllAppdataList.removeAll(mAllBrowserList);
                return mAllAppdataList;
            case  SeafileUtils.TAG_APPDATA_IMPORT:
                String appName = null;
                for (String name : SeafileUtils.execCommand("ls " +
                        mConfigPath + SEAFILE_PATH_APPDATA)) {
                    appName = name.replace(".tar.gz", "");
                    for (ResolveInfo info : mAllAppdataList) {
                        if (appName.equals(info.activityInfo.packageName)) {
                            mImportList.add(info);
                            break;
                        }
                    }
                }
                return mImportList;
            case  SeafileUtils.TAG_BROWSER_EXPORT:
                return mAllBrowserList;
            case  SeafileUtils.TAG_BROWSER_IMPORT:
                String browserName = null;
                for (String name : SeafileUtils.execCommand("ls " +
                        mConfigPath + SEAFILE_PATH_BROWSER)) {
                    browserName = name.replace(".tar.gz", "");
                    for (ResolveInfo info : mAllBrowserList) {
                        if (browserName.equals(info.activityInfo.packageName)) {
                            mImportList.add(info);
                            break;
                        }
                    }
                }
                return mImportList;
        }
        return null;
    }

    public void restoreSettings(boolean wallpaper, boolean wifi,
            boolean appdata, List<String> syncAppdata, boolean startupmenu,
            boolean browser, List<String> syncBrowsers, boolean appstore) {
        if (mImportBusy) {
            return;
        }
        if (mExportBusy) {
            return;
        }
        mImportBusy = true;
        mTempStartupMenu = startupmenu;
        if (wallpaper) {
            importWallpaperFiles();
        }
        if (wifi) {
            importWifiFiles();
        }
        if (appdata) {
            importFiles(syncAppdata,  SEAFILE_PATH_APPDATA);
        }
        if (browser) {
            importFiles(syncBrowsers, SEAFILE_PATH_BROWSER);
        }
        if (appstore) {
            ArrayList<String> pkgNames = new ArrayList();
            File file = new File("SEAFILE_PATH_APPSTORE_PKGNAME");
            BufferedReader appReader = null;
            try {
                String path = mConfigPath + SEAFILE_PATH_APPSTORE;
                SeafileUtils.exec("busybox chmod 777 " + path);
                appReader = new BufferedReader(new FileReader(path));
                String line = null;
                while ((line = appReader.readLine()) != null) {
                    pkgNames.add(line);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (appReader != null) {
                    try {
                        appReader.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            // download apps from appstore
            Intent intent = new Intent();
            intent.setComponent(new ComponentName("org.openthos.appstore",
                    "org.openthos.appstore.download.DownloadService"));
            intent.putStringArrayListExtra("packageNames", pkgNames);
            startService(intent);
        }
        mImportBusy = false;
    }

    public void saveSettings(boolean wallpaper, boolean wifi,
            boolean appdata, List<String> syncAppdata, boolean startupmenu,
            boolean browser, List<String> syncBrowsers, boolean appstore) {
        if (mExportBusy) {
            return;
        }
        if (mImportBusy) {
            return;
        }
        mExportBusy = true;
        mWallpaper = wallpaper;
        mWifi = wifi;
        mAppdata = appdata;
        mStartupmenu = startupmenu;
        mBrowser = browser;
        mAppstore = appstore;
        mSyncBrowsers = syncBrowsers;
        mSyncAppdata = syncAppdata;
        if (mIsTimer) {
            if (wallpaper) {
                exportWallpaperFiles();
            }
            if (wifi) {
                exportWifiFiles();
            }
            if (appdata) {
                exportFiles(syncAppdata, SEAFILE_PATH_APPDATA);
            }
            if (startupmenu) {
                List<String> packages = new ArrayList();
                packages.add(SYSTEM_PATH_STATUSBAR);
                exportFiles(packages, "/");
            }
            if (browser) {
                exportFiles(syncBrowsers, SEAFILE_PATH_BROWSER);
            }
            if (appstore) {
                String path = mConfigPath + SEAFILE_PATH_APPSTORE;
                if (SeafileUtils.checkFile(path)) {
                    SeafileUtils.exec("rm " + path);
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

    private void exportWallpaperFiles() {
        String path =  mConfigPath + SEAFILE_PATH_WALLPAPER;
        if (SeafileUtils.checkFile(SYSTEM_PATH_WALLPAPER)) {
            SeafileUtils.exec("cp -f " + SYSTEM_PATH_WALLPAPER + " "
                    + mConfigPath + SEAFILE_PATH_WALLPAPER);
        } else {
            SeafileUtils.exec("rm -r " + mConfigPath + SEAFILE_PATH_WALLPAPER);
        }
    }

    private void importWallpaperFiles() {
        String path =  mConfigPath  + SEAFILE_PATH_WALLPAPER;
        if (SeafileUtils.checkFile(path)) {
            SeafileUtils.exec("busybox chmod 777 " + path);
            try {
                WallpaperManager.getInstance(this).setStream(new FileInputStream(path));
            } catch (IOException exception) {
                exception.printStackTrace();
            }
        }
    }

    private void importWifiFiles() {
        String path = mConfigPath + SEAFILE_PATH_WIFI;
        if (SeafileUtils.checkFile(path)) {
            WifiManager wifiManager = (WifiManager) (getSystemService(Context.WIFI_SERVICE));
            if (wifiManager.isWifiEnabled()) {
                wifiManager.setWifiEnabled(false);
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            SeafileUtils.untarFile(path);
            if (!wifiManager.isWifiEnabled()) {
                wifiManager.setWifiEnabled(true);
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void exportWifiFiles() {
        SeafileUtils.tarFile(SYSTEM_PATH_WIFI_INFO,
                mConfigPath + SEAFILE_PATH_WIFI);
    }

    private void exportAppstoreFiles() {
        List<PackageInfo> tempInfos = getPackageManager().getInstalledPackages(0);
        List<PackageInfo> packageInfos = new ArrayList<>();
        for (PackageInfo f : tempInfos) {
            if ((f.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                packageInfos.add(f);
            }
        }
        BufferedWriter appWriter = null;
        try {
            String path = mConfigPath + SEAFILE_PATH_APPSTORE;
            if (packageInfos.size() > 0) {
                SeafileUtils.exec("echo > " + path + ";busybox chmod 777 " + path);
                appWriter = new BufferedWriter(new FileWriter(path));
                for (PackageInfo f :packageInfos){
                    appWriter.write(f.packageName);
                    appWriter.newLine();
                    appWriter.flush();
                }
            }
            if (appWriter != null) {
                appWriter.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (appWriter != null) {
                try {
                    appWriter.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void importFiles(List<String> packages, String configPath) {
        for (int i = 0; i < packages.size(); i++) {
            HashMap<String, String> map = SeafileUtils.execCommands("ls -l /data/data");
            String uid = map.get(packages.get(i));
            if (!TextUtils.isEmpty(uid)) {
                SeafileUtils.untarFile(mConfigPath +
                        configPath + packages.get(i) + ".tar.gz");
                SeafileUtils.chownFile(mConfigPath +
                        configPath + packages.get(i), uid);
            }
        }
    }

    private void exportFiles(List<String> packages, String configPath) {
        for (int i = 0; i < packages.size(); i++) {
            File configFile = new File(mConfigPath + configPath);
            if (!configFile.exists()) {
                configFile.mkdirs();
            }
            SeafileUtils.tarFile(SYSTEM_PATH_DATA + packages.get(i),
                    configFile.getAbsolutePath() + "/" + packages.get(i) + ".tar.gz");
        }
    }

    public void startTimer() {
        mTimer = new Timer();
        mAutoTask = new AutoBackupTask();
        mTimer.schedule(mAutoTask, 0, 3600000);
    }

    public void stopTimer() {
        if (mTimer != null) {
            mTimer.cancel();
            mTimer = null;
        }
        if (mAutoTask != null) {
            mAutoTask.cancel();
            mAutoTask = null;
        }
    }

    private class AutoBackupTask extends TimerTask {
        @Override
        public void run() {
            mIsTimer = true;
            saveSettings(mWallpaper, mWifi, mAppdata, mSyncAppdata,
                    mStartupmenu, mBrowser, mSyncBrowsers, mAppstore);
        }
    }
}
