package org.openthos.seafile.seaapp;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Comparator;
import org.openthos.seafile.R;

/**
 * SeafRepo: A Seafile library
 * @author plt
 */
public class SeafRepo implements SeafItem {
    public String id;     // repo id
    public String name;
    public String owner;
    public long mtime;    // the last modification time

    public boolean isGroupRepo;
    public boolean isPersonalRepo;
    public boolean isSharedRepo;
    public boolean encrypted;
    public String permission;
    public String magic;
    public String encKey;
    public long    size;
    public String root; // the id of root directory
    private SeafileActivity mActivity;

    public void  fromJson(JSONObject obj) throws JSONException {
        id = obj.getString("id");
        name = obj.getString("name");
        owner = obj.getString("owner");
        permission = obj.getString("permission");
        mtime = obj.getLong("mtime");
        encrypted = obj.getBoolean("encrypted");
        root = obj.getString("root");
        size = obj.getLong("size");
        isGroupRepo = obj.getString("type").equals("grepo");
        isPersonalRepo = obj.getString("type").equals("repo");
        isSharedRepo = obj.getString("type").equals("srepo");
        magic = obj.optString("magic");
        encKey = obj.optString("random_key");
    }

    public SeafRepo(SeafileActivity activity) {
        mActivity = activity;
    }

    public String getID() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getRootDirID() {
        return root;
    }

    @Override
    public String getTitle() {
        return name;
    }

    @Override
    public String getSubtitle() {
        return Utils.translateCommitTime(mtime * 1000, mActivity);
    }

    @Override
    public int getIcon() {
        return R.drawable.folder;
    }

//    public boolean canLocalDecrypt() {
//        return encrypted && SettingsManager.instance().isEncryptEnabled();
//    }

    public boolean hasWritePermission() {
        return permission.indexOf('w') != -1;
    }

    /**
     * Repository last modified time comparator class
     */
    public static class RepoLastMTimeComparator implements Comparator<SeafRepo> {

        @Override
        public int compare(SeafRepo itemA, SeafRepo itemB) {
            return (int) (itemA.mtime - itemB.mtime);
        }
    }

    /**
     * Repository name comparator class
     */
    public class RepoNameComparator implements Comparator<SeafRepo> {

        @Override
        public int compare(SeafRepo itemA, SeafRepo itemB) {
            // get the first character unicode from each file name
            int unicodeA = itemA.name.codePointAt(0);
            int unicodeB = itemB.name.codePointAt(0);

            String strA, strB;

            // both are Chinese words
            if ((19968 < unicodeA && unicodeA < 40869) && (19968 < unicodeB && unicodeB < 40869)) {
//                strA = PinyinUtils.toPinyin(SeadroidApplication.getAppContext(), itemA.name).toLowerCase();
                strA = PinyinUtils.toPinyin(mActivity, itemA.name).toLowerCase();
//                strB = PinyinUtils.toPinyin(SeadroidApplication.getAppContext(), itemB.name).toLowerCase();
                strB = PinyinUtils.toPinyin(mActivity, itemB.name).toLowerCase();
            } else if ((19968 < unicodeA && unicodeA < 40869) && !(19968 < unicodeB && unicodeB < 40869)) {
                // itemA is Chinese and itemB is English
                return 1;
            } else if (!(19968 < unicodeA && unicodeA < 40869) && (19968 < unicodeB && unicodeB < 40869)) {
                // itemA is English and itemB is Chinese
                return -1;
            } else {
                // both are English words
                strA = itemA.name.toLowerCase();
                strB = itemB.name.toLowerCase();
            }

            return strA.compareTo(strB);
        }
    }
}
