package com.example.textreconization

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.naver.android.helloyako.imagecrop.view.ImageCropView
import com.squareup.picasso.Picasso
import com.theartofdev.edmodo.cropper.CropImage
import kotlinx.android.synthetic.main.layout_crop_prieview.*
import java.io.File


class CropPreviewActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.layout_crop_prieview)
        val path: String = intent.extras?.get("PHOTO_PATH") as String
//        cropImageViewss.setImageUriAsync(Uri.parse(path));
        CropImage.activity(Uri.parse(path))
            .start(this);

    }

    private fun setPic(view: ImageView, imageUrl: String) {
        val image = File(imageUrl)
        if (image.exists()) {
            Picasso.with(this@CropPreviewActivity).load(image)
                .noFade()
                .fit().centerInside()
                .into(view)
        }
    }



    private fun performCrop(picUri: Uri) {
        try {
            val cropIntent = Intent("com.android.camera.action.CROP")
            // indicate image type and Uri
            cropIntent.setDataAndType(picUri, "image/*")
            // set crop properties here
            cropIntent.putExtra("crop", true)
            // indicate aspect of desired crop
            cropIntent.putExtra("aspectX", 1)
            cropIntent.putExtra("aspectY", 1)
            // indicate output X and Y
            cropIntent.putExtra("outputX", 128)
            cropIntent.putExtra("outputY", 128)
            // retrieve data on return
            cropIntent.putExtra("return-data", true)
            // start the activity - we handle returning in onActivityResult
            startActivityForResult(cropIntent, 112)
        } // respond to users whose devices do not support the crop action
        catch (anfe: ActivityNotFoundException) {
            // display an error message
            val errorMessage =
                "Whoops - your device doesn't support the crop action!"
            val toast = Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT)
            toast.show()
        }
    }


}