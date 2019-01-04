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

	state_info=(`$proot_cmd seaf-cli status-info -c $conf_dir`)
	echo "${state_info[@]}" | grep -E 'downloading|uploading'
	if [ $? -eq 0 ];then
		echo "fetch" > $data_info_output
	fi
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

while false
do
{
	sleep 315

	[ -f $conf_dir/account.conf ] && source $conf_dir/account.conf
	if [ ! $token ];then
		echo "retrieve token failed"
		continue
	fi

	user_info=(`$proot_cmd seaf-cli user-info -c $conf_dir -s $server_url -tk $token`)
	if [ $? -ne 0 ];then
		echo "retrieve user-info failed"
		continue
	fi

	unset libs_info
	libs_info=(`$proot_cmd seaf-cli list-remote -a -c $conf_dir -s $server_url -tk $token 2>&1`)
	if [ $? -ne 0 ];then
		echo "retrieve libraries-info failed"
		continue
	fi
	if [[ ${libs_info[@]} = *"HTTP Error 401"* ]];then
		echo "token-invalid" > $data_info_output
		$proot_cmd account_desync
		continue
	fi
	if [ "${libs_info[1]}"x != "ID"x ];then
		#echo "libraries-info Null"
		continue
	fi

	lib_data=
	mCount=3 #Retrieve first ID
	for i in ${!libs_info[@]}
	do
		if [ "$i" -eq "$mCount" ];then
			if [ "${libs_info[i]}"x = "$nDATA"x ];then
				lib_data=${libs_info[i+2]}
				break
			fi
			let "mCount+=3"
		fi
	done

	libs_other=`expr ${user_info[5]} - $lib_data`
	#echo ${libs_info[@]}, ${user_info[9]}
	#echo $lib_data, $libs_other

	[ -f $conf_dir/quota.conf ] && rm $conf_dir/quota.conf
	echo "libs_other_info=$libs_other" >> $conf_dir/quota.conf
	echo "libs_total_info=${user_info[9]}" >> $conf_dir/quota.conf
}
done&

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
	if [ -f $data_info_output ]&&[[ "$old_state" != "$state_info" ]];then
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
				export TMPDIR=/data/local/tmp
				data_blk=`awk '/ \/data /{print $1}' /proc/mounts `
				datafiles=/dev/block/${data_blk:5}
				[  -h $data_blk ] || ln -s $datafiles /dev/
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

	if [ -f $conf_dir/quota.conf ];then
		source $conf_dir/quota.conf

		if [ ! $libs_other_info ];then
			#echo "other libs info Null"
			continue
		fi

		if [ ! $libs_total_info ];then
			#echo "total libs info Null"
			continue
		fi

		local_size=(`du -k -s $seafile_sdcard/$user/$nDATA`)
		if [ $? -ne 0 ];then
			echo "retrieve local-size failed"
			continue
		fi

		local_usage=`expr ${local_size[0]} \* 1024`
		#echo ${local_size[@]}, $local_usage

		data_usage=`expr $libs_other_info + $local_usage`
		data_rate=`awk 'BEGIN{printf "%.10f\n",('$data_usage'/'$libs_total_info')}'`
		#echo $data_rate, $data_usage

		status_info=(`$proot_cmd seaf-cli status-info -c $conf_dir`)
		#echo ${status_info[@]}

		if [ $(expr $data_rate \> 0.9) -eq 1 ];then
			if echo "${status_info[@]}" | grep -w "auto sync disabled" &>/dev/null; then
				echo 'seafile alreay disabled' &>/dev/null
			else
				#echo 'seafile will disable'
				$proot_cmd seaf-cli disable-auto-sync -c $conf_dir
				if [ -f $quota_info_output ];then
					source $quota_info_output
					if [ $quota_state != "DISABLED" ];then
						echo quota_state=DISABLED > $quota_info_output
					fi
				else
					echo quota_state=DISABLED > $quota_info_output
				fi
			fi
		elif [ $(expr $data_rate \> 0.8) -eq 1 ];then
			if echo "${status_info[@]}" | grep -w "auto sync disabled" &>/dev/null; then
				#echo 'seafile need auto sync enable'
				$proot_cmd seaf-cli enable-auto-sync -c $conf_dir
			fi
			if [ -f $quota_info_output ];then
				source $quota_info_output
				if [ $quota_state != "WARNING" ];then
					echo quota_state=WARNING > $quota_info_output
				fi
			else
				echo quota_state=WARNING > $quota_info_output
			fi
		else
			[ -f $quota_info_output ] && rm $quota_info_output
			if echo "${status_info[@]}" | grep -w "auto sync disabled" &>/dev/null; then
				$proot_cmd seaf-cli enable-auto-sync -c $conf_dir
				#echo 'seafile need auto sync enable'
			fi
		fi
	fi
done
