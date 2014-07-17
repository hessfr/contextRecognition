package com.example.welcome;

import com.example.contextrecognition.R;
import com.example.contextrecognition.R.id;
import com.example.contextrecognition.R.layout;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;

public class WelcomeActivity extends FragmentActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);     

        ViewPager pager = (ViewPager) findViewById(R.id.viewPager);
        pager.setAdapter(new MyPagerAdapter(getSupportFragmentManager()));
    }

    private class MyPagerAdapter extends FragmentPagerAdapter {

        public MyPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int pos) {
            switch(pos) {

            case 0: return Fragement1.newInstance("FirstFragment");
            case 1: return Fragment2.newInstance("SecondFragment");
            case 2: return Fragment3.newInstance("ThirdFragment");
            default: return Fragment3.newInstance("ThirdFragment");
            }
        }

        @Override
        public int getCount() {
            return 3;
        }       
    }
}