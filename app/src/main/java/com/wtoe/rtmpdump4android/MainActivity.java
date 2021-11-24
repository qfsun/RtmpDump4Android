package com.wtoe.rtmptest;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Camera;
import android.os.Bundle;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private CameraHelper cameraHelper;

    private Button btn_start,btn_switch;
    private EditText et_rtmp_url,bitrate;
    private Spinner size;
    private SurfaceView sv_camera_front;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        initView();
    }

    private void initView() {
        ArrayList<String> list=new ArrayList<String>();
        list.add("1088x1920");
        list.add("720x1280");
        list.add("480x640");
        list.add("240x320");
        final SpinnerAdapter adapter = new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,list);
        sv_camera_front = findViewById(R.id.sv_camera_front);
        bitrate= findViewById(R.id.bitrate);
        size= findViewById(R.id.size);
        size.setAdapter(adapter);
        size.setSelection(2);
        et_rtmp_url = findViewById(R.id.et_rtmp_url);
        btn_start = findViewById(R.id.btn_start);
        btn_switch = findViewById(R.id.btn_switch);
        SharedPreferences sp = this.getSharedPreferences("testContextSp", Context.MODE_PRIVATE);
        String rtmpUrl = sp.getString("rtmp", "");
        if (rtmpUrl.isEmpty()){
            rtmpUrl = "rtmp://192.168.3.140/live/test";
        }
        if (et_rtmp_url.getText().toString().isEmpty()){
            et_rtmp_url.setText(rtmpUrl);
        }
        btn_start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (btn_start.getText().equals("开始")){
                    startCameraPush();
                    btn_start.setText("停止");
                }else if (btn_start.getText().equals("停止")){
                    stopCameraPush();
                    btn_start.setText("开始");
                }
            }
        });

        btn_switch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Constants.VIDEO_CAMERA_ID == Camera.CameraInfo.CAMERA_FACING_FRONT){
                    stopCameraPush();
                    Constants.VIDEO_CAMERA_ID = Camera.CameraInfo.CAMERA_FACING_BACK;
                    startCameraPush();
                }else {
                    stopCameraPush();
                    Constants.VIDEO_CAMERA_ID = Camera.CameraInfo.CAMERA_FACING_FRONT;
                    startCameraPush();
                }
            }
        });

        size.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                //这个方法里可以对点击事件进行处理
                switch (i){
                    case 0:
                        Constants.VIDEO_WIDTH = 1920;
                        Constants.VIDEO_HEIGHT = 1088;
                        break;
                    case 1:
                        Constants.VIDEO_WIDTH = 1280;
                        Constants.VIDEO_HEIGHT = 720;
                        break;
                    case 2:
                        Constants.VIDEO_WIDTH = 640;
                        Constants.VIDEO_HEIGHT = 480;
                        break;
                    case 3:
                        Constants.VIDEO_WIDTH = 320;
                        Constants.VIDEO_HEIGHT = 240;
                        break;
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });
    }

    private void startCameraPush(){
        if (cameraHelper == null){
            String rtmpUrl = et_rtmp_url.getText().toString();
            if (rtmpUrl.isEmpty() || !rtmpUrl.startsWith("rtmp://")){
                Toast.makeText(this,"请输入正确rtmp地址",Toast.LENGTH_SHORT).show();
                return;
            }
            et_rtmp_url.setEnabled(false);
            SharedPreferences sp = this.getSharedPreferences("testContextSp", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sp.edit();//获取Editor
            //得到Editor后，写入需要保存的数据
            editor.putString("rtmp", rtmpUrl);
            editor.commit();//提交修改
            cameraHelper = new CameraHelper(sv_camera_front,rtmpUrl);
        }
    }

    private void stopCameraPush(){
        if (cameraHelper != null){
            cameraHelper.onDestroy();
            cameraHelper = null;
            et_rtmp_url.setEnabled(true);
        }
    }

    @Override
    protected void onDestroy() {
        stopCameraPush();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        super.onDestroy();
    }
}
