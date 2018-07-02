LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_STATIC_JAVA_LIBRARIES := android-support-v4
LOCAL_STATIC_JAVA_LIBRARIES += android-support-v7-appcompat
LOCAL_STATIC_JAVA_LIBRARIES += jsoup
LOCAL_STATIC_JAVA_LIBRARIES += imageloader1.9.5

LOCAL_SRC_FILES := $(call all-java-files-under, src) \
                   src/org/openthos/seafile/ISeafileService.aidl

LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/seaapp/res $(LOCAL_PATH)/res
LOCAL_AAPT_FLAGS := --auto-add-overlay

LOCAL_PACKAGE_NAME := OtoCloudService
LOCAL_PRIVILEGED_MODULE := true
LOCAL_JNI_SHARED_LIBRARIES := libkillpid

LOCAL_CERTIFICATE := platform

include $(BUILD_PACKAGE)
include $(CLEAR_VARS)
LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES  := jsoup:libs/jsoup-1.8.1.jar
LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES  += imageloader1.9.5:libs/universal-image-loader-1.9.5.jar
include $(BUILD_MULTI_PREBUILT)
include $(CLEAR_VARS)

LOCAL_CFLAGS := \
	-DANDROID_NDK \
	-fexceptions \
	-DNDEBUG \
	-D_REENTRANT \
	-DENV_UNIX \
	-DEXTERNAL_CODECS \
	-DUNICODE \
	-D_UNICODE

LOCAL_MODULE := libkillpid
LOCAL_SRC_FILES := jni/android/org_openthos_seafile_Jni.cpp
LOCAL_SHARED_LIBRARIES := liblog
#LOCAL_MODULE_TAGS := optional
include $(BUILD_SHARED_LIBRARY)
