package org.openthos.seafile.seaapp;

import android.content.Context;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
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

import java.io.IOException;
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
    public static NavContext mNavContext;
    public static List<Object> mStoredViews;
    public static FileDialog mFileDialog;
    public String mViewTag = null;
    public static String TAG_LIST = "list";
    public static String TAG_GRID = "grid";
    public static String TAG_NEW_REPO_DIALOG = "NewRepoDialog";
    public static String TAG_RENAME_REPO_DIALOG = "RenameRepoDialog";
    public static final String TAG_DELETE_REPO_DIALOG = "DeleteRepoDialog";
    public static final String TAG_NEW_FILE_DIALOG = "NewFileDialog";
    public static final String TAG_CHOOSE_APP_DIALOG = "ChooseAppDialog";
    public static final String TAG_CHARE_LINK_PASSWORD_DIALOG = "ChareLinkPasswordDialog";
    public static final String TAG_PASSWORD_DIALOG = "PasswordDialog";
    public static final String TAG_OPEN_FILE_DIALOG = "OpenFileDialog";
    public static final String TAG_RENAME_FILE_DIALOG = "RenameFileDialog";
    public static final String TAG_DELETE_FILE_DIALOG = "DeleteFileDialog";
    public static MenuDialog mMenuDialog;
    public static UploadFileDialog mUploadFileDialog;
    public FetchFileDialog mFetchFileDialog;
    public static DownloadTaskManager mDownloadTaskManager = new DownloadTaskManager();;

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
                    break;
                case 2:
                    if (mUploadFileDialog.isShowing()) {
                        mUploadFileDialog.dismiss();
                    }
                    String path = (String) msg.obj;
                    ToastUtil.showSingletonToast(SeafileActivity.mActivity,
                            SeafileActivity.mActivity.getString(R.string.upload_finished) + path);
                    break;
            }
            super.handleMessage(msg);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_seafile);
        mActivity = this;
        mNavContext = new NavContext();
        mStoredViews = new ArrayList<>();
        mDownloadTaskManager = new DownloadTaskManager();
        init();
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
        findViewById(R.id.back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v){
                onBackPressed();
            }
        });
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

        public LoginTask(Account loginAccount,
                String passwd, String authToken, boolean rememberDevice) {
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
                loginAccount = new Account(loginAccount.server, accountInfo.getEmail(),
                        loginAccount.token, false, loginAccount.sessionKey);

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
            mAdapter.setItemsAndRefresh((List) o);
        }
        if (mStoredViews.size() <= 1) {
            removeBackTag();
        }
    }

    public void setBackTag(){
        findViewById(R.id.back).setVisibility(View.VISIBLE);
    }

    public void removeBackTag(){
        findViewById(R.id.back).setVisibility(View.GONE);
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

    public void showNewRepoDialog() {
        final NewRepoDialog dialog = new NewRepoDialog();
        dialog.init(mAccount);
        dialog.setTaskDialogLisenter(new TaskDialog.TaskDialogListener() {
            @Override
            public void onTaskSuccess(){
                mStoredViews.clear();
                ConcurrentAsyncTask.execute(new LoadTask(mDataManager));
                ToastUtil.showSingletonToast(SeafileActivity.this, String.format(getResources()
                        .getString(R.string.create_new_repo_success), dialog.getRepoName()));
            }
        });
        dialog.show(getSupportFragmentManager(), TAG_NEW_REPO_DIALOG);
    }

    public void showRenameRepoDialog(String repoID, String repoName) {
        final RenameRepoDialog dialog = new RenameRepoDialog();
        dialog.init(repoID, repoName, mAccount);
        dialog.setTaskDialogLisenter(new TaskDialog.TaskDialogListener() {
            @Override
            public void onTaskSuccess() {
                mStoredViews.clear();
                ConcurrentAsyncTask.execute(new LoadTask(mDataManager));
                ToastUtil.showSingletonToast(
                        SeafileActivity.this, getString(R.string.rename_successful));
            }
        });
        dialog.show(getSupportFragmentManager(), TAG_RENAME_REPO_DIALOG);
    }

    public void deleteRepoDialog(String repoID) {
        final DeleteRepoDialog dialog = new DeleteRepoDialog();
        dialog.init(repoID, mAccount);
        dialog.setTaskDialogLisenter(new TaskDialog.TaskDialogListener() {
            @Override
            public void onTaskSuccess() {
                mStoredViews.clear();
                ConcurrentAsyncTask.execute(new LoadTask(mDataManager));
                ToastUtil.showSingletonToast(
                        SeafileActivity.this, getString(R.string.delete_successful));
            }
        });
        dialog.show(getSupportFragmentManager(), TAG_DELETE_REPO_DIALOG);
    }

    public void showNewFileDialog() {
        final NewFileDialog dialog = new NewFileDialog();
        dialog.init(mNavContext.getRepoID(), mNavContext.getDirPath(), mAccount);
        dialog.setTaskDialogLisenter(new TaskDialog.TaskDialogListener() {
            @Override
            public void onTaskSuccess() {
                ConcurrentAsyncTask.execute(new LoadDirTask(mDataManager),
                        mNavContext.getRepoName(),
                        mNavContext.getRepoID(),
                        mNavContext.getDirPath());
                final String message = String.format(getString(
                        R.string.create_new_file_success), dialog.getNewFileName());
                ToastUtil.showSingletonToast(SeafileActivity.this, message);
            }
        });
        dialog.show(getSupportFragmentManager(), TAG_NEW_FILE_DIALOG);
    }

    public void showShareDialog(final SeafDirent dirent) {
        final String repoID = mNavContext.getRepoID();
        final String dir = mNavContext.getDirPath();
        final String path = Utils.pathJoin(dir, dirent.name);
        final boolean isDir = dirent.isDir();

        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        String[] strings = getResources().getStringArray(R.array.file_action_share_array_zh);
        builder.setItems(strings, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                WidgetUtils.chooseShareApp(SeafileActivity.this,
                        repoID, path, isDir, mAccount, null, null);
            }
        }).show();
    }

    public void showNewDirDialog() {
        final NewDirDialog dialog = new NewDirDialog();
        dialog.init(mNavContext.getRepoID(), mNavContext.getDirPath(), mAccount);
        dialog.setTaskDialogLisenter(new TaskDialog.TaskDialogListener() {
            @Override
            public void onTaskSuccess() {
                ConcurrentAsyncTask.execute(new LoadDirTask(mDataManager),
                        mNavContext.getRepoName(),
                        mNavContext.getRepoID(),
                        mNavContext.getDirPath());
                final String message = String.format(
                        getString(R.string.create_new_folder_success), dialog.getNewDirName());
                ToastUtil.showSingletonToast(SeafileActivity.this, message);
            }
        });
        dialog.show(getSupportFragmentManager(), "NewDirDialogFragment");
    }

    public void showRenameFileDialog(String repoID, String path, boolean isdir) {
        final RenameFileDialog dialog = new RenameFileDialog();
        dialog.init(repoID, path, isdir, mAccount);
        dialog.setTaskDialogLisenter(new TaskDialog.TaskDialogListener() {
            @Override
            public void onTaskSuccess() {
                ConcurrentAsyncTask.execute(new LoadDirTask(mDataManager),
                        mNavContext.getRepoName(),
                        mNavContext.getRepoID(),
                        mNavContext.getDirPath());
                ToastUtil.showSingletonToast(
                        SeafileActivity.this, getString(R.string.rename_successful));
            }
        });
        dialog.show(getSupportFragmentManager(), TAG_RENAME_FILE_DIALOG);
    }

    public void fetchFileAndExport(final ResolveInfo appInfo, final Intent intent,
                                   final String repoName, final String repoID,
                                   final String path, final long fileSize) {

        mFetchFileDialog = new FetchFileDialog();
        mFetchFileDialog.init(
                repoName, repoID, path, fileSize, new FetchFileDialog.FetchFileListener() {
            @Override
            public void onSuccess() {
                startActivity(intent);
            }

            @Override
            public void onDismiss() {
                mFetchFileDialog = null;
            }

            @Override
            public void onFailure(SeafException err) {
            }
        });
        mFetchFileDialog.show(getSupportFragmentManager(), TAG_OPEN_FILE_DIALOG);
    }

    public PasswordDialog showPasswordDialog(String repoName, String repoID,
                                             TaskDialog.TaskDialogListener listener,
                                             String password) {
        PasswordDialog passwordDialog = new PasswordDialog();
        passwordDialog.setRepo(repoName, repoID, mAccount);
        if (password != null) {
            passwordDialog.setPassword(password);
        }
        passwordDialog.setTaskDialogLisenter(listener);
        passwordDialog.show(getSupportFragmentManager(), TAG_PASSWORD_DIALOG);
        return passwordDialog;
    }

    public PasswordDialog showPasswordDialog(String repoName, String repoID,
                                             TaskDialog.TaskDialogListener listener) {
        return showPasswordDialog(repoName, repoID, listener, null);
    }

    public void showDeleteFileDialog(final String repoID, String path, boolean isdir) {
        final DeleteFileDialog dialog = new DeleteFileDialog();
        dialog.init(repoID, path, isdir, mAccount);
        dialog.setTaskDialogLisenter(new TaskDialog.TaskDialogListener() {
            @Override
            public void onTaskSuccess() {
                ConcurrentAsyncTask.execute(new LoadDirTask(mDataManager),
                        mNavContext.getRepoName(),
                        mNavContext.getRepoID(),
                        mNavContext.getDirPath());
                ToastUtil.showSingletonToast(
                        SeafileActivity.this, getString(R.string.delete_successful));
            }
        });
        dialog.show(getSupportFragmentManager(), TAG_DELETE_FILE_DIALOG);
    }

    public void showUploadFileDialog(final String filePath) {
        mUploadFileDialog = new UploadFileDialog(SeafileActivity.mActivity,
                mNavContext.getRepoName(), mNavContext.getRepoID(), filePath);
        mUploadFileDialog.show();
        new Thread() {
            @Override
            public void run() {
                try {
                    String newFileID = null;
                    SeafConnection sc = new SeafConnection(mAccount);
                    newFileID = sc.uploadFile(mNavContext.getRepoID(),
                            mNavContext.getDirPath(), filePath, null, false);
                    if (newFileID != null) {
                        ConcurrentAsyncTask.execute(new LoadDirTask(mDataManager),
                                mNavContext.getRepoName(),
                                mNavContext.getRepoID(),
                                mNavContext.getDirPath());
                        Message msg = Message.obtain();
                        msg.what = 2;
                        msg.obj = filePath;
                        SeafileActivity.mHandler.sendMessage(msg);
                    }
                } catch (SeafException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

    private class LoadDirTask extends AsyncTask<String, Void, List<SeafDirent>> {

        SeafException err = null;
        String myRepoName;
        String myRepoID;
        String myPath;

        DataManager dataManager;

        public LoadDirTask(DataManager dataManager) {
            this.dataManager = dataManager;
        }

        @Override
        protected void onPreExecute() {
        }

        @Override
        protected List<SeafDirent> doInBackground(String... params) {
            if (params.length != 3) {
                return null;
            }

            myRepoName = params[0];
            myRepoID = params[1];
            myPath = params[2];
            try {
                return dataManager.getDirentsFromServer(myRepoID, myPath);
            } catch (SeafException e) {
                err = e;
                return null;
            }

        }

        private void resend() {
            if (mActivity == null)
                return;

            if (!myRepoID.equals(mNavContext.getRepoID())
                    || !myPath.equals(mNavContext.getDirPath())) {
                return;
            }

            ConcurrentAsyncTask.execute(new LoadDirTask(dataManager), myRepoName, myRepoID, myPath);
        }

        private void displaySSLError() {
            if (mActivity == null)
                return;

            if (!myRepoID.equals(mNavContext.getRepoID())
                    || !myPath.equals(mNavContext.getDirPath())) {
                return;
            }
            ToastUtil.showSingletonToast(mActivity,getString(R.string.ssl_error));
        }

        // onPostExecute displays the results of the AsyncTask.
        @Override
        protected void onPostExecute(List<SeafDirent> dirents) {
            if (mActivity == null)
                // this occurs if user navigation to another activity
                return;

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
                dialog.show(getFragmentManager(), SslConfirmDialog.FRAGMENT_TAG);
                return;
            } else if (err == SeafException.remoteWipedException) {
            }

            if (err != null) {
                if (err.getCode() == SeafConnection.HTTP_STATUS_REPO_PASSWORD_REQUIRED) {
                    // showPasswordDialog();
                } else if (err.getCode() == HttpURLConnection.HTTP_UNAUTHORIZED) {
                    // Token expired, should login again
                    ToastUtil.showSingletonToast(mActivity,getString(R.string.err_token_expired));
                } else if (err.getCode() == HttpURLConnection.HTTP_NOT_FOUND) {
                    final String message = String.format(
                            getString(R.string.op_exception_folder_deleted), myPath);
                    ToastUtil.showSingletonToast(mActivity, message);
                } else {
                    err.printStackTrace();
                    ToastUtil.showSingletonToast(
                            mActivity,getString(R.string.error_when_load_dirents));
                }
                return;
            }

            if (dirents == null) {
                ToastUtil.showSingletonToast(
                        mActivity,getString(R.string.error_when_load_dirents));
                return;
            }
            mDataManager.setDirsRefreshTimeStamp(myRepoID, myPath);
            mAdapter.setItemsAndRefresh(dirents);
        }
    }
}
