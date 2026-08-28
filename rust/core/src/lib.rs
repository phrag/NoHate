use jni::objects::{JClass, JString, JObjectArray};
use jni::sys::{jfloat, jobjectArray};
use jni::JNIEnv;
use unicode_normalization::UnicodeNormalization;

/// Normalize the input so zero-width injections and decorative Unicode don't
/// trivially bypass phrase / word matching.
///
/// Conservative steps only — aggressive leet substitution proved to corrupt
/// legitimate input ("Great post!" should not become "great posti"), so leet
/// folding is applied per-token against the slur list only (see [`defeat_leet`]).
///
///   1. NFKC compose — folds full-width forms, compatibility chars, etc.
///   2. Strip zero-width joiners / non-joiners and bidi controls.
///   3. Collapse runs of 3+ identical chars to 2 ("looooser" -> "looser").
///   4. Lowercase.
fn normalize(text: &str) -> String {
	let mut out = String::with_capacity(text.len());
	let composed: String = text.nfkc().collect();
	for ch in composed.chars() {
		match ch {
			'\u{200B}' | '\u{200C}' | '\u{200D}' | '\u{FEFF}' => continue,
			'\u{202A}'..='\u{202E}' | '\u{2066}'..='\u{2069}' => continue,
			_ => out.push(ch),
		}
	}
	collapse_repeats(&out, 2).to_lowercase()
}

fn collapse_repeats(text: &str, max_run: usize) -> String {
	let mut out = String::with_capacity(text.len());
	let mut prev: Option<char> = None;
	let mut run = 0usize;
	for ch in text.chars() {
		if Some(ch) == prev {
			run += 1;
			if run <= max_run { out.push(ch); }
		} else {
			out.push(ch);
			prev = Some(ch);
			run = 1;
		}
	}
	out
}

/// Collapse a normalized string to a space-delimited, space-padded field of
/// alphanumeric tokens, so `field.contains(" needle ")` is a **word-boundary**
/// match rather than a substring match.
///
/// This is the fix for the class of false positive that dominated early builds:
/// plain `contains("kill")` fires on "skills", `contains("die")` fires on
/// "ladies" / "audience" / "medieval" / "foodie". Every needle below is matched
/// against this field, never against the raw text.
///
/// Punctuation becomes a separator, so needles must be written without it —
/// `"dont belong"`, not `"don't belong"`. `needles_are_canonical` enforces that.
fn word_field(normalized: &str) -> String {
	let mut s = String::with_capacity(normalized.len() + 2);
	s.push(' ');
	let mut last_space = true;
	for ch in normalized.chars() {
		if ch.is_alphanumeric() {
			s.push(ch);
			last_space = false;
		} else if !last_space {
			s.push(' ');
			last_space = true;
		}
	}
	if !last_space { s.push(' '); }
	s
}

/// Word-boundary containment against a [`word_field`] string.
fn has(field: &str, needle: &str) -> bool {
	let mut padded = String::with_capacity(needle.len() + 2);
	padded.push(' ');
	padded.push_str(needle);
	padded.push(' ');
	field.contains(&padded)
}

/// Fold common leet substitutions. Applied **per token, against the slur list
/// only** — never to the text as a whole, so benign phrasing can't be mangled
/// into a match.
fn defeat_leet(tok: &str) -> String {
	tok.chars()
		.map(|c| match c {
			'0' => 'o', '1' => 'i', '3' => 'e', '4' => 'a',
			'5' => 's', '7' => 't', '8' => 'b', '$' => 's', '@' => 'a',
			_ => c,
		})
		.collect()
}

// ---------------------------------------------------------------------------
// Tier 1 — coded hate symbols, numerals and slogans.
//
// Sourced from the ADL "Hate on Display" database and equivalents. These have
// essentially no benign use in a comment thread, so any one of them is enough
// to escalate on its own. Neural hate-speech classifiers reliably MISS these:
// they're lexical conventions, not linguistic patterns, and most public models
// were trained before these codes circulated widely.
// ---------------------------------------------------------------------------
const CODED: &[(&str, f32)] = &[
	("1488", 0.95),
	("14 88", 0.95),
	("6mwe", 0.95),
	("rahowa", 0.95),
	("wpww", 0.90),
	("groyper", 0.85),
	("groypers", 0.85),
	("zog", 0.80),
	("sieg heil", 0.95),
	("heil hitler", 0.95),
	("blood and soil", 0.90),
	("blut und boden", 0.90),
	("day of the rope", 0.95),
	("14 words", 0.90),
	("fourteen words", 0.90),
	("white genocide", 0.90),
	("great replacement", 0.85),
	("secure the existence of our people", 0.95),
	("back to the ovens", 0.95),
	("gas the", 0.95),
	("untermensch", 0.90),
	("subhuman", 0.85),
	("subhumans", 0.85),
	("remove kebab", 0.85),
];

/// Numerals that are hate codes in the right context but ordinary numbers
/// otherwise ("88" is also a year, a score, a follower count). Counted only
/// when the comment is *nothing but* the code — the realistic dog-whistle
/// case is a bare reply of "88" or "14" under someone's photo.
const WEAK_CODES: &[&str] = &["88", "14", "109", "110", "1352"];

// ---------------------------------------------------------------------------
// Tier 2 — unambiguous slurs.
//
// Deliberately a seed list, not an attempt at completeness: a comprehensive,
// current slur lexicon belongs in a maintained external dataset, not hardcoded
// here. Users extend it at runtime via the custom lexicon (Settings), which
// feeds `classifyWithUserLexicon`. See docs/MODELS.md.
//
// Matched per-token with leet folding, so "n1gg3r" and "f4gg0t" resolve too.
// ---------------------------------------------------------------------------
const SLURS: &[&str] = &[
	"nigger", "niggers", "nigga", "niggas",
	"faggot", "faggots", "fag", "fags",
	"tranny", "trannies", "shemale",
	"kike", "kikes", "yid",
	"spic", "spics", "wetback", "wetbacks", "beaner",
	"chink", "chinks", "gook", "gooks", "jap", "japs",
	"paki", "pakis", "raghead", "towelhead", "sandnigger",
	"coon", "coons", "darkie", "darkies",
	"retard", "retards", "retarded",
	"dyke", "dykes",
	"gypsy", "gypsies",
];

// ---------------------------------------------------------------------------
// Tier 3 — targeted attacks: an identity reference plus a hostile predicate.
//
// This is the structural definition of hate speech the app cares about — an
// attack aimed at a protected characteristic, as distinct from generic rudeness.
// ---------------------------------------------------------------------------
const IDENTITY: &[&str] = &[
	"women", "woman", "men", "man", "girls", "boys",
	"gay", "gays", "lesbian", "lesbians", "trans", "transgender", "queer", "homosexual",
	"black", "blacks", "white", "whites", "asian", "asians",
	"latino", "latina", "latinos", "hispanic",
	"jew", "jews", "jewish", "muslim", "muslims", "islam", "arab", "arabs",
	"hindu", "hindus", "sikh", "sikhs", "christian", "christians",
	"immigrant", "immigrants", "migrant", "migrants",
	"refugee", "refugees", "foreigner", "foreigners",
	"mexican", "mexicans", "indian", "indians", "african", "africans",
	"chinese", "japanese", "korean", "turks", "kurds",
	"disabled", "autistic",
	"you people", "those people", "these people", "your kind",
];

/// Hostile predicates that only count as an attack when an identity reference
/// is also present. On their own these are ordinary English — "go back to the
/// gym", "get out of here!", "they don't belong in this playlist".
const HOSTILE_WITH_IDENTITY: &[(&str, f32)] = &[
	("go back to", 0.85),
	("go back where", 0.90),
	("go home", 0.70),
	("get out of our", 0.85),
	("get out of this country", 0.90),
	("dont belong", 0.75),
	("do not belong", 0.75),
	("doesnt belong", 0.75),
	("should be deported", 0.90),
	("deport them", 0.85),
	("deport every", 0.90),
	("not welcome here", 0.75),
	("ruining our", 0.75),
	("invading our", 0.85),
	("breeding like", 0.90),
	("go back to your", 0.90),
];

/// Hostile predicates that are an attack regardless of what precedes them.
const HOSTILE_STANDALONE: &[(&str, f32)] = &[
	("are subhuman", 0.95),
	("are vermin", 0.95),
	("are parasites", 0.90),
	("are cockroaches", 0.95),
	("are animals", 0.80),
	("are a disease", 0.90),
	("are a plague", 0.90),
	("should be gassed", 0.95),
	("should be exterminated", 0.95),
	("should all die", 0.95),
	("should be wiped out", 0.95),
	("deserve to die", 0.90),
];

// ---------------------------------------------------------------------------
// Tier 4 — harassment. Abusive and worth flagging, but not hate speech in the
// protected-characteristic sense. Kept separate so the UI can eventually say
// *which* kind of problem a comment is.
// ---------------------------------------------------------------------------
const HARASSMENT: &[(&str, f32)] = &[
	("kill yourself", 0.95),
	("kill your self", 0.95),
	("kys", 0.90),
	("neck yourself", 0.95),
	("hang yourself", 0.95),
	("go die", 0.90),
	("hope you die", 0.90),
	("you should die", 0.90),
	("nobody wants you here", 0.70),
	("fuck you", 0.70),
	("stupid bitch", 0.80),
	("dumb bitch", 0.80),
	("piece of shit", 0.65),
	("worthless piece", 0.70),
];

/// Combine independent signals: take the strongest, then add a small bonus per
/// additional distinct signal.
///
/// Deliberately *not* additive — the old scorer summed raw weights, so two
/// unrelated weak hits ("dirty" + "go back") reached 1.0 and flagged at maximum
/// confidence. Deliberately not noisy-OR either, which stacks nearly as fast.
fn combine(weights: &[f32]) -> f32 {
	if weights.is_empty() { return 0.0; }
	let max = weights.iter().cloned().fold(0.0f32, f32::max);
	let extra = (weights.len() - 1) as f32 * 0.05;
	(max + extra).min(1.0)
}

fn compute_score_base(normalized: &str) -> f32 {
	let field = word_field(normalized);
	let mut hits: Vec<f32> = Vec::new();

	// Tier 1 — coded terms and slogans.
	for (needle, w) in CODED.iter() {
		if has(&field, needle) { hits.push(*w); }
	}

	// Bare numeric codes: only when the comment is nothing but the code.
	let toks: Vec<&str> = field.split_whitespace().collect();
	if toks.len() == 1 && WEAK_CODES.contains(&toks[0]) {
		hits.push(0.75);
	}

	// Triple-parenthesis "echo" notation around a name. Note that
	// `collapse_repeats` has already folded "(((" down to "((" by this point,
	// so match the collapsed form — checking for three would never fire.
	if normalized.contains("((") && normalized.contains("))") {
		hits.push(0.90);
	}

	// Tier 2 — slurs, per token, with leet folding.
	for tok in toks.iter() {
		if SLURS.contains(tok) || SLURS.contains(&defeat_leet(tok).as_str()) {
			hits.push(0.95);
			break;
		}
	}

	// Tier 3 — targeted attacks.
	let has_identity = IDENTITY.iter().any(|t| has(&field, t));
	if has_identity {
		for (needle, w) in HOSTILE_WITH_IDENTITY.iter() {
			if has(&field, needle) { hits.push(*w); }
		}
	}
	for (needle, w) in HOSTILE_STANDALONE.iter() {
		if has(&field, needle) { hits.push(*w); }
	}

	// Tier 4 — harassment.
	for (needle, w) in HARASSMENT.iter() {
		if has(&field, needle) { hits.push(*w); }
	}

	combine(&hits)
}

fn compute_score_with_user(text: &str, user_hate: &[String], user_safe: &[String]) -> f32 {
	let norm = normalize(text);
	let field = word_field(&norm);
	let mut score = compute_score_base(&norm);

	// User phrases are matched on word boundaries too, so a one-word entry
	// can't fire on a substring of an unrelated word.
	for phrase in user_hate.iter() {
		let p = word_field(&normalize(phrase));
		let p = p.trim();
		if !p.is_empty() && has(&field, p) {
			score = combine(&[score, 0.85]);
		}
	}
	for phrase in user_safe.iter() {
		let p = word_field(&normalize(phrase));
		let p = p.trim();
		if !p.is_empty() && has(&field, p) { score -= 0.4; }
	}
	if score < 0.0 { 0.0 } else if score > 1.0 { 1.0 } else { score }
}

#[no_mangle]
pub extern "system" fn Java_com_nohate_app_NativeClassifier_classify(
	mut env: JNIEnv,
	_class: JClass,
	input: JString,
) -> jfloat {
	let text: String = env.get_string(&input).map(|s| s.into()).unwrap_or_default();
	compute_score_base(&normalize(&text)) as jfloat
}

#[no_mangle]
pub extern "system" fn Java_com_nohate_app_NativeClassifier_classifyWithUserLexicon(
	mut env: JNIEnv,
	_class: JClass,
	input: JString,
	user_hate_arr: jobjectArray,
	user_safe_arr: jobjectArray,
) -> jfloat {
	let text: String = env.get_string(&input).map(|s| s.into()).unwrap_or_default();
	let user_hate = jstring_array_to_vec(&mut env, user_hate_arr);
	let user_safe = jstring_array_to_vec(&mut env, user_safe_arr);
	compute_score_with_user(&text, &user_hate, &user_safe) as jfloat
}

fn jstring_array_to_vec(env: &mut JNIEnv, arr: jobjectArray) -> Vec<String> {
	if arr.is_null() { return Vec::new(); }
	let joa: JObjectArray = unsafe { JObjectArray::from_raw(arr) };
	let len = env.get_array_length(&joa).unwrap_or(0);
	let mut out = Vec::with_capacity(len as usize);
	for i in 0..len {
		let obj = match env.get_object_array_element(&joa, i) { Ok(o) => o, Err(_) => continue };
		let js: JString = JString::from(obj);
		let rust_string = match env.get_string(&js) {
			Ok(javastr) => javastr.into(),
			Err(_) => continue,
		};
		out.push(rust_string);
	}
	out
}

#[cfg(test)]
mod tests {
	use super::*;

	fn score(s: &str) -> f32 { compute_score_base(&normalize(s)) }

	#[test]
	fn normalize_strips_zero_width() {
		assert_eq!(normalize("kill\u{200B} yourself"), "kill yourself");
	}

	#[test]
	fn normalize_collapses_long_runs() {
		assert_eq!(normalize("loooooser"), "looser");
	}

	#[test]
	fn normalize_preserves_punctuation() {
		assert_eq!(normalize("Great post!"), "great post!");
	}

	/// Every needle must already be in `word_field` form — lowercase,
	/// alphanumerics separated by single spaces. A needle containing an
	/// apostrophe or hyphen can never match, silently.
	#[test]
	fn needles_are_canonical() {
		let mut all: Vec<&str> = Vec::new();
		all.extend(CODED.iter().map(|(n, _)| *n));
		all.extend(WEAK_CODES.iter().copied());
		all.extend(SLURS.iter().copied());
		all.extend(IDENTITY.iter().copied());
		all.extend(HOSTILE_WITH_IDENTITY.iter().map(|(n, _)| *n));
		all.extend(HOSTILE_STANDALONE.iter().map(|(n, _)| *n));
		all.extend(HARASSMENT.iter().map(|(n, _)| *n));
		for n in all {
			let canonical = word_field(n);
			assert_eq!(
				format!(" {} ", n),
				canonical,
				"needle {:?} is not in canonical word_field form",
				n
			);
		}
	}

	// -- regression: the substring-matching false positives -------------------

	/// These are the exact comments that the old substring scorer flagged.
	/// "kill" matched inside "skills", "die" inside "ladies" / "audience" /
	/// "medieval" / "foodie" / "indie" / "candies" / "buddies" / "oldies".
	#[test]
	fn benign_comments_do_not_flag() {
		let benign = [
			"Love your skills, ladies!",
			"The audience went wild",
			"Killer outfit!!",
			"Studies show this works",
			"Best foodie account out there",
			"Oldies but goldies",
			"This is my new diet inspo",
			"Can't wait for the indie release",
			"Those candies look amazing",
			"I hate Mondays lol",
			"Go back to posting more of these!",
			"Get out of here, that's incredible",
			"Dirty martini season",
			"Great post!",
			"Such a dumbbell workout beast",
			"Buddies forever",
			"medieval history is underrated",
			"Amen to that",
			"Congrats on the new management role",
			"immigrants make great neighbours",
			"the women in this shot look stunning",
			"88 likes already, congrats!",
		];
		for t in benign.iter() {
			let s = score(t);
			assert!(s < 0.5, "benign comment flagged: {:?} scored {}", t, s);
		}
	}

	// -- coded / dog-whistle content -----------------------------------------

	#[test]
	fn coded_hate_is_caught() {
		let coded = [
			"1488 brother",
			"we must secure the existence of our people",
			"day of the rope soon",
			"(((they))) control everything",
			"groypers rise up",
			"6MWE",
			"RAHOWA",
			"it's the great replacement, wake up",
			"blood and soil",
		];
		for t in coded.iter() {
			let s = score(t);
			assert!(s >= 0.8, "coded hate missed: {:?} scored {}", t, s);
		}
	}

	#[test]
	fn bare_numeric_code_flags_only_when_alone() {
		assert!(score("88") >= 0.7, "bare 88 should flag");
		assert!(score("14") >= 0.7, "bare 14 should flag");
		assert!(score("we hit 88 degrees today") < 0.5, "88 in a sentence should not flag");
	}

	// -- targeted attacks -----------------------------------------------------

	#[test]
	fn identity_plus_hostile_predicate_flags() {
		assert!(score("immigrants should go back to their country") >= 0.8);
		assert!(score("these people are ruining our neighbourhood") >= 0.7);
		assert!(score("muslims are not welcome here") >= 0.7);
	}

	#[test]
	fn hostile_predicate_without_identity_does_not_flag() {
		// "go back to" only counts alongside an identity reference.
		assert!(score("go back to the gym champ") < 0.5);
		assert!(score("I'll go back to that cafe tomorrow") < 0.5);
	}

	#[test]
	fn standalone_dehumanisation_flags_without_identity_term() {
		assert!(score("they are vermin") >= 0.9);
		assert!(score("they should be exterminated") >= 0.9);
	}

	// -- slurs ----------------------------------------------------------------

	#[test]
	fn leet_slurs_are_caught() {
		assert!(score("f4gg0t") >= 0.9, "leet slur missed");
		assert!(score("you are a r3t4rd") >= 0.9, "leet slur missed");
	}

	#[test]
	fn slur_matching_is_word_bounded() {
		// "coon" must not fire on "raccoon"/"cocoon"; "fag" not on "fagot" ->
		// note "faggots" is listed, but a bassoon-adjacent word must be safe.
		assert!(score("look at that raccoon") < 0.5);
		assert!(score("the cocoon opened") < 0.5);
	}

	// -- harassment -----------------------------------------------------------

	#[test]
	fn harassment_still_flags() {
		assert!(score("just go kill yourself") >= 0.9);
		assert!(score("kys loser") >= 0.85);
		assert!(score("fuck you") >= 0.7);
	}

	// -- combination ----------------------------------------------------------

	#[test]
	fn weak_signals_do_not_stack_to_certainty() {
		// Two mid-weight hits should not reach 1.0 the way the additive
		// scorer did.
		let s = combine(&[0.7, 0.7]);
		assert!(s < 0.8, "two 0.7 signals combined to {}", s);
	}

	#[test]
	fn user_lexicon_matches_on_word_boundaries() {
		let hate = vec!["clown".to_string()];
		let safe: Vec<String> = vec![];
		assert!(compute_score_with_user("you absolute clown", &hate, &safe) >= 0.8);
		// must not fire on a substring of an unrelated word
		assert!(compute_score_with_user("clowning around at the circus", &hate, &safe) < 0.5);
	}

	#[test]
	fn user_safe_phrase_suppresses() {
		let hate: Vec<String> = vec![];
		let safe = vec!["inside joke".to_string()];
		let without = compute_score_with_user("fuck you", &hate, &vec![]);
		let with = compute_score_with_user("fuck you, inside joke", &hate, &safe);
		assert!(with < without, "safe phrase did not reduce score");
	}
}
