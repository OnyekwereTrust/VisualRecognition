package com.ibm.watson.visualrecognition;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.ibm.watson.developer_cloud.android.library.camera.CameraHelper;
import com.ibm.watson.developer_cloud.android.library.camera.GalleryHelper;
import com.ibm.watson.developer_cloud.visual_recognition.v3.VisualRecognition;
import com.ibm.watson.developer_cloud.visual_recognition.v3.model.ClassifyImagesOptions;
import com.ibm.watson.developer_cloud.visual_recognition.v3.model.VisualClassification;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;


//Uses galleryHelper and cameraHelper from Watson Android SDK


public class MainActivity extends AppCompatActivity {
    Button button, uploadButton, cameraButton;
    ImageView previewImage;
    TextView textView;
    String imageUri = null;
    private GalleryHelper galleryHelper;
    private CameraHelper cameraHelper;
    File file = null;

    //Wrapper for passing resultString from doInBackground() to onPostExecute()
    public class Wrapper
    {
        String resultString;
    }

    //Resizing images that are larger than 2 MB
    public File resize(Uri uri){
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
        Bitmap b = BitmapFactory.decodeFile(getPath(uri));
        Bitmap out = Bitmap.createScaledBitmap(b, 320, 480, false);

        file = new File(dir, "resize.png");
        FileOutputStream os;
        try {
            os = new FileOutputStream(file);
            out.compress(Bitmap.CompressFormat.PNG, 100, os);
            os.flush();
            os.close();
            b.recycle();
            out.recycle();
            return file;
        } catch (Exception e) {
            return null;
        }
    }

    //get absolute path of the selected image
    public String getPath(Uri uri) {
        String[] projection = { MediaStore.Images.Media.DATA };
        Cursor cursor = getContentResolver().query(uri, projection, null, null, null);
        int column_index = cursor != null ? cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA) : 0;
        if (cursor != null) {
            cursor.moveToFirst();
        }
        assert cursor != null;
        return cursor.getString(column_index);
    }

    // Ask for permission to access External Storage (API 23 and above)
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults){
        switch(requestCode) {
            case 1:
                if (grantResults.length <= 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(MainActivity.this, "Permission denied to read your External Storage", Toast.LENGTH_SHORT).show();
                }
                return;
        }
    }
    //Visual Recognition has to run on another thread
    private class watsonVisualRecognition extends AsyncTask<String, Void, Wrapper>{
        @Override
        protected Wrapper doInBackground(String... imagesUri){

            Wrapper w = new Wrapper();

            //Visual Recognition

            VisualRecognition service = new VisualRecognition(VisualRecognition.VERSION_DATE_2016_05_20);
            service.setApiKey("5fd0bcc554986baa080f26015769fd94be97300a");
            ClassifyImagesOptions options = new ClassifyImagesOptions.Builder().images(file).build();
            VisualClassification result = service.classify(options).execute();
            w.resultString = result.toString();
            System.out.println(result.toString());
            return w;
        }

        protected void onPostExecute(Wrapper w){
            String jsonString = w.resultString;
            String imageResult = "Visual Recognition result: \n";
            String typeResult = "\nType/Hierarchy: \n";
            textView.setVisibility(View.VISIBLE);
            //Parsing JSON
            try
            {
                JSONObject obj = new JSONObject(jsonString);
                JSONArray images = obj.getJSONArray("images");
                for(int a = 0; a < images.length(); a++)
                {
                    JSONArray classifiers = new JSONArray(images.getJSONObject(a).getString("classifiers"));
                    for(int b = 0; b < classifiers.length(); b++)
                    {
                        JSONArray classes = new JSONArray(classifiers.getJSONObject(b).getString("classes"));
                        for(int c = 0; c < classes.length(); c++) {
                            JSONObject classObj = classes.getJSONObject(c);
                            String classify = classObj.getString("class");
                            String score = classObj.getString("score");
                            //Sometimes JSON String does not have "type_hierarchy" key
                            if(classObj.has("type_hierarchy")) {
                                String type = classObj.getString("type_hierarchy");
                                typeResult += type + "\n";
                            }
                            imageResult += "Class: " + classify + " with the Score of: " + score + "\n";
                        }
                        if(classes.length() == 0 ){
                            textView.setText("No result available");
                        }
                        else textView.setText(imageResult + typeResult);
                    }
                }
            } catch (JSONException ignored)
            {
            }
            previewImage.setVisibility(View.INVISIBLE);
            uploadButton.setVisibility(View.INVISIBLE);
            Toast.makeText(MainActivity.this, "Image uploaded successfully", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
        setContentView(R.layout.activity_main);
        button = (Button) findViewById(R.id.button);
        cameraButton = (Button) findViewById(R.id.cameraButton);
        previewImage = (ImageView) findViewById(R.id.image);
        textView = (TextView) findViewById(R.id.resultString);
        uploadButton = (Button)findViewById(R.id.uploadButton);
        uploadButton.setVisibility(View.INVISIBLE);
        galleryHelper = new GalleryHelper(this);
        cameraHelper = new CameraHelper(this);

        button.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                galleryHelper.dispatchGalleryIntent();
            }
        });

        cameraButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                cameraHelper.dispatchTakePictureIntent();
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        //using Gallery Helper from Watson Android SDK for simple image selection
        if(requestCode == GalleryHelper.PICK_IMAGE_REQUEST) {
            textView.setVisibility(View.INVISIBLE);
            if (data != null) {
                imageUri = data.getData().toString();
                uploadButton.setVisibility(View.VISIBLE);
                Bitmap bitmap = galleryHelper.getBitmap(resultCode, data);
                previewImage.setImageBitmap(bitmap);
                Uri uri = Uri.parse(imageUri);
                //Create a file object from selected image
                double size = ((double) bitmap.getByteCount()) / 1048576;
                if (size > 2) file = resize(uri);
                previewImage.setVisibility(View.VISIBLE);
            }
        }
        //using Camera Helper from Watson Android SDK for quick image capture
        if (requestCode == CameraHelper.REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            textView.setVisibility(View.INVISIBLE);
            String path;
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());

            uploadButton.setVisibility(View.VISIBLE);
            previewImage.setVisibility(View.VISIBLE);
            Bitmap bitmap = cameraHelper.getBitmap(resultCode);
            previewImage.setImageBitmap(bitmap);

            // Saving image to Gallery
            path = MediaStore.Images.Media.insertImage(getContentResolver(), bitmap, "image_" + timeStamp, "image");
            Uri uri = Uri.parse(path);

            double size = ((double) bitmap.getByteCount()) / 1048576;
            if(size > 2) file = resize(uri);
        }
    }

    public void uploadImage(View v){
        watsonVisualRecognition task = new watsonVisualRecognition();
        task.execute();
    }

}