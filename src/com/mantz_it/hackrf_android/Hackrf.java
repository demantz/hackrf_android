package com.mantz_it.hackrf_android;

import java.util.HashMap;
import java.util.Iterator;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.util.Log;
import android.widget.Toast;

/**
 * <h1>HackRF USB Library for Android</h1>
 * 
 * Module:      Hackrf.java
 * Description: The Hackrf class represents the HackRF device and 
 *              acts as abstraction layer that manages the USB
 *              communication between the device and the application.
 * 
 * @author Dennis Mantz
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 */
public class Hackrf {
	
	public UsbManager usbManager = null;
	public UsbDevice usbDevice = null;
	public UsbInterface usbInterface = null;
	public UsbDeviceConnection usbConnection = null;
	
	// Constants:
	private static final String logTag = "HACKRF";
	private static final String HACKRF_USB_PERMISSION = "com.mantz_it.hackrf_android.USB_PERMISSION";
	private static final int HACKRF_VENDOR_REQUEST_SET_TRANSCEIVER_MODE = 1;
	private static final int HACKRF_VENDOR_REQUEST_MAX2837_WRITE = 2;
	private static final int HACKRF_VENDOR_REQUEST_MAX2837_READ = 3;
	private static final int HACKRF_VENDOR_REQUEST_SI5351C_WRITE = 4;
	private static final int HACKRF_VENDOR_REQUEST_SI5351C_READ = 5;
	private static final int HACKRF_VENDOR_REQUEST_SAMPLE_RATE_SET = 6;
	private static final int HACKRF_VENDOR_REQUEST_BASEBAND_FILTER_BANDWIDTH_SET = 7;
	private static final int HACKRF_VENDOR_REQUEST_RFFC5071_WRITE = 8;
	private static final int HACKRF_VENDOR_REQUEST_RFFC5071_READ = 9;
	private static final int HACKRF_VENDOR_REQUEST_SPIFLASH_ERASE = 10;
	private static final int HACKRF_VENDOR_REQUEST_SPIFLASH_WRITE = 11;
	private static final int HACKRF_VENDOR_REQUEST_SPIFLASH_READ = 12;
	private static final int HACKRF_VENDOR_REQUEST_BOARD_ID_READ = 14;
	private static final int HACKRF_VENDOR_REQUEST_VERSION_STRING_READ = 15;
	private static final int HACKRF_VENDOR_REQUEST_SET_FREQ = 16;
	private static final int HACKRF_VENDOR_REQUEST_AMP_ENABLE = 17;
	private static final int HACKRF_VENDOR_REQUEST_BOARD_PARTID_SERIALNO_READ = 18;
	private static final int HACKRF_VENDOR_REQUEST_SET_LNA_GAIN = 19;
	private static final int HACKRF_VENDOR_REQUEST_SET_VGA_GAIN = 20;
	private static final int HACKRF_VENDOR_REQUEST_SET_TXVGA_GAIN = 21;
	private static final int HACKRF_VENDOR_REQUEST_ANTENNA_ENABLE = 23;
	private static final int HACKRF_VENDOR_REQUEST_SET_FREQ_EXPLICIT = 24;
	
	
	/**
	 * Initializing the Hackrf Instance with a USB Device.
	 * 
	 * @return false if no Hackrf could be found
	 */
	public static boolean initHackrf(Context context, final HackrfCallbackInterface callbackInterface)
	{
		final UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
		UsbDevice hackrfUsbDvice = null;
		
		// Get a list of connected devices
		HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
		
		// Iterate over the list. Use the first Device that matches a HackRF
		Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
		while(deviceIterator.hasNext()){
			UsbDevice device = deviceIterator.next();
		    
			// HackRF One (Vendor ID: 7504 [0x1d50]; Product ID: 24713 [0x6089] )
			if ( device.getVendorId() == 7504 && device.getProductId() == 24713 )
			{
				Log.d(logTag,"Found HackRF One at " + device.getDeviceName());
				hackrfUsbDvice = device;
			}
		    
			// HackRF Jawbreaker (Vendor ID: 7504 [0x1d50]; Product ID: 24651 [0x604b])
			if ( device.getVendorId() == 7504 && device.getProductId() == 24651 )
			{
				Log.d(logTag,"Found HackRF Jawbreaker at " + device.getDeviceName());
				hackrfUsbDvice = device;
			}
		}
		
		// Check if we found a device:
		if (hackrfUsbDvice == null)
		{
			Log.e(logTag,"No HackRF Device found.");
			return false;
		}
		
		// Requesting Permissions:
		// First we define a broadcast receiver that handles the permission_granted intend:
		BroadcastReceiver permissionBroadcastReceiver = new BroadcastReceiver() {
		    public void onReceive(Context context, Intent intent) {
		        if (HACKRF_USB_PERMISSION.equals(intent.getAction())) {
	                UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
	                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false) && device != null) {
	                	// We have permissions to open the device! Lets init the hackrf instance and
	                	// return it to the calling application.
	                	Log.d(logTag,"Permission granted for device " + device.getDeviceName());
	                	try {
							Hackrf hackrf = new Hackrf(usbManager, device);
							Toast.makeText(context, "HackRF at " + device.getDeviceName() + " is ready!",Toast.LENGTH_LONG).show();
							callbackInterface.onHackrfReady(hackrf);
						} catch (HackrfUsbException e) {
							Log.e(logTag, "Couldn't open device " + device.getDeviceName());
							Toast.makeText(context, "Couldn't open HackRF device",Toast.LENGTH_LONG).show();
		                    callbackInterface.onHackrfError("Couldn't open device " + device.getDeviceName());
						}
	                } 
	                else 
	                {
	                    Log.e(logTag, "Permission denied for device " + device.getDeviceName());
	                    Toast.makeText(context, "Permission denied to open HackRF device",Toast.LENGTH_LONG).show();
	                    callbackInterface.onHackrfError("Permission denied for device " + device.getDeviceName());
	                }
		        }
		    }
		};
		
		// Now create a intent to request for the permissions and register the broadcast receiver for it:
		PendingIntent mPermissionIntent = PendingIntent.getBroadcast(context, 0, new Intent(HACKRF_USB_PERMISSION), 0);
		IntentFilter filter = new IntentFilter(HACKRF_USB_PERMISSION);
		context.registerReceiver(permissionBroadcastReceiver, filter);
		
		// Fire the request:
		usbManager.requestPermission(hackrfUsbDvice, mPermissionIntent);
		Log.d(logTag,"Permission request for device " + hackrfUsbDvice.getDeviceName() + " was send. waiting...");
		
		return true;
	}
	
	/**
	 * Initializing the Hackrf Instance with a USB Device.
	 * Note: The application must have reclaimed permissions to
	 * access the USB Device BEFOR calling this constructor.
	 * 
	 * @param usbManager	Instance of the USB Manager (System Service)
	 * @param usbDevice		Instance of an USB Device representing the HackRF
	 * @throws HackrfUsbException
	 */
	private Hackrf (UsbManager usbManager, UsbDevice usbDevice) throws HackrfUsbException
	{
		this.usbManager = usbManager;
		this.usbDevice = usbDevice;
		this.usbInterface = usbDevice.getInterface(0);
		this.usbConnection = usbManager.openDevice(usbDevice);
		
		if(this.usbConnection == null)
		{
			Log.e(logTag, "Couldn't open HackRF USB Device!");
			throw(new HackrfUsbException("Couldn't open HackRF USB Device!"));
		}
	}
	
	
	/**
	 * Executes a Read Request.
	 * 
	 * Note: This function interacts with the USB Hardware and
	 * should not be called from a GUI Thread!
	 * 
	 * @param request	request type (HACKRF_VENDOR_REQUEST_**_READ)
	 * @param buffer	reference to the receive buffer
	 * @param length	length of the receive buffer
	 * @return count of received bytes. Negative on error
	 * @throws HackrfUsbException
	 */
	private int sendReadRequest(int request, byte[] buffer, int length) throws HackrfUsbException
	{
		int len = 0;
		
		// Claim the usb interface
		if( !this.usbConnection.claimInterface(this.usbInterface, true))
		{
			Log.e(logTag, "Couldn't claim HackRF USB Interface!");
			throw(new HackrfUsbException("Couldn't claim HackRF USB Interface!"));
		}
		
		// Send Board ID Read request
		len = this.usbConnection.controlTransfer(
				UsbConstants.USB_DIR_IN | UsbConstants.USB_TYPE_VENDOR,	// Request Type
				request,	// Request
				0,			// Value (unused)
				0,			// Index (unused)
				buffer,		// Buffer
				length, 	// Length
				0			// Timeout
			);
		
		// Release usb interface
		this.usbConnection.releaseInterface(this.usbInterface);
		
		return len;
	}
	
	/**
	 * Returns the Board ID of the HackRF.
	 * 
	 * Note: This function interacts with the USB Hardware and
	 * should not be called from a GUI Thread!
	 * 
	 * @return HackRF Board ID
	 * @throws HackrfUsbException
	 */
	public byte getBoardID() throws HackrfUsbException
	{
		byte[] buffer = new byte[1];
		
		if (this.sendReadRequest(HACKRF_VENDOR_REQUEST_BOARD_ID_READ, buffer, 1) < 1)
		{
			Log.e(logTag, "USB Transfer failed!");
			throw(new HackrfUsbException("USB Transfer failed!"));
		}
		
		return buffer[0];
	}
	
	/**
	 * Converts the Board ID into a human readable String (e.g. #2 => "HackRF One")
	 * 
	 * @param boardID	boardID to convert
	 * @return Board ID interpretation as String
	 * @throws HackrfUsbException
	 */
	public static String convertBoardIdToString(int boardID)
	{
		switch(boardID)
		{
			case 0: return "Jellybean";
			case 1: return "Jawbreaker";
			case 2: return "HackRF One";
			default: return "INVALID BOARD ID";
		}
	}
	
	/**
	 * Returns the Version String of the HackRF.
	 * 
	 * Note: This function interacts with the USB Hardware and
	 * should not be called from a GUI Thread!
	 * 
	 * @return HackRF Version String
	 * @throws HackrfUsbException
	 */
	public String getVersionString() throws HackrfUsbException
	{
		byte[] buffer = new byte[256];
		int len = 0;
		
		len = this.sendReadRequest(HACKRF_VENDOR_REQUEST_VERSION_STRING_READ, buffer, 255);
		
		if (len < 1)
		{
			Log.e(logTag, "USB Transfer failed!");
			throw(new HackrfUsbException("USB Transfer failed!"));
		}
		
		buffer[len] = '\0';
		
		return new String(buffer);
	}
	
	
	/**
	 * Returns the Part ID + Serial Number of the HackRF.
	 * 
	 * Note: This function interacts with the USB Hardware and
	 * should not be called from a GUI Thread!
	 * 
	 * @return int[2+6] => int[0-1] is Part ID; int[2-5] is Serial No
	 * @throws HackrfUsbException
	 */
	public int[] getPartIdAndSerialNo() throws HackrfUsbException
	{
		byte[] buffer = new byte[8+16];
		int[] ret = new int[2+4];
		
		if(this.sendReadRequest(HACKRF_VENDOR_REQUEST_BOARD_PARTID_SERIALNO_READ, buffer, 8+16) < 8+16)
		{
			Log.e(logTag, "USB Transfer failed!");
			throw(new HackrfUsbException("USB Transfer failed!"));
		}
		
		for(int i = 0; i < 6; i++)
		{
			ret[i] = buffer[4*i] & 0xFF | (buffer[4*i+1] & 0xFF) << 8 | 
					(buffer[4*i+2] & 0xFF) << 16 | (buffer[4*i+3] & 0xFF) << 24;
		}
		
		return ret;
	}
	
	
	
	
	
	
	
	
	
	

}
