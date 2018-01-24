package com.mcgdemo.www;

import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/**
 * Created by yy on 2016/7/12.
 */
public class TcpSocket {
    private MainActivity mContext = null;
    public WifiManager wifiManager;                                                                 // 定义WifiManager对象
    public WifiInfo wifiInfo;                                                                       // 定义WifiInfo对象
    //----------TCP通信--------//
    public boolean isConnecting = false; 												            // 连接处于断开
    private Thread mThreadClient = null; 												            // TCP中断进行连接
    private Socket mSocketClient = null; 												            // Socket通信
    public InputStream  inyy;
    public OutputStream outyy;
    public String recv_Data="";															            //接收到soctet通信数据
    byte[] rev_buf=new byte[256];														            //数据接收BUFF

    String TcpSocket_IP="";
    public TcpSocket(MainActivity context, String IP) {
        mContext = context;
        this.TcpSocket_IP = IP;
        InitWifiSetup();
        Tcp_Socket_Connect();
    }
    //----------------WIFI状态信息控制-----------------------------//
    public void InitWifiSetup() {
        wifiManager = (WifiManager) mContext.getSystemService(mContext.WIFI_SERVICE);               // 取得WifiManager对象
        wifiInfo = wifiManager.getConnectionInfo();                                                 // 取得WifiInfo对象
    }
    //##################################################################################//
    //函数名称: TCP数据处理主线程
    //函数说明: 处理TCP Socket线程消息
    // ##################################################################################//
    Handler mHandler = new Handler()
    {
        public void handleMessage(Message msg)
        {
            super.handleMessage(msg);
            //--------------------处理服务器返回数据-------------------//
            if(msg.what == 0)
            {//处理接收数据  recv_Data
                //#################处理打印机数据######################//
            }
            else if(msg.what == 1)
            {//TCP连接异常
                mContext.DeviceWifiConnectState = false;
                mContext.showToast("轨道服务基站连接失败，请重新尝试!", Toast.LENGTH_SHORT);
            }
            else if(msg.what == 2)
            {//TCP连接成功
                mContext.DeviceWifiConnectState = true;
                mContext.showToast("轨道服务基站连接成功!",Toast.LENGTH_SHORT);
            }
        }
    };
    // ##################################################################################//
    // 函数名称: TCP主线程
    // 函数说明: 处理TCP Socket线程消息
    // ##################################################################################//
    private Runnable mRunnable = new Runnable() {
        public void run() {
            // -----------------------------连接server----------------------------------//
            try {
                // ------------------------连接服务器-----------------------------------//
                mSocketClient = new Socket(TcpSocket_IP,7000);
                // -----------------------取得输入、输出流-------------------------------//
                inyy = mSocketClient.getInputStream();
                outyy = mSocketClient.getOutputStream();

                Message msg = new Message();
                msg.what = 2;                                                                       // 服务器连接成功
                mHandler.sendMessage(msg);
            } catch (Exception e) {                                                                 // 连接异常
                Message msg = new Message();
                msg.what = 1;
                mHandler.sendMessage(msg);
                return;
            }
            // -----------------------------接受sercer数据-------------------------------//
            byte[] buffer = new byte[256];
            while (isConnecting) {
                try {
                    if (inyy.read(buffer) > 0) {
                        rev_buf = buffer;
                        Message msg = new Message();
                        msg.what = 0;
                        mHandler.sendMessage(msg);
                    }
                } catch (Exception e) {
                    recv_Data = "接收异常:" + e.getMessage(); // 消息换行
                    Message msg = new Message();
                    msg.what = 0;
                    mHandler.sendMessage(msg);
                }
            }
        }
    };
    //##################################################################################//
    //函数名称: void Tcp_Socket_Connect()
    //函数说明: TCP socket建立连接
    // ##################################################################################//
    public void Tcp_Socket_Connect() {
        isConnecting = true;
        mThreadClient = new Thread(mRunnable);
        mThreadClient.start();
    }
    // ###############################################//
    // 函数名称: public void Tcp_Socket_DisConnect()
    // 函数说明: 关闭TCP socket连接
    // ###############################################//
    public void Tcp_Socket_DisConnect() {
        isConnecting = false;										                                // 关闭TCP连接
        try {
            if (mSocketClient != null) {
                mSocketClient.close();
                mSocketClient = null;
                inyy.close();
                outyy.close();
            }
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        mThreadClient.interrupt();                                                                  // 中断TCP协议连接
    }
    // ###############################################//
    // 函数名称: void Tcp_SendByte(byte[] ss)
    // 函数说明: 发送BYTE数据
    // ###############################################//
    public void Tcp_SendByte(byte[] ss) {
        try {
            outyy.write(ss);
            outyy.flush();
        } catch (Exception e) {
            Toast.makeText(mContext, "发送异常:" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
