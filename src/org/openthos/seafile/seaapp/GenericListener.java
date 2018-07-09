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
    private Long mLastClickTime;
    private long DOUBLE_CLICK_INTERVAL_TIME = 1000; // 1.0 second
    private NavContext mNavContext;


    @Override
    public boolean onTouch(View view, MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
//                if (event.getButtonState() == MotionEvent.BUTTON_PRIMARY) {
                    switch (view.getId()) {
                        case R.id.list_item_container:
                            if (mPreSeaf ==  view.getTag()
                                    && (Math.abs(System.currentTimeMillis()) - mLastClickTime <= 1000)) {
                                // enter
//                                Toast.makeText(SeafileActivity.mActivity, "111", Toast.LENGTH_SHORT).show();
                                open(mPreSeaf);
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


//                } else if (event.getButtonState() == MotionEvent.BUTTON_SECONDARY) {


//                }

//                break;
        }



        return false;
    }

    private void open(Object seaf) {
        if (mNavContext == null) {
            mNavContext = SeafileActivity.mNavContext;
        }
        if (seaf instanceof SeafRepo) {
            openLibrary((SeafRepo) seaf);
        } else if (seaf instanceof SeafDirent) {
            openFile((SeafDirent) seaf);
        }
    }

    private void openLibrary(SeafRepo seafRepo) {
        mNavContext.setDirPermission(seafRepo.permission);
        mNavContext.setRepoID(seafRepo.id);
        mNavContext.setRepoName(seafRepo.getName());
        mNavContext.setDir("/", seafRepo.root);
        loadDir();
    }

    private void openFile(SeafDirent seafDirent) {
        if (seafDirent.isDir()) {
            SeafileActivity.mActivity.mStoredViews.add(seafDirent);
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
                int taskID = SeafileActivity.mDownloadTaskManager
                        .addTask(SeafileActivity.mAccount, repoName, repoID, filePath, fileSize);
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
            if (!myRepoID.equals(mNavContext.getRepoID()) || !myPath.equals(mNavContext.getDirPath())) {
                return;
            }

            ConcurrentAsyncTask.execute(new LoadDirTask(dataManager), myRepoName, myRepoID, myPath);
        }

        private void displaySSLError() {

            if (!myRepoID.equals(mNavContext.getRepoID()) || !myPath.equals(mNavContext.getDirPath())) {
                return;
            }
//            showError(R.string.ssl_error);
        }

        // onPostExecute displays the results of the AsyncTask.
        @Override
        protected void onPostExecute(List<SeafDirent> dirents) {
                String lastUpdate = dataManager.getLastPullToRefreshTime(DataManager.PULL_TO_REFRESH_LAST_TIME_FOR_REPOS_FRAGMENT);
                //mListView.onRefreshComplete(lastUpdate);
//                refreshLayout.setRefreshing(false);
//                getDataManager().saveLastPullToRefreshTime(System.currentTimeMillis(), DataManager.PULL_TO_REFRESH_LAST_TIME_FOR_REPOS_FRAGMENT);
//                mPullToRefreshStopRefreshing = 0;

            if (!myRepoID.equals(mNavContext.getRepoID()) || !myPath.equals(mNavContext.getDirPath())) {
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
        }
    }
}
