package org.openthos.seafile;

import android.content.Context;
import android.text.TextUtils;

import java.io.File;

/**
 * Created by Wang Zhixu on 12/23/16.
 */

public class SeafileAccount {
    public String mOpenthosUrl;
    public String mUserName;
    public int mUserId;
    public SeafileLibrary mDataLibrary;
    public SeafileLibrary mConfigLibrary;
    public File mFile;
    public String mToken = "";

    public SeafileAccount(Context context) {
        SeafileAccount account = Utils.readAccount(context);
        mOpenthosUrl = account.mOpenthosUrl;
        mUserName = account.mUserName;
        mToken = account.mToken;
    }

    public SeafileAccount(String url) {
        mOpenthosUrl = url;
        mUserName = "";
        mToken = "";
    }

    public static SeafileAccount getDefaultAccount(String url) {
        return new SeafileAccount(url);
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("[");
        sb.append("{\"id\":\"" + mDataLibrary.libraryId);
        sb.append("\",\"name\":\"" + mDataLibrary.libraryName + "\"}");
        sb.append("]");
        return sb.toString();
    }

    public boolean isExistsAccount() {
        return !(TextUtils.isEmpty(mUserName) || TextUtils.isEmpty(mToken));
    }

    public void clear() {
        mUserName = "";
        mUserId = -1;
        mDataLibrary = null;
        mFile = null;
        mToken = "";
    }
}
