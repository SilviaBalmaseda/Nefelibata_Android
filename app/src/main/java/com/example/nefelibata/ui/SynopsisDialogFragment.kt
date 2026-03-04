package com.example.nefelibata.ui

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Html
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

        val tvStatus = view.findViewById<TextView>(R.id.tv_dialog_status)
        val tvGenres = view.findViewById<TextView>(R.id.tv_dialog_genres)
        val tvSynopsis = view.findViewById<TextView>(R.id.tv_dialog_synopsis)
        val ivClose = view.findViewById<ImageView>(R.id.iv_close_dialog)
        val btnClose = view.findViewById<MaterialButton>(R.id.btn_close_dialog)

        val status = arguments?.getString("status") ?: "Desconocido"
        val genres = arguments?.getString("genres") ?: "Sin géneros"
        val synopsis = arguments?.getString("synopsis") ?: "No hay sinopsis disponible."

        tvStatus.text = Html.fromHtml("<b>Estado:</b> $status", Html.FROM_HTML_MODE_LEGACY)
        tvGenres.text = Html.fromHtml("<b>Géneros:</b> $genres", Html.FROM_HTML_MODE_LEGACY)
        tvSynopsis.text = Html.fromHtml("<b>Sinopsis:</b> <i>$synopsis</i>", Html.FROM_HTML_MODE_LEGACY)

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