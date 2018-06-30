package org.openthos.seafile;

import android.content.pm.ResolveInfo;

interface ISeafileService {
    void syncData();
    void desyncData();
    String getUserName();
    boolean isSync();
    boolean initFinished();

    void registeAccount(String userName, String email, String password);
    void loginAccount(String userName, String password);
    void stopAccount();

    void restoreSettings(boolean wallpaper, boolean wifi,
            boolean appdata, in List<String> syncAppdata, boolean startupmenu,
            boolean browser, in List<String> syncBrowsers, boolean appstore);
    void saveSettings(boolean wallpaper, boolean wifi,
            boolean appdata, in List<String> syncAppdata, boolean startupmenu,
            boolean browser, in List<String> syncBrowsers, boolean appstore);
    List<ResolveInfo> getAppsInfo(int tag);
    void setBinder(IBinder b);
    void unsetBinder(IBinder b);

    int getCodeSendInto();
    int getCodeSendOut();
    int getCodeRestoreFinish();
    int getCodeDownloadFinish();
    int getCodeRegiestSuccess();
    int getCodeRegiestFailed();
    int getCodeLoginSuccess();
    int getCodeLoginFailed();
    int getTagAppdataImport();
    int getTagAppdataExport();
    int getTagBrowserImport();
    int getTagBrowserExport();

    void setDevServer(boolean isDev);
    boolean isDevServer();

    void setOpenthosUrl(String url);
    String getOpenthosUrl();
}
