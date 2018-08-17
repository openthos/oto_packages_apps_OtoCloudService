package org.openthos.seafile;

import android.content.Context;
import android.text.TextUtils;
import java.io.File;
import java.util.ArrayList;

/**
 * Created by Wang Zhixu on 12/23/16.
 */

public class SeafileAccount {
    public String mOpenthosUrl;
    public String mUserName;
    public String mUserPassword;
    public int mUserId;
    public SeafileLibrary mDataLibrary;
    public SeafileLibrary mSettingLibrary;
    public File mFile;

    public SeafileAccount(Context context){
        SeafileAccount account = Utils.readAccount(context);
        mOpenthosUrl = account.mOpenthosUrl;
        mUserName = account.mUserName;
        mUserPassword = account.mUserPassword;
    }

    public SeafileAccount(String url){
        mOpenthosUrl = url;
        mUserName = "";
        mUserPassword = "";
    }

    public static SeafileAccount getDefaultAccount(String url) {
        return new SeafileAccount(url);
    }
    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("[");
        sb.append("{\"id\":\"" + mSettingLibrary.libraryId);
        sb.append("\",\"name\":\"" + mSettingLibrary.libraryName + "\"},");
        sb.append("{\"id\":\"" + mDataLibrary.libraryId);
        sb.append("\",\"name\":\"" + mDataLibrary.libraryName + "\"}");
        sb.append("]");
        return sb.toString();
    }

    public boolean isExistsAccount() {
        return !TextUtils.isEmpty(mUserName) && !TextUtils.isEmpty(mUserPassword);
    }

    public void clear() {
        mUserName = "";
        mUserPassword = "";
        mUserId = -1;
        mDataLibrary = null;
        mSettingLibrary = null;
        mFile = null;
    }
}
