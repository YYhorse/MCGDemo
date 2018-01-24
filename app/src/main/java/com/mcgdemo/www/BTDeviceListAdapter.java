package com.mcgdemo.www;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;

public class BTDeviceListAdapter extends BaseAdapter {

	private LayoutInflater mInflator;
	private ArrayList<BluetoothDevice> mLeDevices;
	private Context mContext;

	public BTDeviceListAdapter(Context context) {
		super();
		mContext = context;
		mLeDevices = new ArrayList<BluetoothDevice>();
		mInflator = LayoutInflater.from(mContext);
	}

	public void addDevice(BluetoothDevice device) {
		Log.e("yy",device.getName()+"   "+device.getAddress());
		if (!mLeDevices.contains(device)&&device.getName()!=null){
			if(device.getName().compareTo("BrainLink_Lite")==0)
				mLeDevices.add(device);								//必须是BrainLink_Lite的设备才能搜索到
		}
	}

	public BluetoothDevice getDevice(int position) {
		return mLeDevices.get(position);
	}

	public void clear() {
		mLeDevices.clear();
	}

	@Override
	public int getCount() {
		return mLeDevices.size();
	}

	@Override
	public Object getItem(int i) {
		return mLeDevices.get(i);
	}

	@Override
	public long getItemId(int i) {
		return i;
	}

	@Override
	public View getView(int i, View view, ViewGroup viewGroup) {

		ViewHolder viewHolder;
		// General ListView optimization code.
		if (view == null) {
			view = mInflator.inflate(R.layout.listitem_device, null);
			viewHolder = new ViewHolder();
			viewHolder.deviceName = (TextView) view.findViewById(R.id.device_name);
			view.setTag(viewHolder);
		}
		else
			viewHolder = (ViewHolder) view.getTag();
		BluetoothDevice device = mLeDevices.get(i);
		final String deviceName = device.getName();
		String deviceAddress = device.getAddress();
		if (deviceName != null && deviceName.length() > 0)
			viewHolder.deviceName.setText(deviceAddress);//deviceName + "  (" + deviceAddress+")");
		else
			viewHolder.deviceName.setText( "无名称" + "  (" +deviceAddress+")");
		return view;
	}
	static class ViewHolder {
		TextView deviceName;
	}
}