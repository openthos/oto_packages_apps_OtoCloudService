package com.openthos.seafile;

//import com.openthos.seafile.SeafileLibrary;

interface ISeafileService {
    void sync(String libraryid, String filePath);
    void desync(String filePath);
    String getLibrary();
    int getUserId();
    String getUserName();
    String getUserPassword();
    int isSync(String libraryId, String libraryName);
    int updateSync(int userId, String libraryId, String libraryName, int isSync);
    int insertLibrary(int userId, String libraryId, String libraryName);
    String create(String text);
}