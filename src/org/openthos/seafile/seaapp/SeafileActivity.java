package org.openthos.seafile.seaapp;

import android.app.FragmentTransaction;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.FragmentActivity;
import android.view.View;
import android.widget.GridView;
import android.widget.ListView;

import org.json.JSONException;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;

import org.openthos.seafile.R;
import org.openthos.seafile.SeafileUtils;
import org.openthos.seafile.seaapp.monitor.FileMonitorService;
import org.openthos.seafile.seaapp.ssl.CertsManager;
import org.openthos.seafile.seaapp.transfer.PendingUploadInfo;
import org.openthos.seafile.seaapp.transfer.TransferService;
import org.openthos.seafile.seaapp.transfer.DownloadTaskManager;
import org.openthos.seafile.seaapp.ToastUtil;

public class SeafileActivity extends FragmentActivity {
    public static SeafileActivity mActivity;
    private ListView mListView;
    private GridView mGridView;
    public static SeafItemAdapter mAdapter;
    public static Account mAccount;
    public static DataManager mDataManager;
    private GenericListener mGenericListener;
    public static TransferService txService = null;
    private ArrayList<PendingUploadInfo> pendingUploads = new ArrayList<>();
    public static FragmentTransaction mTransaction;
    public static NavContext mNavContext = new NavContext();
    public static List<Object> mStoredViews = new ArrayList<>();
    public static FileDialog mFileDialog;
    public String mViewTag = null;
    public static String TAG_LIST = "list";
    public static String TAG_GRID = "grid";
    public static DownloadTaskManager mDownloadTaskManager = new DownloadTaskManager();

    public static Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                    if (mFileDialog != null && mFileDialog.isShowing()) {
                        mFileDialog.dismiss();
                    }
                    String filePath = (String) msg.obj;
                    ToastUtil.showSingletonToast(SeafileActivity.mActivity,
                            SeafileActivity.mActivity.getString(R.string.download_finished)
                                    + "  " + filePath);
            }
            super.handleMessage(msg);
        }
    };


    ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            TransferService.TransferBinder binder = (TransferService.TransferBinder) service;
            txService = binder.getService();

            for (PendingUploadInfo info : pendingUploads) {
                txService.addTaskToUploadQue(mAccount,
                        info.repoID,
                        info.repoName,
                        info.targetDir,
                        info.localFilePath,
                        info.isUpdate,
                        info.isCopyToLocal);
            }
            pendingUploads.clear();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            txService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_seafile);
        mActivity = this;
        mTransaction = getFragmentManager().beginTransaction();
        Intent txIntent = new Intent(this, TransferService.class);
        startService(txIntent);
        // bind transfer service
        Intent bIntent = new Intent(this, TransferService.class);
        bindService(bIntent, mConnection, Context.BIND_AUTO_CREATE);
//        Intent monitorIntent = new Intent(this, FileMonitorService.class);
//        startService(monitorIntent);
        init();
    }

    @Override
    protected void onDestroy() {
        if (txService != null) {
            unbindService(mConnection);
            txService = null;
        }
        super.onDestroy();
    }

    private void init() {
        mGenericListener = new GenericListener();
        mAdapter = new SeafItemAdapter(this);
        mListView = (ListView) findViewById(R.id.lv);
        mGridView = (GridView) findViewById(R.id.gv);
        mListView.setOnTouchListener(mGenericListener);
        mListView.setAdapter(mAdapter);
        mGridView.setOnTouchListener(mGenericListener);
        mGridView.setAdapter(mAdapter);
        switchView(TAG_LIST);
        getAccountAndLogin();
    }

    public void getAccountAndLogin() {
        SharedPreferences sp = getSharedPreferences("account",Context.MODE_PRIVATE);
        String serverURL = sp.getString("url", SeafileUtils.SEAFILE_URL_LIBRARY);
        String email = sp.getString("user", "");
        String passwd = sp.getString("password", "");
        mAccount = new Account(serverURL, email, null, false, null);
        mDataManager = new DataManager(mAccount);
        ConcurrentAsyncTask.execute(new LoginTask(mAccount, passwd, null,false));
    }

    private class LoginTask extends AsyncTask<Void, Void, String> {
        Account loginAccount;
        SeafException err = null;
        String passwd;
        String authToken;
        boolean rememberDevice;

        public LoginTask(Account loginAccount, String passwd, String authToken, boolean rememberDevice) {
            this.loginAccount = loginAccount;
            this.passwd = passwd;
            this.authToken = authToken;
            this.rememberDevice = rememberDevice;
        }

        @Override
        protected void onPreExecute() {
            //super.onPreExecute();
//            progressDialog.show();
        }

        @Override
        protected String doInBackground(Void... params) {
            if (params.length != 0)
                return "Error number of parameter";

            return doLogin();
        }

        private void resend() {
//            ConcurrentAsyncTask.execute(new AccountDetailActivity.LoginTask(loginAccount, passwd, authToken, rememberDevice));
        }

        @Override
        protected void onPostExecute(final String result) {

            if (result != null && result.equals("Success")) {
                ConcurrentAsyncTask.execute(new LoadTask(mDataManager));
            }
        }

        private String doLogin() {
            SeafConnection sc = new SeafConnection(loginAccount);

            try {
                // if successful, this will place the auth token into "loginAccount"
                if (!sc.doLogin(passwd, authToken, rememberDevice))
                    return getString(R.string.err_login_failed);

                // fetch email address from the server
                DataManager manager = new DataManager(loginAccount);
                AccountInfo accountInfo = manager.getAccountInfo();

                if (accountInfo == null)
                    return "Unknown error";

                // replace email address/username given by the user with the address known by the server.
                loginAccount = new Account(loginAccount.server, accountInfo.getEmail(), loginAccount.token, false, loginAccount.sessionKey);

                return "Success";

            } catch (SeafException e) {
                err = e;
                if (e == SeafException.sslException) {
                    return getString(R.string.ssl_error);
                } else if (e == SeafException.twoFactorAuthTokenMissing) {
                    return getString(R.string.two_factor_auth_error);
                } else if (e == SeafException.twoFactorAuthTokenInvalid) {
                    return getString(R.string.two_factor_auth_invalid);
                }
                switch (e.getCode()) {
                    case HttpURLConnection.HTTP_BAD_REQUEST:
                        return getString(R.string.err_wrong_user_or_passwd);
                    case HttpURLConnection.HTTP_NOT_FOUND:
                        return getString(R.string.invalid_server_address);
                    default:
                        return e.getMessage();
                }
            } catch (JSONException e) {
                return e.getMessage();
            }
        }
    }

    private class LoadTask extends AsyncTask<Void, Void, List<SeafRepo>> {
        SeafException err = null;
        DataManager dataManager;

        public LoadTask(DataManager dataManager) {
            this.dataManager = dataManager;
        }

        @Override
        protected void onPreExecute() {

        }

        @Override
        protected List<SeafRepo> doInBackground(Void... params) {
            try {
                return dataManager.getReposFromServer();
            } catch (SeafException e) {
                err = e;
                return null;
            }
        }

        private void displaySSLError() {

        }

        private void resend() {
            new LoadTask(dataManager);
        }

        // onPostExecute displays the results of the AsyncTask.
        @Override
        protected void onPostExecute(List<SeafRepo> rs) {
            // Prompt the user to accept the ssl certificate
            if (err == SeafException.sslException) {
                SslConfirmDialog dialog = new SslConfirmDialog(dataManager.getAccount(),
                        new SslConfirmDialog.Listener() {
                            @Override
                            public void onAccepted(boolean rememberChoice) {
                                Account account = dataManager.getAccount();
                                CertsManager.instance().saveCertForAccount(account, rememberChoice);
                                resend();
                            }

                            @Override
                            public void onRejected() {
                                displaySSLError();
                            }
                        });
//                dialog.show(getFragmentManager(), SslConfirmDialog.FRAGMENT_TAG);
                return;
            } else if (err == SeafException.remoteWipedException) {
//                mActivity.completeRemoteWipe();
            }

            if (err != null) {
                if (err.getCode() == HttpURLConnection.HTTP_UNAUTHORIZED) {
                    // Token expired, should login again
//                    mActivity.showShortToast(mActivity, R.string.err_token_expired);
//                    mActivity.logoutWhenTokenExpired();
                } else {
//                    Log.e(DEBUG_TAG, "failed to load repos: " + err.getMessage());
//                    showError(R.string.error_when_load_repos);
                    return;
                }
            }

            if (rs != null) {
                mStoredViews.add(rs);
                dataManager.setReposRefreshTimeStamp();
                mAdapter.setItemsAndRefresh(rs);

            } else {
//                Log.i(DEBUG_TAG, "failed to load repos");
//                showError(R.string.error_when_load_repos);
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (mStoredViews.size() > 1) {
            mStoredViews.remove(mStoredViews.size() - 1);
            Object o = mStoredViews.get(mStoredViews.size() - 1);
            if (o instanceof List) {
                mAdapter.setItemsAndRefresh((List) o);
            } else if (o instanceof SeafRepo) {

            }

        } else {
//            super.onBackPressed();
        }
    }

    public void switchView(String tag) {
        mViewTag = tag;
        if (TAG_LIST.equals(mViewTag)) {
            mListView.setVisibility(View.VISIBLE);
            mGridView.setVisibility(View.GONE);
        } else if (TAG_GRID.equals(mViewTag)) {
            mListView.setVisibility(View.GONE);
            mGridView.setVisibility(View.VISIBLE);
        }
    }
}
