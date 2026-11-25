package com.arcturus.haven.ui;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import com.arcturus.haven.R;
import com.arcturus.haven.services.ILoadIconCallback;
import com.arcturus.haven.services.IHavenService;
import com.arcturus.haven.util.ApplicationInfoWrapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class AppListAdapter extends RecyclerView.Adapter<AppListAdapter.ViewHolder> {
    class ViewHolder extends RecyclerView.ViewHolder {
        private MaterialCardView mCard;
        private ImageView mIcon;
        private TextView mTitle;
        private TextView mPackage;
        // This text view shows the order of all selected items
        private TextView mSelectOrder;
        int mIndex = -1;
        ViewHolder(View view) {
            super(view);
            mCard = view.findViewById(R.id.list_app_card);
            mIcon = view.findViewById(R.id.list_app_icon);
            mTitle = view.findViewById(R.id.list_app_title);
            mPackage = view.findViewById(R.id.list_app_package);
            mSelectOrder = view.findViewById(R.id.list_app_select_order);
            view.setOnClickListener((v) -> onClick());
            if (mAllowMultiSelect) {
                view.setOnLongClickListener((v) -> onLongClick());
            }
        }

        private int currentIndex() {
            int pos = getBindingAdapterPosition();
            return pos != RecyclerView.NO_POSITION ? pos : mIndex;
        }

        void onClick() {
            int pos = currentIndex();
            if (pos == -1) return;

            if (!mMultiSelectMode) {
                // Show available operations via the Fragment
                // pass the full info to it, since we can't be sure
                // the index won't change
                if (mContextMenuHandler != null) {
                    mContextMenuHandler.showContextMenu(mList.get(pos), itemView);
                }
            } else {
                // In multi-select mode, single clicks just adds to the selection
                // or cancels the selection if already selected
                if (!mSelectedIndices.contains(pos)) {
                    select();
                } else {
                    deselect();
                }
            }
        }

        boolean onLongClick() {
            int pos = currentIndex();
            if (pos == -1) return false;

            // If we have an action mode handler, we notify it to enter
            // action mode on long click, and register this adapter
            // to be in multi-select mode
            if (!mMultiSelectMode && mActionModeHandler != null && mActionModeHandler.createActionMode()) {
                mMultiSelectMode = true;
                select();
                return true;
            } else {
                return false;
            }
        }

        // When the user selects the item
        // we need to play the animation of the "select order" appearing
        // on the right side of the item view
        void select() {
            int pos = currentIndex();
            if (pos == -1) return;
            mSelectedIndices.add(pos);
            mSelectOrder.clearAnimation();
            mSelectOrder.startAnimation(AnimationUtils.loadAnimation(itemView.getContext(), R.anim.scale_appear));
            showSelectOrder(pos);
        }

        // When the user deselects the item
        void deselect() {
            int pos = currentIndex();
            if (pos == -1) return;
            mSelectedIndices.remove((Integer) pos);
            mSelectOrder.clearAnimation();
            setUnselectedBackground(pos);
            Animation anim = AnimationUtils.loadAnimation(itemView.getContext(), R.anim.scale_hide);
            anim.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {

                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    if (mActionModeCancelHandler != null && mSelectedIndices.isEmpty()) {
                        // If there is no selection left, tell parent to cancel the action mode.
                        mActionModeCancelHandler.cancelActionMode();
                    }
                    // Update the deselected item and remaining selected items whose order badge shifted.
                    if (pos < mList.size()) {
                        notifyItemChanged(pos);
                    }
                    for (int selectedIdx : new ArrayList<>(mSelectedIndices)) {
                        if (selectedIdx >= 0 && selectedIdx < mList.size()) {
                            notifyItemChanged(selectedIdx);
                        }
                    }
                }

                @Override
                public void onAnimationRepeat(Animation animation) {

                }
            });
            mSelectOrder.startAnimation(anim);
        }

        // When an item should be displayed in selected state
        // (not necessarily when the user clicked on it; the view might have been recycled)
        void showSelectOrder(int pos) {
            if (!mList.get(pos).isHidden()) {
                setCardColor(R.color.selectedAppBackground);
            } else {
                setCardColor(R.color.selectedAndDisabledAppBackground);
            }
            mSelectOrder.setVisibility(View.VISIBLE);
            mSelectOrder.setText(String.valueOf(mSelectedIndices.indexOf(pos) + 1));
        }

        // When an item should be displayed in deselected state
        void hideSelectOrder(int pos) {
            setUnselectedBackground(pos);
            mSelectOrder.setVisibility(View.GONE);
        }

        // Set the background when not in the selected state
        void setUnselectedBackground(int pos) {
            if (!mList.get(pos).isHidden()) {
                setCardColor(R.color.md_theme_surface);
            } else {
                setCardColor(R.color.disabledAppBackground);
            }
        }

        void setCardColor(int colorRes) {
            mCard.setCardBackgroundColor(ContextCompat.getColor(itemView.getContext(), colorRes));
        }

        void setIndex(final int index) {
            mIndex = index;

            if (mIndex >= 0) {
                // Clear all animations first
                mSelectOrder.clearAnimation();

                ApplicationInfoWrapper info = mList.get(mIndex);
                mPackage.setText(info.getPackageName());

                if (info.isHidden()) {
                    String label = String.format(mLabelDisabled, info.getLabel());
                    mTitle.setText(label);
                } else {
                    mTitle.setText(info.getLabel());
                }

                // Special logic when in multi-select mode and this item is selected
                if (mMultiSelectMode && mSelectedIndices.contains(mIndex)) {
                    showSelectOrder(mIndex);
                } else {
                    hideSelectOrder(mIndex);
                }

                // Load the application icon from cache
                // or populate the cache through the service
                if (mIconCache.containsKey(info.getPackageName())) {
                    mIcon.setImageBitmap(mIconCache.get(info.getPackageName()));
                } else {
                    mIcon.setImageDrawable(mDefaultIcon);

                    try {
                        mService.loadIcon(info, new ILoadIconCallback.Stub() {
                            @Override
                            public void callback(Bitmap icon) {
                                if (currentIndex() == index) {
                                    mHandler.post(() -> mIcon.setImageBitmap(icon));
                                }

                                synchronized (AppListAdapter.class) {
                                    mIconCache.put(info.getPackageName(), icon);
                                }
                            }
                        });
                    } catch (RemoteException e) {
                        // Do Nothing
                    }
                }
            }
        }
    }

    interface ContextMenuHandler {
        void showContextMenu(ApplicationInfoWrapper info, View view);
    }

    interface ActionModeHandler {
        boolean createActionMode();
    }

    interface ActionModeCancelHandler {
        void cancelActionMode();
    }

    // The ORIGINAL list of applications without filtering
    private List<ApplicationInfoWrapper> mOrigList = new ArrayList<>();
    // The list of applications that is ACTUALLY displayed
    // (after filtering by search query if applicable)
    private List<ApplicationInfoWrapper> mList = new ArrayList<>();
    private String mSearchQuery = null;
    private IHavenService mService;
    private Drawable mDefaultIcon;
    private String mLabelDisabled;
    private Map<String, Bitmap> mIconCache = new HashMap<>();
    private ContextMenuHandler mContextMenuHandler = null;
    private ActionModeHandler mActionModeHandler = null;
    private ActionModeCancelHandler mActionModeCancelHandler = null;
    private Handler mHandler = new Handler(Looper.getMainLooper());

    // Multi-selection mode
    private boolean mAllowMultiSelect = false;
    private boolean mMultiSelectMode = false;
    private List<Integer> mSelectedIndices = new ArrayList<>();

    AppListAdapter(IHavenService service, Drawable defaultIcon) {
        mService = service;
        mDefaultIcon = defaultIcon;
    }

    void setContextMenuHandler(ContextMenuHandler handler) {
        mContextMenuHandler = handler;
    }

    // When we enter multi-select mode, we have to notify our parent fragment
    // to enter action mode, in order to show the specific menus
    void setActionModeHandler(ActionModeHandler handler) {
        mActionModeHandler = handler;
    }

    // When there is no selection left, we have to notify our parent fragment
    // to exit action mode
    void setActionModeCancelHandler(ActionModeCancelHandler handler) {
        mActionModeCancelHandler = handler;
    }

    void allowMultiSelect() {
        mAllowMultiSelect = true;
    }

    boolean isMultiSelectMode() {
        return mMultiSelectMode;
    }

    void cancelMultiSelectMode() {
        mMultiSelectMode = false;
        List<Integer> previousSelection = new ArrayList<>(mSelectedIndices);
        mSelectedIndices.clear();
        for (int selectedIdx : previousSelection) {
            if (selectedIdx >= 0 && selectedIdx < mList.size()) {
                notifyItemChanged(selectedIdx);
            }
        }
    }

    List<ApplicationInfoWrapper> getSelectedItems() {
        if (!mMultiSelectMode) return null;
        if (mSelectedIndices.size() == 0) return null;

        return mSelectedIndices.stream()
                .map((idx) -> mList.get(idx))
                .collect(Collectors.toList());
    }

    void setData(List<ApplicationInfoWrapper> apps) {
        mOrigList.clear();
        mIconCache.clear();
        mOrigList.addAll(apps);
        notifyChange();
    }

    // null = clear search query
    void setSearchQuery(String query) {
        mSearchQuery = query == null ? null : query.toLowerCase(Locale.ROOT).trim();
        notifyChange();
    }

    // Call this on ACTUAL data set change and/or search query change
    private void notifyChange() {
        List<ApplicationInfoWrapper> oldList = new ArrayList<>(mList);
        List<ApplicationInfoWrapper> newList;
        if (mSearchQuery == null) {
            // No search query, do not filter
            newList = new ArrayList<>(mOrigList);
        } else {
            // Filter by search query
            newList = mOrigList.stream()
                    .filter((app) ->
                            app.getPackageName().toLowerCase(Locale.ROOT).contains(mSearchQuery)
                                    || app.getLabel().toLowerCase(Locale.ROOT).contains(mSearchQuery))
                    .collect(Collectors.toList());
        }
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldList.size();
            }

            @Override
            public int getNewListSize() {
                return newList.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return Objects.equals(
                        oldList.get(oldItemPosition).getPackageName(),
                        newList.get(newItemPosition).getPackageName());
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                ApplicationInfoWrapper oldItem = oldList.get(oldItemPosition);
                ApplicationInfoWrapper newItem = newList.get(newItemPosition);
                return Objects.equals(oldItem.getLabel(), newItem.getLabel())
                        && oldItem.isHidden() == newItem.isHidden()
                        && oldItem.getEnabled() == newItem.getEnabled();
            }
        });
        mList = newList;
        diffResult.dispatchUpdatesTo(this);
    }

    @Override
    public int getItemCount() {
        return mList.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        if (mLabelDisabled == null) {
            mLabelDisabled = viewGroup.getContext().getString(R.string.list_item_disabled);
        }
        LayoutInflater inflater = LayoutInflater.from(viewGroup.getContext());
        View view = inflater.inflate(R.layout.app_list_item, viewGroup, false);
        ViewHolder vh = new ViewHolder(view);
        vh.setIndex(i);
        return vh;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder viewHolder, int i) {
        viewHolder.setIndex(i);
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        super.onViewRecycled(holder);
        holder.setIndex(-1);
    }
}
