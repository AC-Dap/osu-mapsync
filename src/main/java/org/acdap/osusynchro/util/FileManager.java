package org.acdap.osusynchro.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
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
     * Returns a list of beatmaps sorted by id, that are in {@code local}, but missing in {@code remote}.
     * @param local A <b>sorted</b> list of beatmaps that are on the local side.
     * @param remote A <b>sorted</b> list of beatmaps that are on the remote side.
     */
    public static ArrayList<Beatmap> getMissingBeatmaps(List<Beatmap> local, List<Beatmap> remote){
        ArrayList<Beatmap> missing = new ArrayList<>();

        // Loop through local and remote, taking advantage of the fact
        // that both lists are sorted
        int l = 0;
        int r = 0;
        while(l < local.size() && r < remote.size()){
            int lId = local.get(l).id();
            int rId = remote.get(r).id();
            if(lId > rId){ // ID in remote but not in local
                r++;
            }else if(lId == rId){ // ID in both local and remote
                l++;
                r++;
            }else{ // ID in local but not remote
                missing.add(local.get(l));
                l++;
            }
        }

        // Mark remaining IDs in local as missing
        for(; l < local.size(); l++){
            missing.add(local.get(l));
        }

        return missing;
    }


    /**
     * Zips the map folders matching the given beatmaps in {@code rootDir} into .osz files,
     * then zips everything into one larger zip folder. Assumes that all the ids are present
     * in {@code rootDir} and the map folders are formatted correctly.
     * @param rootDir The path to the directory containing the map folders.
     * @param bms The beatmaps to zip.
     */
    public static Path zipBeatmaps(Path rootDir, List<Beatmap> bms) throws IOException {
        Path tempFolder = Files.createTempDirectory("osusyncro");
        Path zipPath = rootDir.resolve("Missing-" + new SimpleDateFormat("MMddyy-hhmmss").format(new Date()) + ".zip");

        try(ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))){
            for(Beatmap bm : bms){
                String folder = bm.id() + " " + bm.name();
                Path p = rootDir.resolve(folder);

                Path osz = zipOsz(p, tempFolder);
                if(osz == null){
                    System.out.println("Error zipping " + bm);
                    continue;
                }

                zos.putNextEntry(new ZipEntry(osz.getFileName().toString()));
                Files.copy(osz, zos);
                zos.closeEntry();
                System.out.println("Added " + bm);
            }
        }

        // Delete temp folder when done
        try (Stream<Path> walk = Files.walk(tempFolder)) {
            walk.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }

        return zipPath;
    }

    /**
     * Copies and zips the given {@code beatmapFolder} into a .osz file, saved in the given {@code saveFolder}.
     * @return The path to the newly created .osz file, or null if the given paths aren't directories.
     */
    public static Path zipOsz(Path beatmapFolder, Path saveFolder){
        if(!Files.isDirectory(beatmapFolder) || !Files.isDirectory(saveFolder)) return null;

        Path oszFile = saveFolder.resolve(beatmapFolder.getFileName() + ".osz");
        try(ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(oszFile.toFile()))){
            // Traverse beatmapFolder, copying each file and subdirectory to zos
            SimpleFileVisitor<Path> fv = new SimpleFileVisitor<>() {
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    zos.putNextEntry(new ZipEntry(beatmapFolder.relativize(file).toString()));
                    Files.copy(file, zos);
                    zos.closeEntry();
                    return FileVisitResult.CONTINUE;
                }
            };

            Files.walkFileTree(beatmapFolder, fv);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return oszFile;
    }
}
