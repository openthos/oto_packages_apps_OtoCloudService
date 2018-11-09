package org.openthos.seafile.seaapp;

import android.view.MotionEvent;
import android.view.View;

import java.io.File;

import org.openthos.seafile.R;

public class GenericListener implements View.OnTouchListener{
    public Object mCurParent;
    private Long mLastClickTime;
    private long DOUBLE_CLICK_INTERVAL_TIME = 1000; // 1.0 second
    private SeafileActivity mActivity;
    private View mTempView;

    public GenericListener(SeafileActivity activity) {
        mActivity = activity;
    }

    @Override
    public boolean onTouch(View view, MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (event.getButtonState() == MotionEvent.BUTTON_PRIMARY) {
                    switch (view.getId()) {
                        case R.id.list_item_container:
                            if (mTempView == view && (Math.abs(System.currentTimeMillis())
                                    - mLastClickTime <= DOUBLE_CLICK_INTERVAL_TIME)) {
                                if (mTempView != null) {
                                    mTempView.setSelected(false);
                                    mTempView = null;
                                }
                                open(view.getTag());
                                break;
                            }
                            if (mTempView != null) {
                                mTempView.setSelected(false);
                            }
                            mTempView = view;
                            mTempView.setSelected(true);
                            mLastClickTime = System.currentTimeMillis();
                            break;
                        case R.id.lv:
                        case R.id.gv:
                            if (mTempView != null) {
                                mTempView.setSelected(false);
                                mTempView = null;
                            }
                            mLastClickTime = null;
                            break;
                    }
                } else if (event.getButtonState() == MotionEvent.BUTTON_SECONDARY) {
                    // app getX, getY, openthos getRawX, getRawY
                    int x = 0;
                    int y = 0;
                    mLastClickTime = null;
                    switch (view.getId()) {
                        case R.id.list_item_container:
                            x = (int) event.getRawX();
                            y = (int) event.getRawY();
                            if (mTempView != null) {
                                mTempView.setSelected(false);
                            }
                            mTempView = view;
                            mTempView.setSelected(true);
                            if (mActivity.mStoredViews.size() > 1) {
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
                            if (mTempView != null) {
                                mTempView.setSelected(false);
                                mTempView = null;
                            }
                            if (mActivity.mStoredViews.size() > 1) {
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
        mActivity.mMenuDialog = new MenuDialog(mActivity, type);
        mActivity.mMenuDialog.showDialog(x, y, seaf);
    }

    public void open(Object seaf) {
        if (!Utils.isNetworkOn(mActivity)) {
            ToastUtil.showSingletonToast(mActivity,
                    mActivity.getString(R.string.network_down));
            mActivity.showDirentError();
        } else {
            if (seaf instanceof SeafRepo) {
                openLibrary((SeafRepo) seaf);
            } else if (seaf instanceof SeafDirent) {
                openFile((SeafDirent) seaf);
            }
        }
    }

    private void openLibrary(SeafRepo seafRepo) {
        mActivity.mLoadingDialog = new LoadingDialog(mActivity);
        mActivity.mLoadingDialog.show();
        mCurParent = seafRepo;
        NavContext navContext = mActivity.getNavContext();
        navContext.setDirPermission(seafRepo.permission);
        navContext.setRepoID(seafRepo.id);
        navContext.setRepoName(seafRepo.getName());
        navContext.setDir("/", seafRepo.root);
        loadDir();
    }

    private void openFile(SeafDirent seafDirent) {
        NavContext navContext = mActivity.getNavContext();
        DataManager dataManager = mActivity.getDataManager();
        if (seafDirent.isDir()) {
            mActivity.mLoadingDialog = new LoadingDialog(mActivity);
            mActivity.mLoadingDialog.show();
            mCurParent = seafDirent;
            String currentPath = navContext.getDirPath();
            String newPath = currentPath.endsWith("/") ?
                    currentPath + seafDirent.name : currentPath + "/" + seafDirent.name;
            navContext.setDir(newPath, seafDirent.id);
            navContext.setDirPermission(seafDirent.permission);
            loadDir();
        } else {
            String fileName= seafDirent.name;
            navContext.fileName = fileName;
            long fileSize = seafDirent.size;
            String repoName = navContext.getRepoName();
            String repoID = navContext.getRepoID();
            String dirPath = navContext.getDirPath();
            String filePath = Utils.pathJoin(navContext.getDirPath(), fileName);
            String localPath = Utils.pathJoin(dataManager.getRepoDir(repoName, repoID), filePath);
            File localFile = new File(localPath);
            if (localFile.exists() && (localFile.length() == fileSize)) {
                IntentBuilder.viewFile(mActivity, localPath);
            } else {
                if (localFile.exists()) {
                    localFile.delete();
                }
                mActivity.showDownloadFileDialog(filePath);
            }
        }
    }

    private void loadDir() {
        mActivity.refreshDirent();
    }
}
