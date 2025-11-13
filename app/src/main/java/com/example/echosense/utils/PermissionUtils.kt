package com.echosense.utils

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class PermissionUtils {
    
    companion object {
        const val PERMISSION_REQUEST_CODE = 100
        
        fun checkAndRequestPermissions(activity: Activity): Boolean {
            val permissions = mutableListOf<String>()
            
            // Audio recording permission
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.RECORD_AUDIO)
            }
            
            // Storage permissions (for Android 12 and below)
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                if (ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                    permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
                
                if (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                    permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }
            
            // Audio media permission (for Android 13+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_MEDIA_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                    permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
                }
            }
            
            if (permissions.isNotEmpty()) {
                ActivityCompat.requestPermissions(
                    activity,
                    permissions.toTypedArray(),
                    PERMISSION_REQUEST_CODE
                )
                return false
            }
            
            return true
        }
        
        fun hasAudioPermission(activity: Activity): Boolean {
            return ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
}