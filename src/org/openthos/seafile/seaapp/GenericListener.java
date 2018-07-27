package org.openthos.seafile.seaapp;

import android.app.FragmentTransaction;
import android.os.AsyncTask;
import android.view.MotionEvent;
import android.view.View;

import java.io.File;
import java.net.HttpURLConnection;
import java.util.List;

import org.openthos.seafile.R;
import org.openthos.seafile.seaapp.ssl.CertsManager;

public class GenericListener implements View.OnTouchListener{
    private Object mPreSeaf;
    public Object mCurParent;
    private Long mLastClickTime;
    private long DOUBLE_CLICK_INTERVAL_TIME = 1000; // 1.0 second
    private NavContext mNavContext;


    @Override
    public boolean onTouch(View view, MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (event.getButtonState() == MotionEvent.BUTTON_PRIMARY) {
                    switch (view.getId()) {
                        case R.id.list_item_container:
                            if (mPreSeaf ==  view.getTag() && (Math.abs(System.currentTimeMillis())
                                    - mLastClickTime <= DOUBLE_CLICK_INTERVAL_TIME)) {
                                open(view.getTag());
                                break;
                            }
                            mPreSeaf = view.getTag();
                            mLastClickTime = System.currentTimeMillis();
                            break;
                        case R.id.lv:
                        case R.id.gv:
                            mPreSeaf = null;
                            mLastClickTime = null;
                            break;
                    }
                } else if (event.getButtonState() == MotionEvent.BUTTON_SECONDARY) {
                    // app getX, getY, openthos getRawX, getRawY
                    int x = 0;
                    int y = 0;
                    mPreSeaf = null;
                    mLastClickTime = null;
                    switch (view.getId()) {
                        case R.id.list_item_container:
                            x = (int) event.getRawX();
                            y = (int) event.getRawY();
                            if (SeafileActivity.mStoredViews.size() > 1) {
                                SeafDirent dirent = (SeafDirent) view.getTag();
                                show("repo", x , y, dirent);
                            } else {
                                SeafRepo repo = (SeafRepo) view.getTag();
                                show("library", x , y, repo);
                            }
                            break;
                        case R.id.lv:
                        case R.id.gv:
                            x = (int) event.getRawX();
                            y = (int) event.getRawY();
                            if (SeafileActivity.mStoredViews.size() > 1) {
                                show("repo_blank", x, y, null);
                            } else {
                                show("library_blank", x, y, null);
                            }
                            break;
                    }
                }
                break;
        }
//        return false;
        return true;
    }

    private void show(String type, int x, int y, Object seaf) {
        if (mNavContext == null) {
            mNavContext = SeafileActivity.mNavContext;
        }
        SeafileActivity.mMenuDialog = new MenuDialog(SeafileActivity.mActivity, type);
        SeafileActivity.mMenuDialog.showDialog(x, y, seaf);
    }

    public void open(Object seaf) {
        if (!Utils.isNetworkOn()) {
            ToastUtil.showSingletonToast(SeafileActivity.mActivity,
                    SeafileActivity.mActivity.getString(R.string.network_down));
            SeafileActivity.mActivity.showDirentError();
        } else {
            if (mNavContext == null) {
                mNavContext = SeafileActivity.mNavContext;
            }
            if (seaf instanceof SeafRepo) {
                openLibrary((SeafRepo) seaf);
            } else if (seaf instanceof SeafDirent) {
                openFile((SeafDirent) seaf);
            }
        }
    }

    private void openLibrary(SeafRepo seafRepo) {
        SeafileActivity.mLoadingDialog = new LoadingDialog(SeafileActivity.mActivity);
        SeafileActivity.mLoadingDialog.show();
        mCurParent = seafRepo;
        mNavContext.setDirPermission(seafRepo.permission);
        mNavContext.setRepoID(seafRepo.id);
        mNavContext.setRepoName(seafRepo.getName());
        mNavContext.setDir("/", seafRepo.root);
        loadDir();
    }

    private void openFile(SeafDirent seafDirent) {
        if (seafDirent.isDir()) {
            SeafileActivity.mLoadingDialog = new LoadingDialog(SeafileActivity.mActivity);
            SeafileActivity.mLoadingDialog.show();
            mCurParent = seafDirent;
            String currentPath = mNavContext.getDirPath();
            String newPath = currentPath.endsWith("/") ?
                    currentPath + seafDirent.name : currentPath + "/" + seafDirent.name;
            mNavContext.setDir(newPath, seafDirent.id);
            mNavContext.setDirPermission(seafDirent.permission);
            loadDir();
        } else {
            String currentPath = mNavContext.getDirPath();
            String fileName= seafDirent.name;
            mNavContext.fileName = fileName;
            long fileSize = seafDirent.size;
            String repoName = mNavContext.getRepoName();
            String repoID = mNavContext.getRepoID();
            String dirPath = mNavContext.getDirPath();
            String filePath = Utils.pathJoin(mNavContext.getDirPath(), fileName);
            String localPath = Utils.pathJoin(
                    SeafileActivity.mDataManager.getRepoDir(repoName, repoID), filePath);
            File localFile = new File(localPath);
            if (localFile.exists()) {
                IntentBuilder.viewFile(SeafileActivity.mActivity, localPath);
            } else {
                SeafRepo repo = SeafileActivity.mDataManager.getCachedRepoByID(repoID);
                int taskID = SeafileActivity.mDownloadTaskManager.addTask(
                        SeafileActivity.mAccount, repoName, repoID, filePath, fileSize);
                SeafileActivity.mFileDialog = new FileDialog(
                        SeafileActivity.mActivity, repoName, repoID, filePath, taskID);
                SeafileActivity.mFileDialog.show();
            }
        }
    }

    private void loadDir() {
        ConcurrentAsyncTask.execute(new LoadDirTask(SeafileActivity.mDataManager),
                mNavContext.getRepoName(),
                mNavContext.getRepoID(),
                mNavContext.getDirPath());
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
//            showError(R.string.ssl_error);
        }

        // onPostExecute displays the results of the AsyncTask.
        @Override
        protected void onPostExecute(List<SeafDirent> dirents) {
            if (!myRepoID.equals(mNavContext.getRepoID())
                    || !myPath.equals(mNavContext.getDirPath())) {
                return;
            }

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
                return;
            } else if (err == SeafException.remoteWipedException) {
            }

            if (err != null) {
                if (err.getCode() == SeafConnection.HTTP_STATUS_REPO_PASSWORD_REQUIRED) {
                } else if (err.getCode() == HttpURLConnection.HTTP_UNAUTHORIZED) {
                    // Token expired, should login again
                } else if (err.getCode() == HttpURLConnection.HTTP_NOT_FOUND) {
                } else {
                }
                return;
            }

            if (dirents == null) {
                return;
            }
            dataManager.setDirsRefreshTimeStamp(myRepoID, myPath);
//            updateAdapterWithDirents(dirents, false);
            SeafileActivity.mAdapter.setItemsAndRefresh(dirents);
            SeafileActivity.mActivity.mStoredViews.add(dirents);
            SeafileActivity.mActivity.setBackTag();
        }
    }
}
