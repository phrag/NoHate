use jni::objects::{JClass, JString, JObjectArray};
use jni::sys::{jfloat, jobjectArray};
use jni::JNIEnv;
use unicode_normalization::UnicodeNormalization;

/// Normalize the input so zero-width injections and decorative Unicode don't
/// trivially bypass phrase / word matching.
///
/// Conservative steps only — aggressive leet substitution proved to corrupt
/// legitimate input ("Great post!" should not become "great posti"). A future
/// pass can add context-aware leet handling.
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

/// Identity references that, when paired with a hateful word, push the score
/// up. Conservative list; expand with care to avoid false positives.
const IDENTITY_TERMS: &[&str] = &[
	"you people", "those people", "these people",
	"women", "men", "girls", "boys",
	"gay", "lesbian", "trans", "queer",
	"black", "white", "asian", "latino", "latina", "jewish", "muslim",
	"immigrants", "refugees", "foreigners",
];

fn compute_score_base(normalized: &str) -> f32 {
	let mut score: f32 = 0.0;
	let phrases: [(&str, f32); 8] = [
		("fuck you", 0.9),
		("kill yourself", 1.0),
		("go die", 0.95),
		("stupid bitch", 0.95),
		("you people", 0.6),
		("dirty", 0.5),
		("get out", 0.4),
		("go back", 0.5),
	];
	for (p, w) in phrases.iter() {
		if normalized.contains(p) { score += *w; }
	}
	let words: [(&str, f32); 12] = [
		("awful", 0.5), ("toxic", 0.6), ("abuse", 0.7), ("hate", 0.7),
		("kill", 0.9), ("die", 0.8), ("bitch", 0.8), ("slur", 0.7),
		("idiot", 0.5), ("dumb", 0.4), ("stupid", 0.5), ("trash", 0.4),
	];
	let mut hateful_word_hit = false;
	for (w, weight) in words.iter() {
		if normalized.contains(w) {
			score += *weight;
			hateful_word_hit = true;
		}
	}
	if hateful_word_hit && IDENTITY_TERMS.iter().any(|t| normalized.contains(t)) {
		score += 0.2;
	}
	if score > 1.0 { 1.0 } else { score }
}

fn compute_score_with_user(text: &str, user_hate: &[String], user_safe: &[String]) -> f32 {
	let norm = normalize(text);
	let mut score = compute_score_base(&norm);
	for phrase in user_hate.iter() {
		if !phrase.is_empty() && norm.contains(&phrase.to_lowercase()) { score += 0.8; }
	}
	for phrase in user_safe.iter() {
		if !phrase.is_empty() && norm.contains(&phrase.to_lowercase()) { score -= 0.4; }
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

	#[test]
	fn base_score_picks_up_kill_yourself() {
		let n = normalize("just go kill yourself");
		let s = compute_score_base(&n);
		assert!(s >= 0.9, "expected >=0.9, got {}", s);
	}

	#[test]
	fn identity_boost_only_with_hateful_word() {
		let neutral = compute_score_base(&normalize("immigrants make great neighbours"));
		assert!(neutral < 0.3, "neutral identity mention should not score, got {}", neutral);
		let hateful = compute_score_base(&normalize("immigrants are awful trash"));
		assert!(hateful > 0.8, "identity + hateful words should score >0.8, got {}", hateful);
	}
}
