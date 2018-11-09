package org.openthos.seafile.seaapp;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.Comparator;
import org.openthos.seafile.R;

public class SeafDirent implements SeafItem, Serializable {
    public static final long serialVersionUID = 0L;
    private static final String DEBUG_TAG = "SeafDirent";
    public enum DirentType {DIR, FILE}
    public String permission;
    public String id;
    public DirentType type;
    public String name;
    public long size;    // size of file, 0 if type is dir
    public long mtime;   // last modified timestamp
    private SeafileActivity mActivity;

    public SeafDirent(SeafileActivity activity) {
        mActivity = activity;
    }

    public void fromJson(JSONObject obj) {
        try {
            id = obj.getString("id");
            name = obj.getString("name");
            mtime = obj.getLong("mtime");
            permission = obj.getString("permission");
            String t = obj.getString("type");
            if (t.equals("file")) {
                type = DirentType.FILE;
                size = obj.getLong("size");
            } else
                type = DirentType.DIR;
        } catch (JSONException e) {
            Log.d(DEBUG_TAG, e.getMessage());
        }
    }

    public boolean isDir() {
        return (type == DirentType.DIR);
    }

    @Override
    public String getTitle() {
        return name;
    }

    @Override
    public String getSubtitle() {
        String timestamp = Utils.translateCommitTime(mtime * 1000, mActivity);
        if (isDir())
            return timestamp;
        return Utils.readableFileSize(size) + ", " + timestamp;
    }


    @Override
    public int getIcon() {
        if (isDir()) {
            return R.drawable.folder;
        } else {
            return Utils.getFileIcon(name);
        }
    }

    public boolean hasWritePermission() {
        return permission.indexOf('w') != -1;
    }

    /**
     * SeafDirent last modified time comparator class
     */
    public static class DirentLastMTimeComparator implements Comparator<SeafDirent> {

        @Override
        public int compare(SeafDirent itemA, SeafDirent itemB) {
            return (int) (itemA.mtime - itemB.mtime);
        }
    }

    /**
     * SeafDirent name comparator class
     */
    public class DirentNameComparator implements Comparator<SeafDirent> {

        @Override
        public int compare(SeafDirent itemA, SeafDirent itemB) {
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
