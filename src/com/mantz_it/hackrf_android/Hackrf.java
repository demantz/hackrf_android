package com.mantz_it.hackrf_android;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
	 * Converts a byte array into an integer using little endian byteorder.
	 * 
	 * @param b			byte array (length 4)
	 * @param offset	offset pointing to the first byte in the bytearray that should be used
	 * @return 			integer
	 */
	private int byteArrayToInt(byte[] b, int offset)
	{
		return b[offset+0] & 0xFF | (b[offset+1] & 0xFF) << 8 | 
					(b[offset+2] & 0xFF) << 16 | (b[offset+3] & 0xFF) << 24;
	}
	
	/**
	 * Converts an integer into a byte array using little endian byteorder.
	 * 
	 * @param i		integer
	 * @return 		byte array (length 4)
	 */
	private byte[] intToByteArray(int i)
	{
		byte[] b = new byte[4];
		b[0] = (byte) (i & 0xff);
		b[1] = (byte) ((i >> 8) & 0xff);
		b[2] = (byte) ((i >> 16) & 0xff);
		b[3] = (byte) ((i >> 24) & 0xff);
		return b;
	}
	
	/**
	 * Executes a Request to the USB interface.
	 * 
	 * Note: This function interacts with the USB Hardware and
	 * should not be called from a GUI Thread!
	 * 
	 * @param endpoint	USB_DIR_IN or USB_DIR_OUT
	 * @param request	request type (HACKRF_VENDOR_REQUEST_**_READ)
	 * @param value		value to use in the controlTransfer call
	 * @param index		index to use in the controlTransfer call
	 * @param buffer	buffer to use in the controlTransfer call
	 * @return count of received bytes. Negative on error
	 * @throws HackrfUsbException
	 */
	private int sendUsbRequest(int endpoint, int request, int value, int index, byte[] buffer) throws HackrfUsbException
	{
		int len = 0;
		
		// Determine the length of the buffer:
		if(buffer != null)
			len = buffer.length;
		
		// Claim the usb interface
		if( !this.usbConnection.claimInterface(this.usbInterface, true))
		{
			Log.e(logTag, "Couldn't claim HackRF USB Interface!");
			throw(new HackrfUsbException("Couldn't claim HackRF USB Interface!"));
		}
		
		// Send Board ID Read request
		len = this.usbConnection.controlTransfer(
				endpoint | UsbConstants.USB_TYPE_VENDOR,	// Request Type
				request,	// Request
				value,		// Value (unused)
				index,		// Index (unused)
				buffer,		// Buffer
				len, 		// Length
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
		
		if (this.sendUsbRequest(UsbConstants.USB_DIR_IN, HACKRF_VENDOR_REQUEST_BOARD_ID_READ, 0, 0, buffer) != 1)
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
		byte[] buffer = new byte[255];
		int len = 0;
		
		len = this.sendUsbRequest(UsbConstants.USB_DIR_IN, HACKRF_VENDOR_REQUEST_VERSION_STRING_READ, 0, 0, buffer);
		
		if (len < 1)
		{
			Log.e(logTag, "USB Transfer failed!");
			throw(new HackrfUsbException("USB Transfer failed!"));
		}
		
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
		
		if(this.sendUsbRequest(UsbConstants.USB_DIR_IN, HACKRF_VENDOR_REQUEST_BOARD_PARTID_SERIALNO_READ, 
				0, 0, buffer) != 8+16)
		{
			Log.e(logTag, "USB Transfer failed!");
			throw(new HackrfUsbException("USB Transfer failed!"));
		}
		
		for(int i = 0; i < 6; i++)
		{
			ret[i] = this.byteArrayToInt(buffer, 4*i);
		}
		
		return ret;
	}
	
	/**
	 * Sets the Sample Rate of the HackRF.
	 * 
	 * Note: This function interacts with the USB Hardware and
	 * should not be called from a GUI Thread!
	 * 
	 * @param	sampRate	Sample Rate in Hz
	 * @param	divider		Divider
	 * @return 	true on success
	 * @throws 	HackrfUsbException
	 */
	public boolean setSampleRate(int sampRate, int divider) throws HackrfUsbException
	{
		ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
		
		try {
			byteOut.write(this.intToByteArray(sampRate));
			byteOut.write(this.intToByteArray(divider));
		} catch (IOException e) {
			Log.e(logTag,"Error while converting arguments to byte buffer.");
			return false;
		}
		
		if(this.sendUsbRequest(UsbConstants.USB_DIR_OUT, HACKRF_VENDOR_REQUEST_SAMPLE_RATE_SET, 
				0, 0, byteOut.toByteArray()) != 8)
		{
			Log.e(logTag, "USB Transfer failed!");
			throw(new HackrfUsbException("USB Transfer failed!"));
		}
		
		return true;
	}
	
	/**
	 * Computes a valid Baseband Filter Bandwidth that is closest to
	 * a given Sample Rate. If there is no exact match, the returned
	 * Bandwidth will be smaller than the Sample Rate.
	 * 
	 * @param	sampRate	Bandwidth for the Baseband Filter
	 * @return 	Baseband Filter Bandwidth
	 * @throws 	HackrfUsbException
	 */
	public static int computeBasebandFilterBandwidth(int sampRate)
	{
		int bandwidth = 1750000;
		int[] supportedBandwidthValues = {1750000, 2500000, 3500000, 5000000, 5500000, 
										  6000000, 7000000, 8000000, 9000000, 10000000, 
										  12000000, 14000000, 15000000, 20000000, 24000000, 
										  28000000 };
		
		for(int candidate: supportedBandwidthValues)
		{
			if(sampRate < candidate)
				break;
			bandwidth = candidate;
		}

		return bandwidth;
	}
	
	/**
	 * Sets the baseband filter bandwidth of the HackRF.
	 * 
	 * Note: This function interacts with the USB Hardware and
	 * should not be called from a GUI Thread!
	 * 
	 * @param	bandwidth	Bandwidth for the Baseband Filter
	 * @return 	true on success
	 * @throws 	HackrfUsbException
	 */
	public boolean setBasebandFilterBandwidth(int bandwidth) throws HackrfUsbException
	{
		if(this.sendUsbRequest(UsbConstants.USB_DIR_OUT, HACKRF_VENDOR_REQUEST_BASEBAND_FILTER_BANDWIDTH_SET, 
				bandwidth & 0xffff, (bandwidth >> 16) & 0xffff, null) != 0)
		{
			Log.e(logTag, "USB Transfer failed!");
			throw(new HackrfUsbException("USB Transfer failed!"));
		}
		
		return true;
	}
	
	/**
	 * Sets the RX VGA Gain of the HackRF.
	 * 
	 * Note: This function interacts with the USB Hardware and
	 * should not be called from a GUI Thread!
	 * 
	 * @param	gain	RX VGA Gain (0-62)
	 * @return 	true on success
	 * @throws 	HackrfUsbException
	 */
	public boolean setRxVGAGain(int gain) throws HackrfUsbException
	{
		byte[] retVal = new byte[1];
		
		if(gain > 62)
		{
			Log.e(logTag,"RX VGA Gain must be within 0-62!");
			return false;
		}
		
		if(this.sendUsbRequest(UsbConstants.USB_DIR_IN, HACKRF_VENDOR_REQUEST_SET_VGA_GAIN, 
				0, gain, retVal) != 1)
		{
			Log.e(logTag, "USB Transfer failed!");
			throw(new HackrfUsbException("USB Transfer failed!"));
		}
		
		if (retVal[0] == 0)
		{
			Log.e(logTag,"HackRF returned with an error!");
			return false;
		}
		
		return true;
	}
	
	/**
	 * Sets the TX VGA Gain of the HackRF.
	 * 
	 * Note: This function interacts with the USB Hardware and
	 * should not be called from a GUI Thread!
	 * 
	 * @param	gain	TX VGA Gain (0-62)
	 * @return 	true on success
	 * @throws 	HackrfUsbException
	 */
	public boolean setTxVGAGain(int gain) throws HackrfUsbException
	{
		byte[] retVal = new byte[1];
		
		if(gain > 47)
		{
			Log.e(logTag,"TX VGA Gain must be within 0-47!");
			return false;
		}
		
		if(this.sendUsbRequest(UsbConstants.USB_DIR_IN, HACKRF_VENDOR_REQUEST_SET_TXVGA_GAIN, 
				0, gain, retVal) != 1)
		{
			Log.e(logTag, "USB Transfer failed!");
			throw(new HackrfUsbException("USB Transfer failed!"));
		}
		
		if (retVal[0] == 0)
		{
			Log.e(logTag,"HackRF returned with an error!");
			return false;
		}
		
		return true;
	}
	
	/**
	 * Sets the RX LNA Gain of the HackRF.
	 * 
	 * Note: This function interacts with the USB Hardware and
	 * should not be called from a GUI Thread!
	 * 
	 * @param	gain	RX LNA Gain (0-62)
	 * @return 	true on success
	 * @throws 	HackrfUsbException
	 */
	public boolean setRxLNAGain(int gain) throws HackrfUsbException
	{
		byte[] retVal = new byte[1];
		
		if(gain > 40)
		{
			Log.e(logTag,"RX LNA Gain must be within 0-40!");
			return false;
		}
		
		if(this.sendUsbRequest(UsbConstants.USB_DIR_IN, HACKRF_VENDOR_REQUEST_SET_LNA_GAIN, 
				0, gain, retVal) != 1)
		{
			Log.e(logTag, "USB Transfer failed!");
			throw(new HackrfUsbException("USB Transfer failed!"));
		}
		
		if (retVal[0] == 0)
		{
			Log.e(logTag,"HackRF returned with an error!");
			return false;
		}
		
		return true;
	}
	
	/**
	 * Sets the Frequency of the HackRF.
	 * 
	 * Note: This function interacts with the USB Hardware and
	 * should not be called from a GUI Thread!
	 * 
	 * @param	frequency	Frequency in Hz
	 * @return 	true on success
	 * @throws 	HackrfUsbException
	 */
	public boolean setFrequency(long frequency) throws HackrfUsbException
	{
		ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
		int mhz = (int) (frequency/1000000l);
		int hz = (int) (frequency%1000000l);
		
		Log.d(logTag, "Tune HackRF to " + mhz + "." + hz + "MHz...");
		
		try {
			byteOut.write(this.intToByteArray(mhz));
			byteOut.write(this.intToByteArray(hz));
		} catch (IOException e) {
			Log.e(logTag,"Error while converting arguments to byte buffer.");
			return false;
		}
		
		if(this.sendUsbRequest(UsbConstants.USB_DIR_OUT, HACKRF_VENDOR_REQUEST_SET_FREQ, 
				0, 0, byteOut.toByteArray()) != 8)
		{
			Log.e(logTag, "USB Transfer failed!");
			throw(new HackrfUsbException("USB Transfer failed!"));
		}
		
		return true;
	}
	
	/**
	 * Enables or Disables the Amplifier of the HackRF.
	 * 
	 * Note: This function interacts with the USB Hardware and
	 * should not be called from a GUI Thread!
	 * 
	 * @param	enable		true for enable or false for disable
	 * @return 	true on success
	 * @throws 	HackrfUsbException
	 */
	public boolean setAmp(boolean enable) throws HackrfUsbException
	{
		if(this.sendUsbRequest(UsbConstants.USB_DIR_OUT, HACKRF_VENDOR_REQUEST_AMP_ENABLE, 
				(enable ? 1 : 0) , 0, null) != 0)
		{
			Log.e(logTag, "USB Transfer failed!");
			throw(new HackrfUsbException("USB Transfer failed!"));
		}
		
		return true;
	}

	/**
	 * Enables or Disables the Antenna Port Power of the HackRF.
	 * 
	 * Note: This function interacts with the USB Hardware and
	 * should not be called from a GUI Thread!
	 * 
	 * @param	enable		true for enable or false for disable
	 * @return 	true on success
	 * @throws 	HackrfUsbException
	 */
	public boolean setAntennaPower(boolean enable) throws HackrfUsbException
	{
		if(this.sendUsbRequest(UsbConstants.USB_DIR_OUT, HACKRF_VENDOR_REQUEST_ANTENNA_ENABLE, 
				(enable ? 1 : 0) , 0, null) != 0)
		{
			Log.e(logTag, "USB Transfer failed!");
			throw(new HackrfUsbException("USB Transfer failed!"));
		}
		
		return true;
	}
	
	
	
	
	
	
}
