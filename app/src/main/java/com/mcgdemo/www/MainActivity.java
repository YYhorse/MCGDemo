package com.mcgdemo.www;

import android.app.Activity;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.neurosky.connection.ConnectionStates;
import com.neurosky.connection.DataType.MindDataType;
import com.neurosky.connection.TgStreamHandler;
import com.neurosky.connection.TgStreamReader;
import com.shinelw.library.ColorArcProgressBar;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import cn.pedant.SweetAlert.SweetAlertDialog;

public class MainActivity extends Activity {
    public ToggleButton device_tbtn, car_tbtn;
    Button test_btn;
    private int ChooseCarNumber = 0;                    //1号车 2号车
    TextView signal_txt;
    public LinearLayout mainshow_layout, wave_layout;
    private RelativeLayout buildshow_layout;
    private ColorArcProgressBar attention_bar, meditation_bar;
    //评测相关数据
    private String BeginTime, StopTime;
    private int avg_attention, avg_meditation = 0;
    boolean TestState = false;                                                                      //评测开关
    //EEG 脑波固件库
    private TgStreamReader tgStreamReader;
    private static final int MSG_UPDATE_BAD_PACKET = 1001;
    private static final int MSG_UPDATE_STATE = 1002;
    private boolean isReadFilter = false;
    private int badPacketCount = 0;
    //蓝牙相关接口
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothDevice mBluetoothDevice;
    private String address = null;
    private Boolean DeviceBlueConnectState = false;                                                 //设备蓝牙连接状态
    //******************WIFI控制**********************//
    public Boolean DeviceWifiConnectState = false;                                                  //设备WIFI连接状态
    private boolean SearchFlag = false;                                                             //WIFI搜素 标志
    private boolean SocketFlag = false;                                                             //连接SOcket
    private String wifiPassword = null;
    public WifiUtils localWifiUtils;
    private List<ScanResult> wifiResultList;
    private List<String> wifiListString = new ArrayList<String>();
    private ArrayAdapter<String> arrayWifiAdapter;
    TcpSocket tsocket;
    String TcpSocketIp = "12.12.122.254";
    String lastsenddata = "";

    public SweetAlertDialog pDialog;
    public int LoadDialogflag = 0;                      //加载条工作状态 0无   1蓝牙连接   2WIFI连接    3蓝牙断开连接
    DrawWaveView waveView = null;                                    //绘画波形
    Handler handler = new Handler();
    Runnable runnable = new Runnable() {
        @Override
        public void run() {
            handler.removeCallbacks(runnable);
            if (SearchFlag == true) {
                SearchFlag = false;
                SearchAndPairWifi();
            }
            if (SocketFlag == true) {
                WifiInfo wifiinfo = localWifiUtils.localWifiManager.getConnectionInfo();
                if (wifiinfo.getSSID().compareTo("\"BrainPort\"") == 0) {
                    SocketFlag = false;
                    ConnectTcpSocket();
                } else
                    handler.postDelayed(runnable, 500);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        device_tbtn = (ToggleButton) findViewById(R.id.device_tbtn);
        car_tbtn = (ToggleButton) findViewById(R.id.car_tbtn);
        test_btn = (Button) findViewById(R.id.test_btn);
        signal_txt = (TextView) findViewById(R.id.signal_txt);
        attention_bar = (ColorArcProgressBar) findViewById(R.id.attention_bar);
        meditation_bar = (ColorArcProgressBar) findViewById(R.id.meditation_bar);
        mainshow_layout = (LinearLayout) findViewById(R.id.mainshow_layout);
        buildshow_layout = (RelativeLayout) findViewById(R.id.buildshow_layout);
        wave_layout = (LinearLayout) findViewById(R.id.wave_layout);
        device_tbtn.setOnClickListener(new ClickDeviceButtonMethod());
        car_tbtn.setOnClickListener(new ClickDeviceMethod());
        BlueToothCheck();
        initWifi();
        setUpDrawWaveView();

        scanDevice();                                   //搜索附近头戴设备
    }

    /*********************************************************************************************************
     * * 函数名称: void BlueToothCheck()
     * * 功能说明：本地蓝牙判断
     ********************************************************************************************************/
    private void BlueToothCheck() {
        try {
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
                Toast.makeText(this, "请打开蓝牙进行连接!", Toast.LENGTH_LONG).show();
                finish();
            }
        } catch (Exception e) {
            Toast.makeText(this, "蓝牙打开失败!", Toast.LENGTH_LONG).show();
            e.printStackTrace();
            return;
        }
    }

    /*********************************************************************************************************
     * * 函数名称: setUpDrawWaveView()
     * * 功能说明：使能绘画视图线程
     ********************************************************************************************************/
    public void setUpDrawWaveView() {
        waveView = new DrawWaveView(getApplicationContext());
        wave_layout.addView(waveView, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        waveView.setValue(2048, 2048, -2048);
    }

    /*********************************************************************************************************
     * * 函数名称: void updateWaveView(int data)
     * * 功能说明：更新绘画视图数据
     ********************************************************************************************************/
    public void updateWaveView(int data) {
        if (waveView != null)
            waveView.updateData(data);
    }

    /*********************************************************************************************************
     * * 函数名称: TgStreamReader createStreamReader(BluetoothDevice bd)
     * * 功能说明：读取脑波数据流
     ********************************************************************************************************/
    public TgStreamReader createStreamReader(BluetoothDevice bd) {
        if (tgStreamReader == null) {
            tgStreamReader = new TgStreamReader(bd, callback);
            tgStreamReader.startLog();
        } else {
            tgStreamReader.changeBluetoothDevice(bd);
            tgStreamReader.setTgStreamHandler(callback);
        }
        return tgStreamReader;
    }

    /*********************************************************************************************************
     * * 函数名称: TgStreamHandler callback = new TgStreamHandler()
     * * 功能说明：响应蓝牙回复数据
     ********************************************************************************************************/
    private TgStreamHandler callback = new TgStreamHandler() {
        @Override
        public void onStatesChanged(int connectionStates) {
            // TODO Auto-generated method stub
            switch (connectionStates) {
                case ConnectionStates.STATE_CONNECTED:
                    DeviceBlueConnectState = true;
                    showToast("头戴握手成功", Toast.LENGTH_SHORT);
                    break;
                case ConnectionStates.STATE_WORKING:
                    //byte[] cmd = new byte[1];
                    //cmd[0] = 's';
                    //tgStreamReader.sendCommandtoDevice(cmd);
                    LinkDetectedHandler.sendEmptyMessageDelayed(1234, 5000);
                    break;
                case ConnectionStates.STATE_GET_DATA_TIME_OUT:
                    DeviceBlueConnectState = false;
                    LoadDialogflag = 3;
                    showToast("头戴已离线", Toast.LENGTH_SHORT);
                    break;
                case ConnectionStates.STATE_COMPLETE:
                    break;
                case ConnectionStates.STATE_STOPPED:
                    break;
                case ConnectionStates.STATE_DISCONNECTED:
                    break;
                case ConnectionStates.STATE_ERROR:
                    break;
                case ConnectionStates.STATE_FAILED:
                    DeviceBlueConnectState = false;
                    showToast("头戴连接失败，请确保开机", Toast.LENGTH_LONG);
                    break;
            }
            Message msg = LinkDetectedHandler.obtainMessage();
            msg.what = MSG_UPDATE_STATE;                                        //更新状态包
            msg.arg1 = connectionStates;
            LinkDetectedHandler.sendMessage(msg);
        }

        @Override
        public void onRecordFail(int a) {
        }

        /*********************************************************************************************************
         * * 函数名称: void onChecksumFail(byte[] payload, int length, int checksum)
         * * 功能说明：EEG数据包校验失败
         ********************************************************************************************************/
        @Override
        public void onChecksumFail(byte[] payload, int length, int checksum) {
            // TODO Auto-generated method stub
            badPacketCount++;
            Message msg = LinkDetectedHandler.obtainMessage();
            msg.what = MSG_UPDATE_BAD_PACKET;                            //上传校验失败包
            msg.arg1 = badPacketCount;
            LinkDetectedHandler.sendMessage(msg);
        }

        /*********************************************************************************************************
         * * 函数名称: void onDataReceived(int datatype, int data, Object obj)
         * * 功能说明：EEG数据包上传
         ********************************************************************************************************/
        @Override
        public void onDataReceived(int datatype, int data, Object obj) {
            // TODO Auto-generated method stub
            Message msg = LinkDetectedHandler.obtainMessage();
            msg.what = datatype;
            msg.arg1 = data;
            msg.obj = obj;
            LinkDetectedHandler.sendMessage(msg);
        }
    };

    /*********************************************************************************************************
     * * 函数名称: Handler LinkDetectedHandler = new Handler()
     * * 功能说明：解析EEG数据线程
     ********************************************************************************************************/
    private Handler LinkDetectedHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1234:
                    tgStreamReader.MWM15_getFilterType();
                    isReadFilter = true;
                    break;
                case 1235:
                    tgStreamReader.MWM15_setFilterType(MindDataType.FilterType.FILTER_60HZ);
                    LinkDetectedHandler.sendEmptyMessageDelayed(1237, 1000);
                    break;
                case 1236:
                    tgStreamReader.MWM15_setFilterType(MindDataType.FilterType.FILTER_50HZ);
                    LinkDetectedHandler.sendEmptyMessageDelayed(1237, 1000);
                    break;
                case 1237:
                    tgStreamReader.MWM15_getFilterType();
                    break;

                case MindDataType.CODE_FILTER_TYPE:
                    if (isReadFilter) {
                        isReadFilter = false;
                        if (msg.arg1 == MindDataType.FilterType.FILTER_50HZ.getValue())
                            LinkDetectedHandler.sendEmptyMessageDelayed(1235, 1000);
                        else if (msg.arg1 == MindDataType.FilterType.FILTER_60HZ.getValue())
                            LinkDetectedHandler.sendEmptyMessageDelayed(1236, 1000);
                    }
                    break;
                case MindDataType.CODE_RAW:
                    updateWaveView(msg.arg1);
                    break;
                //=============放松度数据==============//
                case MindDataType.CODE_MEDITATION:
                    meditation_bar.setCurrentValues(msg.arg1);
                    if (TestState == true)
                        avg_meditation = (avg_meditation + msg.arg1) / 2;
                    break;
                //=============专注度数据==============//
                case MindDataType.CODE_ATTENTION:
                    attention_bar.setCurrentValues(msg.arg1);
                    if (car_tbtn.isChecked()) {
                        //赛车连接成功   赛车驱动
                        int num = floatToInt((float) msg.arg1 / 10);
                        SendAttentionData(num);
                    }
                    if (TestState == true)
                        avg_attention = (avg_attention + msg.arg1) / 2;
                    break;
                //=============各类波数据==============//
                case MindDataType.CODE_EEGPOWER:
                    break;
                //=============信号值==============//
                case MindDataType.CODE_POOR_SIGNAL://
                    signal_txt.setText("信号强度:" + msg.arg1);
                    if (msg.arg1 < 20) {
                        signal_txt.setTextColor(0xff008000);
                        signal_txt.setText("信号强度:优");
                    } else if (msg.arg1 >= 20 && msg.arg1 < 60) {
                        signal_txt.setTextColor(0xffFFA500);
                        signal_txt.setText("信号强度:良");
                    } else if (msg.arg1 > 60 && msg.arg1 < 200) {
                        signal_txt.setTextColor(0xffFF0000);
                        signal_txt.setText("信号强度:差");
                    } else if (msg.arg1 == 200) {
                        signal_txt.setTextColor(0xffFF0000);
                        signal_txt.setText("请佩戴好设备");
                    }
                    break;
                //===========校验失败包=============//
                case MSG_UPDATE_BAD_PACKET:
                    break;
                default:
                    break;
            }
            super.handleMessage(msg);
        }
    };

    private void SendAttentionData(int num) {
        String sendData = " ";
//        if(num==10) {
//            if(ChooseCarNumber ==1)
//                sendData = "{PAX}";
//            else if(ChooseCarNumber == 2)
//                sendData = "{PBX}";
//        }
//        else {
//            if(ChooseCarNumber == 1)
//                sendData = "{PA" + String.valueOf(num) + "}";
//            else if(ChooseCarNumber == 2)
//                sendData = "{PB" + String.valueOf(num) + "}";
//        }
        //----------降低难度   最大速度7
        if (ChooseCarNumber == 1 || ChooseCarNumber == 2) {
            if (num > 3) {
                switch (num) {
                    case 4:
                        num = 4;
                        break;
                    case 5:
                        num = 4;
                        break;
                    case 6:
                        num = 5;
                        break;
                    case 7:
                        num = 5;
                        break;
                    case 8:
                        num = 6;
                        break;
                    case 9:
                        num = 6;
                        break;
                    case 10:
                        num = 7;
                        break;
                }
            }
            if (ChooseCarNumber == 1)
                sendData = "{PA" + String.valueOf(num) + "}";
            else
                sendData = "{PB" + String.valueOf(num) + "}";
        }
        if (sendData.compareTo(" ") != 0 && sendData.compareTo(lastsenddata) != 0) {
            lastsenddata = sendData;
            try {
                tsocket.Tcp_SendByte(sendData.getBytes("gb2312"));
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
    }

    public void ClickChooseCarMethod(View view) {
        ChooseCarMethod();
    }

    public void ChooseCarMethod() {
        final AlertDialog myDialog = new AlertDialog.Builder(MainActivity.this).create();
        Window w = myDialog.getWindow();
        WindowManager.LayoutParams lp = w.getAttributes();
        lp.x = 0;
        lp.y = 100;
        myDialog.show();
        myDialog.setCanceledOnTouchOutside(false);
        myDialog.getWindow().setContentView(R.layout.choosecartipe);
        myDialog.getWindow().findViewById(R.id.chooseAcar_btn)
                .setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        myDialog.dismiss();
                        ChooseCarNumber = 1;
                    }
                });
        myDialog.getWindow().findViewById(R.id.chooseBcar_btn)
                .setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        myDialog.dismiss();
                        ChooseCarNumber = 2;
                    }
                });
    }

    //###############################蓝牙搜索#####################################//
    private ListView list_select;
    private ImageView flash_iview;
    private BTDeviceListAdapter deviceListApapter = null;
    private Dialog selectDialog;

    /*********************************************************************************************************
     * * 函数名称: void scanDevice()
     * * 功能说明：搜索附近蓝牙设备
     ********************************************************************************************************/
    public void scanDevice() {
        if (mBluetoothAdapter.isDiscovering())
            mBluetoothAdapter.cancelDiscovery();
        setUpDeviceListView();
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        this.registerReceiver(mReceiver, filter);
        mBluetoothAdapter.startDiscovery();
        showToast("搜索附近头戴设备!", Toast.LENGTH_LONG);
    }

    /*********************************************************************************************************
     * * 函数名称: void setUpDeviceListView()
     * * 功能说明：显示附近蓝牙设备
     ********************************************************************************************************/
    private void setUpDeviceListView() {
        LayoutInflater inflater = LayoutInflater.from(this);
        View view = inflater.inflate(R.layout.dialog_select_device, null);
        list_select = (ListView) view.findViewById(R.id.list_select);
        flash_iview = (ImageView) view.findViewById(R.id.flash_iview);
        selectDialog = new Dialog(this, R.style.dialog1);
        selectDialog.setContentView(view);
        deviceListApapter = new BTDeviceListAdapter(this);
        list_select.setAdapter(deviceListApapter);
        list_select.setOnItemClickListener(selectDeviceItemClickListener);
        selectDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface arg0) {
                // TODO Auto-generated method stub
                MainActivity.this.unregisterReceiver(mReceiver);
            }
        });
        flash_iview.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //点击刷新蓝牙搜索
                if (mBluetoothAdapter.isDiscovering())
                    mBluetoothAdapter.cancelDiscovery();
                mBluetoothAdapter.startDiscovery();
                showToast("开始搜索附近设备!", Toast.LENGTH_LONG);
            }
        });
        view.setAnimation(AnimationUtil.moveToViewLocation());              //添加进入动画
        selectDialog.show();
//        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
//        for(BluetoothDevice device: pairedDevices){
//            deviceListApapter.addDevice(device);
//        }
        deviceListApapter.notifyDataSetChanged();
    }

    /*********************************************************************************************************
     * * 函数名称: BroadcastReceiver mReceiver
     * * 功能说明：蓝牙搜索后蓝牙数据广播
     ********************************************************************************************************/
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            // When discovery finds a device
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                // update to UI
                deviceListApapter.addDevice(device);
                deviceListApapter.notifyDataSetChanged();
            }
        }
    };

    /*********************************************************************************************************
     * * 函数名称: OnItemClickListener selectDeviceItemClickListener
     * * 功能说明：点击响应连接对应蓝牙设备
     ********************************************************************************************************/
    private AdapterView.OnItemClickListener selectDeviceItemClickListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
            // TODO Auto-generated method stub
            if (mBluetoothAdapter.isDiscovering())
                mBluetoothAdapter.cancelDiscovery();
            MainActivity.this.unregisterReceiver(mReceiver);
            mBluetoothDevice = deviceListApapter.getDevice(arg2);
            selectDialog.dismiss();
            selectDialog = null;
            address = mBluetoothDevice.getAddress().toString();
            BluetoothDevice remoteDevice = mBluetoothAdapter.getRemoteDevice(mBluetoothDevice.getAddress().toString());
            tgStreamReader = createStreamReader(remoteDevice);
            tgStreamReader.connectAndStart();
            Loading("头戴连接中");
            LoadDialogflag = 1;                                             //打开加载条   状态为搜索蓝牙
        }
    };

    /*********************************************************************************************************
     * * 函数名称: void initWifi()
     * * 功能说明：初始化WIFI
     ********************************************************************************************************/
    private void initWifi() {
        localWifiUtils = new WifiUtils(MainActivity.this);
        arrayWifiAdapter = new ArrayAdapter<String>(this, R.layout.wifi_item, wifiListString);
        wifiListString.clear();
        if (localWifiUtils.WifiCheckState() == 1)
            localWifiUtils.WifiOpen();            //WIFI未打开
    }

    public void ClickBeginGameMethod(View view) {
        if (device_tbtn.isChecked() == true)
            ChooseWifiMethod();
        else
            scanDevice();
    }

    /*********************************************************************************************************
     * * 函数名称: ClickDeviceMethod
     * * 功能说明：点击连接WIFI  连接或者断开连接
     ********************************************************************************************************/
    class ClickDeviceMethod implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            if (car_tbtn.isChecked()) {
                car_tbtn.setChecked(false);
                ChooseWifiMethod();
            } else {
                tsocket.Tcp_Socket_DisConnect();
                Toast.makeText(MainActivity.this, "断开脑波基站", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /*********************************************************************************************************
     * * 函数名称: ClickDeviceButtonMethod
     * * 功能说明：点击连接头戴   连接或者断开连接
     ********************************************************************************************************/
    class ClickDeviceButtonMethod implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            if (device_tbtn.isChecked()) {
                device_tbtn.setChecked(false);
                scanDevice();
            } else {
                showToast("断开头戴设备!", 1000);
                if (tgStreamReader != null) {
                    tgStreamReader.stop();
                    tgStreamReader.close();
                    tgStreamReader = null;
                }
            }
        }
    }

    public void ClickBeginTestMethod(View view) {
        if (TestState == false) {
            TestState = true;
            test_btn.setBackgroundResource(R.drawable.flatbuttonyellow);
            test_btn.setTextColor(0xffffffff);
            test_btn.setText("结束评测");
            avg_attention = 0;
            avg_meditation = 0;
            TimeUtil tu = new TimeUtil();
            BeginTime = tu.getNowTime();
        } else {
            TestState = false;
            test_btn.setBackgroundResource(R.drawable.flatbuttonwhite);
            test_btn.setTextColor(0xff000000);
            test_btn.setText("开始评测");
            TimeUtil tu = new TimeUtil();
            StopTime = tu.getNowTime();
            String AllTime = tu.getTimeDifference(BeginTime, StopTime);

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage("本次评测花费" + AllTime + "\n平均专注度:" + String.valueOf(avg_attention) + "\n平均冥想度:" + String.valueOf(avg_meditation));
            builder.setTitle("评测打分");
            builder.setPositiveButton("确认", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface arg0, int arg1) {
                    Toast.makeText(getApplicationContext(), "完成", Toast.LENGTH_SHORT).show();
                }
            }).show();
        }

    }

    public void ConnectTcpSocket() {
        tsocket = new TcpSocket(this, TcpSocketIp);                                                  //打印测试  建立TCP连接
    }

    /*********************************************************************************************************
     * * 函数名称: int floatToInt(float f)
     * * 功能说明：float 转 int 四舍五入
     ********************************************************************************************************/
    public int floatToInt(float f) {
        int i = 0;
        if (f > 0)                                                                                     //正数
            i = (int) (f * 10 + 5) / 10;
        else if (f < 0) //负数
            i = (int) (f * 10 - 5) / 10;
        else i = 0;
        return i;
    }

    /*********************************************************************************************************
     * * 函数名称: void ChooseWifiMethod()
     * * 功能说明：尝试WIFI联入
     ********************************************************************************************************/
    public void ChooseWifiMethod() {
        WifiInfo wifiinfo = localWifiUtils.localWifiManager.getConnectionInfo();
        if (wifiinfo.getSSID().compareTo("\"BrainPort\"") == 0) {
            LoadDialogflag = 2;
            Loading("脑波基站握手中");
            ConnectTcpSocket();
        } else {
            localWifiUtils.WifiStartScan();
            Loading("搜索附近脑波基站");
            SearchFlag = true;
            handler.postDelayed(runnable, 3000);                                                        //3秒后从新请求服务器
        }
    }

    /*********************************************************************************************************
     * * 函数名称: void SearchAndPairWifi()
     * * 功能说明：搜索到WIFI并建立绑定连接
     ********************************************************************************************************/
    public void SearchAndPairWifi() {
        pDialog.cancel();
        while (localWifiUtils.WifiCheckState() != WifiManager.WIFI_STATE_ENABLED)
            Log.i("WifiState", String.valueOf(localWifiUtils.WifiCheckState()));
        wifiResultList = localWifiUtils.getScanResults();
        localWifiUtils.getConfiguration();

        wifiListString.clear();
        if (wifiListString != null)
            scanResultToString(wifiResultList, wifiListString);

        final AlertDialog myDialog = new AlertDialog.Builder(MainActivity.this).create();
        myDialog.show();
        myDialog.setCanceledOnTouchOutside(false);
        myDialog.getWindow().setContentView(R.layout.wificonfig);
        ListView WIFI_listview = (ListView) myDialog.getWindow().findViewById(R.id.WIFI_listview);
        WIFI_listview.setAdapter(arrayWifiAdapter);
        WIFI_listview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            String wifiItemSSID = null;

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // TODO Auto-generated method stub
                String wifiItem = arrayWifiAdapter.getItem(position);                               //获得选中的设备
                String[] ItemValue = wifiItem.split("--");
                wifiItemSSID = ItemValue[0];
                int netId = localWifiUtils.AddWifiConfig(wifiResultList, wifiItemSSID, wifiPassword);
                if (netId != -1) {
                    localWifiUtils.getConfiguration();                                              //添加了配置信息，要重新得到配置信息
                    if (localWifiUtils.ConnectWifi(netId)) {
                        myDialog.cancel();
                        SocketFlag = true;
                        LoadDialogflag = 2;
                        Loading("脑波基站握手中");
                        handler.postDelayed(runnable, 6000);                                        //4秒后从新请求服务器
                    }
                }
            }
        });
    }

    /*********************************************************************************************************
     * * 函数名称:  scanResultToString(List<ScanResult> listScan,List<String> listStr)
     * * 功能说明： ScanResult类型转为String
     ********************************************************************************************************/
    public void scanResultToString(List<ScanResult> listScan, List<String> listStr) {
        for (int i = 0; i < listScan.size(); i++) {
            ScanResult strScan = listScan.get(i);
            String str = strScan.SSID + "--" + strScan.BSSID;
            if (strScan.SSID.compareTo("BrainPort") == 0) {
                boolean bool = listStr.add(str);
                if (bool)
                    arrayWifiAdapter.notifyDataSetChanged();                                        //数据更新,只能单个Item更新，不能够整体List更新
                else
                    Log.i("scanResultToSting", "fail");
            }
        }
    }

    public void ClickExitGameMethod(View view) {
        LoadDialogflag = 4;
        showToast("断开头戴设备!", 1000);
    }

    /*********************************************************************************************************
     * * 函数名称: void showToast(final String msg,final int timeStyle)
     * * 功能说明：非UI线程显示Toast
     ********************************************************************************************************/
    public void showToast(final String msg, final int timeStyle) {
        MainActivity.this.runOnUiThread(new Runnable() {
            public void run() {
                if (LoadDialogflag == 1) {
                    //蓝牙连接返回处理
                    LoadDialogflag = 0;
                    pDialog.cancel();
                    //---------蓝牙连接状态修正
                    if (DeviceBlueConnectState == true) {
                        device_tbtn.setChecked(true);
                        ChooseWifiMethod();             //打开WIFI搜索
                    } else
                        device_tbtn.setChecked(false);
                } else if (LoadDialogflag == 2) {
                    //WIFI连接返回处理
                    LoadDialogflag = 0;
                    pDialog.cancel();
                    //---------wifi连接状态修正
                    if (DeviceWifiConnectState == true) {
                        car_tbtn.setChecked(true);
                        //-----开始游戏主界面
                        buildshow_layout.setVisibility(View.GONE);
                        buildshow_layout.setAnimation(AnimationUtil.moveToViewBottom());
                        mainshow_layout.setVisibility(View.VISIBLE);
                        mainshow_layout.setAnimation(AnimationUtil.moveToViewLocation());
                        ChooseCarMethod();
                    } else
                        car_tbtn.setChecked(false);

                } else if (LoadDialogflag == 3 || LoadDialogflag == 4) {
                    if (LoadDialogflag == 4) {
                        //退出游戏
                        if (tgStreamReader != null) {
                            tgStreamReader.stop();
                            tgStreamReader.close();
                            tgStreamReader = null;
                        }
                    }
                    //蓝牙头戴断开连接
                    LoadDialogflag = 0;
                    device_tbtn.setChecked(false);
                    car_tbtn.setChecked(false);
                    TestState = false;
                    test_btn.setBackgroundResource(R.drawable.flatbuttonwhite);
                    test_btn.setTextColor(0xff000000);
                    test_btn.setText("开始评测");
                    if (DeviceWifiConnectState == true) {
                        DeviceWifiConnectState = false;
                        tsocket.Tcp_Socket_DisConnect();
                    }
                    //-----开始游戏主界面
                    mainshow_layout.setVisibility(View.GONE);
                    mainshow_layout.setAnimation(AnimationUtil.moveToViewBottom());
                    buildshow_layout.setVisibility(View.VISIBLE);
                    buildshow_layout.setAnimation(AnimationUtil.moveToViewLocation());
                }
                Toast.makeText(getApplicationContext(), msg, timeStyle).show();
            }

        });
    }

    /*******************************************************************************************
     * * 函数名称: void Loading(String Title)
     * * 功能说明：进度条加载
     ********************************************************************************************************/
    public void Loading(String Title) {
        pDialog = new SweetAlertDialog(this, SweetAlertDialog.PROGRESS_TYPE);
        pDialog.getProgressHelper().setBarColor(Color.parseColor("#A5DC86"));
        pDialog.setTitleText(Title);
        pDialog.setCancelable(true);
        pDialog.show();
    }
}
