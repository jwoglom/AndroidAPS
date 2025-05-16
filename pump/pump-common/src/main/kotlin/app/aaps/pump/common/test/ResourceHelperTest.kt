package app.aaps.pump.common.test

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import androidx.annotation.StringRes
import app.aaps.core.interfaces.resources.ResourceHelper

class ResourceHelperTest: ResourceHelper {



    fun addString(@StringRes resId: Int, value: String ) {

    }


    override fun gs(@StringRes id: Int): String {
        return "Unknown String ${id}"
    }

    override fun gs(id: Int, vararg args: Any?): String {
        TODO("Not yet implemented")
    }

    override fun gq(id: Int, quantity: Int, vararg args: Any?): String {
        TODO("Not yet implemented")
    }

    override fun gsNotLocalised(id: Int, vararg args: Any?): String {
        TODO("Not yet implemented")
    }

    override fun gc(id: Int): Int {
        TODO("Not yet implemented")
    }

    override fun gd(id: Int): Drawable? {
        TODO("Not yet implemented")
    }

    override fun gb(id: Int): Boolean {
        TODO("Not yet implemented")
    }

    override fun gcs(id: Int): String {
        TODO("Not yet implemented")
    }

    override fun gsa(id: Int): Array<String> {
        TODO("Not yet implemented")
    }

    override fun openRawResourceFd(id: Int): AssetFileDescriptor? {
        TODO("Not yet implemented")
    }

    override fun decodeResource(id: Int): Bitmap {
        TODO("Not yet implemented")
    }

    override fun getDisplayMetrics(): DisplayMetrics {
        TODO("Not yet implemented")
    }

    override fun dpToPx(dp: Int): Int {
        TODO("Not yet implemented")
    }

    override fun dpToPx(dp: Float): Int {
        TODO("Not yet implemented")
    }

    override fun shortTextMode(): Boolean {
        TODO("Not yet implemented")
    }

    override fun gac(attributeId: Int): Int {
        TODO("Not yet implemented")
    }

    override fun gac(context: Context?, attributeId: Int): Int {
        TODO("Not yet implemented")
    }

    override fun getThemedCtx(context: Context): Context {
        TODO("Not yet implemented")
    }
}