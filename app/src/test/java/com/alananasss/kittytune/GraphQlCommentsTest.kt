package com.alananasss.kittytune

import com.alananasss.kittytune.domain.*
import com.alananasss.kittytune.utils.AppUtils
import org.junit.Assert.*
import org.junit.Test

class GraphQlCommentsTest {

    @Test
    fun testCreatorReactionMapping() {
        val rawComment = GraphQlApiComment(
            urn = "soundcloud:comments:123456",
            body = "Incredible track!",
            trackTime = 42000L,
            createdAtTimestamp = 1792603589000L,
            user = GraphQlApiUser(
                urn = "soundcloud:users:999",
                username = "FanUser",
                permalink = "fan-user",
                userAvatarUrlTemplate = "https://i1.sndcdn.com/avatars-xxx-{size}.jpg",
                verified = false
            ),
            reactions = GraphQlCommentReactions(
                creatorReaction = "sc:interactiontypevalue:like",
                userReaction = "sc:interactiontypevalue:like",
                reactionCounts = listOf(
                    GraphQlReactionCount(
                        reactionTypeValueUrn = "sc:interactiontypevalue:like",
                        count = 42
                    )
                )
            ),
            replies = null
        )

        val comment = rawComment.toComment(
            creatorArtworkUrl = "https://i1.sndcdn.com/avatars-artist.jpg",
            creatorUsername = "Skrillex"
        )

        assertEquals(123456L, comment.id)
        assertEquals("Incredible track!", comment.body)
        assertEquals(42000L, comment.trackTimestamp)
        assertEquals(999L, comment.user?.id)
        assertEquals("FanUser", comment.user?.username)
        assertEquals("https://i1.sndcdn.com/avatars-xxx-large.jpg", comment.user?.avatarUrl)
        assertTrue(comment.isLiked)
        assertTrue(comment.isLikedByCreator)
        assertEquals("https://i1.sndcdn.com/avatars-artist.jpg", comment.creatorArtworkUrl)
        assertEquals("Skrillex", comment.creatorUsername)
        assertEquals(42, comment.likesCount)
    }

    @Test
    fun testCreatorReactionNotPresent() {
        val rawComment = GraphQlApiComment(
            urn = "soundcloud:comments:654321",
            body = "Normal comment",
            trackTime = 0L,
            createdAtTimestamp = 1792603589000L,
            user = GraphQlApiUser(
                urn = "soundcloud:users:888",
                username = "OtherUser",
                permalink = "other-user",
                userAvatarUrlTemplate = null,
                verified = true
            ),
            reactions = GraphQlCommentReactions(
                creatorReaction = null,
                userReaction = null,
                reactionCounts = emptyList()
            ),
            replies = null
        )

        val comment = rawComment.toComment(
            creatorArtworkUrl = "https://i1.sndcdn.com/avatars-artist.jpg",
            creatorUsername = "Skrillex"
        )

        assertEquals(654321L, comment.id)
        assertFalse(comment.isLikedByCreator)
        assertNull(comment.creatorArtworkUrl)
        assertNull(comment.creatorUsername)
        assertFalse(comment.isLiked)
        assertEquals(0, comment.likesCount)
    }

    @Test
    fun testNestedRepliesMapping() {
        val replyComment = GraphQlApiComment(
            urn = "soundcloud:comments:99999",
            body = "Thank you!",
            trackTime = null,
            createdAtTimestamp = 1792604000000L,
            user = GraphQlApiUser(
                urn = "soundcloud:users:777",
                username = "ArtistProducer",
                permalink = "artist-producer",
                userAvatarUrlTemplate = null,
                verified = true
            ),
            reactions = GraphQlCommentReactions(
                creatorReaction = "sc:interactiontypevalue:like",
                userReaction = null,
                reactionCounts = listOf(
                    GraphQlReactionCount(
                        reactionTypeValueUrn = "sc:interactiontypevalue:like",
                        count = 5
                    )
                )
            ),
            replies = null
        )

        val parentComment = GraphQlApiComment(
            urn = "soundcloud:comments:11111",
            body = "Top song",
            trackTime = 12000L,
            createdAtTimestamp = 1792603589000L,
            user = GraphQlApiUser(
                urn = "soundcloud:users:888",
                username = "OtherUser"
            ),
            reactions = null,
            replies = GraphQlCommentReplies(
                comments = listOf(replyComment),
                total = 1L
            )
        )

        val comment = parentComment.toComment(
            creatorArtworkUrl = "https://i1.sndcdn.com/avatars-artist.jpg",
            creatorUsername = "ArtistProducer"
        )

        assertEquals(11111L, comment.id)
        assertFalse(comment.isLikedByCreator)
        assertNotNull(comment.replies)
        assertEquals(1, comment.replies?.size)

        val reply = comment.replies!![0]
        assertEquals(99999L, reply.id)
        assertEquals("Thank you!", reply.body)
        assertTrue(reply.isLikedByCreator)
        assertEquals("ArtistProducer", reply.creatorUsername)
        assertEquals("https://i1.sndcdn.com/avatars-artist.jpg", reply.creatorArtworkUrl)
        assertEquals(5, reply.likesCount)
    }

    @Test
    fun testGsonDeserializationFromSoundCloudGraphQlPayload() {
        val sampleJson = """
        {
          "data": {
            "trackComments": {
              "comments": [
                {
                  "body": "Banger!",
                  "trackTime": 80433,
                  "createdAtTimestamp": 1792603589000,
                  "urn": "soundcloud:comments:2607069971",
                  "user": {
                    "urn": "soundcloud:users:1767130740",
                    "username": "Natalya",
                    "permalink": "natalya",
                    "userAvatarUrlTemplate": "https://i1.sndcdn.com/avatars-xxx-{size}.jpg",
                    "verified": false
                  },
                  "reactions": {
                    "creatorReaction": "sc:interactiontypevalue:like",
                    "reactionCounts": [
                      {
                        "reactionTypeValueUrn": "sc:interactiontypevalue:like",
                        "count": 14
                      }
                    ],
                    "userReaction": "sc:interactiontypevalue:like"
                  },
                  "replies": {
                    "comments": [],
                    "total": 0,
                    "pageInfo": {
                      "endCursor": null,
                      "hasNextPage": false
                    }
                  }
                }
              ],
              "pageInfo": {
                "endCursor": "soundcloud:comments:2607069971",
                "hasNextPage": true
              }
            }
          }
        }
        """.trimIndent()

        val parsed = AppUtils.gson.fromJson(sampleJson, GraphQlCommentsResponse::class.java)
        assertNotNull(parsed.data?.trackComments)
        val comments = parsed.data!!.trackComments!!.comments
        assertNotNull(comments)
        assertEquals(1, comments!!.size)

        val c = comments[0].toComment(
            creatorArtworkUrl = "https://i1.sndcdn.com/creator.jpg",
            creatorUsername = "OfficialArtist"
        )
        assertEquals(2607069971L, c.id)
        assertEquals("Banger!", c.body)
        assertTrue(c.isLikedByCreator)
        assertTrue(c.isLiked)
        assertEquals(14, c.likesCount)
        assertEquals("OfficialArtist", c.creatorUsername)
        assertEquals("https://i1.sndcdn.com/creator.jpg", c.creatorArtworkUrl)
        assertEquals("soundcloud:comments:2607069971", parsed.data!!.trackComments!!.pageInfo?.endCursor)
        assertTrue(parsed.data!!.trackComments!!.pageInfo?.hasNextPage == true)
    }
}
