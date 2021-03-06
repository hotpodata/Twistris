package com.hotpodata.twistris.adapter

import android.content.Context
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.hotpodata.common.adapter.SideBarAdapter
import com.hotpodata.common.data.App
import com.hotpodata.common.enums.HotPoDataApps
import com.hotpodata.common.enums.Libraries
import com.hotpodata.common.interfaces.IAnalyticsProvider
import com.hotpodata.twistris.R
import com.hotpodata.twistris.adapter.viewholder.SignInViewHolder
import com.hotpodata.twistris.interfaces.IGameController
import com.hotpodata.twistris.interfaces.IGooglePlayGameServicesProvider
import java.util.*

/**
 * Created by jdrotos on 11/7/15.
 */
class TwistrisSideBarAdapter(ctx: Context, val gameController: IGameController, val playGameServicesProvider: IGooglePlayGameServicesProvider, val analytics: IAnalyticsProvider?) : SideBarAdapter(ctx, analytics, App.Factory.createApp(ctx, HotPoDataApps.TWISTRIS), false, false, Libraries.AutoFitTextView, Libraries.Picasso, Libraries.RxAndroid, Libraries.RxJava, Libraries.RxKotlin, Libraries.Timber) {
    private val ROW_TYPE_SIGN_IN = 100

    init{
        rebuildRowSet()
    }

    override fun genCustomRows(): List<Any> {
        var sideBarRows = ArrayList<Any>()
        var helpRow = RowSettings(ctx.resources.getString(R.string.how_to_play), "", View.OnClickListener {
            gameController.showHelp()
        }, R.drawable.ic_info_outline_24dp)
        sideBarRows.add(ctx.resources.getString(R.string.game))
        if (playGameServicesProvider.isLoggedIn()) {
            sideBarRows.add(RowSettings(ctx.resources.getString(R.string.high_scores), "", View.OnClickListener {
                playGameServicesProvider.showLeaderBoard()
            }, R.drawable.ic_trophy_black_48dp))
            sideBarRows.add(RowDiv(true))
            sideBarRows.add(RowSettings(ctx.resources.getString(R.string.achievements), "", View.OnClickListener {
                playGameServicesProvider.showAchievements()
            }, R.drawable.ic_grade_24dp))
            sideBarRows.add(RowDiv(true))
            sideBarRows.add(helpRow)
            sideBarRows.add(RowDiv(true))
            sideBarRows.add(RowSettings(ctx.resources.getString(R.string.sign_out), "", View.OnClickListener {
                playGameServicesProvider.logout()
            }, R.drawable.ic_highlight_remove_24dp))
        } else {
            sideBarRows.add(RowSignIn(View.OnClickListener {
                playGameServicesProvider.login()
            }))
            sideBarRows.add(RowDiv(false))
            sideBarRows.add(helpRow)
        }
        return sideBarRows
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder? {
        return when (viewType) {
            ROW_TYPE_SIGN_IN -> {
                val inflater = LayoutInflater.from(parent.context)
                val v = inflater.inflate(R.layout.row_signin, parent, false)
                SignInViewHolder(v)
            }
            else -> super.onCreateViewHolder(parent, viewType)
        }
    }

    @Suppress("DEPRECATION")
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val type = getItemViewType(position)
        val objData = mRows[position]
        when (type) {
            ROW_TYPE_SIGN_IN -> {
                val vh = holder as SignInViewHolder
                val data = objData as RowSignIn
                vh.signInBtn.setOnClickListener(data.onClickListener)
            }
            else -> super.onBindViewHolder(holder, position)
        }
    }

    override fun getItemViewType(position: Int): Int {
        val data = mRows[position]
        return when (data) {
            is RowSignIn -> ROW_TYPE_SIGN_IN
            else -> super.getItemViewType(position)
        }
    }

    class RowSignIn(val onClickListener: View.OnClickListener)
}