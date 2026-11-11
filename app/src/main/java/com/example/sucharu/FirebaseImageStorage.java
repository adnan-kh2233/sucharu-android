package com.example.sucharu;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.widget.ImageView;

import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/** Small JPEG compression and Firebase Storage helpers shared by profile and booking photos. */
public final class FirebaseImageStorage {
    public interface UploadListener {
        void onProgress(int percent);
        void onSuccess(String downloadUrl);
        void onFailure(Exception error);
    }

    private FirebaseImageStorage() {}

    public static void upload(Context context, Uri source, String storagePath, UploadListener listener) {
        upload(FirebaseStorage.getInstance(), context, source, storagePath, listener);
    }

    public static void upload(FirebaseStorage storage, Context context, Uri source, String storagePath, UploadListener listener) {
        new Thread(() -> {
            byte[] bytes = compress(context, source);
            if (bytes == null) {
                listener.onFailure(new IllegalArgumentException("This image could not be prepared. Choose another photo."));
                return;
            }
            StorageReference reference = storage.getReference().child(storagePath);
            StorageMetadata metadata = new StorageMetadata.Builder().setContentType("image/jpeg").build();
            UploadTask task = reference.putBytes(bytes, metadata);
            task.addOnProgressListener(snapshot -> {
                long total = snapshot.getTotalByteCount();
                int percent = total <= 0 ? 0 : (int) (100L * snapshot.getBytesTransferred() / total);
                listener.onProgress(percent);
            }).addOnSuccessListener(snapshot -> reference.getDownloadUrl()
                    .addOnSuccessListener(uri -> listener.onSuccess(uri.toString()))
                    .addOnFailureListener(listener::onFailure))
                    .addOnFailureListener(listener::onFailure);
        }, "sucharu-image-upload").start();
    }

    public static void loadInto(String downloadUrl, ImageView target, int placeholderRes) {
        target.setImageResource(placeholderRes);
        if (downloadUrl == null || downloadUrl.trim().isEmpty()) return;
        try {
            FirebaseStorage.getInstance().getReferenceFromUrl(downloadUrl).getBytes(3L * 1024L * 1024L)
                    .addOnSuccessListener(bytes -> {
                        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                        if (bitmap != null) target.setImageBitmap(bitmap);
                    });
        } catch (IllegalArgumentException ignored) { }
    }

    private static byte[] compress(Context context, Uri source) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
            try (InputStream input = context.getContentResolver().openInputStream(source)) { BitmapFactory.decodeStream(input, null, bounds); }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
            int sample = 1;
            while (bounds.outWidth / sample > 1280 || bounds.outHeight / sample > 1280) sample *= 2;
            BitmapFactory.Options options = new BitmapFactory.Options(); options.inSampleSize = sample;
            Bitmap bitmap;
            try (InputStream input = context.getContentResolver().openInputStream(source)) { bitmap = BitmapFactory.decodeStream(input, null, options); }
            if (bitmap == null) return null;
            int maxDimension = Math.max(bitmap.getWidth(), bitmap.getHeight());
            if (maxDimension > 1280) {
                float scale = 1280f / maxDimension;
                Bitmap scaled = Bitmap.createScaledBitmap(bitmap, Math.round(bitmap.getWidth() * scale), Math.round(bitmap.getHeight() * scale), true);
                if (scaled != bitmap) bitmap.recycle();
                bitmap = scaled;
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 78, output);
            bitmap.recycle();
            return output.toByteArray();
        } catch (Exception ignored) { return null; }
    }
}
