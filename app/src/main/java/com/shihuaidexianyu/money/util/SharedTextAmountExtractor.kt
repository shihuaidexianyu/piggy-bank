package com.shihuaidexianyu.money.util

import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.Normalizer

data class SharedAmountCandidate(
    val amountInMinor: Long,
    val score: Int,
    val matchedText: String,
)

data class SharedTextParseResult(
    val amountInMinor: Long?,
    val direction: CashFlowDirection?,
    val candidates: List<SharedAmountCandidate>,
    val isAmbiguous: Boolean,
    val isUncertain: Boolean,
)

object SharedTextAmountExtractor {
    private val amountPattern = Regex(
        "(?<![0-9-])(?:RMB|CNY)?\\s*[¥$]?\\s*" +
            "([0-9]{1,3}(?:,[0-9]{3})+(?:\\.[0-9]{1,2})?|[0-9]{1,12}(?:\\.[0-9]{1,2})?)" +
            "\\s*(?:元|块|RMB|CNY)?(?![0-9])",
        RegexOption.IGNORE_CASE,
    )
    private val datePattern = Regex(
        "(?:19|20)[0-9]{2}(?:[-/.年])[0-9]{1,2}(?:[-/.月])[0-9]{1,2}(?:日)?",
    )
    private val phonePattern = Regex("(?<![0-9])1[3-9][0-9]{9}(?![0-9])")
    private val longIdentifierPattern = Regex("(?<![0-9])[0-9]{8,}(?![0-9])")
    private val incomeKeyword = Regex("收入|到账|入账|收款|工资|薪水|退款|存入")
    private val outflowKeyword = Regex("支出|消费|付款|支付|扣款|转出|花费")
    private val moneyKeyword = Regex(
        "金额|收入|到账|入账|收款|工资|薪水|退款|存入|支出|消费|付款|支付|扣款|转出|花费",
    )

    fun parse(text: String): SharedTextParseResult {
        if (text.isBlank()) return emptyResult()
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFKC)
            .replace('￥', '¥')
            .replace('，', ',')
            .replace('．', '.')
            .replace('：', ':')
        val riskRanges = buildList {
            datePattern.findAll(normalized).forEach { add(it.range) }
            phonePattern.findAll(normalized).forEach { add(it.range) }
            longIdentifierPattern.findAll(normalized).forEach { add(it.range) }
        }
        val scored = amountPattern.findAll(normalized).mapNotNull { match ->
            if (riskRanges.any { it.overlaps(match.range) }) return@mapNotNull null
            val beforeMatch = normalized.substring(0, match.range.first)
            if (Regex("-\\s*[¥$]?\\s*$").containsMatchIn(beforeMatch)) return@mapNotNull null
            val raw = match.groupValues[1]
            val amount = raw.replace(",", "").toMinorUnits() ?: return@mapNotNull null
            if (amount <= 0L) return@mapNotNull null
            val contextStart = (match.range.first - 16).coerceAtLeast(0)
            val contextEnd = (match.range.last + 16).coerceAtMost(normalized.lastIndex)
            val context = normalized.substring(contextStart, contextEnd + 1)
            var score = 0
            if (match.value.contains('¥') || match.value.contains('$')) score += 5
            if (Regex("元|块|RMB|CNY", RegexOption.IGNORE_CASE).containsMatchIn(match.value)) score += 4
            if (moneyKeyword.containsMatchIn(context)) score += 5
            if (raw.contains('.') || raw.contains(',')) score += 1
            SharedAmountCandidate(amount, score, match.value.trim())
        }.toList()

        val plausible = scored.filter { it.score >= MIN_PLAUSIBLE_SCORE }
        if (plausible.isEmpty()) return emptyResult(candidates = scored)
        val topScore = plausible.maxOf { it.score }
        val top = plausible.filter { topScore - it.score <= AMBIGUITY_SCORE_WINDOW }
            .distinctBy { it.amountInMinor }
        val ambiguous = top.size > 1
        val selected = top.singleOrNull()
        val direction = selected?.let { inferDirection(normalized) }
        return SharedTextParseResult(
            amountInMinor = selected?.amountInMinor,
            direction = direction,
            candidates = plausible.sortedWith(
                compareByDescending<SharedAmountCandidate> { it.score }.thenBy { it.amountInMinor },
            ),
            isAmbiguous = ambiguous,
            isUncertain = ambiguous || selected == null || direction == null,
        )
    }

    fun extractAmountMillis(text: String): Long? = parse(text).amountInMinor

    private fun inferDirection(text: String): CashFlowDirection? {
        val lastIncome = incomeKeyword.findAll(text).lastOrNull()?.range?.first ?: -1
        val lastOutflow = outflowKeyword.findAll(text).lastOrNull()?.range?.first ?: -1
        return when {
            lastIncome < 0 && lastOutflow < 0 -> null
            lastIncome > lastOutflow -> CashFlowDirection.INFLOW
            lastOutflow > lastIncome -> CashFlowDirection.OUTFLOW
            else -> null
        }
    }

    private fun String.toMinorUnits(): Long? = runCatching {
        BigDecimal(this)
            .setScale(2, RoundingMode.UNNECESSARY)
            .movePointRight(2)
            .longValueExact()
    }.getOrNull()

    private fun IntRange.overlaps(other: IntRange): Boolean =
        first <= other.last && other.first <= last

    private fun emptyResult(
        candidates: List<SharedAmountCandidate> = emptyList(),
    ) = SharedTextParseResult(
        amountInMinor = null,
        direction = null,
        candidates = candidates,
        isAmbiguous = false,
        isUncertain = true,
    )

    private const val MIN_PLAUSIBLE_SCORE = 4
    private const val AMBIGUITY_SCORE_WINDOW = 0
}
