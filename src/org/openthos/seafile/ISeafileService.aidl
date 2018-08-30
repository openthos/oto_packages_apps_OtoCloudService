package org.openthos.seafile;

import android.content.pm.ResolveInfo;

interface ISeafileService {
    void syncData();
    void desyncData();
    String getUserName();
    boolean isSync();

    void registeAccount(String userName, String email, String password);
    void loginAccount(String userName, String password);
    void stopAccount();

    void setBinder(IBinder b);
    void unsetBinder(IBinder b);

    int getCodeSendInto();
    int getCodeSendOut();
    int getCodeRegiestSuccess();
    int getCodeRegiestFailed();
    int getCodeLoginSuccess();
    int getCodeLoginFailed();
    int getCodeChangeUrl();
    int getCodeUnbindAccount();

    boolean setOpenthosUrl(String url);
    String getOpenthosUrl();
}
