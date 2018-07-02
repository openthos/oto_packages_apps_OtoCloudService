package org.openthos.seafile.seaapp.transfer;

import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;


import java.util.ArrayList;
import java.util.List;

import org.openthos.seafile.seaapp.Account;
import org.openthos.seafile.seaapp.SeafileActivity;

//import com.google.common.collect.Lists;

/**
 * Upload task manager
 * <p/>
 */
public class UploadTaskManager extends TransferManager implements UploadStateListener {
    private static final String DEBUG_TAG = "UploadTaskManager";

    public static final String BROADCAST_FILE_UPLOAD_SUCCESS = "uploaded";
    public static final String BROADCAST_FILE_UPLOAD_FAILED = "uploadFailed";
    public static final String BROADCAST_FILE_UPLOAD_PROGRESS = "uploadProgress";
    public static final String BROADCAST_FILE_UPLOAD_CANCELLED = "uploadCancelled";

//    private static UploadNotificationProvider mNotifyProvider;


    public int addTaskToQue(Account account, String repoID, String repoName, String dir, String filePath, boolean isUpdate, boolean isCopyToLocal, boolean byBlock) {
        if (repoID == null || repoName == null)
            return 0;

        // create a new one to avoid IllegalStateException
//        UploadTask task = new UploadTask(++notificationID, account, repoID, repoName, dir, filePath, isUpdate, isCopyToLocal, byBlock, this);
//        addTaskToQue(task);
//        return task.getTaskID();
        return -1;
    }

    public List<UploadTaskInfo> getNoneCameraUploadTaskInfos() {
//        List<UploadTaskInfo> noneCameraUploadTaskInfos = Lists.newArrayList();
        List<UploadTaskInfo> noneCameraUploadTaskInfos = new ArrayList<>();
        List<UploadTaskInfo> uploadTaskInfos = (List<UploadTaskInfo>) getAllTaskInfoList();
        for (UploadTaskInfo uploadTaskInfo : uploadTaskInfos) {
            // use isCopyToLocal as a flag to mark a camera photo upload task if false
            // mark a file upload task if true
            if (!uploadTaskInfo.isCopyToLocal) {
                continue;
            }
            noneCameraUploadTaskInfos.add(uploadTaskInfo);
        }

        return noneCameraUploadTaskInfos;
    }

    public void retry(int taskID) {
        UploadTask task = (UploadTask) getTask(taskID);
        if (task == null || !task.canRetry())
            return;
//        addTaskToQue(task.getAccount(), task.getRepoID(), task.getRepoName(), task.getDir(), task.getPath(), task.isUpdate(), task.isCopyToLocal(),false);
    }

    private void notifyProgress(int taskID) {
        UploadTaskInfo info = (UploadTaskInfo) getTaskInfo(taskID);
        if (info == null)
            return;

        // use isCopyToLocal as a flag to mark a camera photo upload task if false
        // mark a file upload task if true
        if (!info.isCopyToLocal)
            return;

        //Log.d(DEBUG_TAG, "notify key " + info.repoID);
//        if (mNotifyProvider != null) {
//            mNotifyProvider.updateNotification();
//        }

    }

//    public void saveUploadNotifProvider(UploadNotificationProvider provider) {
//        mNotifyProvider = provider;
//    }

//    public boolean hasNotifProvider() {
//        return mNotifyProvider != null;
//    }
//
//    public UploadNotificationProvider getNotifProvider() {
//        if (hasNotifProvider())
//            return mNotifyProvider;
//        else
//            return null;
//    }
//
//    public void cancelAllUploadNotification() {
//        if (mNotifyProvider != null)
//            mNotifyProvider.cancelNotification();
//    }

    // -------------------------- listener method --------------------//
    @Override
    public void onFileUploadProgress(int taskID) {
        Intent localIntent = new Intent(BROADCAST_ACTION).putExtra("type",
                BROADCAST_FILE_UPLOAD_PROGRESS).putExtra("taskID", taskID);
//        LocalBroadcastManager.getInstance(SeadroidApplication.getAppContext()).sendBroadcast(localIntent);
        LocalBroadcastManager.getInstance(SeafileActivity.mActivity).sendBroadcast(localIntent);
        notifyProgress(taskID);
    }

    @Override
    public void onFileUploaded(int taskID) {
        remove(taskID);
        doNext();
        Intent localIntent = new Intent(BROADCAST_ACTION).putExtra("type",
                BROADCAST_FILE_UPLOAD_SUCCESS).putExtra("taskID", taskID);
//        LocalBroadcastManager.getInstance(SeadroidApplication.getAppContext()).sendBroadcast(localIntent);
        LocalBroadcastManager.getInstance(SeafileActivity.mActivity).sendBroadcast(localIntent);
        notifyProgress(taskID);
    }

    @Override
    public void onFileUploadCancelled(int taskID) {
        Intent localIntent = new Intent(BROADCAST_ACTION).putExtra("type",
                BROADCAST_FILE_UPLOAD_CANCELLED).putExtra("taskID", taskID);
//        LocalBroadcastManager.getInstance(SeadroidApplication.getAppContext()).sendBroadcast(localIntent);
        LocalBroadcastManager.getInstance(SeafileActivity.mActivity).sendBroadcast(localIntent);
        notifyProgress(taskID);
    }

    @Override
    public void onFileUploadFailed(int taskID) {
        remove(taskID);
        doNext();
        Intent localIntent = new Intent(BROADCAST_ACTION).putExtra("type",
                BROADCAST_FILE_UPLOAD_FAILED).putExtra("taskID", taskID);
//        LocalBroadcastManager.getInstance(SeadroidApplication.getAppContext()).sendBroadcast(localIntent);
        LocalBroadcastManager.getInstance(SeafileActivity.mActivity).sendBroadcast(localIntent);
        notifyProgress(taskID);
    }

}
