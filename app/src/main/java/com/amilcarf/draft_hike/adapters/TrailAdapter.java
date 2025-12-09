package com.amilcarf.draft_hike.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.amilcarf.draft_hike.R;
import com.amilcarf.draft_hike.models.Trail;
import java.util.List;

public class TrailAdapter extends RecyclerView.Adapter<TrailAdapter.TrailViewHolder> {

    private List<Trail> trailList;
    private OnItemClickListener onItemClickListener;
    private OnFavoriteClickListener onFavoriteClickListener;
    private OnStartTrailClickListener onStartTrailClickListener;

    // Interfaces for click events
    public interface OnItemClickListener {
        void onItemClick(Trail trail);
    }

    public interface OnFavoriteClickListener {
        void onFavoriteClick(Trail trail, int position);
    }

    public interface OnStartTrailClickListener {
        void onStartTrailClick(Trail trail);
    }

    // Constructor
    public TrailAdapter(List<Trail> trailList, OnItemClickListener onItemClickListener,
                        OnFavoriteClickListener onFavoriteClickListener,
                        OnStartTrailClickListener onStartTrailClickListener) {
        this.trailList = trailList;
        this.onItemClickListener = onItemClickListener;
        this.onFavoriteClickListener = onFavoriteClickListener;
        this.onStartTrailClickListener = onStartTrailClickListener;
    }

    @NonNull
    @Override
    public TrailViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_trail, parent, false);
        return new TrailViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TrailViewHolder holder, int position) {
        Trail trail = trailList.get(position);
        holder.bind(trail);

        // Set click listeners
        holder.itemView.setOnClickListener(v -> {
            if (onItemClickListener != null) {
                onItemClickListener.onItemClick(trail);
            }
        });

        holder.favoriteIcon.setOnClickListener(v -> {
            if (onFavoriteClickListener != null) {
                onFavoriteClickListener.onFavoriteClick(trail, position);
            }
        });

        holder.btnViewDetails.setOnClickListener(v -> {
            if (onItemClickListener != null) {
                onItemClickListener.onItemClick(trail);
            }
        });

        holder.btnStartTrail.setOnClickListener(v -> {
            if (onStartTrailClickListener != null) {
                onStartTrailClickListener.onStartTrailClick(trail);
            }
        });
    }

    @Override
    public int getItemCount() {
        return trailList != null ? trailList.size() : 0;
    }

    // Update data method
    public void updateData(List<Trail> newTrailList) {
        this.trailList = newTrailList;
        notifyDataSetChanged();
    }

    // Update single item
    public void updateItem(int position, Trail trail) {
        if (position >= 0 && position < trailList.size()) {
            trailList.set(position, trail);
            notifyItemChanged(position);
        }
    }

    // ViewHolder class
    static class TrailViewHolder extends RecyclerView.ViewHolder {
        TextView trailName;
        TextView trailDistance;
        TextView trailDuration;
        TextView trailBenches;
        TextView trailDifficulty;
        TextView trailStatus;
        TextView trailDescription;
        ImageView favoriteIcon;
        Button btnViewDetails;
        Button btnStartTrail;

        public TrailViewHolder(@NonNull View itemView) {
            super(itemView);

            // Initialize views
            trailName = itemView.findViewById(R.id.trailName);
            trailDistance = itemView.findViewById(R.id.trailDistance);
            trailDuration = itemView.findViewById(R.id.trailDuration);
            trailBenches = itemView.findViewById(R.id.trailBenches);
            trailDifficulty = itemView.findViewById(R.id.trailDifficulty);
            trailStatus = itemView.findViewById(R.id.trailStatus);
            trailDescription = itemView.findViewById(R.id.trailDescription);
            favoriteIcon = itemView.findViewById(R.id.favoriteIcon);
            btnViewDetails = itemView.findViewById(R.id.btnViewDetails);
            btnStartTrail = itemView.findViewById(R.id.btnStartTrail);
        }

        public void bind(Trail trail) {
            trailName.setText(trail.getName());
            trailDistance.setText(String.format("%.1f km", trail.getDistance()));
            trailDuration.setText(trail.getDuration());
            trailBenches.setText(trail.getBenchCount() + " benches");
            trailDifficulty.setText(trail.getDifficulty());
            trailStatus.setText(trail.getStatus());
            trailDescription.setText(trail.getDescription());

            // Favorite icon
            int favoriteIconRes = trail.isFavorite() ?
                    android.R.drawable.btn_star_big_on : android.R.drawable.btn_star_big_off;;
            favoriteIcon.setImageResource(favoriteIconRes);

            // Difficulty background
            String difficulty = trail.getDifficulty().toLowerCase();
            int difficultyBg;
            int difficultyTextColor;

            if (difficulty.contains("hard")) {
                difficultyBg = R.drawable.bg_difficulty_hard;
                difficultyTextColor = itemView.getContext().getResources().getColor(R.color.difficulty_hard_text);
            } else if (difficulty.contains("medium")) {
                difficultyBg = R.drawable.bg_difficulty_medium;
                difficultyTextColor = itemView.getContext().getResources().getColor(R.color.difficulty_medium_text);
            } else {
                difficultyBg = R.drawable.bg_difficulty_easy;
                difficultyTextColor = itemView.getContext().getResources().getColor(R.color.difficulty_easy_text);
            }

            trailDifficulty.setBackgroundResource(difficultyBg);
            trailDifficulty.setTextColor(difficultyTextColor);

            // status background
            String status = trail.getStatus().toLowerCase();
            int statusBg = status.contains("open") ?
                    R.drawable.bg_status_open : R.drawable.bg_status_closed;
            int statusTextColor = status.contains("open") ?
                    itemView.getContext().getResources().getColor(R.color.status_open_text) :
                    itemView.getContext().getResources().getColor(R.color.status_closed_text);

            trailStatus.setBackgroundResource(statusBg);
            trailStatus.setTextColor(statusTextColor);
        }
    }
}