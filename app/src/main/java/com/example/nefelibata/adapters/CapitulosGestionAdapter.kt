package com.example.nefelibata.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.nefelibata.databinding.ItemCapituloGestionBinding
import com.example.nefelibata.models.Capitulo

class CapitulosGestionAdapter(
    private val lista: List<Capitulo>,
    private val onEdit: (Capitulo) -> Unit,
    private val onDelete: (Capitulo) -> Unit
) : RecyclerView.Adapter<CapitulosGestionAdapter.CapHolder>() {

    class CapHolder(val binding: ItemCapituloGestionBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CapHolder {
        val binding = ItemCapituloGestionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CapHolder(binding)
    }

    override fun onBindViewHolder(holder: CapHolder, position: Int) {
        val cap = lista[position]
        with(holder.binding) {
            tvItemCapNum.text = "Cap. ${cap.numCapitulo}"
            tvItemCapTitulo.text = cap.tituloCap.takeIf { !it.isNullOrEmpty() } ?: "Cap. ${cap.numCapitulo}"
            
            ivItemCapEdit.setOnClickListener { onEdit(cap) }
            ivItemCapDelete.setOnClickListener { onDelete(cap) }
        }
    }

    override fun getItemCount() = lista.size
}
