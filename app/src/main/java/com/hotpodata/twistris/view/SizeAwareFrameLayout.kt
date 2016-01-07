package com.hotpodata.twistris.view

import android.annotation.TargetApi
import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.widget.FrameLayout

/**
 * Created by jdrotos on 1/6/16.
 */
class SizeAwareFrameLayout : FrameLayout {

    public interface ISizeChangeListener {
        fun onSizeChange(w: Int, h: Int, oldw: Int, oldh: Int)
    }

    var sizeChangeListener: ISizeChangeListener? = null
        set(listener: ISizeChangeListener?) {
            field = listener
            listener?.onSizeChange(width, height, width, height)
        }

    constructor(context: Context) : super(context) {

    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {

    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle) {

    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {

    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        sizeChangeListener?.onSizeChange(w, h, oldw, oldh)
    }
}