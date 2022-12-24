package com.example.lead2data;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

public class ReadCSVThread extends Thread{
    String fileName;
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
            while ((csvString = reader.readLine()) != null) {
                arrayFinish = csvString.split(",");
                for (String arrayFinishData : arrayFinish) {
                    csvArray.add(arrayFinishData);
                }
            }
            reader.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception error) {
            error.printStackTrace();
        }
    }
}
