package org.acdap.osusynchro.util;

import org.acdap.osusynchro.network.NetworkManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

public class RemoteBeatmapSource extends BeatmapSource{

    private String remoteAddress = "";
    private String connectedAddress = "";

    private NetworkManager network;

    public void updateNetwork(NetworkManager network){
        this.network = network;
    }

    public void updateRemoteAddress(String address){
        remoteAddress = address;
    }

    public void remoteUpdateBeatmaps(ArrayList<Beatmap> beatmaps){
        this.beatmaps = Collections.synchronizedList(beatmaps);
        notifyOnRefresh();
    }

    @Override
    public boolean refreshBeatmaps() {
        if(!connectedAddress.equals(remoteAddress)){
            connectedAddress = remoteAddress;
            try {
                network.startClientConnection(connectedAddress);
            } catch (IOException e) {
                System.out.println("Error connecting to " + connectedAddress);
                return false;
            }
        }
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
