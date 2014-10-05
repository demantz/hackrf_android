package com.mantz_it.hackrf_test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.mantz_it.hackrf_android.Hackrf;
import com.mantz_it.hackrf_android.HackrfCallbackInterface;
import com.mantz_it.hackrf_android.HackrfUsbException;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class MainActivity extends Activity implements HackrfCallbackInterface{

	private EditText et_sampRate = null;
	private EditText et_freq = null;
	private TextView tv_output = null;
	private Hackrf hackrf = null;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		et_sampRate = (EditText) findViewById(R.id.et_sampRate);
		et_freq = (EditText) findViewById(R.id.et_freq);
		tv_output = (TextView) findViewById(R.id.tv_output);
		tv_output.setMovementMethod(new ScrollingMovementMethod());
		this.toggleButtonsEnabled(false);
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
	
	public void openHackrf(View view)
	{
		int queueSize = 2000000 * 2;	// 2 Msps with 2 byte each ==> will buffer for 1 second
		
		if (!Hackrf.initHackrf(view.getContext(), this, queueSize))
		{
			tv_output.append("No HackRF could be found!\n");
		}
	}
	
	public void info(View view)
	{
		if (hackrf != null)
		{
			try 
			{
				int boardID = hackrf.getBoardID();
				tv_output.append("Board ID:   " + boardID + " (" + Hackrf.convertBoardIdToString(boardID) + ")\n" );
				tv_output.append("Version:    " + hackrf.getVersionString() +"\n");
				int[] tmp = hackrf.getPartIdAndSerialNo();
				tv_output.append("Part ID:    0x" + Integer.toHexString(tmp[0]) + 
											" 0x" + Integer.toHexString(tmp[1]) +"\n");
				tv_output.append("Serial No:  0x" + Integer.toHexString(tmp[2]) + 
											" 0x" + Integer.toHexString(tmp[3]) +
											" 0x" + Integer.toHexString(tmp[4]) +
											" 0x" + Integer.toHexString(tmp[5]) +"\n\n");
			} catch (HackrfUsbException e) {
				tv_output.append("Error while reading Board Information!\n");
				this.toggleButtonsEnabled(false);
			}
		}
	}
	
	public void rx(View view)
	{
		if (hackrf != null)
		{
			int sampRate = Integer.valueOf(et_sampRate.getText().toString());
			long freq = (long) Integer.valueOf(et_freq.getText().toString());
			int basebandFilterWidth = Hackrf.computeBasebandFilterBandwidth((int)(0.75*sampRate));
			int vgaGain = 20;
			int lnaGain = 8;
			boolean amp = false;
			boolean antennaPower = false;
			try {
				// First set all settings manually:
				// We also could do that by just pass the args to startRX()
				tv_output.append("Setting Sample Rate to " + sampRate + " Sps ... ");
				hackrf.setSampleRate(sampRate, 1);
				tv_output.append("ok.\nSetting Frequency to " + freq + " Hz ... ");
				hackrf.setFrequency(freq);
				tv_output.append("ok.\nSetting Baseband Filter Bandwidth to " + basebandFilterWidth + " Hz ... ");
				hackrf.setBasebandFilterBandwidth(basebandFilterWidth);
				tv_output.append("ok.\nSetting RX VGA Gain to " + vgaGain + " ... ");
				hackrf.setRxVGAGain(vgaGain);
				tv_output.append("ok.\nSetting LNA Gain to " + lnaGain + " ... ");
				hackrf.setRxLNAGain(lnaGain);
				tv_output.append("ok.\nSetting Amplifier to " + amp + " ... ");
				hackrf.setAmp(amp);
				tv_output.append("ok.\nSetting Antenna Power to " + antennaPower + " ... ");
				hackrf.setAntennaPower(antennaPower);
				tv_output.append("ok.\n\n");
				
				// Open output file
				if(!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED))
			    {
					tv_output.append("External Media Storage not available.\n\n");
			    	return;
			    }
				String filename = "hackrf_receive.io";
				File file = new File(Environment.getExternalStorageDirectory(), filename);
				tv_output.append("Save samples to " + file.getAbsolutePath() + "\n");
				FileOutputStream outputStream = new FileOutputStream(file);
				
				// Start Receiving:
				tv_output.append("Start Receiving... ");
				ArrayBlockingQueue<byte[]> queue = hackrf.startRX();
				
				for(int i = 0; i < 100000; i++)
				{
					outputStream.write(queue.poll(1000, TimeUnit.MILLISECONDS));
				}
				outputStream.close();
				tv_output.append("DONE! (Average Transfer Rate: " + hackrf.getAverageTransceiveRate() + " B/s)\n\n");
				System.out.println("TIME: " + hackrf.getTransceivingTime());
				System.out.println("COUNT: " + hackrf.getTransceiverPacketCounter());
			} catch (HackrfUsbException e) {
				tv_output.append("error (USB)!\n");
			} catch (IOException e) {
				tv_output.append("error (File)!\n");
			} catch (InterruptedException e) {
				tv_output.append("error (Queue)!\n");
			}
		}
	}
	
	public void tx(View view)
	{
		if (hackrf != null)
		{
			int sampRate = Integer.valueOf(et_sampRate.getText().toString());
			long freq = (long) Integer.valueOf(et_freq.getText().toString());
			int basebandFilterWidth = Hackrf.computeBasebandFilterBandwidth((int)(0.75*sampRate));
			int vgaGain = 0;
			boolean amp = false;
			boolean antennaPower = false;
			try {
				tv_output.append("Setting Sample Rate to " + sampRate + " Sps ... ");
				hackrf.setSampleRate(sampRate, 1);
				tv_output.append("ok.\nSetting Frequency to " + freq + " Hz ... ");
				hackrf.setFrequency(freq);
				tv_output.append("ok.\nSetting Baseband Filter Bandwidth to " + basebandFilterWidth + " Hz ... ");
				hackrf.setBasebandFilterBandwidth(basebandFilterWidth);
				tv_output.append("ok.\nSetting TX VGA Gain to " + vgaGain + " ... ");
				hackrf.setTxVGAGain(vgaGain);
				tv_output.append("ok.\nSetting Amplifier to " + amp + " ... ");
				hackrf.setAmp(amp);
				tv_output.append("ok.\nSetting Antenna Power to " + antennaPower + " ... ");
				hackrf.setAntennaPower(antennaPower);
				tv_output.append("ok.\n\n");
			} catch (HackrfUsbException e) {
				tv_output.append("error!\n");
			}
			tv_output.append("TX is not implemented yet!\n");
		}
	}
	
	public void toggleButtonsEnabled(boolean enable)
	{
		((Button) this.findViewById(R.id.bt_info)).setEnabled(enable);
		((Button) this.findViewById(R.id.bt_rx)).setEnabled(enable);
		((Button) this.findViewById(R.id.bt_tx)).setEnabled(enable);
	}

	@Override
	public void onHackrfReady(Hackrf hackrf) {
		tv_output.append("HackRF is ready!\n");
		
		this.hackrf = hackrf;
		this.toggleButtonsEnabled(true);
	}

	@Override
	public void onHackrfError(String message) {
		tv_output.append("Error while opening HackRF: " + message +"\n");
		this.toggleButtonsEnabled(false);
	}
}
