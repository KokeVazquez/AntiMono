package com.antimono.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.antimono.app.databinding.ItemBlacklistBinding

class BlacklistAdapter(
    private val onDeleteClicked: (String) -> Unit
) : RecyclerView.Adapter<BlacklistAdapter.ViewHolder>() {

    private val items = mutableListOf<String>()

    fun submitList(newItems: List<String>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBlacklistBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val domain = items[position]
        holder.binding.tvDomain.text = domain
        holder.binding.btnDelete.setOnClickListener {
            onDeleteClicked(domain)
        }
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(val binding: ItemBlacklistBinding) : RecyclerView.ViewHolder(binding.root)
}
