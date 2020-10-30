package com.example.magnetometer_contact_tracing;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.os.AsyncTask;
import android.telephony.CellIdentityCdma;
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityWcdma;
import android.telephony.CellInfo;
import android.telephony.CellInfoCdma;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoWcdma;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.TextView;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.opencsv.CSVWriter;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import javax.net.ssl.HttpsURLConnection;

public class Record extends Activity implements SensorEventListener {
    TextView field_strength, time;
    Button rec_stop,detect;
    //String[] info;
    int sensorid;
    Sensor sensorObj;
    private SensorManager sensorManager;
    FileWriter opfile;
    CSVWriter writer;
    int count=0;
    private DecimalFormat df=new DecimalFormat("#.####");
    private DecimalFormat dffield=new DecimalFormat("#.##");
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent=getIntent();
        sensorid=intent.getIntExtra(MainActivity.SENSOR_INFO,-1);
        logging(sensorid, "ONINTENT START");
        //String intentstr=intent.getStringExtra(MainActivity.SENSOR_INFO);
        //info=intentstr.split("\n");
        setContentView(R.layout.record_screen);
        rec_stop=findViewById(R.id.record);
        detect=findViewById(R.id.button);
        //field_strength=findViewById(R.id.magnetic_field);
        //time=findViewById(R.id.time);
        get_or_createFile();
        recButton();
    }
    @Override
    public void onResume(){
        super.onResume();
        if(count==1){
            sensorManager.registerListener(Record.this, sensorObj, 100000);
        }
        get_or_createFile();
        recButton();

    }

    @Override
    public void onPause(){
        super.onPause();
        sensorManager.unregisterListener(Record.this);

    }

    @Override
    public final void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Do something here if sensor accuracy changes.
    }
    @Override
    public final void onSensorChanged(SensorEvent event){
        float[] magxyz=event.values;
        float x=magxyz[0];
        float y=magxyz[1];
        float z=magxyz[2];
        long timestamp=event.timestamp;
        double time_in_sec_double=timestamp*Math.pow(10,-9);
        String timeinsec=df.format(time_in_sec_double)+" sec";
        //String text = "("+x+", "+y+", "+z+")uT";
        double magnitude=Math.pow(x,2)+Math.pow(y,2)+Math.pow(z,2);
        magnitude=Math.sqrt(magnitude);
        String mag=dffield.format(magnitude)+" uT";
        logging(magnitude,"ONSENSORCHANGED");
        record_data(magnitude, time_in_sec_double);
        //field_strength.setText(mag);
        //time.setText(timeinsec);
        logging(x, "X FIELD(in uT)");
        logging(y, "Y FIELD(in uT)");
        logging(z, "Z FIELD(in uT)");

    }
    public void detectButton(){
        rec_stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                detectContact();
            }
        });
    }
    public void recButton(){
        sensorManager=(SensorManager) getSystemService(Context.SENSOR_SERVICE);
        /*rec_stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                count=count+1;*/
                if(sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)!=null){
                    //switch(count) {
                        //case 1:
                            List<Sensor> magsensors = sensorManager.getSensorList(Sensor.TYPE_MAGNETIC_FIELD);
                            if(sensorid==-123445){
                                sensorObj=sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
                            }
                            else {
                                for (int i = 0; i < magsensors.size(); i++) {
                                    sensorObj = magsensors.get(i);
                                    logging(sensorObj.getId(), "ONRECORD PRESS");
                                    if (sensorid == sensorObj.getId()) {
                                        break;
                                    }
                                }
                            }
                            //rec_stop.setText(R.string.stop);
                            sensorManager.registerListener(Record.this, sensorObj, 100000);
                            //break;
                        /*case 2:
                            rec_stop.setText(R.string.record);
                            count=0;
                            try{
                                writer.close();
                                opfile.close();
                            }
                            catch(IOException e){
                                Log.d("STOP EXCEPTION", e.toString());
                            }
                            sensorManager.unregisterListener(Record.this);
                            break;*/
                    //}
                }
                else{
                    Toast.makeText(Record.this,R.string.no_sensor,Toast.LENGTH_SHORT).show();
                }
            //}
        //});
    }

    public void record_data(double field, double timestamps){
        File file=new File(this.getFilesDir(),"strength");
        Log.d("CSV COME ON:",getApplicationContext().getExternalFilesDir(null).getPath());
        logging(Boolean.toString(file.isFile()),"FILE OR NOT");
        get_or_createFile();
        String[] header={Double.toString(field),Double.toString(timestamps)};
        if(writer==null){
            logging("writer null","RECORD_DATA");
        }
        writer.writeNext(header);
    }

    private void get_or_createFile() {
        String confFolderName ;
        String folderName=getFolderName();
        File storage = getApplicationContext().getExternalFilesDir(null);
        File conf;
        boolean mkdir=false;
        if(todayFolderName().equals(folderName)){
            confFolderName=folderName+"/"+getFileName();
            conf= new File(storage, confFolderName);
        }
        else{
            confFolderName=todayFolderName();
            write_foldername(confFolderName);
            conf= new File(storage, confFolderName+"/");
            mkdir=conf.mkdir();
            conf= new File(storage, confFolderName+"/"+getFileName());
            logging((mkdir?"DIRECTORY SUCCESSFULLy CREATED":"DIRECTORY CREATION FAILED"),"GET_OR_CREATEFILE");
        }
        if(mkdir){
            logging("EXISTS","GET_OR_CREATE_PATH_1");
        }
        else{
            logging(getFileName(),"GET_OR_CREATE_PATH_1");
        }
        logging(conf.getAbsolutePath(),"GET_OR_CREATE_PATH");
        try {
            Log.d("END_FILE_NAME",confFolderName);

            opfile = new FileWriter(conf,true);
            writer = new CSVWriter(opfile);
        }
        catch (IOException e){
            Log.d("EXCEPTION_get_or_create", e.toString());
        }
    }

    public String getFolderName() {

        SharedPreferences timet=this.getPreferences(Context.MODE_PRIVATE);
        String ret;
        String filename = timet.getString("FOLDER_NAME", "NO_VALUE");
        Log.d("GET_FILE_NAME",filename);
        if (!filename.equals("NO_VALUE")) {
            ret=filename;
        } else {
            int date= Calendar.getInstance().get(Calendar.DAY_OF_MONTH);
            int month= Calendar.getInstance().get(Calendar.MONTH);
            int year=Calendar.getInstance().get(Calendar.YEAR);
            ret=year+"_"+month+"_"+date;
            write_foldername(ret);
        }
        if(timet.getString("INSTALL_DATE","NO_VALUE").equals("NO_VALUE")){
            writeValues("INSTALL_DATE",ret);
        }

        return ret;
    }
    public void write_foldername(String filename) {

        SharedPreferences store = this.getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = store.edit();
        editor.putString("FOLDER_NAME", filename);
        Log.d("WRITE_FOLDER_NAME", filename+" WRITTEN");
        editor.apply();
    }
    public String todayFolderName(){
        int date= Calendar.getInstance().get(Calendar.DAY_OF_MONTH);
        int month= Calendar.getInstance().get(Calendar.MONTH);
        int year=Calendar.getInstance().get(Calendar.YEAR);
        return year+"_"+month+"_"+date;
    }
    public double[] returnArray=new double[3];
    public double[] get_location(){
        JSONObject json=new JSONObject();
        JSONObject celltower=new JSONObject();
        ArrayList<JSONObject> celltowers=new ArrayList<>();
        int mcc=getApplicationContext().getResources().getConfiguration().mcc;
        int mnc=getApplicationContext().getResources().getConfiguration().mnc;
        TelephonyManager telephonyManager=(TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);

        String radiotype="";
        switch(telephonyManager.getNetworkType()){
            case TelephonyManager.NETWORK_TYPE_CDMA:
                radiotype="cdma";
                break;
            case TelephonyManager.NETWORK_TYPE_GSM:
                radiotype="gsm";
                break;
            case TelephonyManager.NETWORK_TYPE_LTE:
                radiotype="lte";
                break;
            case TelephonyManager.NETWORK_TYPE_UMTS:
                radiotype="umts";
                break;
            default:
                radiotype="NO_DATA";
                break;
        }
        int cid,lac;
        String mccc,mncc;
        List<CellInfo> cellInfoList=null;
        try {
             cellInfoList= telephonyManager.getAllCellInfo();
        }
        catch (SecurityException e){
            logging(e.toString(),"GET_LOCATION");
        }
        if(cellInfoList!=null) {
            for (CellInfo cellInfo : cellInfoList) {
                if(cellInfo instanceof CellInfoWcdma) {
                    CellInfoWcdma cellInfoCdma = (CellInfoWcdma) cellInfo;
                    CellIdentityWcdma cellIdentityWCdma = cellInfoCdma.getCellIdentity();
                    cid = cellIdentityWCdma.getCid();
                    lac = cellIdentityWCdma.getLac();
                    mccc = cellIdentityWCdma.getMccString();
                    mncc = cellIdentityWCdma.getMncString();
                    try {
                        celltower.put("cellId", cid);
                        celltower.put("locationAreaCode",lac);
                        celltower.put("mobileCountryCode",mccc);
                        celltower.put("mobileNetworkCode",mncc);
                    }
                    catch (org.json.JSONException e){
                        logging("JSON EXCEPTION","GET_LOCATION");
                    }
                }
                else if(cellInfo instanceof CellInfoGsm) {
                    CellInfoGsm cellInfoGsm = (CellInfoGsm) cellInfo;
                    CellIdentityGsm cellIdentityGsm = cellInfoGsm.getCellIdentity();
                    cid = cellIdentityGsm.getCid();
                    lac = cellIdentityGsm.getLac();
                    mccc = cellIdentityGsm.getMccString();
                    mncc = cellIdentityGsm.getMncString();
                    try {
                        celltower.put("cellId", cid);
                        celltower.put("locationAreaCode",lac);
                        celltower.put("mobileCountryCode",mccc);
                        celltower.put("mobileNetworkCode",mncc);
                    }
                    catch (org.json.JSONException e){
                        logging("JSON EXCEPTION","GET_LOCATION");
                    }
                }
                else{
                    logging("Cellinfo nope","GETLOCATION");
                }
                celltowers.add(celltower);
            }
        }
        else{
            logging("CELLINFOLIST NULL","GETLOCATION");
        }
        String carrier;
        boolean considerip=true;
        if(telephonyManager.getSimState()==TelephonyManager.SIM_STATE_READY){
            carrier=telephonyManager.getSimOperatorName();
        }
        else{
            carrier=telephonyManager.getNetworkOperatorName();
        }
        try {
            json.put("homeMobileCountryCode", mcc);
            json.put("homeMobileNetworkCode",mnc);
            json.put("radioType",radiotype);
            json.put("carrier",carrier);
            json.put("considerIp",considerip);
            json.put("cellTowers",celltowers);
        }
        catch (org.json.JSONException e){
            logging("JSON EXCEPTION","GET_LOCATION");
        }
        URL url;
        double latitude=-12,longitude=-12,accuracy=-12;

        try {
            url = new URL("https://www.googleapis.com/geolocation/v1/geolocate?key=AIzaSyBj1kkb_0_nW9WSFS04NrupIGLSivsQ2Zg");
            HttpsURLConnection httpsURLConnection=(HttpsURLConnection)url.openConnection();
            httpsURLConnection.setRequestMethod("POST");
            httpsURLConnection.setDoOutput(true);
            httpsURLConnection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            try(OutputStream outputStream=httpsURLConnection.getOutputStream()){
                outputStream.write(json.toString().getBytes(StandardCharsets.UTF_8));
            }
            try{
                InputStream inputStream=httpsURLConnection.getInputStream();
                ByteArrayInputStream byteArrayInputStream=(ByteArrayInputStream)inputStream;
                int available_bytes=byteArrayInputStream.available();
                byte[] b=new byte[available_bytes];
                if(byteArrayInputStream.read(b,0,available_bytes)>=-1){
                    String responsestr=new String(b,StandardCharsets.UTF_8);
                    JSONObject response=new JSONObject(responsestr);
                    JSONObject location=response.getJSONObject("location");
                    accuracy=response.getDouble("accuracy");
                    latitude=location.getDouble("lat");
                    longitude=location.getDouble("lng");
                }
                else{
                    logging("INPUT STREAM PROBLEMS","GET_LOCATION");
                }
            }
            catch (JSONException e){
                logging(e.toString(),"GET_LOCATION");
            }
            catch (SocketTimeoutException e){
                logging("socket timeout","GET_LOCATION");
            }
            catch (IOException e){
                logging("INputstream ioexception","GET_LOCATION");
            }
        }
        catch (java.net.MalformedURLException excp){
            logging("Malformed URL","GET_LOCATION");
        }
        catch (java.io.IOException io){
            logging("Openconnection io exception","GET_LOCATION");
        }
        logging(radiotype,"TELEPHONY MANAGER_GET_LOCATION");
        logging(mcc,"MobileCOuntryCode");
        logging(mnc,"MobileNetworkCOde");
        returnArray[0]=latitude;
        returnArray[1]=longitude;
        returnArray[2]=accuracy;
        return returnArray;
    }
    public void writeValues(String Key,String Value){
        SharedPreferences store = this.getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = store.edit();
        editor.putString(Key, Value);
        editor.apply();
    }
    public String getValues(String Key){
        SharedPreferences store = this.getPreferences(Context.MODE_PRIVATE);
        return store.getString(Key,"NO_VALUE");
    }
    public String getFileName(){
        LocalTime localTime=LocalTime.now();
        Integer integer;
        Double doubl_lat,doubl_lng,doubl_acc;
        String filename;
        logging("GET_LOCATION","GET_LOCATION");
        if(getValues("REQUEST_TIME").equals("NO_VALUE")){
            logging("NO_VALUE","GET_LOCATION");
            integer=localTime.getMinute();
            String minute=integer.toString();
            writeValues("REQUEST_TIME",minute);
            new RetrieveFeedTask().execute();
            double[] location=returnArray;
            doubl_lat=location[0];
            doubl_lng=location[1];
            doubl_acc=location[2];
            filename=doubl_lat.toString()+"_"+doubl_lng.toString()+"_"+doubl_acc+".csv";
            writeValues("CURRENT_FILE_NAME",filename);
        }
        else{
            int prevRequest=Integer.valueOf(getValues("REQUEST_TIME")).intValue();
            int currentminute=localTime.getMinute();
            int diff=Math.abs(currentminute-prevRequest);
            logging(diff,"GET_LOCATION");
            if(diff==0){
                new RetrieveFeedTask().execute();
                double[] location=returnArray;
                doubl_lat=location[0];
                logging(doubl_lat,"GET_LOCATION_LAT");
                doubl_lng=location[1];
                doubl_acc=location[2];
                filename=doubl_lat.toString()+"_"+doubl_lng.toString()+"_"+doubl_acc+".csv";
                writeValues("CURRENT_FILE_NAME",filename);
                integer=currentminute;
                writeValues("REQUEST_TIME",integer.toString());
            }
            else{
                filename=getValues("CURRENT_FILE_NAME");
            }

        }
        return filename;
    }
    public OutputStream request;
    public InputStream op;
    public HttpsURLConnection httpConn;
    public void detectContact(){
        String fileName;
        String[] location;
        String boundary="****";
        String crlf="\r\n";

        int c=0;
        InputStream is;
        ArrayList<Double> lat=new ArrayList<>();
        ArrayList<Double> lng=new ArrayList<>();
        ArrayList<Double> acc=new ArrayList<>();
        SharedPreferences store=this.getPreferences(Context.MODE_PRIVATE);
        try{
            URL url=new URL("");
            httpConn=(HttpsURLConnection)url.openConnection();
            httpConn.setUseCaches(false);
            httpConn.setDoOutput(true);
            httpConn.setDoInput(true);
            httpConn.setRequestMethod("POST");
            //alter this part if you wanna set any headers or something
            httpConn.setRequestProperty("Connection", "Keep-Alive");
            httpConn.setRequestProperty("Cache-Control", "no-cache");
            httpConn.setRequestProperty(
                    "Content-Type", "multipart/form-data;boundary=" + boundary);
            request = httpConn.getOutputStream();
        }
        catch (java.net.MalformedURLException e){
            logging(e.toString(),"DETECT_CONTACT");
        }
        catch (java.io.IOException e){
            logging(e.toString(),"DETECT_CONTACT");
        }
        String currentFolderName=store.getString("FOLDER_NAME","NO_VALUE");
        if(!currentFolderName.equals("NO_VALUE")){
            File storage = getApplicationContext().getExternalFilesDir(null);
            File conf=new File(storage,currentFolderName+"/");
            File[] traces=conf.listFiles();
            if(traces!=null) {
                for (File trace : traces) {
                    fileName=trace.getName();
                    fileName=fileName.substring(0,fileName.length()-3);
                    logging(fileName,"DETECT_CONTACT_FOR_LOOP");
                    location=fileName.split("_");
                    lat.add(Double.valueOf(location[0]));
                    lng.add(Double.valueOf(location[1]));
                    acc.add(Double.valueOf(location[2]));
                    try {
                        request.write(("--" + boundary + crlf).getBytes());
                        request.write(("Content-Disposition: form-data; name=\"" +
                                "trace" + c + "\";filename=\"" +
                                trace.getName() + "\"" + crlf).getBytes());
                        request.write(crlf.getBytes());
                        try {
                            is = new FileInputStream(trace);
                            byte[] bytes = new byte[1024];
                            int c_is = is.read(bytes);
                            while (c_is > 0) {
                                request.write(bytes, 0, c);
                                c_is = is.read(bytes);
                            }
                            request.write(crlf.getBytes());
                            is.close();
                        } catch (java.io.FileNotFoundException r) {
                            logging(r.toString(), "DETECT_CONTACT");
                        }
                    }
                    catch (java.io.IOException r){
                        logging(r.toString(),"DETECT_CONTACT");
                    }
                    c=c+1;

                }
                try {
                    request.write(("--" + boundary + crlf).getBytes());
                    String value;
                    for(Double latval:lat) {
                        value=latval.toString();
                        request.write(("Content-Disposition: form-data; name=\"" + "lat[]" + "\"" + crlf).getBytes());
                        request.write(crlf.getBytes());
                        request.write((value).getBytes());
                        request.write(crlf.getBytes());
                    }
                    for(Double lngval:lat) {
                        value=lngval.toString();
                        request.write(("Content-Disposition: form-data; name=\"" + "lng[]" + "\"" + crlf).getBytes());
                        request.write(crlf.getBytes());
                        request.write((value).getBytes());
                        request.write(crlf.getBytes());
                    }
                    for(Double accval:acc) {
                        value=accval.toString();
                        request.write(("Content-Disposition: form-data; name=\"" + "acc[]" + "\"" + crlf).getBytes());
                        request.write(crlf.getBytes());
                        request.write((value).getBytes());
                        request.write(crlf.getBytes());
                    }
                    request.flush();
                }
                catch (java.io.IOException t){
                    logging(t.toString(),"DETECT_CONTACT");
                }
                try {
                    op = httpConn.getInputStream();
                    logging(op.toString(),"DETECT_CONTACT");
                }
                catch (java.io.IOException r){
                    logging(r.toString(),"DETECT_CONTACT");
                }
            }
            else{
                logging("NULL","DETECT_CONTACT");
            }

        }
        else{
            logging("NO VALUE","DETECT_CONTACT");
        }
    }
    public void logging(String msg, String tag){
        Log.d(tag, msg);
    }
    public void logging(double msg, String tag){
        Log.d(tag, String.valueOf(msg));
    }
    public void logging(float msg, String tag){
        Log.d(tag, String.valueOf(msg));
    }
    public void logging(int msg, String tag){
        Log.d(tag, String.valueOf(msg));
    }


}
class RetrieveFeedTask extends AsyncTask<Void, Void, String> {

    private Exception exception;
    @Override
    protected String doInBackground(Void... urls) {
        try {
            Record a=new Record();
            a.get_location();

        } catch (Exception e) {
            Log.d("ASYNCTASK",e.toString());


        } finally {

        }
        return "EXECUTED";
    }

    protected void onPostExecute() {
        // TODO: check this.exception
        // TODO: do something with the feed
    }
}