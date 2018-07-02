package org.openthos.seafile.seaapp.notification;

import android.app.PendingIntent;
import android.content.Intent;

import java.util.List;

import org.openthos.seafile.seaapp.CustomNotificationBuilder;
import org.openthos.seafile.R;
import org.openthos.seafile.seaapp.SeafileActivity;
import org.openthos.seafile.seaapp.transfer.DownloadTaskInfo;
import org.openthos.seafile.seaapp.transfer.DownloadTaskManager;
import org.openthos.seafile.seaapp.transfer.TaskState;
import org.openthos.seafile.seaapp.transfer.TransferService;

/**
 * Download notification provider
 *
 */
public class DownloadNotificationProvider extends BaseNotificationProvider {

    public DownloadNotificationProvider(DownloadTaskManager downloadTaskManager,
                                        TransferService transferService) {
        super(downloadTaskManager, transferService);
    }

    @Override
    protected String getProgressInfo() {
        String progressStatus = "";

        if (txService == null)
            return progressStatus;

        // failed or cancelled tasks won`t be shown in notification state
        // but failed or cancelled detailed info can be viewed in TransferList
        if (getState().equals(NotificationState.NOTIFICATION_STATE_COMPLETED_WITH_ERRORS))
            progressStatus = SeafileActivity.mActivity.getString(R.string.notification_download_completed);
        else if (getState().equals(NotificationState.NOTIFICATION_STATE_COMPLETED))
            progressStatus = SeafileActivity.mActivity.getString(R.string.notification_download_completed);
        else if (getState().equals(NotificationState.NOTIFICATION_STATE_PROGRESS)) {
            int downloadingCount = 0;
            List<DownloadTaskInfo> infos = txService.getAllDownloadTaskInfos();
            for (DownloadTaskInfo info : infos) {
                if (info.state.equals(TaskState.INIT)
                        || info.state.equals(TaskState.TRANSFERRING))
                    downloadingCount++;
            }
            if (downloadingCount != 0)
                progressStatus = SeafileActivity.mActivity.getResources().
                        getQuantityString(R.plurals.notification_download_info,
                                downloadingCount,
                                downloadingCount,
                                getProgress());
        }
        return progressStatus;
    }

    @Override
    protected NotificationState getState() {
        if (txService == null)
            return NotificationState.NOTIFICATION_STATE_COMPLETED;

        List<DownloadTaskInfo> infos = txService.getAllDownloadTaskInfos();

        int progressCount = 0;
        int errorCount = 0;

        for (DownloadTaskInfo info : infos) {
            if (info == null)
                continue;
            if (info.state.equals(TaskState.INIT)
                    || info.state.equals(TaskState.TRANSFERRING))
                progressCount++;
            else if (info.state.equals(TaskState.FAILED)
                    || info.state.equals(TaskState.CANCELLED))
                errorCount++;
        }

        if (progressCount == 0 && errorCount == 0)
            return NotificationState.NOTIFICATION_STATE_COMPLETED;
        else if (progressCount == 0 && errorCount > 0)
            return NotificationState.NOTIFICATION_STATE_COMPLETED_WITH_ERRORS;
        else // progressCount > 0
            return NotificationState.NOTIFICATION_STATE_PROGRESS;
    }

    @Override
    protected int getNotificationID() {
        return NOTIFICATION_ID_DOWNLOAD;
    }

    @Override
    protected String getNotificationTitle() {
        return SeafileActivity.mActivity.getString(R.string.notification_download_started_title);
    }

    @Override
    public void notifyStarted() {
//        Intent dIntent = new Intent(SeafileActivity.mActivity, TransferActivity.class);
//        dIntent.putExtra(NOTIFICATION_MESSAGE_KEY, NOTIFICATION_OPEN_DOWNLOAD_TAB);
//        dIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

//        PendingIntent dPendingIntent = PendingIntent.getActivity(SeafileActivity.mActivity,
//                (int) System.currentTimeMillis(),
//                dIntent,
//                0);
//        mNotifBuilder = CustomNotificationBuilder.getNotificationBuilder(SeafileActivity.mActivity)
//                .setSmallIcon(R.drawable.icon)
//                .setContentTitle(SeafileActivity.mActivity.getString(R.string.notification_download_started_title))
//                .setOngoing(true)
//                .setContentText(SeafileActivity.mActivity.getString(R.string.notification_download_started_title))
//                .setContentIntent(dPendingIntent)
//                .setProgress(100, 0, false);

        // Make this service run in the foreground, supplying the ongoing
        // notification to be shown to the user while in this state.
        txService.startForeground(NOTIFICATION_ID_DOWNLOAD, mNotifBuilder.build());
    }

    @Override
    protected int getProgress() {
        long downloadedSize = 0l;
        long totalSize = 0l;
        if (txService == null)
            return 0;

        List<DownloadTaskInfo> infos = txService.getAllDownloadTaskInfos();
        for (DownloadTaskInfo info : infos) {
            if (info == null)
                continue;
            downloadedSize += info.finished;
            totalSize += info.fileSize;
        }

        // avoid ArithmeticException
        if (totalSize == 0l)
            return 0;
        return (int) (downloadedSize * 100 / totalSize);
    }

}