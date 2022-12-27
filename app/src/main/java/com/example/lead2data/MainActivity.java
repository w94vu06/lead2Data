package com.example.lead2data;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.annotation.SuppressLint;
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

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textOutput = findViewById(R.id.textOutput);
        btnFileInput = findViewById(R.id.btnFileInput);
        btnFileOutput = findViewById(R.id.btnFileOutput);
        btnFileOutput2 = findViewById(R.id.btnFileOutput2);

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
        try {
            readCSV();
            initInterpreter();
            initPredict();
            initChooser();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

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
                        openCSV(path);
                    }
                });
            }
        });
    }

    public void initPredict() {
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
                    float elapsedTime = (endTime - startTime)/1000;
                    textOutput.setText(Arrays.toString(result)+"\n"+checkSignal+"\n使用"+elapsedTime+"秒");
                });
            }
        });
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
                    float elapsedTime = (endTime - startTime)/1000;
                    textOutput.setText(Arrays.toString(combined)+"\n"+checkSignal+"\n使用"+elapsedTime+"秒");
                });
            }
        });
    }

    public void initInterpreter() throws Exception {
        Interpreter.Options options = new Interpreter.Options();
        options.setNumThreads(8);
        options.setUseNNAPI(true);

//        interpreter = new Interpreter(loadModelFile(getAssets(), mModelPath), options);
        interpreter = new Interpreter(loadModelFile(getAssets(), mModelPath_loose), options);

    }

    public float[] doInference(float[][][] data) {

        float[][] outputs = new float[1][1];
        interpreter.run(data, outputs);

        float[] outputArray = new float[1];
        outputArray[0] = outputs[0][0];
        return outputArray;
    }

    public void readCSV() {
        btnFileInput.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                SingleFilePickerDialog singleFilePickerDialog = new SingleFilePickerDialog(MainActivity.this,
                                () -> textOutput.setText("取消選取"),
                                files -> openCSV(files[0].getPath()));
                singleFilePickerDialog.show();
            }
        });
    }

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
            Log.d("checkkkk", "openCSV: "+rawSignalSecond.length);

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
    private MappedByteBuffer loadModelFile(AssetManager assetManager, String modelPath) throws IOException {
        AssetFileDescriptor fileDescriptor = assetManager.openFd(modelPath);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }
}