package com.hotpodata.twistris.adapter

import android.content.Context
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import com.hotpodata.twistris.R
import com.hotpodata.twistris.adapter.viewholder.HelpItemViewHolder
import com.squareup.picasso.Picasso
import java.util.*

/**
 * Created by jdrotos on 1/20/16.
 */
class TwistrisHelpAdapter(val ctx: Context) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    class HelpItem(val imageResId: Int, val textResId: Int)

    val VIEWTYPE_HEADER = 0
    val VIEWTYPE_HELP_ITEM = 1

    var helpItems = ArrayList<HelpItem>()

    init {
        helpItems.add(HelpItem(R.drawable.right_to_left, R.string.help_pieces_move_right_to_left))
        helpItems.add(HelpItem(R.drawable.rotate, R.string.help_rotation))
        helpItems.add(HelpItem(R.drawable.touch_to_control, R.string.help_controls))
        helpItems.add(HelpItem(R.drawable.multiple_pieces, R.string.help_level))
        helpItems.add(HelpItem(R.drawable.slide_out, R.string.help_clear_lines))
    }

    override fun getItemCount(): Int {
        return helpItems.size + 1
    }

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): RecyclerView.ViewHolder? {
        return when (viewType) {
            VIEWTYPE_HEADER -> {
                val inflater = LayoutInflater.from(ctx)
                val v = inflater.inflate(R.layout.row_help_header, parent, false)
                object : RecyclerView.ViewHolder(v) {}
            }
            VIEWTYPE_HELP_ITEM -> {
                val inflater = LayoutInflater.from(ctx)
                val v = inflater.inflate(R.layout.row_help_item, parent, false)
                HelpItemViewHolder(v)
            }
            else -> {
                null
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder?, position: Int) {
        if (getItemViewType(position) == VIEWTYPE_HELP_ITEM) {
            var data = helpItems.get(position - 1)
            var vh = holder as HelpItemViewHolder
            Picasso.with(ctx).load(data.imageResId).fit().into(vh.image)
            vh.text.setText(data.textResId)
        }
    }

    override fun getItemViewType(position: Int): Int {
        if (position == 0) {
            return VIEWTYPE_HEADER
        } else {
            return VIEWTYPE_HELP_ITEM
        }
    }
}