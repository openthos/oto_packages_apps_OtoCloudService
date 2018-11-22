#!/system/bin/sh

cd `dirname $0`
source setenv

seaf_start()
{
	[ -d $seafile_ddata ] || mkdir -p $seafile_ddata
	mountpoint -q $seaDir/data || busybox mount --bind $seafile_ddata $seaDir/data

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

do_exit()
{
	seaf_stop
	echo 'seafile daemon EXIT'
}

cd $seaDir
busybox chmod -R 755 .
busybox chmod -R 777 tmp

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
		[ -d $seafile_sdcard ] || mkdir -p $seafile_sdcard
		[ -d $seafile_sync ] || mkdir -p $seafile_sync
		mountpoint -q $seafile_sync || busybox mount --bind $seafile_sdcard $seafile_sync

		$proot_cmd account_login
	fi

	sleep 2
done

