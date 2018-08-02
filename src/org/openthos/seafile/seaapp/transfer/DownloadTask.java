package org.openthos.seafile.seaapp.transfer;

import android.os.Message;

import java.io.File;

import org.openthos.seafile.seaapp.Account;
import org.openthos.seafile.seaapp.DataManager;
import org.openthos.seafile.seaapp.ProgressMonitor;
import org.openthos.seafile.seaapp.SeafException;
import org.openthos.seafile.seaapp.SeafRepo;
import org.openthos.seafile.seaapp.SeafileActivity;
import org.openthos.seafile.seaapp.IntentBuilder;
import org.openthos.seafile.seaapp.ToastUtil;
import org.openthos.seafile.R;

/**
 * Download task
 *
 */
public class DownloadTask extends TransferTask {
    public static final String DEBUG_TAG = "DownloadTask";

    private String localPath;
    private DownloadStateListener downloadStateListener;
    private boolean updateTotal;
    private SeafileActivity mActivity;

    public DownloadTask(int taskID, Account account, String repoName, String repoID, String path,
                        DownloadStateListener downloadStateListener, SeafileActivity activity) {
        super(taskID, account, repoName, repoID, path);
        this.downloadStateListener = downloadStateListener;
        mActivity = activity;
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
                file =  mActivity.getDataManager().getFile(repoName, repoID, path,
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
            final int code = e.getCode();
            mActivity.getHandler().sendEmptyMessage(3);
            mActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    switch (code) {
                        case 403:
                            ToastUtil.showSingletonToast(mActivity,
                                    mActivity.getString(R.string.resource_not_available));
                            break;
                        case 404:
                            ToastUtil.showSingletonToast(mActivity,
                                    mActivity.getString(R.string.resource_not_found));
                            break;
                    }
                    mActivity.showDirentError();
                }
            });
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
                mActivity.getHandler().sendMessage(msg);
                IntentBuilder.viewFile( mActivity, file.getAbsolutePath());
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
