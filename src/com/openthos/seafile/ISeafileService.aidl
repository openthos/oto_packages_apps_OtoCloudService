package com.openthos.seafile;

interface ISeafileService {
    void sync(String libraryId, String libraryName, String filePath);
    void desync(String libraryId, String libraryName, String filePath);
    String getLibrary();
    int getUserId();
    String getUserName();
    int isSync(String libraryId, String libraryName);
    void updateAccount();
    void stopAccount();
    void restoreSettings(boolean wallpaper, boolean wifi, boolean email, boolean appdata,
            boolean startupmenu, boolean browser, in List<String> syncBrowsers, boolean appstore);
    void saveSettings(boolean wallpaper, boolean wifi, boolean email, boolean appdata,
            boolean startupmenu, boolean browser, in List<String> syncBrowsers, boolean appstore);
    void regiestAccount(String userName, String password);
    void setBinder(IBinder b);
    void unsetBinder(IBinder b);
    int getCodeSendInto();
    int getCodeSendOut();
    int getCodeRestoreFinish();
    int getCodeDownloadFinish();
    int getCodeRegiestSuccess();
    int getCodeRegiestFailed();
}
