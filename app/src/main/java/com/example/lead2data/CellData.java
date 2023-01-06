package com.example.lead2data;

import java.util.ArrayList;

public class CellData {
    private int len;
    private ArrayList<String> newcell,datav;
    private ArrayList<Integer> spv;

    public CellData(int len, ArrayList newcell, ArrayList spv, ArrayList datav){
        this.len = len;
        this.newcell = newcell;
        this.spv = spv;
        this.datav = datav;
    }

    public ArrayList getList(int i){
        if(i == 1){
            return newcell;
        }else if(i == 2){
            return spv;
        }else {
            return datav;
        }
    }

    public int getLen(){
        return len;
    }
}
