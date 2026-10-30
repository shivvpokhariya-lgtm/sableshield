package com.sableshield.app

/**
 * SableShield rule engine — direct port of the web demo's ruleBased().
 * Same patterns, same weights, same scoring. No ML, no network: pure
 * keyword + tactic matching, so it works offline and never fails.
 *
 * Three pattern sets run together, because real Indian scam calls mix
 * all three: English, Hindi (Devanagari, as the Hindi Vosk model outputs),
 * and Roman Hinglish ("account block ho jayega", "kisi ko mat batana").
 * The match patterns stay multilingual — the flag text shown to the
 * user is always English, because the app UI is purely English.
 */
object ScamEngine {

    data class Verdict(
        val type: String, // "scam" or "safe"
        val title: String,
        val score: String,
        val sub: String,
        val flags: List<String>,
        val safe: List<String>
    )

    // regex -> flag text -> score weight
    private data class Rule(val pattern: String, val flag: String, val weight: Int)

    private val englishRules = listOf(
        Rule(
            "otp|one time password|verification code",
            "An OTP is being requested — a real bank or company never asks for an OTP on a call",
            40
        ),
        Rule(
            "block|blocked|freeze|suspend|close.*account|urgent|immediately|within.*hour|2 hours|today itself",
            "Urgency / threat — “account blocked” used to rush you",
            25
        ),
        Rule(
            "do not tell|don.t tell|secret|confidential|don.t inform",
            "“Do not tell anyone” — isolation tactic",
            20
        ),
        Rule(
            "password|pin|card number|cvv|bank detail|account number",
            "Password, PIN, or card details are being requested",
            30
        ),
        Rule(
            "prize|won|lottery|gift|offer.*expire|click.*link|download.*app|anydesk|teamviewer",
            "Prize, link, or remote-access app — common scam pattern",
            25
        ),
        Rule(
            "police|arrest|legal|case|fine",
            "Police / legal threat — fear tactic",
            20
        )
    )

    // Devanagari — output of the Hindi Vosk model (vosk-model-small-hi-0.22).
    private val hindiRules = listOf(
        Rule(
            "ओटीपी|वन टाइम पासवर्ड|सत्यापन कोड",
            "An OTP is being requested — a real bank never asks for an OTP on a call",
            40
        ),
        Rule(
            "ब्लॉक|बंद हो जाएगा|बंद हो जायेगा|फ्रीज़|तुरंत|अभी|2 घंटे|दो घंटे|आज ही|खाता बंद",
            "Urgency / threat — “account closing” used to rush you",
            25
        ),
        Rule(
            "किसी को मत बताना|मत बताना|गुप्त|किसी से मत कहना|सीक्रेट",
            "“Do not tell anyone” — isolation tactic, so you cannot ask for help",
            20
        ),
        Rule(
            "पासवर्ड|पिन|कार्ड नंबर|सीवीवी|बैंक डिटेल|खाता नंबर|एटीएम",
            "Password, PIN, or card details are being requested",
            30
        ),
        Rule(
            "इनाम|लॉटरी|जीत|ऑफर|लिंक पर क्लिक|क्लिक करो|ऐप डाउनलोड|एनीडेस्क|टीमव्यूअर",
            "Prize, link, or remote-access app — common scam pattern",
            25
        ),
        Rule(
            "पुलिस|गिरफ्तार|कानूनी|केस|जुर्माना|एफआईआर",
            "Police / legal threat — fear tactic",
            20
        )
    )

    // Roman Hinglish — only patterns that do NOT overlap the English set,
    // so one scam line is never double-counted.
    private val hinglishRules = listOf(
        Rule(
            "band ho jayega|band ho jaega|turant|abhi|jaldi|do ghante",
            "Urgency / threat — “account will be closed” used to rush you",
            25
        ),
        Rule(
            "kisi ko mat|mat batana",
            "“Do not tell anyone” — isolation tactic",
            20
        ),
        Rule(
            "khata number|bank khata",
            "Account details are being requested",
            30
        ),
        Rule(
            "inaam|jeet gaye",
            "Prize lure — common scam pattern",
            25
        ),
        Rule(
            "giraftar|kanuni|jurmana|challan",
            "Legal threat — fear tactic",
            20
        )
    )

    fun analyze(text: String): Verdict {
        val t = text.lowercase()
        val flags = mutableListOf<String>()
        var score = 5
        for (rule in englishRules + hindiRules + hinglishRules) {
            if (Regex(rule.pattern).containsMatchIn(t)) {
                if (!flags.contains(rule.flag)) {
                    flags.add(rule.flag)
                    score += rule.weight
                }
            }
        }
        if (score > 98) score = 98
        if (flags.isEmpty()) {
            return Verdict(
                type = "safe",
                title = "NO SCAM SIGNALS FOUND",
                score = "LOW RISK",
                sub = "No classic scam patterns found. Still: never share an OTP or password on a call.",
                flags = emptyList(),
                safe = listOf(
                    "No OTP or password demand",
                    "No urgency or threat",
                    "No isolation pressure"
                )
            )
        }
        return Verdict(
            type = "scam",
            title = "SCAM SIGNALS FOUND — BE CAREFUL",
            score = "$score% SCAM RISK",
            sub = "${flags.size} red flag(s) found. If the caller is asking for an OTP or password, hang up — this is almost certainly a scam.",
            flags = flags,
            safe = emptyList()
        )
    }
}
