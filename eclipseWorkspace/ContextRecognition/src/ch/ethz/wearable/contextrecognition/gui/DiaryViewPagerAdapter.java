package ch.ethz.wearable.contextrecognition.gui;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import org.apache.commons.lang3.ArrayUtils;

import android.content.Context;
import android.graphics.Color;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import ch.ethz.wearable.contextrecognition.R;
import ch.ethz.wearable.contextrecognition.data.HistoricPredictions;
import ch.ethz.wearable.contextrecognition.utils.Globals;

import com.echo.holographlibrary.PieGraph;
import com.echo.holographlibrary.PieSlice;
import com.google.gson.Gson;

/*
 * Pager adapter for the ViewPager in the diary activity.
 */
public class DiaryViewPagerAdapter extends PagerAdapter {

	private static final String TAG = "ViewPagerAdapter";
	
	private static double PREDICTION_WINDOW = 2.016; // in seconds
	
	ListView legend;
	TextView recordingTimeTV;
	TextView silentTimeTV;
	
	Context context;
    LayoutInflater inflater;
    
    HistoricPredictions historicPredictions;
    
    public DiaryViewPagerAdapter(Context context, String[] todayContextClasses,
    		Integer[] todayTotalCounts, Integer todaySilenceCount) {
        this.context = context;
        
		Gson gson2 = new Gson();
		
		if(Globals.HISTORIC_PREDICTIONS_FILE.exists()) {
			
			Log.d(TAG,"JSON file found");
			
			try {
				BufferedReader br = new BufferedReader(new FileReader(Globals.HISTORIC_PREDICTIONS_FILE));
			
				this.historicPredictions = gson2.fromJson(br, HistoricPredictions.class);
				
			} catch (IOException e) {
				this.historicPredictions = null;
				Log.e(TAG,"Couldn't open JSON file");
				e.printStackTrace();
			}
		} else {
			this.historicPredictions = null;
			Log.i(TAG, "Historic predictions file does not exist, only stats from today will be shown");
        }
		
		if (this.historicPredictions != null) {
			
			// Add the value from today:
			this.historicPredictions.append_to_context_class_list(todayContextClasses);
			this.historicPredictions.append_to_prediction_list(new ArrayList<Integer>(Arrays.asList(todayTotalCounts)));
			this.historicPredictions.append_to_silence_list(todaySilenceCount);
			
			Calendar cal = Calendar.getInstance();
			Date today = cal.getTime();
			this.historicPredictions.append_to_date_list(today);
			
		} else {
			Log.i(TAG, "historicPredictions is null, initializing array only with data from today");
			
			Calendar cal = Calendar.getInstance();
			Date date = cal.getTime();
			
			// If the file doesn't exists yet, create a completely new one:
			historicPredictions = new HistoricPredictions(new ArrayList<Integer>(Arrays.asList(todayTotalCounts)), 
					todaySilenceCount, todayContextClasses, date);
		}
    }
 
    @Override
    public int getCount() {
    	if (this.historicPredictions != null) {
    		return this.historicPredictions.get_size();
    	} else {
    		return 1;
    	}
        
    }
    
    @Override
    public String getPageTitle(int position) {
    	
    	Date date = this.historicPredictions.get_date_list().get(position);
    	
    	SimpleDateFormat df = new SimpleDateFormat("EEE, MMM d");
		String dateString = df.format(date);
    	
    	return dateString;
    }
 
    @Override
    public boolean isViewFromObject(View view, Object object) {
        return view == ((LinearLayout) object);
    }
 
    @Override
    public Object instantiateItem(ViewGroup container, int position) {
 
        inflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View itemView = inflater.inflate(R.layout.viewpager_diary_item, container,
                false);

        ArrayList<String> tmpContextClassesList = this.historicPredictions.get_context_class_list().get(position);
        ArrayList<Integer> tmpPredictionList = this.historicPredictions.get_prediction_list().get(position);
        
        String[] contextClasses = tmpContextClassesList.toArray(new String[tmpContextClassesList.size()]);
        Integer[] totalCounts = tmpPredictionList.toArray(new Integer[tmpPredictionList.size()]);
        Integer silenceCount = this.historicPredictions.get_silence_list().get(position);
        
        legend = (ListView) itemView.findViewById(R.id.listView1);
        
        if (contextClasses.length == totalCounts.length) {
        	createChart(itemView, totalCounts, contextClasses);
        } else {
        	Log.e(TAG, "Diary activity not opened, because new classes not fully incorporated yet");
        }
        
        recordingTimeTV = (TextView) itemView.findViewById(R.id.recordingTime);
        silentTimeTV = (TextView) itemView.findViewById(R.id.silentTime);
        
        int totalPredSum = 0;
        for (int i : totalCounts) {
        	totalPredSum += i;
        }
        double totalPredTime = totalPredSum * PREDICTION_WINDOW;
        
        double totalSilenceTime = silenceCount * PREDICTION_WINDOW;
        SimpleDateFormat df = new SimpleDateFormat("HH:mm");
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        
        double totalRecTime = totalPredTime + totalSilenceTime; // Total recording time in seconds
        
        Date totalRecDate = new Date((long) totalRecTime*1000);
        String totalRecTimeString = df.format(totalRecDate);
        recordingTimeTV.setText(totalRecTimeString + "h\nin total");

        Date totalSilenceDate = new Date((long) totalSilenceTime*1000);
        String silenceTimeString = df.format(totalSilenceDate);
        silentTimeTV.setText(silenceTimeString + "h\nsilence");
        

 
        // Add viewpager_item.xml to ViewPager
        ((ViewPager) container).addView(itemView);
 
        return itemView;
    }
 
    @Override
    public void destroyItem(ViewGroup container, int position, Object object) {
        // Remove viewpager_item.xml from ViewPager
        ((ViewPager) container).removeView((LinearLayout) object);
 
    }
    
private void createChart(View itemView, final Integer[] t, String[] c) {
    	
    	String[] colors = {
    			
    			"#E9AB17", "#9DC209", "#C35817", "#966F33", "#566D7E",
    			"#F87217", "#728C00", "#E55451", "#827839",	"#3090C7", 
    			"#95B9C7", "#3BB9FF", "#4C4646", "#8EEBEC", "#78866B", 
    			"#CD7F32", "#52D017", "#966F33", "#566D7E", "#6F4E37", 
    			"#3090C7", "#438D80", "#566D7E", 

    			};
    			
    	
    	/*
    	 * From http://stackoverflow.com/questions/112234/sorting-matched-arrays-in-java
    	 */
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
    		contextClasses[i] = contextClasses[i] + " " + Math.round(percentage) + "%";
//    		contextClasses[i] = contextClasses[i] + " " + String.format("%.1f",percentage) + "%";
    	}

    	PieGraph pg = (PieGraph) itemView.findViewById(R.id.piegraph);
    	
    	for(int i=0; i<totalCounts.length; i++) {
    		PieSlice slice = new PieSlice();
			slice.setColor(Color.parseColor(colors[i]));
    		slice.setValue(1);
    		pg.addSlice(slice);
    	}

		int holeSize = 100;
        pg.setInnerCircleRatio(holeSize);	
		
		/*
		 *  Workaround to avoid the bug, that diagram is not displayed anymore,
		 *  if only one class:
		 */
        for(int i=0; i<totalCounts.length; i++) {
        	// Add a tiny dummy slice, if there would be only one class:
    		if (totalCounts[i] == 1) {
    			PieSlice slice = new PieSlice();
    			slice.setColor(Color.parseColor("#ffffff")); // white
    	        slice.setValue(0.001f);
    	        pg.addSlice(slice);
    		}
        }

        
		for(int i=0; i<pg.getSlices().size(); i++) {
			PieSlice s = pg.getSlice(i);
			if (i<totalCounts.length) {
				s.setGoalValue(totalCounts[i]);
			} else {
				// The dummy slice:
				s.setGoalValue(0.001f);
			}
			
		}
            
        pg.setDuration(2000);
        pg.setInterpolator(new AccelerateDecelerateInterpolator());
        pg.animateToGoalValues();
        
        
        // Create legend ListView:
        final ArrayList<String> list = new ArrayList<String>();
        for (int i = 0; i < contextClasses.length; ++i) {
          list.add(contextClasses[i]);
        }
        
        CustomListAdapter listAdapter = new CustomListAdapter(context, 
        		R.layout.legend_list_element, list, colors);
        legend.setAdapter(listAdapter);        
    }

/*
 * Code from: http://stackoverflow.com/questions/7361135/how-to-change-color-and-font-on-listview
 */
@SuppressWarnings("rawtypes")
private class CustomListAdapter extends ArrayAdapter {

    private Context mContext;
    private int id;
    private List <String>items ;
    private String[] mColors;

    @SuppressWarnings("unchecked")
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