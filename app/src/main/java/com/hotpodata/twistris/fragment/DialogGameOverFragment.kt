package com.hotpodata.twistris.fragment

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import com.hotpodata.twistris.R
import com.hotpodata.twistris.interfaces.IGameController
import kotlinx.android.synthetic.main.fragment_dialog_gameover.*

/**
 * Created by jdrotos on 12/28/15.
 */
class DialogGameOverFragment : DialogFragment() {
    var gameController: IGameController? = null

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        gameController = context as? IGameController
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog? {
        val dialog = super.onCreateDialog(savedInstanceState)
        // request a window without the title
        dialog.window.requestFeature(Window.FEATURE_NO_TITLE)
        dialog.setCanceledOnTouchOutside(false)
        dialog.setCancelable(false)
        return dialog
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater?.inflate(R.layout.fragment_dialog_gameover, container, false)
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        btn_play_again.setOnClickListener {
            gameController?.resetGame()
            gameController?.resumeGame()
            dismiss()
        }
    }
}