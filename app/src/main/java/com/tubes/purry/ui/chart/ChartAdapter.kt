package com.tubes.purry.ui.chart

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tubes.purry.R
import com.tubes.purry.data.model.ChartItem

class ChartAdapter(
    private val items: List<ChartItem>,
    private val onClick: (ChartItem) -> Unit
) : RecyclerView.Adapter<ChartAdapter.ChartViewHolder>() {

    inner class ChartViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val chartImage: ImageView = view.findViewById(R.id.chartImage)
        val chartTitle: TextView = view.findViewById(R.id.chartTitle)
        val chartDescription: TextView = view.findViewById(R.id.chartDescription)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChartViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chart_card, parent, false)
        return ChartViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ChartViewHolder, position: Int) {
        val item = items[position]
        holder.chartImage.setImageResource(item.imageRes)
        holder.chartTitle.text = item.title
        holder.chartDescription.text = item.description
        holder.itemView.setOnClickListener { onClick(item) }
    }
}
