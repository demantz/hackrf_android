package com.mantz_it.hackrf_test;

import com.mantz_it.hackrf_android.Hackrf;
import com.mantz_it.hackrf_android.HackrfCallbackInterface;
import com.mantz_it.hackrf_android.HackrfUsbException;

import android.app.Activity;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends Activity implements HackrfCallbackInterface{

	private TextView tv_output = null;
	private Hackrf hackrf = null;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
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
		if (!Hackrf.initHackrf(view.getContext(), this))
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
			tv_output.append("Not implemented yet!\n");
		}
	}
	
	public void tx(View view)
	{
		if (hackrf != null)
		{
			tv_output.append("Not implemented yet!\n");
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
