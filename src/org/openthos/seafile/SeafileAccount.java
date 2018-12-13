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

    public boolean isExistsAccount() {
        return !(TextUtils.isEmpty(mUserName) || TextUtils.isEmpty(mToken));
    }

    public void clear() {
        mUserName = "";
        mToken = "";
    }
}
