package org.openthos.seafile.seaapp;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Pair;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import org.openthos.seafile.R;

import java.io.File;
import java.io.IOException;

public class UploadFileDialog extends Dialog {
    private SeafileActivity mActivity;
    private TextView mFileNameText;
    private ImageView mFileIcon;
    private Button mButtonCancel;

    private TextView mProgressText;
    private ProgressBar mProgressBar;

    private String mRepoName, mRepoID;
    private String mFileName;
    private int mTotalFileCount, mCurUploadCount;
    private NavContext mNav;
    private String mDirPath, mCurDirPath;;
    private File mFile;

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
        executeUploadTask();
    }

    public UploadFileDialog(Context context, String repoName,
            String repoID, File file) {
        super(context);
        mActivity = (SeafileActivity) context;
        mRepoName = repoName;
        mRepoID = repoID;
        mFile = file;
    }

    public UploadFileDialog(@NonNull Context context) {
        super(context);
    }

    private void initWidgets() {
        mFileName = Utils.fileNameFromPath(mFile.getAbsolutePath());
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

    private void executeUploadTask() {
        mNav = mActivity.getNavContext();
        mDirPath = mNav.getDirPath();
        mCurUploadCount = 0;
        if (mFile.isDirectory()) {
            File[] subFiles = mFile.listFiles();
            mTotalFileCount = getFileCount(subFiles);
        } else {
            mTotalFileCount = 1;
        }

        mProgressBar.setMax(mTotalFileCount);
        mProgressBar.setProgress(mCurUploadCount);
        new Thread() {
            @Override
            public void run() {
                SeafConnection sc = new SeafConnection(mActivity.getAccount(), mActivity);
                if (mFile.isDirectory()) {
                    mCurDirPath = mDirPath.endsWith("/") ? mDirPath : mDirPath + "/";
                    uploadDir(mNav, sc, mFileName, mFile.listFiles());
                } else {
                    uploadFile(mNav, sc, mDirPath, mFile.getAbsolutePath());
                }
            }
        }.start();
    }

    private void uploadDir(NavContext nav, SeafConnection sc, String dirName, File[] subFiles) {
        mProgressBar.setIndeterminate(false);
        try {
            Pair<String, String> serverDir = createNewDir(sc, mCurDirPath, dirName);
            if (serverDir != null) {
                mCurDirPath = mCurDirPath + dirName + "/";
                for (int i = 0; i < subFiles.length; i++) {
                    File subFile = subFiles[i];
                    if (subFile.isDirectory()) {
                        uploadDir(nav, sc, subFile.getName(), subFile.listFiles());
                    } else {
                        final String text = String.format(
                                mActivity.getString(R.string.upload_progress),
                                mCurUploadCount, mTotalFileCount, subFile.getName());
                        mActivity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mProgressText.setText(text);
                            }
                        });
                        String newFileID = sc.uploadFile(nav.getRepoID(),
                                mCurDirPath, subFile.getAbsolutePath(), null, false);
                        if (newFileID != null) {
                            mCurUploadCount++;
                            mProgressBar.setProgress(mCurUploadCount);
                            if (mCurUploadCount == mTotalFileCount) {
                                mActivity.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        mActivity.refreshDirent();
                                        UploadFileDialog.this.dismiss();
                                        ToastUtil.showSingletonToast(mActivity, mActivity.getString(R.string.upload_finished));
                                    }
                                });
                                return;
                            }
                        } else {
                            mActivity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    UploadFileDialog.this.dismiss();
                                    ToastUtil.showSingletonToast(mActivity, mActivity.getString(R.string.upload_failed));
                                }
                            });
                            break;
                        }
                    }
                    if (i == subFiles.length - 1) {
                        mCurDirPath = mCurDirPath.substring(0, mCurDirPath.lastIndexOf(dirName));
                    }
                }
            }
        } catch (SeafException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private int getFileCount(File[] subFiles) {
        int count = 0;
        for(File subFile : subFiles) {
            if (subFile.isFile()) {
                count++;
            } else {
                count = count + getFileCount(subFile.listFiles());
            }
        }
        return count;
    }

    private Pair createNewDir(SeafConnection sc, String dirPath, String dirName) throws SeafException {
        return  sc.createNewDir(mRepoID, dirPath, dirName);
    }

    private void uploadFile(NavContext nav, SeafConnection sc, String dirPath, String filePath) {
        mProgressBar.setIndeterminate(true);
        try {
            final String text = String.format(
                    mActivity.getString(R.string.upload_progress),
                    mCurUploadCount, mTotalFileCount, mFile.getName());
            mActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mProgressText.setText(text);
                }
            });
            String newFileID =  sc.uploadFile(nav.getRepoID(),
                    dirPath, filePath, null, false);
            if (newFileID != null) {
//                mProgressBar.setProgress(1);
                mActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mActivity.refreshDirent();
                        UploadFileDialog.this.dismiss();
                        ToastUtil.showSingletonToast(mActivity, mActivity.getString(R.string.upload_finished));
                    }
                });
            } else {
                mActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        UploadFileDialog.this.dismiss();
                        ToastUtil.showSingletonToast(mActivity, mActivity.getString(R.string.upload_failed));
                    }
                });
            }
        } catch (SeafException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
