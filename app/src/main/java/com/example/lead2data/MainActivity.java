package com.example.lead2data;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
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


import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


import ir.androidexception.filepicker.dialog.SingleFilePickerDialog;


public class MainActivity extends AppCompatActivity {
    private TextView textOutput;
    private Button btnFileInput;
    private Button btnFileOutput;
    private ReadCSVThread readCSVThread;
    private ArrayList<String> csvDataList = new ArrayList<>();
    private ArrayList<Float> csvDataListFloat = new ArrayList<Float>();
    private ArrayList<Float> csvDataListFloatFinish = new ArrayList<>();

    private List<Float> listFloat = new ArrayList<Float>() {
    };
    private List<Float> listFloat2 = new ArrayList<Float>() {
    };
    private List<Float> listFloatPartA = new ArrayList<Float>() {
    };
    private List<Float> listFloatPartB = new ArrayList<Float>() {
    };
    private int[] shape;
    private float[] input1Array;
    private float[] input2Array;
    private float[][] array;
    private float[][][] reshapedArray;

    private final String mModelPath = "mymodel.tflite";
    private Interpreter interpreter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textOutput = findViewById(R.id.textOutput);
        btnFileInput = findViewById(R.id.btnFileInput);
        btnFileOutput = findViewById(R.id.btnFileOutput);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1000);
        }
//        mHandler = new MHandler();

        try {
            readCSV();
            initInterpreter();
            initPredict();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void initPredict() {
        btnFileOutput.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                float[] result = doInference(reshapedArray);
                Log.d("bbbb", "onClick: " + Arrays.toString(result));
                                runOnUiThread(() -> {
                    textOutput.setText(Arrays.toString(result));
                });
            }
        });
    }

    public void initInterpreter() throws Exception {
        Interpreter.Options options = new Interpreter.Options();
        options.setNumThreads(4);
        options.setUseNNAPI(true);
        interpreter = new Interpreter(loadModelFile(getAssets(), mModelPath), options);

        interpreter.getInputTensor(0);
        int input_tensor_count = interpreter.getInputTensorCount();
        // 遍歷每個輸入張量
        for (int i = 0; i < input_tensor_count; i++) {
            // 獲取輸入張量的形狀
            shape = interpreter.getInputTensor(i).shape();
            Log.d("gggg", "initInterpreter: " + shape);
        }

//        int output_tensor_count = interpreter.getOutputTensorCount();
//        // 遍歷每個輸入張量
//        for (int i = 0; i < output_tensor_count; i++) {
//            // 獲取輸出張量的形狀
//            int[] shape2 = interpreter.getOutputTensor(i).shape();
//            Log.d("gggg", "init: "+Arrays.toString(shape2));
//        }
//        Tensor filterTensor = interpreter.getInputTensor(0);
//        int[] shape3 = filterTensor.shape();
//        for (int i = 0; i < shape3.length; i++) {
//            Log.d("gggg", "sf: "+Arrays.toString(shape3));
//        }
    }

    public float[] doInference(float[][][] daa) {
        float[][] outputs = new float[1][1];
        interpreter.run(daa,outputs);

        float[] outputArray = new float[1];
        outputArray[0] = outputs[0][0];
        return outputArray;
    }

    private MappedByteBuffer loadModelFile(AssetManager assetManager, String modelPath) throws IOException {
        AssetFileDescriptor fileDescriptor = assetManager.openFd(modelPath);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    public void readCSV() {
        btnFileInput.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                SingleFilePickerDialog singleFilePickerDialog = new SingleFilePickerDialog(MainActivity.this,
                        () -> textOutput.setText("Canceled!!"),
                        files -> openCSV(files[0].getPath()));
                singleFilePickerDialog.show();
            }
        });
    }

    private void openCSV(String fileName) {
        if (fileName != "") {
            readCSVThread = new ReadCSVThread(fileName);
            readCSVThread.run();
            csvDataList.clear();
            csvDataList.addAll(readCSVThread.csvArray);
            csvDataList.remove(0);
            if (csvDataList.size() > 24000) {
                int selectTime = (csvDataList.size() - 24000) / 2;

                for (int i = 0; i < csvDataList.size(); i++) {
                    csvDataListFloat.add(Float.valueOf(csvDataList.get(i)));
                }
                listFloat = csvDataListFloat.subList(selectTime, csvDataListFloat.size() - selectTime);
                listFloat2 = listFloat.subList(0, 24000);
                listFloatPartA = listFloat2.subList(0, 12000);
                listFloatPartB = listFloat2.subList(12000, 24000);

                input1Array = new float[listFloatPartA.size()];
                for (int i = 0; i < listFloatPartA.size(); i++) {
                    input1Array[i] = listFloatPartA.get(i);
                }
                input2Array = new float[listFloatPartB.size()];
                for (int i = 0; i < listFloatPartB.size(); i++) {
                    input2Array[i] = listFloatPartB.get(i);
                }
                float[] rawSignalSubarray = Arrays.copyOfRange(input1Array, 0, 12000);
                reshapedArray = new float[1][12000][1];
                for (int i = 0; i < 12000; i++) {
                    reshapedArray[0][i][0] = rawSignalSubarray[i];
                }


            } else {
                Toast.makeText(this, "資料錯誤", Toast.LENGTH_LONG).show();
            }
            textOutput.setText("Success!!");
        } else {
            textOutput.setText("PLS INPUT!!");
        }

    }

    /**
     * 接收線程裡的資料
     */
//    class MHandler extends Handler {
//        @Override
//        public void handleMessage(@NonNull Message msg) {
//            super.handleMessage(msg);
//            switch (msg.what) {
//                case MESSAGE_TYPE_1:
//                    backData = (float[]) msg.obj;
//                    break;
//                case MESSAGE_TYPE_2:
//                    backData2 = (float[]) msg.obj;
//                    break;
//            }
//        }
//    }
}