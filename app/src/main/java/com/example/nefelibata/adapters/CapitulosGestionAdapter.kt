package com.example.nefelibata.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.nefelibata.R
import com.example.nefelibata.models.Capitulo

class CapitulosGestionAdapter(
    private val lista: List<Capitulo>,
    private val onEdit: (Capitulo) -> Unit,
    private val onDelete: (Capitulo) -> Unit
) : RecyclerView.Adapter<CapitulosGestionAdapter.CapHolder>() {

    class CapHolder(v: View) : RecyclerView.ViewHolder(v) {
        val tvNum: TextView = v.findViewById(R.id.tv_item_cap_num)
        val tvTitulo: TextView = v.findViewById(R.id.tv_item_cap_titulo)
        val ivEdit: ImageView = v.findViewById(R.id.iv_item_cap_edit)
        val ivDelete: ImageView = v.findViewById(R.id.iv_item_cap_delete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CapHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_capitulo_gestion, parent, false)
        return CapHolder(v)
    }

    override fun onBindViewHolder(holder: CapHolder, position: Int) {
        val cap = lista[position]
        holder.tvNum.text = "Cap. ${cap.numCapitulo}"
        // Si no hay título, mostramos "Cap " + el número del capítulo
        holder.tvTitulo.text = if (cap.tituloCap.isNullOrEmpty()) "Cap. ${cap.numCapitulo}" else cap.tituloCap
        
        holder.ivEdit.setOnClickListener { onEdit(cap) }
        holder.ivDelete.setOnClickListener { onDelete(cap) }
    }

    override fun getItemCount() = lista.size
}