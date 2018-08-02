package org.openthos.seafile.seaapp;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import org.openthos.seafile.R;

import java.util.ArrayList;

class GetShareLinkTask extends TaskDialog.Task {
    String repoID;
    String path;
    boolean isdir;
    SeafConnection conn;
    String link;
    Account account;
    String password;
    String days;
    SeafileActivity activity;

    public GetShareLinkTask(String repoID, String path, boolean isdir,
                            SeafConnection conn, Account account, String password,
                            String days, SeafileActivity activity) {
        this.repoID = repoID;
        this.path = path;
        this.isdir = isdir;
        this.conn = conn;
        this.account = account;
        this.password = password;
        this.days = days;
        this.activity = activity;
    }

    @Override
    protected void runTask() {

        // If you has  Shared links to delete Shared links
        DataManager dataManager = new DataManager(account, activity);
        ArrayList<SeafLink> shareLinks = dataManager.getShareLink(repoID, path);
        for (SeafLink shareLink : shareLinks) {
            //delete link
            dataManager.deleteShareLink(shareLink.getToken());
        }
        //create new link
        try {
            link = conn.getShareLink(repoID, path, password, days);
        } catch (SeafException e) {
            setTaskException(e);
        }
    }

    public String getResult() {
        return link;
    }
}

public class GetShareLinkDialog extends TaskDialog {
    private String repoID;
    private String path;
    private boolean isdir;
    private SeafConnection conn;
    Account account;
    private String password;
    private String days;
    private SeafileActivity activity;

    public void init(String repoID, String path, boolean isdir, Account account, String password,
                     String days, SeafileActivity activity) {
        this.repoID = repoID;
        this.path = path;
        this.isdir = isdir;
        this.conn = new SeafConnection(account, activity);
        this.account = account;
        this.password = password;
        this.days = days;
        this.activity = activity;
    }

    @Override
    protected View createDialogContentView(LayoutInflater inflater, Bundle savedInstanceState) {
        return null;
    }

    @Override
    protected boolean executeTaskImmediately() {
        return true;
    }

    @Override
    protected void onDialogCreated(Dialog dialog) {
        dialog.setTitle(getActivity().getString(R.string.generating_link));
        // dialog.setTitle(getActivity().getString(R.string.generating_link));
    }

    @Override
    protected GetShareLinkTask prepareTask() {
        password = null;
        days = null;
        android.util.Log.i("123", "repoID--" + repoID);
        android.util.Log.i("123", "path--" + path);
        android.util.Log.i("123", "isdir--" + isdir);
        android.util.Log.i("123", "account--" + account);
        android.util.Log.i("123", "password--" + password);
        android.util.Log.i("123", "days--" + days);
        GetShareLinkTask task = new GetShareLinkTask(
                repoID, path, isdir, conn, account, password, days, activity);
        return task;
    }

    public String getLink() {
        if (getTask() != null) {
            GetShareLinkTask task = (GetShareLinkTask)getTask();
            return task.getResult();
        }

        return null;
    }
}
