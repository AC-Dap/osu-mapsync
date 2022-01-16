package org.acdap.osusynchro.util;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class FileManager {

    private static class SongDirectoryFilter implements DirectoryStream.Filter<Path>{

        // Song directory format (id name)
        static final Pattern patt = Pattern.compile("[0-9]+ .*");

        @Override
        public boolean accept(Path entry) throws IOException {
            return Files.isDirectory(entry) && patt.matcher(entry.getFileName().toString()).matches();
        }
    }

    /**
     * Returns a list of all the beatmaps, sorted by id, found in the given map directory.
     * Assumes all the map folder names have the format "<b>numerical-id</b> <i>map-name</i>".
     * @param rootDir The path to the directory containing all the map folders.
     */
    public static ArrayList<Beatmap> getAllBeatmaps(Path rootDir){
        ArrayList<Beatmap> bms = new ArrayList<>();

        // Filter for only song directories
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(rootDir, new SongDirectoryFilter())){
            for(Path p : ds){
                String dirName = p.getFileName().toString();
                String[] split = dirName.split(" ", 2);

                int id = Integer.parseInt(split[0]);
                String mapName = split[1];
                bms.add(new Beatmap(id, mapName));
            }
        } catch (IOException e) {
            return null;
        }

        bms.sort(Comparator.naturalOrder());
        System.out.println(bms);
        return bms;
    }


    /**
     * Returns a list of beatmaps sorted by id, that are in {@code remote}, but missing in {@code local}.
     * @param local A <b>sorted</b> list of beatmaps that are on the local side.
     * @param remote A <b>sorted</b> list of beatmaps that are on the remote side.
     */
    public static ArrayList<Beatmap> getMissingLocal(ArrayList<Beatmap> local, ArrayList<Beatmap> remote){
        ArrayList<Beatmap> missing = new ArrayList<>();

        // Loop through local and remote, taking advantage of the fact
        // that both lists are sorted
        int i = 0;
        int j = 0;
        while(i < local.size() && j < remote.size()){
            int lId = local.get(i).id();
            int rId = remote.get(j).id();
            if(lId < rId){ // ID in local but not in remote
                i++;
            }else if(lId == rId){ // ID in both local and remote
                i++;
                j++;
            }else{ // ID in remote but not local
                missing.add(remote.get(j));
                j++;
            }
        }

        // Mark remaining IDs in remote as missing
        for(; j < remote.size(); j++){
            missing.add(remote.get(j));
        }

        return missing;
    }


    /**
     * Zips the map folders matching the given beatmaps in {@code rootDir}. Assumes that all the
     * ids are present in {@code rootDir} and the map folders are formatted correctly.
     * @param rootDir The path to the directory containing the map folders.
     * @param bms The beatmaps to zip.
     */
    public static Path zipBeatmaps(Path rootDir, ArrayList<Beatmap> bms){
        Path zipPath = rootDir.resolve("Missing-" + new SimpleDateFormat("MMddyy-hhmmss").format(new Date()) + ".zip");

        try(ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))){
            SimpleFileVisitor<Path> fv = new SimpleFileVisitor<>() {
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    zos.putNextEntry(new ZipEntry(rootDir.relativize(file).toString()));
                    Files.copy(file, zos);
                    zos.closeEntry();
                    return FileVisitResult.CONTINUE;
                }
            };

            for(Beatmap bm : bms){
                String folder = bm.id() + " " + bm.name();
                Path p = rootDir.resolve(folder);
                Files.walkFileTree(p, fv);
                System.out.println("Added " + bm);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return zipPath;
    }
}
