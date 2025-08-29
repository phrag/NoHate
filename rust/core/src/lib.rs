use jni::objects::{JClass, JString, JObjectArray};
use jni::sys::{jfloat, jobjectArray};
use jni::JNIEnv;

fn compute_score_base(text: &str) -> f32 {
	let normalized = text.to_lowercase();
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
	for (w, weight) in words.iter() {
		if normalized.contains(w) { score += *weight; }
	}
	if score > 1.0 { 1.0 } else { score }
}

fn compute_score_with_user(text: &str, user_hate: &[String], user_safe: &[String]) -> f32 {
	let mut score = compute_score_base(text);
	let norm = text.to_lowercase();
	for phrase in user_hate.iter() {
		if norm.contains(phrase) { score += 0.6; }
	}
	for phrase in user_safe.iter() {
		if norm.contains(phrase) { score -= 0.4; }
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
	compute_score_base(&text) as jfloat
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
