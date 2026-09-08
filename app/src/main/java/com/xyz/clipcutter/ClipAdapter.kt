package com.xyz.clipcutter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ClipAdapter(
    private val clips: MutableList<ClipRange>,
    private val onPreview: (Int) -> Unit,
    private val onEdit: (Int) -> Unit,
    private val onRemove: (Int) -> Unit
) : RecyclerView.Adapter<ClipAdapter.ClipViewHolder>() {

    class ClipViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val label: TextView = view.findViewById(R.id.txtClipLabel)
        val editBtn: Button = view.findViewById(R.id.btnEditClip)
        val removeBtn: Button = view.findViewById(R.id.btnRemoveClip)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClipViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_clip, parent, false)
        return ClipViewHolder(view)
    }

    override fun onBindViewHolder(holder: ClipViewHolder, position: Int) {
        holder.label.text = clips[position].label(position)
        holder.itemView.setOnClickListener { onPreview(holder.bindingAdapterPosition) }
        holder.editBtn.setOnClickListener { onEdit(holder.bindingAdapterPosition) }
        holder.removeBtn.setOnClickListener { onRemove(holder.bindingAdapterPosition) }
    }

    override fun getItemCount(): Int = clips.size
}
