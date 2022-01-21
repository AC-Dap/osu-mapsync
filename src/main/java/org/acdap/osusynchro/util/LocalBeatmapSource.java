package org.acdap.osusynchro.util;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;

public class LocalBeatmapSource extends BeatmapSource{

    public String beatmapSourcePath = "";

    @Override
    public boolean refreshBeatmaps() {
        ArrayList<Beatmap> updatedList = FileManager.getAllBeatmaps(Paths.get(beatmapSourcePath));
        if(updatedList == null){
            beatmaps = null;
        }else{
            beatmaps = Collections.synchronizedList(updatedList);
        }
        notifyOnRefresh();
        return beatmaps != null;
    }

    @Override
    public void setBeatmapIgnore(int i, boolean ignore) {
        if(i >= beatmaps.size()) return;
        if(beatmaps.get(i).ignore() != ignore){
            beatmaps.get(i).setIgnore(ignore);
            notifyOnIgnore(i, ignore);
        }
    }
}
