package com.example.heat_itv2;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

Button scanButton,sendButton;
Button startButton,stopButton;
ListView scanListView/*=null*/;
TextView statusTextView,dataReceivedTextView,textView;
EditText sendDataEditText;
EditText inputTemperatureEditText;
TextView seekBarValueTextView;
SeekBar keepHeatingSeekBar;
long timeToKeepWarm;

private CountDownTimer mCountDownTimer;
boolean mTimerRunning = false;

static final UUID myUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
BluetoothAdapter bluetoothAdapter=BluetoothAdapter.getDefaultAdapter();
BluetoothMessaging bluetoothMessaging;

BluetoothDevice[] bluetoothArray;
BluetoothSocket bluetoothSocket;

NotificationCompat.Builder builder=new NotificationCompat.Builder(this).setContentTitle("ALARM");

static final int STATE_CONNECTING = 1;
static final int STATE_CONNECTED = 2;
static final int STATE_CONNECTION_FAILED = 3;
static final int STATE_MESSAGE_RECEIVED = 4;

int REQUEST_ENABLE_BLUETOOTH = 1 ;

private volatile boolean stopThread1=false, stopThread2=false, toRing=false;
@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    findViewByIdes();

    if(!bluetoothAdapter.isEnabled()){
        Intent enableIntent = new Intent(bluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableIntent,REQUEST_ENABLE_BLUETOOTH);
    }
    implementListeners();

    Uri alarmSound= RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

    Intent notificationIntent=new Intent(this,MainActivity.class);

    PendingIntent contentIntent=PendingIntent.getActivity(this,0,notificationIntent,PendingIntent.FLAG_UPDATE_CURRENT);

    builder.setContentIntent(contentIntent);
    builder.setAutoCancel(true);
    builder.setLights(Color.BLUE,500,500);
    long [] pattern={500,500,500,500,500,500,500,500,500};
    builder.setVibrate(pattern);
    builder.setSound(alarmSound);
    builder.setStyle(new NotificationCompat.InboxStyle());


    /*BroadcastReceiver myReceiver1 = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if(toRing){
                System.out.println("Zagotowane!");
            }
            else {
                System.out.println("Też zagotowane!");
            }


        }
    };*/
    //registerReceiver(myReceiver1, new IntentFilter());
   // inputTemperatureEditText.onEditorAction(EditorInfo.IME_ACTION_DONE);
   // closeKeyboard();
}



 Handler handler = new Handler(new Handler.Callback() {
     @Override
     public boolean handleMessage(@NonNull Message message) {
         switch (message.what)
         {
             case STATE_CONNECTING:
                 statusTextView.setText("Connecting");
                 break;
             case STATE_CONNECTED:
                 statusTextView.setText("Connected");
                 HeatingRunnable runnable = new HeatingRunnable(returnActualTemp(), 2);
                 new Thread(runnable).start();
                 break;
             case STATE_CONNECTION_FAILED:
                 statusTextView.setText("Connection failed");
                 break;
             case STATE_MESSAGE_RECEIVED:
                 double last_temp;
                 byte[] readBuff = (byte[]) message.obj;
                 String tempMsg = new String(readBuff,0,message.arg1);
                // System.out.print("tempMsg: ");
                 //System.out.println(tempMsg);
                 try {
                     last_temp = Double.valueOf(tempMsg);
                     dataReceivedTextView.setText(String.valueOf(last_temp));
                   //  System.out.print("Last temp: ");
                   //  System.out.println(last_temp);
                 }catch(Exception e)
                 { }
                 break;
         }
         return true;
     }
 });
private class BluetoothServices extends Thread{
 private BluetoothDevice bluetoothDevice;

 public BluetoothServices(BluetoothDevice device){
     bluetoothDevice = device;
     try {
         bluetoothSocket = device.createInsecureRfcommSocketToServiceRecord(myUUID);

     }catch (IOException e){
         e.printStackTrace();
     }
 }

    public void run() {
        try {
            bluetoothSocket.connect();
            Message message = Message.obtain();
            message.what=STATE_CONNECTED;
            handler.sendMessage(message);
        } catch (IOException e) {
            e.printStackTrace();
            Message message = Message.obtain();
            message.what=STATE_CONNECTION_FAILED;
            handler.sendMessage(message);
        }
    }
}


private void findViewByIdes(){
scanButton = findViewById(R.id.scanButton);
scanListView = findViewById(R.id.scanListView);
//scanListView = new ListView(this);
textView=new TextView(this);
statusTextView = findViewById(R.id.statusTextView);
dataReceivedTextView = findViewById(R.id.dataReceivedTextView);
//sendButton = findViewById(R.id.sendButton);
//sendDataEditText = findViewById(R.id.sendDataEditText);
startButton = findViewById(R.id.startButton);
stopButton = findViewById(R.id.stopButton);
inputTemperatureEditText = findViewById(R.id.inputTemperatureEditText);
//inputTemperatureEditText.setInputType(InputType.TYPE_NULL);
seekBarValueTextView = findViewById(R.id.seekBarValueTextView);
keepHeatingSeekBar = findViewById(R.id.keepHeatingSeekBar);

    }

private void implementListeners() {

scanButton.setOnClickListener(new View.OnClickListener() {
    @Override
    public void onClick(View view) {
        bluetoothAdapter.startDiscovery();
        Set<BluetoothDevice> bluetooth = bluetoothAdapter.getBondedDevices();
        String [] strings = new String[bluetooth.size()];
        bluetoothArray = new BluetoothDevice[bluetooth.size()];
        int index = 0;
        if(bluetooth.size()>0){
            for(BluetoothDevice bluetoothDevice : bluetooth){
                bluetoothArray[index] = bluetoothDevice;
                strings[index] = bluetoothDevice.getName();
                index++;
            }
            ArrayAdapter<String> arrayAdapter = new ArrayAdapter<String>(getApplicationContext()/*MainActivity.this,R.layout.bluetoothdialoglistview,R.id.textView,*/,android.R.layout.simple_list_item_1, strings);
            scanListView.setAdapter(arrayAdapter);

    }
/*
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setCancelable(true);
        builder.setPositiveButton("CONNECT",null);
        builder.setView(scanListView);
        AlertDialog dialog=builder.create();
        if(textView.getParent()!=null){
            ((ViewGroup)textView.getParent()).removeView(textView);
        }
        dialog.show();*/
    }
});

    scanListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
            BluetoothServices bluetoothServices = new BluetoothServices(bluetoothArray[i]);
            bluetoothServices.start();
            bluetoothMessaging = new BluetoothMessaging(bluetoothSocket);
            bluetoothMessaging.start();
            statusTextView.setText("Connecting");
        }
    });
/*    sendButton.setOnClickListener(new View.OnClickListener() { // do wyrzucenia
        @Override
        public void onClick(View view) {
            String string = String.valueOf(sendDataEditText.getText());
            bluetoothMessaging.write(string.getBytes());
        }
    });*/
    seekBarMinute();
    startButton.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View view) {
        String inputTemp = inputTemperatureEditText.getText().toString();
        try {
            double checker = Double.valueOf(inputTemp);
            if(checker < 0 || checker > 90){
                Toast.makeText(MainActivity.this,"Input temperature should be between 0 and 90 degrees of Celsius", Toast.LENGTH_SHORT).show();
                inputTemperatureEditText.setText("");
            }else {
                HeatingRunnable runnable = new HeatingRunnable(checker, 1);
                new Thread(runnable).start();
                inputTemperatureEditText.setText("");
            }
        }catch (Exception e)
        {
            e.printStackTrace();
            Toast.makeText(MainActivity.this, "Invalid input", Toast.LENGTH_SHORT).show();
            inputTemperatureEditText.setText("");
        }
        }
    });
    stopButton.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            if(stopThread2){
                stopThread2 = false;
                stopThread1 = true;
                mTimerRunning = false;
                turnOffHeater();
                HeatingRunnable runnable = new HeatingRunnable(22,2);
                new Thread(runnable).start();
            }
            if(!stopThread1){
                stopThread1 = true;
                stopThread2 = false;
                mTimerRunning = false;
                turnOffHeater();
                HeatingRunnable runnable = new HeatingRunnable(22,2);
                new Thread(runnable).start();
            }

        }
    });



    }
private class BluetoothMessaging extends Thread
{
    private final BluetoothSocket bluetoothSocket;
    private final InputStream inputStream;
    private final OutputStream outputStream;

    public BluetoothMessaging(BluetoothSocket socket){
        bluetoothSocket = socket;
        InputStream tempIn = null;
        OutputStream tempOut = null;

        try {
            tempIn = bluetoothSocket.getInputStream();
            tempOut = bluetoothSocket.getOutputStream();
        } catch (IOException e) {
            e.printStackTrace();
        }
        inputStream=tempIn;
        outputStream=tempOut;
    }

    @Override
    public void run() {
        byte[] buffer = new byte[16];
        int bytes;
        while(true){
            try {
                bytes = inputStream.read(buffer);
                handler.obtainMessage(STATE_MESSAGE_RECEIVED, bytes, -1, buffer).sendToTarget();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    public void write(byte[] bytes){
        try {
            outputStream.write(bytes);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
  }
  class HeatingRunnable implements Runnable {
    double temperatureGoal, actualTemperature;
    int option;
    HeatingRunnable(double temp, int option){
        this.temperatureGoal = temp;
        this.option = option;
    }

      @Override
      public void run() {
        switch (option) {
            case 1:
                toRing=false;
                stopThread1 = false;
                stopThread2 = true;
                turnOnHeater();
                while (temperatureGoal > actualTemperature && !stopThread1) {
                    actualTemperature = returnActualTemp();
                    try { Thread.sleep(1000); } catch (Exception e) { e.printStackTrace(); }
                }
                if(timeToKeepWarm>0 && !stopThread1){
                    stopThread1=true;
                    stopThread2=true;
                    HeatingRunnable runnable = new HeatingRunnable(temperatureGoal,3);
                    new Thread(runnable).start();
                }
                else if(stopThread2){
                    toRing=true;
                    //sendBroadcast(new Intent().setAction("Warmed"));

                    NotificationManager manager=(NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
                    manager.notify(1,builder.build());

                    stopThread1 = true;
                    stopThread2 = false;
                    HeatingRunnable runnable = new HeatingRunnable(temperatureGoal,2);
                    new Thread(runnable).start();
                }
                turnOffHeater();
                break;
            case 2:
                toRing=false;
                stopThread1 = true;
                stopThread2 = false;
                while(option == 2 && !stopThread2) {
                    try { Thread.sleep(1000); } catch (Exception e) { e.printStackTrace(); }
                    actualTemperature = returnActualTemp();
                }
                break;
            case 3:
                toRing=false;
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        startTimer();
                        System.out.println("Timer wystarowal");
                    }
                });
                while (timeToKeepWarm>0 && mTimerRunning){
                    System.out.println("Podtrzymuje temperature a temperatura oczekiwana to: ");
                    System.out.print(temperatureGoal);
                    try { Thread.sleep(1000); } catch (Exception e) { e.printStackTrace(); }
                   if(temperatureGoal > actualTemperature){
                       turnOnHeater();
                   }else {
                       turnOffHeater();
                   }
                    actualTemperature = returnActualTemp();
                }
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        stopTimer();
                    }
                });
                stopThread1=true;
                stopThread2=false;
                //sendBroadcast(new Intent().setAction("Kept"));

                NotificationManager manager=(NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
                manager.notify(1,builder.build());

                turnOffHeater();
                HeatingRunnable runnable = new HeatingRunnable(temperatureGoal,2);
                new Thread(runnable).start();
                toRing=true;
                break;
        }
      }
  }
  private void turnOnHeater(){
    bluetoothMessaging.write("a".getBytes());
  }
  private void turnOffHeater(){
    bluetoothMessaging.write("b".getBytes());
  }
  private double returnActualTemp(){
    Double actualTemp = 0.0;
    bluetoothMessaging.write("c".getBytes());
    try {
        actualTemp=Double.valueOf(dataReceivedTextView.getText().toString());
    }catch (Exception e){
        e.printStackTrace();
    }
    System.out.print("actualTemp: ");
    System.out.println(actualTemp);
    return actualTemp;
  }
    public void seekBarMinute(){
    int max_value = 60;
    keepHeatingSeekBar.setMax(max_value);
        keepHeatingSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            String progressValue;
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean b) {
                progressValue = Integer.toString(progress);
                seekBarValueTextView.setText(progressValue + "min");
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                seekBarValueTextView.setText(progressValue+" min");
                timeToKeepWarm=Integer.valueOf(progressValue);
            }
        });
    }
    private void startTimer(){
    mCountDownTimer = new CountDownTimer(timeToKeepWarm = timeToKeepWarm * 60 * 1000, 1000) {
        @Override
        public void onTick(long millisUntilFinished) {
            timeToKeepWarm = millisUntilFinished;
            updateCountDownText();
        }

        @Override
        public void onFinish() {
            mTimerRunning = false;
        }
    }.start();
    mTimerRunning = true;
    }
    private void stopTimer(){
    mCountDownTimer.cancel();
    mTimerRunning = false;
    }
    private void updateCountDownText(){
        int minutes = (int) timeToKeepWarm/1000/60;
        int seconds = (int) timeToKeepWarm/1000%60;
        String timeLeftFormatted = String.format(Locale.getDefault(),"%02d:%02d",minutes,seconds);
        seekBarValueTextView.setText(timeLeftFormatted);
    }
  /*  private void closeKeyboard(){ //
    View view = this.getCurrentFocus();
    if(view != null){
        InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(view.getWindowToken(),0);
    }
    }*/
}
