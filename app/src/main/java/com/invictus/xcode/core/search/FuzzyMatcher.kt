package com.invictus.xcode.core.search

/** Plan 3.6: simple subsequence fuzzy match -- no heavy library needed. */
object FuzzyMatcher {
    data class Result(val score: Int, val indices: List<Int>)

    fun match(query: String, candidate: String): Result? {
        if (query.isEmpty()) return Result(0, emptyList())
        val q = query.lowercase()
        val c = candidate.lowercase()
        val indices = ArrayList<Int>(q.length)
        var ci = 0
        for (i in q.indices) {
            val found = c.indexOf(q[i], ci)
            if (found < 0) return null
            indices.add(found)
            ci = found + 1
        }
        var score = 0
        var run = 0
        for (i in indices.indices) {
            val pos = indices[i]
            val prev = if (i == 0) -2 else indices[i - 1]
            if (pos == prev + 1) {
                run++
                score += 10 + run * 4
            } else {
                run = 0
            }
            when {
                pos == 0 -> score += 8
                candidate[pos - 1] in "/\\_.- " -> score += 7 // start-of-word / separator
                candidate[pos - 1].isLowerCase() && candidate[pos].isUpperCase() -> score += 5 // camelCase boundary
            }
            score -= pos / 16 // earlier match is better
        }
        score -= candidate.length / 32 // shorter name wins ties
        return Result(score, indices)
    }
}
