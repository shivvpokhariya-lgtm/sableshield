/* SableShield — demo + live analysis logic (Tailwind edition).
   Demo: scripted transcripts (zero fail).
   Live: Gemma 4 via Gemini API if a key is present, else instant rule-based analysis.
   No audio is recorded, stored, or transmitted by this demo page.
   No emojis anywhere in UI strings — clean editorial text only. */

let demoTimer = null;

const SCAM_SCRIPT = [
  { who: 'Caller', cls: 'scammer', text: "Hello, I am calling from your bank's security department. This is urgent.", flag: null },
  { who: 'Caller', cls: 'scammer', text: 'We have detected suspicious activity on your account. Your account will be BLOCKED in 2 hours.', flag: 'URGENCY TACTIC — "2 hours" pressure to stop you thinking' },
  { who: 'You', cls: 'you', text: 'What? What do I need to do?', flag: null },
  { who: 'Caller', cls: 'scammer', text: 'Do not worry, I will help you. An OTP has been sent to your phone. Please share it so I can verify your identity.', flag: 'CRITICAL — Real banks NEVER ask for OTP on a call' },
  { who: 'Caller', cls: 'scammer', text: 'Please hurry, if you do not share it now your money will be frozen and you will lose access.', flag: 'FEAR + URGENCY COMBO — classic scam pattern' },
  { who: 'Caller', cls: 'scammer', text: 'Also, do not tell anyone about this call — it is a confidential security matter.', flag: 'ISOLATION TACTIC — scammers stop you asking family or friends for help' },
];

const SAFE_SCRIPT = [
  { who: 'Caller', cls: 'scammer', text: 'Hello, this is BawliManav from your bank. I am calling about your new debit card delivery.', flag: null },
  { who: 'Caller', cls: 'scammer', text: 'Your card will arrive in 3-5 working days. No action is needed from your side.', flag: 'NO URGENT ACTION DEMANDED' },
  { who: 'You', cls: 'you', text: 'Okay, thank you. Do you need anything from me?', flag: null },
  { who: 'Caller', cls: 'scammer', text: 'No, I do not need any OTP, password, or PIN. If you have questions, please call the number on the back of your card.', flag: 'EXPLICITLY REFUSES SENSITIVE INFO + DIRECTS TO OFFICIAL CHANNEL' },
];

const SCAM_VERDICT = {
  type: 'scam', title: 'SCAM DETECTED — HANG UP NOW', score: '94% SCAM',
  sub: 'This call is a bank fraud. The caller wants to scare you into sharing an OTP. The moment you share it, your money is gone. Hang up. Never share an OTP with anyone.',
  flags: [
    'A real bank never asks for an OTP on a call — this is the #1 scam signal',
    '"Blocked in 2 hours" — urgency tactic, designed to stop you thinking',
    '"Do not tell anyone" — isolation tactic, so you cannot ask family or friends',
    'Fear + urgency combo — the pattern in almost every scam call',
    'Caller ID can be spoofed — a number that looks like the bank can still be a scam'
  ]
};

const SAFE_VERDICT = {
  type: 'safe', title: 'THIS CALL LOOKS SAFE', score: '8% SCAM RISK',
  sub: 'No scam signals found in this call. The caller asked for no OTP or password, showed no urgency, and directed you to an official channel.',
  safe: [
    'No OTP, password, or PIN was requested',
    'No urgency or threat — "3-5 days" is a normal timeline',
    'Caller directed you to the official number on the back of your card',
    'No pressure to keep the call confidential'
  ]
};

function setCaller(name, num, live) {
  document.getElementById('callerName').textContent = name;
  document.getElementById('callerNum').textContent = num;
  const st = document.getElementById('callStatus');
  st.textContent = live ? '● LIVE — AI LISTENING' : 'IDLE';
  st.className = 'px-3 py-1.5 rounded-md text-[11px] font-bold tracking-wider uppercase whitespace-nowrap ' +
    (live ? 'bg-[#FF2E63] text-white animate-pulse synth-glow' : 'bg-[#1a1a1a] text-[#888888]');
  const av = document.getElementById('avatar');
  if (av) {
    if (!live) av.textContent = '?';
    else if (name.indexOf('Unknown') === 0) av.textContent = '?';
    else av.textContent = name.charAt(0).toUpperCase();
  }
}

function runDemo(kind) {
  resetCall(true);
  const isScam = kind === 'scam';
  const script = isScam ? SCAM_SCRIPT : SAFE_SCRIPT;
  setCaller(
    isScam ? 'Unknown — Claims: “Bank Security”' : 'BawliManav — Claims: Bank Customer Care',
    isScam ? '+91 98XXX-XXXXX (unverified)' : '+91 1800-XXX-XXX (toll-free)',
    true
  );
  const empty = document.getElementById('transcriptEmpty');
  if (empty) empty.style.display = 'none';
  let i = 0;
  const step = () => {
    if (i >= script.length) { showVerdict(isScam ? SCAM_VERDICT : SAFE_VERDICT); return; }
    const line = script[i];
    const div = document.createElement('div');
    div.className = 'mb-3';
    div.innerHTML = '<div class="text-[10px] font-bold text-[#666666] uppercase tracking-[0.2em] mb-1">' + line.who + '</div>' +
      '<div class="inline-block max-w-[96%] text-[16px] leading-relaxed text-[#ebebeb] bg-[#1a1a1a] border border-[#333333] px-3.5 py-3 rounded-2xl">' + line.text + '</div>' +
      (line.flag ? '<br><span class="inline-block mt-1.5 text-xs font-bold text-[#FF6B8A] bg-[rgba(255,46,99,0.14)] border border-[rgba(255,46,99,0.45)] px-2.5 py-1 rounded-md">' + line.flag + '</span>' : '');
    document.getElementById('transcript').appendChild(div);
    div.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    i++;
    demoTimer = setTimeout(step, 1600);
  };
  step();
}

function showVerdict(v) {
  const el = document.getElementById('verdict');
  el.className = 'block rounded-[2rem] p-8 text-center text-white mb-4 ' + (v.type === 'scam' ? 'synth-verdict' : 'bg-[#4A5D23]');
  document.getElementById('verdictTitle').textContent = v.title;
  document.getElementById('verdictScore').textContent = v.score;
  document.getElementById('verdictSub').textContent = v.sub;
  const fc = document.getElementById('flagsCard');
  const sc = document.getElementById('safeCard');
  if (v.type === 'scam') {
    fc.style.display = 'block'; sc.style.display = 'none';
    document.getElementById('flagsList').innerHTML = v.flags.map(f => '<li>' + f + '</li>').join('');
  } else {
    sc.style.display = 'block'; fc.style.display = 'none';
    document.getElementById('safeList').innerHTML = v.safe.map(f => '<li>' + f + '</li>').join('');
  }
  document.getElementById('callActions').style.display = 'grid';
  setCaller(document.getElementById('callerName').textContent, document.getElementById('callerNum').textContent, false);
  el.scrollIntoView({ behavior: 'smooth' });
}

function hangUp() {
  alert('Call ended. You did the right thing — never share an OTP, password, or PIN on a call.');
  resetCall();
}
function reportScam() {
  alert('Scam reported (demo). In the real app, this number would go to a shared scam database and warn other users.');
}

function resetCall(silent) {
  if (demoTimer) { clearTimeout(demoTimer); demoTimer = null; }
  document.getElementById('transcript').innerHTML = '<div id="transcriptEmpty" class="text-center text-[#666666] text-[15px] leading-relaxed px-2.5 py-7">Start a demo call —<br>the transcript will appear here live,<br>and AI will flag every red flag as it speaks.</div>';
  document.getElementById('verdict').className = 'hidden';
  document.getElementById('flagsCard').style.display = 'none';
  document.getElementById('safeCard').style.display = 'none';
  document.getElementById('callActions').style.display = 'none';
  setCaller('No active call', 'Tap a demo below to simulate a call', false);
  if (!silent) window.scrollTo({ top: 0, behavior: 'smooth' });
}

/* ---------- Live analysis ---------- */
async function analyzeLive() {
  const text = document.getElementById('liveText').value.trim();
  const apiKey = document.getElementById('apiKey').value.trim();
  // Remember the key in THIS browser only: once entered, it auto-loads on
  // every visit and the page never asks again. Clearing the box and pressing
  // Analyse forgets it. The key is never written into the code or the repo.
  try {
    if (apiKey) localStorage.setItem('sableshield_key', apiKey);
    else localStorage.removeItem('sableshield_key');
  } catch (e) { /* private mode — ignore */ }
  hideError();
  if (!text) { showError('First type or paste what the caller said.'); return; }
  if (apiKey) {
    try {
      const v = await callGemma(apiKey, text);
      renderLiveVerdict(v);
      return;
    } catch (e) {
      showError('Gemma API failed: ' + e.message + ' — showing rule-based analysis instead.');
    }
  }
  renderLiveVerdict(ruleBased(text));
}

function ruleBased(text) {
  const t = text.toLowerCase();
  const flags = [];
  let score = 5;
  if (/otp|one time password|verification code/.test(t)) { flags.push('An OTP is being requested — a real bank or company never asks for an OTP on a call'); score += 40; }
  if (/block|blocked|freeze|suspend|close.*account|urgent|immediately|within.*hour|2 hours|today itself/.test(t)) { flags.push('Urgency / threat — “account blocked” used to rush you'); score += 25; }
  if (/do not tell|don.t tell|secret|confidential|don.t inform/.test(t)) { flags.push('“Do not tell anyone” — isolation tactic'); score += 20; }
  if (/password|pin|card number|cvv|bank detail|account number/.test(t)) { flags.push('Password, PIN, or card details are being requested'); score += 30; }
  if (/prize|won|lottery|gift|offer.*expire|click.*link|download.*app|anydesk|teamviewer/.test(t)) { flags.push('Prize, link, or remote-access app — common scam pattern'); score += 25; }
  if (/police|arrest|legal|case|fine/.test(t)) { flags.push('Police / legal threat — fear tactic'); score += 20; }
  score = Math.min(98, score);
  if (flags.length === 0) {
    return { type: 'safe', title: 'NO SCAM SIGNALS FOUND', score: 'LOW RISK', sub: 'No classic scam patterns found in this text. Still: never share an OTP or password on a call.', safe: ['No OTP or password demand', 'No urgency or threat', 'No isolation pressure'] };
  }
  return { type: 'scam', title: 'SCAM SIGNALS FOUND — BE CAREFUL', score: score + '% SCAM RISK', sub: flags.length + ' red flag(s) found. If the caller is asking for an OTP or password, hang up — this is almost certainly a scam.', flags };
}

async function callGemma(apiKey, text) {
  const prompt = `You are SableShield, a scam-call detector. Analyse this caller transcript and return ONLY valid JSON (no markdown, no fences):\n{\n  "type": "scam" or "safe",\n  "score": "e.g. 92% SCAM RISK or LOW RISK",\n  "title": "short verdict",\n  "sub": "one sentence explanation",\n  "flags": ["red flag 1", "red flag 2"],\n  "safe": ["safe reason 1"]\n}\nTranscript: ${text}`;
  const url = 'https://generativelanguage.googleapis.com/v1beta/models/gemma-4-26b-a4b-it:generateContent?key=' + encodeURIComponent(apiKey);
  const res = await fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ contents: [{ parts: [{ text: prompt }] }], generationConfig: { temperature: 0.2, maxOutputTokens: 800 } }) });
  if (!res.ok) throw new Error('API ' + res.status);
  const data = await res.json();
  let out = data.candidates?.[0]?.content?.parts?.[0]?.text || '';
  out = out.replace(/```json\s*/g, '').replace(/```\s*/g, '').trim();
  return JSON.parse(out);
}

function renderLiveVerdict(v) {
  const el = document.getElementById('verdict');
  el.className = 'block rounded-[2rem] p-8 text-center text-white mb-4 ' + (v.type === 'scam' ? 'synth-verdict' : 'bg-[#4A5D23]');
  document.getElementById('verdictTitle').textContent = v.title;
  document.getElementById('verdictScore').textContent = v.score;
  document.getElementById('verdictSub').textContent = v.sub;
  const fc = document.getElementById('flagsCard');
  const sc = document.getElementById('safeCard');
  if (v.type === 'scam') {
    fc.style.display = 'block'; sc.style.display = 'none';
    document.getElementById('flagsList').innerHTML = (v.flags || []).map(f => '<li>' + f + '</li>').join('');
  } else {
    sc.style.display = 'block'; fc.style.display = 'none';
    document.getElementById('safeList').innerHTML = (v.safe || []).map(f => '<li>' + f + '</li>').join('');
  }
  document.getElementById('callActions').style.display = 'grid';
  el.scrollIntoView({ behavior: 'smooth' });
}

function hideError() { document.getElementById('errorBox').classList.add('hidden'); }
function showError(m) { const el = document.getElementById('errorBox'); el.textContent = m; el.classList.remove('hidden'); }

/* Auto-load the saved key (this browser only, see analyzeLive). */
(function () {
  var keyInput = document.getElementById('apiKey');
  if (!keyInput) return;
  try {
    var saved = localStorage.getItem('sableshield_key');
    if (saved) keyInput.value = saved;
  } catch (e) { /* private mode — ignore */ }
})();
