package ru.namaztime.tv.repository

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts

interface OperatorImageSelectionEnvironment {
    fun capabilities(): OperatorImageSelectionCapabilities

    fun permissionName(permission: OperatorImageReadPermission): String
}

class AndroidOperatorImageSelectionEnvironment(context: Context) :
    OperatorImageSelectionEnvironment {
    private val applicationContext = context.applicationContext

    override fun capabilities(): OperatorImageSelectionCapabilities {
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            OperatorImageReadPermission.MEDIA_IMAGES
        } else {
            OperatorImageReadPermission.LEGACY_MEDIA_IMAGES
        }
        return OperatorImageSelectionCapabilities(
            sdkInt = Build.VERSION.SDK_INT,
            openDocumentResolvable = openDocumentIntent().resolveActivity(
                applicationContext.packageManager,
            ) != null,
            photoPickerAvailable = ActivityResultContracts.PickVisualMedia
                .isPhotoPickerAvailable(applicationContext),
            mediaReadPermissionGranted = applicationContext.checkSelfPermission(
                permissionName(permission),
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }

    override fun permissionName(permission: OperatorImageReadPermission): String =
        when (permission) {
            OperatorImageReadPermission.LEGACY_MEDIA_IMAGES -> Manifest.permission.READ_EXTERNAL_STORAGE
            OperatorImageReadPermission.MEDIA_IMAGES -> Manifest.permission.READ_MEDIA_IMAGES
        }

    private fun openDocumentIntent() = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "image/*"
        putExtra(Intent.EXTRA_MIME_TYPES, OPERATOR_IMAGE_MIME_TYPES)
    }
}

val OPERATOR_IMAGE_MIME_TYPES = arrayOf("image/jpeg", "image/png", "image/webp")
