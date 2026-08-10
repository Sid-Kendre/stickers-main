/*
 * Copyright (c) WhatsApp Inc. and its affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.skstudio.WAstickersApp;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.WindowCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import android.view.Menu;


public class StickerPackListActivity extends AddStickerPackActivity {
    public static final String EXTRA_STICKER_PACK_LIST_DATA = "sticker_pack_list";
    private static final int STICKER_PREVIEW_DISPLAY_LIMIT = 5;
    private LinearLayoutManager packLayoutManager;
    private RecyclerView packRecyclerView;
    private StickerPackListAdapter allStickerPacksListAdapter;
    private WhiteListCheckAsyncTask whiteListCheckAsyncTask;
    private ArrayList<StickerPack> stickerPackList;
    private AdView mAdView;
    private AdView mAdView1;
    private RewardedAdManager rewardedAdManager;
    private View loadingOverlay;
    private com.facebook.shimmer.ShimmerFrameLayout shimmerFrameLayout;
    private SwipeRefreshLayout swipeRefreshLayout;
    private int currentPage = 1;
    private boolean isLoading = false;
    private boolean hasNextPage = true;
    private java.util.Map<String, StickerPack> packMap = new java.util.LinkedHashMap<>();
    private LoadMoreAsyncTask loadMoreAsyncTask;

    private List<StickerPack> originalStickerPackList;
    private View emptyStateView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sticker_pack_list);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        rewardedAdManager = RewardedAdManager.getInstance();
        rewardedAdManager.loadAd(this);

        swipeRefreshLayout = findViewById(R.id.swipe_refresh);
        swipeRefreshLayout.setOnRefreshListener(this::refreshStickerPacks);
        swipeRefreshLayout.setColorSchemeResources(R.color.colorPrimary);

        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            updateAdVisibility(id);
            if (id == R.id.nav_home) {
                showRecyclerView();
                filterByCategory("ALL");
                return true;
            } else if (id == R.id.nav_favorites) {
                showRecyclerView();
                filterByCategory("FAVORITES");
                return true;
            } else if (id == R.id.nav_settings) {
                showSettingsFragment();
                return true;
            }
            return false;
        });

        mAdView = findViewById(R.id.adView);
        mAdView1 = findViewById(R.id.adView7);
        loadingOverlay = findViewById(R.id.loading_overlay);
        shimmerFrameLayout = findViewById(R.id.shimmer_view_container);
        emptyStateView = findViewById(R.id.empty_state_view);

        packRecyclerView = findViewById(R.id.sticker_pack_list);
        stickerPackList = getIntent().getParcelableArrayListExtra(EXTRA_STICKER_PACK_LIST_DATA);
        showStickerPackList(stickerPackList != null ? stickerPackList : new ArrayList<>());

        if (stickerPackList == null) {
            refreshStickerPacks();
        }
        AdRequest adRequest = new AdRequest.Builder().build();
        mAdView.loadAd(adRequest);
        mAdView1.loadAd(adRequest);
        loadNativeAd("ca-app-pub-6979979912689100/6586602956", findViewById(R.id.native_ad_container));
        updateAdVisibility(R.id.nav_home);
        MobileAds.initialize(this, initializationStatus -> {});
        emptyStateView = findViewById(R.id.empty_state_view);
        findViewById(R.id.empty_state_button).setOnClickListener(v -> {
            bottomNavigationView.setSelectedItemId(R.id.nav_home);
        });
    }

    private void updateAdVisibility(int navId) {
        if (mAdView == null || mAdView1 == null) return;
        if (navId == R.id.nav_home) {
            mAdView.setVisibility(View.GONE);
            mAdView1.setVisibility(View.GONE);
            if (findViewById(R.id.native_ad_container) != null) {
                findViewById(R.id.native_ad_container).setVisibility(View.VISIBLE);
            }
        } else {
            mAdView.setVisibility(View.VISIBLE);
            mAdView1.setVisibility(View.VISIBLE);
            if (findViewById(R.id.native_ad_container) != null) {
                findViewById(R.id.native_ad_container).setVisibility(View.GONE);
            }
        }
    }

    private void showSettingsFragment() {
        findViewById(R.id.swipe_refresh).setVisibility(View.GONE);
        if (emptyStateView != null) emptyStateView.setVisibility(View.GONE);
        findViewById(R.id.fragment_container).setVisibility(View.VISIBLE);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, new SettingsFragment())
                .commit();
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Settings");
        }
    }

    private void showRecyclerView() {
        findViewById(R.id.swipe_refresh).setVisibility(View.VISIBLE);
        if (emptyStateView != null) emptyStateView.setVisibility(View.GONE);
        findViewById(R.id.fragment_container).setVisibility(View.GONE);
        if (getSupportActionBar() != null) {
            if (stickerPackList != null) {
                getSupportActionBar().setTitle(getResources().getQuantityString(R.plurals.title_activity_sticker_packs_list, stickerPackList.size()));
            } else {
                getSupportActionBar().setTitle(R.string.app_name);
            }
        }
    }

    private void refreshStickerPacks() {
        if (isLoading) {
            swipeRefreshLayout.setRefreshing(false);
            return;
        }
        isLoading = true;
        currentPage = 1;
        hasNextPage = true;
        showLoader(); // Show Shimmer
        new RefreshAsyncTask(this).execute();
    }

    static class RefreshAsyncTask extends AsyncTask<Void, Void, StickerPackLoader.FetchResult> {
        private final WeakReference<StickerPackListActivity> activityRef;

        RefreshAsyncTask(StickerPackListActivity activity) {
            this.activityRef = new WeakReference<>(activity);
        }

        @Override
        protected StickerPackLoader.FetchResult doInBackground(Void... voids) {
            try {
                StickerPackListActivity activity = activityRef.get();
                if (activity != null) {
                    // Start fresh for refresh
                    return StickerPackLoader.fetchFromRemoteApi(1, 200, new java.util.LinkedHashMap<>());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(StickerPackLoader.FetchResult result) {
            StickerPackListActivity activity = activityRef.get();
            if (activity != null) {
                activity.isLoading = false;
                activity.swipeRefreshLayout.setRefreshing(false);
                activity.hideLoader(); // Hide Shimmer
                if (result != null) {
                    activity.packMap.clear();
                    for (StickerPack pack : result.stickerPacks) {
                        activity.packMap.put(pack.identifier, pack);
                    }
                    activity.originalStickerPackList = new ArrayList<>(result.stickerPacks);
                    activity.stickerPackList = new ArrayList<>(activity.originalStickerPackList);
                    activity.allStickerPacksListAdapter.updateData(activity.stickerPackList);
                    if (activity.getSupportActionBar() != null) {
                        activity.getSupportActionBar().setTitle(activity.getResources().getQuantityString(R.plurals.title_activity_sticker_packs_list, activity.stickerPackList.size()));
                    }
                    Toast.makeText(activity, "Stickers updated", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(activity, "Failed to refresh stickers", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (stickerPackList != null && !stickerPackList.isEmpty()) {
            whiteListCheckAsyncTask = new WhiteListCheckAsyncTask(this);
            whiteListCheckAsyncTask.execute(stickerPackList.toArray(new StickerPack[0]));
        }
        if (rewardedAdManager != null) {
            rewardedAdManager.loadAd(this);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (whiteListCheckAsyncTask != null && !whiteListCheckAsyncTask.isCancelled()) {
            whiteListCheckAsyncTask.cancel(true);
        }
        if (loadMoreAsyncTask != null && !loadMoreAsyncTask.isCancelled()) {
            loadMoreAsyncTask.cancel(true);
        }
    }

    private void filterByCategory(String category) {
        if (category.equals("ALL")) {
            stickerPackList = new ArrayList<>(originalStickerPackList);
            if (emptyStateView != null) emptyStateView.setVisibility(View.GONE);
            swipeRefreshLayout.setVisibility(View.VISIBLE);
        } else if (category.equals("FAVORITES")) {
            stickerPackList = new ArrayList<>();
            FavoriteManager fm = new FavoriteManager(this);
            for (StickerPack pack : originalStickerPackList) {
                if (fm.isFavorite(pack.identifier)) {
                    stickerPackList.add(pack);
                }
            }
            if (stickerPackList.isEmpty()) {
                if (emptyStateView != null) emptyStateView.setVisibility(View.VISIBLE);
                swipeRefreshLayout.setVisibility(View.GONE);
            } else {
                if (emptyStateView != null) emptyStateView.setVisibility(View.GONE);
                swipeRefreshLayout.setVisibility(View.VISIBLE);
            }
        }
        allStickerPacksListAdapter.setData(stickerPackList);
        allStickerPacksListAdapter.notifyDataSetChanged();
    }

    private void showStickerPackList(List<StickerPack> stickerPackList) {
        allStickerPacksListAdapter = new StickerPackListAdapter(stickerPackList, onAddButtonClickedListener);
        packRecyclerView.setAdapter(allStickerPacksListAdapter);
        packLayoutManager = new LinearLayoutManager(this);
        packLayoutManager.setOrientation(RecyclerView.VERTICAL);
        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(
                packRecyclerView.getContext(),
                packLayoutManager.getOrientation()
        );
        packRecyclerView.addItemDecoration(dividerItemDecoration);
        packRecyclerView.setLayoutManager(packLayoutManager);
        packRecyclerView.getViewTreeObserver().addOnGlobalLayoutListener(this::recalculateColumnCount);
        setupPagination();
    }

    private void setupPagination() {
        packRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (dy > 0) { // check for scroll down
                    int visibleItemCount = packLayoutManager.getChildCount();
                    int totalItemCount = packLayoutManager.getItemCount();
                    int pastVisibleItems = packLayoutManager.findFirstVisibleItemPosition();

                    if (!isLoading && hasNextPage) {
                        if ((visibleItemCount + pastVisibleItems) >= totalItemCount - 10) {
                            loadNextPage();
                        }
                    }
                }
            }
        });
        
        // Initialize packMap with initial data
        if (stickerPackList != null) {
            for (StickerPack pack : stickerPackList) {
                packMap.put(pack.identifier, pack);
            }
        }
    }

    private void loadNextPage() {
        isLoading = true;
        allStickerPacksListAdapter.showLoadingFooter(true);
        loadMoreAsyncTask = new LoadMoreAsyncTask(this);
        loadMoreAsyncTask.execute(currentPage + 1);
    }

    static class LoadMoreAsyncTask extends AsyncTask<Integer, Void, StickerPackLoader.FetchResult> {
        private final WeakReference<StickerPackListActivity> activityRef;

        LoadMoreAsyncTask(StickerPackListActivity activity) {
            this.activityRef = new WeakReference<>(activity);
        }

        @Override
        protected StickerPackLoader.FetchResult doInBackground(Integer... params) {
            int page = params[0];
            try {
                StickerPackListActivity activity = activityRef.get();
                if (activity != null) {
                    return StickerPackLoader.fetchFromRemoteApi(page, 200, activity.packMap);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(StickerPackLoader.FetchResult result) {
            StickerPackListActivity activity = activityRef.get();
            if (activity != null) {
                activity.isLoading = false;
                activity.allStickerPacksListAdapter.showLoadingFooter(false);
                if (result != null) {
                    activity.currentPage++;
                    activity.hasNextPage = result.hasNextPage;
                    activity.originalStickerPackList = new ArrayList<>(activity.packMap.values());
                    activity.stickerPackList = new ArrayList<>(activity.originalStickerPackList);
                    activity.allStickerPacksListAdapter.updateData(activity.stickerPackList);
                } else {
                    Toast.makeText(activity, "Failed to load more stickers", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }


    private final StickerPackListAdapter.OnAddButtonClickedListener onAddButtonClickedListener =
            pack -> {
                if (!isInternetAvailable()) {
                    Toast.makeText(this, "Internet not available", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (!StickerPackLoader.isStickerPackDownloaded(this, pack)) {
                    showLoader();
                    Toast.makeText(this, "Downloading stickers, please wait...", Toast.LENGTH_SHORT).show();
                    StickerPackLoader.downloadStickers(this, pack, () -> {
                        hideLoader();
                        // Proceed with ad after download
                        showAdAndAdd(pack);
                    });
                } else {
                    showAdAndAdd(pack);
                }
            };

    private void showAdAndAdd(StickerPack pack) {
        if (rewardedAdManager.isAdLoaded()) {
            rewardedAdManager.showAd(this, rewardItem -> {
                addStickerPackToWhatsApp(pack.identifier, pack.name);
            });
        } else {
            // If ad is not ready, add the pack directly to ensure good UX, but try loading for next time
            addStickerPackToWhatsApp(pack.identifier, pack.name);
            rewardedAdManager.loadAd(this);
        }
    }

    private void showLoader() {
        if (loadingOverlay != null) {
            loadingOverlay.setVisibility(View.VISIBLE);
        }
        if (shimmerFrameLayout != null) {
            shimmerFrameLayout.startShimmer();
        }
    }

    private void hideLoader() {
        if (loadingOverlay != null) {
            loadingOverlay.setVisibility(View.GONE);
        }
        if (shimmerFrameLayout != null) {
            shimmerFrameLayout.stopShimmer();
        }
    }

    private boolean isInternetAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network network = cm.getActiveNetwork();
            if (network == null) return false;

            NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
            return capabilities != null &&
                    (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR));
        } else {
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnected();
        }
    }

    private void recalculateColumnCount() {
        final int previewSize = getResources().getDimensionPixelSize(R.dimen.sticker_pack_list_item_preview_image_size);
        int firstVisibleItemPosition = packLayoutManager.findFirstVisibleItemPosition();
        StickerPackListItemViewHolder viewHolder = (StickerPackListItemViewHolder) packRecyclerView.findViewHolderForAdapterPosition(firstVisibleItemPosition);
        if (viewHolder != null) {
            final int widthOfImageRow = viewHolder.imageRowView.getMeasuredWidth();
            final int max = Math.max(widthOfImageRow / previewSize, 1);
            int maxNumberOfImagesInARow = Math.min(STICKER_PREVIEW_DISPLAY_LIMIT, max);
            int minMarginBetweenImages = (widthOfImageRow - maxNumberOfImagesInARow * previewSize) / (maxNumberOfImagesInARow - 1);
            allStickerPacksListAdapter.setImageRowSpec(maxNumberOfImagesInARow, minMarginBetweenImages);
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_sticker_pack_list, menu);
        MenuItem searchItem = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchItem.getActionView();
        searchView.setQueryHint("Search stickers, emojis...");
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if (allStickerPacksListAdapter != null) {
                    allStickerPacksListAdapter.filter(newText);
                }
                return true;
            }
        });
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public void onBackPressed() {
        if (findViewById(R.id.fragment_container).getVisibility() == View.VISIBLE) {
            BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
            bottomNavigationView.setSelectedItemId(R.id.nav_home);
        } else {
            super.onBackPressed();
        }
    }

    private void shareApp() {
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, getString(R.string.share_text, "https://play.google.com/store/apps/details?id=" + getPackageName()));
        sendIntent.setType("text/plain");
        startActivity(Intent.createChooser(sendIntent, "Share via"));
    }

    private void showHowToUse() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.how_to_use_title)
                .setMessage(R.string.how_to_use_content)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void contactUs() {
        Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", "skstudio@example.com", null));
        intent.putExtra(Intent.EXTRA_SUBJECT, "Support: " + getString(R.string.app_name));
        startActivity(Intent.createChooser(intent, "Send email..."));
    }

    private void rateApp() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + getPackageName())));
        } catch (android.content.ActivityNotFoundException anfe) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + getPackageName())));
        }
    }

    private void moreApps() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://dev?id=8066128835537801410")));
        } catch (android.content.ActivityNotFoundException anfe) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/dev?id=8066128835537801410")));
        }
    }

    private void openPrivacyPolicy() {
        String url = getString(R.string.privacy_policy);
        if (!url.startsWith("http")) {
            url = "https://skstudio.com/privacy-policy"; // Fallback or use real URL from strings
        }
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(intent);
    }

    static class WhiteListCheckAsyncTask extends AsyncTask<StickerPack, Void, List<StickerPack>> {
        private final WeakReference<StickerPackListActivity> stickerPackListActivityWeakReference;

        WhiteListCheckAsyncTask(StickerPackListActivity stickerPackListActivity) {
            this.stickerPackListActivityWeakReference = new WeakReference<>(stickerPackListActivity);
        }

        @Override
        protected final List<StickerPack> doInBackground(StickerPack... stickerPackArray) {
            final StickerPackListActivity stickerPackListActivity = stickerPackListActivityWeakReference.get();
            if (stickerPackListActivity == null) {
                return Arrays.asList(stickerPackArray);
            }
            for (StickerPack stickerPack : stickerPackArray) {
                stickerPack.setIsWhitelisted(WhitelistCheck.isWhitelisted(stickerPackListActivity, stickerPack.identifier));
            }
            return Arrays.asList(stickerPackArray);
        }

        @Override
        protected void onPostExecute(List<StickerPack> stickerPackList) {
            final StickerPackListActivity stickerPackListActivity = stickerPackListActivityWeakReference.get();
            if (stickerPackListActivity != null) {
                stickerPackListActivity.allStickerPacksListAdapter.setStickerPackList(stickerPackList);
                stickerPackListActivity.allStickerPacksListAdapter.notifyDataSetChanged();
            }
        }
    }
}
