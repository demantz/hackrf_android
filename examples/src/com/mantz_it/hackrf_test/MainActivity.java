package com.mantz_it.hackrf_test;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Formatter;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.text.method.ScrollingMovementMethod;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.mantz_it.hackrf_android.Hackrf;
import com.mantz_it.hackrf_android.HackrfCallbackInterface;
import com.mantz_it.hackrf_android.HackrfUsbException;

public class MainActivity extends Activity implements Runnable, HackrfCallbackInterface{
	
	private Button bt_openHackRF = null;
	private Button bt_info = null;
	private Button bt_rx = null;
	private Button bt_tx = null;
	private Button bt_stop = null;
	private EditText et_sampRate = null;
	private EditText et_freq = null;
	private TextView tv_output = null;
	private Hackrf hackrf = null;
	
	private int sampRate = 0;
	private long frequency = 0;
	
	private Handler handler = null;
	
	// This variable is used to select what the Thread should do if it is started
	private int task = -1;
	private static final int PRINT_INFO = 0;
	private static final int RECEIVE = 1;
	private static final int TRANSMIT = 2;	
	
	private boolean stopRequested = false;	// Used to stop receive/transmit thread
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		handler = new Handler();
		
		bt_info 		= ((Button) this.findViewById(R.id.bt_info));
   		bt_rx 			= ((Button) this.findViewById(R.id.bt_rx));
   		bt_tx			= ((Button) this.findViewById(R.id.bt_tx));
   		bt_stop			= ((Button) this.findViewById(R.id.bt_stop));
   		bt_openHackRF	= ((Button) this.findViewById(R.id.bt_openHackRF));
		et_sampRate 	= (EditText) findViewById(R.id.et_sampRate);
		et_freq 		= (EditText) findViewById(R.id.et_freq);
		tv_output 		= (TextView) findViewById(R.id.tv_output);
		tv_output.setMovementMethod(new ScrollingMovementMethod());
		this.toggleButtonsEnabledIfHackrfReady(false);
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
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
	
	public void printOnScreen(final String msg)
	{
		handler.post(new Runnable() {
           public void run() {
                    tv_output.append(msg);
                }
            });
	}
	
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

	@Override
	public void onHackrfReady(Hackrf hackrf) {
		tv_output.append("HackRF is ready!\n");
		
		this.hackrf = hackrf;
		this.toggleButtonsEnabledIfHackrfReady(true);
		this.toggleButtonsEnabledIfTransceiving(false);
	}

	@Override
	public void onHackrfError(String message) {
		tv_output.append("Error while opening HackRF: " + message +"\n");
		this.toggleButtonsEnabledIfHackrfReady(false);
	}
	
	public void info(View view)
	{
		if (hackrf != null)
		{
			this.task = PRINT_INFO;
			new Thread(this).start();
		}
	}
	
	public void rx(View view)
	{
		if (hackrf != null)
		{
			sampRate = Integer.valueOf(et_sampRate.getText().toString());
			frequency = (long) Integer.valueOf(et_freq.getText().toString());
			this.task = RECEIVE;
			this.stopRequested = false;
			new Thread(this).start();
			toggleButtonsEnabledIfTransceiving(true);
		}
	}
	
	public void tx(View view)
	{
		if (hackrf != null)
		{
			sampRate = Integer.valueOf(et_sampRate.getText().toString());
			frequency = (long) Integer.valueOf(et_freq.getText().toString());
			this.task = TRANSMIT;
			this.stopRequested = false;
			new Thread(this).start();
			toggleButtonsEnabledIfTransceiving(true);
		}
	}
	
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
	
	public void receiveThread()
	{
		String filename = "hackrf_receive.io";
		int basebandFilterWidth = Hackrf.computeBasebandFilterBandwidth((int)(0.75*sampRate));
		int vgaGain = 20;
		int lnaGain = 8;
		boolean amp = false;
		boolean antennaPower = false;
		int i = 0;
		long lastTransceiverPacketCounter = 0;
		long lastTransceivingTime = 0;
		
		try {
			// First set all settings manually:
			// We also could do that by just pass the args to startRX(...)
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
			
			File file = new File(Environment.getExternalStorageDirectory(), filename);
			printOnScreen("Saving samples to " + file.getAbsolutePath() + "\n");
			
			BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(file));
			
			// Start Receiving:
			printOnScreen("Start Receiving... \n");
			ArrayBlockingQueue<byte[]> queue = hackrf.startRX();
			
			while(!this.stopRequested)
			{
				i++;
				
				byte[] receivedBytes = queue.poll(1000, TimeUnit.MILLISECONDS);
				
				if(receivedBytes != null)
				{
					bufferedOutputStream.write(receivedBytes);
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
					printOnScreen( String.format("Current Transfer Rate: %4.1f MB/s)\n",(bytes/time)/1000000.0));
					lastTransceiverPacketCounter = hackrf.getTransceiverPacketCounter();
					lastTransceivingTime = hackrf.getTransceivingTime();
				}
			}
			bufferedOutputStream.close();
			printOnScreen( String.format("Finished! (Average Transfer Rate: %4.1f MB/s)\n", 
											hackrf.getAverageTransceiveRate()/1000000.0));
			printOnScreen(String.format("Recorded %d packets (each %d Bytes) in %5.3f Seconds.\n\n", 
											hackrf.getTransceiverPacketCounter(), hackrf.getPacketSize(), 
											hackrf.getTransceivingTime()/1000.0));
		} catch (HackrfUsbException e) {
			printOnScreen("error (USB)!\n");
			toggleButtonsEnabledIfHackrfReady(false);
		} catch (IOException e) {
			printOnScreen("error (File IO)!\n");
		} catch (InterruptedException e) {
			printOnScreen("error (Queue)!\n");
		}
	}
	
	public void transmitThread()
	{
		int basebandFilterWidth = Hackrf.computeBasebandFilterBandwidth((int)(0.75*sampRate));
		int vgaGain = 0;
		boolean amp = false;
		boolean antennaPower = false;
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
		} catch (HackrfUsbException e) {
			tv_output.append("error!\n");
			toggleButtonsEnabledIfHackrfReady(false);
		}
		printOnScreen("TX is not implemented yet!\n");
	}

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
	
	
}
