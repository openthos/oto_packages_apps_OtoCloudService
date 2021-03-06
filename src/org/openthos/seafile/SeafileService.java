package org.openthos.seafile;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
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
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.view.WindowManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import java.util.TimerTask;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;

import java.text.SimpleDateFormat;

import java.util.ArrayList;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SeafileService extends BaseService {
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
    private static final String SEAFILE_STATUS_DISABLED = "auto sync disabled";
    public static SeafileAccount mAccount;
    private String mUserPath;
    private ArrayList<IBinder> mIBinders = new ArrayList();
    private SeafileBinder mBinder = new SeafileBinder();
    private Handler mHandler;
    private String mTmpOpenthosUrl = SeafileUtils.SEAFILE_URL_LIBRARY;
    private StateObserver mLogObserver;
    private StateObserver mStateObserver;
    private StateObserver mQuotaStateObserver;
    private NotificationManager mNotificationManager;
    private Notification.Builder mBuilder;
    private Notification.BigTextStyle mStyle;
    private AlertDialog mDialog;
    private AlertDialog mReloginDialog;
    private EditText mUserID_bind;

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            Thread.sleep(12306);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        initStateMonitor();
        mHandler = new SeafileHandler(Looper.getMainLooper());
        mAccount = new SeafileAccount(this);
        if (mAccount.isExistsAccount()) {
            startAccount(false);
        }
    }

    // sync bound account
    private void startAccount(boolean isNewAccount) {
        mUserPath = SeafileUtils.SEAFILE_DATA_ROOT_PATH + "/" + mAccount.mUserName;
        mLogObserver.startWatching();
        mStateObserver.startWatching();
        mQuotaStateObserver.startWatching();
        //notify
        if (isNewAccount) {
            notifySeafileKeeper(mAccount.mOpenthosUrl, mAccount.mUserName, mAccount.mToken);
        }
        startAutoBackup();
    }

    private void startAutoBackup() {
        TimerTask task = new TimerTask() {
            public void run() {
                if (!getSharedPreferences("flag", Context.MODE_PRIVATE)
                        .getBoolean("AutoRecovery", true)) {
                    return;
                }
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
                Date now = new Date();
                String date = sdf.format(now);
                if (!new File("/sdcard/seafile/" 
                        + mAccount.mUserName + "/.UserConfig/" + date + ".tar").exists()) {
                    backup(date);
                }
            }
        };
        Timer timer = new Timer();
        long time = 1000L * 60L * 60L;
        timer.schedule(task, time, time);
    }

    private void backup(String date) {
        initEnvironment();
        BufferedReader br = null;
        try {
            Process pro = Runtime.getRuntime().exec(
                    new String[]{"su", "-c", "./data/data/org.openthos.seafile/backup " + date
                    + " /sdcard/seafile/"+ mAccount.mUserName + "/.UserConfig/"});
            br = new BufferedReader(new InputStreamReader(pro.getInputStream()));
            String line = "";
            while ((line = br.readLine()) != null) {
            }
            br.close();
            br = null;
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // monitor librarys state
    private void initStateMonitor() {
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mBuilder = new Notification.Builder(this);
        mBuilder.setContentTitle(getString(R.string.seafile_status_title));
        mBuilder.setSmallIcon(R.mipmap.ic_launcher);
        mBuilder.setAutoCancel(false);
        mBuilder.setOngoing(true);
        mStyle = new Notification.BigTextStyle();

        AlertDialog.Builder builder = new AlertDialog.Builder(SeafileService.this);
        builder.setTitle(R.string.seafile_status_title)
                .setIcon(R.mipmap.ic_cloud)
                .setPositiveButton(R.string.confirm, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
        mDialog = builder.create();
        mDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);

        View viewBind = LayoutInflater.from(this).inflate(R.layout.dialog_relogin, null);
        mUserID_bind = (EditText) viewBind.findViewById(R.id.dialog_name);
        final EditText userPassword_bind = (EditText) viewBind.findViewById(R.id.dialog_name_bind);
        mUserID_bind.setEnabled(false);
        mReloginDialog  = new AlertDialog.Builder(this)
            .setMessage(R.string.relogin_warning)
            .setView(viewBind)
            .setPositiveButton(R.string.confirm,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        AccountLogin libraryThread = new AccountLogin(mHandler,
                                SeafileService.this, mAccount.mOpenthosUrl,
                                mAccount.mUserName.split("@")[0],
                                userPassword_bind.getText().toString().trim(), Mark.LOGIN);
                        libraryThread.start();
                    }
                })
            .setNegativeButton(android.R.string.cancel, null).create();
        mReloginDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);

        mStateObserver = new StateObserver(SeafileUtils.SEAFILE_KEEPER_STATE_PATH);
        mQuotaStateObserver = new StateObserver(SeafileUtils.SEAFILE_QUOTA_STATE_PATH);
        mLogObserver = new StateObserver(SeafileUtils.SEAFILE_STATE_PATH);
    }

    private class StateObserver extends FileObserver {

        public StateObserver(String path) {
            super(path);
        }

        @Override
        public void onEvent(int event, String path) {
            int action = event & FileObserver.ALL_EVENTS;
            switch (action) {
                case FileObserver.DELETE:
                    if (SeafileUtils.SEAFILE_STATE_FILE.equals(path)) {
                        showNotification(getString(R.string.sync_complete));
                    }
                    break;
                case FileObserver.MODIFY:
                    if (SeafileUtils.SEAFILE_QUOTA_STATE_FILE.equals(path)) {
                        final String notice = SeafileUtils.readLog(SeafileService.this,
                                SeafileUtils.SEAFILE_QUOTA_STATE_PATH, path);
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                showQuotaDialog(notice);
                            }
                        });
                    }
                    if (SeafileUtils.SEAFILE_STATE_FILE.equals(path)) {
                        String content = SeafileUtils.readLog(SeafileService.this,
                                SeafileUtils.SEAFILE_STATE_PATH,
                                        SeafileUtils.SEAFILE_STATE_FILE);
                        if (content.contains(SeafileUtils.TOKEN_INVALID_TAG)) {
                            mHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    initReloginDialog();
                                }
                            });
                        }
                    }
                    if (SeafileUtils.SEAFILE_KEEPER_STATE_FILE.equals(path)) {
                        showNotification(SeafileUtils.readLog(SeafileService.this,
                                SeafileUtils.SEAFILE_KEEPER_STATE_PATH, path));
                    }
                    break;
            }
        }
    }

    private void initReloginDialog() {
        mUserID_bind.setText(mAccount.mUserName);
        if (!mReloginDialog.isShowing()) {
            mReloginDialog.show();
        }
    }

    private void showQuotaDialog(String notice) {
        if (notice.contains("Warning")) {
            notice = getString(R.string.seafile_status_warning);
        } else if (notice.contains("Error")) {
            notice = getString(R.string.seafile_status_disabled);
        } else {
            return;
        }
        mDialog.setMessage(notice);
        if (!mDialog.isShowing()) {
            mDialog.show();
        }
    }

    private void showNotification(String notice) {
        if (!TextUtils.isEmpty(notice)) {
            if (notice.contains(SEAFILE_STATUS_DISABLED)) {
                notice = getString(R.string.seafile_disabled);
            } else {
                notice = notice.replace(SEAFILE_STATUS_UPLOADING,
                        getString(R.string.seafile_uploading));
                notice = notice.replace(SEAFILE_STATUS_DOWNLOADING,
                        getString(R.string.seafile_downloading));
                notice = notice.replace(SeafileUtils.DATA_SEAFILE_NAME,
                        getString(R.string.data_seafile_name));
                notice = notice.replace(SeafileUtils.SETTING_SEAFILE_NAME,
                        getString(R.string.userconfig_seafile_name));
            }
            mStyle.bigText(notice);
            mBuilder.setStyle(mStyle);
            mBuilder.setWhen(System.currentTimeMillis());
            mNotificationManager.notify(0, mBuilder.getNotification());
        }
    }

    public class SeafileBinder extends ISeafileService.Stub {

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
            if (mQuotaStateObserver != null) {
                mQuotaStateObserver.stopWatching();
            }
            mNotificationManager.cancel(0);
            mAccount.clear();
            Utils.writeAccount(SeafileService.this, mAccount.mOpenthosUrl, "", "");
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
            mAccount.mOpenthosUrl = url;
            if (mAccount.isExistsAccount()) {
                stopAccount();
            }
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

        public void setFlagAutoRecovery(boolean flag) {
            getSharedPreferences("flag", Context.MODE_PRIVATE)
                    .edit().putBoolean("AutoRecovery", flag).commit();
        }

        public boolean getFlagAutoRecovery() {
            return getSharedPreferences("flag", Context.MODE_PRIVATE)
                    .getBoolean("AutoRecovery", true);
        }

        public void manualBackup() {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
            Date now = new Date();
            String date = sdf.format(now);
            backup(date);
        }

        @Override
        public void changeUrl() {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    View changeUrlDialog = LayoutInflater.from(SeafileService.this).inflate(R.layout.dialog_change_url, null);
                    Builder builder = new Builder(SeafileService.this);
                    builder.setTitle(R.string.title_change_url);
                    builder.setView(changeUrlDialog);
                    builder.setCancelable(true);
                    RadioGroup group = (RadioGroup) changeUrlDialog.findViewById(R.id.url_group);
                    final RadioButton rbDev = (RadioButton) changeUrlDialog.findViewById(R.id.url_dev);
                    final RadioButton rbLab = (RadioButton) changeUrlDialog.findViewById(R.id.url_lab);
                    final RadioButton rbCloud = (RadioButton) changeUrlDialog.findViewById(R.id.url_cloud);

                    if (mAccount.mOpenthosUrl.equals(rbDev.getText().toString())) {
                        rbDev.setChecked(true);
                    } else if (mAccount.mOpenthosUrl.equals(rbLab.getText().toString())) {
                        rbLab.setChecked(true);
                    } else if (mAccount.mOpenthosUrl.equals(rbCloud.getText().toString())) {
                        rbCloud.setChecked(true);
                    }
                    group.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
                        @Override
                        public void onCheckedChanged(RadioGroup group, int checkedId) {
                            if (checkedId == rbDev.getId()) {
                                rbDev.setChecked(true);
                            } else if (checkedId == rbLab.getId()) {
                                rbLab.setChecked(true);
                            } else if (checkedId == rbCloud.getId()) {
                                rbCloud.setChecked(true);
                            }
                        }
                    });
                    builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(final DialogInterface dialog, final int which) {
                            String tempUrl = "";
                            if (rbDev.isChecked()) {
                                tempUrl = rbDev.getText().toString();
                            } else if (rbLab.isChecked()) {
                                tempUrl = rbLab.getText().toString();
                            } else if (rbCloud.isChecked()) {
                                tempUrl = rbCloud.getText().toString();
                            }
                            setOpenthosUrl(tempUrl);
                        }
                    });
                    builder.setNegativeButton(android.R.string.cancel, null);
                    AlertDialog dialog = builder.create();
                    dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
                    dialog.show();
                }
            });
        }
    }

    private class SeafileHandler extends Handler {

        public SeafileHandler(Looper looper) {
            super(looper);
        }

        @Override
        public synchronized void handleMessage(Message msg) {
            switch (msg.what) {
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
                        if (mReloginDialog != null && mReloginDialog.isShowing()) {
                            mBinder.stopAccount();
                            mReloginDialog.dismiss();
                            mReloginDialog = null;
                        }
                        mAccount.mUserName = bundle.getString("user");
                        mAccount.mToken = bundle.getString("token");
                        startAccount(true);
                    }
                    Toast.makeText(SeafileService.this, msg.obj.toString(),
                            Toast.LENGTH_SHORT).show();
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
        if (mQuotaStateObserver != null) {
            mQuotaStateObserver.stopWatching();
        }
        if (mLogObserver != null) {
            mLogObserver.stopWatching();
        }
        super.onDestroy();
    }

    private void notifySeafileKeeper(String url, String accout, String token) {
        try {
            String serverUrl = "server_url=" + url;
            String user = "user=" + accout;
            String seafToken = "token=" + token;
            String action = "action=login";
            FileWriter writer = new FileWriter(new File(SeafileUtils.SEAFILE_ACCOUNT_CONFIG));
            BufferedWriter bufferedWriter = new BufferedWriter(writer);
            bufferedWriter.write(serverUrl + "\n" + user +
                        "\n" + seafToken + "\n" + action);
            bufferedWriter.flush();
            writer.close();
            bufferedWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void initEnvironment() {
        String mBackupPath = "/data/data/org.openthos.seafile/backup";
        File f = new File(mBackupPath);
        if (!f.exists()) {
            InputStream in = null;
            OutputStream out = null;
            try {
                in = getAssets().open("backup");
                out = new FileOutputStream(f);
                int byteconut;
                byte[] bytes = new byte[1024];
                while ((byteconut = in.read(bytes)) != -1) {
                    out.write(bytes, 0, byteconut);
                }
                in.close();
                out.close();
                in = null;
                out = null;
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
                if (out != null) {
                    try {
                        out.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        BufferedReader br = null;
        try {
            Process pro = Runtime.getRuntime().exec(
                    new String[]{"su", "-c", "chmod 755 " + f.getAbsolutePath()});
            br = new BufferedReader(new InputStreamReader(pro.getInputStream()));
            String line = "";
            while ((line = br.readLine()) != null) {
            }
            br.close();
            br = null;
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
