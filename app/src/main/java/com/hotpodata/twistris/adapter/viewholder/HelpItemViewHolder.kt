package com.hotpodata.twistris.adapter.viewholder

import android.support.v7.widget.RecyclerView
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.hotpodata.twistris.R


/**
 * Created by jdrotos on 1/21/15.
 */
class HelpItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    var image: ImageView
    var text: TextView

    init {
        image = itemView.findViewById(R.id.help_image) as ImageView
        text = itemView.findViewById(R.id.help_text) as TextView
    }
}
