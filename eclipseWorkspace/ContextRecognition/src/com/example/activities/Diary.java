package com.example.activities;

//import android.app.ActionBar;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.apache.commons.lang3.ArrayUtils;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.echo.holographlibrary.PieGraph;
import com.echo.holographlibrary.PieSlice;
import com.example.contextrecognition.R;
import com.example.tools.Globals;



public class Diary extends ActionBarActivity {
	
	private static final String TAG = "DiaryAcitivty";
	
	ListView legend;
	
	@Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_diary);
 
        // get action bar   
        ActionBar actionBar = getSupportActionBar();
 
        // Enabling Up / Back navigation
        actionBar.setDisplayHomeAsUpEnabled(true);

        String[] contextClasses = Globals.getStringArrayPref(this, Globals.CONTEXT_CLASSES);
        
        ArrayList<Integer> t = Globals.getIntListPref(this, Globals.CLASS_COUNTS);
        Integer[] totalCounts = new Integer[t.size()];
        t.toArray(totalCounts);

        legend = (ListView) findViewById(R.id.listView1);
        
        createChart(totalCounts, contextClasses);

    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        //Create the options entry in the ActionBar
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	//Handle ActionBar clicks
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            //Go to settings activity
        	callSettings();
        	return true;
        }
        if (id == R.id.action_rating) {
        	//Go to help activity
            callRating();
        	return true;
        }
        if (id == R.id.action_help) {
        	//Go to help activity
            callHelp();
        	return true;
        }
        
        return super.onOptionsItemSelected(item);
    }
    
    private void createChart(final Integer[] t, String[] c) {
    	
    	String[] colors = {"#41A317", "#CC6600", "#1569C7", "#008080", "#A52A2A",
    			"#438D80", "#827839", "#571B7E", "#8C001A", "#D462FF", "#FFA500",
    			"#3B3131", "#566D7E", "#7F525D", "#538074"}; //TODO
    	
    	// http://stackoverflow.com/questions/112234/sorting-matched-arrays-in-java
    	Integer[] idx = new Integer[t.length];
    	for( int i = 0 ; i < idx.length; i++ ) idx[i] = i;              
    	Arrays.sort(idx, new Comparator<Integer>() {
    	    public int compare(Integer i1, Integer i2) {                        
    	        return Double.compare(t[i1], t[i2]);
    	    }                   
    	});
    	ArrayUtils.reverse(idx);
    	
    	Integer[] totalCounts = new Integer[t.length];
    	String[] contextClasses = new String[t.length];
    	
    	
    	for(int i=0; i<totalCounts.length; i++) {
    		totalCounts[i] = t[idx[i]];
    		contextClasses[i] = c[idx[i]];
    	}
    	
    	// Calculate the percentage of each context and append it to the strings:
    	int totalSum = 0;
    	for(int i=0; i<t.length; i++) {
    		totalSum += totalCounts[i];
    	}
    	for(int i=0; i<t.length; i++) {
    		double percentage = 100 * totalCounts[i] / ((double) totalSum);
    		contextClasses[i] = contextClasses[i] + " " + String.format("%.1f",percentage) + "%";
    	}

    	PieGraph pg = (PieGraph) findViewById(R.id.piegraph);
    	
    	for(int i=0; i<totalCounts.length; i++) {
    		PieSlice slice = new PieSlice();
    		slice.setColor(Color.parseColor(colors[i]));
    		slice.setValue(1);
    		pg.addSlice(slice);
    	}

		int holeSize = 100;
        pg.setInnerCircleRatio(holeSize);	

        int j=0;
		for (PieSlice s : pg.getSlices()) {
			s.setGoalValue(totalCounts[j]);
			j++;
		}
            
        pg.setDuration(2000);
        pg.setInterpolator(new AccelerateDecelerateInterpolator());
        pg.animateToGoalValues();
        
        
        // Create legend ListView:
        final ArrayList<String> list = new ArrayList<String>();
        for (int i = 0; i < contextClasses.length; ++i) {
          list.add(contextClasses[i]);
        }
        
        CustomListAdapter listAdapter = new CustomListAdapter(this, 
        		R.layout.legend_list_element, list, colors);
        legend.setAdapter(listAdapter);        
    }
    
    /**
     * Launch Settings activity
     * */
    private void callSettings() {
        Intent i = new Intent(Diary.this, Settings.class);
        startActivity(i);
    }
    /**
     * Launch Rating activity
     * */
    private void callRating() {
        Intent i = new Intent(Diary.this, Rating.class);
        startActivity(i);
    }
    /**
     * Launch Help activity
     * */
    private void callHelp() {
        Intent i = new Intent(Diary.this, Help.class);
        startActivity(i);
    }
    
    
    /*
     * Code from: http://stackoverflow.com/questions/7361135/how-to-change-color-and-font-on-listview
     */
    private class CustomListAdapter extends ArrayAdapter {

        private Context mContext;
        private int id;
        private List <String>items ;
        private String[] mColors;

        public CustomListAdapter(Context context, int textViewResourceId , List<String> list, String[] colors) 
        {
            super(context, textViewResourceId, list);      
            mColors = colors;
            mContext = context;
            id = textViewResourceId;
            items = list ;
        }

        @Override
        public View getView(int position, View v, ViewGroup parent)
        {
            View mView = v ;
            if(mView == null){
                LayoutInflater vi = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                mView = vi.inflate(id, null);
            }

            TextView text = (TextView) mView.findViewById(R.id.textView);
            
            if(items.get(position) != null )
            {
                text.setTextColor(Color.WHITE);
                text.setText(items.get(position));
                text.setBackgroundColor(Color.parseColor(mColors[position]));
                text.setTextSize(18);
            }

            return mView;
        }

    }
    
}
