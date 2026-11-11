package com.example.sucharu;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** RecyclerView adapter for the real service catalog returned by the existing API. */
public final class ServiceAdapter extends RecyclerView.Adapter<ServiceAdapter.ServiceViewHolder> {
    public interface Listener {
        void onRequest(ServiceItem item);
        void onDetails(ServiceItem item);
        void onFavorite(ServiceItem item);
    }

    private final Context context;
    private final Listener listener;
    private final List<ServiceItem> items = new ArrayList<>();
    private final Set<String> favoriteIds = new HashSet<>();

    public ServiceAdapter(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
        setHasStableIds(true);
    }

    public void submitItems(List<ServiceItem> updated) {
        items.clear();
        if (updated != null) items.addAll(updated);
        notifyDataSetChanged();
    }

    public void setFavoriteIds(Set<String> updated) {
        favoriteIds.clear();
        if (updated != null) favoriteIds.addAll(updated);
        notifyDataSetChanged();
    }

    @Override public long getItemId(int position) {
        return items.get(position).id.hashCode();
    }

    @NonNull @Override public ServiceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        MaterialCardView card = Ui.card(context);
        card.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(context, 17), Ui.dp(context, 16), Ui.dp(context, 17), Ui.dp(context, 14));
        card.addView(content);

        LinearLayout topRow = new LinearLayout(context);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView category = Ui.text(context, "", 11, Ui.GREEN, true);
        topRow.addView(category, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView favorite = Ui.text(context, "♡", 25, Ui.MUTED, true);
        favorite.setGravity(Gravity.CENTER);
        favorite.setPadding(Ui.dp(context, 8), 0, Ui.dp(context, 4), 0);
        topRow.addView(favorite, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(context, 38)));
        content.addView(topRow);

        TextView name = Ui.text(context, "", 19, Ui.INK, true);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nameParams.topMargin = Ui.dp(context, 5);
        content.addView(name, nameParams);
        TextView description = Ui.text(context, "", 13, Ui.MUTED, false);
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        descriptionParams.topMargin = Ui.dp(context, 5);
        content.addView(description, descriptionParams);
        TextView price = Ui.text(context, "", 15, Ui.GREEN, true);
        LinearLayout.LayoutParams priceParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        priceParams.topMargin = Ui.dp(context, 9);
        content.addView(price, priceParams);

        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button request = new Button(context);
        request.setText("Request service"); request.setAllCaps(false); request.setTextColor(Color.WHITE);
        request.setBackgroundTintList(ColorStateList.valueOf(Ui.GREEN));
        Button details = new Button(context);
        details.setText("Details"); details.setAllCaps(false);
        LinearLayout.LayoutParams requestParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        requestParams.topMargin = Ui.dp(context, 5); requestParams.rightMargin = Ui.dp(context, 4);
        actions.addView(request, requestParams);
        LinearLayout.LayoutParams detailsParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        detailsParams.topMargin = Ui.dp(context, 5); detailsParams.leftMargin = Ui.dp(context, 4);
        actions.addView(details, detailsParams);
        content.addView(actions);

        return new ServiceViewHolder(card, category, name, description, price, favorite, request, details);
    }

    @Override public void onBindViewHolder(@NonNull ServiceViewHolder holder, int position) {
        ServiceItem item = items.get(position);
        boolean saved = favoriteIds.contains(item.id);
        holder.category.setText(item.icon + "  " + item.category.toUpperCase(Locale.ROOT));
        holder.name.setText(item.name);
        holder.description.setText(item.description);
        holder.description.setVisibility(item.description.isEmpty() ? View.GONE : View.VISIBLE);
        holder.price.setText(item.startingPrice == null ? "QUOTE REQUIRED" : "STARTING AT  ৳" + NumberFormat.getIntegerInstance(Locale.forLanguageTag("en-BD")).format(item.startingPrice));
        holder.favorite.setText(saved ? "♥" : "♡");
        holder.favorite.setTextColor(saved ? Color.rgb(201, 72, 91) : Ui.MUTED);
        holder.favorite.setContentDescription(saved ? "Remove from saved services" : "Save service");
        holder.favorite.setOnClickListener(v -> listener.onFavorite(item));
        holder.request.setOnClickListener(v -> listener.onRequest(item));
        holder.details.setOnClickListener(v -> listener.onDetails(item));
    }

    @Override public int getItemCount() { return items.size(); }

    static final class ServiceViewHolder extends RecyclerView.ViewHolder {
        final TextView category, name, description, price, favorite;
        final Button request, details;
        ServiceViewHolder(@NonNull View itemView, TextView category, TextView name, TextView description,
                          TextView price, TextView favorite, Button request, Button details) {
            super(itemView);
            this.category = category; this.name = name; this.description = description;
            this.price = price; this.favorite = favorite; this.request = request; this.details = details;
        }
    }
}
