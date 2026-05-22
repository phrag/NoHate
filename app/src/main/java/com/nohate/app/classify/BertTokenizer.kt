package com.nohate.app.classify

import android.content.Context
import java.text.Normalizer

/**
 * Minimal BERT-style WordPiece tokenizer for models whose ONNX graph doesn't
 * carry an embedded tokenizer op. Implements the standard HuggingFace pipeline:
 *
 *   1. BasicTokenizer: NFC, optional lowercasing + accent stripping, split on
 *      whitespace and punctuation (Unicode P* categories + ASCII punct).
 *   2. WordpieceTokenizer: greedy longest-match against `vocab.txt`, with the
 *      `##` continuation marker. Unknown words become `[UNK]`.
 *   3. Surround with `[CLS]` and `[SEP]`, pad/truncate to [maxSeqLength], emit
 *      `input_ids` + `attention_mask` as LongArrays.
 *
 * This is a parity-checked reimplementation of the Python `BertTokenizer` for
 * uncased models. Mismatches are exercised by [TokenizerParityTest].
 */
class BertTokenizer(
    private val vocab: Map<String, Int>,
    private val maxSeqLength: Int = 128,
    private val doLowerCase: Boolean = true,
) {
    private val unkId: Long = (vocab[UNK_TOKEN] ?: 100).toLong()
    private val clsId: Long = (vocab[CLS_TOKEN] ?: 101).toLong()
    private val sepId: Long = (vocab[SEP_TOKEN] ?: 102).toLong()
    private val padId: Long = (vocab[PAD_TOKEN] ?: 0).toLong()

    data class Encoded(val inputIds: LongArray, val attentionMask: LongArray) {
        // Override equals/hashCode because of LongArray fields; mostly for tests.
        override fun equals(other: Any?): Boolean = other is Encoded &&
            inputIds.contentEquals(other.inputIds) &&
            attentionMask.contentEquals(other.attentionMask)
        override fun hashCode(): Int = 31 * inputIds.contentHashCode() + attentionMask.contentHashCode()
    }

    fun encode(text: String): Encoded {
        val ids = LongArray(maxSeqLength) { padId }
        val mask = LongArray(maxSeqLength) { 0L }
        var pos = 0
        ids[pos] = clsId; mask[pos] = 1L; pos++

        val pieces = wordpiece(basicTokenize(text))
        for (p in pieces) {
            if (pos >= maxSeqLength - 1) break // leave room for [SEP]
            ids[pos] = (vocab[p] ?: unkId.toInt()).toLong()
            mask[pos] = 1L
            pos++
        }

        ids[pos] = sepId; mask[pos] = 1L
        return Encoded(ids, mask)
    }

    private fun basicTokenize(text: String): List<String> {
        var t = Normalizer.normalize(text, Normalizer.Form.NFC)
        if (doLowerCase) {
            t = t.lowercase()
            // Strip combining marks (accents) — NFD then drop NON_SPACING_MARK.
            val nfd = Normalizer.normalize(t, Normalizer.Form.NFD)
            val sb = StringBuilder(nfd.length)
            for (ch in nfd) {
                if (Character.getType(ch).toByte() != Character.NON_SPACING_MARK) sb.append(ch)
            }
            t = sb.toString()
        }
        val out = mutableListOf<String>()
        val buf = StringBuilder()
        for (ch in t) {
            when {
                ch.isWhitespace() || ch.code == 0 || ch.code == 0xFFFD -> {
                    if (buf.isNotEmpty()) { out.add(buf.toString()); buf.clear() }
                }
                isPunctuation(ch) || isCjk(ch) -> {
                    if (buf.isNotEmpty()) { out.add(buf.toString()); buf.clear() }
                    out.add(ch.toString())
                }
                else -> buf.append(ch)
            }
        }
        if (buf.isNotEmpty()) out.add(buf.toString())
        return out
    }

    private fun isPunctuation(ch: Char): Boolean {
        val code = ch.code
        // BERT treats all ASCII non-letter / non-digit as punctuation.
        if (code in 33..47 || code in 58..64 || code in 91..96 || code in 123..126) return true
        val type = Character.getType(ch).toByte()
        return type == Character.CONNECTOR_PUNCTUATION ||
            type == Character.DASH_PUNCTUATION ||
            type == Character.START_PUNCTUATION ||
            type == Character.END_PUNCTUATION ||
            type == Character.INITIAL_QUOTE_PUNCTUATION ||
            type == Character.FINAL_QUOTE_PUNCTUATION ||
            type == Character.OTHER_PUNCTUATION
    }

    private fun isCjk(ch: Char): Boolean {
        val cp = ch.code
        return (cp in 0x4E00..0x9FFF) || (cp in 0x3400..0x4DBF) ||
            (cp in 0xF900..0xFAFF) || (cp in 0x3040..0x30FF)
    }

    private fun wordpiece(words: List<String>): List<String> {
        val out = mutableListOf<String>()
        for (word in words) {
            if (word.length > 200) {
                out.add(UNK_TOKEN); continue
            }
            var start = 0
            var bad = false
            val sub = mutableListOf<String>()
            while (start < word.length) {
                var end = word.length
                var match: String? = null
                while (start < end) {
                    var cand = word.substring(start, end)
                    if (start > 0) cand = "##$cand"
                    if (vocab.containsKey(cand)) { match = cand; break }
                    end--
                }
                if (match == null) { bad = true; break }
                sub.add(match)
                start = if (match.startsWith("##")) start + match.length - 2 else start + match.length
            }
            if (bad) out.add(UNK_TOKEN) else out.addAll(sub)
        }
        return out
    }

    companion object {
        private const val UNK_TOKEN = "[UNK]"
        private const val CLS_TOKEN = "[CLS]"
        private const val SEP_TOKEN = "[SEP]"
        private const val PAD_TOKEN = "[PAD]"

        /** Load a `vocab.txt` (one token per line, line number = token id). */
        fun loadVocab(context: Context, assetPath: String): Map<String, Int> {
            val map = HashMap<String, Int>(32_768)
            context.assets.open(assetPath).bufferedReader().useLines { lines ->
                lines.forEachIndexed { idx, raw ->
                    val tok = raw.trimEnd('\n', '\r')
                    if (tok.isNotEmpty()) map[tok] = idx
                }
            }
            return map
        }
    }
}
