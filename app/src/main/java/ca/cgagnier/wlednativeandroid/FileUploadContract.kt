package ca.cgagnier.wlednativeandroid

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract

class FileUploadContract : ActivityResultContract<Int, FileUploadContractResult>() {

    var type = "*/*"

    override fun createIntent(context: Context, input: Int): Intent {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = type
        intent.addCategory(Intent.CATEGORY_OPENABLE)

        return intent
    }

    override fun parseResult(resultCode: Int, intent: Intent?): FileUploadContractResult =
        FileUploadContractResult(resultCode, intent)
}
