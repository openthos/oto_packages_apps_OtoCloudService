package org.openthos.seafile.seaapp;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import org.openthos.seafile.R;

/**
 * Upload a file
 */
@SuppressLint("ValidFragment")
public class UploadFileDialog extends Dialog {
    private TextView mFileNameText;
    private ImageView mFileIcon;
    private Button mButtonCancel;

    private TextView mProgressText;
    private ProgressBar mProgressBar;

    private String mRepoName, mRepoID, mFilePath;

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
    }

    public UploadFileDialog(Context context, String repoName,
                            String repoID, String filePath) {
        super(context);
        mRepoName = repoName;
        mRepoID = repoID;
        mFilePath = filePath;
    }

    public UploadFileDialog(@NonNull Context context) {
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
                dismiss();
            }
        });
    }
}
