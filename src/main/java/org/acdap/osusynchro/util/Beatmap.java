package org.acdap.osusynchro.util;

import java.util.Objects;

public class Beatmap implements Comparable<Beatmap> {

    private final int id;
    private final String name;
    private boolean ignore;

    public Beatmap(int id, String name, boolean ignore){
        this.id = id;
        this.name = name;
        this.ignore = ignore;
    }

    public Beatmap(int id, String name){
        this(id, name, false);
    }

    public int id(){
        return id;
    }
    public String name(){
        return name;
    }
    public boolean ignore(){
        return ignore;
    }
    public void setIgnore(boolean newIgnore){
        ignore = newIgnore;
    }

    @Override
    public int compareTo(Beatmap o) {
        return id - o.id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Beatmap beatmap = (Beatmap) o;
        return id == beatmap.id && ignore == beatmap.ignore && Objects.equals(name, beatmap.name);
    }
}
