package com.hotpodata.twistris.utils

import android.graphics.Canvas
import com.hotpodata.blocklib.view.GridBinderView

/**
 * Created by jdrotos on 1/2/16.
 */
object GridOfColorsBlockDrawer : GridBinderView.RedBlockDrawer() {
    override fun drawBlock(canvas: Canvas, data: Any) {
        if (data is Int) {
            canvas.drawColor(data)
        } else {
            super.drawBlock(canvas, data)
        }
    }
}