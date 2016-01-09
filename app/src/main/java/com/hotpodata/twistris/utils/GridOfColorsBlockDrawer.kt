package com.hotpodata.twistris.utils

import android.graphics.Canvas
import android.graphics.RectF
import com.hotpodata.blocklib.view.GridBinderView

/**
 * Created by jdrotos on 1/2/16.
 */
object GridOfColorsBlockDrawer : GridBinderView.RedBlockDrawer() {
    override fun drawBlock(canvas: Canvas, blockCoords: RectF, data: Any) {
        if (data is Int) {
            paint.color = data
            canvas.drawRect(blockCoords, paint)
        } else {
            super.drawBlock(canvas, blockCoords, data)
        }
    }
}