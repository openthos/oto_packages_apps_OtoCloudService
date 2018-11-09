#!/system/bin/sh

export HOME=/
export LD_HWCAP_MASK=0
export SHELL=/bin/sh
export PROOT_NO_SECCOMP=1 #kernel4.9 execve() bug
export PROOT_TMPDIR=/system/linux/sea/tmp
export PROOT_TMP_DIR=$PROOT_TMPDIR
export PATH=/bin:/sbin:/usr/bin:/usr/sbin:/usr/local/bin

#./proot-x86-2 -w / -r ./ -b /dev -b /sys -b /proc
/system/linux/sea/proot  -w / -r /system/linux/sea -b /dev -b /sys -b /proc "$@"



