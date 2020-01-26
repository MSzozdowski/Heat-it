package com.example.heat_itv2;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import android.app.AlertDialog;
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
import android.text.InputType;
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
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    Button scanButton;
    Button startButton,stopButton;
    ListView scanListView;
    TextView statusTextView,dataReceivedTextView,textView,heaterStatusTextView;
    EditText inputTemperatureEditText;
    TextView seekBarValueTextView;
    SeekBar keepHeatingSeekBar;

    long timeToKeepWarm;
    double lastTemp;
    private CountDownTimer mCountDownTimer;
    boolean mTimerRunning = false;
    String CHANNEL="CHANNEL";
    String [] strings;
    int REQUEST_ENABLE_BLUETOOTH = 1 ;
    private volatile boolean stopThread1=false, stopThread2=false;

    static final UUID myUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    BluetoothAdapter bluetoothAdapter=BluetoothAdapter.getDefaultAdapter();
    BluetoothMessaging bluetoothMessaging;
    BluetoothDevice[] bluetoothArray;
    BluetoothSocket bluetoothSocket;

    NotificationCompat.Builder builder=new NotificationCompat.Builder(this,CHANNEL)
            .setContentTitle("ALARM");

    Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    findViewByIdes();

    if(!bluetoothAdapter.isEnabled()) {
        Intent enableIntent = new Intent(bluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableIntent,REQUEST_ENABLE_BLUETOOTH);
    }


    Uri alarmSound= RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
    Intent notificationIntent=new Intent(this,MainActivity.class);
    PendingIntent contentIntent=PendingIntent.getActivity(this,
            0,notificationIntent,PendingIntent.FLAG_UPDATE_CURRENT);
    builder.setContentIntent(contentIntent);
    builder.setAutoCancel(true);
    builder.setLights(Color.BLUE,500,500);
    long [] pattern={500,500,500,500,500,500,500,500,500};
    builder.setVibrate(pattern);
    builder.setSound(alarmSound);
    builder.setStyle(new NotificationCompat.InboxStyle()
    .addLine("Your water is ready!")
    .setBigContentTitle("HeatIt"));

    scanDevices();

    ArrayAdapter<String> arrayAdapter = new ArrayAdapter<String>(getApplicationContext(),R.layout.list_row, strings);
    scanListView.setAdapter(arrayAdapter);
    AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(MainActivity.this);
    dialogBuilder.setCancelable(true);
    dialogBuilder.setTitle("Choose your device");
    dialogBuilder.setView(scanListView);
    final AlertDialog dialog = dialogBuilder.create();

    scanButton.setOnClickListener(new View.OnClickListener() {
    @Override
    public void onClick(View view) {
        if(!statusTextView.getText().toString().equals("Connected")){
            dialog.show();
        }
        else{
            Toast.makeText(getApplicationContext(),"You are already connected",Toast.LENGTH_SHORT).show();
        }
    }
    });

    scanListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {

        if(!statusTextView.getText().toString().equals("Connected")) {
            BluetoothServices bluetoothServices = new BluetoothServices(bluetoothArray[i]);
            bluetoothServices.start();
            bluetoothMessaging = new BluetoothMessaging(bluetoothSocket);
            bluetoothMessaging.start();
            statusTextView.setText("Connecting");
        }
        else{
            Toast.makeText(getApplicationContext(),"You are already connected!",Toast.LENGTH_SHORT).show();
        }
        dialog.dismiss();
    }
    });
    implementListeners();
    }

    private class BluetoothServices extends Thread{
        private BluetoothDevice bluetoothDevice;

        public BluetoothServices(BluetoothDevice device){
            bluetoothDevice = device;
            try {bluetoothSocket = device.createInsecureRfcommSocketToServiceRecord(myUUID); }
            catch (IOException e){ e.printStackTrace();}
        }

        public void run() {
            try {
                bluetoothSocket.connect();
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        statusTextView.setText("Connected");
                        HeatingRunnable runnable = new HeatingRunnable(returnActualTemp(), 2);
                        new Thread(runnable).start();
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
                handler.post(new Runnable() {
                    @Override
                        public void run() {
                            statusTextView.setText("Connection Failed");
                        }
                    });
            }
        }
    }

    private class BluetoothMessaging extends Thread {
        private final BluetoothSocket bluetoothSocket;
        private final InputStream inputStream;
        private final OutputStream outputStream;

        public BluetoothMessaging(BluetoothSocket socket){
            bluetoothSocket = socket;
            InputStream dataIn = null;
            OutputStream dataOut = null;

            try {
                dataIn = bluetoothSocket.getInputStream();
                dataOut = bluetoothSocket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }
            inputStream=dataIn;
            outputStream=dataOut;
        }

        @Override
        public void run() {
            byte[] buffer = new byte[16];
            int bytes;
            while(true){
                try {
                bytes = inputStream.read(buffer);
                final String getData = new String(buffer,0,bytes);
                handler.post(new Runnable() {
                    @Override
                        public void run() {
                            try {
                                lastTemp = Double.valueOf(getData);
                                dataReceivedTextView.setText(String.valueOf(lastTemp));
                            }catch(Exception e) { }
                        }
                });
                } catch (IOException e) { e.printStackTrace(); }
            }
        }

        public void write(byte[] bytes){
            try { outputStream.write(bytes); } catch (IOException e) { e.printStackTrace(); }
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
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            heaterStatusTextView.setText("Heats");
                        }
                    });
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
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            heaterStatusTextView.setText("Idle");
                        }
                    });
                    stopThread1 = true;
                    stopThread2 = false;
                    while(option == 2 && !stopThread2) {
                        try { Thread.sleep(1000); } catch (Exception e) { e.printStackTrace(); }
                        actualTemperature = returnActualTemp();
                    }
                    break;
                case 3:
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            startTimer();
                            heaterStatusTextView.setText("Keeps temperature");
                            System.out.println("Timer wystarowal");
                            System.out.println(timeToKeepWarm);
                            mTimerRunning=true;
                        }
                    });
                    while (timeToKeepWarm > 0 && mTimerRunning){
                        try { Thread.sleep(1000); } catch (Exception e) { e.printStackTrace(); }
                        if(temperatureGoal >  actualTemperature){
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
                            seekBarValueTextView.setText("0 min");
                            timeToKeepWarm=0;
                            keepHeatingSeekBar.setProgress(0);
                        }
                    });
                    turnOffHeater();
                    if(stopThread2) {
                        HeatingRunnable runnable = new HeatingRunnable(temperatureGoal, 2);
                        new Thread(runnable).start();
                        NotificationManager manager=(NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
                        manager.notify(1,builder.build());
                    }
                    stopThread1=true;
                    stopThread2=false;
                    break;
            }
        }
    }

    private void findViewByIdes(){
        scanButton = findViewById(R.id.scanButton);
        scanListView = new ListView(this);
        textView=new TextView(this);
        statusTextView = findViewById(R.id.statusTextView);
        dataReceivedTextView = findViewById(R.id.dataReceivedTextView);
        startButton = findViewById(R.id.startButton);
        stopButton = findViewById(R.id.stopButton);
        inputTemperatureEditText = findViewById(R.id.inputTemperatureEditText);
        inputTemperatureEditText.setInputType(InputType.TYPE_NULL);
        seekBarValueTextView = findViewById(R.id.seekBarValueTextView);
        keepHeatingSeekBar = findViewById(R.id.keepHeatingSeekBar);
        heaterStatusTextView = findViewById(R.id.heaterStatus);
    }

    private void implementListeners() {
        seekBarMinute();

        inputTemperatureEditText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                inputTemperatureEditText.setInputType(InputType.TYPE_CLASS_NUMBER);
            }
        });

        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (bluetoothSocket != null) {
                    String inputTemp = inputTemperatureEditText.getText().toString();
                    try {
                        double checker = Double.valueOf(inputTemp);
                        if (checker < lastTemp || checker > 90) {
                            Toast.makeText(MainActivity.this, "Input temperature should be between actual temperature and 90 degrees of Celsius", Toast.LENGTH_LONG).show();
                            inputTemperatureEditText.setText("");
                        } else {
                            HeatingRunnable runnable = new HeatingRunnable(checker, 1);
                            new Thread(runnable).start();
                            System.out.println("Ustawiony czas timera na ");
                            System.out.print(timeToKeepWarm);
                            inputTemperatureEditText.setText("");
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(MainActivity.this, "Invalid input", Toast.LENGTH_SHORT).show();
                        inputTemperatureEditText.setText("");
                    }
                }else {
                    Toast.makeText(getApplicationContext(),"Connect your device!", Toast.LENGTH_SHORT).show();
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
                    seekBarValueTextView.setText("0 min");
                    keepHeatingSeekBar.setProgress(0);
                    turnOffHeater();
                    HeatingRunnable runnable = new HeatingRunnable(22,2);
                    new Thread(runnable).start();
                }
            }
        });
    }

    private void scanDevices(){
        Set<BluetoothDevice> bluetooth = bluetoothAdapter.getBondedDevices();
        strings = new String[bluetooth.size()];
        bluetoothArray = new BluetoothDevice[bluetooth.size()];
        int index = 0;
        if(bluetooth.size()>0){
            for(BluetoothDevice bluetoothDevice : bluetooth){
                bluetoothArray[index] = bluetoothDevice;
                strings[index] = bluetoothDevice.getName();
                index++;
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
        int max_value = 30;
        keepHeatingSeekBar.setMax(max_value);
        keepHeatingSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
        String progressValue;
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean b) {
            progressValue = Integer.toString(progress);
            seekBarValueTextView.setText(progressValue + "min");
        }
        @Override
        public void onStartTrackingTouch(SeekBar seekBar) { }
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
        }}.start();
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
}
