package org.openthos.seafile.seaapp;

import android.content.Context;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.FragmentActivity;
import android.view.View;
import android.widget.GridView;
import android.widget.ListView;
import android.widget.TextView;

import org.json.JSONException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.File;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;

import org.openthos.seafile.R;
import org.openthos.seafile.SeafileUtils;
import org.openthos.seafile.SeafileAccount;
import org.openthos.seafile.seaapp.transfer.DownloadTaskManager;

public class SeafileActivity extends FragmentActivity {
    private ListView mListView;
    private GridView mGridView;
    private TextView mErrorText;
    private SeafItemAdapter mAdapter;
    private Account mAccount;
    private DataManager mDataManager;
    public GenericListener mGenericListener;
    private NavContext mNavContext;
    public List<Object> mStoredViews;
    public FileDialog mFileDialog;
    private String mViewTag = null;
    private String TAG_LIST = "list";
    private String TAG_GRID = "grid";
    private String TAG_NEW_REPO_DIALOG = "NewRepoDialog";
    private String TAG_RENAME_REPO_DIALOG = "RenameRepoDialog";
    private String TAG_DELETE_REPO_DIALOG = "DeleteRepoDialog";
    private String TAG_NEW_FILE_DIALOG = "NewFileDialog";
    private String TAG_CHOOSE_APP_DIALOG = "ChooseAppDialog";
    private String TAG_CHARE_LINK_PASSWORD_DIALOG = "ChareLinkPasswordDialog";
    private String TAG_PASSWORD_DIALOG = "PasswordDialog";
    private String TAG_OPEN_FILE_DIALOG = "OpenFileDialog";
    private String TAG_RENAME_FILE_DIALOG = "RenameFileDialog";
    private String TAG_DELETE_FILE_DIALOG = "DeleteFileDialog";

    public MenuDialog mMenuDialog;
    private DownloadTaskManager mDownloadTaskManager;
    private UploadFileDialog mUploadFileDialog;
    private DownloadFileDialog mDownloadFileDialog;
    public LoadingDialog mLoadingDialog;
    private List<String> mCurDirNames = new ArrayList<>();
    private List<String> mCurFileNames = new ArrayList<>();
    private String mServerURL = SeafileUtils.SEAFILE_URL_LIBRARY;
    private String mUserId = "";
    private String mPassword = "";

    private SeaHandler mHandler = new SeaHandler();

    public class SeaHandler extends Handler{
        public final int MSG_WHAT_DOWNLOAD_FINISHED = 1;
        public final int MSG_WHAT_LOAD_FINISHED = 2;

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_WHAT_DOWNLOAD_FINISHED:
                    if (mDownloadFileDialog != null && mDownloadFileDialog.isShowing()) {
                        mDownloadFileDialog.dismiss();
                    }
                    String filePath = (String) msg.obj;
                    ToastUtil.showSingletonToast(SeafileActivity.this,
                            SeafileActivity.this.getString(R.string.download_finished)
                                    + "  " + filePath);
                    break;
                case MSG_WHAT_LOAD_FINISHED:
                    mErrorText.setVisibility(View.GONE);
                    if (mLoadingDialog != null && mLoadingDialog.isShowing()) {
                        mLoadingDialog.dismiss();
                    }
            }
            super.handleMessage(msg);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_seafile);
        mNavContext = new NavContext();
        mStoredViews = new ArrayList<>();
        mDownloadTaskManager = new DownloadTaskManager(this);
        init();
    }

    private void init() {
        mErrorText = (TextView) findViewById(R.id.error_message);
        mGenericListener = new GenericListener(this);
        mAdapter = new SeafItemAdapter(this);
        mListView = (ListView) findViewById(R.id.lv);
        mGridView = (GridView) findViewById(R.id.gv);
        mListView.setOnTouchListener(mGenericListener);
        mListView.setAdapter(mAdapter);
        mGridView.setOnTouchListener(mGenericListener);
        mGridView.setAdapter(mAdapter);
        SeafileAccount account = org.openthos.seafile.Utils.readAccount(this);
        if (account.isExistsAccount()) {
            mServerURL = account.mOpenthosUrl;
            mUserId = account.mUserName;
            mPassword = account.mUserPassword;
            getAccountAndLogin();
        }
        findViewById(R.id.back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v){
                onBackPressed();
            }
        });
    }

    public void getAccountAndLogin() {
        mAccount = new Account(mServerURL, mUserId, null, false, null);
        mDataManager = new DataManager(mAccount, this);
        ConcurrentAsyncTask.execute(new LoginTask(mAccount, mPassword, null,false));
        if (!Utils.isNetworkOn(this)) {
            ToastUtil.showSingletonToast(this, getString(R.string.network_down));
            showRepoError();
        } else {
            switchView(TAG_LIST);
            mLoadingDialog = new LoadingDialog(this);
            mLoadingDialog.show();
            ConcurrentAsyncTask.execute(new LoginTask(mAccount, mPassword, null, false));
        }
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
        }

        @Override
        protected void onPostExecute(final String result) {
            if (result != null && result.equals("Success")) {
                refreshRepo();
            }
        }

        private String doLogin() {
            SeafConnection sc = new SeafConnection(loginAccount, SeafileActivity.this);

            try {
                // if successful, this will place the auth token into "loginAccount"
                if (!sc.doLogin(passwd, authToken, rememberDevice))
                    return getString(R.string.err_login_failed);

                // fetch email address from the server
                DataManager manager = new DataManager(loginAccount, SeafileActivity.this);
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
            // filter library of "DATA" and "UserConfig"
            for (int i = 0; i < rs.size(); i++) {
                String name  = rs.get(i).getName();
                if (name.equals("DATA") || name.equals("UserConfig")) {
                    rs.remove(i);
                    i--;
                }
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
            if (mNavContext.inRepo()) {
                if (mNavContext.isRepoRoot()) {
                    mNavContext.setRepoID(null);
                } else {
                    String parentPath = Utils.getParentPath(mNavContext
                            .getDirPath());
                    mNavContext.setDir(parentPath, null);
                }
            }
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
        final NewRepoDialog dialog = new NewRepoDialog(this);
        dialog.init(mAccount);
        dialog.setTaskDialogLisenter(new TaskDialog.TaskDialogListener() {
            @Override
            public void onTaskSuccess(){
                refreshRepo();
                ToastUtil.showSingletonToast(SeafileActivity.this, String.format(getResources()
                        .getString(R.string.create_new_repo_success), dialog.getRepoName()));
            }
        });
        dialog.show(getSupportFragmentManager(), TAG_NEW_REPO_DIALOG);
    }

    public void showRenameRepoDialog(String repoID, String repoName) {
        final RenameRepoDialog dialog = new RenameRepoDialog(this);
        dialog.init(repoID, repoName, mAccount);
        dialog.setTaskDialogLisenter(new TaskDialog.TaskDialogListener() {
            @Override
            public void onTaskSuccess() {
                refreshRepo();
                ToastUtil.showSingletonToast(SeafileActivity.this, getString(R.string.rename_successful));
            }
        });
        dialog.show(getSupportFragmentManager(), TAG_RENAME_REPO_DIALOG);
    }

    public void deleteRepoDialog(String repoID) {
        final DeleteRepoDialog dialog = new DeleteRepoDialog(this);
        dialog.init(repoID, mAccount);
        dialog.setTaskDialogLisenter(new TaskDialog.TaskDialogListener() {
            @Override
            public void onTaskSuccess() {
                refreshRepo();
                ToastUtil.showSingletonToast(SeafileActivity.this, getString(R.string.delete_successful));
            }
        });
        dialog.show(getSupportFragmentManager(), TAG_DELETE_REPO_DIALOG);
    }

    public void showNewFileDialog() {
        final NewFileDialog dialog = new NewFileDialog(this);
        dialog.init(mNavContext.getRepoID(), mNavContext.getDirPath(), mAccount);
        dialog.setTaskDialogLisenter(new TaskDialog.TaskDialogListener() {
            @Override
            public void onTaskSuccess() {
                refreshDirent();
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
                WidgetUtils.chooseShareApp(SeafileActivity.this, repoID, path, isDir, mAccount, null, null);
            }
        }).show();
    }

    public void showNewDirDialog() {
        final NewDirDialog dialog = new NewDirDialog(this);
        dialog.init(mNavContext.getRepoID(), mNavContext.getDirPath(), mAccount);
        dialog.setTaskDialogLisenter(new TaskDialog.TaskDialogListener() {
            @Override
            public void onTaskSuccess() {
                refreshDirent();
                final String message = String.format(getString(R.string.create_new_folder_success), dialog.getNewDirName());
                ToastUtil.showSingletonToast(SeafileActivity.this, message);
            }
        });
        dialog.show(getSupportFragmentManager(), "NewDirDialogFragment");
    }

    public void showRenameFileDialog(String repoID, String path, boolean isdir) {
        final RenameFileDialog dialog = new RenameFileDialog(this);
        dialog.init(repoID, path, isdir, mAccount);
        dialog.setTaskDialogLisenter(new TaskDialog.TaskDialogListener() {
            @Override
            public void onTaskSuccess() {
                refreshDirent();
                ToastUtil.showSingletonToast(
                        SeafileActivity.this, getString(R.string.rename_successful));
            }
        });
        dialog.show(getSupportFragmentManager(), TAG_RENAME_FILE_DIALOG);
    }

    public void showDeleteFileDialog(final String repoID, String path, boolean isdir) {
        final DeleteFileDialog dialog = new DeleteFileDialog(this);
        dialog.init(repoID, path, isdir, mAccount);
        dialog.setTaskDialogLisenter(new TaskDialog.TaskDialogListener() {
            @Override
            public void onTaskSuccess() {
                refreshDirent();
                ToastUtil.showSingletonToast(
                        SeafileActivity.this, getString(R.string.delete_successful));
            }
        });
        dialog.show(getSupportFragmentManager(), TAG_DELETE_FILE_DIALOG);
    }

    public void showUploadFileDialog(File file) {
        mUploadFileDialog = new UploadFileDialog(this,
                mNavContext.getRepoName(), mNavContext.getRepoID(), file);
        mUploadFileDialog.show();
    }

    public void showDownloadFileDialog(String filePath) {
        mDownloadFileDialog = new DownloadFileDialog(this,
                mNavContext.getRepoName(), mNavContext.getRepoID(), filePath);
        mDownloadFileDialog.show();
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
            if (!myRepoID.equals(mNavContext.getRepoID())
                    || !myPath.equals(mNavContext.getDirPath())) {
                return;
            }

            ConcurrentAsyncTask.execute(new LoadDirTask(dataManager), myRepoName, myRepoID, myPath);
        }

        private void displaySSLError() {
            if (!myRepoID.equals(mNavContext.getRepoID())
                    || !myPath.equals(mNavContext.getDirPath())) {
                return;
            }
            ToastUtil.showSingletonToast(SeafileActivity.this,getString(R.string.ssl_error));
        }

        // onPostExecute displays the results of the AsyncTask.
        @Override
        protected void onPostExecute(List<SeafDirent> dirents) {
            if (err != null) {
                if (err.getCode() == SeafConnection.HTTP_STATUS_REPO_PASSWORD_REQUIRED) {
                    // showPasswordDialog();
                } else if (err.getCode() == HttpURLConnection.HTTP_UNAUTHORIZED) {
                    // Token expired, should login again
                    ToastUtil.showSingletonToast(SeafileActivity.this,getString(R.string.err_token_expired));
                } else if (err.getCode() == HttpURLConnection.HTTP_NOT_FOUND) {
                    final String message = String.format(getString(R.string.op_exception_folder_deleted), myPath);
                    ToastUtil.showSingletonToast(SeafileActivity.this, message);
                } else {
                    err.printStackTrace();
                    ToastUtil.showSingletonToast(SeafileActivity.this,getString(R.string.error_when_load_dirents));
                }
                return;
            }

            if (dirents == null) {
            ToastUtil.showSingletonToast(SeafileActivity.this,getString(R.string.error_when_load_dirents));
                return;
            }
            mDataManager.setDirsRefreshTimeStamp(myRepoID, myPath);
            mAdapter.setItemsAndRefresh(dirents);
            mStoredViews.add(dirents);
            setBackTag();
        }
    }

    public void refreshRepo() {
        mStoredViews.clear();
        ConcurrentAsyncTask.execute(new LoadTask(mDataManager));
    }

    public void refreshDirent() {
        ConcurrentAsyncTask.execute(new LoadDirTask(mDataManager),
                mNavContext.getRepoName(),
                mNavContext.getRepoID(),
                mNavContext.getDirPath());
    }

    public void showRepoError() {
        mListView.setVisibility(View.GONE);
        mGridView.setVisibility(View.GONE);
        mErrorText.setText(getString(R.string.error_when_load_repos));
        mErrorText.setVisibility(View.VISIBLE);
        mErrorText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!Utils.isNetworkOn(SeafileActivity.this)) {
                    ToastUtil.showSingletonToast(SeafileActivity.this, getString(R.string.network_down));
                } else {
                    switchView(TAG_LIST);
                    mLoadingDialog = new LoadingDialog(SeafileActivity.this);
                    mLoadingDialog.show();
                    ConcurrentAsyncTask.execute(new LoginTask(mAccount, mPassword, null,false));
                }
            }
        });
    }

    public void showDirentError() {
        mListView.setVisibility(View.GONE);
        mGridView.setVisibility(View.GONE);
        mErrorText.setText(getString(R.string.error_when_load_dirents));
        mErrorText.setVisibility(View.VISIBLE);
        mErrorText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!Utils.isNetworkOn(SeafileActivity.this)) {
                    ToastUtil.showSingletonToast(SeafileActivity.this, getString(R.string.network_down));
                } else {
                    switchView(TAG_LIST);
                    mLoadingDialog = new LoadingDialog(SeafileActivity.this);
                    mLoadingDialog.show();
                    refreshDirent();
                }
            }
        });
    }

    public SeaHandler getHandler() {
        return mHandler;
    }

    public DataManager getDataManager() {
        return mDataManager;
    }

    public NavContext getNavContext() {
        return mNavContext;
    }

    public Account getAccount() {
        return mAccount;
    }

    public DownloadTaskManager getDownloadTaskManager() {
        return mDownloadTaskManager;
    }

    public List<String> getCurDirNames() {
        return mCurDirNames;
    }

    public List<String> getCurFileNames() {
        return mCurFileNames;
    }
}
