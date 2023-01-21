export type SongFolder = {
    id: number,
    name: string,
    checksum: string
}

export type SongFolderMatch = "None" | "Direct" | "Similar" | "Missing";

export type SongFolderWithMatch = {
    song: SongFolder,
    match: SongFolderMatch
}