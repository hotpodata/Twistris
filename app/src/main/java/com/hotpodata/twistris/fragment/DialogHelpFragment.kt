package com.hotpodata.twistris.fragment

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import com.hotpodata.twistris.R
import kotlinx.android.synthetic.main.fragment_dialog_start.*
import timber.log.Timber

/**
 * Created by jdrotos on 12/28/15.
 */
class DialogHelpFragment : DialogFragment() {

    public interface IHelpDialogListener {
        fun onHelpDialogDismissed()
    }

    var dialogLisetner: IHelpDialogListener? = null

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        dialogLisetner = context as? IHelpDialogListener
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
        return inflater?.inflate(R.layout.fragment_dialog_help, container, false)
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        btn_start.setOnClickListener {
            Timber.d("Calling dismiss()")
            dismiss()
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        Timber.d("OnDismissListener firing!")
        dialogLisetner?.onHelpDialogDismissed()
    }
}