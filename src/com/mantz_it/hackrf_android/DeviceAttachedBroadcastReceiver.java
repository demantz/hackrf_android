package com.mantz_it.hackrf_android;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.util.Log;
import android.widget.Toast;

public class DeviceAttachedBroadcastReceiver extends BroadcastReceiver {

	private static final String ACTION_USB_PERMISSION = "com.mantz_it.hackrf_android.USB_PERMISSION";
	
	@Override
	public void onReceive(Context context, Intent intent) {
		String action = intent.getAction();
        if (action.equals("android.hardware.usb.action.USB_DEVICE_ATTACHED")) {
        	this.onAttached(context, intent);
        } else if (action.equals(ACTION_USB_PERMISSION)) {
        	this.onPermissionGranted(context, intent);
        }
	}
	
	public void onAttached(Context context, Intent intent) {
		UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
		
		// Lets print as many info as we can!
		System.out.println("Found HackRF One!");
		System.out.println("Device Name: " + device.getDeviceName());
		System.out.println("Device ID: " + device.getDeviceId());
		System.out.println("Device Class: " + device.getDeviceClass());
		System.out.println("Device Subclass: " + device.getDeviceSubclass());
		System.out.println("Device Protocol: " + device.getDeviceProtocol());
		System.out.println("Device Interface Count: " + device.getInterfaceCount());
		
		// Iterate over all Interfaces
		for(int i = 0; i < device.getInterfaceCount(); i++)
		{
			UsbInterface usbInterface = device.getInterface(i);
			System.out.println("    Interface " + i + ": ID: " + usbInterface.getId());
			System.out.println("    Interface " + i + ": Class: " + usbInterface.getInterfaceClass());
			System.out.println("    Interface " + i + ": Protocol: " + usbInterface.getInterfaceProtocol());
			System.out.println("    Interface " + i + ": Subclass: " + usbInterface.getInterfaceSubclass());
			System.out.println("    Interface " + i + ": Endpoint Count: " + usbInterface.getEndpointCount());
			
			// Iterate over all Endpoints
			for(int e = 0; e < usbInterface.getEndpointCount(); e++)
			{
				UsbEndpoint endpoint = usbInterface.getEndpoint(e);
				System.out.println("        Endpoint " + e + ": Number: " + endpoint.getEndpointNumber());
				System.out.println("        Endpoint " + e + ": Attributes: " + endpoint.getAttributes());
				System.out.println("        Endpoint " + e + ": Direction: " + endpoint.getDirection());
				System.out.println("        Endpoint " + e + ": Interval: " + endpoint.getInterval());
				System.out.println("        Endpoint " + e + ": Max. Packet Size: " + endpoint.getMaxPacketSize());
				System.out.println("        Endpoint " + e + ": Type: " + endpoint.getType());
			}
		}
		
		// Requesting Permissions:
		UsbManager manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
		PendingIntent mPermissionIntent = PendingIntent.getBroadcast(context, 0, new Intent(ACTION_USB_PERMISSION), 0);
		manager.requestPermission(device, mPermissionIntent);
	}
	
	public void onPermissionGranted(Context context, Intent intent) {
		synchronized (this) {
            UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

            if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                if(device != null){
                	Log.d("HACKRF", "Permission Granted!");
                	
                	// Open a Connection to the Device:
            		UsbManager manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
            		UsbDeviceConnection connection = manager.openDevice(device);
            		connection.claimInterface(device.getInterface(0), true);
            		byte[] buffer = new byte[1];
            		connection.controlTransfer(
            				UsbConstants.USB_DIR_IN | UsbConstants.USB_TYPE_VENDOR,	// Request Type
            				14,			// Request
            				0,			// Value
            				0,			// Index
            				buffer,		// Buffer
            				1, 			// Length
            				0			// Timeout
            			);	
            		
            		System.out.println("HackRF Board ID: " + buffer[0]);
            		if(buffer[0] == 2)
            			Toast.makeText(context, "HackRF One Attached!!!!.",Toast.LENGTH_LONG).show();
                	
               }
            } 
            else {
                Log.d("HACKRF", "Couldn't get permissions to open device!");
            }
        }
	}

}
