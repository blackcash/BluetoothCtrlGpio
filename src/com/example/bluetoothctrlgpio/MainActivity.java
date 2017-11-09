package com.example.bluetoothctrlgpio;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.UUID;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

public class MainActivity extends Activity implements OnCheckedChangeListener {

	private static BluetoothAdapter mBluetoothAdapter = null; // 用來搜尋、管理藍芽裝置
	private static BluetoothSocket mBluetoothSocket = null; // 用來連結藍芽裝置、以及傳送指令
	private static final UUID MY_UUID = UUID
			.fromString("00001101-0000-1000-8000-00805F9B34FB"); // 一定要是這組
	private static final String DEVICE_ADDRESS = "00:12:09:29:44:77"; // Bluetooth
																		// Address
	static private InputStream mInputStream;
	static private OutputStream mOutputStream;
	private boolean gpio[] = { false, false, false, false, false, false, false,
			false, false, false };
	private ToggleButton btn[];
	private Button btnClear, btnConnect;
	private SharedPreferences pref;
	private TextView tvMessage;
	private String reMsg = "";
	private SparseArray<String> sa_btn;
	private boolean stopWorker = true;
	private boolean isInit = true;
	private boolean isError = false;
	private boolean isConnect = false;
	private Thread readTask = null;
	private Thread getTask = null;

	final Handler handler = new Handler() {

		@Override
		public void handleMessage(Message msg) {
			reMsg = (String) msg.obj;
			String[] list = reMsg.split(" ");
			if (list.length > 0) {
				for (int i = 0; i < list.length; i++) {
					if (list[i].equals("Get")) {
						try {
							for (int j = i + 1; j < list.length; j++) {
								int data = Integer.parseInt(list[j]);
								boolean check = (data > 0) ? true : false;
								if (check != gpio[j - i - 1]) {
									gpio[j - i - 1] = check;
									isInit = true;
									btn[j - i - 1].setChecked(check);
									isInit = false;
								}
							}
						} catch (NumberFormatException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
				}
			}
			tvMessage.setText(reMsg);
		}

	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		// init();
		initdata();
		findViews();
		display();
	}

	@Override
	protected void onPause() {
		// TODO Auto-generated method stub
		super.onPause();
	}

	@Override
	protected void onResume() {
		// TODO Auto-generated method stub
		super.onResume();
		IntentFilter filter = new IntentFilter(
				BluetoothAdapter.ACTION_STATE_CHANGED);
		registerReceiver(mReceiver, filter);
		isInit = false;
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		unregisterReceiver(mReceiver);
		stop();
	}

	private void stop() {
		try {
			isConnect = false;
			stopWorker = true;
			readTask = null;
			getTask = null;
			if (mInputStream != null)
				mInputStream.close();
			if (mOutputStream != null)
				mOutputStream.close();
			if (mBluetoothSocket != null)
				mBluetoothSocket.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void display() {
		Log.d("display", "====");
		String cmd = pref.getString("cmd", "");
		Log.d("cmd", cmd + "=" + gpio.length);
		for (int i = 0; i < cmd.length(); i++) {
			if (i < gpio.length) {
				char data = cmd.charAt(i);
				// Log.d("display", i + "");
				if (data == '1') {
					gpio[i] = true;
				} else {
					gpio[i] = false;
				}
				btn[i].setChecked(gpio[i]);
			}
		}
	}

	private void initdata() {
		sa_btn = new SparseArray<String>();
		sa_btn.put(R.id.tbGPIO1, "4");
		sa_btn.put(R.id.tbGPIO2, "5");
		sa_btn.put(R.id.tbGPIO3, "6");
		sa_btn.put(R.id.tbGPIO4, "7");
		sa_btn.put(R.id.tbGPIO5, "8");
		sa_btn.put(R.id.tbGPIO6, "9");
		sa_btn.put(R.id.tbGPIO7, "10");
		sa_btn.put(R.id.tbGPIO8, "11");
		sa_btn.put(R.id.tbGPIO9, "12");
		sa_btn.put(R.id.tbGPI10, "13");
		btn = new ToggleButton[sa_btn.size()];
		pref = getSharedPreferences("data", Context.MODE_PRIVATE);

	}

	private void findViews() {
		for (int i = 0; i < sa_btn.size(); i++) {
			btn[i] = (ToggleButton) findViewById(sa_btn.keyAt(i));
			btn[i].setOnCheckedChangeListener(this);
		}
		tvMessage = (TextView) findViewById(R.id.tvMessage);
		tvMessage.setText(reMsg);
		btnClear = (Button) findViewById(R.id.btnClear);
		btnClear.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				reMsg = "";
				tvMessage.setText(reMsg);
			}
		});
		btnConnect = (Button) findViewById(R.id.btnConnect);
		btnConnect.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				if(!isConnect){
				   init();
				   setAllCmd();
				   if(isConnect){
				       btnConnect.setText("斷線");
				   }
				}else{
				   stop();
				   if(!isConnect){
				       btnConnect.setText("連線");
				   }
				}
			}
		});
	}

	private void getMessage() {
		stopWorker = false;
		readTask = new Thread(new InputMessageTask());
		readTask.start();
		getTask = new Thread(new GetMessageTask());
		getTask.start();
	}

	private void init() {
		try {
			mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
			if (mBluetoothAdapter.isEnabled()) {
				BluetoothDevice device = mBluetoothAdapter
						.getRemoteDevice(DEVICE_ADDRESS);
				Log.d("name", mBluetoothAdapter.getName());
				Log.d("address", mBluetoothAdapter.getAddress());
				
				mBluetoothSocket = device
						.createInsecureRfcommSocketToServiceRecord(MY_UUID);
				mBluetoothSocket.connect();
				if (mBluetoothSocket.isConnected()) {
					mInputStream = mBluetoothSocket.getInputStream();
					mOutputStream = mBluetoothSocket.getOutputStream();
					btnConnect.setText("斷線");
					isConnect = true;
					isError = false;
					getMessage();
				} else {
					Toast.makeText(this, "請開啟設備", Toast.LENGTH_SHORT).show();
					btnConnect.setText("連線");
					isConnect = false;
					isError = true;
				}
			} else {
				Toast.makeText(this, "請開啟藍芽", Toast.LENGTH_SHORT).show();
				btnConnect.setText("連線");
				isConnect = false;
				isError = true;
			}
		} catch (IOException e) {
			Toast.makeText(this, "請開啟藍芽和設備", Toast.LENGTH_SHORT).show();
			btnConnect.setText("連線");
			isConnect = false;
			isError = true;
		}
	}

	class GetMessageTask implements Runnable {

		@Override
		public void run() {
			while (!Thread.currentThread().isInterrupted() && !stopWorker
					&& !isError) {
				getAllCmd();
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}

	}

	class InputMessageTask implements Runnable {

		byte[] readBuffer;
		int readBufferPosition;

		public InputMessageTask() {
			readBufferPosition = 0;
			readBuffer = new byte[1024];
		}

		@Override
		public void run() {
			while (!Thread.currentThread().isInterrupted() && !stopWorker
					&& !isError) {
				try {
					if (mInputStream == null) {
						stop();
						break;
					}
					int bytesAvailable = mInputStream.available();
					if (bytesAvailable > 0) {
						byte[] packetBytes = new byte[bytesAvailable];
						mInputStream.read(packetBytes);
						for (int i = 0; i < bytesAvailable; i++) {
							byte b = packetBytes[i];
							readBuffer[readBufferPosition++] = b;
						}
						byte[] encodedBytes = new byte[readBufferPosition];
						System.arraycopy(readBuffer, 0, encodedBytes, 0,
								encodedBytes.length);
						final String data = new String(encodedBytes, "UTF-8");
						readBufferPosition = 0;
						sendMsg(data);
					}
					// mBluetoothGatt.readRemoteRssi();
					try {
						Thread.sleep(100);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				} catch (IOException ex) {
					stopWorker = true;
				}
			}
		}

	}

	synchronized private void sendMsg(String data) {
		Message msg = new Message();
		msg = handler.obtainMessage(1, data);
		handler.sendMessage(msg);
	}

	@Override
	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
		if (isInit)
			return;

		String data = sa_btn.get(buttonView.getId());
		Log.d("check", data);
		gpio[Integer.parseInt(data) - 4] = isChecked;
		setOneCmd(Integer.parseInt(data), isChecked);
	}

	private void setOneCmd(int num, boolean isOn) {
		if (!isError()) {
			String cmd = "Set ";
			if (num < 10) {
				cmd = cmd + "0" + num;
			} else {
				cmd = cmd + num;
			}
			if (isOn) {
				cmd = cmd + " 1\n";
			} else {
				cmd = cmd + " 0\n";
			}
			try {
				mOutputStream.write(cmd.getBytes(Charset.forName("UTF-8")));
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			Log.d("cmd", cmd);
			save();
		}
	}

	private void getOneCmd(int num) {
		if (!isError()) {
			String cmd = "Get ";
			if (num < 10) {
				cmd = cmd + "0" + num + "\n";
			} else {
				cmd = cmd + num + "\n";
			}
			try {
				mOutputStream.write(cmd.getBytes(Charset.forName("UTF-8")));
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			Log.d("cmd", cmd);
		}
	}

	private void getAllCmd() {
		if (!isError()) {
			String cmd = "Get All\n";
			try {
				mOutputStream.write(cmd.getBytes(Charset.forName("UTF-8")));
			} catch (IOException e) {
				e.printStackTrace();
			}
			// Log.d("cmd", cmd);
		}
	}

	private void save() {
		String cmd = "";
		for (boolean check : gpio) {
			if (check) {
				cmd += "1";
			} else {
				cmd += "0";
			}
		}
		pref.edit().putString("cmd", cmd).commit();
	}

	private void setAllCmd() {
		if (!isError()) {
			try {
				String cmd = "All ";
				for (boolean check : gpio) {
					if (check) {
						cmd += "1";
					} else {
						cmd += "0";
					}
				}
				cmd = cmd + "\n";
				Log.d("cmd", cmd);
				mOutputStream.write(cmd.getBytes(Charset.forName("UTF-8")));
			} catch (IOException e) {
				Toast.makeText(this, "送出失敗", Toast.LENGTH_SHORT).show();
			}
			save();
		}
	}

	private boolean isError() {
		if (!mBluetoothAdapter.isEnabled()) {
			isError = true;
		}

		if ((mOutputStream == null) || (mInputStream == null)
				|| (mBluetoothSocket == null)) {
			isError = true;			
		}

		if(isError){
			stop();
			isConnect = false;
			btnConnect.setText("連線");
			Log.d("isError", "Error!!!!!!!");
		}
		return isError;
	}

	private final BroadcastReceiver mReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			final String action = intent.getAction();

			if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
				final int state = intent.getIntExtra(
						BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
				switch (state) {
				case BluetoothAdapter.STATE_OFF:
					Log.d("BT", "Bluetooth off");
					break;
				case BluetoothAdapter.STATE_TURNING_OFF:
					Log.d("BT", "Turning Bluetooth off...");
					break;
				case BluetoothAdapter.STATE_ON:
					Log.d("BT", "Bluetooth on");
					break;
				case BluetoothAdapter.STATE_TURNING_ON:
					Log.d("BT", "Turning Bluetooth on...");
					break;
				}
			}
		}
	};
}
