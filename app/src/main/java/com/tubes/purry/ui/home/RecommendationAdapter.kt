package com.tubes.purry.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tubes.purry.R
import com.tubes.purry.data.model.RecommendationItem

class RecommendationAdapter(
    private val items: List<RecommendationItem>,
    private val onItemClick: (RecommendationItem) -> Unit
) : RecyclerView.Adapter<RecommendationAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.ivRecommendationImage)
        val title: TextView = view.findViewById(R.id.tvRecommendationTitle)
        val description: TextView = view.findViewById(R.id.tvRecommendationDescription)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recommendation, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        holder.image.setImageResource(item.imageRes)
        holder.title.text = item.title
        holder.description.text = item.description

        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount() = items.size
}