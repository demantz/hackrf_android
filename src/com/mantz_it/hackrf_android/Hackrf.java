package com.mantz_it.hackrf_android;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.util.Log;

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
	private static final String logTag = "private static final int HACKRF";
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
	 * Note: The application must have reclaimed permissions to
	 * access the USB Device BEFOR calling this constructor.
	 * 
	 * @param usbManager	Instance of the USB Manager (System Service)
	 * @param usbDevice		Instance of an USB Device representing the HackRF
	 * @throws HackrfUsbException
	 */
	public Hackrf (UsbManager usbManager, UsbDevice usbDevice) throws HackrfUsbException
	{
		// Check that usbDevice is indeed a HackRF 
		// (Vendor ID: 7504 [0x1d50];  Product ID: 24713 [0x6089] / 24651 [0x604b])
		if ( usbDevice.getVendorId() != 7504 || 
				(usbDevice.getProductId() != 24713 && usbDevice.getProductId() != 24651))
		{
			Log.w(logTag, "USB Device is not a HackRF USB Device!");
			throw(new HackrfUsbException("USB Device is not a HackRF USB Device!"));
		}
		
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
		int len = 0;
		
		if( !this.usbConnection.claimInterface(this.usbDevice.getInterface(0), true))
		{
			Log.e(logTag, "Couldn't claim HackRF USB Interface!");
			throw(new HackrfUsbException("Couldn't claim HackRF USB Interface!"));
		}
		
		len = this.usbConnection.controlTransfer(
				UsbConstants.USB_DIR_IN | UsbConstants.USB_TYPE_VENDOR,	// Request Type
				HACKRF_VENDOR_REQUEST_BOARD_ID_READ,					// Request
				0,			// Value (unused)
				0,			// Index (unused)
				buffer,		// Buffer
				1, 			// Length
				0			// Timeout
			);
		
		if (len < 1)
		{
			Log.e(logTag, "USB Transfer failed!");
			throw(new HackrfUsbException("USB Transfer failed!"));
		}
		
		return buffer[0];
	}
	
	

}
