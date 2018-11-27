#!/system/bin/sh

cd `dirname $0`
source setenv

seaf_start()
{
	[ -d $seafile_data ] || mkdir -p $seafile_data

	$proot_cmd seaf-cli init -c $conf_dir -d $seafile_data

	$proot_cmd seaf-cli start -c $conf_dir &
	pid_start=$!

	sleep 4
}

seaf_stop()
{
	$proot_cmd seaf-cli stop > /dev/null 2>&1
	$proot_cmd seaf-cli stop -c $conf_dir > /dev/null 2>&1
}

set_environment()
{
		[ -d tmp ] || mkdir -p tmp
		mountpoint -q tmp && umount tmp
		mount -t tmpfs tmpfs tmp
		mkdir tmp/logs tmp/state

		seafile_uid=`awk '/org.openthos.seafile/{print $2}' /data/system/packages.list`
		chown -R $seafile_uid:$seafile_uid tmp
		busybox chmod -R +x .

		#/data/data/seafile
		[ -d $seafile_ddata ] || mkdir -p $seafile_ddata
		mountpoint -q $seaDir/data || busybox mount --bind $seafile_ddata $seaDir/data

		[ -d $seafile_sdcard ] || mkdir -p $seafile_sdcard
		[ -d $seafile_sync ] || mkdir -p $seafile_sync
		mountpoint -q $seafile_sync || busybox mount --bind $seafile_sdcard $seafile_sync

		if [ -f $conf_dir/account.conf ];then
			source $conf_dir/account.conf
			mountpoint -q $seafile_sync && busybox mount --bind /sdcard/Documents $seafile_sync/$user/$nDATA/Documents
			mountpoint -q $seafile_sync && busybox mount --bind /sdcard/Pictures $seafile_sync/$user/$nDATA/Pictures
		fi
}

do_exit()
{
	seaf_stop
	echo 'seafile daemon EXIT'

	umount $seafile_sync/$user/$nDATA/Documents
	umount $seafile_sync/$user/$nDATA/Pictures
	umount tmp
}

cd $seaDir

if [ -f /cache/keeper.log ];then
	echo "There's already a seafile keeper running !"
	date
	exit
fi

set_environment

#Catch the exit SIG, "kill PID" instead of "kill -9 PID"
trap do_exit exit

while true
do
	#status_info=(`$proot_cmd seaf-cli status -c $conf_dir`)
	$proot_cmd seaf-cli status-info -c $conf_dir > $status_info_output
	if [ $? -ne 0 ];then
		echo 'Restarting ...'
		seaf_stop
		seaf_start
	fi

	#if [ status_info err ]
	#account_sync()

	if [ -f $account_conf ];then

		grep "token=" $account_conf
		if [ $? -eq 0 ];then
			previous_user=$user
			source $account_conf
			if [ "$user"x != "$previous_user"x ];then
				umount $seafile_sync/$previous_user/$nDATA/Documents
				umount $seafile_sync/$previous_user/$nDATA/Pictures

				[ -d $seafile_sync/$user/$nDATA/Documents ] || mkdir -p $seafile_sync/$user/$nDATA/Documents
				[ -d $seafile_sync/$user/$nDATA/Pictures ] || mkdir -p $seafile_sync/$user/$nDATA/Pictures
				mountpoint -q $seafile_sync && busybox mount --bind /sdcard/Documents $seafile_sync/$user/$nDATA/Documents
				mountpoint -q $seafile_sync && busybox mount --bind /sdcard/Pictures $seafile_sync/$user/$nDATA/Pictures
			fi
		fi

		###login check
		$proot_cmd account_login

	fi

	sleep 2
done

