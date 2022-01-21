package org.acdap.osusynchro.util;

import org.acdap.osusynchro.network.NetworkManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

public class RemoteBeatmapSource extends BeatmapSource{

    private NetworkManager network;

    public void updateNetwork(NetworkManager network){
        this.network = network;
    }

    public void remoteUpdateBeatmaps(ArrayList<Beatmap> beatmaps){
        this.beatmaps = Collections.synchronizedList(beatmaps);
        notifyOnRefresh();
    }

    @Override
    public boolean refreshBeatmaps() {
        if(!network.isConnected()) return false;
        network.sendRemoteBeatmapsRequest();
        return true;
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
