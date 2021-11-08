/*
 * Copyright 2020 Google LLC. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.good.scanner.preference;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build.VERSION_CODES;
import android.preference.PreferenceManager;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.camera.core.CameraSelector;

import com.google.common.base.Preconditions;
import com.google.mlkit.vision.demo.R;

/**
 * Utility class to retrieve shared preferences.
 */
public class PreferenceUtils {

    @RequiresApi(VERSION_CODES.LOLLIPOP)
    @Nullable
    public static android.util.Size getCameraXTargetResolution(Context context, int lensfacing) {
        Preconditions.checkArgument(
                lensfacing == CameraSelector.LENS_FACING_BACK
                        || lensfacing == CameraSelector.LENS_FACING_FRONT);
        String prefKey =
                lensfacing == CameraSelector.LENS_FACING_BACK
                        ? context.getString(R.string.pref_key_camerax_rear_camera_target_resolution)
                        : context.getString(R.string.pref_key_camerax_front_camera_target_resolution);
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        try {
            return android.util.Size.parseSize(sharedPreferences.getString(prefKey, null));
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean shouldHideDetectionInfo(Context context) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        String prefKey = context.getString(R.string.pref_key_info_hide);
        return sharedPreferences.getBoolean(prefKey, false);
    }

    public static boolean shouldGroupRecognizedTextInBlocks(Context context) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        String prefKey = context.getString(R.string.pref_key_group_recognized_text_in_blocks);
        return sharedPreferences.getBoolean(prefKey, true);
    }

    private PreferenceUtils() {
    }
}
