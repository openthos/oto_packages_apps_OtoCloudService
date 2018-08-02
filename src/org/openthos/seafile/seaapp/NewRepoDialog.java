package org.openthos.seafile.seaapp;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import org.openthos.seafile.R;

class NewRepoTask extends TaskDialog.Task {

    private String mRepoName;
    private String mPassword;
    private DataManager mDataManager;

    public NewRepoTask(String repoName, String password, DataManager dataManager) {
        mRepoName = repoName;
        mPassword = password;
        mDataManager = dataManager;
    }

    @Override
    protected void runTask() {
        try {
            mDataManager.createNewRepo(mRepoName, mPassword);
        } catch (SeafException e) {
            setTaskException(e);
        }
    }
}

@SuppressLint("ValidFragment")
public class NewRepoDialog extends TaskDialog {

    private final static String STATE_ACCOUNT = "new_repo_dialog.account";

    // The input fields of the dialog
    private EditText mRepoNameText;

    private Account mAccount;
    private SeafileActivity mActivity;

    @SuppressLint("ValidFragment")
    public NewRepoDialog(SeafileActivity activity) {
        super();
        mActivity = activity;
    }

    public void init(Account account) {
        // The DataManager is not parcelable, so we save the intermediate Account instead
        mAccount = account;
    }

    private DataManager getDataManager() {
        return mActivity.getDataManager();
    }

    public String getRepoName() { return mRepoNameText.getText().toString().trim(); }

    @Override
    protected View createDialogContentView(LayoutInflater inflater, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_new_repo, null);
        mRepoNameText = (EditText) view.findViewById(R.id.new_repo_name);
        if (savedInstanceState != null) {
            // Restore state
            mAccount = (Account) savedInstanceState.getParcelable(STATE_ACCOUNT);
        }
        return view;
    }

    @Override
    protected void onSaveDialogContentState(Bundle outState) {
        // Save state
        outState.putParcelable(STATE_ACCOUNT, mAccount);
    }

    @Override
    protected void onDialogCreated(Dialog dialog) {
        dialog.setTitle(R.string.create_new_repo);
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
    }

    @Override
    protected void onValidateUserInput() throws Exception {
        if (getRepoName().length() == 0) {
            throw new Exception(getResources().getString(R.string.repo_name_empty));
        }
    }

    @Override
    protected NewRepoTask prepareTask() {
        return new NewRepoTask(getRepoName(), null, getDataManager());
    }

    @Override
    protected void disableInput() {
        super.disableInput();
        mRepoNameText.setEnabled(false);
    }

    @Override
    protected void enableInput() {
        super.enableInput();
        mRepoNameText.setEnabled(true);
    }
}
