package com.sableshield.app

/**
 * Scripted demo calls — the same scripts as the web demo, played line
 * by line into the demo card on the start page. The verdict is NOT
 * scripted: the real ScamEngine scores the full transcript, so the
 * demo shows the real engine working.
 */
object DemoScripts {
    val SCAM_LINES: List<Pair<String, String>> = listOf(
        "Caller" to "Hello, I am calling from your bank's security department. This is urgent.",
        "Caller" to "We have detected suspicious activity on your account. Your account will be BLOCKED in 2 hours.",
        "You" to "What? What do I need to do?",
        "Caller" to "Do not worry, I will help you. An OTP has been sent to your phone. Please share it so I can verify your identity.",
        "Caller" to "Please hurry, if you do not share it now your money will be frozen and you will lose access.",
        "Caller" to "Also, do not tell anyone about this call — it is a confidential security matter."
    )
    val SAFE_LINES: List<Pair<String, String>> = listOf(
        "Caller" to "Hello, this is BawliManav from your bank. I am calling about your new debit card delivery.",
        "Caller" to "Your card will arrive in 3-5 working days. No action is needed from your side.",
        "You" to "Okay, thank you. Do you need anything from me?",
        "Caller" to "No, I do not need any OTP, password, or PIN. If you have questions, please call the number on the back of your card."
    )
}
