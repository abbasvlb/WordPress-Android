package org.wordpress.android.ui.posts.services;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;

import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader;

import org.wordpress.android.WordPress;
import org.wordpress.aztec.Html;

import java.io.File;

public class AztecImageLoader implements Html.ImageGetter {

    private Context context;

    public AztecImageLoader(Context context) {
        this.context = context;
    }

    @Override
    public void loadImage(String url, final Callbacks callbacks, int maxWidth) {
        // TODO: if a local file then load it directly. This is a quick fix though.
        if (new File(url).exists()) {
            Bitmap bitmap = BitmapFactory.decodeFile(url);
            BitmapDrawable bitmapDrawable = new BitmapDrawable(context.getResources(), bitmap);
            callbacks.onImageLoaded(bitmapDrawable);
            return;
        }

        WordPress.sImageLoader.get(url, new ImageLoader.ImageListener() {
            @Override
            public void onResponse(ImageLoader.ImageContainer response, boolean isImmediate) {
                Bitmap bitmap = response.getBitmap();

                if (bitmap == null) {
                    callbacks.onImageLoading(null);
                } else {
                    BitmapDrawable bitmapDrawable = new BitmapDrawable(context.getResources(), bitmap);
                    callbacks.onImageLoaded(bitmapDrawable);
                }
            }

            @Override
            public void onErrorResponse(VolleyError error) {
                callbacks.onImageFailed();
            }
        }, maxWidth, 0);
    }
}
