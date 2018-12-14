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
		if [ $? -eq 0 ];then
			mountpoint -q $seaDir/data/seafile/$user/$nDATA/Documents || busybox mount --bind /sdcard/Documents /sdcard/seafile/$user/$nDATA/Documents
			mountpoint -q $seaDir/data/seafile/$user/$nDATA/Pictures || busybox mount --bind /sdcard/Pictures /sdcard/seafile/$user/$nDATA/Pictures
		fi
	fi

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
	sleep 12
	mountpoint -q tmp && umount tmp
	mount -t tmpfs tmpfs tmp
	mkdir tmp/logs tmp/state

	seafile_uid=`awk '/org.openthos.seafile/{print $2}' /data/system/packages.list`
	chown -R $seafile_uid:$seafile_uid tmp

	#/data/data/seafile
	#[ -d $seafile_ddata ] || mkdir -p $seafile_ddata
	mountpoint -q $seaDir/data || busybox mount --bind /sdcard $seaDir/data

	[ -d $seafile_sdcard ] || mkdir -p $seafile_sdcard
	[ -d $seafile_sync ] || mkdir -p $seafile_sync
	[ -d /sdcard/Documents ] || mkdir -p /sdcard/Documents
	#mountpoint -q $seafile_sync || busybox mount --bind $seafile_sdcard $seafile_sync
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
				mountpoint -q $seaDir/data/seafile/$previous_user/$nDATA/Documents && umount $seaDir/data/seafile/$previous_user/$nDATA/Documents
				mountpoint -q $seaDir/data/seafile/$previous_user/$nDATA/Pictures && umount $seaDir/data/seafile/$previous_user/$nDATA/Pictures
			fi
			[ -d /sdcard/seafile/$user/$nDATA/Documents ] || mkdir -p /sdcard/seafile/$user/$nDATA/Documents
			[ -d /sdcard/seafile/$user/$nDATA/Pictures ] || mkdir -p /sdcard/seafile/$user/$nDATA/Pictures
			mountpoint -q $seaDir/data/seafile/$user/$nDATA/Documents || busybox mount --bind /sdcard/Documents /sdcard/seafile/$user/$nDATA/Documents
			mountpoint -q $seaDir/data/seafile/$user/$nDATA/Pictures || busybox mount --bind /sdcard/Pictures /sdcard/seafile/$user/$nDATA/Pictures
		fi
		###login check
		$proot_cmd account_login
	fi
	sleep 2

	if [ -f $conf_dir/quota.conf ];then
		source $conf_dir/quota.conf

		if [ ! $libs_other_info ];then
			echo "other libs info Null"
			continue
		fi

		if [ ! $libs_total_info ];then
			echo "total libs info Null"
			continue
		fi

		local_size=(`du -k -s $seafile_sdcard/$user/$nDATA`)
		if [ $? -ne 0 ];then
			echo "retrieve local-size failed"
			continue
		fi

		local_usage=`expr ${local_size[0]} \* 1024`

		echo ${local_size[@]}, $local_usage

		data_usage=`expr $libs_other_info + $local_usage`
		data_rate=`awk 'BEGIN{printf "%.10f\n",('$data_usage'/'$libs_total_info')}'`
		echo $data_rate, $data_usage

		status_info=(`$proot_cmd seaf-cli status-info -c $conf_dir`)
		echo ${status_info[@]}

		if [ $(expr $data_rate \> 0.8) -eq 1 ];then
			echo "WARNING" > $seaDir/$quota_info_output
			if [ $(expr $data_rate \> 0.9) -eq 1 ];then
				echo "DISABLED" > $seaDir/$quota_info_output
				if echo "${status_info[@]}" | grep -w "auto sync disabled" &>/dev/null; then
					echo 'seafile alreay disabled'
				else
					$proot_cmd seaf-cli disable-auto-sync -c $conf_dir
					echo 'seafile will disable'
				fi
			else
				if echo "${status_info[@]}" | grep -w "auto sync disabled" &>/dev/null; then
					$proot_cmd seaf-cli enable-auto-sync -c $conf_dir
					echo 'seafile need auto sync enable'
				fi
			fi
		else
			[ -f $seaDir/$quota_info_output ] && rm $seaDir/$quota_info_output
			if echo "${status_info[@]}" | grep -w "auto sync disabled" &>/dev/null; then
				$proot_cmd seaf-cli enable-auto-sync -c $conf_dir
				echo 'seafile need auto sync enable'
			fi
		fi
	fi
done&

while true
do
{
	[ -f $conf_dir/account.conf ] && source $conf_dir/account.conf
	if [ ! $token ];then
		echo "retrieve token failed"
		continue
	fi

	user_info=(`$proot_cmd seaf-cli user-info -c $conf_dir -s $server_url -u $user -tk $token`)
	if [ $? -ne 0 ];then
		echo "retrieve user-info failed"
		continue
	fi

	libs_info=(`$proot_cmd seaf-cli list-remote -a -c $conf_dir -s $server_url -u $user -tk $token`)
	if [ $? -ne 0 ];then
		echo "retrieve libraries-info failed"
		continue
	fi

	if [ ! ${libs_info[@]} ];then
		echo "libraries-info Null"
		continue
	fi

	lib_data=
	mCount=3 #Retrieve first ID
	for i in ${!libs_info[@]}
	do
		if [ "$i" -eq "$mCount" ];then
			if [ "${libs_info[i]}" -eq "$nDATA" ];then
				lib_data=${libs_info[i+2]}
				break
			fi
			let "mCount+=3"
		fi
	done

	libs_other=`expr ${user_info[5]} - $lib_data`
	echo ${libs_info[@]}, ${user_info[9]}
	echo $lib_data, $libs_other

	[ -f $conf_dir/quota.conf ] && rm $conf_dir/quota.conf
	echo "libs_other_info=$libs_other" >> $conf_dir/quota.conf
	echo "libs_total_info=${user_info[9]}" >> $conf_dir/quota.conf

	sleep 315
}
done
