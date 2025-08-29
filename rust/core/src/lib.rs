use jni::objects::{JClass, JString};
use jni::sys::jfloat;
use jni::JNIEnv;

fn compute_score(text: &str) -> f32 {
	let normalized = text.to_lowercase();
	let mut score: f32 = 0.0;

	// Weighted phrase matches (higher severity)
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
		if normalized.contains(p) {
			score += *w;
		}
	}

	// Weighted single-word matches
	let words: [(&str, f32); 12] = [
		("awful", 0.5),
		("toxic", 0.6),
		("abuse", 0.7),
		("hate", 0.7),
		("kill", 0.9),
		("die", 0.8),
		("bitch", 0.8),
		("slur", 0.7),
		("idiot", 0.5),
		("dumb", 0.4),
		("stupid", 0.5),
		("trash", 0.4),
	];
	for (w, weight) in words.iter() {
		if normalized.contains(w) {
			score += *weight;
		}
	}

	// Cap and normalize to [0,1]
	if score > 1.0 { 1.0 } else { score }
}

#[no_mangle]
pub extern "system" fn Java_com_nohate_app_NativeClassifier_classify(
	mut env: JNIEnv,
	_class: JClass,
	input: JString,
) -> jfloat {
	let text: String = env.get_string(&input).map(|s| s.into()).unwrap_or_default();
	compute_score(&text) as jfloat
}
