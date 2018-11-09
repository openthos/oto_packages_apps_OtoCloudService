package org.openthos.seafile.seaapp;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Message;
import android.support.annotation.NonNull;
import android.util.Pair;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;

import org.openthos.seafile.R;
import org.openthos.seafile.seaapp.SeafileActivity.SeaHandler;

public class DownloadFileDialog extends Dialog {
    private SeafileActivity mActivity;
    private TextView mFileNameText;
    private ImageView mFileIcon;
    private Button mButtonCancel;

    private TextView mProgressText;
    private ProgressBar mProgressBar;

    private String mRepoName, mRepoID;
    private String mFileName;
    private NavContext mNav;
    private String mFilePath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow() ;

        window.setContentView(R.layout.file_dialog);
        mFileNameText = (TextView)findViewById(R.id.file_name);
        mFileIcon = (ImageView)findViewById(R.id.file_icon);
        mButtonCancel = (Button)findViewById(R.id.op_cancel);
        mProgressBar = (ProgressBar)findViewById(R.id.progress_bar);
        mProgressText = (TextView)findViewById(R.id.progress_text);
        initWidgets();
        executeDownloadTask();
    }

    public DownloadFileDialog(Context context, String repoName, String repoID, String filePath) {
        super(context);
        mActivity = (SeafileActivity) context;
        mRepoName = repoName;
        mRepoID = repoID;
        mFilePath = filePath;
    }

    public DownloadFileDialog(@NonNull Context context) {
        super(context);
    }

    private void initWidgets() {
        mFileName = Utils.fileNameFromPath(mFilePath);
        mFileNameText.setText(mFileName);
        // icon
        mFileIcon.setImageResource(Utils.getFileIcon(mFileName));
        mButtonCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dismiss();
            }
        });
    }

    private void executeDownloadTask() {
        mNav = mActivity.getNavContext();
        new Thread() {
            @Override
            public void run() {
                File file = downloadFile();
                if (file != null) {
                    Message msg = Message.obtain();
                    SeaHandler handler = mActivity.getHandler();
                    msg.what = handler.MSG_WHAT_DOWNLOAD_FINISHED;
                    msg.obj = file.getAbsolutePath();
                    handler.sendMessage(msg);
                    IntentBuilder.viewFile(mActivity, file.getAbsolutePath());
                }
                DownloadFileDialog.this.dismiss();
            }
        }.start();
    }

    private File downloadFile() {
        File file = null;
        try {
            String cachedFileID = null;
            SeafConnection sc = new SeafConnection(mActivity.getAccount(), mActivity);
            File localFile = mActivity.getDataManager()
                    .getLocalRepoFile(mRepoName, mRepoID, mFilePath);
            SeafCachedFile cf = mActivity.getDataManager()
                    .getCachedFile(mRepoName, mRepoID, mFilePath);
            if (cf != null) {
                if (localFile.exists()) {
                    cachedFileID = cf.fileID;
                }
            }
            Pair<String, File> ret = sc.getFile(
                    mRepoID, mFilePath, localFile.getPath(), cachedFileID, null);
            String fileID = ret.first;
            if (fileID.equals(cachedFileID)) {
                // cache is valid
                return localFile;
            } else {
                file = ret.second;
                mActivity.getDataManager()
                        .addCachedFile(mRepoName, mRepoID, mFilePath, fileID, file);
                return file;
            }
        } catch (SeafException e) {
            final int code = e.getCode();
            SeaHandler handler = mActivity.getHandler();
            handler.sendEmptyMessage(handler.MSG_WHAT_LOAD_FINISHED);
            mActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    switch (code) {
                        case 403:
                            ToastUtil.showSingletonToast(mActivity,
                                    mActivity.getString(R.string.resource_not_available));
                            mActivity.showDirentError();
                            break;
                        case 404:
                            ToastUtil.showSingletonToast(mActivity,
                                    mActivity.getString(R.string.resource_not_found));
                            mActivity.showDirentError();
                            break;
                    }
                }
            });
            return file;
        }
    }
}
