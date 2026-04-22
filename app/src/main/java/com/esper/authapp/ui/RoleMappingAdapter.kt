package com.esper.authapp.ui

import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.RecyclerView
import com.esper.authapp.config.RoleGroupMapping
import com.esper.authapp.databinding.ItemRoleMappingBinding

class RoleMappingAdapter(
    private val items: MutableList<RoleGroupMapping>
) : RecyclerView.Adapter<RoleMappingAdapter.RoleMappingViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RoleMappingViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return RoleMappingViewHolder(ItemRoleMappingBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: RoleMappingViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun addMapping(mapping: RoleGroupMapping = RoleGroupMapping(role = "", groupId = "")) {
        items += mapping
        notifyItemInserted(items.lastIndex)
    }

    fun getMappings(): List<RoleGroupMapping> {
        return items.map {
            RoleGroupMapping(role = it.role.trim(), groupId = it.groupId.trim())
        }.filter { it.role.isNotEmpty() || it.groupId.isNotEmpty() }
    }

    inner class RoleMappingViewHolder(
        private val binding: ItemRoleMappingBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        private var roleWatcher: TextWatcher? = null
        private var groupWatcher: TextWatcher? = null

        fun bind(item: RoleGroupMapping) {
            roleWatcher?.let(binding.roleEditText::removeTextChangedListener)
            groupWatcher?.let(binding.groupIdEditText::removeTextChangedListener)

            if (binding.roleEditText.text?.toString() != item.role) {
                binding.roleEditText.setText(item.role)
            }
            if (binding.groupIdEditText.text?.toString() != item.groupId) {
                binding.groupIdEditText.setText(item.groupId)
            }

            roleWatcher = binding.roleEditText.doAfterTextChanged { text ->
                val position = bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION } ?: return@doAfterTextChanged
                items[position] = items[position].copy(role = text?.toString().orEmpty())
            }
            groupWatcher = binding.groupIdEditText.doAfterTextChanged { text ->
                val position = bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION } ?: return@doAfterTextChanged
                items[position] = items[position].copy(groupId = text?.toString().orEmpty())
            }

            binding.deleteButton.setOnClickListener {
                val position = bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION } ?: return@setOnClickListener
                items.removeAt(position)
                notifyItemRemoved(position)
            }
        }
    }
}
