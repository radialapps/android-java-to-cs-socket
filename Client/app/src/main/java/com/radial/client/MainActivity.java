package com.radial.client;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v13.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

public class MainActivity extends Activity {
    /* Defaults and statics */
    public static final String PREFS_NAME = "TCPClientConf";
    public static final int DEFAULT_QUALITY = 80;
    public static final int DEFAULT_DIM = 512;
    public static final int DEFAULT_PORT = 3800;
    private static final int CAMERA_REQUEST = 1888;
    private static final int BUFFER_SIZE = 1024;
    public static final String FILE_STORAGE_DIR = Environment.getExternalStorageDirectory().getAbsolutePath() + "/TCPClient/";

    /* Declare Controls */
    private EditText IPEditText;
    private EditText QualityEditText;
    private EditText DimensionEditText;
    private EditText PortEditText;
    private Switch PreserveCheckbox;
    private Button retryButton;
    private ImageView imageViewMain;

    private Uri mImageUri;
    private static Boolean sharedFile = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        if (Build.VERSION.SDK_INT >= 23) {
            if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            }
        }

        /* Initialize Controls */
        Button photoButton = (Button) this.findViewById(R.id.btnPhoto);
        Button saveButton = (Button) this.findViewById(R.id.btnSave);
        retryButton = (Button) this.findViewById(R.id.btnRetry);
        retryButton.setEnabled(false);

        IPEditText = (EditText)findViewById(R.id.txtip);
        QualityEditText = (EditText)findViewById(R.id.txtquality);
        DimensionEditText = (EditText)findViewById(R.id.txtdim);
        PortEditText = (EditText)findViewById(R.id.txtport);
        PreserveCheckbox = (Switch)findViewById(R.id.checkbox_preserve);
        imageViewMain = (ImageView)findViewById(R.id.imageView);

        /* Load Preferences */
        final SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);     
        IPEditText.setText(settings.getString("IP", ""));
        QualityEditText.setText(Integer.toString(settings.getInt("Quality", DEFAULT_QUALITY)));
        DimensionEditText.setText(Integer.toString(settings.getInt("Dimension", DEFAULT_DIM)));
        PortEditText.setText(Integer.toString(settings.getInt("Port", DEFAULT_PORT)));
        PreserveCheckbox.setChecked(settings.getBoolean("PreserveImage", false));

        photoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new ConnectTry().execute();
            }
        });

        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { savePreferences();

            }
        });

        retryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final SharedPreferences newsettings = getSharedPreferences(PREFS_NAME, 0);
                int ImageDim = newsettings.getInt("Dimension", DEFAULT_DIM);
                new SendAsync().execute(scaleDown(grabImage(), ImageDim, true));
            }
        });

        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();

        if (Intent.ACTION_SEND.equals(action) && type != null) {
            if (type.startsWith("image/")) {
                mImageUri = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
                if (mImageUri != null) {
                    sharedFile = true;
                    final SharedPreferences newsettings = getSharedPreferences(PREFS_NAME, 0);
                    int ImageDim = newsettings.getInt("Dimension", DEFAULT_DIM);
                    new SendAsync().execute(scaleDown(grabImage(), ImageDim, true));
                }
            }
        }
        else new ConnectTry().execute();
        
    }

    public final void notifyMediaStoreScanner(final File file) {
        try {
            Context mContext = getApplicationContext();
            MediaStore.Images.Media.insertImage(mContext.getContentResolver(),
                    file.getAbsolutePath(), file.getName(), null);
            mContext.sendBroadcast(new Intent(
                    Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file)));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void savePreferences()
    {
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        SharedPreferences.Editor editor = settings.edit();
        try{
                    /* Check for erroneous values */
            int Qual=Integer.parseInt(QualityEditText.getText().toString());
            int Dim=Integer.parseInt(DimensionEditText.getText().toString());
            int Port=Integer.parseInt(PortEditText.getText().toString());
            if (Qual > 100 || Qual < 0) throw new Exception("Invalid Quality Value");
            if (Dim < 0) throw new Exception("Invalid Dimension Value");
            if (Port <= 0 || Port >= 65534) throw new Exception("Invalid Port");

                    /* Save Preferences */
            editor.putInt("Quality", Qual);
            editor.putInt("Dimension", Dim);
            editor.putInt("Port", Port);
            editor.putString("IP", IPEditText.getText().toString());
            editor.putBoolean("PreserveImage", PreserveCheckbox.isChecked());
        }
        catch(Exception e){
            Toast.makeText(getApplicationContext(),e.getMessage(),Toast.LENGTH_SHORT).show();
            return;
        }
                /* Commit the edits! */
        editor.commit();
        Toast.makeText(getApplicationContext(), "Preferences Saved", Toast.LENGTH_SHORT).show();
    }


    /* Create temporary file to test permissions */
    public File createTemporaryFile(String part, String ext) throws Exception
    {
        File tempDir = new File(FILE_STORAGE_DIR);
        if(!tempDir.exists()) tempDir.mkdirs();
        return File.createTempFile(part, ext, tempDir);
    }
    
    /* Get the bitmap of the image */
    public Bitmap grabImage()
    {
        this.getContentResolver().notifyChange(mImageUri, null);
        ContentResolver cr = this.getContentResolver();
        try {return android.provider.MediaStore.Images.Media.getBitmap(cr, mImageUri);}
        catch (Exception e) {Toast.makeText(this, "Failed to load", Toast.LENGTH_SHORT).show();}
        return null;
    }

    /* After camera activity closes */
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {  
        if (requestCode == CAMERA_REQUEST && resultCode == Activity.RESULT_OK) {
            
            /* Load Preferences */
            SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
            final int ImageDim = settings.getInt("Dimension", DEFAULT_DIM);
            
            /* Scale down and send the image */
            try
            {
                new SendAsync().execute(scaleDown(grabImage(), ImageDim, true));
            }
            catch (Exception e)
            {
                Toast.makeText(getApplicationContext(),e.getMessage(),Toast.LENGTH_SHORT).show();
            }
            
            super.onActivityResult(requestCode, resultCode, data);
        }
    } 
    
    public boolean runJavaSocket(Bitmap bmp) {
        /* Load Preferences */
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        final String IP = settings.getString("IP", "");
        final int ImageQuality = settings.getInt("Quality", DEFAULT_QUALITY);
        
        /* Check if server was killed while clicking */
        boolean ServerAlive = serverListening(IP, settings.getInt("Port", DEFAULT_PORT));
        if (!ServerAlive) return false;
        
        /* Compress Image */
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.JPEG, ImageQuality, stream);
        ByteArrayInputStream rdr = new ByteArrayInputStream(stream.toByteArray());

        byte[] buffer = new byte[BUFFER_SIZE];

        try{
            /* Open a connection */
            Socket socket = new Socket(InetAddress.getByName(IP), settings.getInt("Port", DEFAULT_PORT));
    
            /* Write everything */
            OutputStream output = socket.getOutputStream();
            
            String string = "HEADER\n" + Integer.toString(stream.size()) + "\n";
            System.arraycopy(string.getBytes("US-ASCII"), 0, buffer, 0, string.length());
            
            output.write(buffer);
            
            int count;
            while ((count = rdr.read(buffer,0,buffer.length)) > 0) {
                output.write(buffer, 0, count);
            }

            /* Flush the output to commit */
            output.flush();

            return true;
        }
        catch (Exception e){
            Log.e("Client", "exception", e);
            return false;
        }
    }
    
    /* Scales down a bitmap keeping aspect ratio to have one dimension as indicated */
    public static Bitmap scaleDown(Bitmap realImage, float maxImageSize,
        boolean filter) {
        float ratio = Math.min(
                (float) maxImageSize / realImage.getWidth(),
                (float) maxImageSize / realImage.getHeight());
        int width = Math.round((float) ratio * realImage.getWidth());
        int height = Math.round((float) ratio * realImage.getHeight());

        return Bitmap.createScaledBitmap(realImage, width, height, filter);
    }
    
    /* Check if IP:Port is open */
    public boolean serverListening(String host, int port, boolean prompt)
    {
        Socket s = new Socket();
        try
        {
            s.connect(new InetSocketAddress(host, port), 1000);
            return true;
        }
        catch (Exception e)
        {
            if (prompt) Toast.makeText(getApplicationContext(),e.getMessage(),Toast.LENGTH_SHORT).show();
            return false;
        }
        finally
        {
            try {s.close();}
            catch(Exception ignored){}
        }
    }

    public boolean serverListening(String host, int port){
        return serverListening(host, port, false);
    }

    public void startClick()
    {
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        Intent intent = new Intent("android.media.action.IMAGE_CAPTURE");
        File photo;
        try
        {
            /* Place where to store camera taken picture */
                photo = createTemporaryFile("picture", ".jpg");
                photo.delete();
        }
        catch(Exception e)
        {
            Toast.makeText(getApplicationContext(), "Please check SD card! Image shot is impossible!", Toast.LENGTH_SHORT).show();
            return;
        }
        sharedFile = false;
        mImageUri = Uri.fromFile(photo);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, mImageUri);
        //Start Camera Intent
        startActivityForResult(intent, CAMERA_REQUEST);
    }

    private class ConnectTry extends AsyncTask<Integer,Integer,Boolean> {
        @Override
        protected Boolean doInBackground(Integer... integers) {
            SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
            return (serverListening(settings.getString("IP",""), settings.getInt("Port", DEFAULT_PORT), false));
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (result) startClick();
            else Toast.makeText(getApplicationContext(), "Connection Failed",Toast.LENGTH_SHORT).show();
        }
    }

    private class SendAsync extends AsyncTask<Bitmap,Integer,Boolean>{
        @Override
        protected Boolean doInBackground(Bitmap[] bitmap) {
            return runJavaSocket(bitmap[0]);
        }

        @Override
        protected void onPostExecute(Boolean result) {
            File fdelete = new File(mImageUri.getPath());
            if (result) {
                if (!sharedFile) {
                    final SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
                    if (!settings.getBoolean("PreserveImage", false) && fdelete.exists())
                        fdelete.delete();
                    else
                        notifyMediaStoreScanner(fdelete);
                }
                retryButton.setEnabled(false);
                imageViewMain.setImageDrawable(null);
            }
            else{
                if (!sharedFile) notifyMediaStoreScanner(fdelete);
                /* Display the image */
                imageViewMain.setImageBitmap(scaleDown(grabImage(), 800, false));
                retryButton.setEnabled(true);
            }

            Toast.makeText(getApplicationContext(), "File sending " + (result ? "" : "un") + "successful", Toast.LENGTH_SHORT).show();
        }
    }
}
