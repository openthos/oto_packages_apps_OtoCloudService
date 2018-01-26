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
import android.content.ComponentName;
import org.json.JSONArray;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import java.util.ArrayList;

public class SeafileService extends Service {

    public static String mLibrary;
    private static final String APPSTORE_PKGNAME_SEAFILE_PATH = "";

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
            return MainSeafileService.mConsole.queryFile(
                    MainSeafileService.mAccount.mUserId, libraryId, libraryName);
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

        public void restoreSettings(boolean wallpaper, boolean wifi, boolean email, boolean appdata,
                boolean startupmenu, boolean browser, boolean appstore) {
            if (appstore) {
                ArrayList<String> pkgNames = new ArrayList();
                File file = new File("APPSTORE_PKGNAME_SEAFILE_PATH");
                try {
                    BufferedReader appReader = new BufferedReader(
                                               new FileReader(APPSTORE_PKGNAME_SEAFILE_PATH));
                    String line = null;
                    while ((line = appReader.readLine()) != null) {
                        pkgNames.add(line);
                    }
                    appReader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                // download apps from appstore
                Intent intent = new Intent();
                intent.setComponent(new ComponentName("com.openthos.appstore",
                        "com.openthos.appstore.download.DownloadService"));
                intent.putStringArrayListExtra("packageNames", pkgNames);
                startService(intent);
            }
        }

        public void saveSettings(boolean wallpaper, boolean wifi, boolean email, boolean appdata,
                boolean startupmenu, boolean browser, boolean appstore) {}
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
