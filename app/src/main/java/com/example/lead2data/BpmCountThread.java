package com.example.lead2data;

import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import uk.me.berndporr.iirj.Butterworth;

public class BpmCountThread extends Thread {
    List<Float> dataList;
    public BpmCountThread(List<Float> dataList) {
        this.dataList = dataList;
    }

    float minValue = Float.MAX_VALUE;
    float maxValue = Float.MIN_VALUE;

    float minValueA = Float.MAX_VALUE;
    float maxValueA = Float.MIN_VALUE;
    float minValueB = Float.MAX_VALUE;
    float maxValueB = Float.MIN_VALUE;

    //回傳結果
    Float minFloatValue;
    Float[] resultFloatArray;
    double bpmUp;
    double bpmDown;

    List<Float> peakListUp = new ArrayList();
    List<Float> peakListDown = new ArrayList();

    List<Float> R_dot_up = new ArrayList();
    List<Float> dotLocateDown = new ArrayList();
    List<Integer> R_index = new ArrayList();//R點索引

    List<Integer> R_index_down = new ArrayList();

    List<Integer> RRIUp = new ArrayList<>();
    List<Integer> RRIDown = new ArrayList<>();

    List<Float> T_dot = new ArrayList<>(); //T點
    List<Integer> T_index = new ArrayList<>(); //T點索引

    @Override
    public void run() {
        super.run();
        /** 宣告*/
        float maxFloat = 0;
        float minFloat = 0;

        List<Float> bandStop = new ArrayList<>(Arrays.asList(butter_bandStop_filter(dataList, 55, 65, 1000, 1)));

        Float[] floats = butter_bandpass_filter(bandStop, 2, 10, 1000, 1);

        Float[] floats2 = butter_bandpass_filter2(bandStop, 2, 10, 1000, 1);

        if (!Float.isNaN(maxValueA)) {
            Log.d("maxValueA", "isNOTNULL");
            maxValue = maxValueA;
            minValue = minValueA;
        } else {
            Log.d("maxValueA", "isNULL");
            floats = floats2;
            maxValue = maxValueB;
            minValue = minValueB;
        }

        resultFloatArray = floats;

        int chunkSize = 12000;
        for (int i = 0; i < resultFloatArray.length; i += chunkSize) {
            int endIndex = Math.min(i + chunkSize, resultFloatArray.length);
            Float[] chunk = Arrays.copyOfRange(resultFloatArray, i, endIndex);

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
                    peakListDown.add(0F);  // 添加零值到 peakListDown
                } else if (value < minValue / 1.5) {
                    peakListUp.add(0F);    // 添加零值到 peakListUp
                    peakListDown.add(value);
                } else {
                    peakListUp.add(0F);
                    peakListDown.add(0F);
                }
            }

        }
        Log.d("gggg", "resultFloatArray: "+resultFloatArray.length);
        Log.d("gggg", "peakListUp: "+peakListUp.size());

        //找出上下的R
        calPeakListUp();
        calPeakListDown();
        findTDot();

        /** 算平均心率 */

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

        minFloatValue = minValue;
        //初始化
        minValue = Float.MAX_VALUE;
        maxValue = 0;
        minValueA = Float.MAX_VALUE;
        maxValueA = 0;
        minValueB = Float.MAX_VALUE;
        maxValueB = 0;
    }

    public void calPeakListUp() {
        float maxFloat = 0;
        boolean insideR = false;

        // 清空之前的数据
        RRIUp.clear();
        R_dot_up.clear();
        R_index.clear();

        // 遍歷 peakListUp 尋找 R 點
        for (int i = 0; i < peakListUp.size(); i++) {
            float value = peakListUp.get(i);

            if (value != 0) {
                maxFloat = Math.max(maxFloat, value);
                insideR = true;
            } else if (insideR) {
                R_dot_up.add(maxFloat);
                R_index.add(i - 1);
                maxFloat = 0;
                insideR = false;
            }
        }

        // 印 R_index
        for (int index : R_index) {
            Log.d("R_index", "" + index);
        }

        // 计算 RR 间距
        for (int i = 0; i < R_index.size() - 1; i++) {
            RRIUp.add((R_index.get(i + 1)) - R_index.get(i));
        }
    }


    public void calPeakListDown() {
        float minFloat = 0;
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
                R_index_down.add(i);
            }
        }
        for (int i = 0; i < R_index_down.size() - 1; i++) {
            RRIDown.add((R_index_down.get(i + 1)) - R_index_down.get(i));
        }
    }

    public void findTDot() {
        // 遍歷 R_index，找出每個 R 點之間的最大值
        for (int i = 0; i < R_index.size() - 1; i++) {
            int start = R_index.get(i);
            int end = R_index.get(i + 1);
            float maxBetweenR = 0;

            // 找出兩個 R 點之間的最大值
            for (int j = start + 1; j < end; j++) {
                float value = peakListUp.get(j);
                maxBetweenR = Math.max(maxBetweenR, value);
            }

            // 將最大值添加到列表中
            T_dot.add(maxBetweenR);
        }

        // 輸出結果
        for (float maxValue : T_dot) {
            Log.d("MaxValueBetweenR", "" + maxValue);
        }

        //拿到T的索引
        for (int i = 0; i < peakListUp.size(); i++) {
            if (T_dot.contains(peakListUp.get(i))) {
                T_index.add(i);
                Log.d("T_index", ""+i);
            }
        }
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
