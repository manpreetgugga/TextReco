package com.example.textreconization

import android.R.attr
import android.R.attr.bitmap
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class TextBlockRecoView : View {

    constructor(context: Context) : super(context) {
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    ) {

    }

    constructor(context: Context, attrs: AttributeSet, bitmap: Bitmap) : super(
        context,
        attrs
    ) {
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        if (TextScannerActivity.bitMap != null) {
            val paint = Paint()
            paint.isAntiAlias = true
            paint.isFilterBitmap = true
            paint.isDither = true
            canvas?.drawBitmap(TextScannerActivity.bitMap, x, y, paint)
        }
    }
}