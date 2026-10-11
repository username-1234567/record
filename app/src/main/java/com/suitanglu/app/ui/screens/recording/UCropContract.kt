package com.suitanglu.app.ui.screens.recording

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.content.FileProvider
import com.yalantis.ucrop.UCrop

/** 系统相机/相册 → uCrop 自由比例裁剪 */
class UCropContract : ActivityResultContract<Pair<Uri, Uri>, Uri?>() {

    override fun createIntent(context: Context, input: Pair<Uri, Uri>): Intent {
        val (source, dest) = input
        val options = UCrop.Options().apply {
            setFreeStyleCropEnabled(true)
            setToolbarTitle("裁剪图片")
            setToolbarWidgetColor(0xFFFFFFFF.toInt())
            setStatusBarColor(0x00000000)
            setRootViewBackgroundColor(0xFF1B1B1B.toInt())
            setActiveControlsWidgetColor(0xFF1F8A73.toInt())
        }
        return UCrop.of(source, dest).withOptions(options).getIntent(context)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == android.app.Activity.RESULT_OK) {
            intent?.let { UCrop.getOutput(it) }
        } else null
}

object PendingMarkerStore {
    @Volatile var capturedPath: String? = null
}

/** 创建临时拍照文件 Uri（FileProvider） */
fun newPhotoUri(context: Context): Uri {
    val dir = java.io.File(context.cacheDir, "photos").apply { mkdirs() }
    val file = java.io.File(dir, "IMG_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

fun newCropUri(context: Context): Uri {
    val dir = java.io.File(context.cacheDir, "crops").apply { mkdirs() }
    val file = java.io.File(dir, "CROP_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
