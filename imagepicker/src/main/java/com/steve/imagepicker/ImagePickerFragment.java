/*
 * Copyright 2018 Steve Tchatchouang
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.steve.imagepicker;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.BottomSheetDialogFragment;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.theartofdev.edmodo.cropper.CropImage;
import com.theartofdev.edmodo.cropper.CropImageView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import static android.app.Activity.RESULT_OK;

public class ImagePickerFragment extends BottomSheetDialogFragment implements ImagePickerView, View.OnClickListener {

    public static final  String TAG              = ImagePickerFragment.class.getSimpleName();
    private static final int    RC_GALLERY       = 1;
    private static final int    RC_CAMERA        = 2;
    private static final int    RC_WRITE_STORAGE = 3;
    //private static final int RC_CROP = 3;
    private static final String DO_CROP_KEY      = "mDoCropAction";
    private static String title;

    private boolean                               mDoCropAction;
    private Uri                                   mImageCaptureUri;
    private Bitmap                                originalBitmap;
    private ImagePickerResult                     mImagePickerResult;
    private ImagePickerPresenter<ImagePickerView> mPresenter;
    private LinearLayout                          sheetContainer;

    private static String  actionText;
    private static String  descText;
    private static boolean shouldUseDefaultConfig;

    public ImagePickerFragment() {
    }

    public static ImagePickerFragment newInstance(Boolean doCropAction) {
        shouldUseDefaultConfig = true;
        ImagePickerFragment fragment = new ImagePickerFragment();
        Bundle bundle = new Bundle();
        bundle.putBoolean(DO_CROP_KEY, doCropAction);
        fragment.setArguments(bundle);
        return fragment;
    }

    public static ImagePickerFragment newInstance(boolean doCrop,
                                                  String title,
                                                  String deniedActionDescText,
                                                  String actionText) {

        shouldUseDefaultConfig = false;
        ImagePickerFragment.actionText = actionText;
        ImagePickerFragment.title = title;
        descText = deniedActionDescText;
        return newInstance(doCrop);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof ImagePickerResult) {
            mImagePickerResult = (ImagePickerResult) context;
        } else {
            throw new ClassCastException("Activity must implement ImagePickerResult");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mImagePickerResult = null;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPresenter = new ImagePickerPresenter<ImagePickerView>(this);
        mDoCropAction = getArguments().getBoolean(DO_CROP_KEY);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.picture_chooser, container, false);
        view.findViewById(R.id.pick_camera).setOnClickListener(this);
        view.findViewById(R.id.pick_gallery).setOnClickListener(this);
        sheetContainer = (LinearLayout) view.findViewById(R.id.sheet_container);
        if(!shouldUseDefaultConfig){
            TextView textView = (TextView) view.findViewById(R.id.title);
            textView.setText(title);
        }
        return view;
    }

    @Override
    public void onClick(View view) {
        int i = view.getId();
        if (i == R.id.pick_camera) {
            mPresenter.onPickCameraClicked();

        } else if (i == R.id.pick_gallery) {
            mPresenter.onPickGalleryClicked();
        }
    }

    @Override
    public void pickFromGallery() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(intent, RC_GALLERY);
    }

    @Override
    public void pickFromCamera() {
        if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            mImageCaptureUri = getTmpFile();
            Log.i(TAG, "pickFromCamera: path " + String.valueOf(mImageCaptureUri));
            intent.putExtra(MediaStore.EXTRA_OUTPUT, mImageCaptureUri);
            intent.putExtra("return-data", true);
            startActivityForResult(intent, RC_CAMERA);
        } else if (ActivityCompat.shouldShowRequestPermissionRationale(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            Snackbar.make(sheetContainer,
                    shouldUseDefaultConfig ? "Permission previously refused; do you want to grant it ?" : descText,
                    Snackbar.LENGTH_LONG
            ).setAction(shouldUseDefaultConfig ? "Grant" : actionText, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    requestPermission();
                }

            }).show();

        } else {
            requestPermission();
        }
    }

    private void requestPermission() {
        requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, RC_WRITE_STORAGE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        int grantResult = grantResults[0];
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            pickFromCamera();
        } else {
            Toast.makeText(getContext(), "Permission denied", Toast.LENGTH_SHORT).show();
        }
    }

    private Uri getTmpFile() {
        File root = getRootDirectory();
        String fileName = "tmp_avatar_" + String.valueOf(System.currentTimeMillis()) + ".jpg";
        return Uri.fromFile(new File(root, fileName));
    }

    private File getRootDirectory() {
        if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                File file = Environment.getExternalStorageDirectory();
                if (file.getFreeSpace() > 5 * Math.pow(1024, 2)) {
                    return Environment.getExternalStorageDirectory();
                }
            }
        }
        return getContext().getCacheDir();
    }

    private void performCrop(Uri uri) {
        if (!mDoCropAction) {
            mImagePickerResult.onChooserSuccess(originalBitmap, null, false);
            dismiss();
            return;
        }
        CropImage.activity(uri)
                .setAspectRatio(1, 1)
                .setActivityTitle(getString(R.string.app_name))
                .setRequestedSize(512, 512, CropImageView.RequestSizeOptions.RESIZE_INSIDE)
                .start(getContext(), this);
        /*
        Intent cropIntent = new Intent("com.android.camera.action.CROP");
        cropIntent.setDataAndType(uri, "image/*");
        cropIntent.putExtra("crop", "true");
        cropIntent.putExtra("aspectX", 1);
        cropIntent.putExtra("aspectY", 1);
        cropIntent.putExtra("outputX", 256);
        cropIntent.putExtra("outputY", 256);
        cropIntent.putExtra("return-data", true);
        try {
            startActivityForResult(cropIntent, RC_CROP);
        } catch (ActivityNotFoundException anfe) {
            String errorMessage = getString(R.string.device_dont_support_crop_txt);
            Log.e(TAG, "performCrop: " + errorMessage);
            mImagePickerResult.onChooserSuccess(BitmapFactory.decodeFile(uri.toString()), null, false);
        }*/
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == RC_CAMERA) {
                File f = new File(mImageCaptureUri.getPath());
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                try {
                    originalBitmap = BitmapFactory.decodeStream(new FileInputStream(f), null, options);
                } catch (FileNotFoundException e) {
                    mImagePickerResult.onChooserError(e.getMessage());
                }
                performCrop(mImageCaptureUri);
            }
            if (requestCode == RC_GALLERY) {
                try {
                    originalBitmap = MediaStore.Images.Media.getBitmap(getContext().getContentResolver(), data.getData());
                } catch (IOException e) {
                    mImagePickerResult.onChooserError(e.getMessage());
                }
                performCrop(data.getData());
            }
            /*if (requestCode == RC_CROP) {
                Bundle extras = data.getExtras();
                Bitmap mBitmap = extras.getParcelable("data");
                if (mBitmap != null) {
                    if (mImageCaptureUri != null) {
                        File f = new File(mImageCaptureUri.getPath());
                        if (f.exists()) {
                            //noinspection ResultOfMethodCallIgnored
                            f.delete();
                        }
                    }
                    mImagePickerResult.onChooserSuccess(originalBitmap, mBitmap, true);
                } else {
                    mImagePickerResult.onChooserError("Null bitmap");
                }
                dismiss();
            }*/
            if (requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE) {
                CropImage.ActivityResult result = CropImage.getActivityResult(data);
                Uri resultUri = result.getUri();
                try {
                    FileInputStream inputStream = new FileInputStream(new File(resultUri.getPath()));
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                    Bitmap crp = BitmapFactory.decodeStream(inputStream, null, options);
                    mImagePickerResult.onChooserSuccess(originalBitmap, crp, true);

                } catch (FileNotFoundException e) {
                    mImagePickerResult.onChooserError(e.getMessage());
                }
                Log.e(TAG, "onActivityResult: resultUri : " + result.getBitmap());
                dismiss();
            }
        }
        if (resultCode == CropImage.CROP_IMAGE_ACTIVITY_RESULT_ERROR_CODE) {
            CropImage.ActivityResult result = CropImage.getActivityResult(data);
            Exception error = result.getError();
            mImagePickerResult.onChooserError(error.getMessage());
            dismiss();
        }
    }

    public interface ImagePickerResult {
        void onChooserError(String errorMessage);

        void onChooserSuccess(Bitmap original, Bitmap cropped, boolean hasCropped);
    }
}