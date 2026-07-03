package com.gso.webpconverter

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class FileItem(
    val uri: Uri,
    val name: String,
    var selected: Boolean = true,
    var status: String = ""
)

class FileAdapter(
    private val items: List<FileItem>,
    private val onSelectionChanged: () -> Unit
) : RecyclerView.Adapter<FileAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val checkBox: CheckBox = view.findViewById(R.id.itemCheck)
        val nameText: TextView = view.findViewById(R.id.itemName)
        val statusText: TextView = view.findViewById(R.id.itemStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.nameText.text = item.name
        holder.statusText.text = item.status
        holder.checkBox.setOnCheckedChangeListener(null)
        holder.checkBox.isChecked = item.selected
        holder.checkBox.setOnCheckedChangeListener { _, checked ->
            item.selected = checked
            onSelectionChanged()
        }
        holder.itemView.setOnClickListener {
            holder.checkBox.isChecked = !holder.checkBox.isChecked
        }
    }

    override fun getItemCount() = items.size
}
