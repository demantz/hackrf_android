package com.mantz_it.hackrf_android;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbRequest;
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
 * Copyright (C) 2014 Dennis Mantz
 * based on code of libhackrf [https://github.com/mossmann/hackrf/tree/master/host/libhackrf]: 
 *     Copyright (c) 2012, Jared Boone <jared@sharebrained.com>
 *     Copyright (c) 2013, Benjamin Vernoux <titanmkd@gmail.com>
 *     Copyright (c) 2013, Michael Ossmann <mike@ossmann.com>
 *     All rights reserved.
 *     Redistribution and use in source and binary forms, with or without modification, 
 *     are permitted provided that the following conditions are met:
 *     - Redistributions of source code must retain the above copyright notice, this list 
 *       of conditions and the following disclaimer.
 *     - Redistributions in binary form must reproduce the above copyright notice, this 
 *       list of conditions and the following disclaimer in the documentation and/or other 
 *       materials provided with the distribution.
 *     - Neither the name of Great Scott Gadgets nor the names of its contributors may be 
 *       used to endorse or promote products derived from this software without specific 
 *       prior written permission.
 * License: http://www.gnu.org/licenses/gpl.html GPL version 2 or higher
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation; either
 * version 2 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 */
public class Hackrf implements Runnable{
	
	// Attributes to hold the USB related objects:
	private UsbManager usbManager = null;
	private UsbDevice usbDevice = null;
	private UsbInterface usbInterface = null;
	private UsbDeviceConnection usbConnection = null;
	private UsbEndpoint usbEndpointIN = null;
	private UsbEndpoint usbEndpointOUT = null;
	
	private int transceiverMode = HACKRF_TRANSCEIVER_MODE_OFF;	// current mode of the HackRF
	private Thread usbThread = null;							// hold the transceiver Thread if running
	private ArrayBlockingQueue<byte[]> queue = null;			// queue that buffers samples to pass them
																// between hackrf_android and the application
	private ArrayBlockingQueue<byte[]> bufferPool = null;		// queue that holds old buffers which can be
																// reused while receiving or transmitting samples
	
	// startTime (in ms since 1970) and packetCounter for statistics:
	private long transceiveStartTime = 0;
	private long transceivePacketCounter = 0;
	
	// Transceiver Modes:
	public static final int HACKRF_TRANSCEIVER_MODE_OFF 		= 0;
	public static final int HACKRF_TRANSCEIVER_MODE_RECEIVE 	= 1;
	public static final int HACKRF_TRANSCEIVER_MODE_TRANSMIT 	= 2;
	
	// USB Vendor Requests (from hackrf.c)
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
	
	// RF Filter Paths (from hackrf.c)
	public static final int RF_PATH_FILTER_BYPASS 		= 0;
	public static final int RF_PATH_FILTER_LOW_PASS 	= 1;
	public static final int RF_PATH_FILTER_HIGH_PASS 	= 2;
	
	// Some Constants:
	private static final String logTag 					= "hackrf_android";
	private static final String HACKRF_USB_PERMISSION 	= "com.mantz_it.hackrf_android.USB_PERMISSION";
	private static final int numUsbRequests 			= 4; 		// Number of parallel UsbRequests
	private static final int packetSize 				= 1024*16;	// Buffer Size of each UsbRequest
	
	/**
	 * Initializing the Hackrf Instance with a USB Device. This will try to request
	 * the permissions to open the USB device and then create an instance of
	 * the Hackrf class and pass it back via the callbackInterface
	 * 
	 * @param context				Application context. Used to retrieve System Services (USB)
	 * @param callbackInterface		This interface declares two methods that are called if the
	 * 								device is ready or if there was an error
	 * @param queueSize				Size of the receive/transmit queue in bytes
	 * @return false if no Hackrf could be found
	 */
	public static boolean initHackrf(Context context, final HackrfCallbackInterface callbackInterface, final int queueSize)
	{
		final UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
		UsbDevice hackrfUsbDvice = null;
		
		if(usbManager == null) {
			Log.e(logTag, "initHackrf: Couldn't get an instance of UsbManager!");
			return false;
		}
		
		// Get a list of connected devices
		HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
		
		if(deviceList == null) {
			Log.e(logTag, "initHackrf: Couldn't read the USB device list!");
			return false;
		}
		
		Log.i(logTag, "initHackrf: Found " + deviceList.size() + " USB devices.");
		
		// Iterate over the list. Use the first Device that matches a HackRF
		Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
		while(deviceIterator.hasNext()){
			UsbDevice device = deviceIterator.next();
			
			Log.d(logTag,"initHackrf: deviceList: vendor="+device.getVendorId() + " product="+device.getProductId());
		    
			// HackRF One (Vendor ID: 7504 [0x1d50]; Product ID: 24713 [0x6089] )
			if ( device.getVendorId() == 7504 && device.getProductId() == 24713 )
			{
				Log.i(logTag,"initHackrf: Found HackRF One at " + device.getDeviceName());
				hackrfUsbDvice = device;
			}

			// rad1o (Vendor ID: 7504 [0x1d50]; Product ID: 52245 [0xCC15] )
			if ( device.getVendorId() == 7504 && device.getProductId() == 52245 )
			{
				Log.i(logTag,"initHackrf: Found rad1o at " + device.getDeviceName());
				hackrfUsbDvice = device;
			}
		    
			// HackRF Jawbreaker (Vendor ID: 7504 [0x1d50]; Product ID: 24651 [0x604b])
			if ( device.getVendorId() == 7504 && device.getProductId() == 24651 )
			{
				Log.i(logTag,"initHackrf: Found HackRF Jawbreaker at " + device.getDeviceName());
				hackrfUsbDvice = device;
			}
		}
		
		// Check if we found a device:
		if (hackrfUsbDvice == null)
		{
			Log.e(logTag,"initHackrf: No HackRF Device found.");
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
	                	Log.d(logTag,"initHackrf: Permission granted for device " + device.getDeviceName());
	                	try {
							Hackrf hackrf = new Hackrf(usbManager, device, queueSize);
							Toast.makeText(context, "HackRF at " + device.getDeviceName() + " is ready!",Toast.LENGTH_LONG).show();
							callbackInterface.onHackrfReady(hackrf);
						} catch (HackrfUsbException e) {
							Log.e(logTag, "initHackrf: Couldn't open device " + device.getDeviceName());
							Toast.makeText(context, "Couldn't open HackRF device",Toast.LENGTH_LONG).show();
		                    callbackInterface.onHackrfError("Couldn't open device " + device.getDeviceName());
						}
	                } 
	                else 
	                {
	                    Log.e(logTag, "initHackrf: Permission denied for device " + device.getDeviceName());
	                    Toast.makeText(context, "Permission denied to open HackRF device",Toast.LENGTH_LONG).show();
	                    callbackInterface.onHackrfError("Permission denied for device " + device.getDeviceName());
	                }
		        }
		        
		        // unregister the Broadcast Receiver:
		        context.unregisterReceiver(this);
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
	 * @param queueSize		Size of the receive/transmit queue in bytes
	 * @throws HackrfUsbException
	 */
	private Hackrf (UsbManager usbManager, UsbDevice usbDevice, int queueSize) throws HackrfUsbException
	{
		// Initialize the class attributes:
		this.usbManager = usbManager;
		this.usbDevice = usbDevice;
		
		// For detailed trouble shooting: Read out information of the device:
		Log.i(logTag,"constructor: create Hackrf instance from " + usbDevice.getDeviceName()
				+ ". Vendor ID: " + usbDevice.getVendorId() + " Product ID: " + usbDevice.getProductId());
		Log.i(logTag,"constructor: device protocol: " + usbDevice.getDeviceProtocol());
		Log.i(logTag,"constructor: device class: " + usbDevice.getDeviceClass()
				+ " subclass: " + usbDevice.getDeviceSubclass());
		Log.i(logTag,"constructor: interface count: " + usbDevice.getInterfaceCount());
		
		try {
			// Extract interface from the device:
			this.usbInterface = usbDevice.getInterface(0);
			
			// For detailed trouble shooting: Read out interface information of the device:
			Log.i(logTag,"constructor: [interface 0] interface protocol: " + usbInterface.getInterfaceProtocol()
					+ " subclass: " + usbInterface.getInterfaceSubclass());
			Log.i(logTag,"constructor: [interface 0] interface class: " + usbInterface.getInterfaceClass());
			Log.i(logTag,"constructor: [interface 0] endpoint count: " + usbInterface.getEndpointCount());
			
			// Extract the endpoints from the device:
			this.usbEndpointIN = usbInterface.getEndpoint(0);
			this.usbEndpointOUT = usbInterface.getEndpoint(1);
			
			// For detailed trouble shooting: Read out endpoint information of the interface:
			Log.i(logTag,"constructor:     [endpoint 0 (IN)] address: " + usbEndpointIN.getAddress()
					+ " attributes: " + usbEndpointIN.getAttributes() + " direction: " + usbEndpointIN.getDirection()
					+ " max_packet_size: " + usbEndpointIN.getMaxPacketSize());
			Log.i(logTag,"constructor:     [endpoint 1 (OUT)] address: " + usbEndpointOUT.getAddress()
					+ " attributes: " + usbEndpointOUT.getAttributes() + " direction: " + usbEndpointOUT.getDirection()
					+ " max_packet_size: " + usbEndpointOUT.getMaxPacketSize());
			
			// Open the device:
			this.usbConnection = usbManager.openDevice(usbDevice);
			
			if(this.usbConnection == null) {
				Log.e(logTag, "constructor: Couldn't open HackRF USB Device: openDevice() returned null!");
				throw(new HackrfUsbException("Couldn't open HackRF USB Device! (device is gone)"));
			}
		} catch (Exception e) {
			Log.e(logTag, "constructor: Couldn't open HackRF USB Device: " + e.getMessage());
			throw(new HackrfUsbException("Error: Couldn't open HackRF USB Device!"));
		}
		
		// Create the queue that is used to transport samples to the application.
		// Each queue element is a byte array of size usbEndpointIN.getMaxPacketSize() (512 Bytes)
		this.queue = new ArrayBlockingQueue<byte[]>(queueSize/getPacketSize());
		
		// Create another queue that will be used to collect old buffers for reusing them.
		// This will speed up things a lot!
		this.bufferPool = new ArrayBlockingQueue<byte[]>(queueSize/getPacketSize());
	}
	
	/**
	 * This returns the size of the packets that are used in receiving /
	 * transmitting samples. Note that the size is measured in bytes and
	 * a complex sample always consists of 2 bytes!
	 * 
	 * @return Packet size in Bytes
	 */
	public int getPacketSize()
	{
		//return this.usbEndpointIN.getMaxPacketSize(); <= gives 512 which is way too small
		return packetSize;
	}
	
	/**
	 * Get a buffer (byte array with size getPacketSize()) that can be used to hold samples
	 * for transmitting. Use this function to allocate your buffers which you will pass into the
	 * queue while transmitting. It will reuse old buffers and save a lot of expensive memory
	 * allocation and garbage collection time. If no old buffers are existing, it will allocate
	 * a new one.
	 * 
	 * @return allocated buffer of size getPacketSize()
	 */
	public byte[] getBufferFromBufferPool()
	{
		byte[] buffer = this.bufferPool.poll();
		
		// Check if we got a buffer:
		if(buffer == null)
			buffer = new byte[getPacketSize()];
		
		return buffer;
	}
	
	/**
	 * Returns a buffer that isn't used by the application any more to the buffer pool of this hackrf instance.
	 * The buffer must be a byte array with size getPacketSize() (the one you got from the queue while receiving).
	 * This will reuse old buffers while receiving and save a lot of expensive memory
	 * allocation and garbage collection time.
	 * 
	 * @param buffer	a byte array of size getPacketSize() that is not used by the application any more.
	 */
	public void returnBufferToBufferPool(byte[] buffer)
	{
		if (buffer.length == getPacketSize())
		{
			// Throw it into the pool (don't care if it's working or not):
			this.bufferPool.offer(buffer);
		}
		else
			Log.w(logTag, "returnBuffer: Got a buffer with wrong size. Ignore it!");
	}
	
	/**
	 * This returns the number of packets (of size getPacketSize()) received/transmitted since start.
	 * 
	 * @return Number of packets (of size getPacketSize()) received/transmitted since start
	 */
	public long getTransceiverPacketCounter()
	{
		return this.transceivePacketCounter;
	}
	
	/**
	 * This returns the time in milliseconds since receiving/transmitting was started.
	 * 
	 * @return time in milliseconds since receiving/transmitting was started.
	 */
	public long getTransceivingTime()
	{
		if(this.transceiveStartTime == 0)
			return 0;
		return System.currentTimeMillis() - this.transceiveStartTime;
	}

	/**
	 * Returns the average rx/tx transfer rate in byte/seconds.
	 * 
	 * @return average transfer rate in byte/seconds
	 */
	public long getAverageTransceiveRate()
	{
		long transTime = this.getTransceivingTime()/1000;	// Transfer Time in seconds
		if(transTime == 0)
			return 0;
		return this.getTransceiverPacketCounter() * this.getPacketSize() / transTime;
	}
	
	/**
	 * Returns the current mode of receiving / transmitting
	 * 
	 * @return HACKRF_TRANSCEIVER_MODE_OFF, *_RECEIVE, *_TRANSMIT
	 */
	public int getTransceiverMode() {
		return transceiverMode;
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
	 * Converts a byte array into a long integer using little endian byteorder.
	 * 
	 * @param b			byte array (length 8)
	 * @param offset	offset pointing to the first byte in the bytearray that should be used
	 * @return 			long integer
	 */
	private long byteArrayToLong(byte[] b, int offset)
	{
		return b[offset+0] & 0xFF | (b[offset+1] & 0xFF) << 8 | (b[offset+2] & 0xFF) << 16 | 
				(b[offset+3] & 0xFF) << 24 | (b[offset+4] & 0xFF) << 32 | (b[offset+5] & 0xFF) << 40 | 
				(b[offset+6] & 0xFF) << 48 | (b[offset+7] & 0xFF) << 56;
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
	 * Converts a long integer into a byte array using little endian byteorder.
	 * 
	 * @param i		long integer
	 * @return 		byte array (length 8)
	 */
	private byte[] longToByteArray(long i)
	{
		byte[] b = new byte[8];
		b[0] = (byte) (i & 0xff);
		b[1] = (byte) ((i >> 8) & 0xff);
		b[2] = (byte) ((i >> 16) & 0xff);
		b[3] = (byte) ((i >> 24) & 0xff);
		b[4] = (byte) ((i >> 32) & 0xff);
		b[5] = (byte) ((i >> 40) & 0xff);
		b[6] = (byte) ((i >> 48) & 0xff);
		b[7] = (byte) ((i >> 56) & 0xff);
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
			Log.e(logTag, "getBoardID: USB Transfer failed!");
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
			case 3: return "rad1o";
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
			Log.e(logTag, "getVersionString: USB Transfer failed!");
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
			Log.e(logTag, "getPartIdAndSerialNo: USB Transfer failed!");
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
			Log.e(logTag,"setSampleRate: Error while converting arguments to byte buffer.");
			return false;
		}
		
		if(this.sendUsbRequest(UsbConstants.USB_DIR_OUT, HACKRF_VENDOR_REQUEST_SAMPLE_RATE_SET, 
				0, 0, byteOut.toByteArray()) != 8)
		{
			Log.e(logTag, "setSampleRate: USB Transfer failed!");
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
			Log.e(logTag, "setBasebandFilterBandwidth: USB Transfer failed!");
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
	 * @param	gain	RX VGA Gain (0-62 in steps of 2)
	 * @return 	true on success
	 * @throws 	HackrfUsbException
	 */
	public boolean setRxVGAGain(int gain) throws HackrfUsbException
	{
		byte[] retVal = new byte[1];
		
		if(gain > 62)
		{
			Log.e(logTag,"setRxVGAGain: RX VGA Gain must be within 0-62!");
			return false;
		}
		
		// Must be in steps of two!
		if(gain % 2 != 0)
			gain = gain - (gain%2);
		
		if(this.sendUsbRequest(UsbConstants.USB_DIR_IN, HACKRF_VENDOR_REQUEST_SET_VGA_GAIN, 
				0, gain, retVal) != 1)
		{
			Log.e(logTag, "setRxVGAGain: USB Transfer failed!");
			throw(new HackrfUsbException("USB Transfer failed!"));
		}
		
		if (retVal[0] == 0)
		{
			Log.e(logTag,"setRxVGAGain: HackRF returned with an error!");
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
			Log.e(logTag,"setTxVGAGain: TX VGA Gain must be within 0-47!");
			return false;
		}
		
		if(this.sendUsbRequest(UsbConstants.USB_DIR_IN, HACKRF_VENDOR_REQUEST_SET_TXVGA_GAIN, 
				0, gain, retVal) != 1)
		{
			Log.e(logTag, "setTxVGAGain: USB Transfer failed!");
			throw(new HackrfUsbException("USB Transfer failed!"));
		}
		
		if (retVal[0] == 0)
		{
			Log.e(logTag,"setTxVGAGain: HackRF returned with an error!");
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
	 * @param	gain	RX LNA Gain (0-40 in steps of 8)
	 * @return 	true on success
	 * @throws 	HackrfUsbException
	 */
	public boolean setRxLNAGain(int gain) throws HackrfUsbException
	{
		byte[] retVal = new byte[1];
		
		if(gain > 40)
		{
			Log.e(logTag,"setRxLNAGain: RX LNA Gain must be within 0-40!");
			return false;
		}
		
		// Must be in steps of 8!
		if(gain % 8 != 0)
			gain = gain - (gain%8);
		
		if(this.sendUsbRequest(UsbConstants.USB_DIR_IN, HACKRF_VENDOR_REQUEST_SET_LNA_GAIN, 
				0, gain, retVal) != 1)
		{
			Log.e(logTag, "setRxLNAGain: USB Transfer failed!");
			throw(new HackrfUsbException("USB Transfer failed!"));
		}
		
		if (retVal[0] == 0)
		{
			Log.e(logTag,"setRxLNAGain: HackRF returned with an error!");
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
			Log.e(logTag,"setFrequency: Error while converting arguments to byte buffer.");
			return false;
		}
		
		if(this.sendUsbRequest(UsbConstants.USB_DIR_OUT, HACKRF_VENDOR_REQUEST_SET_FREQ, 
				0, 0, byteOut.toByteArray()) != 8)
		{
			Log.e(logTag, "setFrequency: USB Transfer failed!");
			throw(new HackrfUsbException("USB Transfer failed!"));
		}
		
		return true;
	}
	
	/**
	 * Sets the explicit IF and LO frequency of the HackRF.
	 * 
	 * Note: This function interacts with the USB Hardware and
	 * should not be called from a GUI Thread!
	 * 
	 * @param	ifFrequency		Intermediate Frequency in Hz. Must be in [2150000000; 2750000000]
	 * @param	loFrequency		Local Oscillator Frequency in Hz. Must be in [84375000; 5400000000]
	 * @param	path			RF_PATH_FILTER_BYPASS, *_HIGH_PASS or *_LOW_PASS
	 * @return 	true on success
	 * @throws 	HackrfUsbException
	 */
	public boolean setFrequencyExplicit(long ifFrequency, long loFrequency, int rfPath) throws HackrfUsbException
	{
		ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
		
		// check range of IF Frequency:
		if (ifFrequency < 2150000000l || ifFrequency > 2750000000l) {
			Log.e(logTag,"setFrequencyExplicit: IF Frequency must be in [2150000000; 2750000000]!");
			return false;
		}

		if ((rfPath != RF_PATH_FILTER_BYPASS) && (loFrequency < 84375000l || loFrequency > 5400000000l)) {
			Log.e(logTag,"setFrequencyExplicit: LO Frequency must be in [84375000; 5400000000]!");
			return false;
		}
		
		// Check if path is in the valid range:
		if (rfPath < 0 || rfPath > 2)
		{
			Log.e(logTag,"setFrequencyExplicit: Invalid value for rf_path!");
			return false;
		}
			
		Log.d(logTag, "Tune HackRF to IF:" + ifFrequency + " Hz; LO:" + loFrequency + " Hz...");
		
		try {
			byteOut.write(this.longToByteArray(ifFrequency));
			byteOut.write(this.longToByteArray(loFrequency));
			byteOut.write(rfPath);
		} catch (IOException e) {
			Log.e(logTag,"setFrequencyExplicit: Error while converting arguments to byte buffer.");
			return false;
		}
		
		if(this.sendUsbRequest(UsbConstants.USB_DIR_OUT, HACKRF_VENDOR_REQUEST_SET_FREQ_EXPLICIT, 
				0, 0, byteOut.toByteArray()) != 17)
		{
			Log.e(logTag, "setFrequencyExplicit: USB Transfer failed!");
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
			Log.e(logTag, "setAmp: USB Transfer failed!");
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
		// The Jawbreaker doesn't support this command!
		if(this.getBoardID() == 1) {		// == Jawbreaker
			Log.w(logTag, "setAntennaPower: Antenna Power is not supported for HackRF Jawbreaker. Ignore.");
			return false;
		}
		// The rad1o doesn't support this command!
		if(this.getBoardID() == 3) {		// == rad1o
			Log.w(logTag, "setAntennaPower: Antenna Power is not supported for rad1o. Ignore.");
			return false;
		}
		if(this.sendUsbRequest(UsbConstants.USB_DIR_OUT, HACKRF_VENDOR_REQUEST_ANTENNA_ENABLE, 
				(enable ? 1 : 0) , 0, null) != 0)
		{
			Log.e(logTag, "setAntennaPower: USB Transfer failed!");
			throw(new HackrfUsbException("USB Transfer failed!"));
		}
		
		return true;
	}
	
	/**
	 * Sets the Transceiver Mode of the HackRF (OFF,RX,TX)
	 * 
	 * Note: This function interacts with the USB Hardware and
	 * should not be called from a GUI Thread!
	 * 
	 * @param	mode		HACKRF_TRANSCEIVER_MODE_OFF, *_RECEIVE or *_TRANSMIT
	 * @return 	true on success
	 * @throws 	HackrfUsbException
	 */
	public boolean setTransceiverMode(int mode) throws HackrfUsbException
	{
		if (mode < 0 || mode > 2)
		{
			Log.e(logTag,"Invalid Transceiver Mode: " + mode);
			return false;
		}
		
		this.transceiverMode = mode;
		
		if(this.sendUsbRequest(UsbConstants.USB_DIR_OUT, HACKRF_VENDOR_REQUEST_SET_TRANSCEIVER_MODE, 
				mode , 0, null) != 0)
		{
			Log.e(logTag, "setTransceiverMode: USB Transfer failed!");
			throw(new HackrfUsbException("USB Transfer failed!"));
		}
		
		return true;
	}
	
	/**
	 * Starts receiving.
	 * 
	 * @return 	An ArrayBlockingQueue that will fill with the samples as they arrive. 
	 * 			Each queue element is a block of samples (byte[]) of size getPacketSize().
	 * @throws	HackrfUsbException
	 */
	public ArrayBlockingQueue<byte[]> startRX() throws HackrfUsbException
	{
		// Flush the queue
	    this.queue.clear();
	    
		// Signal the HackRF Device to start receiving:
		this.setTransceiverMode(HACKRF_TRANSCEIVER_MODE_RECEIVE);
		
		// Start the Thread to queue the received samples:
		this.usbThread = new Thread(this);  
		this.usbThread.start();
		
		// Reset the packet counter and start time for statistics:
		this.transceiveStartTime = System.currentTimeMillis();
		this.transceivePacketCounter = 0;
		
		return this.queue;
	}
	
	/**
	 * Starts transmitting.
	 * 
	 * @return 	An ArrayBlockingQueue from which the hackrf will read the samples to transmit. 
	 * 			Each queue element must be a block of samples (byte[]) of size getPacketSize().
	 * @throws	HackrfUsbException
	 */
	public ArrayBlockingQueue<byte[]> startTX() throws HackrfUsbException
	{
		// Flush the queue
	    this.queue.clear();
	    
		// Signal the HackRF Device to start transmitting:
		this.setTransceiverMode(HACKRF_TRANSCEIVER_MODE_TRANSMIT);
		
		// Start the Thread to queue the received samples:
		this.usbThread = new Thread(this);  
		this.usbThread.start();
		
		// Reset the packet counter and start time for statistics:
		this.transceiveStartTime = System.currentTimeMillis();
		this.transceivePacketCounter = 0;
		
		return this.queue;
	}
	
	/**
	 * Stops receiving or transmitting.
	 * 
	 * @throws	HackrfUsbException
	 */
	public void stop() throws HackrfUsbException
	{
		// Signal the HackRF Device to start receiving:
		this.setTransceiverMode(HACKRF_TRANSCEIVER_MODE_OFF);
	}
	
	/**
	 * This method will be executed in a separate Thread after the HackRF starts receiving
	 * Samples. It will return as soon as the transceiverMode changes or an error occurs.
	 */
	private void receiveLoop()
	{
		UsbRequest[] usbRequests = new UsbRequest[numUsbRequests];
		ByteBuffer buffer;
		
		try
		{
			// Create, initialize and queue all usb requests:
			for(int i = 0; i < numUsbRequests; i++)
			{
				// Get a ByteBuffer for the request from the buffer pool:
				buffer = ByteBuffer.wrap(this.getBufferFromBufferPool());
				
			    // Initialize the USB Request:
				usbRequests[i] = new UsbRequest();
				usbRequests[i].initialize(usbConnection, usbEndpointIN);
				usbRequests[i].setClientData(buffer);
			    
			    // Queue the request
			    if(	usbRequests[i].queue(buffer, getPacketSize()) == false)
			    {
		            Log.e(logTag,"receiveLoop: Couldn't queue USB Request.");
		            this.stop();
		            break;
			    }
			}
			
			// Run loop until transceiver mode changes...
		    while(this.transceiverMode == HACKRF_TRANSCEIVER_MODE_RECEIVE)
		    {
			    // Wait for a request to return. This will block until one of the requests is ready.
		    	UsbRequest request = usbConnection.requestWait(); 
			    
			    if(request == null)
			    {
			    	Log.e(logTag,"receiveLoop: Didn't receive USB Request.");
			    	break;
			    }
			    
			 // Make sure we got an UsbRequest for the IN endpoint!
		    	if(request.getEndpoint() != usbEndpointIN)
		    		continue;
			    
			    // Extract the buffer
			    buffer = (ByteBuffer) request.getClientData();
			    
			    // Increment the packetCounter (for statistics)
			    this.transceivePacketCounter++;
			    
			    // Put the received samples into the queue, so that they can be read by the application
			    if(!this.queue.offer(buffer.array()))
			    {
			    	// We hit the timeout.
			    	Log.e(logTag,"receiveLoop: Queue is full. Stop receiving!");
			    	break;	
			    }
			    
			    // Get a fresh ByteBuffer for the request from the buffer pool:
				buffer = ByteBuffer.wrap(this.getBufferFromBufferPool());
				request.setClientData(buffer);
			    
			    // Queue the request again...
			    if(request.queue(buffer, getPacketSize()) == false){
	                Log.e(logTag,"receiveLoop: Couldn't queue USB Request.");
	                break;
			    }
		    }
		} catch (HackrfUsbException e) {
			Log.e(logTag,"receiveLoop: USB Error!");
		}
		
		// Receiving is done. Cancel and close all usb requests:
	    for(UsbRequest request: usbRequests)
	    {
	    	if(request != null) {
		    	request.cancel();
		    	//request.close();    <-- This will cause the VM to crash with a SIGABRT when the next transceive starts?!?
	    	}
	    }
		
		// If the transceiverMode is still on RECEIVE, we stop Receiving:
		if(this.transceiverMode == HACKRF_TRANSCEIVER_MODE_RECEIVE)
		{
			try {
				this.stop();
			} catch (HackrfUsbException e) {
				Log.e(logTag,"receiveLoop: Error while stopping RX!");
			}
		}
	}
	
	/**
	 * This method will be executed in a separate Thread after the HackRF starts transmitting
	 * Samples. It will return as soon as the transceiverMode changes or an error occurs.
	 */
	private void transmitLoop()
	{
		UsbRequest[] usbRequests = new UsbRequest[numUsbRequests];
		ByteBuffer buffer;
		byte[] packet;
		
		try
		{
			// Create, initialize and queue all usb requests:
			for(int i = 0; i < numUsbRequests; i++)
			{
				// Get a packet from the queue:
			    packet = (byte[]) queue.poll(1000, TimeUnit.MILLISECONDS);
			    if(packet == null || packet.length != getPacketSize())
			    {
			    	Log.e(logTag,"transmitLoop: Queue empty or wrong packet format. Abort.");
			    	this.stop();
			    	break;
			    }
			    
			    // Wrap the packet in a ByteBuffer object:
			    buffer = ByteBuffer.wrap(packet);
				
			    // Initialize the USB Request:
				usbRequests[i] = new UsbRequest();
				usbRequests[i].initialize(usbConnection, usbEndpointOUT);
				usbRequests[i].setClientData(buffer);
			    
			    // Queue the request
			    if(	usbRequests[i].queue(buffer, getPacketSize()) == false)
			    {
		            Log.e(logTag,"receiveLoop: Couldn't queue USB Request.");
		            this.stop();
		            break;
			    }
			}
			
			// Run loop until transceiver mode changes...
		    while(this.transceiverMode == HACKRF_TRANSCEIVER_MODE_TRANSMIT)
		    {
			    // Wait for a request to return. This will block until one of the requests is ready.
		    	UsbRequest request = usbConnection.requestWait();
			    
			    if(request == null)
			    {
			    	Log.e(logTag,"transmitLoop: Didn't receive USB Request.");
			    	break;
			    }
			    
			    // Make sure we got an UsbRequest for the OUT endpoint!
		    	if(request.getEndpoint() != usbEndpointOUT)
		    		continue;

			    // Increment the packetCounter (for statistics)
			    this.transceivePacketCounter++;
			    
			    // Extract the buffer and return it to the buffer pool:
			    buffer = (ByteBuffer) request.getClientData();
			    this.returnBufferToBufferPool(buffer.array());
			    
			    // Get the next packet from the queue:
			    packet = (byte[]) queue.poll(1000, TimeUnit.MILLISECONDS);
			    if(packet == null || packet.length != getPacketSize())
			    {
			    	Log.e(logTag,"transmitLoop: Queue empty or wrong packet format. Stop transmitting.");
			    	break;
			    }
			    
			    // Wrap the packet in a ByteBuffer object:
			    buffer = ByteBuffer.wrap(packet);
			    request.setClientData(buffer);
			    
			    // Queue the request again...
			    if(request.queue(buffer, getPacketSize()) == false){
	                Log.e(logTag,"transmitLoop: Couldn't queue USB Request.");
	                break;
			    }
		    }
		} catch (HackrfUsbException e) {
			Log.e(logTag,"transmitLoop: USB Error!");
		} catch (InterruptedException e) {
			Log.e(logTag,"transmitLoop: Interrup while waiting on queue!");
		}
		
		// Transmitting is done. Cancel and close all usb requests:
	    for(UsbRequest request: usbRequests)
	    {
	    	if(request != null) {
		    	request.cancel();
		    	//request.close();   <-- This will cause the VM to crash with a SIGABRT when the next transceive starts?!?
	    	}
	    }
		
		// If the transceiverMode is still on TRANSMIT, we stop Transmitting:
		if(this.transceiverMode == HACKRF_TRANSCEIVER_MODE_TRANSMIT)
		{
			try {
				this.stop();
			} catch (HackrfUsbException e) {
				Log.e(logTag,"transmitLoop: Error while stopping TX!");
			}
		}
	}

	/**
	 * This method will run when a new Thread was created. It simply calls
	 * receiveLoop() or transmitLoop() according to the transceiveMode.
	 */
	@Override
	public void run() {
		switch(this.transceiverMode)
		{
			case HACKRF_TRANSCEIVER_MODE_RECEIVE: 	receiveLoop();
													break;
			case HACKRF_TRANSCEIVER_MODE_TRANSMIT:  transmitLoop();
													break;
			default:
		}
	}
	
	
}
