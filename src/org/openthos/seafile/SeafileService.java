package org.openthos.seafile;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.FileObserver;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Parcel;
import android.os.RemoteException;
import android.text.TextUtils;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.io.File;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SeafileService extends Service {
    private static final int START_STATE_MONITOR = 40000001;
    private static final int ADD_BINDER = 40000002;
    private static final int REMOVE_BINDER = 40000003;
    private static final int CODE_SEND_INTO = 80000001;
    private static final int CODE_SEND_OUT = 80000002;
    private static final int CODE_REGIEST_SUCCESS = 80000005;
    private static final int CODE_REGIEST_FAILED = 80000006;
    private static final int CODE_LOGIN_SUCCESS = 80000007;
    private static final int CODE_LOGIN_FAILED = 80000008;
    private static final int CODE_CHANGE_URL = 80000009;
    private static final int CODE_UNBIND_ACCOUNT = 80000010;
    private static final String SEAFILE_STATUS_DOWNLOADING = "downloading";
    private static final String SEAFILE_STATUS_UPLOADING = "uploading";
    public static SeafileAccount mAccount;
    private String mUserPath;
    private ArrayList<IBinder> mIBinders = new ArrayList();
    private SeafileBinder mBinder = new SeafileBinder();
    private Handler mHandler;
    private String mTmpOpenthosUrl = SeafileUtils.SEAFILE_URL_LIBRARY;

    private StateObserver mLogObserver;
    private StateObserver mStateObserver;
    private NotificationManager mNotificationManager;
    private Notification.Builder mBuilder;
    private Notification.BigTextStyle mStyle;
    private boolean mIsNotificationShown = false;

    @Override
    public void onCreate() {
        super.onCreate();
        mHandler = new SeafileHandler(Looper.getMainLooper());
        mAccount = new SeafileAccount(this);
        if (mAccount.isExistsAccount()) {
            startAccount();
        }
    }

    // sync bound account
    private void startAccount() {
	android.util.Log.i("wwww", "startaccount");
        mUserPath = SeafileUtils.SEAFILE_DATA_ROOT_PATH + "/" + mAccount.mUserName;
    }

    // monitor librarys status
    private void startStatusMonitor() {
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mBuilder = new Notification.Builder(this);
        mBuilder.setContentTitle(getString(R.string.seafile_status_title));
        mBuilder.setSmallIcon(R.mipmap.ic_launcher);
        mBuilder.setAutoCancel(false);
        mBuilder.setOngoing(true);
        mStyle = new Notification.BigTextStyle();

        mStateObserver = new StateObserver(SeafileUtils.SEAFILE_KEEPER_STATE_PATH);
        mLogObserver = new StateObserver(SeafileUtils.SEAFILE_STATE_PATH);
        mLogObserver.startWatching();
    }

    private class StateObserver extends FileObserver {

        public StateObserver(String path) {
            super(path);
        }

        @Override
        public void onEvent(int event, String path) {
            int action = event & FileObserver.ALL_EVENTS;
            switch (action) {
                case FileObserver.CREATE:
                    if (SeafileUtils.SEAFILE_STATE_FILE.equals(path)) {
                        mStateObserver.startWatching();
                    }
                    break;
                case FileObserver.DELETE:
                    if (SeafileUtils.SEAFILE_STATE_FILE.equals(path)) {
                        mStateObserver.stopWatching();
                        if (mIsNotificationShown) {
                            showNotification(getString(R.string.sync_complete));
                        }
                    }
                    break;
                case FileObserver.MODIFY:
                    if (SeafileUtils.SEAFILE_KEEPER_STATE_FILE.equals(path)) {
                        showNotification(SeafileUtils.readLog(SeafileService.this));
                        mIsNotificationShown = true;
                    }
                    break;
            }
        }
    }

    private void showNotification(String notice) {
        mStyle.bigText(notice);
        mBuilder.setStyle(mStyle);
        mBuilder.setWhen(System.currentTimeMillis());
        mNotificationManager.notify(0, mBuilder.getNotification());
    }

    private class SeafileBinder extends ISeafileService.Stub {

        @Override
        public boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            return super.onTransact(code, data, reply, flags);
        }

        @Override
        public void syncData() {
        }

        @Override
        public void desyncData() {
        }

        @Override
        public String getUserName() {
            return mAccount.mUserName;
        }

        @Override
        public boolean isSync() {
            return true;
        }

        @Override
        public void stopAccount() {
            if (mLogObserver != null) {
                mLogObserver.stopWatching();
            }
            if (mStateObserver != null) {
                mStateObserver.stopWatching();
            }
            if (mIsNotificationShown) {
                mIsNotificationShown = false;
                mNotificationManager.cancel(0);
            }
            mAccount.clear();
            Utils.writeAccount(SeafileService.this, mAccount.mOpenthosUrl, "", "");
            Intent intent = new Intent(SeafileService.this, RecoveryService.class);
            intent.putExtra("timer", true);
            startService(intent);
            for (IBinder iBinder : mIBinders) {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    iBinder.transact(CODE_UNBIND_ACCOUNT, _data, _reply, 0);
                } catch (RemoteException e) {
                    e.printStackTrace();
                } finally {
                    _data.recycle();
                    _reply.recycle();
                }
            }
        }

        @Override
        public void registeAccount(String userName, String email, String password) {
            AccountLogin libraryThread =
                    new AccountLogin(mHandler, SeafileService.this,
                            mAccount.mOpenthosUrl, userName, email, password, Mark.REGISTE);
            libraryThread.start();
        }

        @Override
        public void loginAccount(String userName, String password) {
            AccountLogin libraryThread = new AccountLogin(mHandler,
                    SeafileService.this, mAccount.mOpenthosUrl, userName, password, Mark.LOGIN);
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

        @Override
        public int getCodeUnbindAccount() {
            return CODE_UNBIND_ACCOUNT;
        }

        public boolean setOpenthosUrl(String url) {
            mTmpOpenthosUrl = mAccount.mOpenthosUrl;
            if (!Utils.writeAccount(SeafileService.this, url, "", "")) {
                mAccount.mOpenthosUrl = mTmpOpenthosUrl;
                return false;
            }
            if (mAccount.isExistsAccount()) {
                stopAccount();
            }
            mAccount.mOpenthosUrl = url;
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(SeafileService.this,
                            getString(R.string.toast_relogin), Toast.LENGTH_SHORT).show();
                }
            });
            return true;
        }

        public String getOpenthosUrl() {
            return mAccount.mOpenthosUrl;
        }
    }

    private class SeafileHandler extends Handler {

        public SeafileHandler(Looper looper) {
            super(looper);
        }

        @Override
        public synchronized void handleMessage(Message msg) {
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
                case AccountLogin.MSG_REGIST_SEAFILE_OK:
                    Toast.makeText(SeafileService.this, msg.obj.toString(),
                            Toast.LENGTH_SHORT).show();
                    break;
                case AccountLogin.MSG_REGIST_SEAFILE_FAILED:
                    Toast.makeText(SeafileService.this, msg.obj.toString(),
                            Toast.LENGTH_SHORT).show();
                    break;
                case AccountLogin.MSG_LOGIN_SEAFILE_OK:
                    Bundle bundle = msg.getData();
                    Utils.writeAccount(SeafileService.this, mAccount.mOpenthosUrl,
                            bundle.getString("user"), bundle.getString("token"));
                    if (!TextUtils.isEmpty(bundle.getString("user"))
                            && !TextUtils.isEmpty(bundle.getString("token"))) {
                        mAccount.mUserName = bundle.getString("user");
                        mAccount.mToken = bundle.getString("token");
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
                case AccountLogin.MSG_LOGIN_SEAFILE_FAILED:
                    Toast.makeText(SeafileService.this, msg.obj.toString(),
                            Toast.LENGTH_SHORT).show();
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
        if (mStateObserver != null) {
            mStateObserver.stopWatching();
        }
        if (mLogObserver != null) {
            mLogObserver.stopWatching();
        }
        super.onDestroy();
    }
}
