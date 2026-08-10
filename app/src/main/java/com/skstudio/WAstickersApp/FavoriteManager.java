package com.skstudio.WAstickersApp;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;

public class FavoriteManager {
    private static final String PREF_NAME = "sticker_favorites";
    private static final String KEY_FAVORITES = "favorite_packs";
    private final SharedPreferences prefs;

    public FavoriteManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public boolean isFavorite(String identifier) {
        Set<String> favorites = prefs.getStringSet(KEY_FAVORITES, new HashSet<>());
        return favorites.contains(identifier);
    }

    public void toggleFavorite(String identifier) {
        Set<String> favorites = new HashSet<>(prefs.getStringSet(KEY_FAVORITES, new HashSet<>()));
        if (favorites.contains(identifier)) {
            favorites.remove(identifier);
        } else {
            favorites.add(identifier);
        }
        prefs.edit().putStringSet(KEY_FAVORITES, favorites).apply();
    }
}
