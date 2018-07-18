package org.openthos.seafile.seaapp;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import org.openthos.seafile.R;

class DeleteRepoTask extends TaskDialog.Task {

    private String mRepoID;
    private DataManager mDataManager;

    DeleteRepoTask(String repoID, DataManager dataManager) {
        mRepoID = repoID;
        mDataManager = dataManager;
    }

    @Override
    protected void runTask() {
        try {
            mDataManager.deleteRepo(mRepoID);
        } catch (SeafException e) {
            setTaskException(e);
        }
    }
}

public class DeleteRepoDialog extends TaskDialog {

    private final static String STATE_REPO_ID = "delete_repo_dialog.repo_id";
    private final static String STATE_ACCOUNT = "delete_repo_dialog.account";

    private String mRepoID;
    private Account mAccount;
    private DataManager mDataManager;

    public void init(String repoID, Account account) {
        mRepoID = repoID;
        mAccount = account;
    }

    private DataManager getDataManager() {
        if (mDataManager == null) {
            mDataManager = new DataManager(mAccount);
        }

        return mDataManager;
    }

    @Override
    protected View createDialogContentView(LayoutInflater inflater, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_delete_repo, null);

        if (savedInstanceState != null) {
            // Restore state
            mRepoID = savedInstanceState.getString(STATE_REPO_ID);
            mAccount = savedInstanceState.getParcelable(STATE_ACCOUNT);
        }

        return view;
    }

    @Override
    protected void onSaveDialogContentState(Bundle outState) {
        super.onSaveDialogContentState(outState);
        outState.putString(STATE_REPO_ID, mRepoID);
        outState.putParcelable(STATE_ACCOUNT, mAccount);
    }

    @Override
    protected void onDialogCreated(Dialog dialog) {
        super.onDialogCreated(dialog);
        dialog.setTitle(R.string.delete_repo_title);
    }

    @Override
    protected DeleteRepoTask prepareTask() {
        return new DeleteRepoTask(mRepoID, getDataManager());
    }
}
