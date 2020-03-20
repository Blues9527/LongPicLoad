package com.blues.longpicload;

import android.os.Bundle;

import com.blues.longpic.LongView;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        LongView lv = findViewById(R.id.lv);
//        try {
//            lv.setImage(getAssets().open("long.jpg"));
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
        lv.setImage(R.mipmap.long1);
    }
}
