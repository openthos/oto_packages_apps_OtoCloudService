package org.openthos.seafile;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.app.WallpaperManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.PackageParser;
import android.content.pm.PackageUserState;
import android.content.pm.ResolveInfo;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.FileObserver;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Parcel;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Date;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class SeafileService extends Service {
    private static final String SYSTEM_PATH_DATA = "/data/data/";
    private static final String SYSTEM_PATH_WALLPAPER = "/data/system/users/0/wallpaper";
    private static final String SYSTEM_PATH_WIFI = "/data/misc/wifi";
    private static final String SYSTEM_PATH_WIFI_INFO = "/data/misc/wifi";
    private static final String SYSTEM_PATH_STATUSBAR = "com.android.systemui";

    private static final String SEAFILE_PATH_WALLPAPER = "/wallpaper";
    private static final String SEAFILE_PATH_WIFI = "/wifi.tar.gz";
    private static final String SEAFILE_PATH_WIFI_INFO = "/wifi/wpa_supplicant.conf";
    private static final String SEAFILE_PATH_APPSTORE = "/app_pkg_names";
    private static final String SEAFILE_PATH_STARTUPMENU = "/UserConfig/startupmenu";
    private static final String SEAFILE_PATH_STATUSBAR_DB =
                         SEAFILE_PATH_STARTUPMENU + "/Status_bar_database.db";
    private static final String SEAFILE_PATH_BROWSER = "/browser/";
    private static final String SEAFILE_PATH_APPDATA = "/appdata/";

    private static final String DESCRIPTOR = "org.openthos.seafile.ISeafileService";
    private static final String APPSTORE_DOWNLOAD_PATH
            = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            .getAbsolutePath() + "/app";

    private static final int INIT_NOTIFICATION = 40000001;
    private static final int ADD_BINDER = 40000002;
    private static final int REMOVE_BINDER = 40000003;

    private static final int CODE_SEND_INTO = 80000001;
    private static final int CODE_SEND_OUT = 80000002;
    private static final int CODE_RESTORE_FINISH = 80000003;
    private static final int CODE_DOWNLOAD_FINISH = 80000004;
    private static final int CODE_REGIEST_SUCCESS = 80000005;
    private static final int CODE_REGIEST_FAILED = 80000006;
    private static final int CODE_LOGIN_SUCCESS = 80000007;
    private static final int CODE_LOGIN_FAILED = 80000008;

    private static final long TIMER_SHORT = 1000;
    private static final long TIMER_MEDIUM= TIMER_SHORT * 10;
    private static final long TIMER_LONG = TIMER_MEDIUM * 10;

    private static final String SEAFILE_STATUS_DOWNLOADING = "downloading";
    private static final String SEAFILE_STATUS_UPLOADING = "uploading";

    private InitLibrarysThread mInitLibrarysThread;
    public SeafileAccount mAccount;
    public SeafileUtils.SeafileSQLConsole mConsole;
    private File mConfigPath;
    private Timer mTimer;
    private AutoBackupTask mAutoTask;
    private boolean mIsTimer;
    private PackageManager mPackageManager;
    private boolean mWallpaper, mWifi, mAppdata, mStartupmenu, mBrowser, mAppstore;
    private List<String> mSyncAppdata = new ArrayList();
    private List<ResolveInfo> mAllAppdataList = new ArrayList();
    private List<ResolveInfo> mAllBrowserList = new ArrayList();
    private List<ResolveInfo> mImportList = new ArrayList();
    private List<String> mSyncBrowsers = new ArrayList();
    private boolean mImportBusy, mExportBusy;
    private String mUserPath;
    private ArrayList<IBinder> mIBinders = new ArrayList();
    private ArrayList<String> mAppNames = new ArrayList();
    private ArrayList<String> mApkPaths = new ArrayList();
    private int mTotalApks, mDownloadApks, mTotal;
    private final PackageParser parser = new PackageParser();
    private SeafileBinder mBinder = new SeafileBinder();
    private boolean DEBUG = false;
    private Handler mHandler;
    private boolean mTempStartupMenu = false;
    private Intent mAppdataIntent;
    private Intent mBrowserIntent;
    private ScheduledExecutorService mScheduledService;
    public static SharedPreferences mSp;

    private Timer mStatusTimer;
    private StatusTask mStatusTask;
    private StatusObserver mDataObserver;
    private StatusObserver mUserConfigObserver;
    private NotificationManager mNotificationManager;
    private Notification.Builder mBuilder;
    private Notification.BigTextStyle mStyle;
    private boolean mIsNotificationShown = false;
    private NetworkReceiver mNetworkReceiver;

    @Override
    public void onCreate() {
        super.onCreate();
        mHandler = new SeafileHandler(Looper.getMainLooper());
        SeafileUtils.init();
        mSp = getSharedPreferences("account",Context.MODE_PRIVATE);
        mPackageManager = getPackageManager();
        initAppIntent();
        initAccount(mSp.getString("user", ""), mSp.getString("password", ""));
    }

    private void initAccount(String userName, String password) {
        SeafileUtils.mUserId = userName;
        SeafileUtils.mUserPassword = password;
        mUserPath = SeafileUtils.SEAFILE_DATA_ROOT_PATH + "/" + SeafileUtils.mUserId;
        mConfigPath = new File(SeafileUtils.SEAFILE_PROOT_PATH,
                SeafileUtils.mUserId + "/" + SeafileUtils.SETTING_SEAFILE_NAME);
        if (TextUtils.isEmpty(SeafileUtils.mUserId)
                || TextUtils.isEmpty(SeafileUtils.mUserPassword)) {
            return;
        }
        if (mInitLibrarysThread != null) {
            mInitLibrarysThread = null;
        }
        mScheduledService = Executors.newScheduledThreadPool(1);
        mInitLibrarysThread = new InitLibrarysThread();
        mInitLibrarysThread.start();
    }

    private class InitLibrarysThread extends Thread {
        @Override
        public void run() {
            super.run();
            if (!SeafileUtils.isExistsAccount()) {
                mInitLibrarysThread = null;
                return;
            }
            if (!SeafileUtils.isNetworkOn(SeafileService.this)) {
                regiestNetworkReceiver();
                mInitLibrarysThread = null;
                return;
            }
            SeafileUtils.start();
            String library;
            if ((library = getLibrarysSuccess()) == null) {
                postDelayedThread();
                return;
            }
            mAccount = new SeafileAccount();
            mAccount.mUserName = SeafileUtils.mUserId;
            mConsole = new SeafileUtils.SeafileSQLConsole(SeafileService.this);
            mAccount.mUserId = mConsole.queryAccountId(mAccount.mUserName);
            try {
                JSONArray jsonArray = new JSONArray(library);
                JSONObject jsonObject = null;
                for (int i = 0; i < jsonArray.length(); i++) {
                    SeafileLibrary seafileLibrary = new SeafileLibrary();
                    jsonObject = jsonArray.getJSONObject(i);
                    seafileLibrary.libraryName = jsonObject.getString("name");
                    seafileLibrary.libraryId = jsonObject.getString("id");
                    if (seafileLibrary.libraryName.equals(SeafileUtils.SETTING_SEAFILE_NAME)) {
                        mAccount.mSettingLibrary = seafileLibrary;
                        continue;
                    }
                    if (seafileLibrary.libraryName.equals(SeafileUtils.DATA_SEAFILE_NAME)) {
                        mAccount.mDataLibrary = seafileLibrary;
                        continue;
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }

            if (mAccount.mSettingLibrary == null) {
                SeafileLibrary seafileLibrary = new SeafileLibrary();
                seafileLibrary.libraryName = SeafileUtils.SETTING_SEAFILE_NAME;
                seafileLibrary.libraryId
                        = SeafileUtils.create(SeafileUtils.SETTING_SEAFILE_NAME);
                mAccount.mSettingLibrary = seafileLibrary;
            }
            if (!TextUtils.isEmpty(mAccount.mSettingLibrary.libraryId)) {
                if (!mConfigPath.exists()) {
                    mConfigPath.mkdirs();
                }
                mAccount.mSettingLibrary.filePath
                        = "/" + SeafileUtils.mUserId + "/" + SeafileUtils.SETTING_SEAFILE_NAME;
                SeafileUtils.sync(mAccount.mSettingLibrary.libraryId,
                        mAccount.mSettingLibrary.filePath);
                startTimer();
            } else {
                mAccount.mSettingLibrary = null;
            }

            if (mAccount.mDataLibrary == null) {
                SeafileLibrary seafileLibrary = new SeafileLibrary();
                seafileLibrary.libraryName = SeafileUtils.DATA_SEAFILE_NAME;
                seafileLibrary.libraryId
                        = SeafileUtils.create(SeafileUtils.DATA_SEAFILE_NAME);
                mAccount.mDataLibrary = seafileLibrary;
            }
            if (!TextUtils.isEmpty(mAccount.mDataLibrary.libraryId)) {
                String name = mAccount.mDataLibrary.libraryName;
                int isSync = mConsole.queryFile(mAccount.mUserId,
                        mAccount.mDataLibrary.libraryId, mAccount.mDataLibrary.libraryName);
                mAccount.mDataLibrary.isSync = isSync;
                mAccount.mDataLibrary.filePath = SeafileUtils.SEAFILE_DATA_ROOT_PATH
                        + "/" + SeafileUtils.mUserId + "/" + mAccount.mDataLibrary.libraryName;
                if (isSync == SeafileUtils.SYNC) {
                    SeafileUtils.sync(mAccount.mDataLibrary.libraryId,
                            mAccount.mDataLibrary.filePath);
                }
            } else {
                mAccount.mDataLibrary = null;
            }
            mHandler.sendEmptyMessage(INIT_NOTIFICATION);
        }
    }

    private void initNotification() {
        mStatusTimer = new Timer();
        mStatusTask = new StatusTask();
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mBuilder = new Notification.Builder(this);
        mBuilder.setContentTitle(getString(R.string.seafile_status_title));
        mBuilder.setSmallIcon(R.mipmap.ic_launcher);
        mBuilder.setAutoCancel(false);
        mBuilder.setOngoing(true);
        mStyle = new Notification.BigTextStyle();
        mStatusTimer.schedule(mStatusTask, 0, TIMER_LONG);
        mDataObserver = new StatusObserver(mUserPath + "/" + SeafileUtils.DATA_SEAFILE_NAME);
        mUserConfigObserver = new StatusObserver(mConfigPath.getAbsolutePath() +
                "/" + SeafileUtils.SETTING_SEAFILE_NAME);
        mDataObserver.startWatching();
        mUserConfigObserver.startWatching();
    }

    private class StatusTask extends TimerTask {
        @Override
        public void run() {
            String notice = "";
            ArrayList<String> result = SeafileUtils.
                    execCommand(SeafileUtils.SEAFILE_COMMAND_BASE + "status");
            for (String s : result) {
                if (s.contains(SEAFILE_STATUS_UPLOADING)
                        || s.contains(SEAFILE_STATUS_DOWNLOADING)) {
                    s = s.replace(SEAFILE_STATUS_UPLOADING,
                            getString(R.string.seafile_uploading));
                    s = s.replace(SEAFILE_STATUS_DOWNLOADING,
                            getString(R.string.seafile_downloading));
                    s = s.replace(SeafileUtils.DATA_SEAFILE_NAME,
                            getString(R.string.data_seafile_name)) + "\n";
                    s = s.replace(SeafileUtils.SETTING_SEAFILE_NAME,
                            getString(R.string.userconfig_seafile_name));
                    notice += s;
                }
            }
            if (!TextUtils.isEmpty(notice)) {
                showNotification(notice);
                if (!mIsNotificationShown) {
                    mIsNotificationShown = true;
                    mBuilder.setWhen(System.currentTimeMillis());
                    restartTimer(TIMER_SHORT);
                }
            } else {
                if (mIsNotificationShown) {
                    mIsNotificationShown = false;
                    mNotificationManager.cancel(0);
                    restartTimer(TIMER_LONG);
                }
            }
        }
    }

    private void restartTimer(long period) {
        if (period == TIMER_LONG) {
            mDataObserver.startWatching();
            mUserConfigObserver.startWatching();
        } else {
            mDataObserver.stopWatching();
            mUserConfigObserver.stopWatching();
        }
        mStatusTask.cancel();
        mStatusTimer.cancel();
        mStatusTask = new StatusTask();
        mStatusTimer = new Timer();
        mStatusTimer.schedule(mStatusTask, period, period);
    }

    private void showNotification(String notice) {
        mStyle.bigText(notice);
        mBuilder.setStyle(mStyle);
        mNotificationManager.notify(0, mBuilder.getNotification());
    }

    private class StatusObserver extends FileObserver {

        public StatusObserver(String path) {
            super(path);
        }

        @Override
        public void onEvent(int event, String path) {
            int action = event & FileObserver.ALL_EVENTS;
            switch (action) {
                case FileObserver.CREATE:
                case FileObserver.MOVED_TO:
                case FileObserver.MOVED_FROM:
                case FileObserver.DELETE:
                    if (!mIsNotificationShown) {
                        mIsNotificationShown = true;
                        restartTimer(TIMER_MEDIUM);
                    }
                    break;
            }
        }
    }

    private void postDelayedThread () {
        mInitLibrarysThread = null;
        mInitLibrarysThread = new InitLibrarysThread();
        mScheduledService.schedule(mInitLibrarysThread, 60, TimeUnit.SECONDS);
    }

    private void initAppIntent() {
        mBrowserIntent = new Intent(Intent.ACTION_VIEW);
        mBrowserIntent.addCategory(Intent.CATEGORY_BROWSABLE);
        Uri uri = Uri.parse("http://");
        mBrowserIntent.setData(uri);
        mAppdataIntent = new Intent(Intent.ACTION_MAIN, null);
        mAppdataIntent.addCategory(Intent.CATEGORY_LAUNCHER);
    }

    private String getLibrarysSuccess() {
        try {
            String token = SeafileUtils.getToken(SeafileService.this);
            return SeafileUtils.getResult(token);
        } catch (UnsupportedEncodingException | HttpRequest.HttpRequestException
                | PackageManager.NameNotFoundException | JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }
    private void regiestNetworkReceiver() {
        mNetworkReceiver = new NetworkReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(mNetworkReceiver, intentFilter);
    }

    private void unregiestNetworkReceiver() {
        unregisterReceiver(mNetworkReceiver);
        mNetworkReceiver = null;
    }

    private class NetworkReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case ConnectivityManager.CONNECTIVITY_ACTION:
                    ConnectivityManager manager = (ConnectivityManager) context
                            .getSystemService(Context.CONNECTIVITY_SERVICE);
                    NetworkInfo activeNetwork = manager.getActiveNetworkInfo();
                    if (activeNetwork != null && activeNetwork.isConnected()) {
                        mInitLibrarysThread = new InitLibrarysThread();
                        mInitLibrarysThread.start();
                        unregiestNetworkReceiver();
                    }
                    break;
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    private void exportWallpaperFiles() {
        String path =  mConfigPath.getAbsolutePath() + SEAFILE_PATH_WALLPAPER;
        if (SeafileUtils.checkFile(SYSTEM_PATH_WALLPAPER)) {
            SeafileUtils.exec("cp -f " + SYSTEM_PATH_WALLPAPER + " "
                    + mConfigPath.getAbsolutePath() + SEAFILE_PATH_WALLPAPER);
        } else {
            SeafileUtils.exec("rm -r " + mConfigPath.getAbsolutePath() + SEAFILE_PATH_WALLPAPER);
        }
    }

    private void importWallpaperFiles() {
        String path =  mConfigPath.getAbsolutePath()  + SEAFILE_PATH_WALLPAPER;
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
        String path = mConfigPath.getAbsolutePath() + SEAFILE_PATH_WIFI;
        if (SeafileUtils.checkFile(path)) {
            WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
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
                mConfigPath.getAbsolutePath() + SEAFILE_PATH_WIFI);
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
            String path = mConfigPath.getAbsolutePath() + SEAFILE_PATH_APPSTORE;
            if (packageInfos.size() > 0) {
                SeafileUtils.exec("echo > " + path + ";busybox chmod 777 " + path);
                appWriter = new BufferedWriter(new FileWriter(path));
                for (PackageInfo f :packageInfos){
                    appWriter.write(f.packageName);
                    appWriter.newLine();
                    appWriter.flush();
                }
            }
            appWriter.close();
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
            try {
                int uid = mPackageManager.getApplicationInfo(
                         packages.get(i), PackageManager.GET_ACTIVITIES).uid;
                SeafileUtils.untarFile(mConfigPath.getAbsolutePath() +
                        configPath + packages.get(i) + ".tar.gz");
                SeafileUtils.chownFile(mConfigPath.getAbsolutePath() +
                        configPath + packages.get(i), uid);
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    private void exportFiles(List<String> packages, String configPath) {
        for (int i = 0; i < packages.size(); i++) {
            File configFile = new File(mConfigPath.getAbsolutePath() + configPath);
            if (!configFile.exists()) {
                configFile.mkdirs();
            }
            SeafileUtils.tarFile(SYSTEM_PATH_DATA + packages.get(i),
                    configFile.getAbsolutePath() + "/" + packages.get(i) + ".tar.gz");
        }
    }

    private void registSeafile(String openthosID, String password) {
    }

    private void startTimer() {
        mTimer = new Timer();
        mAutoTask = new AutoBackupTask();
        mTimer.schedule(mAutoTask, 0, 3600000);
    }

    private void stopTimer() {
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
            mBinder.saveSettings(mWallpaper, mWifi, mAppdata, mSyncAppdata,
                    mStartupmenu, mBrowser, mSyncBrowsers, mAppstore);
        }
    }

    private void initializeApp() {
        mAppNames.clear();
        mApkPaths.clear();
        File file = new File(APPSTORE_DOWNLOAD_PATH);
        File[] files = file.listFiles();
        try {
            for (File apk: files) {
                String name = getAppName(apk);
                if (TextUtils.isEmpty(name)) {
                    continue;
                }
                mAppNames.add(name);
                mApkPaths.add(apk.getAbsolutePath());
            }
        } catch (Exception e) {
        }
        mTotalApks = mAppNames.size();
    }

    private String getAppName(File sourceFile) {
        try {
            PackageParser.Package pkg = parser.parseMonolithicPackage(sourceFile, 0);
            parser.collectManifestDigest(pkg);
            PackageInfo info = PackageParser.generatePackageInfo(pkg, null,
                     PackageManager.GET_PERMISSIONS, 0, 0, null,
                     new PackageUserState());
            Resources pRes = getResources();
            AssetManager assmgr = new AssetManager();
            assmgr.addAssetPath(sourceFile.getAbsolutePath());
            Resources res = new Resources(assmgr,
                                 pRes.getDisplayMetrics(), pRes.getConfiguration());
            CharSequence label = null;
            if (info.applicationInfo.labelRes != 0) {
                label = res.getText(info.applicationInfo.labelRes);
            }
            if (label == null) {
                label = (info.applicationInfo.nonLocalizedLabel != null) ?
                      info.applicationInfo.nonLocalizedLabel : info.applicationInfo.packageName;
            }
            return label.toString();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    public class InstallAsyncTask extends AsyncTask<Void, Object, Void> {
        private static final int CURRENT_INDEX = 0;
        private static final int APPNAME = 1;


        @Override
        protected Void doInBackground(Void... params) {
            initializeApp();
            for (int i = 0; i < mTotalApks; i++) {
                Object[] result = new Object[2];
                result[CURRENT_INDEX] = i + 1;
                result[APPNAME] = mAppNames.get(i);
                publishProgress(result);
                installSlient(mApkPaths.get(i));
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(Object... values) {
            int index = (int) values[CURRENT_INDEX];
            String appName = (String) values[APPNAME];
            for (IBinder iBinder : mIBinders) {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                _data.writeInterfaceToken(DESCRIPTOR);
                _data.writeString(getText(R.string.restore_progress) + " " + appName
                        + " " + index + "/" + mTotalApks);
                try {
                    iBinder.transact(CODE_SEND_OUT, _data, _reply, 0);
                } catch (RemoteException e) {
                    e.printStackTrace();
                } finally {
                    _data.recycle();
                    _reply.recycle();
                }
            }
        }

        @Override
        protected void onPostExecute(Void avoid) {
             restoreFinish();
        }
    }

    private void restoreFinish(){
        if (mTempStartupMenu) {
            List<String> packages = new ArrayList();
            packages.add(SYSTEM_PATH_STATUSBAR);
            importFiles(packages, "/");
            mHandler.post(new Runnable() {
                public void run(){
                    Process pro = null;
                    BufferedReader in = null;
                    ArrayList<String> temp = new ArrayList();
                    try {
                        pro = Runtime.getRuntime().exec(new String[]{"su", "-c", "netcfg"});
                        in = new BufferedReader(new InputStreamReader(pro.getInputStream()));
                        String line;

                        while ((line = in.readLine()) != null) {
                            String tempStr = line.split("\\s+")[0];
                            if (tempStr.startsWith("eth")) {
                                temp.add(tempStr);
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        if (in != null) {
                            try {
                                in.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    StringBuffer sb = new StringBuffer();
                    for (String str : temp) {
                        sb.append("netcfg ").append(str).append(" down;");
                    }
                    SeafileUtils.exec(new String[]{"su", "-c",
                            sb.toString() + "kill " + Jni.nativeKillPid()});
                }
            });
        }
        mImportBusy = false;
        Log.i("seafile", "restoreFinish" + mIBinders.size());
        for (IBinder iBinder : mIBinders) {
            Parcel _data = Parcel.obtain();
            Parcel _reply = Parcel.obtain();
            _data.writeInterfaceToken(DESCRIPTOR);
            try {
                iBinder.transact(CODE_RESTORE_FINISH, _data, _reply, 0);
            } catch (RemoteException e) {
                e.printStackTrace();
            } finally {
                _data.recycle();
                _reply.recycle();
            }
        }
    }

    public void updateAccount(String name, String password) {
        mSp.edit().putString("user", name).putString("password", password).commit();
        initAccount(name, password);
    }

    private void installSlient(String apkPath) {
        apkPath = apkPath.replace(Environment.getExternalStorageDirectory().getAbsolutePath(),
                "/storage/emulated/legacy");
        String cmd = "pm install " + apkPath;
        DataOutputStream os = null;
        try {
            Process process = Runtime.getRuntime().exec("su");
            os = new DataOutputStream(process.getOutputStream());
            os.write(cmd.getBytes());
            os.writeBytes("\n");
            os.writeBytes("exit\n");
            os.flush();
            process.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (os != null) {
                try {
                    os.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private class SeafileBinder extends ISeafileService.Stub {

        @Override
        public boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            switch (code) {
                case CODE_SEND_INTO:
                    String info = data.readString();
                    for (IBinder iBinder : mIBinders) {
                        Parcel _data = Parcel.obtain();
                        Parcel _reply = Parcel.obtain();
                        _data.writeString(
                                getText(R.string.appstore_download_app) + " " + info);
                        try {
                            iBinder.transact(CODE_SEND_OUT, _data, _reply, 0);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        } finally {
                            _data.recycle();
                            _reply.recycle();
                        }
                        reply.writeNoException();
                    }
                    return true;
               case CODE_DOWNLOAD_FINISH:
                    new InstallAsyncTask().execute();
                    reply.writeNoException();
                    return true;
            }
            return super.onTransact(code, data, reply, flags);
        }

        @Override
        public void syncData() {
            mConsole.updateSync(mAccount.mUserId, mAccount.mDataLibrary.libraryId,
                     mAccount.mDataLibrary.libraryName, SeafileUtils.SYNC);
            SeafileUtils.sync(mAccount.mDataLibrary.libraryId, mAccount.mDataLibrary.filePath);
        }

        @Override
        public void desyncData() {
            mConsole.updateSync(mAccount.mUserId, mAccount.mDataLibrary.libraryId,
                     mAccount.mDataLibrary.libraryName, SeafileUtils.UNSYNC);
            SeafileUtils.desync(mAccount.mDataLibrary.filePath);
        }

        @Override
        public String getUserName() {
            return SeafileUtils.mUserId;
        }

        @Override
        public boolean isSync() {
            return mAccount != null
                && mAccount.mDataLibrary != null
                && mAccount.mDataLibrary.isSync == SeafileUtils.SYNC;
        }

        @Override
        public void stopAccount() {
            if (mDataObserver != null) {
                mDataObserver.stopWatching();
            }
            if (mUserConfigObserver != null) {
                mUserConfigObserver.stopWatching();
            }
            mSp.edit().putString("user", "").putString("password", "").commit();
            mAccount = null;
            SeafileUtils.stop();
            stopTimer();
            if (mInitLibrarysThread != null) {
                mInitLibrarysThread = null;
            }
            mScheduledService.shutdown();
            mScheduledService = null;
        }

        @Override
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
                    String path = mConfigPath.getAbsolutePath() + SEAFILE_PATH_APPSTORE;
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
            } else {
                restoreFinish();
            }
        }

        @Override
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
                    String path = mConfigPath.getAbsolutePath() + SEAFILE_PATH_APPSTORE;
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

        @Override
        public void registeAccount(String userName, String email, String password) {
            if (SeafileUtils.mIsDevServer) {
                DevRequestThread devThread = new DevRequestThread(mHandler, SeafileService.this,
                        userName, email, password, Mark.REGISTE);
                devThread.start();
            } else {
                LibraryRequestThread libraryThread = new LibraryRequestThread(
                        mHandler, SeafileService.this, userName, email, password, Mark.REGISTE);
                libraryThread.start();
            }
        }

        @Override
        public void loginAccount(String userName, String password) {
            if (SeafileUtils.mIsDevServer) {
                DevRequestThread devThread = new DevRequestThread(
                        mHandler, SeafileService.this, userName, password, Mark.LOGIN);
                devThread.start();
            } else {
                LibraryRequestThread libraryThread = new LibraryRequestThread(
                        mHandler, SeafileService.this, userName, password, Mark.LOGIN);
                libraryThread.start();
            }
        }

        @Override
        public void setBinder(IBinder b) {
            Message msg = new Message();
            msg.what = ADD_BINDER;
            msg.obj = b;
            mHandler.sendMessage(msg);
        }

        @Override
        public void unsetBinder(IBinder b) {
            Message msg = new Message();
            msg.what = REMOVE_BINDER;
            msg.obj = b;
            mHandler.sendMessage(msg);
        }

        @Override
        public int getCodeSendInto() {
            return CODE_SEND_INTO;
        }

        @Override
        public int getCodeSendOut() {
            return CODE_SEND_OUT;
        }

        @Override
        public int getCodeRestoreFinish() {
            return CODE_RESTORE_FINISH;
        }

        @Override
        public int getCodeDownloadFinish() {
            return CODE_DOWNLOAD_FINISH;
        }

        @Override
        public int getCodeRegiestSuccess() {
            return CODE_REGIEST_SUCCESS;
        }

        @Override
        public int getCodeRegiestFailed() {
            return CODE_REGIEST_FAILED;
        }

        @Override
        public int getCodeLoginSuccess() {
            return CODE_LOGIN_SUCCESS;
        }

        @Override
        public int getCodeLoginFailed() {
            return CODE_LOGIN_FAILED;
        }

        @Override
        public int getTagAppdataImport() {
            return SeafileUtils.TAG_APPDATA_IMPORT;
        }

        @Override
        public int getTagAppdataExport() {
            return SeafileUtils.TAG_APPDATA_EXPORT;
        }

        @Override
        public int getTagBrowserImport() {
            return SeafileUtils.TAG_BROWSER_IMPORT;
        }

        @Override
        public int getTagBrowserExport() {
            return SeafileUtils.TAG_BROWSER_EXPORT;
        }

        @Override
        public List<ResolveInfo> getAppsInfo(int tag) {
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
                            mConfigPath.getAbsolutePath() + SEAFILE_PATH_APPDATA)) {
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
                            mConfigPath.getAbsolutePath() + SEAFILE_PATH_BROWSER)) {
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

        public void setDevServer(boolean isDev) {
            SeafileUtils.mIsDevServer = isDev;
        }

        public boolean isDevServer() {
            return SeafileUtils.mIsDevServer;
        }
    }

    private class SeafileHandler extends Handler {

        public SeafileHandler (Looper looper) {
            super(looper);
        }

        @Override
        public synchronized void handleMessage (Message msg) {
            switch (msg.what) {
                case INIT_NOTIFICATION:
                    initNotification();
                    break;
                case ADD_BINDER:
                    mIBinders.add((IBinder) msg.obj);
                    break;
                case REMOVE_BINDER:
                    mIBinders.remove((IBinder) msg.obj);
                    break;
                case LibraryRequestThread.MSG_REGIST_SEAFILE_OK:
                    for (IBinder iBinder : mIBinders) {
                        Parcel _data = Parcel.obtain();
                        Parcel _reply = Parcel.obtain();
                        _data.writeString(msg.obj.toString());
                        try {
                            iBinder.transact(CODE_REGIEST_SUCCESS, _data, _reply, 0);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        } finally {
                            _data.recycle();
                            _reply.recycle();
                        }
                    }
                    break;
                case LibraryRequestThread.MSG_REGIST_SEAFILE_FAILED:
                    for (IBinder iBinder : mIBinders) {
                        Parcel _data = Parcel.obtain();
                        Parcel _reply = Parcel.obtain();
                        _data.writeString(msg.obj.toString());
                        try {
                            iBinder.transact(CODE_REGIEST_FAILED, _data, _reply, 0);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        } finally {
                            _data.recycle();
                            _reply.recycle();
                        }
                    }
                    break;
                case LibraryRequestThread.MSG_LOGIN_SEAFILE_OK:
                    Bundle bundle = msg.getData();
                    updateAccount(bundle.getString("user"), bundle.getString("password"));
                    for (IBinder iBinder : mIBinders) {
                        Parcel _data = Parcel.obtain();
                        Parcel _reply = Parcel.obtain();
                        _data.writeInterfaceToken(DESCRIPTOR);
                        try {
                            iBinder.transact(CODE_LOGIN_SUCCESS, _data, _reply, 0);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        } finally {
                            _data.recycle();
                            _reply.recycle();
                        }
                    }
                    break;
                case LibraryRequestThread.MSG_LOGIN_SEAFILE_FAILED:
                    for (IBinder iBinder : mIBinders) {
                        Parcel _data = Parcel.obtain();
                        Parcel _reply = Parcel.obtain();
                        _data.writeString(msg.obj.toString());
                        try {
                            iBinder.transact(CODE_LOGIN_FAILED, _data, _reply, 0);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        } finally {
                            _data.recycle();
                            _reply.recycle();
                        }
                    }
                    break;
            }
        }
    }

    @Override
    public void onDestroy() {
        if (mDataObserver != null) {
            mDataObserver.stopWatching();
        }
        if (mUserConfigObserver != null) {
            mUserConfigObserver.stopWatching();
        }
        super.onDestroy();
    }
}
