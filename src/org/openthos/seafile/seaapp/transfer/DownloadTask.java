package org.openthos.seafile.seaapp.transfer;


import android.os.Message;
import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import org.openthos.seafile.seaapp.Account;
import org.openthos.seafile.seaapp.DataManager;
import org.openthos.seafile.seaapp.ProgressMonitor;
import org.openthos.seafile.seaapp.SeafException;
import org.openthos.seafile.seaapp.SeafRepo;
import org.openthos.seafile.seaapp.SeafileActivity;
import org.openthos.seafile.seaapp.IntentBuilder;

/**
 * Download task
 *
 */
public class DownloadTask extends TransferTask {
    public static final String DEBUG_TAG = "DownloadTask";

    private String localPath;
    private DownloadStateListener downloadStateListener;
    private boolean updateTotal;

    public DownloadTask(int taskID, Account account, String repoName, String repoID, String path,
                        DownloadStateListener downloadStateListener) {
        super(taskID, account, repoName, repoID, path);
        this.downloadStateListener = downloadStateListener;
    }

    /**
     * When downloading a file, we don't know the file size in advance, so
     * we make use of the first progress update to return the file size.
     */
    @Override
    protected void onProgressUpdate(Long... values) {
        state = TaskState.TRANSFERRING;
        if (totalSize == -1 || updateTotal) {
            totalSize = values[0];
            return;
        }
        finished = values[0];
        downloadStateListener.onFileDownloadProgress(taskID);
    }

    @Override
    protected File doInBackground(Void... params) {
        File file = null;
        try {
            DataManager dataManager = new DataManager(account);
            final SeafRepo repo = dataManager.getCachedRepoByID(repoID);
//            if (repo != null && repo.canLocalDecrypt()) {
//                return dataManager.getFileByBlocks(repoName, repoID, path, totalSize,
            /*file = dataManager.getFileByBlocks(repoName, repoID, path, totalSize,
                    new ProgressMonitor() {

                        @Override
                        public void onProgressNotify(long total, boolean updateTotal) {
                            DownloadTask.this.updateTotal = updateTotal;
                            publishProgress(total);
                        }

                        @Override
                        public boolean isCancelled() {
                            return DownloadTask.this.isCancelled();
                        }
                    }
            );
            return file;*/
//            } else
//                return dataManager.getFile(repoName, repoID, path,
                file =  dataManager.getFile(repoName, repoID, path,
                        new ProgressMonitor() {

                            @Override
                            public void onProgressNotify(long total, boolean updateTotal) {
                                publishProgress(total);
                            }

                            @Override
                            public boolean isCancelled() {
                                return DownloadTask.this.isCancelled();
                            }
                        }
                );
                return file;
        } catch (SeafException e) {
            err = e;
            return file;
//        } catch (JSONException e) {
//            err = SeafException.unknownException;
//            e.printStackTrace();
//            return null;
//        } catch (IOException e) {
//            err = SeafException.networkException;
//            e.printStackTrace();
//            return null;
//        } catch (NoSuchAlgorithmException e) {
//            err = SeafException.unknownException;
//            e.printStackTrace();
        }
    }

    @Override
    protected void onPostExecute(File file) {
        if (downloadStateListener != null) {
            if (file != null) {
                state = TaskState.FINISHED;
                localPath = file.getPath();
                downloadStateListener.onFileDownloaded(taskID);

                Message msg = Message.obtain();
                msg.what = 1;
                msg.obj = file.getAbsolutePath();
                SeafileActivity.mHandler.sendMessage(msg);
                IntentBuilder.viewFile(SeafileActivity.mActivity, file.getAbsolutePath());
            } else {
                state = TaskState.FAILED;
                if (err == null)
                    err = SeafException.unknownException;
                downloadStateListener.onFileDownloadFailed(taskID);
            }
        }
    }

    @Override
    protected void onCancelled() {
        state = TaskState.CANCELLED;
    }

    @Override
    public DownloadTaskInfo getTaskInfo() {
        DownloadTaskInfo info = new DownloadTaskInfo(account, taskID, state, repoID,
                repoName, path, localPath, totalSize, finished, err);
        return info;
    }

    public String getLocalPath() {
        return localPath;
    }
}
