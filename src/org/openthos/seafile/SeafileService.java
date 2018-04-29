package org.openthos.seafile;

import android.app.Activity;
import android.app.Service;
import android.app.WallpaperManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
import android.net.EthernetManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Parcel;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import org.apache.http.message.BasicNameValuePair;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
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

import android.os.Environment;
import android.os.RemoteException;

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

    private static final String ROOT_COMMOND = "chmod -R 777 ";
    private static final String TAG = "SeafileService";
    private static final String DESCRIPTOR = "org.openthos.seafile.ISeafileService";
    private static final String APPSTORE_DOWNLOAD_PATH
            = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            .getAbsolutePath() + "/app";
    private static final String URL_REGIEST_ACCOUNT= "https://dev.openthos.org/accounts/register/";

    private static final int CODE_SEND_INTO = 80000001;
    private static final int CODE_SEND_OUT = 80000002;
    private static final int CODE_RESTORE_FINISH = 80000003;
    private static final int CODE_DOWNLOAD_FINISH = 80000004;
    private static final int CODE_REGIEST_SUCCESS = 80000005;
    private static final int CODE_REGIEST_FAILED = 80000006;

    private StartSeafileThread mStartSeafileThread;
    public SeafileAccount mAccount;
    public SeafileUtils.SeafileSQLConsole mConsole;
    public String mLibrary = "";
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

    @Override
    public void onCreate() {
        super.onCreate();
        mHandler = new SeafileHandler(Looper.getMainLooper());
        SeafileUtils.init();
    }

    private void initData() {
        mScheduledService = Executors.newScheduledThreadPool(1);
        mPackageManager = getPackageManager();
        ContentResolver mResolver = SeafileService.this.getContentResolver();
        Uri uriQuery = Uri.parse(SeafileUtils.OPENTHOS_URI);
        Cursor cursor = mResolver.query(uriQuery, null, null, null, null);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                SeafileUtils.mUserId
                        = cursor.getString(cursor.getColumnIndex("openthosID")) + "@opentos.org";
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
        mUserPath = SeafileUtils.SEAFILE_PROOT_PATH + SeafileUtils.SEAFILE_DATA_ROOT_PATH
                + "/" + SeafileUtils.mUserId;
        mConfigPath = new File(SeafileUtils.SEAFILE_PROOT_PATH,
                SeafileUtils.mUserId + "/" + SeafileUtils.SETTING_SEAFILE_NAME);
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
            initAppIntent();
            if (!SeafileUtils.isNetworkOn(SeafileService.this)) {
                mStartSeafileThread = null;
                return;
            }
            if (!getLibrarysSuccess()) {
                postDelayedThread();
                return;
            }
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
                    if (!seafileLibrary.libraryName.equals(SeafileUtils.DATA_SEAFILE_NAME)) {
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
            if (!mConfigPath.exists()) {
                mConfigPath.mkdirs();
            }
            if (!isExistsSetting) {
                settingId = SeafileUtils.create(SeafileUtils.SETTING_SEAFILE_NAME);
            }
            SeafileUtils.sync(settingId, "/"
                   + SeafileUtils.mUserId + "/" + SeafileUtils.SETTING_SEAFILE_NAME);
            if (!isExistsFileManager) {
                SeafileLibrary seafileLibrary = new SeafileLibrary();
                seafileLibrary.libraryName = SeafileUtils.DATA_SEAFILE_NAME;
                seafileLibrary.libraryId
                        = SeafileUtils.create(SeafileUtils.DATA_SEAFILE_NAME);
                mAccount.mLibrarys.add(seafileLibrary);
            }
            if (mAccount.mLibrarys.size() > 0) {
                for (SeafileLibrary seafileLibrary : mAccount.mLibrarys) {
                    String name = seafileLibrary.libraryName;
                    int isSync = mConsole.queryFile(mAccount.mUserId,
                            seafileLibrary.libraryId, seafileLibrary.libraryName);
                    seafileLibrary.isSync = isSync;
                    if (isSync == SeafileUtils.SYNC) {
                        SeafileUtils.sync(seafileLibrary.libraryId, SeafileUtils.SEAFILE_DATA_ROOT_PATH 
                                + "/" + SeafileUtils.mUserId + "/" + seafileLibrary.libraryName);
                    }
                }
            }
            mLibrary = mAccount.toString();
        }
    }
    private void postDelayedThread () {
        mStartSeafileThread = null;
        mStartSeafileThread = new StartSeafileThread();
        mScheduledService.schedule(mStartSeafileThread, 60, TimeUnit.SECONDS);
    }

    private void initAppIntent() {
        mBrowserIntent = new Intent(Intent.ACTION_VIEW);
        mBrowserIntent.addCategory(Intent.CATEGORY_BROWSABLE);
        Uri uri = Uri.parse("http://");
        mBrowserIntent.setData(uri);

        mAppdataIntent = new Intent(Intent.ACTION_MAIN, null);
        mAppdataIntent.addCategory(Intent.CATEGORY_LAUNCHER);
    }

    private boolean getLibrarysSuccess() {
        try {
            String token = SeafileUtils.getToken(SeafileService.this);
            mLibrary =  SeafileUtils.getResult(token);
        } catch (UnsupportedEncodingException
                | HttpRequest.HttpRequestException
                | PackageManager.NameNotFoundException
                | JSONException e) {
            e.printStackTrace();
            return false;
        }
        return true;
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
                        if (activeNetwork.isConnected() && TextUtils.isEmpty(mLibrary)) {
                            initData();
                        }
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
        String path =  mConfigPath.getAbsolutePath()  + SEAFILE_PATH_WALLPAPER;
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


    private void getCsrf(String user, String password) {
        List<NameValuePair> list = new ArrayList<>();
        list.add(new BasicNameValuePair("email", user));
        list.add(new BasicNameValuePair("password1", password));
        list.add(new BasicNameValuePair("password2", password));
        RequestThread thread = new RequestThread(mHandler, URL_REGIEST_ACCOUNT, list);
        thread.start();
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
        public void sync(String libraryId, String libraryName, String filePath) {
            mConsole.updateSync(mAccount.mUserId, libraryId, libraryName, SeafileUtils.SYNC);
            SeafileUtils.sync(libraryId, filePath);
        }

        @Override
        public void desync(String libraryId, String libraryName, String filePath) {
            mConsole.updateSync(mAccount.mUserId, libraryId, libraryName, SeafileUtils.UNSYNC);
            SeafileUtils.desync(filePath);
        }

        @Override
        public String getLibrary() {
            return mLibrary;
        }

        @Override
        public int getUserId() {
            return mAccount.mUserId;
        }

        @Override
        public String getUserName() {
            return SeafileUtils.mUserId;
        }

        @Override
        public int isSync(String libraryId, String libraryName) {
            return mConsole.queryFile(mAccount.mUserId, libraryId, libraryName);
        }

        @Override
        public void updateAccount() {
            initData();
        }

        @Override
        public void stopAccount() {
            SeafileUtils.stop();
            mStartSeafileThread = null;
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
        public void regiestAccount(String userName, String password) {
            getCsrf(userName, password);
        }

        @Override
        public void setBinder(IBinder b) {
            mIBinders.add(b);
        }

        @Override
        public void unsetBinder(IBinder b) {
            mIBinders.remove(b);
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
                    for (String name : SeafileUtils.listFiles(
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
                    for (String name : SeafileUtils.listFiles(
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
    }

    private class SeafileHandler extends Handler {

        public SeafileHandler (Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage (Message msg) {
            switch (msg.what) {
                case RequestThread.MSG_REGIST_SEAFILE_OK:
                    mBinder.updateAccount();
                    for (IBinder iBinder : mIBinders) {
                        Parcel _data = Parcel.obtain();
                        Parcel _reply = Parcel.obtain();
                        _data.writeInterfaceToken(DESCRIPTOR);
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
                case RequestThread.MSG_REGIST_SEAFILE_FAILED:
                    for (IBinder iBinder : mIBinders) {
                        Parcel _data = Parcel.obtain();
                        Parcel _reply = Parcel.obtain();
                        _data.writeInterfaceToken(DESCRIPTOR);
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
            }
        }
    }
}
