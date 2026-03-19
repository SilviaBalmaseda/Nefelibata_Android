package com.example.nefelibata.ui

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.example.nefelibata.R
import com.google.android.material.button.MaterialButton

class SynopsisDialogFragment : DialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.dialog_synopsis, container, false)

        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val tvStatusValue = view.findViewById<TextView>(R.id.tv_dialog_status_value)
        val tvGenresValue = view.findViewById<TextView>(R.id.tv_dialog_genres_value)
        val tvSynopsisValue = view.findViewById<TextView>(R.id.tv_dialog_synopsis_value)
        val ivClose = view.findViewById<ImageView>(R.id.iv_close_dialog)
        val btnClose = view.findViewById<MaterialButton>(R.id.btn_close_dialog)

        tvStatusValue.text = arguments?.getString("status") ?: ""
        tvGenresValue.text = arguments?.getString("genres") ?: ""
        tvSynopsisValue.text = arguments?.getString("synopsis") ?: ""

        ivClose.setOnClickListener { dismiss() }
        btnClose.setOnClickListener { dismiss() }

        return view
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    companion object {
        fun newInstance(status: String, genres: String, synopsis: String): SynopsisDialogFragment {
            val fragment = SynopsisDialogFragment()
            val args = Bundle().apply {
                putString("status", status)
                putString("genres", genres)
                putString("synopsis", synopsis)
            }
            fragment.arguments = args
            return fragment
        }
    }
}