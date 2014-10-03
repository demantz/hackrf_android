package com.mantz_it.hackrf_android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public class DeviceAttachedBroadcastReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		Toast.makeText(context, "HackRF One Attached!!!!.",Toast.LENGTH_LONG).show();
	}

}
