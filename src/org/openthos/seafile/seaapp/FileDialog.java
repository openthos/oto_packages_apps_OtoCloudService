package org.openthos.seafile.seaapp;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NavUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.net.HttpURLConnection;
import java.util.zip.Inflater;

import org.openthos.seafile.R;
import org.openthos.seafile.seaapp.notification.DownloadNotificationProvider;
import org.openthos.seafile.seaapp.transfer.DownloadTaskInfo;
import org.openthos.seafile.seaapp.transfer.TaskState;
import org.openthos.seafile.seaapp.transfer.TransferService;
import org.openthos.seafile.seaapp.transfer.TransferService.TransferBinder;

/**
 * Display a file
 */
@SuppressLint("ValidFragment")
public class FileDialog extends Dialog {
    private static final String DEBUG_TAG = "FileActivity";

    private TextView mFileNameText;
    private ImageView mFileIcon;
    private Button mButtonCancel;

    private TextView mProgressText;
    private ProgressBar mProgressBar;

    private String mRepoName, mRepoID, mFilePath;
    private DataManager mDataManager;
    private Account mAccount;

    private int mTaskID = -1;
    private TransferService mTransferService;
    private boolean timerStarted;
    private final Handler mTimer = new Handler();
    private SeafileActivity mActivity;
    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            TransferService.TransferBinder binder = (TransferBinder) service;
            mTransferService = binder.getService();
            onTransferServiceConnected();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window window = getWindow() ;

        window.setContentView(R.layout.file_dialog);
//        LayoutInflater inflater = LayoutInflater.from(mActivity);
//        View view = inflater.inflate(R.layout.file_dialog, null, false);
        mFileNameText = (TextView)findViewById(R.id.file_name);
        mFileIcon = (ImageView)findViewById(R.id.file_icon);
        mButtonCancel = (Button)findViewById(R.id.op_cancel);
        mProgressBar = (ProgressBar)findViewById(R.id.progress_bar);
        mProgressText = (TextView)findViewById(R.id.progress_text);
        initWidgets();
        bindTransferService();
    }

    public FileDialog(Context context, String repoName,
                      String repoID, String filePath, int taskID) {
        super(context);
        mActivity = (SeafileActivity) context;
        mRepoName = repoName;
        mRepoID = repoID;
        mFilePath = filePath;
        mTaskID = taskID;
    }

    public FileDialog(@NonNull Context context) {
        super(context);
    }

    private void initWidgets() {
        String fileName = Utils.fileNameFromPath(mFilePath);
        mFileNameText.setText(fileName);

        // icon
        mFileIcon.setImageResource(Utils.getFileIcon(fileName));

        mButtonCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
//                if (mTaskID > 0) {
//                    mTransferService.cancelDownloadTask(mTaskID);
//                    mTransferService.cancelNotification();
//                }
//                if (mTransferService != null) {
//                    SeafileActivity.mActivity.unbindService(mConnection);
//                    mTransferService = null;
//                }
                dismiss();
            }
        });
    }

    private void onTransferServiceConnected() {
        mProgressBar.setVisibility(View.VISIBLE);
        mProgressBar.setIndeterminate(true);
        mProgressText.setVisibility(View.VISIBLE);

        startTimer();
    }

    private void bindTransferService() {
        Intent txIntent = new Intent(SeafileActivity.mActivity, TransferService.class);
        SeafileActivity.mActivity.startService(txIntent);
        Log.d(DEBUG_TAG, "start TransferService");
    }
//
    private void onFileDownloadProgress(DownloadTaskInfo info) {
        long fileSize = info.fileSize;
        long finished = info.finished;

        mProgressBar.setIndeterminate(false);
        int percent;
        if (fileSize == 0) {
            percent = 100;
        } else {
            percent = (int)(finished * 100 / fileSize);
        }
        mProgressBar.setProgress(percent);

        String txt = Utils.readableFileSize(finished) + " / " + Utils.readableFileSize(fileSize);

        mProgressText.setText(txt);

        if (!mTransferService.hasDownloadNotifProvider()) {
            DownloadNotificationProvider provider = new DownloadNotificationProvider(
                    mTransferService.getDownloadTaskManager(),
                    mTransferService);
            mTransferService.saveDownloadNotifProvider(provider);
        } else {
            // if the notificationManager mapping the repoID exist, update its data set
            // mTransferService.getDownloadNotifProvider().updateTotalSize(fileSize);
        }
    }

    private void onFileDownloaded() {
        mProgressBar.setVisibility(View.GONE);
        mProgressText.setVisibility(View.GONE);
        mButtonCancel.setVisibility(View.GONE);

        File file = mDataManager.getLocalRepoFile(mRepoName, mRepoID, mFilePath);
        if (file != null && timerStarted) {
            Intent result = new Intent();
            result.putExtra("path", file.getAbsolutePath());
//            setResult(RESULT_OK, result);
        }
        else {
//            setResult(RESULT_CANCELED);
        }
        stopTimer();
        dismiss();
    }

    private void onFileDownloadFailed(DownloadTaskInfo info) {
        mProgressBar.setVisibility(View.GONE);
        mProgressText.setVisibility(View.GONE);
        mButtonCancel.setVisibility(View.GONE);

        SeafException err = info.err;
        String fileName = Utils.fileNameFromPath(info.pathInRepo);
        if (err.getCode() == HttpURLConnection.HTTP_NOT_FOUND) {
            // file deleted
            showToast("The file \"" + fileName + "\" has been deleted");
        } else if (err.getCode() == SeafConnection.HTTP_STATUS_REPO_PASSWORD_REQUIRED) {
//            handlePassword();
        } else {
            showToast("Failed to download file \"" + fileName);
        }
        stopTimer();
    }

    public void showToast(CharSequence msg) {
//        Context context = getApplicationContext();
        Context context = SeafileActivity.mActivity;
        Toast toast = Toast.makeText(context, msg, Toast.LENGTH_SHORT);
        toast.show();
    }

    public void startTimer() {
        if (timerStarted) {
            return;
        }
        timerStarted = true;
        Log.d(DEBUG_TAG, "timer started");
        mTimer.postDelayed(new Runnable() {

            @Override
            public void run() {
                if (mTransferService == null)
                    return;

                DownloadTaskInfo downloadTaskInfo = mTransferService.getDownloadTaskInfo(mTaskID);
                if (downloadTaskInfo.state == TaskState.INIT
                        || downloadTaskInfo.state == TaskState.TRANSFERRING)
                    onFileDownloadProgress(downloadTaskInfo);
                else if (downloadTaskInfo.state == TaskState.FAILED)
                    onFileDownloadFailed(downloadTaskInfo);
                else if (downloadTaskInfo.state == TaskState.FINISHED)
                    onFileDownloaded();
                else if (downloadTaskInfo.state == TaskState.CANCELLED)
                    // do nothing when cancelled

                Log.d(DEBUG_TAG, "timer post refresh signal " + System.currentTimeMillis());
                mTimer.postDelayed(this, 1 * 1000);
            }
        }, 1 * 1000);
    }

    public void stopTimer() {
        if (!timerStarted) {
            return;
        }
        timerStarted = false;
        Log.d(DEBUG_TAG, "timer stopped");
        mTimer.removeCallbacksAndMessages(null);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            if (mTaskID > 0) {
                mTransferService.cancelDownloadTask(mTaskID);
                mTransferService.cancelNotification();
            }
//            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

}
