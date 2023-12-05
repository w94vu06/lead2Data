package com.example.lead2data;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StrictMode;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.ScatterChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.CombinedData;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.data.ScatterData;
import com.github.mikephil.charting.data.ScatterDataSet;
import com.obsez.android.lib.filechooser.ChooserDialog;

import org.apache.commons.lang3.ArrayUtils;
import org.tensorflow.lite.Interpreter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;


import tw.com.jchang.geniiecgbt.decompNDK;


public class MainActivity extends AppCompatActivity {
    private TextView textOutput;
    private TextView textOutput2;
    private Button btnFileInput;
    private Button btnFileOutput;
    private Button btnFileOutput2;

    //處理資料
    private ProcessData processData;
    private float[][][] reshapedArray;
    private float[][][] reshapedArray2;
    private ReadCSVThread readCSVThread;
    private BpmCountThread bpmCountThread;
    //最大最小
    private float minValue = Float.MAX_VALUE;
    private float maxValue = 0;
    private float minValueA = Float.MAX_VALUE;
    private float maxValueA = 0;
    private float minValueB = Float.MAX_VALUE;
    private float maxValueB = 0;
    //TFLite
    private String checkSignal;
    private Interpreter interpreter;
    private final String mModelPath = "mymodel.tflite";
    private final String mModelPath_loose = "mymodel_loose.tflite";

    //畫ECG

    private LineChart mLineChart;
    private LineChart mLineChart2;
    private LineChart mLineChart3;
    Float minFloatValue;
    Float minFloatValue2;
    double bpm;
    //FileChooser
    ChooserDialog chooserDialog;
    private String chooserFileName;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        try {
            initItem();
            initPermission();
        } catch (Exception e) {
            e.printStackTrace();
        }
        //關閉標題列
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }

    }

    @Override
    protected void onResume() {
        super.onResume();
        initChooser();
        try {
            initInterpreter();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void initItem() {

        mLineChart = findViewById(R.id.chart_line);
        mLineChart2 = findViewById(R.id.chart_line2);
        mLineChart3 = findViewById(R.id.chart_line3);
        textOutput = findViewById(R.id.textOutput);
//        textOutput2 = findViewById(R.id.textOutput2);
        btnFileInput = findViewById(R.id.btnFileInput);
        btnFileOutput = findViewById(R.id.btnFileOutput);
        btnFileOutput2 = findViewById(R.id.btnFileOutput2);
    }


    /**
     * 檢查權限
     */
    public void initPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1000);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()) {
        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage("本程式需要您同意允許存取所有檔案權限");
            builder.setPositiveButton("同意", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivity(intent);
                }
            });
            builder.show();
        }


    }


    /**
     * 檔案選擇器
     */
    public void initChooser() {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                String externalStorageDirectory = Environment.getExternalStorageDirectory().getAbsolutePath();
                chooserDialog = new ChooserDialog(MainActivity.this)
                        .withStartFile(externalStorageDirectory)
                        .withChosenListener(new ChooserDialog.Result() {
                            @Override
                            public void onChoosePath(String dir, File dirFile) {
                                if (dir.endsWith(".csv")) {
                                    openCSV(dir);
                                }
                                if (dir.endsWith(".CHA")) {
                                    readCHA(dir);
                                }
                                if (dir.endsWith(".lp4")) {
                                    readLP4(dir);
                                }
                                File file = new File(dir);
                            }
                        })

                        .withOnCancelListener(new DialogInterface.OnCancelListener() {
                            @Override
                            public void onCancel(DialogInterface dialog) {
                                dialog.cancel();

                            }
                        });
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        btnFileInput.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                chooserDialog.build();
                                chooserDialog.show();
                            }
                        });

                    }
                });
            }
        });
        thread.start();
        initPredict();
    }

    /**
     * 直譯器
     */
    public void initInterpreter() throws IOException {
        Interpreter.Options options = new Interpreter.Options();
        options.setNumThreads(4);
        options.setUseNNAPI(true);
        /** 換模 */
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
//                    interpreter = new Interpreter(loadModelFile(getAssets(), mModelPath), options);
                    interpreter = new Interpreter(loadModelFile(getAssets(), mModelPath_loose), options);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public void initPredict() {
        /** 預測一段 */
        btnFileOutput.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                long startTime = System.currentTimeMillis();
                float[] result = doInference(reshapedArray);
                for (float f : result) {
                    if (f < 0.1) {
                        checkSignal = "壞訊號";
                    } else {
                        checkSignal = "好訊號";
                    }
                }
                runOnUiThread(() -> {
                    long endTime = System.currentTimeMillis();
                    float elapsedTime = (endTime - startTime) / 1000;
                    textOutput.setText(Arrays.toString(result) + "\n" + checkSignal + "\n使用" + elapsedTime + "秒");
                });
            }
        });
        /** 預測兩段 */
        btnFileOutput2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                long startTime = System.currentTimeMillis();
                float[] result = doInference(reshapedArray);
                float[] result2 = doInference(reshapedArray2);
                float[] combined = ArrayUtils.addAll(result, result2);
                boolean isGood = true;
                for (float f : combined) {
                    if (f < 0.1) {
                        isGood = false;
                    }
                    break;
                }
                if (isGood) {
                    checkSignal = "好訊號";
                } else {
                    checkSignal = "壞訊號";
                }
                runOnUiThread(() -> {
                    long endTime = System.currentTimeMillis();
                    float elapsedTime = (endTime - startTime) / 1000;
                    textOutput.setText(Arrays.toString(combined) + "\n" + checkSignal + "\n使用" + elapsedTime + "秒");
                });
            }
        });
    }

    /**
     * 預測
     */
    public float[] doInference(float[][][] data) {
        float[][] outputs = new float[1][1];
        interpreter.run(data, outputs);
        float[] outputArray = new float[1];
        outputArray[0] = outputs[0][0];
        return outputArray;
    }

    /**
     * 讀LP4
     */
    private void readLP4(String fileName) {
        tw.com.jchang.geniiecgbt.decompNDK decompNDK = new decompNDK();
        decompNDK.decpEcgFile(fileName);
        int y = fileName.length();
        /** 將副檔名改為.CHA */
        String j = fileName.substring(0, y - 4);
        fileName = j + ".CHA";
        textOutput.setText("匯入資料成功");
        readCHA(fileName);
    }

    /**
     * 讀CHA
     */
    public void readCHA(String fileName) {
        if (fileName != "") {
            processData = new ProcessData(fileName);
            try {
                processData.run();
                processData.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            textOutput.setText("匯入資料成功");
            List<Float> chaFloatData = new ArrayList<>(processData.finalCHAData);
            setReshapedArray(chaFloatData);
//            setLineChart(chaFloatData);
        }
    }

    /**
     * 讀CSV
     */
    private void openCSV(String fileName) {
        if (fileName != "") {
            readCSVThread = new ReadCSVThread(fileName);
            readCSVThread.run();
            List<Float> csvFloatData = new ArrayList<>(readCSVThread.csvFloat);
            textOutput.setText("匯入資料成功");
//            setLineChart(csvFloatData);
            bpmCount(csvFloatData);
        } else {
            textOutput.setText("PLS INPUT!!");
        }
    }

    /**
     * reshapeData
     */
    private void setReshapedArray(List<Float> floatData) {
        if (floatData.size() > 24000) {
            int extraTime = (floatData.size() - 24000) / 2;
            floatData = floatData.subList(extraTime, floatData.size() - extraTime);
            floatData = floatData.subList(0, 24000);
        }
        float[] inputArray = new float[floatData.size()];
        for (int i = 0; i < floatData.size(); i++) {
            inputArray[i] = floatData.get(i);
        }
        float[] rawSignalFirst = Arrays.copyOfRange(inputArray, 0, 12000);
        float[] rawSignalSecond = Arrays.copyOfRange(inputArray, 12000, 24000);
        reshapedArray = new float[1][12000][1];
        for (int i = 0; i < 12000; i++) {
            reshapedArray[0][i][0] = rawSignalFirst[i];
        }
        reshapedArray2 = new float[1][12000][1];
        for (int i = 1; i < 12000; i++) {
            reshapedArray2[0][i][0] = rawSignalSecond[i];
        }
        bpmCount(floatData);
    }

    private void bpmCount(List<Float> dataList) {
        bpmCountThread = new BpmCountThread(dataList);
        bpmCountThread.run();
        minFloatValue2 = bpmCountThread.minFloatValue;
        Float[] floats = bpmCountThread.resultFloatArray;
        List<Integer> T_index = bpmCountThread.T_index;
        List<Integer> R_index = bpmCountThread.R_index;

        double bpmUp = bpmCountThread.bpmUp;
        double bpmDown = bpmCountThread.bpmDown;

//        List<Float> dotLocateUp = bpmCountThread.R_dot_up;
//        List<Float> dotLocateDown = bpmCountThread.dotLocateDown;
//        List<Integer> RRLocateUp = bpmCountThread.R_index;
//        List<Integer> RRLocateDown = bpmCountThread.R_index_down;
//
//        for (int i = 0; i < dotLocateUp.size(); i++) {
//            Log.d("eeee", "Point：" + dotLocateUp.get(i) + "," + RRLocateUp.get(i));
//        }
//        for (int i = 0; i < dotLocateDown.size(); i++) {
//            Log.d("eeee2", "Point：" + dotLocateDown.get(i) + "," + RRLocateDown.get(i));
//        }
        int bpmUpInt = (int) bpmUp;
        int bpmDownInt = (int) bpmDown;

        if (bpmUpInt >= 30 && bpmUpInt <= 200 && bpmDownInt >= 30 && bpmDownInt <= 200) {
            textOutput.setText("檔名：" + chooserFileName + "\nBPM Up: " + bpmUpInt);
        } else if (bpmUpInt < 30 || bpmUpInt > 200) {
            if (bpmDownInt < 30 || bpmDownInt > 200) {
                textOutput.setText("檔名：" + chooserFileName + "\n無法計算");
            } else {
                textOutput.setText("檔名：" + chooserFileName + "\nBPM Down:" + bpmDownInt);
            }
        } else {
            textOutput.setText("檔名：" + chooserFileName + "\nBPM Up:" + bpmUpInt);
        }

        setLineChart(floats);
        setLineChart2(floats);
        setLineChart3(floats);

//        makeCSV(floats);
    }

    /**
     * 讀TFLite模
     */
    private MappedByteBuffer loadModelFile(AssetManager assetManager, String modelPath) throws IOException {
        AssetFileDescriptor fileDescriptor = assetManager.openFd(modelPath);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    public List<Entry> getDataBetweenTwoR(List<Float> dataList, int startIndex, int endIndex) {
        List<Entry> dataBetweenTwoR = new ArrayList<>();

        startIndex = Math.max(0, startIndex);
        endIndex = Math.min(dataList.size() - 1, endIndex);

        for (int i = startIndex; i <= endIndex; i++) {
            int xOffset = i - startIndex;
            dataBetweenTwoR.add(new Entry(xOffset, dataList.get(i)));
            Log.d("xxxx", ""+dataList.get(i));
        }

        return dataBetweenTwoR;
    }

    /**
     * 心電圖
     */
    public void setLineChart(Float[] floats) {
        Float[] selected = Arrays.copyOfRange(floats, 15, 1362);

        List<Float> R_dot_up = bpmCountThread.R_dot_up;
        List<Integer> R_index = bpmCountThread.R_index;

        /** 畫圖*/
        List<Entry> entries1 = new ArrayList<>();
        List<Entry> entries2 = new ArrayList<>();
        List<Entry> entries3 = new ArrayList<>();

        for (int i = 0; i < 10000; i++) {
            entries1.add(new Entry(i, floats[i]));
        }

        for (int i = 0; i < 10000; i++) {

            if (R_index.contains(i)) {
                // 如果当前索引在R_index中，则从R_dot_up中获取对应的值
                entries2.add(new Entry(i, R_dot_up.get(R_index.indexOf(i))));
            } else {
                // 如果当前索引不在R_index中，将值设为0
                entries2.add(new Entry(i, 0));
            }
        }

//        entries1 = getDataBetweenTwoR(Arrays.asList(floats), R_index.get(6), R_index.get(8));
//        for (Entry entry : entries1) {
//            float yValue = entry.getY();
//            Log.d("YYYY", String.valueOf(yValue));
//        }
//        entries2 = getDataBetweenTwoR(Arrays.asList(floats), 15, 1362);
//        entries3 = getDataBetweenTwoR(Arrays.asList(floats), R_index.get(4), R_index.get(6));

        LineDataSet dataSet1 = new LineDataSet(entries1, "R1");
        LineDataSet dataSet2 = new LineDataSet(entries2, "R2");
        LineDataSet dataSet3 = new LineDataSet(entries3, "R3");

        // Customize dataSet1, dataSet2, and dataSet3 as needed
        dataSet1.setColor(Color.RED);
        dataSet1.setCircleColor(Color.RED);
        dataSet2.setColor(Color.BLUE);
        dataSet2.setCircleColor(Color.BLUE);
        dataSet3.setColor(Color.GREEN);
        dataSet3.setCircleColor(Color.GREEN);

        dataSet1.setLineWidth(1.5f);
        dataSet1.setDrawCircles(false);
        dataSet2.setLineWidth(1.5f);
        dataSet2.setDrawCircles(false);
        dataSet3.setLineWidth(1.5f);
        dataSet3.setDrawCircles(false);

        LineData lineData = new LineData(dataSet1,dataSet2);
        mLineChart.setData(lineData);
        mLineChart.fitScreen();//自動調整
        mLineChart.getDescription().setEnabled(false);//取消圖表敘述
        mLineChart.setScaleYEnabled(false);//禁止Y軸上下拖動
        mLineChart.setBackgroundColor(Color.parseColor("#fff3fa"));
        //設置Y軸樣式
        YAxis rightAxis = mLineChart.getAxisRight();//設置圖表右邊
        rightAxis.setEnabled(false);//設置圖表右邊的y軸禁用
        YAxis leftAxis = mLineChart.getAxisLeft();//設置圖表左邊
        leftAxis.setEnabled(true);//設置圖表左邊的y軸禁用
        leftAxis.setAxisMinimum(minFloatValue2);//設置最小數值
        //設置x軸
        XAxis xAxis = mLineChart.getXAxis();
        xAxis.setTextColor(Color.parseColor("#333333"));
        xAxis.setTextSize(1f);

        xAxis.setDrawAxisLine(true);//是否繪製軸線
        xAxis.setDrawGridLines(false);//設置x軸上每個點對應的線
        xAxis.setDrawLabels(false);//繪製標籤  指x軸上的對應數值
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);//設置x軸的顯示位置
        xAxis.setGranularity(1f);//禁止放大後x軸標籤重繪
        xAxis.setEnabled(true);
        xAxis.setDrawGridLines(false);//背景網格

        //slide
//        float scaleX = mLineChart2.getScaleX();
//        if (scaleX == 1)
//            mLineChart2.zoomToCenter(5, 1f);
//        else {
//            BarLineChartTouchListener barLineChartTouchListener = (BarLineChartTouchListener) mLineChart.getOnTouchListener();
//            barLineChartTouchListener.stopDeceleration();
//            mLineChart2.fitScreen();
//        }
//        mLineChart2.invalidate();//refresh
    }

    public void setLineChart2(Float[] floats) {
        Float[] selected = Arrays.copyOfRange(floats, 15, 1362);

        List<Float> R_dot_up = bpmCountThread.R_dot_up;
        List<Integer> R_index = bpmCountThread.R_index;

        /** 畫圖*/
        List<Entry> entries1 = new ArrayList<>();

        entries1 = getDataBetweenTwoR(Arrays.asList(floats), R_index.get(2), R_index.get(4));

        LineDataSet dataSet1 = new LineDataSet(entries1, "R1");

        // Customize dataSet1, dataSet2, and dataSet3 as needed
        dataSet1.setColor(Color.RED);
        dataSet1.setCircleColor(Color.RED);

        dataSet1.setLineWidth(1.5f);
        dataSet1.setDrawCircles(false);

        LineData lineData = new LineData(dataSet1);
        mLineChart2.setData(lineData);
        mLineChart2.fitScreen();//自動調整
        mLineChart2.getDescription().setEnabled(false);//取消圖表敘述
        mLineChart2.setScaleYEnabled(false);//禁止Y軸上下拖動
        mLineChart2.setBackgroundColor(Color.parseColor("#fff3fa"));
        //設置Y軸樣式
        YAxis rightAxis = mLineChart.getAxisRight();//設置圖表右邊
        rightAxis.setEnabled(false);//設置圖表右邊的y軸禁用
        YAxis leftAxis = mLineChart.getAxisLeft();//設置圖表左邊
        leftAxis.setEnabled(true);//設置圖表左邊的y軸禁用
        leftAxis.setAxisMinimum(minFloatValue2);//設置最小數值
        //設置x軸
        XAxis xAxis = mLineChart.getXAxis();
        xAxis.setTextColor(Color.parseColor("#333333"));
        xAxis.setTextSize(1f);

        xAxis.setDrawAxisLine(true);//是否繪製軸線
        xAxis.setDrawGridLines(false);//設置x軸上每個點對應的線
        xAxis.setDrawLabels(false);//繪製標籤  指x軸上的對應數值
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);//設置x軸的顯示位置
        xAxis.setGranularity(1f);//禁止放大後x軸標籤重繪
        xAxis.setEnabled(true);
        xAxis.setDrawGridLines(false);//背景網格
    }

    public void setLineChart3(Float[] floats) {
        Float[] selected = Arrays.copyOfRange(floats, 15, 1362);

        List<Float> R_dot_up = bpmCountThread.R_dot_up;
        List<Integer> R_index = bpmCountThread.R_index;

        /** 畫圖*/
        List<Entry> entries1 = new ArrayList<>();

        entries1 = getDataBetweenTwoR(Arrays.asList(floats), R_index.get(6), R_index.get(8));

        LineDataSet dataSet1 = new LineDataSet(entries1, "R1");

        // Customize dataSet1, dataSet2, and dataSet3 as needed
        dataSet1.setColor(Color.RED);
        dataSet1.setCircleColor(Color.RED);

        dataSet1.setLineWidth(1.5f);
        dataSet1.setDrawCircles(false);

        LineData lineData = new LineData(dataSet1);
        mLineChart3.setData(lineData);
        mLineChart3.fitScreen();//自動調整
        mLineChart3.getDescription().setEnabled(false);//取消圖表敘述
        mLineChart3.setScaleYEnabled(false);//禁止Y軸上下拖動
        mLineChart3.setBackgroundColor(Color.parseColor("#fff3fa"));
        //設置Y軸樣式
        YAxis rightAxis = mLineChart.getAxisRight();//設置圖表右邊
        rightAxis.setEnabled(false);//設置圖表右邊的y軸禁用
        YAxis leftAxis = mLineChart.getAxisLeft();//設置圖表左邊
        leftAxis.setEnabled(true);//設置圖表左邊的y軸禁用
        leftAxis.setAxisMinimum(minFloatValue2);//設置最小數值
        //設置x軸
        XAxis xAxis = mLineChart.getXAxis();
        xAxis.setTextColor(Color.parseColor("#333333"));
        xAxis.setTextSize(1f);

        xAxis.setDrawAxisLine(true);//是否繪製軸線
        xAxis.setDrawGridLines(false);//設置x軸上每個點對應的線
        xAxis.setDrawLabels(false);//繪製標籤  指x軸上的對應數值
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);//設置x軸的顯示位置
        xAxis.setGranularity(1f);//禁止放大後x軸標籤重繪
        xAxis.setEnabled(true);
        xAxis.setDrawGridLines(false);//背景網格
    }




    private void makeCSV(Float[] floats) {

        new Thread(() -> {
            /** 檔名 */
            String date = new SimpleDateFormat("yyyy-MM-dd-hhmmss",
                    Locale.getDefault()).format(System.currentTimeMillis());
            String fileName = "[" + date + "]Lead2Data.csv";
            String[] title = {"Lead2"};
            StringBuffer csvText = new StringBuffer();
            for (int i = 0; i < title.length; i++) {
                csvText.append(title[i] + ",");
            }
            /** 內容 */
            for (int i = 0; i < floats.length; i++) {
                csvText.append("\n" + floats[i]);
            }

            Log.d("CSV", "makeCSV: " + csvText);
            runOnUiThread(() -> {
                try {
                    StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
                    StrictMode.setVmPolicy(builder.build());
                    builder.detectFileUriExposure();
                    FileOutputStream out = openFileOutput(fileName, Context.MODE_PRIVATE);
                    out.write((csvText.toString().getBytes()));
                    out.close();
                    File fileLocation = new File(Environment.getExternalStorageDirectory().getAbsolutePath(), fileName);
                    FileOutputStream fos = new FileOutputStream(fileLocation);
                    fos.write(csvText.toString().getBytes());
                    Uri path = Uri.fromFile(fileLocation);
                    Intent fileIntent = new Intent(Intent.ACTION_SEND);
                    fileIntent.setType("text/csv");
                    fileIntent.putExtra(Intent.EXTRA_SUBJECT, fileName);
                    fileIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    fileIntent.putExtra(Intent.EXTRA_STREAM, path);
                    Log.d("location", "makeCSV: " + fileLocation);
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }).start();
    }//makeCSV


}