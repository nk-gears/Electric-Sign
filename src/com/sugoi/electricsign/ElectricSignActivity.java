package com.sugoi.electricsign;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Picture;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.URLUtil;
import android.webkit.WebView;
import android.webkit.WebView.PictureListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;

public class ElectricSignActivity extends Activity implements TextWatcher
{
   public void onCreate(Bundle savedInstanceState) 
   {
      super.onCreate(savedInstanceState);

      Log.i(ElectricSignActivity.LOG_TAG, "Parkwood HOA Announcements starting up!");
      requestWindowFeature(Window.FEATURE_NO_TITLE);  // might as well save some space

      LinearLayout linLayout = new LinearLayout(this);

      // Set up the settings view (that comes up at launch)
      _settingsView = new RelativeLayout(this);
      {
 		 TextView title = new TextView(this);
 		 title.setText("Electric Sign Settings");
 		 title.setGravity(Gravity.CENTER);
 		 title.setTextSize(28);
 		 
    	 RelativeLayout.LayoutParams tlp = new RelativeLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT);
    	 tlp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
    	 tlp.addRule(RelativeLayout.CENTER_HORIZONTAL);
    	 _settingsView.addView(title, tlp);
 	 
 		 LinearLayout topArea = new LinearLayout(this);
    	 topArea.setOrientation(LinearLayout.VERTICAL);
    	 {
    		 LinearLayout urlLine = new LinearLayout(this);
    		 {
    		    TextView urlText = new TextView(this);
    		    urlText.setText("Load: ");
    		    urlLine.addView(urlText);
    		    
    		    _urlSetting = new EditText(this);
    		    _urlSetting.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_URI);
    		    _urlSetting.addTextChangedListener(this);
    		    urlLine.addView(_urlSetting, new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT));
    		 }
    		 topArea.addView(urlLine);
    		 
    		 LinearLayout freqLine = new LinearLayout(this);
    		 {
    		    TextView everyText = new TextView(this);
    		    everyText.setText("Every ");
    		    freqLine.addView(everyText);
    		    
    		    _freqCountSetting = new Spinner(this);
    		    _freqCountSetting.setAdapter(new FreqCountArrayAdapter(this));
    		    freqLine.addView(_freqCountSetting);
    		    
    		    _freqUnitsSetting = new Spinner(this);
    		    _freqUnitsSetting.setAdapter(new FreqUnitsArrayAdapter(this));
    		    freqLine.addView(_freqUnitsSetting);
    		 }
    		 topArea.addView(freqLine);
    		 
    		 _allowSleepSetting = new CheckBox(this);
    		 _allowSleepSetting.setText("Put device to sleep between updates");
    		 topArea.addView(_allowSleepSetting);
         
    		 _writeScreenSaverSetting = new CheckBox(this);
    		 _writeScreenSaverSetting.setText("Write screenshots to screen-saver");
    		 topArea.addView(_writeScreenSaverSetting);
    	 }
    	 
    	 RelativeLayout.LayoutParams mlp = new RelativeLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT);
    	 mlp.addRule(RelativeLayout.CENTER_IN_PARENT);
    	 _settingsView.addView(topArea, mlp);
    	 
    	 RelativeLayout botArea = new RelativeLayout(this);
    	 {  		
    	    _goButton = new Button(this);
    	    _goButton.setText("Start Display");
    	    _goButton.setOnClickListener(new View.OnClickListener() {
    		    public void onClick(View v) {
    			    ElectricSignActivity.this.startDisplay();
    		    }
    	    });
    	    RelativeLayout.LayoutParams gop = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
       	    gop.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
    	    botArea.addView(_goButton, gop);
    	    
       	    Button cancelButton = new Button(this);
    	    cancelButton.setText("Cancel");
    	    cancelButton.setOnClickListener(new View.OnClickListener() {
    		    public void onClick(View v) {
    			    ElectricSignActivity.this.finish();
    		    }
    	    });
    	    RelativeLayout.LayoutParams cop = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
       	    cop.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
    	    botArea.addView(cancelButton, cop);
    	 }
    	     	 
    	 RelativeLayout.LayoutParams blp = new RelativeLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT);
    	 blp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
    	 blp.addRule(RelativeLayout.CENTER_HORIZONTAL);
    	 _settingsView.addView(botArea, blp);
      }
      linLayout.addView(_settingsView);
      
      // Set up registration for alarm events (we use these so that we'll get them even if the Nook is asleep at the time)
      _alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
      Intent intent = new Intent(ALARM_REFRESH_ACTION);
      _pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
      _alarmReceiver = new BroadcastReceiver() {
         public void onReceive(Context context, Intent intent) {
            if (isSleepAllowed()) setKeepScreenAwake(true);  // necessary to make sure the Nook doesn't go back to sleep as soon as this method returns!
            doReload();
         }
      };
        
      // We register dataReceiver to listen ALARM_REFRESH_ACTION
      IntentFilter filter = new IntentFilter(ALARM_REFRESH_ACTION);
      registerReceiver(_alarmReceiver, filter);
        
      DisplayMetrics displaymetrics = new DisplayMetrics();
      getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
      _width = displaymetrics.widthPixels;
      _height = displaymetrics.heightPixels;
        
      _webView = new WebView(this);
      _webView.setPictureListener(new PictureListener() {
         public void onNewPicture(WebView view, Picture picture) {
            if (picture != null) saveScreenshotToScreensaversFolder(picture);
         }
      });
      _webView.setVisibility(View.GONE);  // we'll make the web view visible when the settings view is dismissed
        
      linLayout.addView(_webView, new LinearLayout.LayoutParams(_width, LayoutParams.FILL_PARENT));
      _contentView = linLayout;
      setContentView(_contentView);

      getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
      loadSettings();
   }
   
   public void afterTextChanged(Editable s)
   {
	   _urlSetting.setBackgroundColor(isUrlValid() ? Color.WHITE : Color.RED);
	   updateGoButtonEnabled();
   }
   public void beforeTextChanged(CharSequence s, int start, int count, int after) {/* empty */}
   public void onTextChanged(CharSequence s, int start, int before, int count) {/*empty */}
   
   public void startDisplay() {
	  saveSettings();
	  
	  long freqCount = Long.parseLong(_freqCountSetting.getSelectedItem().toString());
	  long millisBase = 1000;  
	  String unit = _freqUnitsSetting.getSelectedItem().toString().toLowerCase();
	       if (unit.contains("min"))  millisBase *= 60;
	  else if (unit.contains("hour")) millisBase *= (60*60);
	  else if (unit.contains("day"))  millisBase *= (24*60*60);
	  _reloadIntervalMillis = freqCount * millisBase; 
	  
      _settingsView.setVisibility(View.GONE);
      _webView.setVisibility(View.VISIBLE);
      _nextUpdateTime = scheduleReload(0);  // schedule a download for ASAP
      if (isSleepAllowed() == false) setKeepScreenAwake(true);
   }
    
   public void onDestroy() {
       unregisterReceiver(_alarmReceiver);
       super.onDestroy();
   }
      
   private class DownloadFileTask extends AsyncTask<String,Void,String>
   {
      protected String doInBackground(String... url) {return ElectricSignActivity.this.downloadHTML(url[0]);}

      protected void onPostExecute(String html) 
      {
         int htmlIdx = html.toLowerCase().indexOf("<body");
         if (htmlIdx>=0) html = html.substring(htmlIdx);
         String battPercent = "";
         int bp = (int)(100.0*getBatteryRemainingPercent());
         if (bp >= 0) battPercent = ", "+bp+"%";

         String dateTimeStr = getDateTime();
         html = html.replace(">parkwood</a>", ">Current as of "+dateTimeStr+" ("+_completeTimeMillis+"mS"+battPercent+")</a>");
         if (html.length() > 0) 
         {
            Log.d(ElectricSignActivity.LOG_TAG, "Updating sign display at "+ dateTimeStr + " ("+html.length()+" characters)");
            _webView.loadDataWithBaseURL(getUrl(), html, "text/html", "utf-8", null);
            _displayingSign = true;
         }

         Log.d(ElectricSignActivity.LOG_TAG, "Sign update complete, turning off Wifi and going to sleep for "+_freqCountSetting.getSelectedItem().toString()+" "+_freqUnitsSetting.getSelectedItem().toString().toLowerCase()+".");
         _nextUpdateTime = scheduleReload(_reloadIntervalMillis);

         if (isSleepAllowed())
         {
        	 WifiManager wifi = (WifiManager) ElectricSignActivity.this.getSystemService(Context.WIFI_SERVICE);              
        	 wifi.setWifiEnabled(false);   // disable wifi between refreshes, to save power
        	 setKeepScreenAwake(false);    // back to sleep until next time!   
        	 _wifiShouldWorkAtTime = 0;    // note that we're not waiting for Wifi to start up anymore
         }
      }
   }
    
   public void doReload() 
   {
      WifiManager wifi = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);
      if ((isSleepAllowed())&&(wifi.isWifiEnabled() == false))
      {
         _wifiAttemptNumber++;
         String attStr = "(attempt #"+_wifiAttemptNumber+")";
         boolean worked = wifi.setWifiEnabled(true);
         if (_displayingSign == false) 
         {
            String statusStr;
             
            if (worked) statusStr = "Turning on WiFi, please wait one minute... "+attStr;
                   else statusStr = "Error enabling WiFi!  "+attStr;            
            _webView.loadDataWithBaseURL("dummy", statusStr, "text/html", "utf-8", null);
         }
         if (worked) Log.d(ElectricSignActivity.LOG_TAG, "Turning on Wifi:  Will wait 45 seconds for the Wifi to wake up and connect to the AP...  "+attStr);
                else Log.d(ElectricSignActivity.LOG_TAG, "Error enabling Wifi, will try again soon!  "+attStr);
         _wifiShouldWorkAtTime = scheduleReload(45*1000);
      }
      else
      {
         long now = System.currentTimeMillis();
         if (now < _wifiShouldWorkAtTime)
         {
            Log.d(ElectricSignActivity.LOG_TAG, "Hmm, we got woken up a bit early, WiFi may not be available yet.  Back to sleep a bit longer...");
            scheduleReload(_wifiShouldWorkAtTime-now);
         }
         else if (now < _nextUpdateTime)
         {
            Log.d(ElectricSignActivity.LOG_TAG, "Hmm, we got woken up a bit early, it's not time for a display refresh yet.  Back to sleep a bit longer...");
            scheduleReload(_nextUpdateTime-now);
         }
         else
         {
            // Do the download in a separate thread, to avoid visits from the ANR if the network is slow
            new DownloadFileTask().execute(getUrl());
         }
      }
   }
     
   private void setKeepScreenAwake(boolean awake)
   {
      int flags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON;
      Window win = getWindow();
      if (awake) 
      {
         PowerManager powerManager = (PowerManager) ElectricSignActivity.this.getSystemService(Context.POWER_SERVICE);
         powerManager.userActivity(SystemClock.uptimeMillis(), false);   //false will bring the screen back as bright as it was, true - will dim it
         win.addFlags(flags);
      }
      else win.clearFlags(flags);
   }

   private long scheduleReload(long millis) 
   {
      Calendar calendar = Calendar.getInstance();
      calendar.setTimeInMillis(System.currentTimeMillis());
      calendar.add(Calendar.MILLISECOND, (int)millis);
      long wakeupTime = calendar.getTimeInMillis();
      _alarmManager.set(AlarmManager.RTC_WAKEUP, wakeupTime, _pendingIntent);
      return wakeupTime;
   }
    
   private double getBatteryRemainingPercent()
   {
      Intent batteryIntent = getApplicationContext().registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
      int rawlevel = batteryIntent.getIntExtra("level", -1);
      double scale = batteryIntent.getIntExtra("scale", -1);
      double level = -1;
      if ((rawlevel >= 0) && (scale > 0)) level = rawlevel / scale;
      return level;
   }
    
   public String downloadHTML(String downloadUrl)
   {
      long startTime = System.nanoTime();
       
      StringBuffer buf = new StringBuffer(); 
      try
      {
         URL url = new URL(downloadUrl);
     
         URLConnection conn = url.openConnection();
         conn.setConnectTimeout(30*1000);
         conn.setReadTimeout(30*1000);
            
         BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()), 8192);
         String inputLine;
         while((inputLine = in.readLine()) != null) 
         {
            buf.append(inputLine + "\r\n");
            if ((System.nanoTime()-startTime) >= (((long)30000)*1000*1000))
            {
               Log.e(ElectricSignActivity.LOG_TAG, "HTML download is taking too long, giving up!");
               buf = new StringBuffer();
               break;
            }
         }
         in.close();
      }
      catch(Exception e)
      {
         Log.e(ElectricSignActivity.LOG_TAG, "Exception caught for "+downloadUrl+", exception is: "+e.toString());
         buf = new StringBuffer();
      }
       
      _completeTimeMillis = (int)((System.nanoTime()-startTime)/1000000);        
      Log.d(ElectricSignActivity.LOG_TAG, "Download of ["+downloadUrl+"] took "+_completeTimeMillis+"milliSeconds, downloaded "+buf.length()+" bytes.");            
      return buf.toString();
   }
    
   private String getDateTime() 
   {
      DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
      Date date = new Date();
      return dateFormat.format(date);
   }

   public boolean dispatchTouchEvent(MotionEvent e)
   {
	  if (_webView.getVisibility() == View.VISIBLE)
	  {
         Log.d(ElectricSignActivity.LOG_TAG, "Screen touched, exiting!");
         finish();
         return true;
	  }
	  else return super.dispatchTouchEvent(e);      
   }
   
   private void saveScreenshotToScreensaversFolder(Picture p)
   {
	  if (_writeScreenSaverSetting.isChecked() == false) return;
	  
      Bitmap b = Bitmap.createBitmap(_width, _height, Config.ARGB_8888); 
      Canvas c = new Canvas(b); 
      try { 
         c.drawPicture(p);
      } 
      catch (Exception e) { 
         e.printStackTrace(); 
      } 
        
      // Create the necessary directory, if it isn't already there
      String screenshotDir = "/media/screensavers/ElectricSign";
      try {
         File f = new File(screenshotDir);
         f.mkdir();
      }
      catch(Exception e) {
         e.printStackTrace();
      }
 
      // Then create the file in the directory
      String fileName = screenshotDir;
      if (fileName.endsWith("/") == false) fileName = fileName + "/";
      fileName = fileName + "ElectricSignContents.png";

      File imageFile = new File(fileName);
      try {
         OutputStream fout = new FileOutputStream(imageFile);
         b.compress(Bitmap.CompressFormat.PNG, 100, fout);
         fout.flush();
         fout.close();
         Log.d(ElectricSignActivity.LOG_TAG, "Updated screenshot file "+fileName);
      } catch (Exception e) {
         e.printStackTrace();
      }
   }
    
   private void loadSettings() 
   {
	   SharedPreferences s = getSharedPreferences(PREFS_NAME, 0);
	   _allowSleepSetting.setChecked(      s.getBoolean("allowSleep", true));
       _writeScreenSaverSetting.setChecked(s.getBoolean("writeScreenSaver", true));
       _urlSetting.setText(                s.getString("url", "http://sites.google.com/"));
       
       setSpinnerSettingWithDefault(_freqCountSetting, s.getString("freq",  ""), "6");
       setSpinnerSettingWithDefault(_freqUnitsSetting, s.getString("units", ""), "Minute(s)");
   }
   
   private void setSpinnerSettingWithDefault(Spinner spinner, String val, String defVal)
   {
       ArrayAdapter<String> a = (ArrayAdapter<String>) spinner.getAdapter(); //cast to an ArrayAdapter
       int pos = a.getPosition(val);
       if (pos < 0) pos = a.getPosition(defVal);
       if (pos >= 0) spinner.setSelection(pos);
   }
   
   private void saveSettings()
   {
	   SharedPreferences s = getSharedPreferences(PREFS_NAME, 0);
	   SharedPreferences.Editor e = s.edit();
	   e.putBoolean("allowSleep",       isSleepAllowed());     
	   e.putBoolean("writeScreenSaver", isWriteScreenSaverFileAllowed());
	   e.putString("url",               _urlSetting.getText().toString());  // not using getUrl() because I don't want auto-correct here
	   e.putString("freq",              _freqCountSetting.getSelectedItem().toString());
	   e.putString("units",             _freqUnitsSetting.getSelectedItem().toString());
	   e.commit();
   }
   
   private String getUrl()
   {
      String ret = _urlSetting.getText().toString();
      if (URLUtil.isValidUrl(ret) == false) ret = "http://"+ret;
      return ret;
   } 
   
   private boolean isSleepAllowed()
   {
      return _allowSleepSetting.isChecked();
   }
   
   private boolean isWriteScreenSaverFileAllowed()
   {
	   return _writeScreenSaverSetting.isChecked();
   }
   
   private boolean areAllSettingsValid()
   {
	  return isUrlValid();   // the only criterion for now
   }
   
   private boolean isUrlValid()
   {
      String url = _urlSetting.getText().toString();   // not using getUrl() becaues I don't want auto-correct here
      return ((url.length() > 0)&&((URLUtil.isValidUrl(url))||URLUtil.isValidUrl("http://"+url)));
   }
   
   private void updateGoButtonEnabled()
   {
	   _goButton.setEnabled(areAllSettingsValid());
   }
    
   private class FreqCountArrayAdapter extends ArrayAdapter<String> {
	   public FreqCountArrayAdapter(Context ctxt) {super(ctxt, -1);}
   	
	   public String getItem(int position) {return Integer.toString(_freqVals[position]);}
	   public int getCount() {return _freqVals.length;}
	   public int getPosition(String s)
	   {
	      for (int i=getCount()-1; i>=0; i--) if (s.equalsIgnoreCase(getItem(i))) return i;
	      return -1;
	   }
	   
	   public View getView(int position, View convertView, ViewGroup parent)
	   {	    
		    if (convertView==null) convertView = new TextView(ElectricSignActivity.this);
		    TextView tv = (TextView) convertView; 
		    tv.setText(getItem(position));
		    tv.setTextColor(Color.BLACK);
		    return convertView;
	   }
	   
	   public View getDropDownView(int position, View convertView, ViewGroup parent) {return getView(position, convertView, parent);}

	   private final int _freqVals[] = {1, 2, 3, 4, 5, 6, 8, 10, 12, 15, 30};
   };

   private class FreqUnitsArrayAdapter extends ArrayAdapter<String> {
	   public FreqUnitsArrayAdapter(Context ctxt) {super(ctxt, -1);}
   	
	   public String getItem(int position) {return _freqUnits[position];}
	   public int getCount() {return _freqUnits.length;}
	   public int getPosition(String s)
	   {
	      for (int i=getCount()-1; i>=0; i--) if (s.equalsIgnoreCase(getItem(i))) return i;
	      return -1;
	   }

	   public View getView(int position, View convertView, ViewGroup parent)
	   {	    
		    if (convertView==null) convertView = new TextView(ElectricSignActivity.this);
		    TextView tv = (TextView) convertView; 
		    tv.setText(getItem(position));
		    tv.setTextColor(Color.BLACK);
		    return convertView;
	   }
	   public View getDropDownView(int position, View convertView, ViewGroup parent) {return getView(position, convertView, parent);}

	   private final String _freqUnits[] = {"Minute(s)", "Hour(s)", "Day(s)"};
   };
   
   //private String _url = "http://sites.google.com/site/parkwoodannounce/announcements";
   private long _reloadIntervalMillis;
   private AlarmManager _alarmManager;
   private BroadcastReceiver _alarmReceiver;
   private PendingIntent _pendingIntent;
   
   private View _contentView;
   private RelativeLayout _settingsView;
   private WebView _webView;
   private boolean _displayingSign = false;
   private int _wifiAttemptNumber = 0;
   private int _completeTimeMillis = 0;
   private int _width;
   private int _height;
    
   private static final String LOG_TAG    = "ElectricSign";
   private static final String PREFS_NAME = "ElectricSignPrefs";

   private long _nextUpdateTime       = 0;
   private long _wifiShouldWorkAtTime = 0;
    
   private static final String ALARM_REFRESH_ACTION = "com.sugoi.electricsign.ALARM_REFRESH_ACTION";
  
   private EditText _urlSetting;
   private CheckBox _allowSleepSetting;
   private CheckBox _writeScreenSaverSetting;
   private Spinner  _freqCountSetting;
   private Spinner  _freqUnitsSetting;
   
   private Button _goButton;
}
