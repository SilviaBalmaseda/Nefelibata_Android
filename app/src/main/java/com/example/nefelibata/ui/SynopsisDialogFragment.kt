package com.example.nefelibata.ui

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.example.nefelibata.databinding.DialogSynopsisBinding

class SynopsisDialogFragment : DialogFragment() {

    private var _binding: DialogSynopsisBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogSynopsisBinding.inflate(inflater, container, false)
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvDialogStatusValue.text = arguments?.getString(ARG_STATUS) ?: ""
        binding.tvDialogGenresValue.text = arguments?.getString(ARG_GENRES) ?: ""
        binding.tvDialogSynopsisValue.text = arguments?.getString(ARG_SYNOPSIS) ?: ""

        binding.ivCloseDialog.setOnClickListener { dismiss() }
        binding.btnCloseDialog.setOnClickListener { dismiss() }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_STATUS = "status"
        private const val ARG_GENRES = "genres"
        private const val ARG_SYNOPSIS = "synopsis"

        fun newInstance(status: String, genres: String, synopsis: String): SynopsisDialogFragment {
            return SynopsisDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_STATUS, status)
                    putString(ARG_GENRES, genres)
                    putString(ARG_SYNOPSIS, synopsis)
                }
            }
        }
    }
}
