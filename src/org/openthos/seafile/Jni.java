package org.openthos.seafile;

public final class Jni {

    static {
        System.loadLibrary("killpid");
    }

    public static native int nativeKillPid();
}
