package com.kittenml.tts.scoring

/**
 * Computes Word Error Rate between a reference paragraph and an ASR
 * hypothesis using Levenshtein alignment over normalized words.
 *
 *   WER = (substitutions + deletions + insertions) / reference_word_count
 *
 * The backtrace also yields a word-level [AlignedWord] list so the UI can
 * highlight which words were read correctly, mis-read, or skipped.
 */
object WerScorer {

    /** Lowercase, strip punctuation (keep intra-word apostrophes), split on whitespace. */
    fun normalize(text: String): List<String> {
        return text
            .lowercase()
            .replace(Regex("[^a-z0-9'\\s]"), " ")
            .replace(Regex("(?<![a-z0-9])'|'(?![a-z0-9])"), " ") // edge apostrophes
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
    }

    fun score(reference: String, hypothesis: String): ScoreResult {
        val ref = normalize(reference)
        val hyp = normalize(hypothesis)
        val n = ref.size
        val m = hyp.size

        // DP cost matrix; cost[i][j] = edit distance between ref[0..i) and hyp[0..j)
        val cost = Array(n + 1) { IntArray(m + 1) }
        for (i in 0..n) cost[i][0] = i
        for (j in 0..m) cost[0][j] = j
        for (i in 1..n) {
            for (j in 1..m) {
                if (ref[i - 1] == hyp[j - 1]) {
                    cost[i][j] = cost[i - 1][j - 1]
                } else {
                    val sub = cost[i - 1][j - 1] + 1
                    val del = cost[i - 1][j] + 1     // ref word skipped
                    val ins = cost[i][j - 1] + 1     // extra hyp word
                    cost[i][j] = minOf(sub, del, ins)
                }
            }
        }

        // Backtrace to build alignment + count ops.
        var i = n
        var j = m
        var sCount = 0
        var dCount = 0
        var iCount = 0
        var cCount = 0
        val rev = ArrayList<AlignedWord>(n + m)

        while (i > 0 || j > 0) {
            when {
                i > 0 && j > 0 && ref[i - 1] == hyp[j - 1] -> {
                    rev.add(AlignedWord(WordOp.CORRECT, ref[i - 1], hyp[j - 1]))
                    cCount++; i--; j--
                }
                i > 0 && j > 0 && cost[i][j] == cost[i - 1][j - 1] + 1 -> {
                    rev.add(AlignedWord(WordOp.SUBSTITUTED, ref[i - 1], hyp[j - 1]))
                    sCount++; i--; j--
                }
                i > 0 && cost[i][j] == cost[i - 1][j] + 1 -> {
                    rev.add(AlignedWord(WordOp.DELETED, ref[i - 1], null))
                    dCount++; i--
                }
                else -> {
                    rev.add(AlignedWord(WordOp.INSERTED, null, hyp[j - 1]))
                    iCount++; j--
                }
            }
        }
        rev.reverse()

        val wer = if (n == 0) {
            if (m == 0) 0f else 1f
        } else {
            (sCount + dCount + iCount).toFloat() / n.toFloat()
        }

        return ScoreResult(
            wer = wer,
            referenceWordCount = n,
            correct = cCount,
            substitutions = sCount,
            deletions = dCount,
            insertions = iCount,
            alignment = rev,
            transcript = hypothesis.trim()
        )
    }
}
