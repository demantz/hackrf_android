package com.mantz_it.hackrf_test;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;

import com.mantz_it.hackrf_android.Hackrf;
import com.mantz_it.hackrf_android.HackrfCallbackInterface;
import com.mantz_it.hackrf_android.HackrfUsbException;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * <h1>Hackrf_Test</h1>
 * 
 * Module:      MainActivity.java
 * Description: This Android application shows the usage of the hackrf_android
 * 				library. It offers a simple user interface with buttons to open
 * 				the device, print the device info on the screen and start / stop
 * 				receiving / transmitting.
 * 
 * @author Dennis Mantz
 * 
 * Copyright (C) 2014 Dennis Mantz
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
public class MainActivity extends AppCompatActivity implements Runnable, HackrfCallbackInterface{

	private static final int PERMISSION_REQUEST_WRITE_FILES = 100;
	private static final int PERMISSION_REQUEST_READ_FILES = 101;

	// References to the GUI elements:
	private Button bt_openHackRF = null;
	private Button bt_info = null;
	private Button bt_rx = null;
	private Button bt_tx = null;
	private Button bt_stop = null;
	private EditText et_sampRate = null;
	private EditText et_freq = null;
	private EditText et_filename = null;
	private SeekBar sb_vgaGain = null;
	private SeekBar sb_lnaGain = null;
	private CheckBox cb_amp = null;
	private CheckBox cb_antenna = null;
	private TextView tv_output = null;
	
	// Reference to the hackrf instance:
	private Hackrf hackrf = null;
	
	private int sampRate = 0;
	private long frequency = 0;
	private String filename = null;
	private int vgaGain = 0;
	private int lnaGain = 0;
	private boolean amp = false;
	private boolean antennaPower = false;
	
	// The handler is used to access GUI elements from other threads then the GUI thread
	private Handler handler = null;
	
	// This variable is used to select what the thread should do if it is started
	private int task = -1;
	private static final int PRINT_INFO = 0;
	private static final int RECEIVE = 1;
	private static final int TRANSMIT = 2;	
	
	private boolean stopRequested = false;	// Used to stop receive/transmit thread
	
	// Set this to true to rewind sample file every time the end is reached:
	private boolean repeatTransmitting = false; 
	
	// Folder name for capture files:
	private static final String foldername = "Test_HackRF";
	
	// logcat process:
	Process logcat;
	File logfile;
	
	// This method is called on application startup by the Android System:
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		// Start logging:
		try{
			logfile = new File(Environment.getExternalStorageDirectory() + "/" + foldername, "log.txt");
			logfile.getParentFile().mkdir();	// Create folder
		    logcat = Runtime.getRuntime().exec("logcat -f " + logfile.getAbsolutePath());
		    Log.i("MainActivity", "onCreate: log path: " + logfile.getAbsolutePath());
		} catch (Exception e) {
			Log.e("MainActivity", "onCreate: Failed to start logging!");
		}
		
		// Create a Handler instance to use in other threads:
		handler = new Handler();
		
		// Initialize the GUI references:
		bt_info 		= ((Button) this.findViewById(R.id.bt_info));
   		bt_rx 			= ((Button) this.findViewById(R.id.bt_rx));
   		bt_tx			= ((Button) this.findViewById(R.id.bt_tx));
   		bt_stop			= ((Button) this.findViewById(R.id.bt_stop));
   		bt_openHackRF	= ((Button) this.findViewById(R.id.bt_openHackRF));
		et_sampRate 	= (EditText) this.findViewById(R.id.et_sampRate);
		et_freq 		= (EditText) this.findViewById(R.id.et_freq);
		et_filename 	= (EditText) this.findViewById(R.id.et_filename);
		sb_vgaGain 		= (SeekBar) this.findViewById(R.id.sb_vgaGain);
		sb_lnaGain 		= (SeekBar) this.findViewById(R.id.sb_lnaGain);
		cb_amp 			= (CheckBox) this.findViewById(R.id.cb_amp);
		cb_antenna 		= (CheckBox) this.findViewById(R.id.cb_antenna);
		tv_output 		= (TextView) findViewById(R.id.tv_output);
		tv_output.setMovementMethod(new ScrollingMovementMethod());	// make it scroll!
		this.toggleButtonsEnabledIfHackrfReady(false);	// Disable all buttons except for 'Open HackRF'
		
		// Print Hello
		String version = "";
		try {
			version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
		} catch (NameNotFoundException e) {}
		this.tv_output.setText("Test_HackRF (version " + version + ") by Dennis Mantz\n");

		// Check for the WRITE_EXTERNAL_STORAGE permission (needed for logging):
		if (ContextCompat.checkSelfPermission(this, "android.permission.WRITE_EXTERNAL_STORAGE")
				!= PackageManager.PERMISSION_GRANTED) {
			printOnScreen("Warning: Logfile cannot be written (no Storage Permission)!\n");
		}
	}

	@Override
	protected void onDestroy() {
		if(logcat != null)
			logcat.destroy();
		super.onDestroy();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_help) {
			this.tv_output.setText(Html.fromHtml(getResources().getString(R.string.helpText)));
			return true;
		}
		if (id == R.id.action_showLog) {
			Uri uri = FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".provider", logfile);
			Intent intent = new Intent(Intent.ACTION_VIEW);
			intent.setDataAndType(uri, "text/plain");
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
	        this.startActivity(intent);
	        return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
		switch (requestCode) {
			case PERMISSION_REQUEST_READ_FILES: {
				// If request is cancelled, the result arrays are empty.
				if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
					printOnScreen("Permission granted to read files. Start rx!\n");
					tx(null);
				}
				break;
			}
			case PERMISSION_REQUEST_WRITE_FILES: {
				// If request is cancelled, the result arrays are empty.
				if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
					printOnScreen("Permission granted to write files. Start rx!\n");
					rx(null);
				}
				break;
			}
		}
	}


	/**
	 * Will append the message to the tv_output TextView. Can be called from
	 * outside the GUI thread because it uses the handler reference to access
	 * the TextView.
	 * 
	 * @param msg	Message to print on the screen
	 */
	public void printOnScreen(final String msg)
	{
		handler.post(new Runnable() {
           public void run() {
                    tv_output.append(msg);
                }
            });
	}
	
	/**
	 * Will set all buttons to disabled except for the 'open hackrf' button. If
	 * false is given, the behavior toggles. Can be called from
	 * outside the GUI thread because it uses the handler reference to access
	 * the TextView.
	 * 
	 * @param enable	if true: 'Open HackRf' will be enabled, all others disabled
	 * 					if false: The other way round
	 */
	public void toggleButtonsEnabledIfHackrfReady(final boolean enable)
	{
		handler.post(new Runnable() {
	           public void run() {
	        	    bt_info.setEnabled(enable);
		       		bt_rx.setEnabled(enable);
		       		bt_tx.setEnabled(enable);
		       		bt_stop.setEnabled(enable);
		       		bt_openHackRF.setEnabled(!enable);
	                }
	            });
	}
	
	/**
	 * Will set 'Info', 'RX' and 'TX' to disabled and 'stop' to enabled (while 
	 * receiving/transmitting is running). If false is given, the behavior toggles. 
	 * Can be called from outside the GUI thread because it uses the handler 
	 * reference to access the TextView.
	 * 
	 * @param enable	if true: 'Stop' will be enabled, all others ('Info', 'RX' and 'TX') disabled
	 * 					if false: The other way round
	 */
	public void toggleButtonsEnabledIfTransceiving(final boolean enable)
	{
		handler.post(new Runnable() {
	           public void run() {
	        	    bt_info.setEnabled(!enable);
		       		bt_rx.setEnabled(!enable);
		       		bt_tx.setEnabled(!enable);
		       		bt_stop.setEnabled(enable);
	                }
	            });
	}
	
	/**
	 * Will read the values from the GUI elements into the corresponding variables
	 */
	public void readGuiElements()
	{
		sampRate = Integer.valueOf(et_sampRate.getText().toString());
		frequency = Long.valueOf(et_freq.getText().toString());
		filename = et_filename.getText().toString();
		vgaGain = sb_vgaGain.getProgress();
		lnaGain = sb_lnaGain.getProgress();
		amp = cb_amp.isChecked();
		antennaPower = cb_antenna.isChecked();
	}
	
	/**
	 * Is called if the user presses the 'Open HackRF' Button. Will initialize the
	 * HackRF device.
	 * 
	 * @param view		Reference to the calling View (in this case bt_openHackRF)
	 */
	public void openHackrf(View view)
	{
		int queueSize = 15000000 * 2;	// max. 15 Msps with 2 byte each ==> will buffer for 1 second

		// Initialize the HackRF (i.e. open the USB device, which requires the user to give permissions)
		if (!Hackrf.initHackrf(view.getContext(), this, queueSize))
		{
			tv_output.append("No HackRF could be found!\n");
		}
		// initHackrf() is asynchronous. this.onHackrfReady() will be called as soon as the device is ready.
	}
	
	/**
	 * Is called if the user presses the 'Info' Button. Will start a Thread that
	 * retrieves the BoardID, Version String, PartID and Serial number from the device
	 * and then print the information on the screen.
	 * 
	 * @param view		Reference to the calling View (in this case bt_info)
	 */
	public void info(View view)
	{
		if (hackrf != null)
		{
			this.task = PRINT_INFO;
			new Thread(this).start();
		}
	}
	
	/**
	 * Is called if the user presses the 'RX' Button. Will start a Thread that
	 * sets the HackRF into receiving mode and then save the received samples
	 * to a file. Will run forever until user presses the 'Stop' button.
	 * 
	 * @param view		Reference to the calling View (in this case bt_rx)
	 */
	public void rx(View view)
	{
		// Check for the WRITE_EXTERNAL_STORAGE permission:
		if (ContextCompat.checkSelfPermission(this, "android.permission.WRITE_EXTERNAL_STORAGE")
				!= PackageManager.PERMISSION_GRANTED) {
			printOnScreen("Need to ask for permission to write files...\n");
			ActivityCompat.requestPermissions(this, new String[]{"android.permission.WRITE_EXTERNAL_STORAGE"},
					PERMISSION_REQUEST_WRITE_FILES);
			return; // wait for the permission response (handled in onRequestPermissionResult())
		}

		if (hackrf != null)
		{
			this.readGuiElements();
			this.task = RECEIVE;
			this.stopRequested = false;
			new Thread(this).start();
			toggleButtonsEnabledIfTransceiving(true);
		}
	}
	
	/**
	 * Is called if the user presses the 'TX' Button. Will start a Thread that
	 * sets the HackRF into transmitting mode and then read the samples
	 * from a file and pass them to the HackRF. Will run forever until user 
	 * presses the 'Stop' button.
	 * 
	 * @param view		Reference to the calling View (in this case bt_tx)
	 */
	public void tx(View view)
	{
		// Check for the READ_EXTERNAL_STORAGE permission:
		if (ContextCompat.checkSelfPermission(this, "android.permission.READ_EXTERNAL_STORAGE")
				!= PackageManager.PERMISSION_GRANTED) {
			printOnScreen("Need to ask for permission to read files...\n");
			ActivityCompat.requestPermissions(this, new String[]{"android.permission.READ_EXTERNAL_STORAGE"},
					PERMISSION_REQUEST_WRITE_FILES);
			return; // wait for the permission response (handled in onRequestPermissionResult())
		}

		if (hackrf != null)
		{
			this.readGuiElements();
			this.task = TRANSMIT;
			this.stopRequested = false;
			new Thread(this).start();
			toggleButtonsEnabledIfTransceiving(true);
		}
	}
	
	/**
	 * Is called if the user presses the 'Stop' Button. Will set the stopRequested
	 * attribute to true, which will cause any running thread to shut down. It will
	 * then set the transceiver mode of the HackRF to OFF.
	 * 
	 * @param view		Reference to the calling View (in this case bt_stop)
	 */
	public void stop(View view)
	{
		this.stopRequested = true;
		toggleButtonsEnabledIfTransceiving(false);
		
		if(hackrf != null)
		{
			try {
				hackrf.stop();
			} catch (HackrfUsbException e) {
				printOnScreen("Error (USB)!\n");
				toggleButtonsEnabledIfHackrfReady(false);
			}
		}
	}

	/**
	 * Is called by the hackrf_android library after the device is ready.
	 * Was triggered by the initHackrf() call in openHackrf().
	 * See also HackrfCallbackInterface.java
	 * 
	 * @param hackrf	Instance of the Hackrf class that represents the open device
	 */
	@Override
	public void onHackrfReady(Hackrf hackrf) {
		tv_output.append("HackRF is ready!\n");
		
		this.hackrf = hackrf;
		this.toggleButtonsEnabledIfHackrfReady(true);
		this.toggleButtonsEnabledIfTransceiving(false);
	}

	/**
	 * Is called by the hackrf_android library after a error occurred while opening
	 * the device.
	 * Was triggered by the initHackrf() call in openHackrf().
	 * See also HackrfCallbackInterface.java
	 * 
	 * @param message	Short human readable error message
	 */
	@Override
	public void onHackrfError(String message) {
		tv_output.append("Error while opening HackRF: " + message +"\n");
		this.toggleButtonsEnabledIfHackrfReady(false);
	}
	
	/**
	 * Is called (in a separate Thread) after 'new Thread(this).start()' is 
	 * executed in info(), rx() and tx().
	 * Will run either infoThread(), receiveThread() or transmitThread() depending
	 * on how the task attribute is set.
	 */
	@Override
	public void run() {
		switch(this.task)
		{
			case PRINT_INFO:	infoThread(); break;
			case RECEIVE:		receiveThread(); break;
			case TRANSMIT:		transmitThread(); break;
			default:
		}
		
	}
	
	/**
	 * Will run in a separate thread created in info(). Retrieves the BoardID, 
	 * Version String, PartID and Serial number from the device
	 * and then print the information on the screen.
	 */
	public void infoThread()
	{
		// Read out boardID, version, partID and serialNo:
		try 
		{
			int boardID = hackrf.getBoardID();
			printOnScreen("Board ID:   " + boardID + " (" + Hackrf.convertBoardIdToString(boardID) + ")\n" );
			printOnScreen("Version:    " + hackrf.getVersionString() +"\n");
			int[] tmp = hackrf.getPartIdAndSerialNo();
			printOnScreen("Part ID:    0x" + Integer.toHexString(tmp[0]) + 
									 " 0x" + Integer.toHexString(tmp[1]) +"\n");
			printOnScreen("Serial No:  0x" + Integer.toHexString(tmp[2]) + 
									 " 0x" + Integer.toHexString(tmp[3]) +
									 " 0x" + Integer.toHexString(tmp[4]) +
									 " 0x" + Integer.toHexString(tmp[5]) +"\n\n");
		} catch (HackrfUsbException e) {
			printOnScreen("Error while reading Board Information!\n");
			this.toggleButtonsEnabledIfHackrfReady(false);
		}
	}
	
	/**
	 * Will run in a separate thread created in rx(). Sets the HackRF into receiving 
	 * mode and then save the received samples to a file. Will run forever until user 
	 * presses the 'Stop' button.
	 */
	public void receiveThread()
	{
		int basebandFilterWidth = Hackrf.computeBasebandFilterBandwidth((int)(0.75*sampRate));
		int i = 0;
		long lastTransceiverPacketCounter = 0;
		long lastTransceivingTime = 0;
		
		// vgaGain and lnaGain are still values from 0-100; scale them to the right range:
		vgaGain = (vgaGain * 62) / 100;
		lnaGain = (lnaGain * 40) / 100;
		
		try {
			// First set all parameters:
			printOnScreen("Setting Sample Rate to " + sampRate + " Sps ... ");
			hackrf.setSampleRate(sampRate, 1);
			printOnScreen("ok.\nSetting Frequency to " + frequency + " Hz ... ");
			hackrf.setFrequency(frequency);
			printOnScreen("ok.\nSetting Baseband Filter Bandwidth to " + basebandFilterWidth + " Hz ... ");
			hackrf.setBasebandFilterBandwidth(basebandFilterWidth);
			printOnScreen("ok.\nSetting RX VGA Gain to " + vgaGain + " ... ");
			hackrf.setRxVGAGain(vgaGain);
			printOnScreen("ok.\nSetting LNA Gain to " + lnaGain + " ... ");
			hackrf.setRxLNAGain(lnaGain);
			printOnScreen("ok.\nSetting Amplifier to " + amp + " ... ");
			hackrf.setAmp(amp);
			printOnScreen("ok.\nSetting Antenna Power to " + antennaPower + " ... ");
			hackrf.setAntennaPower(antennaPower);
			printOnScreen("ok.\n\n");
			
			// Check if external memory is available:
			if(!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED))
		    {
				printOnScreen("External Media Storage not available.\n\n");
		    	return;
		    }
			
			// Create a file ...
			// If no filename was given, write to /dev/null
			File file;
			if(filename.equals(""))
				file = new File("/dev/", "null");
			else
				file = new File(Environment.getExternalStorageDirectory() + "/" + foldername, filename);
			file.getParentFile().mkdir();	// Create folder if it does not exist
			printOnScreen("Saving samples to " + file.getAbsolutePath() + "\n");
			
			// ... and open it with a buffered output stream
			BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(file));
			
			// Start Receiving:
			printOnScreen("Start Receiving... \n");
			ArrayBlockingQueue<byte[]> queue = hackrf.startRX();
			
			// Run until user hits the 'Stop' button
			while(!this.stopRequested)
			{
				i++;	// only for statistics
				
				// Grab one packet from the top of the queue. Will block if queue is
				// empty and timeout after one second if the queue stays empty.
				byte[] receivedBytes = queue.poll(1000, TimeUnit.MILLISECONDS);
				
				/*  HERE should be the DSP portion of the app. The receivedBytes
				 *  variable now contains a byte array of size hackrf.getPacketSize().
				 *  This is currently set to 16KB, but may change in the future.
				 *  The bytes are interleaved, 8-bit, signed IQ samples (in-phase
				 *  component first, followed by the quadrature component):
				 *  
				 *  [--------- first sample ----------]   [-------- second sample --------]
				 *         I                  Q                  I                Q ...
				 *  receivedBytes[0]   receivedBytes[1]   receivedBytes[2]       ...
				 *  
				 *  Note: Make sure you read from the queue fast enough, because if it runs
				 *  full, the hackrf_android library will abort receiving and go back to
				 *  OFF mode.
				 */
				
				// We just write the whole packet into the file:
				if(receivedBytes != null)
				{
					// On my Nexus 7 this is to slow for high sample rates. Nexus 5 works, though.
					bufferedOutputStream.write(receivedBytes);
					
					// IMPORTANT: After we used the receivedBytes buffer and don't need it any more,
					// we should return it to the buffer pool of the hackrf! This will save a lot of
					// allocation time and the garbage collector won't go off every second.
					hackrf.returnBufferToBufferPool(receivedBytes);
				}
				else
				{
					printOnScreen("Error: Queue is empty! (This happens most often because the queue ran full"
							+ " which causes the Hackrf class to stop receiving. Writing the samples to a file"
							+ " seems to be working to slowly... try a lower sample rate.)\n");
					break;
				}
				
				// print statistics
				if(i%1000 == 0)
				{
					long bytes = (hackrf.getTransceiverPacketCounter() - lastTransceiverPacketCounter) * hackrf.getPacketSize();
					double time = (hackrf.getTransceivingTime() - lastTransceivingTime)/1000.0;
					printOnScreen( String.format("Current Transfer Rate: %4.1f MB/s\n",(bytes/time)/1000000.0));
					lastTransceiverPacketCounter = hackrf.getTransceiverPacketCounter();
					lastTransceivingTime = hackrf.getTransceivingTime();
				}
			}
			
			// After loop ended: close the file and print more statistics:
			bufferedOutputStream.close();
			printOnScreen( String.format("Finished! (Average Transfer Rate: %4.1f MB/s\n", 
											hackrf.getAverageTransceiveRate()/1000000.0));
			printOnScreen(String.format("Recorded %d packets (each %d Bytes) in %5.3f Seconds.\n\n", 
											hackrf.getTransceiverPacketCounter(), hackrf.getPacketSize(), 
											hackrf.getTransceivingTime()/1000.0));
			toggleButtonsEnabledIfTransceiving(false);
		} catch (HackrfUsbException e) {
			// This exception is thrown if a USB communication error occurres (e.g. you unplug / reset
			// the device while receiving)
			printOnScreen("error (USB)!\n");
			toggleButtonsEnabledIfHackrfReady(false);
		} catch (IOException e) {
			// This exception is thrown if the file could not be opened or write fails.
			printOnScreen("error (File IO: " + e.getMessage() + ")!\n");
			printOnScreen("Note: After granting storage permission, sometimes it is necessary to restart the app!\n");
			toggleButtonsEnabledIfTransceiving(false);
		} catch (InterruptedException e) {
			// This exception is thrown if queue.poll() is interrupted
			printOnScreen("error (Queue)!\n");
			toggleButtonsEnabledIfTransceiving(false);
		}
	}
	
	/**
	 * Will run in a separate thread created in tx(). Sets the HackRF into transmitting 
	 * mode and then read the samples from a file and pass them to the HackRF. Will run 
	 * forever until user presses the 'Stop' button.
	 */
	public void transmitThread()
	{
		int basebandFilterWidth = Hackrf.computeBasebandFilterBandwidth((int)(0.75*sampRate));
		int i = 0;
		long lastTransceiverPacketCounter = 0;
		long lastTransceivingTime = 0;
		
		// vgaGain is still a value from 0-100; scale it to the right range:
		vgaGain = (vgaGain * 47) / 100;
		
		try {
			printOnScreen("Setting Sample Rate to " + sampRate + " Sps ... ");
			hackrf.setSampleRate(sampRate, 1);
			printOnScreen("ok.\nSetting Frequency to " + frequency + " Hz ... ");
			hackrf.setFrequency(frequency);
			printOnScreen("ok.\nSetting Baseband Filter Bandwidth to " + basebandFilterWidth + " Hz ... ");
			hackrf.setBasebandFilterBandwidth(basebandFilterWidth);
			printOnScreen("ok.\nSetting TX VGA Gain to " + vgaGain + " ... ");
			hackrf.setTxVGAGain(vgaGain);
			printOnScreen("ok.\nSetting Amplifier to " + amp + " ... ");
			hackrf.setAmp(amp);
			printOnScreen("ok.\nSetting Antenna Power to " + antennaPower + " ... ");
			hackrf.setAntennaPower(antennaPower);
			printOnScreen("ok.\n\n");
			
			// Check if external memory is available:
			if(!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED))
		    {
				printOnScreen("External Media Storage not available.\n\n");
		    	return;
		    }
			
			// Open a file ...
			File file = new File(Environment.getExternalStorageDirectory() + "/" + foldername, filename);
			printOnScreen("Reading samples from " + file.getAbsolutePath() + "\n");
			if(!file.exists())
			{
				printOnScreen("Error: File does not exist!");
				return;
			}
			
			// ... and open it with a buffered input stream
			BufferedInputStream bufferedInputStream = new BufferedInputStream(new FileInputStream(file));
			
			// Start Transmitting:
			printOnScreen("Start Transmitting... \n");
			ArrayBlockingQueue<byte[]> queue = hackrf.startTX();
			
			// Run until user hits the 'Stop' button
			while(!this.stopRequested)
			{
				i++;	// only for statistics
				
				// IMPORTANT: We don't allocate the buffer for a packet ourself. We use the getBufferFromBufferPool()
				// method of the hackrf instance! This might give us an already allocated buffer from the buffer pool
				// and save a lot of time and memory! You will get a java.lang.OutOfMemoryError if you don't do that
				// trust me ;) If no buffer is available in the pool, this method will automatically allocate a buffer
				// of the correct size!
				byte[] packet = hackrf.getBufferFromBufferPool();
				
				// Read one packet from the file:
				if(bufferedInputStream.read(packet, 0, packet.length) != packet.length)
				{
					// If repeatTransmitting is set, we rewind. Otherwise we stop:
					if(this.repeatTransmitting)
					{
						printOnScreen("Reached End of File. Start over.\n");
						bufferedInputStream.close();
						new BufferedInputStream(new FileInputStream(file));
					}
					else
					{
						printOnScreen("Reached End of File. Stop.\n");
						break;
					}
				}
				
				// Put the packet into the queue:
				if(queue.offer(packet, 1000, TimeUnit.MILLISECONDS) == false)
				{
					printOnScreen("Error: Queue is full. Stop transmitting.\n");
					break;
				}
				
				// print statistics
				if(i%1000 == 0)
				{
					long bytes = (hackrf.getTransceiverPacketCounter() - lastTransceiverPacketCounter) * hackrf.getPacketSize();
					double time = (hackrf.getTransceivingTime() - lastTransceivingTime)/1000.0;
					printOnScreen( String.format("Current Transfer Rate: %4.1f MB/s\n",(bytes/time)/1000000.0));
					lastTransceiverPacketCounter = hackrf.getTransceiverPacketCounter();
					lastTransceivingTime = hackrf.getTransceivingTime();
				}
			}
			
			// After loop ended: close the file and print more statistics:
			bufferedInputStream.close();
			printOnScreen( String.format("Finished! (Average Transfer Rate: %4.1f MB/s)\n", 
											hackrf.getAverageTransceiveRate()/1000000.0));
			printOnScreen(String.format("Transmitted %d packets (each %d Bytes) in %5.3f Seconds.\n\n", 
											hackrf.getTransceiverPacketCounter(), hackrf.getPacketSize(), 
											hackrf.getTransceivingTime()/1000.0));
			toggleButtonsEnabledIfTransceiving(false);
		} catch (HackrfUsbException e) {
			printOnScreen("Error (USB)!\n");
			toggleButtonsEnabledIfHackrfReady(false);
		} catch (IOException e) {
			printOnScreen("error (File IO: " + e.getMessage() + ")!\n");
			printOnScreen("Note: After granting storage permission, sometimes it is necessary to restart the app!\n");
			toggleButtonsEnabledIfTransceiving(false);
		} catch (InterruptedException e) {
			printOnScreen("Error (Queue interrupted)!\n");
			toggleButtonsEnabledIfTransceiving(false);
		}
	}
}
