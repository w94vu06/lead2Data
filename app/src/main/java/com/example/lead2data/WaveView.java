package com.example.lead2data;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public class WaveView extends View {
    private final String NAMESPACE = "http://schemas.android.com/apk/res-auto";

    /**
     * 常規繪製模式 不斷往後推的方式
     */
    public static int NORMAL_MODE = 0;

    /**
     * 繪製模式
     */
    private int drawMode;

    /**
     * 宽高
     */
    private float mWidth = 0, mHeight = 0;

    /**
     * 中心軸線
     */
    private int baseline;

    /**
     * 網格筆畫
     */
    private Paint mLinePaint;
    /**
     * 數據線畫筆
     */
    private Paint mWavePaint;
    /**
     * 線條的路徑
     */
    private Path mPath;

    /**
     * 保存已繪製的數據坐標
     */
    private int[] dataArray;

    /**
     * 數據最大值，默認-20~20之間
     */
    private float MAX_VALUE = 700;

    /**
     * 線條粗細
     */
    private float WAVE_LINE_STROKE_WIDTH = 3;
    /**
     * 波形颜色
     */
    private int waveLineColor = Color.parseColor("#EE4000");
    /**
     * 當前的x，y坐標
     */
    private float nowX, nowY;

    private float startY;

    /**
     * 線條的長度，可用於控制橫坐標
     */
    private int WAVE_LINE_WIDTH = 10;
    /**
     * 數據點的數量
     */
    private int row;

    private int draw_index;

    private boolean isRefresh;

    /** 常規模式下，需要一次繪製的點的數量*/
    private int draw_point_length;


    /**
     * 網格是否可見
     */
    private boolean gridVisible;
    /**
     * 網格的寬高
     */
    private final int GRID_WIDTH = 50;
    /**
     * 網格的橫線和豎線的數量
     */
    private int gridHorizontalNum, gridVerticalNum;
    /**
     * 網格線條的粗細
     */
    private final int GRID_LINE_WIDTH = 2;
    /**
     * 網格颜色
     */
    private int gridLineColor = Color.parseColor("#1b4200");

    public WaveView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        mWidth = w;
        mHeight = h;
//        baseline = h-130;
        baseline = h-130;
        super.onSizeChanged(w, h, oldw, oldh);
    }

    private void init(AttributeSet attrs) {
        MAX_VALUE = attrs.getAttributeIntValue(NAMESPACE, "max_value", 700);
        WAVE_LINE_WIDTH = attrs.getAttributeIntValue(NAMESPACE, "wave_line_width", 4);
        WAVE_LINE_STROKE_WIDTH = attrs.getAttributeIntValue(NAMESPACE, "wave_line_stroke_width", 3);
        gridVisible = attrs.getAttributeBooleanValue(NAMESPACE, "grid_visible", true);
        drawMode = attrs.getAttributeIntValue(NAMESPACE, "draw_mode", NORMAL_MODE);


        String wave_line_color = attrs.getAttributeValue(NAMESPACE, "wave_line_color");
        if (wave_line_color != null && !wave_line_color.isEmpty()) {
            waveLineColor = Color.parseColor(wave_line_color);
        }

        String grid_line_color = attrs.getAttributeValue(NAMESPACE, "grid_line_color");
        if (grid_line_color != null && !grid_line_color.isEmpty()) {
            gridLineColor = Color.parseColor(grid_line_color);
        }

        String wave_background = attrs.getAttributeValue(NAMESPACE, "wave_background");
        if (wave_background != null && !wave_background.isEmpty()) {
            setBackgroundColor(Color.parseColor(wave_background));
        }

        mLinePaint = new Paint();
        mLinePaint.setStyle(Paint.Style.STROKE);
        mLinePaint.setStrokeWidth(GRID_LINE_WIDTH);
        /* 抗锯齿效果*/
        mLinePaint.setAntiAlias(true);

        mWavePaint = new Paint();
        mWavePaint.setStyle(Paint.Style.STROKE);
        mWavePaint.setColor(waveLineColor);
        mWavePaint.setStrokeWidth(WAVE_LINE_STROKE_WIDTH);
        /* 抗锯齿效果*/
        mWavePaint.setAntiAlias(true);

        mPath = new Path();

    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        /* 物件的宽高*/
        mWidth = getMeasuredWidth();
        mHeight = getMeasuredHeight();

        /* 根據網格的單位長寬，獲取能繪製網格橫線和豎線的數量*/
        gridHorizontalNum = (int) (mHeight / GRID_WIDTH);
        gridVerticalNum = (int) (mWidth / GRID_WIDTH);

        /* 根據線條長度，最多能繪製多少個數據點*/
        row = (int) (mWidth / WAVE_LINE_WIDTH);
        dataArray = new int[row];
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        /* 繪製網格*/
        if (gridVisible) {
            drawGrid(canvas);
        }
        /* 繪製折線*/
        switch (drawMode) {
            case 0:
                drawWaveLineNormal(canvas);
                break;
            case 1:
                drawWaveLineLoop(canvas);
                break;
        }
        draw_index += 1;
        if (draw_index >= row) {
            draw_index = 0;
        }
    }

    /**
     * 常規模式繪製折線
     *
     * @param canvas
     */
    private void drawWaveLineNormal(Canvas canvas) {
        drawPathFromDatas(canvas, 0, row - 1);
        for (int i = 0; i < row - draw_point_length; i++) {
            dataArray[i] = dataArray[i + draw_point_length];
        }
    }

    /**
     * 循環模式繪製折線
     *
     * @param canvas
     */
    private void drawWaveLineLoop(Canvas canvas) {
        drawPathFromDatas(canvas, (row - 1) - draw_index > 8 ? 0 : 8 - ((row - 1) - draw_index), draw_index);
        drawPathFromDatas(canvas, Math.min(draw_index + 8, row - 1), row - 1);
    }

    /**
     * 取數組中的指定一段數據來繪製折線
     *
     * @param start 起始數據位
     * @param end   結束數據位
     */
    private void drawPathFromDatas(Canvas canvas, int start, int end) {
        mPath.reset();
        startY = (mHeight / 2 - dataArray[start] * (mHeight / (MAX_VALUE * 2)));
        mPath.moveTo(start * WAVE_LINE_WIDTH, startY);
        for (int i = start + 1; i < end + 1; i++) {
            if (isRefresh) {
                isRefresh = false;
                return;
            }
            nowX = i * WAVE_LINE_WIDTH;
            float dataValue = dataArray[i];
            /* 判斷數據為正數還是負數  超過最大值的數據按最大值來繪製*/
            if (dataValue > 0) {
                if (dataValue > MAX_VALUE) {
                    dataValue = MAX_VALUE;
                }
            } else {
                if (dataValue < -MAX_VALUE) {
                    dataValue = -MAX_VALUE;
                }
            }
            nowY = baseline - dataValue * (mHeight / (MAX_VALUE * 2));
            mPath.lineTo(nowX, nowY);
        }
        canvas.drawPath(mPath, mWavePaint);
    }

    /**
     * 繪製網格
     *
     * @param canvas
     */
    private void drawGrid(Canvas canvas) {
        /* 設置顏色*/
        mLinePaint.setColor(gridLineColor);
        /* 畫橫線*/
        for (int i = 0; i < gridHorizontalNum + 1; i++) {
            canvas.drawLine(0, i * GRID_WIDTH,
                    mWidth, i * GRID_WIDTH, mLinePaint);
        }
        /* 畫豎線*/
        for (int i = 0; i < gridVerticalNum + 1; i++) {
            canvas.drawLine(i * GRID_WIDTH, 0,
                    i * GRID_WIDTH, mHeight, mLinePaint);
        }
    }

    /**
     * 增加新的數據
     * @param line
     */

    public void showLine(int line) {
        if (draw_index >= row) {
            draw_index = 0;
        }
        switch (drawMode) {
            case 0:
                /* 常規模式數據添加至最後一位*/
                draw_point_length = 1;
                dataArray[row - 1] = line;

                break;
            case 1:
                /* 循環模式數據添加至當前繪製的位*/
                dataArray[draw_index] =line;
                break;
        }
        postInvalidate();
    }
}
