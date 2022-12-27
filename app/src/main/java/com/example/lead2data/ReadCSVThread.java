package com.example.lead2data;

import android.content.Context;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ReadCSVThread extends Thread{
    String fileName;
    List<Float> csvFloat = new ArrayList<>();
    ArrayList<String> csvArray = new ArrayList<>();
    public ReadCSVThread(String fileName) {
        this.fileName = fileName;
    }
    @Override
    public void run() {
        super.run();
        File csvFile = new File(fileName);
        try {
            BufferedReader reader = new BufferedReader(new FileReader(csvFile));
            String csvString;
            String[] arrayFinish;
            csvString = reader.readLine();
            arrayFinish = csvString.split(",");
            int titleIndex = -1;
            for (int i = 0; i < arrayFinish.length; i++) {
                if (arrayFinish[i].equals("Lead2")) {
                    titleIndex = i;
                    break;
                }
            }
            while ((csvString = reader.readLine()) != null ) {
                arrayFinish = csvString.split(",");
                if (titleIndex >= 0 && titleIndex < arrayFinish.length) {
                    csvArray.add(arrayFinish[titleIndex]);
                }

            }
            reader.close();
            csvArray.remove(0);
            for (int i = 0; i < csvArray.size(); i++) {
                csvFloat.add(Float.valueOf(csvArray.get(i)));
            }
            if (csvArray.size() >= 24000) {
                int extraTime = (csvFloat.size() - 24000) / 2;
                csvFloat = csvFloat.subList(extraTime, csvFloat.size() - extraTime);
                csvFloat = csvFloat.subList(0, 24000);
            } else {
                throw new Exception("資料錯誤");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
