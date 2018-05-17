package org.openthos.seafile;

import java.io.File;
import java.util.ArrayList;

/**
 * Created by Wang Zhixu on 12/23/16.
 */

public class SeafileAccount {
    public String mUserName;
    public int mUserId;
    public SeafileLibrary mDataLibrary;
    public SeafileLibrary mSettingLibrary;
    public File mFile;

    public SeafileAccount(){
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
}
