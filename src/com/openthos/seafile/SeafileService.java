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
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.PackageParser;
import android.content.pm.PackageUserState;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import android.os.Environment;
import android.os.RemoteException;

public class SeafileService extends Service {
    private static final String SYSTEM_PATH_DATA = "/data/data/";
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

    private static final String SEAFILE_PATH_WALLPAPER = "/UserConfig/wallpaper";
    private static final String SEAFILE_PATH_WIFI = "/UserConfig/wifi";
    private static final String SEAFILE_PATH_WIFI_INFO = "/UserConfig/wifi/wpa_supplicant.conf";
    private static final String SEAFILE_PATH_WALLPAPER_IMAGE =
                                SEAFILE_PATH_WALLPAPER + "/wallpaper";
    private static final String SEAFILE_PATH_EMAIL = "/UserConfig/email";
    private static final String SEAFILE_PATH_PREFS = SEAFILE_PATH_EMAIL + "/shared_prefs";
    private static final String SEAFILE_PATH_STARTUPMENU = "/UserConfig/startupmenu";
    private static final String SEAFILE_PATH_STATUSBAR_DB =
                         SEAFILE_PATH_STARTUPMENU + "/Status_bar_database.db";
    private static final String SEAFILE_PATH_BROWSER = "/UserConfig/browser/";
    private static final String SEAFILE_PATH_BROWSER_INFO = "/UserConfig/browser/mozilla";
    private static final String SEAFILE_PATH_APPSTORE = "/UserConfig/appstore";
    private static final String SEAFILE_PATH_APPSTORE_PKGNAME =
            "/UserConfig/appstore/appPkgNames.txt";

    private static final String ROOT_COMMOND = "chmod -R 777 ";
    private static final String TAG = "SeafileService";
    private static final String DESCRIPTOR = "com.openthos.seafile.ISeafileService";
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
    public String mLibrary;
    private boolean mIsStart = false;
    private File mCloudFolder;
    private Timer mTimer;
    private AutoBackupTask mAutoTask;
    private boolean mIsTimer;
    private boolean mWallpaper, mWifi, mEmail, mAppdata, mStartupmenu, mBrowser, mAppstore;
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

    @Override
    public void onCreate() {
        super.onCreate();
        mHandler = new SeafileHandler();
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

    private void importBrowserFiles(List<String> syncBrowsers) {
        for (int i = 0; i < syncBrowsers.size(); i++) {
            SeafileUtils.exec(new String[]{"su", "-c", "tar -xzvpf " + mUserPath +
                    SEAFILE_PATH_BROWSER + syncBrowsers.get(i) + ".tar.gz"});
        }
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

    private void exportBrowserFiles(List<String> syncBrowsers) {
        for (int i = 0; i < syncBrowsers.size(); i++) {
            SeafileUtils.exec(new String[]{"su", "-c", "tar -czpf " + mUserPath +
                    SEAFILE_PATH_BROWSER + syncBrowsers.get(i) + ".tar.gz " +
                    SYSTEM_PATH_DATA + syncBrowsers.get(i)});
        }
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
            mBinder.saveSettings(mWallpaper, mWifi, mEmail, mAppdata,
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
        mImportBusy = false;
        Toast.makeText(SeafileService.this, getResources().
                getString(R.string.import_reboot_info_warn), Toast.LENGTH_SHORT).show();
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
        public void restoreSettings(boolean wallpaper, boolean wifi, boolean email, boolean appdata,
                boolean startupmenu, boolean browser, List<String> syncBrowsers, boolean appstore) {
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
                importBrowserFiles(syncBrowsers);
            }
            if (appstore) {
                ArrayList<String> pkgNames = new ArrayList();
                File file = new File("SEAFILE_PATH_APPSTORE_PKGNAME");
                BufferedReader appReader = null;
                try {
                    appReader = new BufferedReader(
                            new FileReader(mUserPath + SEAFILE_PATH_APPSTORE_PKGNAME));
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
                intent.setComponent(new ComponentName("com.openthos.appstore",
                        "com.openthos.appstore.download.DownloadService"));
                intent.putStringArrayListExtra("packageNames", pkgNames);
                startService(intent);
            } else {
                restoreFinish();
            }
        }

        @Override
        public void saveSettings(boolean wallpaper, boolean wifi, boolean email, boolean appdata,
                boolean startupmenu, boolean browser, List<String> syncBrowsers, boolean appstore) {
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
            mSyncBrowsers = syncBrowsers;
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
                    exportBrowserFiles(syncBrowsers);
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
    }

    private class SeafileHandler extends Handler {
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
