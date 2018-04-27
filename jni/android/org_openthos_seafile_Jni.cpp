//
// Created by wang on 18-4-26.
//

#include <stdio.h>
#include <stdlib.h>
#include <ctype.h>
#include <string.h>
#include <sys/types.h>
#include <dirent.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <signal.h>
#include <cutils/log.h>
#include <cutils/properties.h>
#include <stdbool.h>
#include <assert.h>
#include <jni.h>
#include "org_openthos_seafile_Jni.h"

#ifdef __cplusplus
extern "C" {
#endif


int get_pid_by_cmdline(const char *cl)
{
    DIR *d;
    struct dirent *de;
    char cmdline[1024];
    int fd, r;

    d = opendir("/proc");
    if(d == 0) return -1;

    while((de = readdir(d)) != 0) {
        if(isdigit(de->d_name[0])) {
            int pid = atoi(de->d_name);
            sprintf(cmdline, "/proc/%d/cmdline", pid);
            fd = open(cmdline, O_RDONLY);
        if(fd == 0) {
            r = 0;
        } else {
            r = read(fd, cmdline, 1023);
            close(fd);
            if(r < 0) r = 0;
        }
        cmdline[r] = 0;
        // printf("%d | %s\n", pid, cmdline);
        if (strcmp(cmdline, cl) == 0)
        {
            return pid;
        }
        }
    }
    closedir(d);

    return 0;
}

int main()
{
    int sf_pid;
    //char cmdline[0x100] = {0};
    sf_pid = get_pid_by_cmdline("zygote");
    //snprintf(cmdline, 0x100, "su -c kill\ %d", sf_pid);
    //ALOGD("wwww  kill. PID: %d  cmd: %s" , sf_pid, cmdline);
    ALOGD("wwww  kill. PID: %d" , sf_pid);
    //system(cmdline);
    //kill(sf_pid, SIGTERM);
    return get_pid_by_cmdline("zygote");
}

JNIEXPORT jint JNICALL Java_org_openthos_seafile_Jni_nativeKillPid(JNIEnv* env, jclass jclazz)
{
    return main();
}

#ifdef __cplusplus
}
#endif
