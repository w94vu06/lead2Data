package com.example.lead2data;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;


import com.codekidlabs.storagechooser.StorageChooser;

import org.apache.commons.lang3.ArrayUtils;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;


import ir.androidexception.filepicker.dialog.SingleFilePickerDialog;


public class MainActivity extends AppCompatActivity {
    private int[] shape;
    private TextView textOutput;
    private Button btnFileInput;
    private Button btnFileOutput;
    private Button btnFileOutput2;
    private ReadCSVThread readCSVThread;
    private ArrayList<String> csvDataList = new ArrayList<>();
    private float[][][] reshapedArray;
    private float[][][] reshapedArray2;
    private final String mModelPath = "mymodel.tflite";
    private final String mModelPath_loose = "mymodel_loose.tflite";
    private Interpreter interpreter;
    private String checkSignal;
    private String getPath;
    private CellData cell;
    ArrayList sample2 = new ArrayList();

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try {
            init();
            initPermission();
            initInterpreter();
            initPredict();
            initChooser();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void init() {
        textOutput = findViewById(R.id.textOutput);
        btnFileInput = findViewById(R.id.btnFileInput);
        btnFileOutput = findViewById(R.id.btnFileOutput);
        btnFileOutput2 = findViewById(R.id.btnFileOutput2);
    }
    /** 檢查權限*/
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
    }
    /** 檔案選擇器*/
    public void initChooser() {
        btnFileInput.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                StorageChooser chooser = new StorageChooser.Builder()
                        .withActivity(MainActivity.this)
                        .withFragmentManager(getFragmentManager())
                        .withMemoryBar(true)
                        .allowCustomPath(true)
                        .setType(StorageChooser.FILE_PICKER)
                        .build();
                chooser.show();

                chooser.setOnSelectListener(new StorageChooser.OnSelectListener() {
                    @Override
                    public void onSelect(String path) {
                        if (path.contains(".csv")) {
                            openCSV(path);
                        }
                        if (path.contains(".CHA")) {
                            readCHA(path);
                        }
                        getPath = path;

                    }
                });
            }
        });
    }
    /** 直譯器*/
    public void initInterpreter() throws IOException {
        Interpreter.Options options = new Interpreter.Options();
        options.setNumThreads(8);
        options.setUseNNAPI(true);
        /** 換模 */
//        interpreter = new Interpreter(loadModelFile(getAssets(), mModelPath), options);
        interpreter = new Interpreter(loadModelFile(getAssets(), mModelPath_loose), options);
    }

    public void initPredict() {
        /** 預測一段 */
        btnFileOutput.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                long startTime = System.currentTimeMillis();
                float[] result = doInference(reshapedArray);
                for (float f : result) {
                    if (f < 0.5) {
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
                    if (f < 0.5) {
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
    /** 預測*/
    public float[] doInference(float[][][] data) {

        float[][] outputs = new float[1][1];
        interpreter.run(data, outputs);

        float[] outputArray = new float[1];
        outputArray[0] = outputs[0][0];
        return outputArray;
    }
    /** 讀CHA*/
    public void readCHA(String fileName) {
        try {
            int x = 64;
            char[] a = new char[32 * 1024 * 1024];
            int cha_size = LcndUtil.len(getPath, a);
            byte[] content = LcndUtil.readFromByteFile(getPath);
            /** Lead1 */
            ArrayList CHA_LI_dataNum = new ArrayList();
            ArrayList CHA_LI_data16 = new ArrayList();
            ArrayList CHA_LI_dataSample = new ArrayList();
            ArrayList CHA_LI_data4 = new ArrayList();
            byte[] c1 = new byte[cha_size];
            for (int L1 = 0; L1 < cha_size; L1 += 136) {
                int y = L1 + x;
                int j = 0;
                for (int k = L1; k < y; k++) {
                    c1[j] = content[k];
                    j++;
                }
                oneCellData(c1, c1.length, 1);
                CHA_LI_dataNum.add(cell.getLen());
                CHA_LI_data16.add(cell.getList(1));
                CHA_LI_dataSample.add(cell.getList(2));
                CHA_LI_data4.add(cell.getList(3));
            }
            int startS = 0;
            double dou = Math.floor((startS * 1000) / 32);
            int pic = (int) dou;
            double sample_len = CHA_LI_dataSample.size();
            double dou_2 = (sample_len / 32) * 31 - 5;
            int picEnd = (int) dou_2;

            /** Lead2 */
            ArrayList CHA_LII_dataNum = new ArrayList();
            ArrayList CHA_LII_data16 = new ArrayList();
            ArrayList CHA_LII_dataSample = new ArrayList();
            ArrayList CHA_LII_data4 = new ArrayList();

            byte[] c2 = new byte[cha_size];
            for (int L2 = 64; L2 < cha_size; L2 += 136) {
                int y = L2 + x;
                int j = 0;
                for (int k = L2; k < y; k++) {
                    c2[j] = content[k];
                    j++;
                }
                oneCellData(c2, c2.length, 2);
                CHA_LII_dataNum.add(cell.getLen());
                CHA_LII_data16.add(cell.getList(1));
                CHA_LII_dataSample.add(cell.getList(2));
                CHA_LII_data4.add(cell.getList(3));
            }
            for (int i = pic; i < picEnd; i++) {
                List s = Arrays.asList(CHA_LII_dataSample.get(i));
                int y = s.get(0).toString().length();
                String f = s.get(0).toString().substring(1, y - 1).replaceAll(" ", "");
                List<String> myList = new ArrayList<>(Arrays.asList(f.split(",")));
                sample2.addAll(myList);
            }
            for (int i = 0; i < sample2.size(); i++) {
                int k = Integer.parseInt(sample2.get(i).toString());
                sample2.set(i, ((k - 2048) * 5) * 0.001);
            }
        } catch (Exception ignored) {

        }
    }

    public void oneCellData(byte[] cha, int len, int c) {
        ArrayList<String> onecell = new ArrayList();
        for (int i = 0; i < 64; i++) {
            String newcha = Integer.toBinaryString(cha[i]);
            int wordnum = newcha.length();
            if (wordnum != 8) {
                for (int k = 0; k < (8 - wordnum); k++) {
                    String x = '0' + newcha;
                    newcha = x;
                }
                if (wordnum > 8) {
                    String x = newcha.substring(24);
                    newcha = x;
                }
            } else {
                newcha = Integer.toBinaryString(cha[i]);
                String x = newcha.substring(0, 8);
                newcha = x;
            }
            onecell.add(newcha);
        }

        ArrayList<String> newCell = new ArrayList<>();
        for (int i = 0; i < 64; i += 2) {
            String low = onecell.get(i);
            String hi = onecell.get(i + 1);
            String new16 = hi + low;
            newCell.add(new16);
        }

        ArrayList<Integer> spv = new ArrayList<>();
        ArrayList<String> dataV = new ArrayList<>();
        for (int i = 0; i < 32; i++) {
            String sp = newCell.get(i).substring(4, 16);
            String dat = newCell.get(i).substring(0, 4);
            int x = Integer.parseInt(sp, 2);
            spv.add(x);
            dataV.add(dat);
        }
        cell = new CellData(len, newCell, spv, dataV);
    }
    /** 讀CSV*/
    private void openCSV(String fileName) {
        if (fileName != "") {
            readCSVThread = new ReadCSVThread(fileName);
            readCSVThread.run();
            List<Float> floatData = new ArrayList<>(readCSVThread.csvFloat);
            textOutput.setText("匯入資料成功");
            float[] inputArray = new float[floatData.size()];
            for (int i = 0; i < floatData.size(); i++) {
                inputArray[i] = floatData.get(i);
            }

            float[] rawSignalFirst = Arrays.copyOfRange(inputArray, 0, 12000);
            float[] rawSignalSecond = Arrays.copyOfRange(inputArray, 12000, 24000);
            Log.d("checkkkk", "openCSV: " + rawSignalSecond.length);

            reshapedArray = new float[1][12000][1];
            for (int i = 0; i < 12000; i++) {
                reshapedArray[0][i][0] = rawSignalFirst[i];
            }
            reshapedArray2 = new float[1][12000][1];
            for (int i = 1; i < 12000; i++) {
                reshapedArray2[0][i][0] = rawSignalSecond[i];
            }
        } else {
            textOutput.setText("PLS INPUT!!");
        }
    }
    /** 讀TFLite模*/
    private MappedByteBuffer loadModelFile(AssetManager assetManager, String modelPath) throws IOException {
        AssetFileDescriptor fileDescriptor = assetManager.openFd(modelPath);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }
}