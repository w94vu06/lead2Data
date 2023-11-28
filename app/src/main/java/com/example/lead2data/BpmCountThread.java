package com.example.lead2data;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.os.StrictMode;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import uk.me.berndporr.iirj.Butterworth;

public class BpmCountThread extends Thread {
    List<Float> dataList;

    float minValue = Float.MAX_VALUE;
    float maxValue = Float.MIN_VALUE;
    float minValueA = Float.MAX_VALUE;
    float maxValueA = Float.MIN_VALUE;
    float minValueB = Float.MAX_VALUE;
    float maxValueB = Float.MIN_VALUE;
    //回傳結果
    Float minFloatValue;
    Float[] resFloat;
    double bpmUp;
    double bpmDown;
    //
    List<Float> peakListUp = new ArrayList();
    List<Float> peakListDown = new ArrayList();
    //
    List<Float> dotLocateUp = new ArrayList();
    List<Float> dotLocateDown = new ArrayList();
    List<Integer> RRLocateUp = new ArrayList();
    List<Integer> RRLocateDown = new ArrayList();
    //
    List<Integer> RRIUp = new ArrayList<>();
    List<Integer> RRIDown = new ArrayList<>();

    public BpmCountThread(List<Float> dataList) {
        this.dataList = dataList;
    }

    @Override
    public void run() {
        super.run();
        /** 宣告*/
        float maxFloat = 0;
        float minFloat = 0;

        List<Float> bandStop = Arrays.asList(butter_bandStop_filter(dataList, 55, 65, 1000, 1));

        Float[] floats = butter_bandpass_filter(bandStop, 1, 50, 1000, 1);

        Float[] floats2 = butter_bandpass_filter2(bandStop, 1, 50, 1000, 1);

        if (!Float.isNaN(maxValueA)) {
            maxValue = maxValueA;
            minValue = minValueA;
        } else {
            floats = floats2;
            maxValue = maxValueB;
            minValue = minValueB;
        }
        resFloat = floats;

        int chunkSize = 4000;
        for (int i = 0; i < floats.length; i += chunkSize) {
            int endIndex = Math.min(i + chunkSize, floats.length);
            Float[] chunk = Arrays.copyOfRange(floats, i, endIndex);

            // 找到該小數組的最大值和最小值
            float maxValue = Float.NEGATIVE_INFINITY;
            float minValue = Float.POSITIVE_INFINITY;
            for (Float value : chunk) {
                maxValue = Math.max(maxValue, value);
                minValue = Math.min(minValue, value);
            }
            // 遍歷小數組中的每個元素，將符合條件的值加入到對應的列表中
            for (Float value : chunk) {
                if (value > maxValue / 1.5) {
                    peakListUp.add(value);
                } else if (value < minValue / 1.5) {
                    peakListDown.add(value);
                } else {
                    peakListUp.add(0F);
                    peakListDown.add(0F);
                }

            }

        }


        /**
         * Up
         * */
        //找出R點
        for (float f : peakListUp) {
            if (f != 0) {
                maxFloat = Math.max(maxFloat, f);
            } else {
                if (maxFloat != 0) {
                    dotLocateUp.add(maxFloat);
                }
                maxFloat = 0;
            }

        }
        //拿到R的索引
        for (int i = 0; i < peakListUp.size(); i++) {
            if (dotLocateUp.contains(peakListUp.get(i))) {
                RRLocateUp.add(i);
            }
        }
//        計算RR間距
        for (int i = 0; i < RRLocateUp.size() - 1; i++) {
            RRIUp.add((RRLocateUp.get(i + 1)) - RRLocateUp.get(i));
        }

        /**
         * Down
         * */
        for (float f : peakListDown) {
            if (f != 0) {
                minFloat = Math.min(minFloat, f);
            } else {
                if (minFloat != 0) {
                    dotLocateDown.add(minFloat);
                }
                minFloat = 0;
            }
        }
        for (int i = 0; i < peakListDown.size(); i++) {
            if (dotLocateDown.contains(peakListDown.get(i))) {
                RRLocateDown.add(i);
            }
        }
        for (int i = 0; i < RRLocateDown.size() - 1; i++) {
            RRIDown.add((RRLocateDown.get(i + 1)) - RRLocateDown.get(i));
        }

        /** 算平均值 */


        int sumUp = 0;
        int sumDown = 0;

        for (int i : RRIUp) {
            sumUp += i;
        }
        for (int i : RRIDown) {
            sumDown += i;
        }
        if (sumUp != 0) {
            bpmUp = 60.0 / ((sumUp / RRIUp.size()) / 1000.0);
        }
        if (sumDown != 0) {
            bpmDown = 60.0 / ((sumDown / RRIDown.size()) / 1000.0);
        }

        Log.d("llll", "run: " + sumUp + "**" + sumDown);
        Log.d("llll", "run: " + bpmUp + "**" + bpmDown);

        minFloatValue = minValue;
        //初始化
        minValue = Float.MAX_VALUE;
        maxValue = 0;
        minValueA = Float.MAX_VALUE;
        maxValueA = 0;
        minValueB = Float.MAX_VALUE;
        maxValueB = 0;
    }

    /**
     * 巴特沃斯濾波器
     */
    public Float[] butter_bandpass_filter(List<Float> data, int lowCut, int highCut, int fs, int order) {
        Butterworth butterworth = new Butterworth();
        float widthFrequency = highCut - lowCut;
        float centerFrequency = (highCut + lowCut) / 2;
        butterworth.bandPass(order, fs, centerFrequency, widthFrequency);
        int in = 0;
        Float[] floatArray = new Float[data.size()];
        for (float a : data) {
            float b = (float) butterworth.filter(a);
            float c = b * b;
            float d = (float) butterworth.filter(c);
            minValueA = Math.min(minValueA, d);
            maxValueA = Math.max(maxValueA, d);
            floatArray[in] = d;
            in++;
        }
        return floatArray;
    }

    public Float[] butter_bandpass_filter2(List<Float> data, int lowCut, int highCut, int fs, int order) {
        Butterworth butterworth = new Butterworth();
        float widthFrequency = highCut - lowCut;
        float centerFrequency = (highCut + lowCut) / 2;
        butterworth.bandPass(order, fs, centerFrequency, widthFrequency);
        int in = 0;
        Float[] floatArray2 = new Float[data.size()];
        for (float x : data) {
            float y = (float) butterworth.filter(x);
            floatArray2[in] = y;
            in++;
            minValueB = Math.min(minValueB, y);
            maxValueB = Math.max(maxValueB, y);
        }
        return floatArray2;
    }

    public Float[] butter_bandStop_filter(List<Float> data, int lowCut, int highCut, int fs, int order) {
        Butterworth butterworth = new Butterworth();
        float widthFrequency = highCut - lowCut;
        float centerFrequency = (highCut + lowCut) / 2;
        butterworth.bandStop(order, fs, centerFrequency, widthFrequency);
        int in = 0;
        Float[] floatArray = new Float[data.size()];
        for (float a : data) {
            float b = (float) butterworth.filter(a);
            floatArray[in] = b;
            in++;
        }
        return floatArray;
    }

    public Float[] butter_highpass_filter(List<Float> data, int lowCut, int highCut, int fs, int order) {
        Butterworth butterworth = new Butterworth();
        float widthFrequency = highCut - lowCut;
        float centerFrequency = (highCut + lowCut) / 2;
        butterworth.bandPass(order, fs, centerFrequency, widthFrequency);
        int in = 0;
        Float[] floatArray2 = new Float[data.size()];
        for (float x : data) {
            float y = (float) butterworth.filter(x);
            floatArray2[in] = y;
            in++;
            minValueB = Math.min(minValueB, y);
            maxValueB = Math.max(maxValueB, y);
        }
        return floatArray2;
    }
}
