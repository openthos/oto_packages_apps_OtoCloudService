package org.openthos.seafile.seaapp.transfer;

/**
 * Upload state listener
 *
 */
public interface UploadStateListener {
    void onFileUploadProgress(int taskID);
    void onFileUploaded(int taskID);
    void onFileUploadCancelled(int taskID);
    void onFileUploadFailed(int taskID);
}
