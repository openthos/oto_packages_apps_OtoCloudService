#!/system/bin/sh

cd `dirname $0`
source setenv

seaf_start()
{
	[ -d $seafile_data ] || mkdir -p $seafile_data

	$proot_cmd seaf-cli init -c $conf_dir -d $seafile_data

	### if has account, must first mount the directorys
	if [ -f $conf_dir/account.conf ];then
		source $conf_dir/account.conf
		grep "user=" $conf_dir/account.conf
	fi

	$proot_cmd seaf-cli start -c $conf_dir &
	pid_start=$!

	sleep 4

	old_state=(`$proot_cmd seaf-cli status-info -c $conf_dir`)
}

seaf_stop()
{
	$proot_cmd seaf-cli stop > /dev/null 2>&1
	$proot_cmd seaf-cli stop -c $conf_dir > /dev/null 2>&1
}

set_environment()
{
	mountpoint -q tmp && umount tmp
	mount -t tmpfs tmpfs tmp
	mkdir tmp/logs tmp/state tmp/quota
	echo "quota_init" > tmp/quota/quota.state

	seafile_uid=`awk '/org.openthos.seafile/{print $2}' /data/system/packages.list`
	chown -R $seafile_uid:$seafile_uid tmp

	#/data/data/seafile
	#[ -d $seafile_ddata ] || mkdir -p $seafile_ddata
	mountpoint -q $seaDir/data || busybox mount --bind /data/media/0 $seaDir/data

	[ -d $seafile_sdcard ] || mkdir -p $seafile_sdcard
	[ -d $seafile_sync ] || mkdir -p $seafile_sync
	[ -d /sdcard/Documents ] || mkdir -p /sdcard/Documents
	[ -d /sdcard/Pictures ] || mkdir -p /sdcard/Pictures
	#mountpoint -q $seafile_sync || busybox mount --bind $seafile_sdcard $seafile_sync
	export TMPDIR=/data/local/tmp
	data_blk=`awk '/ \/data /{print $1}' /proc/mounts `
	datafiles=/dev/block/${data_blk:5}
	[  -h $data_blk ] || ln -s $datafiles /dev/
}

do_exit()
{
	seaf_stop
	echo 'seafile daemon EXIT'

	umount /data/media/0/seafile/$user/$nDATA/Documents
	umount /data/media/0/seafile/$user/$nDATA/Pictures
	umount tmp
}

cd $seaDir
set_environment

#Catch the exit SIG, "kill PID" instead of "kill -9 PID"
trap do_exit exit

old_state=""
while true
do
	sleep 2
	state_info=`$proot_cmd seaf-cli status-info -c $conf_dir 2>&1`
	if echo "$state_info" | grep -E "Invalid config directory|Connection refused";then
		echo 'Restarting ...'
		seaf_stop
		seaf_start
	fi
	if echo "$state_info" | grep -w "ermission";then
		echo "token-invalid" > $data_info_output
		$proot_cmd account_desync
		continue
	fi
	if [ "$old_state" != "$state_info" ];then
		echo "$state_info" > $status_info_output
		old_state="$state_info"
	fi
	#if [ status_info err ]
	#account_sync()

	if [ -f $account_conf ];then
		grep "token=" $account_conf
		###login check
		source $account_conf
		$proot_cmd account_login

		[ -f $conf_dir/account.conf ] && source $conf_dir/account.conf
		if [ ! $user ];then
				continue
		fi

		if [ "$action"x = "logout"x ];then
				continue
		fi

		if [ ! -d $seafile_sdcard/$user ];then
				data_blk=`awk '/ \/data /{print $1}' /proc/mounts `
				datafiles=/dev/block/${data_blk:5}
				mkdir -p $seafile_sdcard/$user
				chattr +P -R -p 0 /data/media/0
				chattr +P -p 666 /data/media/0/seafile/$user
				user_info=(`$proot_cmd seaf-cli user-info -c $conf_dir -s $server_url -tk $token`)
				hard_limit=`expr ${user_info[9]} \* 9 / 10000`
				soft_limit=`expr ${user_info[9]} \* 8 / 10000`
				setquota -P 666 $soft_limit'K' $hard_limit'K' 0 0 $datafiles

		fi
		$proot_cmd account_sync
	fi
	if [ -f $quota_info_output ];then
		grep "Error" $quota_info_output
		if [ $? -eq 0 ];then
			echo "$state_info" | grep -E "auto sync disabled"
			if [ $? -ne 0 ];then
				$proot_cmd seaf-cli disable-auto-sync -c $conf_dir
			fi
		else
			grep "quota_init" $quota_info_output
			if [ $? -ne 0 ];then
				echo "$state_info" | grep -E "auto sync disabled"
				if [ $? -eq 0 ];then
					$proot_cmd seaf-cli enable-auto-sync -c $conf_dir
				fi
			fi
		fi
	fi
done
