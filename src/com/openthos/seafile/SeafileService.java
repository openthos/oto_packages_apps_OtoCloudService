package com.openthos.seafile;

import android.app.Activity;
import android.app.Service;
import android.content.Intent;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;
import android.os.Handler;
import android.os.Message;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;

public class SeafileService extends Service {

    public static String mLibrary;

    private ISeafileService.Stub mBinder = new ISeafileService.Stub() {
        public void sync(String libraryid, String filePath) {
            SeafileUtils.sync(libraryid, filePath);
        }

        public void desync(String filePath) {
            SeafileUtils.desync(filePath);
        }

        public String getLibrary() {
            return MainSeafileService.mLibrary;
        }

        public int getUserId() {
            return MainSeafileService.mAccount.mUserId;
        }

        public String getUserName() {
            return SeafileUtils.mUserId;
        }

        public String getUserPassword() {
            return SeafileUtils.mUserPassword;
        }

        public int isSync(String libraryId, String libraryName) {
            return MainSeafileService.mConsole.queryFile(MainSeafileService.mAccount.mUserId, libraryId, libraryName);
        }

        public int updateSync(int userId, String libraryId, String libraryName, int isSync) {
            return MainSeafileService.mConsole.updateSync(userId, libraryId, libraryName, isSync);
        }

        public int insertLibrary(int userId, String libraryId, String libraryName) {
            return MainSeafileService.mConsole.insertLibrary(userId, libraryId, libraryName);
        }

        public String create(String text) {
            return SeafileUtils.create(text);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
}
