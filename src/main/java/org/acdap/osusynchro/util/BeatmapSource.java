package org.acdap.osusynchro.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class BeatmapSource {

    public interface BeatmapRefreshedListener{
        void onBeatmapRefreshed();
    }
    private ArrayList<BeatmapRefreshedListener> refreshedListeners = new ArrayList<>();

    public interface BeatmapIgnoreListener{
        void onBeatmapIgnore(int i, boolean newIgnore);
    }
    private ArrayList<BeatmapIgnoreListener> ignoreListeners = new ArrayList<>();

    // List is synchronized, should be accessed synchronously
    protected List<Beatmap> beatmaps = Collections.synchronizedList(new ArrayList<>());

    public List<Beatmap> getBeatmaps() {
        return beatmaps;
    }

    /**
     * Refreshes the list of beatmaps as designated by child classes.
     * Notifies all registered {@code BeatmapRefreshedListeners}.
     * @return whether the action was performed successfully.
     */
    public abstract boolean refreshBeatmaps();


    /**
     * Set the ignored state of the beatmap at index {@code i}.
     * Notifies all registered {@code BeatmapIgnoreListeners}.
     * @param ignore The new ignored state.
     */
    public abstract void setBeatmapIgnore(int i, boolean ignore);

    public void addBeatmapRefreshedListener(BeatmapRefreshedListener l){
        refreshedListeners.add(l);
    }
    protected void notifyOnRefresh(){
        for(var l : refreshedListeners){
            l.onBeatmapRefreshed();
        }
    }

    public void addBeatmapIgnoreListener(BeatmapIgnoreListener l){
        ignoreListeners.add(l);
    }
    protected void notifyOnIgnore(int i, boolean newIgnore){
        for(var l : ignoreListeners){
            l.onBeatmapIgnore(i, newIgnore);
        }
    }
}