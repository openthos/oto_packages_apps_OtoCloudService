package org.openthos.seafile;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
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

import java.io.DataOutputStream;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class SeafileService extends Service {

    private static final String DESCRIPTOR = "org.openthos.seafile.ISeafileService";
    private static final String APPSTORE_DOWNLOAD_PATH
            = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            .getAbsolutePath() + "/app";

    private static final int START_STATE_MONITOR = 40000001;
    private static final int ADD_BINDER = 40000002;
    private static final int REMOVE_BINDER = 40000003;

    private static final int CODE_SEND_INTO = 80000001;
    private static final int CODE_SEND_OUT = 80000002;
    private static final int CODE_DOWNLOAD_FINISH = 80000004;
    private static final int CODE_REGIEST_SUCCESS = 80000005;
    private static final int CODE_REGIEST_FAILED = 80000006;
    private static final int CODE_LOGIN_SUCCESS = 80000007;
    private static final int CODE_LOGIN_FAILED = 80000008;
    private static final int CODE_CHANGE_URL = 80000009;

    private static final long TIMER_SHORT = 1000;
    private static final long TIMER_MEDIUM = TIMER_SHORT * 10;
    private static final long TIMER_LONG = TIMER_MEDIUM * 10;

    private static final String SEAFILE_STATUS_DOWNLOADING = "downloading";
    private static final String SEAFILE_STATUS_UPLOADING = "uploading";

    public static SeafileAccount mAccount;
    public SeafileUtils.SeafileSQLConsole mConsole;
    private InitLibrarysThread mInitLibrarysThread;
    private String mUserPath;
    private ArrayList<IBinder> mIBinders = new ArrayList();
    private ArrayList<String> mAppNames = new ArrayList();
    private ArrayList<String> mApkPaths = new ArrayList();
    private int mTotalApks, mDownloadApks, mTotal;
    private SeafileBinder mBinder = new SeafileBinder();
    private boolean DEBUG = false;
    private Handler mHandler;
    private ScheduledExecutorService mScheduledService;
    private String mTmpOpenthosUrl = SeafileUtils.SEAFILE_URL_LIBRARY;

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
        mAccount = new SeafileAccount(this);
        if (mAccount.isExistsAccount()) {
            startAccount();
        }
    }

    // sync bound account
    private void startAccount() {
        mUserPath = SeafileUtils.SEAFILE_DATA_ROOT_PATH + "/" + mAccount.mUserName;
        RecoveryService.mConfigPath = SeafileUtils.SEAFILE_PROOT_PATH + "/" +
                mAccount.mUserName + "/" + SeafileUtils.SETTING_SEAFILE_NAME;
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
            if (!Utils.isNetworkOn(SeafileService.this)) {
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
            Intent intent = new Intent(SeafileService.this, RecoveryService.class);
            intent.putExtra("backup", true);
            startService(intent);
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
                File configFile = new File(RecoveryService.mConfigPath);
                if (!configFile.exists()) {
                    configFile.mkdirs();
                }
                mAccount.mSettingLibrary.filePath
                        = "/" + mAccount.mUserName + "/" + SeafileUtils.SETTING_SEAFILE_NAME;
                SeafileUtils.sync(mAccount.mSettingLibrary.libraryId,
                        mAccount.mSettingLibrary.filePath);
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
                        + "/" + mAccount.mUserName + "/" + mAccount.mDataLibrary.libraryName;
                if (isSync == SeafileUtils.SYNC) {
                    SeafileUtils.sync(mAccount.mDataLibrary.libraryId,
                            mAccount.mDataLibrary.filePath);
                }
            } else {
                mAccount.mDataLibrary = null;
            }
            mHandler.sendEmptyMessage(START_STATE_MONITOR);
        }
    }

    private void postDelayedThread () {
        mInitLibrarysThread = null;
        mInitLibrarysThread = new InitLibrarysThread();
        mScheduledService.schedule(mInitLibrarysThread, 60, TimeUnit.SECONDS);
    }

    // monitor librarys status
    private void startStatusMonitor() {
        mStatusTimer = new Timer();
        mStatusTask = new StatusTask();

        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mBuilder = new Notification.Builder(this);
        mBuilder.setContentTitle(getString(R.string.seafile_status_title));
        mBuilder.setSmallIcon(R.mipmap.ic_launcher);
        mBuilder.setAutoCancel(false);
        mBuilder.setOngoing(true);
        mStyle = new Notification.BigTextStyle();
        mStatusTimer.schedule(mStatusTask, 3000, TIMER_LONG);

        mDataObserver = new StatusObserver(mUserPath + "/" + SeafileUtils.DATA_SEAFILE_NAME);
        mUserConfigObserver = new StatusObserver(RecoveryService.mConfigPath +
                "/" + SeafileUtils.SETTING_SEAFILE_NAME);
        mDataObserver.startWatching();
        mUserConfigObserver.startWatching();
    }

    private class StatusTask extends TimerTask {
        @Override
        public void run() {
            String notice = "";
            ArrayList<String> result = Utils.exec(new String[]{"su", "-c",
                    SeafileUtils.SEAFILE_COMMAND_BASE + "status"});
            for (String s : result) {
                if (s.contains("error")
                        || s.contains("waiting for sync")
                        || s.contains("Failed to get sync info from server")
                        || s.contains("You do not have permission to access this library")) {
                    notice = "";
                    break;
                } else if (s.contains(SeafileUtils.DATA_SEAFILE_NAME)
                        || s.contains(SeafileUtils.SETTING_SEAFILE_NAME)) {
                    notice += s + "\n";
                }
            }
            if (notice.contains(SEAFILE_STATUS_UPLOADING)
                    || notice.contains(SEAFILE_STATUS_DOWNLOADING)) {
                notice = notice.replace(SEAFILE_STATUS_UPLOADING,
                       getString(R.string.seafile_uploading));
                notice = notice.replace(SEAFILE_STATUS_DOWNLOADING,
                       getString(R.string.seafile_downloading));
                notice = notice.replace(SeafileUtils.DATA_SEAFILE_NAME,
                       getString(R.string.data_seafile_name));
                notice = notice.replace(SeafileUtils.SETTING_SEAFILE_NAME,
                            getString(R.string.userconfig_seafile_name));
                if (!mIsNotificationShown) {
                    mIsNotificationShown = true;
                    mBuilder.setWhen(System.currentTimeMillis());
                    restartMonitor(TIMER_SHORT);
                }
                showNotification(notice);
            } else {
                if (mIsNotificationShown) {
                    if (TextUtils.isEmpty(notice)
                            || !notice.contains(SeafileUtils.DATA_SEAFILE_NAME)
                            || !notice.contains(SeafileUtils.SETTING_SEAFILE_NAME)) {
                        reSync();
                        mIsNotificationShown = false;
                        mNotificationManager.cancel(0);
                        restartMonitor(TIMER_MEDIUM);
                    } else {
                        mIsNotificationShown = false;
                        mNotificationManager.cancel(0);
                        restartMonitor(TIMER_LONG);
                        mBuilder.setWhen(System.currentTimeMillis());
                        showNotification(getString(R.string.sync_complete));
                    }
                }
            }
        }
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
                        restartMonitor(TIMER_MEDIUM);
                    }
                    break;
            }
        }
    }

    private void restartMonitor(long period) {
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

    private void reSync() {
        if (mAccount != null && mAccount.isExistsAccount()) {
            if (mAccount.mDataLibrary != null) {
                SeafileUtils.desync(mAccount.mDataLibrary.filePath);
                if (mAccount.mDataLibrary.isSync == SeafileUtils.SYNC) {
                    SeafileUtils.sync(mAccount.mDataLibrary.libraryId,
                            mAccount.mDataLibrary.filePath);
                }
            }
            if (mAccount.mSettingLibrary != null) {
                SeafileUtils.desync(mAccount.mSettingLibrary.filePath);
                SeafileUtils.sync(mAccount.mSettingLibrary.libraryId,
                        mAccount.mSettingLibrary.filePath);
            }
        } else {
            if (mAccount.isExistsAccount()) {
                startAccount();
            }
        }
    }

    private void showNotification(String notice) {
        mStyle.bigText(notice);
        mBuilder.setStyle(mStyle);
        mNotificationManager.notify(0, mBuilder.getNotification());
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
        protected void onPostExecute(Void avoid) {
            Intent intent = new Intent(SeafileService.this, RecoveryService.class);
            intent.putExtra("restore", true);
            startService(intent);
        }
    }

    private void initializeApp() {
        mAppNames.clear();
        mApkPaths.clear();
        File file = new File(APPSTORE_DOWNLOAD_PATH);
        File[] files = file.listFiles();
        try {
            for (File apk: files) {
                String name = Utils.getAppName(this, apk);
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
               case CODE_DOWNLOAD_FINISH:
                    new InstallAsyncTask().execute();
                    reply.writeNoException();
                    return true;
            }
            return super.onTransact(code, data, reply, flags);
        }

        @Override
        public void syncData() {
            if (mAccount == null || mAccount.mDataLibrary == null) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run () {
                        Toast.makeText(SeafileService.this, getString(R.string.toast_data_init), 0).show();
                    }
                });
            } else {
                mAccount.mDataLibrary.isSync =
                        mConsole.updateSync(mAccount.mUserId, mAccount.mDataLibrary.libraryId,
                        mAccount.mDataLibrary.libraryName, SeafileUtils.SYNC);
                SeafileUtils.sync(mAccount.mDataLibrary.libraryId, mAccount.mDataLibrary.filePath);
            }
        }

        @Override
        public void desyncData() {
            if (mAccount == null || mAccount.mDataLibrary == null) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run () {
                        Toast.makeText(SeafileService.this, getString(R.string.toast_data_init), 0).show();
                    }
                });
            } else {
                mAccount.mDataLibrary.isSync =
                        mConsole.updateSync(mAccount.mUserId, mAccount.mDataLibrary.libraryId,
                        mAccount.mDataLibrary.libraryName, SeafileUtils.UNSYNC);
                SeafileUtils.desync(mAccount.mDataLibrary.filePath);
            }
        }

        @Override
        public String getUserName() {
            return mAccount.mUserName;
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
            if (mAccount != null) {
                SeafileUtils.desync(mAccount.mDataLibrary.filePath);
                SeafileUtils.desync(mAccount.mSettingLibrary.filePath);
            }
            if (!Utils.writeAccount(SeafileService.this, "", "")) {
                deleteFile(Utils.ACCOUNT_INFO_FILE);
            }
            mAccount.clear();
            SeafileUtils.stop();
            if (mStatusTask != null) {
                mStatusTask.cancel();
            }
            if (mStatusTimer != null) {
                mStatusTimer.cancel();
            }
            Intent intent = new Intent(SeafileService.this, RecoveryService.class);
            intent.putExtra("timer", true);
            startService(intent);
            if (mInitLibrarysThread != null) {
                mInitLibrarysThread = null;
            }
            mScheduledService.shutdown();
            mScheduledService = null;
            getSharedPreferences("config", Context.MODE_PRIVATE).edit()
                    .putBoolean("wallpaper", false)
                    .putBoolean("wifi", false)
                    .putBoolean("appdata", false)
                    .putBoolean("startupmenu", false)
                    .putBoolean("browser", false)
                    .putBoolean("appstore", false).commit();
        }


        @Override
        public void registeAccount(String userName, String email, String password) {
            LibraryRequestThread libraryThread = new LibraryRequestThread(
                    mHandler, SeafileService.this, userName, email, password, Mark.REGISTE);
            libraryThread.start();
        }

        @Override
        public void loginAccount(String userName, String password) {
            LibraryRequestThread libraryThread = new LibraryRequestThread(
                    mHandler, SeafileService.this, userName, password, Mark.LOGIN);
            libraryThread.start();
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
        public int getCodeChangeUrl() {
            return CODE_CHANGE_URL;
        }

        public void setOpenthosUrl(String url) {
            mTmpOpenthosUrl = mAccount.mOpenthosUrl;
            mHandler.post(new Runnable() {
                @Override
                public void run () {
                    ChangeUrlDialog dialog = new ChangeUrlDialog(SeafileService.this, mHandler);
                    dialog.showDialog();
                }
            });
        }

        public String getOpenthosUrl() {
            return mAccount.mOpenthosUrl;
        }
    }

    private class SeafileHandler extends Handler {

        public SeafileHandler (Looper looper) {
            super(looper);
        }

        @Override
        public synchronized void handleMessage (Message msg) {
            switch (msg.what) {
                case START_STATE_MONITOR:
                    startStatusMonitor();
                    break;
                case ADD_BINDER:
                    mIBinders.add((IBinder) msg.obj);
                    break;
                case REMOVE_BINDER:
                    mIBinders.remove((IBinder) msg.obj);
                    break;
                case LibraryRequestThread.MSG_REGIST_SEAFILE_OK:
                    Toast.makeText(SeafileService.this, msg.obj.toString(),
                            Toast.LENGTH_SHORT).show();
                    break;
                case LibraryRequestThread.MSG_REGIST_SEAFILE_FAILED:
                    Toast.makeText(SeafileService.this, msg.obj.toString(),
                            Toast.LENGTH_SHORT).show();
                    break;
                case LibraryRequestThread.MSG_LOGIN_SEAFILE_OK:
                    Bundle bundle = msg.getData();
                    if (!Utils.writeAccount(SeafileService.this,
                            bundle.getString("user"), bundle.getString("password"))) {
                        break;
                    }
                    if (!TextUtils.isEmpty(bundle.getString("user"))
                            && !TextUtils.isEmpty(bundle.getString("password"))) {
                        mAccount.mUserName = bundle.getString("user");
                        mAccount.mUserPassword = bundle.getString("password");
                        startAccount();
                    }
                    for (IBinder iBinder : mIBinders) {
                        Parcel _data = Parcel.obtain();
                        Parcel _reply = Parcel.obtain();
                        _data.writeString(msg.obj.toString());
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
                    Toast.makeText(SeafileService.this, msg.obj.toString(),
                            Toast.LENGTH_SHORT).show();
                    break;
                case OpenthosIDActivity.MSG_CHANGE_URL:
                    if (!Utils.writeAccount(SeafileService.this,
                            mAccount.mUserName, mAccount.mUserPassword)) {
                        mAccount.mOpenthosUrl = mTmpOpenthosUrl;
                        break;
                    }
                    for (IBinder iBinder : mIBinders) {
                        Parcel _data = Parcel.obtain();
                        Parcel _reply = Parcel.obtain();
                        _data.writeString(mAccount.mOpenthosUrl);
                        try {
                            iBinder.transact(CODE_CHANGE_URL, _data, _reply, 0);
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
    public IBinder onBind(Intent intent) {
        return mBinder;
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
