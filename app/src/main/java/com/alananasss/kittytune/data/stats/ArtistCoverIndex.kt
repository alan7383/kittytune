package com.alananasss.kittytune.data.stats

/** Rank is the existing sorted track order, including tracks without artwork. */
internal class ArtistCoverIndex(tracks: List<Pair<Long, String?>>) {
    private data class Cover(val rank: Int, val url: String?)
    private val byId = HashMap<Long, Cover>(tracks.size)

    init {
        tracks.forEachIndexed { rank, (id, url) -> byId.putIfAbsent(id, Cover(rank, url)) }
    }

    fun coverFor(creditedTrackIds: Iterable<Long>): String? {
        var rank = Int.MAX_VALUE
        var cover: String? = null
        for (id in creditedTrackIds) {
            val candidate = byId[id] ?: continue
            if (candidate.rank < rank) {
                rank = candidate.rank
                cover = candidate.url
            }
        }
        return cover
    }
}
