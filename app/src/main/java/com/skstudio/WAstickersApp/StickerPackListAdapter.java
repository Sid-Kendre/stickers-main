/*
 * Copyright (c) WhatsApp Inc. and its affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.skstudio.WAstickersApp;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.format.Formatter;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.facebook.drawee.view.SimpleDraweeView;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class StickerPackListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int VIEW_TYPE_ITEM = 0;
    private static final int VIEW_TYPE_LOADING = 1;

    @NonNull
    private List<StickerPack> stickerPacks;
    @NonNull
    private List<StickerPack> stickerPacksFull;
    @NonNull
    private final OnAddButtonClickedListener onAddButtonClickedListener;
    private FavoriteManager favoriteManager;
    private int maxNumberOfStickersInARow;
    private int minMarginBetweenImages;
    private boolean showLoadingFooter = false;

    StickerPackListAdapter(@NonNull List<StickerPack> stickerPacks, @NonNull OnAddButtonClickedListener onAddButtonClickedListener) {
        this.stickerPacks = new ArrayList<>(stickerPacks);
        this.stickerPacksFull = new ArrayList<>(stickerPacks);
        this.onAddButtonClickedListener = onAddButtonClickedListener;
    }

    @Override
    public int getItemViewType(int position) {
        if (showLoadingFooter && position == stickerPacks.size()) {
            return VIEW_TYPE_LOADING;
        }
        return VIEW_TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull final ViewGroup viewGroup, final int viewType) {
        final Context context = viewGroup.getContext();
        if (favoriteManager == null) {
            favoriteManager = new FavoriteManager(context);
        }
        final LayoutInflater layoutInflater = LayoutInflater.from(context);
        if (viewType == VIEW_TYPE_LOADING) {
            View view = layoutInflater.inflate(R.layout.layout_loading_footer, viewGroup, false);
            return new LoadingViewHolder(view);
        }
        final View stickerPackRow = layoutInflater.inflate(R.layout.sticker_packs_list_item, viewGroup, false);
        return new StickerPackListItemViewHolder(stickerPackRow);
    }

    @Override
    public void onBindViewHolder(@NonNull final RecyclerView.ViewHolder holder, final int index) {
        if (holder instanceof StickerPackListItemViewHolder) {
            StickerPackListItemViewHolder viewHolder = (StickerPackListItemViewHolder) holder;
            StickerPack pack = stickerPacks.get(index);
            final Context context = viewHolder.publisherView.getContext();
            viewHolder.publisherView.setText(pack.publisher);
            viewHolder.filesizeView.setText(Formatter.formatShortFileSize(context, pack.getTotalSize()));

            viewHolder.titleView.setText(pack.name);
            if (pack.category != null && !pack.category.isEmpty()) {
                viewHolder.categoryView.setText(pack.category.toUpperCase());
                viewHolder.categoryView.setVisibility(View.VISIBLE);
            } else {
                viewHolder.categoryView.setVisibility(View.GONE);
            }
            viewHolder.container.setOnClickListener(view -> {
                Intent intent = new Intent(view.getContext(), StickerPackDetailsActivity.class);
                intent.putExtra(StickerPackDetailsActivity.EXTRA_SHOW_UP_BUTTON, true);
                intent.putExtra(StickerPackDetailsActivity.EXTRA_STICKER_PACK_DATA, pack);
                view.getContext().startActivity(intent);
            });
            viewHolder.imageRowView.removeAllViews();
            //if this sticker pack contains less stickers than the max, then take the smaller size.
            int actualNumberOfStickersToShow = Math.min(maxNumberOfStickersInARow, pack.getStickers().size());
            for (int i = 0; i < actualNumberOfStickersToShow; i++) {
                final SimpleDraweeView rowImage = (SimpleDraweeView) LayoutInflater.from(context).inflate(R.layout.sticker_packs_list_image_item, viewHolder.imageRowView, false);
                rowImage.setImageURI(StickerPackLoader.getStickerUri(context, pack, pack.getStickers().get(i)));
                final LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) rowImage.getLayoutParams();
                final int marginBetweenImages = minMarginBetweenImages - lp.leftMargin - lp.rightMargin;
                if (i != actualNumberOfStickersToShow - 1 && marginBetweenImages > 0) { //do not set the margin for the last image
                    lp.setMargins(lp.leftMargin, lp.topMargin, lp.rightMargin + marginBetweenImages, lp.bottomMargin);
                    rowImage.setLayoutParams(lp);
                }
                viewHolder.imageRowView.addView(rowImage);
            }
            setAddButtonAppearance(viewHolder.addButton, pack);
            viewHolder.animatedStickerPackIndicator.setVisibility(pack.animatedStickerPack ? View.VISIBLE : View.GONE);
            updateFavoriteUI(viewHolder.favoriteButton, pack);
            viewHolder.favoriteButton.setOnClickListener(v -> {
                favoriteManager.toggleFavorite(pack.identifier);
                updateFavoriteUI(viewHolder.favoriteButton, pack);
            });
        }
    }

    private void updateFavoriteUI(ImageButton button, StickerPack pack) {
        if (favoriteManager.isFavorite(pack.identifier)) {
            button.setImageResource(R.drawable.ic_favorite_filled);
        } else {
            button.setImageResource(R.drawable.ic_favorite_border);
        }
    }

    private void setAddButtonAppearance(MaterialButton addButton, StickerPack pack) {
        if (pack.getIsWhitelisted()) {
            addButton.setIconResource(R.drawable.sticker_3rdparty_added);
            addButton.setClickable(false);
            addButton.setOnClickListener(null);
            setBackground(addButton, null);
        } else {
            addButton.setIconResource(R.drawable.sticker_3rdparty_add);
            addButton.setOnClickListener(v -> onAddButtonClickedListener.onAddButtonClicked(pack));
            TypedValue outValue = new TypedValue();
            addButton.getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
            addButton.setBackgroundResource(outValue.resourceId);
        }
    }

    private void setBackground(View view, Drawable background) {
        if (Build.VERSION.SDK_INT >= 16) {
            view.setBackground(background);
        } else {
            view.setBackgroundDrawable(background);
        }
    }

    @Override
    public int getItemCount() {
        return stickerPacks.size() + (showLoadingFooter ? 1 : 0);
    }

    void setImageRowSpec(int maxNumberOfStickersInARow, int minMarginBetweenImages) {
        this.minMarginBetweenImages = minMarginBetweenImages;
        if (this.maxNumberOfStickersInARow != maxNumberOfStickersInARow) {
            this.maxNumberOfStickersInARow = maxNumberOfStickersInARow;
            notifyDataSetChanged();
        }
    }

    void setStickerPackList(List<StickerPack> stickerPackList) {
        this.stickerPacks = new ArrayList<>(stickerPackList);
        this.stickerPacksFull = new ArrayList<>(stickerPackList);
        notifyDataSetChanged();
    }

    public void showLoadingFooter(boolean show) {
        if (this.showLoadingFooter != show) {
            this.showLoadingFooter = show;
            if (show) {
                notifyItemInserted(stickerPacks.size());
            } else {
                notifyItemRemoved(stickerPacks.size());
            }
        }
    }

    public void setData(List<StickerPack> newList) {
        this.stickerPacks = new ArrayList<>(newList);
        // Note: we don't update stickerPacksFull here because that represents the source list
        // which might be different during category filtering.
    }

    public void updateData(List<StickerPack> newList) {
        // Simple update logic: compare sizes and notify
        int oldSize = this.stickerPacks.size();
        this.stickerPacks = new ArrayList<>(newList);
        this.stickerPacksFull = new ArrayList<>(newList);
        
        if (newList.size() > oldSize) {
            notifyItemRangeInserted(oldSize, newList.size() - oldSize);
        } else {
            notifyDataSetChanged();
        }
    }

    static class LoadingViewHolder extends RecyclerView.ViewHolder {
        LoadingViewHolder(View itemView) {
            super(itemView);
        }
    }

    public void filter(String text) {
        stickerPacks = new ArrayList<>();
        if (text.isEmpty()) {
            stickerPacks.addAll(stickerPacksFull);
        } else {
            text = text.toLowerCase();
            for (StickerPack pack : stickerPacksFull) {
                boolean emojiMatch = false;
                if (pack.getStickers() != null) {
                    for (Sticker sticker : pack.getStickers()) {
                        if (sticker.emojis != null) {
                            for (String emoji : sticker.emojis) {
                                if (emoji.contains(text)) {
                                    emojiMatch = true;
                                    break;
                                }
                            }
                        }
                        if (emojiMatch) break;
                    }
                }
                if (pack.name.toLowerCase().contains(text) || pack.publisher.toLowerCase().contains(text) || emojiMatch) {
                    stickerPacks.add(pack);
                }
            }
        }
        notifyDataSetChanged();
    }

    public interface OnAddButtonClickedListener {
        void onAddButtonClicked(StickerPack stickerPack);
    }
}
